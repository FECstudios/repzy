package com.repzy.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repzy.app.core.PlanSummary
import com.repzy.app.core.planFor
import com.repzy.app.data.local.OnboardingDraft
import com.repzy.app.data.local.OnboardingDraftStore
import com.repzy.app.data.model.ActivityLevel
import com.repzy.app.data.model.EquipmentAccess
import com.repzy.app.data.model.ExperienceLevel
import com.repzy.app.data.model.Goal
import com.repzy.app.data.model.Sex
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinLocalDate
import java.time.Instant
import javax.inject.Inject

enum class OnboardingStep {
    CONSENT,
    NAME,
    SEX_AGE,
    BODY,
    GOAL,
    EXPERIENCE,
    EQUIPMENT,
    ACTIVITY,
    MEASUREMENTS,

    /** Plan kurulurken gösterilen ara ekran — kullanıcı butona basmaz, kendisi geçer. */
    CALCULATING,
    SUMMARY,
}

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.CONSENT,
    val draft: OnboardingDraft = OnboardingDraft(),
    val isSubmitting: Boolean = false,
    val error: String? = null,
) {
    // Ekran taslağın alanlarına doğrudan bakar.
    val healthConsent: Boolean get() = draft.healthConsent
    val name: String get() = draft.name
    val sex: Sex? get() = draft.sex
    val birthYear: String get() = draft.birthYear
    val heightCm: String get() = draft.heightCm
    val weightKg: String get() = draft.weightKg
    val goal: Goal? get() = draft.goal
    val experience: ExperienceLevel? get() = draft.experience
    val equipment: EquipmentAccess? get() = draft.equipment
    val activity: ActivityLevel? get() = draft.activity
    val neckCm: String get() = draft.neckCm
    val waistCm: String get() = draft.waistCm
    val hipCm: String get() = draft.hipCm

    val heightValue: Double? get() = draft.heightValue
    val weightValue: Double? get() = draft.weightValue
    val birthYearValue: Int? get() = draft.birthYearValue
    val neckValue: Double? get() = draft.neckValue
    val waistValue: Double? get() = draft.waistValue
    val hipValue: Double? get() = draft.hipValue

    val canContinue: Boolean
        get() = when (step) {
            OnboardingStep.CONSENT -> healthConsent
            OnboardingStep.NAME -> name.trim().length >= 2
            OnboardingStep.SEX_AGE -> sex != null && birthYearValue != null
            OnboardingStep.BODY -> heightValue != null && weightValue != null
            OnboardingStep.GOAL -> goal != null
            OnboardingStep.EXPERIENCE -> experience != null
            OnboardingStep.EQUIPMENT -> equipment != null
            OnboardingStep.ACTIVITY -> activity != null
            OnboardingStep.MEASUREMENTS -> true // opsiyonel adım
            OnboardingStep.CALCULATING -> false // buton gösterilmiyor
            OnboardingStep.SUMMARY -> !isSubmitting
        }

    val progress: Float
        get() = (step.ordinal + 1f) / OnboardingStep.entries.size
}

/**
 * Onboarding hesap açılmadan önce çalışır: cevaplar her değişiklikte cihaza yazılır.
 * Son adımda taslak "completed" işaretlenir; sunucuya yazma işini [com.repzy.app.ui.RootViewModel]
 * oturum açıldığı anda devralır.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val draftStore: OnboardingDraftStore,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    private val today: LocalDate get() = java.time.LocalDate.now().toKotlinLocalDate()

    init {
        viewModelScope.launch {
            draftStore.load()?.let { saved ->
                _state.update { it.copy(draft = saved, step = resumeStep(saved)) }
            }
        }
    }

    /** Uygulama kapanıp açılırsa kullanıcı en baştan başlamaz. */
    private fun resumeStep(draft: OnboardingDraft): OnboardingStep = when {
        !draft.healthConsent -> OnboardingStep.CONSENT
        draft.name.trim().length < 2 -> OnboardingStep.NAME
        draft.sex == null || draft.birthYearValue == null -> OnboardingStep.SEX_AGE
        draft.heightValue == null || draft.weightValue == null -> OnboardingStep.BODY
        draft.goal == null -> OnboardingStep.GOAL
        draft.experience == null -> OnboardingStep.EXPERIENCE
        draft.equipment == null -> OnboardingStep.EQUIPMENT
        draft.activity == null -> OnboardingStep.ACTIVITY
        else -> OnboardingStep.SUMMARY
    }

    private fun edit(transform: (OnboardingDraft) -> OnboardingDraft) {
        val updated = transform(_state.value.draft)
        _state.update { it.copy(draft = updated, error = null) }
        viewModelScope.launch { draftStore.save(updated) }
    }

    fun setHealthConsent(value: Boolean) = edit {
        it.copy(
            healthConsent = value,
            // Rıza anı, kullanıcının kutuyu işaretlediği an olarak saklanır.
            consentAtIso = if (value) Instant.now().toString() else null,
        )
    }

    fun setName(value: String) = edit { it.copy(name = value) }
    fun setSex(value: Sex) = edit { it.copy(sex = value) }
    fun setBirthYear(value: String) = edit { it.copy(birthYear = value.filter(Char::isDigit).take(4)) }
    fun setHeight(value: String) = edit { it.copy(heightCm = value.decimalInput()) }
    fun setWeight(value: String) = edit { it.copy(weightKg = value.decimalInput()) }
    fun setGoal(value: Goal) = edit { it.copy(goal = value) }
    fun setExperience(value: ExperienceLevel) = edit { it.copy(experience = value) }
    fun setEquipment(value: EquipmentAccess) = edit { it.copy(equipment = value) }
    fun setActivity(value: ActivityLevel) = edit { it.copy(activity = value) }
    fun setNeck(value: String) = edit { it.copy(neckCm = value.decimalInput()) }
    fun setWaist(value: String) = edit { it.copy(waistCm = value.decimalInput()) }
    fun setHip(value: String) = edit { it.copy(hipCm = value.decimalInput()) }

    fun next() {
        val current = _state.value
        if (!current.canContinue) return
        val steps = OnboardingStep.entries
        val nextIndex = (current.step.ordinal + 1).coerceAtMost(steps.lastIndex)
        _state.update { it.copy(step = steps[nextIndex], error = null) }
    }

    fun back(): Boolean {
        val current = _state.value
        if (current.step.ordinal == 0) return false
        _state.update { it.copy(step = OnboardingStep.entries[current.step.ordinal - 1], error = null) }
        return true
    }

    /** Hesaplama ekranı bitti — özete geç. */
    fun onCalculationShown() {
        if (_state.value.step == OnboardingStep.CALCULATING) {
            _state.update { it.copy(step = OnboardingStep.SUMMARY) }
        }
    }

    fun summary(): PlanSummary? = planFor(_state.value.draft, today)

    /**
     * Özet ekranındaki son buton. Taslağı "tamamlandı" işaretler; oturum yoksa
     * kullanıcı hesap oluşturma ekranına gider, varsa senkron hemen başlar.
     */
    fun finish() {
        if (summary() == null) {
            _state.update { it.copy(error = "Eksik bilgi var, adımlara geri dönüp tamamla.") }
            return
        }
        _state.update { it.copy(isSubmitting = true) }
        val completed = _state.value.draft.copy(completed = true)
        viewModelScope.launch { draftStore.save(completed) }
    }
}

/** Sadece rakam ve tek ondalık ayırıcı bırakır, virgülü noktaya çevirir. */
private fun String.decimalInput(): String {
    val normalized = replace(',', '.').filter { it.isDigit() || it == '.' }
    val firstDot = normalized.indexOf('.')
    if (firstDot == -1) return normalized.take(6)
    val head = normalized.substring(0, firstDot + 1)
    val tail = normalized.substring(firstDot + 1).filter(Char::isDigit).take(1)
    return (head + tail).take(6)
}
