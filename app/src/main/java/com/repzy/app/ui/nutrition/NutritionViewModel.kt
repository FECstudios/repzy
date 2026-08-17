package com.repzy.app.ui.nutrition

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repzy.app.core.ImagePrep
import com.repzy.app.data.model.DayNutrition
import com.repzy.app.data.model.FoodLog
import com.repzy.app.data.model.FoodLogSource
import com.repzy.app.data.model.MealAnalysis
import com.repzy.app.data.model.MealItem
import com.repzy.app.data.model.MealType
import com.repzy.app.data.model.NutritionTarget
import com.repzy.app.data.repo.AiFailure
import com.repzy.app.data.repo.MealRepository
import com.repzy.app.data.repo.ProfileRepository
import com.repzy.app.data.repo.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinLocalDate
import javax.inject.Inject

/** Onay bekleyen tahmin: kullanıcı kalemleri çıkarabilir, öğünü seçer, sonra kaydeder. */
data class PendingAnalysis(
    val analysis: MealAnalysis,
    val meal: MealType,
    val excluded: Set<Int> = emptySet(),
    val isSaving: Boolean = false,
) {
    val keptItems: List<MealItem>
        get() = analysis.items.filterIndexed { index, _ -> index !in excluded }

    val calories: Int get() = keptItems.sumOf { it.calories }.toInt()
    val proteinG: Int get() = keptItems.sumOf { it.proteinG }.toInt()
    val carbsG: Int get() = keptItems.sumOf { it.carbsG }.toInt()
    val fatG: Int get() = keptItems.sumOf { it.fatG }.toInt()
    val canSave: Boolean get() = !isSaving && keptItems.isNotEmpty()
}

data class NutritionUiState(
    val isLoading: Boolean = true,
    val day: DayNutrition = DayNutrition(),
    val target: NutritionTarget? = null,
    val isAnalyzing: Boolean = false,
    val pending: PendingAnalysis? = null,
    val scansRemaining: Int? = null,
    val error: String? = null,
    val isPremium: Boolean = false,
) {
    val calorieTarget: Int get() = target?.calories ?: 0
    val calorieProgress: Float
        get() = if (calorieTarget <= 0) 0f else (day.calories.toFloat() / calorieTarget).coerceIn(0f, 1f)

    val caloriesLeft: Int get() = (calorieTarget - day.calories).coerceAtLeast(0)
}

@HiltViewModel
class NutritionViewModel @Inject constructor(
    private val mealRepository: MealRepository,
    private val profileRepository: ProfileRepository,
    private val subscriptionRepository: SubscriptionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(NutritionUiState())
    val state: StateFlow<NutritionUiState> = _state.asStateFlow()

    private val today: LocalDate get() = java.time.LocalDate.now().toKotlinLocalDate()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            val (day, target) = coroutineScope {
                val dayJob = async { mealRepository.logsOf(today) }
                val targetJob = async { profileRepository.activeNutritionTarget() }
                dayJob.await() to targetJob.await()
            }

            val premium = subscriptionRepository.isPremium().getOrDefault(false)
            _state.update {
                it.copy(
                    isLoading = false,
                    isPremium = premium,
                    day = day.getOrDefault(DayNutrition()),
                    target = target.getOrNull(),
                    error = day.exceptionOrNull()?.message,
                )
            }
        }
    }

    /** Fotoğraf seçildi/çekildi: küçült, Edge Function'a gönder, onay ekranına taşı. */
    fun analyzePhoto(context: Context, uri: Uri, meal: MealType, turkish: Boolean) {
        if (_state.value.isAnalyzing) return
        _state.update { it.copy(isAnalyzing = true, error = null) }

        viewModelScope.launch {
            // Ölçekleme ve base64 CPU işi — ana thread'i bloklamasın.
            val encoded = withContext(Dispatchers.Default) {
                ImagePrep.toBase64Jpeg(context, uri)
            }

            encoded
                .onFailure { e ->
                    _state.update { it.copy(isAnalyzing = false, error = e.message) }
                }
                .onSuccess { base64 ->
                    mealRepository
                        .analyzePhoto(base64, locale = if (turkish) "tr" else "en")
                        .onSuccess { analysis ->
                            _state.update {
                                it.copy(
                                    isAnalyzing = false,
                                    scansRemaining = analysis.scansRemaining,
                                    // Yemek bulunamadıysa onay ekranı açmayalım, notu hata olarak göster.
                                    pending = if (analysis.isEmpty) {
                                        null
                                    } else {
                                        PendingAnalysis(analysis = analysis, meal = meal)
                                    },
                                    error = if (analysis.isEmpty) {
                                        analysis.note ?: "Fotoğrafta yemek bulunamadı."
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                        .onFailure { e ->
                            _state.update {
                                it.copy(
                                    isAnalyzing = false,
                                    error = e.message,
                                    scansRemaining = (e as? AiFailure.DailyLimitReached)
                                        ?.let { 0 } ?: it.scansRemaining,
                                )
                            }
                        }
                }
        }
    }

    fun toggleItem(index: Int) = _state.update { state ->
        val pending = state.pending ?: return@update state
        val excluded = if (index in pending.excluded) {
            pending.excluded - index
        } else {
            pending.excluded + index
        }
        state.copy(pending = pending.copy(excluded = excluded))
    }

    fun setPendingMeal(meal: MealType) = _state.update { state ->
        state.copy(pending = state.pending?.copy(meal = meal))
    }

    fun discardPending() = _state.update { it.copy(pending = null) }

    fun confirmPending() {
        val pending = _state.value.pending ?: return
        if (!pending.canSave) return
        _state.update { it.copy(pending = pending.copy(isSaving = true)) }

        val logs = pending.keptItems.map { item ->
            FoodLog(
                logDate = today,
                meal = pending.meal,
                name = item.name,
                grams = item.grams,
                calories = item.calories,
                proteinG = item.proteinG,
                carbsG = item.carbsG,
                fatG = item.fatG,
                fiberG = item.fiberG,
                source = FoodLogSource.AI_PHOTO,
                aiConfidence = pending.analysis.confidence,
            )
        }

        viewModelScope.launch {
            mealRepository.addLogs(logs)
                .onSuccess {
                    _state.update { it.copy(pending = null) }
                    load()
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(pending = pending.copy(isSaving = false), error = e.message)
                    }
                }
        }
    }

    fun deleteLog(id: String) {
        viewModelScope.launch {
            mealRepository.deleteLog(id)
                .onSuccess { load() }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }
}
