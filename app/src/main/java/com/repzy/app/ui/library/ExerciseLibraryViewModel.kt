package com.repzy.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repzy.app.data.model.EquipmentAccess
import com.repzy.app.data.model.Exercise
import com.repzy.app.data.model.ExperienceLevel
import com.repzy.app.data.repo.ExerciseRepository
import com.repzy.app.data.repo.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val isLoading: Boolean = true,
    val all: List<Exercise> = emptyList(),
    val query: String = "",
    val muscle: String? = null,
    val setting: EquipmentAccess? = null,
    val level: ExperienceLevel? = null,
    val error: String? = null,
) {
    /** Listede geçen kas grupları — filtre çipleri veriden türetilir, elle liste tutulmaz. */
    val muscles: List<String>
        get() = all.map { it.primaryMuscle }.distinct().sorted()

    fun filtered(turkish: Boolean): List<Exercise> = all.filter { exercise ->
        val matchesQuery = query.isBlank() ||
            exercise.name(turkish).contains(query, ignoreCase = true) ||
            exercise.nameEn.contains(query, ignoreCase = true) ||
            exercise.nameTr.contains(query, ignoreCase = true)

        val matchesMuscle = muscle == null || exercise.primaryMuscle == muscle

        // "Salon" filtresi hem gym hem both'u getirir — both her yerde yapılabilir demek.
        val matchesSetting = setting == null ||
            exercise.setting == setting ||
            exercise.setting == EquipmentAccess.BOTH

        val matchesLevel = level == null || exercise.level == level

        matchesQuery && matchesMuscle && matchesSetting && matchesLevel
    }
}

@HiltViewModel
class ExerciseLibraryViewModel @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            exerciseRepository.all()
                .onSuccess { list ->
                    _state.update { it.copy(isLoading = false, all = list) }
                    applyProfileDefaults()
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    /** Kullanıcı salonda çalışıyorsa listeyi baştan ona göre daralt — boş filtreyle başlatmaktan iyi. */
    private suspend fun applyProfileDefaults() {
        if (_state.value.setting != null || _state.value.level != null) return
        profileRepository.getProfile().getOrNull()?.let { profile ->
            _state.update {
                it.copy(
                    setting = profile.equipmentAccess?.takeIf { access ->
                        access != EquipmentAccess.BOTH
                    },
                    level = profile.experienceLevel,
                )
            }
        }
    }

    fun onQueryChange(value: String) = _state.update { it.copy(query = value) }

    fun toggleMuscle(value: String) =
        _state.update { it.copy(muscle = if (it.muscle == value) null else value) }

    fun toggleSetting(value: EquipmentAccess) =
        _state.update { it.copy(setting = if (it.setting == value) null else value) }

    fun toggleLevel(value: ExperienceLevel) =
        _state.update { it.copy(level = if (it.level == value) null else value) }

    fun clearFilters() =
        _state.update { it.copy(muscle = null, setting = null, level = null, query = "") }
}
