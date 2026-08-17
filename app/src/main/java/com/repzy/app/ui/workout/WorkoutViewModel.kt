package com.repzy.app.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repzy.app.core.DailyPlan
import com.repzy.app.core.DailyPlanner
import com.repzy.app.data.model.Exercise
import com.repzy.app.data.model.Workout
import com.repzy.app.data.model.WorkoutSet
import com.repzy.app.data.repo.ExerciseRepository
import com.repzy.app.data.repo.ProfileRepository
import com.repzy.app.data.repo.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinLocalDate
import javax.inject.Inject

/** Seanstaki tek egzersiz: girilen setler + geçen seferin referansı. */
data class WorkoutEntry(
    val exercise: Exercise,
    val sets: List<WorkoutSet> = emptyList(),
    val lastTime: List<WorkoutSet> = emptyList(),
    val repsInput: String = "",
    val weightInput: String = "",
    val durationInput: String = "",
    val isSaving: Boolean = false,
) {
    val canSave: Boolean
        get() = !isSaving && if (exercise.isTimeBased) {
            durationInput.toIntOrNull()?.let { it > 0 } == true
        } else {
            repsInput.toIntOrNull()?.let { it > 0 } == true
        }

    val totalVolumeKg: Double get() = sets.sumOf { it.volumeKg }
}

data class WorkoutUiState(
    val isLoading: Boolean = true,
    val workout: Workout? = null,
    val entries: List<WorkoutEntry> = emptyList(),
    val history: List<Workout> = emptyList(),
    /** Bugünün kural tabanlı planı; oturum başlatılmadan gösterilir. */
    val plan: DailyPlan? = null,
    val perceivedEffort: Int? = null,
    val isFinishing: Boolean = false,
    val error: String? = null,
) {
    val hasActiveWorkout: Boolean get() = workout?.isActive == true
    val loggedSetCount: Int get() = entries.sumOf { it.sets.size }
    val totalVolumeKg: Double get() = entries.sumOf { it.totalVolumeKg }
}

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WorkoutUiState())
    val state: StateFlow<WorkoutUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            val (active, history) = coroutineScope {
                val activeJob = async { workoutRepository.activeWorkout() }
                val historyJob = async { workoutRepository.recentWorkouts() }
                activeJob.await() to historyJob.await()
            }
            val failure = listOf(active, history).firstNotNullOfOrNull { it.exceptionOrNull() }

            val workout = active.getOrNull()
            _state.update {
                it.copy(
                    isLoading = false,
                    workout = workout,
                    history = history.getOrDefault(emptyList()),
                    error = failure?.message,
                )
            }
            if (workout?.id != null) restoreEntries(workout.id)
            buildPlan(history.getOrDefault(emptyList()))
        }
    }

    /**
     * Günün planını kurar. Kural tabanlı ve tamamen istemcide — AI çağrısı yok,
     * yani anında ve ücretsiz. Girdi: profil + kütüphane + son 7 günün antrenmanları.
     */
    private suspend fun buildPlan(history: List<Workout>) {
        val (profile, library) = coroutineScope {
            val p = async { profileRepository.getProfile() }
            val l = async { exerciseRepository.all() }
            p.await().getOrNull() to l.await().getOrDefault(emptyList())
        }

        val goal = profile?.goal ?: return
        val level = profile.experienceLevel ?: return
        val equipment = profile.equipmentAccess ?: return
        if (library.isEmpty()) return

        val today = java.time.LocalDate.now().toKotlinLocalDate()
        val trained = history.mapNotNull { it.startedAt?.take(10)?.toLocalDateOrNull() }.toSet()

        val plan = DailyPlanner.planFor(
            today = today,
            goal = goal,
            level = level,
            equipment = equipment,
            trainedDatesLast7 = trained.filter { it.toEpochDays() > today.toEpochDays() - 7 }.toSet(),
            library = library,
        )
        _state.update { it.copy(plan = plan) }
    }

    /** Planı seansa çevirir: antrenmanı başlatıp planlanan hareketleri ekliyor. */
    fun startPlannedWorkout() {
        val plan = _state.value.plan ?: return
        if (plan.exercises.isEmpty() || _state.value.hasActiveWorkout) return

        viewModelScope.launch {
            workoutRepository.startWorkout(title = null)
                .onSuccess { workout ->
                    _state.update {
                        it.copy(workout = workout, entries = emptyList(), perceivedEffort = null)
                    }
                    plan.exercises.forEach { planned -> addExercise(planned.exercise.id) }
                }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    /** Uygulama kapanıp açıldıysa girilen setlerden egzersiz listesini yeniden kur. */
    private suspend fun restoreEntries(workoutId: String) {
        val (sets, all) = coroutineScope {
            val setsJob = async { workoutRepository.setsOf(workoutId).getOrDefault(emptyList()) }
            val allJob = async { exerciseRepository.all().getOrDefault(emptyList()) }
            setsJob.await() to allJob.await()
        }
        if (sets.isEmpty()) return

        val entries = sets.map { it.exerciseId }.distinct().mapNotNull { exerciseId ->
            all.find { it.id == exerciseId }?.let { exercise ->
                WorkoutEntry(
                    exercise = exercise,
                    sets = sets.filter { it.exerciseId == exerciseId }.sortedBy { it.setIndex },
                )
            }
        }
        _state.update { it.copy(entries = entries) }
        // Egzersiz başına ayrı sorgu; sırayla beklenirse beş egzersizde beş tur bekleniyor.
        coroutineScope {
            entries.map { async { loadLastPerformance(it.exercise.id, workoutId) } }.forEach { it.await() }
        }
    }

    fun startWorkout() {
        if (_state.value.hasActiveWorkout) return
        viewModelScope.launch {
            workoutRepository.startWorkout(title = null)
                .onSuccess { workout ->
                    _state.update {
                        it.copy(workout = workout, entries = emptyList(), perceivedEffort = null)
                    }
                }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun addExercise(exerciseId: String) {
        val workoutId = _state.value.workout?.id ?: return
        if (_state.value.entries.any { it.exercise.id == exerciseId }) return

        viewModelScope.launch {
            exerciseRepository.byId(exerciseId).getOrNull()?.let { exercise ->
                _state.update { it.copy(entries = it.entries + WorkoutEntry(exercise = exercise)) }
                loadLastPerformance(exerciseId, workoutId)
            }
        }
    }

    fun removeExercise(exerciseId: String) {
        // Sadece hiç set girilmemiş egzersiz listeden çıkarılır; girilen set silinmez.
        _state.update { state ->
            state.copy(
                entries = state.entries.filterNot {
                    it.exercise.id == exerciseId && it.sets.isEmpty()
                },
            )
        }
    }

    private suspend fun loadLastPerformance(exerciseId: String, workoutId: String) {
        val last = workoutRepository.lastPerformance(exerciseId, workoutId).getOrDefault(emptyList())
        if (last.isEmpty()) return
        updateEntry(exerciseId) { it.copy(lastTime = last) }
    }

    fun onRepsChange(exerciseId: String, value: String) =
        updateEntry(exerciseId) { it.copy(repsInput = value.filter(Char::isDigit).take(3)) }

    fun onWeightChange(exerciseId: String, value: String) =
        updateEntry(exerciseId) { it.copy(weightInput = value.asDecimal()) }

    fun onDurationChange(exerciseId: String, value: String) =
        updateEntry(exerciseId) { it.copy(durationInput = value.filter(Char::isDigit).take(4)) }

    fun saveSet(exerciseId: String) {
        val state = _state.value
        val workoutId = state.workout?.id ?: return
        val entry = state.entries.find { it.exercise.id == exerciseId } ?: return
        if (!entry.canSave) return

        val newSet = WorkoutSet(
            workoutId = workoutId,
            exerciseId = exerciseId,
            setIndex = entry.sets.size + 1,
            reps = if (entry.exercise.isTimeBased) null else entry.repsInput.toIntOrNull(),
            weightKg = entry.weightInput.toDoubleOrNull(),
            durationSec = if (entry.exercise.isTimeBased) entry.durationInput.toIntOrNull() else null,
        )

        updateEntry(exerciseId) { it.copy(isSaving = true) }
        viewModelScope.launch {
            workoutRepository.addSet(newSet)
                .onSuccess { saved ->
                    updateEntry(exerciseId) {
                        it.copy(
                            sets = it.sets + saved,
                            isSaving = false,
                            // Tekrar/ağırlık kalır: sonraki set genelde aynı, tekrar yazdırmayalım.
                            repsInput = it.repsInput,
                            weightInput = it.weightInput,
                        )
                    }
                }
                .onFailure { e ->
                    updateEntry(exerciseId) { it.copy(isSaving = false) }
                    _state.update { it.copy(error = e.message) }
                }
        }
    }

    fun deleteSet(exerciseId: String, setId: String) {
        viewModelScope.launch {
            workoutRepository.deleteSet(setId)
                .onSuccess {
                    updateEntry(exerciseId) { entry ->
                        val remaining = entry.sets.filterNot { it.id == setId }
                        // set_index'ler boşluksuz kalsın diye yeniden numaralanır (sadece gösterimde).
                        entry.copy(
                            sets = remaining.mapIndexed { index, set ->
                                set.copy(setIndex = index + 1)
                            },
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun setPerceivedEffort(value: Int) =
        _state.update { it.copy(perceivedEffort = if (it.perceivedEffort == value) null else value) }

    fun finishWorkout() {
        val state = _state.value
        val workoutId = state.workout?.id ?: return
        _state.update { it.copy(isFinishing = true) }

        viewModelScope.launch {
            // Hiç set girilmemişse boş satır bırakmak yerine antrenmanı sil.
            val result = if (state.loggedSetCount == 0) {
                workoutRepository.discardWorkout(workoutId)
            } else {
                workoutRepository.finishWorkout(workoutId, state.perceivedEffort)
            }

            result
                .onSuccess {
                    _state.update {
                        it.copy(
                            workout = null,
                            entries = emptyList(),
                            perceivedEffort = null,
                            isFinishing = false,
                        )
                    }
                    load()
                }
                .onFailure { e ->
                    _state.update { it.copy(isFinishing = false, error = e.message) }
                }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    private fun updateEntry(exerciseId: String, transform: (WorkoutEntry) -> WorkoutEntry) {
        _state.update { state ->
            state.copy(
                entries = state.entries.map { entry ->
                    if (entry.exercise.id == exerciseId) transform(entry) else entry
                },
            )
        }
    }
}

/** "2026-08-17" → LocalDate; biçim beklenmedikse null (sunucu formatı değişirse çökmesin). */
private fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()

/** Ağırlık alanı: rakam ve tek ondalık ayırıcı, virgül noktaya çevrilir. */
private fun String.asDecimal(): String {
    val normalized = replace(',', '.').filter { it.isDigit() || it == '.' }
    val firstDot = normalized.indexOf('.')
    if (firstDot == -1) return normalized.take(5)
    val head = normalized.substring(0, firstDot + 1)
    val tail = normalized.substring(firstDot + 1).filter(Char::isDigit).take(1)
    return (head + tail).take(6)
}
