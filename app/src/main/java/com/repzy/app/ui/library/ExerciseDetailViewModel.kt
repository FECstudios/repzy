package com.repzy.app.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repzy.app.data.model.Exercise
import com.repzy.app.data.repo.ExerciseRepository
import com.repzy.app.ui.ExerciseDetailRoute
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExerciseDetailUiState(
    val isLoading: Boolean = true,
    val exercise: Exercise? = null,
    val alternatives: List<Exercise> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val exerciseId: String = savedStateHandle.toRoute<ExerciseDetailRoute>().id

    private val _state = MutableStateFlow(ExerciseDetailUiState())
    val state: StateFlow<ExerciseDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            exerciseRepository.byId(exerciseId)
                .onSuccess { exercise ->
                    val alternatives = exerciseRepository.alternativesOf(exerciseId)
                        .getOrDefault(emptyList())
                    _state.value = ExerciseDetailUiState(
                        isLoading = false,
                        exercise = exercise,
                        alternatives = alternatives,
                    )
                }
                .onFailure { e ->
                    _state.value = ExerciseDetailUiState(isLoading = false, error = e.message)
                }
        }
    }
}
