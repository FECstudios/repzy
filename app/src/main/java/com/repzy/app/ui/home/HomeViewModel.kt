package com.repzy.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repzy.app.core.BodyMath
import com.repzy.app.core.Perf
import com.repzy.app.data.model.BodyMetric
import com.repzy.app.data.model.DailyBrief
import com.repzy.app.data.model.NutritionTarget
import com.repzy.app.data.model.Profile
import com.repzy.app.data.repo.CoachRepository
import com.repzy.app.data.repo.DailyLogRepository
import com.repzy.app.data.repo.ProfileRepository
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
import java.util.Locale
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val profile: Profile? = null,
    val target: NutritionTarget? = null,
    val latestMetric: BodyMetric? = null,
    val waterMl: Int = 0,
    val streakDays: Int = 0,
    val isWaterUpdating: Boolean = false,
    val brief: DailyBrief? = null,
    val isBriefLoading: Boolean = false,
    val briefError: String? = null,
    val error: String? = null,
) {
    val bmi: Double?
        get() {
            val weight = latestMetric?.weightKg ?: return null
            val height = profile?.heightCm ?: return null
            return BodyMath.bmi(weight, height)
        }

    val waterTargetMl: Int get() = target?.waterMl ?: 0

    val waterProgress: Float
        get() = if (waterTargetMl <= 0) 0f else (waterMl.toFloat() / waterTargetMl).coerceIn(0f, 1f)

    val waterGoalReached: Boolean get() = waterTargetMl > 0 && waterMl >= waterTargetMl
}

/** Paralel yüklemenin sonucu — destructuring için data class. */
private data class LoadResults(
    val profile: Result<Profile?>,
    val target: Result<NutritionTarget?>,
    val metric: Result<BodyMetric?>,
    val water: Result<Int>,
    val streak: Result<Int>,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val dailyLogRepository: DailyLogRepository,
    private val coachRepository: CoachRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val today: LocalDate get() = java.time.LocalDate.now().toKotlinLocalDate()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            // Beşi birbirinden bağımsız — sırayla beklenirse açılış gecikmesi beş katına çıkıyor.
            val (profile, target, metric, water, streak) = Perf.time("home.load") { coroutineScope {
                val profileJob = async { profileRepository.getProfile() }
                val targetJob = async { profileRepository.activeNutritionTarget() }
                val metricJob = async { profileRepository.latestBodyMetric() }
                val waterJob = async { dailyLogRepository.waterTotalMl(today) }
                val streakJob = async { dailyLogRepository.currentStreak() }
                LoadResults(
                    profileJob.await(),
                    targetJob.await(),
                    metricJob.await(),
                    waterJob.await(),
                    streakJob.await(),
                )
            } }

            val failure = listOf(profile, target, metric, water, streak)
                .firstNotNullOfOrNull { it.exceptionOrNull() }

            _state.value = HomeUiState(
                isLoading = false,
                profile = profile.getOrNull(),
                target = target.getOrNull(),
                latestMetric = metric.getOrNull(),
                waterMl = water.getOrDefault(0),
                streakDays = streak.getOrDefault(0),
                error = failure?.message,
            )

            loadBrief(force = false)
        }
    }

    /**
     * Koç brief'i ayrı yükleniyor: AI çağrısı olduğu için en yavaş parça ve
     * ekranın geri kalanının onu beklemesi anlamsız. Aynı gün ikinci açılışta
     * sunucu kayıtlı brief'i döndürüyor, AI'ya gitmiyor.
     */
    fun loadBrief(force: Boolean) {
        if (_state.value.isBriefLoading) return
        _state.update { it.copy(isBriefLoading = true, briefError = null) }

        viewModelScope.launch {
            coachRepository.dailyBrief(force = force, locale = Locale.getDefault().language)
                .onSuccess { brief ->
                    _state.update { it.copy(brief = brief, isBriefLoading = false) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isBriefLoading = false, briefError = e.message) }
                }
        }
    }

    fun dismissBriefError() = _state.update { it.copy(briefError = null) }

    fun addWater(amountMl: Int) {
        if (_state.value.isWaterUpdating) return
        // İyimser güncelleme: buton anında tepki verir, hata olursa geri alınır.
        val previous = _state.value.waterMl
        _state.update { it.copy(waterMl = previous + amountMl, isWaterUpdating = true) }

        viewModelScope.launch {
            dailyLogRepository.addWater(amountMl, today)
                .onSuccess { refreshWaterAndStreak() }
                .onFailure { e ->
                    _state.update {
                        it.copy(waterMl = previous, isWaterUpdating = false, error = e.message)
                    }
                }
        }
    }

    fun undoWater() {
        if (_state.value.isWaterUpdating || _state.value.waterMl <= 0) return
        _state.update { it.copy(isWaterUpdating = true) }

        viewModelScope.launch {
            dailyLogRepository.removeLastWater(today)
                .onSuccess { refreshWaterAndStreak() }
                .onFailure { e ->
                    _state.update { it.copy(isWaterUpdating = false, error = e.message) }
                }
        }
    }

    /** Günün ilk kaydı streak'i de değiştirebilir, ikisini birlikte tazele. */
    private suspend fun refreshWaterAndStreak() {
        val (water, streak) = coroutineScope {
            val waterJob = async { dailyLogRepository.waterTotalMl(today).getOrNull() }
            val streakJob = async { dailyLogRepository.currentStreak().getOrNull() }
            waterJob.await() to streakJob.await()
        }
        _state.update {
            it.copy(
                waterMl = water ?: it.waterMl,
                streakDays = streak ?: it.streakDays,
                isWaterUpdating = false,
                error = null,
            )
        }
    }
}
