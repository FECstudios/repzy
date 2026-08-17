package com.repzy.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repzy.app.core.BodyMath
import com.repzy.app.core.PlanSummary
import com.repzy.app.core.planFrom
import com.repzy.app.data.model.ActivityLevel
import com.repzy.app.data.model.BodyFatSource
import com.repzy.app.data.model.BodyMetric
import com.repzy.app.data.model.EquipmentAccess
import com.repzy.app.data.model.ExperienceLevel
import com.repzy.app.data.model.Goal
import com.repzy.app.data.model.NutritionTarget
import com.repzy.app.data.model.Profile
import com.repzy.app.data.model.Sex
import com.repzy.app.data.local.ReminderPrefs
import com.repzy.app.data.local.ReminderSettings
import com.repzy.app.data.repo.ProfileRepository
import com.repzy.app.notifications.Reminders
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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

/**
 * Ayarlar ekranının durumu. Sayısal alanlar metin olarak tutuluyor: kullanıcı
 * yazarken "7" geçici olarak geçersiz bir kilo, her tuşta parse edip reddetmek yerine
 * kaydetme anında doğruluyoruz (onboarding'deki yaklaşımın aynısı).
 */
data class SettingsUiState(
    val isLoading: Boolean = true,
    val profile: Profile? = null,
    val target: NutritionTarget? = null,
    val metric: BodyMetric? = null,
    val history: List<BodyMetric> = emptyList(),

    val name: String = "",
    val sex: Sex? = null,
    val birthYear: String = "",
    val height: String = "",
    val goal: Goal? = null,
    val experience: ExperienceLevel? = null,
    val equipment: EquipmentAccess? = null,
    val activity: ActivityLevel? = null,

    val weight: String = "",
    val neck: String = "",
    val waist: String = "",
    val hip: String = "",

    // Elle hedef girişi
    val calories: String = "",
    val proteinG: String = "",
    val carbsG: String = "",
    val fatG: String = "",
    val waterMl: String = "",

    val isSaving: Boolean = false,
    val savedMessage: Int? = null,
    val error: String? = null,
    val reminders: ReminderSettings = ReminderSettings(),
    val notificationsBlocked: Boolean = false,
    val isDeleteDialogOpen: Boolean = false,
    val deleteConfirmation: String = "",
    val isDeleting: Boolean = false,
    /** Yeniden hesaplama sonucu, kaydetmeden önce gösterilir. */
    val preview: PlanSummary? = null,
) {
    val weightValue: Double? get() = weight.toDoubleOrNull()?.takeIf { it in 25.0..350.0 }
    val heightValue: Double? get() = height.toDoubleOrNull()?.takeIf { it in 100.0..250.0 }
    val birthYearValue: Int? get() = birthYear.toIntOrNull()?.takeIf { it in 1920..2020 }
    val neckValue: Double? get() = neck.toDoubleOrNull()?.takeIf { it in 20.0..80.0 }
    val waistValue: Double? get() = waist.toDoubleOrNull()?.takeIf { it in 40.0..200.0 }
    val hipValue: Double? get() = hip.toDoubleOrNull()?.takeIf { it in 50.0..200.0 }

    val caloriesValue: Int? get() = calories.toIntOrNull()?.takeIf { it in 800..6000 }
    val proteinValue: Int? get() = proteinG.toIntOrNull()?.takeIf { it in 0..500 }
    val carbsValue: Int? get() = carbsG.toIntOrNull()?.takeIf { it in 0..1000 }
    val fatValue: Int? get() = fatG.toIntOrNull()?.takeIf { it in 0..300 }
    val waterValue: Int? get() = waterMl.toIntOrNull()?.takeIf { it in 500..8000 }

    val canSaveProfile: Boolean
        get() = !isSaving && name.isNotBlank() && sex != null && goal != null &&
            experience != null && equipment != null && activity != null &&
            birthYearValue != null && heightValue != null

    val canSaveMeasurements: Boolean get() = !isSaving && weightValue != null

    /** Hedeflerin hepsi geçerli olmalı: yarısı girilmiş satır DB kısıtına takılır. */
    val canSaveTargets: Boolean
        get() = !isSaving && caloriesValue != null && proteinValue != null &&
            carbsValue != null && fatValue != null && waterValue != null

    val isTargetManual: Boolean get() = target?.source == "user"
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val profileRepository: ProfileRepository,
    private val reminderPrefs: ReminderPrefs,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val today: LocalDate get() = java.time.LocalDate.now().toKotlinLocalDate()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            val (profile, target, history) = coroutineScope {
                val p = async { profileRepository.getProfile(forceRefresh = true) }
                val t = async { profileRepository.activeNutritionTarget() }
                // Grafik geçmişi zaten en son ölçümü içeriyor — ayrıca sorgulamaya gerek yok.
                val h = async { profileRepository.bodyMetricHistory() }
                Triple(p.await(), t.await(), h.await())
            }

            val prof = profile.getOrNull()
            val tgt = target.getOrNull()
            val metrics = history.getOrDefault(emptyList())
            val met = metrics.maxByOrNull { it.measuredOn }

            _state.value = SettingsUiState(
                isLoading = false,
                profile = prof,
                target = tgt,
                metric = met,
                history = metrics,
                name = prof?.displayName.orEmpty(),
                sex = prof?.sex,
                birthYear = prof?.birthYear?.toString().orEmpty(),
                height = prof?.heightCm?.trimZero().orEmpty(),
                goal = prof?.goal,
                experience = prof?.experienceLevel,
                equipment = prof?.equipmentAccess,
                activity = prof?.activityLevel,
                weight = met?.weightKg?.trimZero().orEmpty(),
                neck = met?.neckCm?.trimZero().orEmpty(),
                waist = met?.waistCm?.trimZero().orEmpty(),
                hip = met?.hipCm?.trimZero().orEmpty(),
                calories = tgt?.calories?.toString().orEmpty(),
                proteinG = tgt?.proteinG?.toString().orEmpty(),
                carbsG = tgt?.carbsG?.toString().orEmpty(),
                fatG = tgt?.fatG?.toString().orEmpty(),
                waterMl = tgt?.waterMl?.toString().orEmpty(),
                error = profile.exceptionOrNull()?.message,
                reminders = reminderPrefs.load(),
                notificationsBlocked = !Reminders.hasPermission(appContext),
            )
        }
    }

    /**
     * Hatırlatıcı anahtarı. Tercih DataStore'a yazılıyor, planlama WorkManager'a
     * devrediliyor — uygulama kapalıyken de çalışması gerekiyor.
     */
    fun setWaterReminder(enabled: Boolean) {
        viewModelScope.launch {
            reminderPrefs.setWater(enabled)
            Reminders.setWaterReminders(appContext, enabled)
            _state.update { it.copy(reminders = it.reminders.copy(water = enabled)) }
        }
    }

    fun setWorkoutReminder(enabled: Boolean) {
        viewModelScope.launch {
            reminderPrefs.setWorkout(enabled)
            Reminders.setWorkoutReminder(appContext, enabled, _state.value.reminders.workoutHour)
            _state.update { it.copy(reminders = it.reminders.copy(workout = enabled)) }
        }
    }

    fun setWorkoutHour(hour: Int) {
        viewModelScope.launch {
            reminderPrefs.setWorkoutHour(hour)
            val settings = reminderPrefs.load()
            if (settings.workout) {
                Reminders.setWorkoutReminder(appContext, true, settings.workoutHour)
            }
            _state.update { it.copy(reminders = settings) }
        }
    }

    /** İzin diyaloğu kapandıktan sonra durumu tazele. */
    fun refreshNotificationPermission() {
        _state.update { it.copy(notificationsBlocked = !Reminders.hasPermission(appContext)) }
    }

    fun onName(value: String) = _state.update { it.copy(name = value.take(40)) }
    fun onSex(value: Sex) = _state.update { it.copy(sex = value) }
    fun onBirthYear(value: String) = _state.update { it.copy(birthYear = value.digits(4)) }
    fun onHeight(value: String) = _state.update { it.copy(height = value.decimal()) }
    fun onGoal(value: Goal) = _state.update { it.copy(goal = value) }
    fun onExperience(value: ExperienceLevel) = _state.update { it.copy(experience = value) }
    fun onEquipment(value: EquipmentAccess) = _state.update { it.copy(equipment = value) }
    fun onActivity(value: ActivityLevel) = _state.update { it.copy(activity = value) }

    fun onWeight(value: String) = _state.update { it.copy(weight = value.decimal()) }
    fun onNeck(value: String) = _state.update { it.copy(neck = value.decimal()) }
    fun onWaist(value: String) = _state.update { it.copy(waist = value.decimal()) }
    fun onHip(value: String) = _state.update { it.copy(hip = value.decimal()) }

    fun onCalories(value: String) = _state.update { it.copy(calories = value.digits(4)) }
    fun onProtein(value: String) = _state.update { it.copy(proteinG = value.digits(3)) }
    fun onCarbs(value: String) = _state.update { it.copy(carbsG = value.digits(4)) }
    fun onFat(value: String) = _state.update { it.copy(fatG = value.digits(3)) }
    fun onWater(value: String) = _state.update { it.copy(waterMl = value.digits(4)) }

    fun dismissMessage() = _state.update { it.copy(savedMessage = null, error = null) }
    fun dismissPreview() = _state.update { it.copy(preview = null) }

    fun openDeleteDialog() =
        _state.update { it.copy(isDeleteDialogOpen = true, deleteConfirmation = "") }

    fun closeDeleteDialog() =
        _state.update { it.copy(isDeleteDialogOpen = false, deleteConfirmation = "") }

    fun onDeleteConfirmation(value: String) =
        _state.update { it.copy(deleteConfirmation = value.take(10)) }

    /**
     * Hesabı ve tüm verileri siler. Geri alınamaz olduğu için onay kelimesi yazılmadan
     * çalışmaz; silme başarılıysa oturum kapatılır ve kullanıcı en başa döner.
     */
    fun deleteAccount(confirmationWord: String, onDeleted: () -> Unit) {
        val s = _state.value
        if (s.isDeleting) return
        if (!s.deleteConfirmation.equals(confirmationWord, ignoreCase = true)) return

        _state.update { it.copy(isDeleting = true, error = null) }

        viewModelScope.launch {
            profileRepository.deleteAccount()
                .onSuccess {
                    _state.update { it.copy(isDeleting = false, isDeleteDialogOpen = false) }
                    onDeleted()
                }
                .onFailure { e ->
                    _state.update { it.copy(isDeleting = false, error = e.message) }
                }
        }
    }

    fun saveProfile() {
        val s = _state.value
        if (!s.canSaveProfile) return
        _state.update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            profileRepository.updateProfile(
                displayName = s.name.trim(),
                sex = s.sex,
                birthYear = s.birthYearValue,
                heightCm = s.heightValue,
                goal = s.goal,
                experienceLevel = s.experience,
                equipmentAccess = s.equipment,
                activityLevel = s.activity,
            )
                .onSuccess {
                    _state.update {
                        it.copy(isSaving = false, savedMessage = SavedMessage.PROFILE)
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isSaving = false, error = e.message) }
                }
        }
    }

    /** Ölçüler bugünün tarihine yazılır; aynı gün tekrar girilirse üzerine biner. */
    fun saveMeasurements() {
        val s = _state.value
        val weight = s.weightValue ?: return
        if (!s.canSaveMeasurements) return
        _state.update { it.copy(isSaving = true, error = null) }

        val bodyFat = s.sex?.let { sex ->
            s.heightValue?.let { height ->
                BodyMath.navyBodyFatPct(
                    sex = sex,
                    heightCm = height,
                    neckCm = s.neckValue ?: 0.0,
                    waistCm = s.waistValue ?: 0.0,
                    hipCm = s.hipValue,
                )
            }
        }

        viewModelScope.launch {
            profileRepository.saveBodyMetric(
                BodyMetric(
                    measuredOn = today,
                    weightKg = weight,
                    neckCm = s.neckValue,
                    waistCm = s.waistValue,
                    hipCm = s.hipValue,
                    bodyFatPct = bodyFat,
                    bodyFatSource = bodyFat?.let { BodyFatSource.NAVY },
                ),
            )
                .onSuccess {
                    _state.update {
                        it.copy(isSaving = false, savedMessage = SavedMessage.MEASUREMENTS)
                    }
                    load()
                }
                .onFailure { e ->
                    _state.update { it.copy(isSaving = false, error = e.message) }
                }
        }
    }

    /**
     * Planı güncel profil + kilodan yeniden hesaplar ve önizleme olarak gösterir.
     * Kaydetmek ayrı bir adım: kullanıcı yeni sayıyı görmeden hedefini değiştirmesin.
     */
    fun recalculate() {
        val s = _state.value
        val summary = planFrom(
            sex = s.sex,
            goal = s.goal,
            activity = s.activity,
            heightCm = s.heightValue,
            weightKg = s.weightValue,
            birthYear = s.birthYearValue,
            neckCm = s.neckValue,
            waistCm = s.waistValue,
            hipCm = s.hipValue,
            today = today,
        )

        if (summary == null) {
            _state.update { it.copy(error = null, savedMessage = SavedMessage.RECALC_MISSING) }
            return
        }
        _state.update { it.copy(preview = summary) }
    }

    /** Önizlenen hesabı hedef olarak yazar — kaynak 'rule'. */
    fun applyPreview() {
        val s = _state.value
        val preview = s.preview ?: return
        _state.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            profileRepository.saveNutritionTarget(
                NutritionTarget(
                    effectiveFrom = today,
                    calories = preview.calories,
                    proteinG = preview.macros.proteinG,
                    carbsG = preview.macros.carbsG,
                    fatG = preview.macros.fatG,
                    waterMl = preview.waterMl,
                    source = "rule",
                ),
            )
                .onSuccess {
                    _state.update {
                        it.copy(isSaving = false, preview = null, savedMessage = SavedMessage.TARGETS)
                    }
                    load()
                }
                .onFailure { e ->
                    _state.update { it.copy(isSaving = false, error = e.message) }
                }
        }
    }

    /** Kullanıcının elle girdiği hedefler — kaynak 'user', otomatik hesap ezmez. */
    fun saveTargets() {
        val s = _state.value
        if (!s.canSaveTargets) return
        _state.update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            profileRepository.saveNutritionTarget(
                NutritionTarget(
                    effectiveFrom = today,
                    calories = s.caloriesValue!!,
                    proteinG = s.proteinValue!!,
                    carbsG = s.carbsValue!!,
                    fatG = s.fatValue!!,
                    waterMl = s.waterValue!!,
                    source = "user",
                ),
            )
                .onSuccess {
                    _state.update {
                        it.copy(isSaving = false, savedMessage = SavedMessage.TARGETS)
                    }
                    load()
                }
                .onFailure { e ->
                    _state.update { it.copy(isSaving = false, error = e.message) }
                }
        }
    }
}

/** Kaydetme geri bildirimleri — string kaynağı ekranda çözülüyor. */
object SavedMessage {
    const val PROFILE = 1
    const val MEASUREMENTS = 2
    const val TARGETS = 3
    const val RECALC_MISSING = 4
}

private fun Double.trimZero(): String =
    if (this % 1.0 == 0.0) toInt().toString() else toString()

private fun String.digits(max: Int): String = filter(Char::isDigit).take(max)

/** Rakam ve tek ondalık ayırıcı; virgül noktaya çevrilir. */
private fun String.decimal(): String {
    val normalized = replace(',', '.').filter { it.isDigit() || it == '.' }
    val dot = normalized.indexOf('.')
    if (dot == -1) return normalized.take(5)
    return (normalized.substring(0, dot + 1) +
        normalized.substring(dot + 1).filter(Char::isDigit).take(1)).take(6)
}
