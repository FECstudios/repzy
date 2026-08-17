package com.repzy.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repzy.app.R
import com.repzy.app.data.model.EquipmentAccess
import com.repzy.app.data.model.Exercise
import com.repzy.app.data.model.ExperienceLevel
import com.repzy.app.ui.isTurkishUi

@Composable
fun ExerciseLibraryScreen(
    onExerciseClick: (String) -> Unit,
    titleRes: Int = R.string.library_title,
    viewModel: ExerciseLibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val turkish = isTurkishUi()
    val results = state.filtered(turkish)

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            placeholder = { Text(stringResource(R.string.library_search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        )

        Spacer(Modifier.height(10.dp))
        FilterRow(state = state, viewModel = viewModel)

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        state.error?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }

        if (results.isEmpty() && !state.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.library_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(results, key = { it.id }) { exercise ->
                ExerciseRow(
                    exercise = exercise,
                    turkish = turkish,
                    onClick = { onExerciseClick(exercise.id) },
                )
            }
        }
    }
}

/**
 * Filtreler iki satıra ayrıldı: üstte ortam + seviye (kısa, sabit sayıda), altta kas grupları.
 * Hepsi tek satırdayken kas filtreleri ekran dışında kalıyor, "Temizle" de kayıp gidiyordu.
 */
@Composable
private fun FilterRow(state: LibraryUiState, viewModel: ExerciseLibraryViewModel) {
    val hasFilter = state.muscle != null || state.setting != null || state.level != null
    val padding = PaddingValues(horizontal = 20.dp)

    LazyRow(
        contentPadding = padding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(EquipmentAccess.entries.filter { it != EquipmentAccess.BOTH }) { setting ->
            FilterChip(
                selected = state.setting == setting,
                onClick = { viewModel.toggleSetting(setting) },
                label = { Text(settingLabel(setting)) },
            )
        }
        items(ExperienceLevel.entries) { level ->
            FilterChip(
                selected = state.level == level,
                onClick = { viewModel.toggleLevel(level) },
                label = { Text(levelLabel(level)) },
            )
        }
        if (hasFilter) {
            item {
                FilterChip(
                    selected = false,
                    onClick = viewModel::clearFilters,
                    label = { Text(stringResource(R.string.library_clear_filters)) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
        }
    }

    Spacer(Modifier.height(6.dp))
    LazyRow(
        contentPadding = padding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.muscles) { muscle ->
            FilterChip(
                selected = state.muscle == muscle,
                onClick = { viewModel.toggleMuscle(muscle) },
                label = { Text(muscleLabel(muscle)) },
            )
        }
    }
}

@Composable
private fun ExerciseRow(exercise: Exercise, turkish: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = exercise.name(turkish),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${muscleLabel(exercise.primaryMuscle)} · " +
                        "${equipmentLabel(exercise.equipment)} · ${levelLabel(exercise.level)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
