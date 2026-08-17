package com.repzy.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repzy.app.R
import com.repzy.app.data.model.Exercise
import com.repzy.app.ui.isTurkishUi

@Composable
fun ExerciseDetailScreen(
    onBack: () -> Unit,
    onExerciseClick: (String) -> Unit,
    viewModel: ExerciseDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val turkish = isTurkishUi()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
        }

        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Column
        }

        val exercise = state.exercise
        if (exercise == null) {
            Text(
                text = state.error ?: stringResource(R.string.library_not_found),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(24.dp),
            )
            return@Column
        }

        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = exercise.name(turkish),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(10.dp))

            // Üç etiket her zaman tek satıra sığmıyor (uzun kas adı + "Başlangıç") — sarsın.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(muscleLabel(exercise.primaryMuscle)) },
                )
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(equipmentLabel(exercise.equipment)) },
                )
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(levelLabel(exercise.level)) },
                )
            }

            if (exercise.secondaryMuscles.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    // joinToString'in transform lambda'sı inline değil, @Composable çağrılamaz.
                    text = stringResource(R.string.library_secondary_muscles) + ": " +
                        exercise.secondaryMuscles.map { muscleLabel(it) }.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle(stringResource(R.string.library_how_to))
            exercise.instructions(turkish).forEachIndexed { index, line ->
                NumberedLine(number = index + 1, text = line)
            }

            val mistakes = exercise.commonMistakes(turkish)
            if (mistakes.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                SectionTitle(stringResource(R.string.library_common_mistakes))
                mistakes.forEach { mistake ->
                    Row(
                        modifier = Modifier.padding(vertical = 5.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(text = mistake, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (state.alternatives.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                SectionTitle(stringResource(R.string.library_alternatives))
                Text(
                    text = stringResource(R.string.library_alternatives_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                state.alternatives.forEach { alternative ->
                    AlternativeRow(
                        exercise = alternative,
                        turkish = turkish,
                        onClick = { onExerciseClick(alternative.id) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.disclaimer_short),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun NumberedLine(number: Int, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(22.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AlternativeRow(exercise: Exercise, turkish: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = exercise.name(turkish),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "${settingLabel(exercise.setting)} · ${equipmentLabel(exercise.equipment)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
