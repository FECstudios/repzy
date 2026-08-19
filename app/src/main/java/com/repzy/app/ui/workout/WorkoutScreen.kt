package com.repzy.app.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repzy.app.R
import com.repzy.app.core.DailyPlan
import com.repzy.app.core.PlanFocus
import com.repzy.app.ui.components.Badge
import com.repzy.app.ui.components.Decor
import com.repzy.app.data.model.WorkoutSet
import com.repzy.app.ui.components.MetricRow
import com.repzy.app.ui.components.MetricTile
import com.repzy.app.ui.isTurkishUi
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun WorkoutScreen(
    viewModel: WorkoutViewModel,
    onAddExerciseClick: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.workout_title),
            style = MaterialTheme.typography.headlineMedium,
        )

        state.error?.let { message ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = viewModel::dismissError) {
                    Icon(Icons.Default.Close, contentDescription = null)
                }
            }
        }

        if (state.hasActiveWorkout) {
            ActiveWorkout(
                state = state,
                viewModel = viewModel,
                onAddExerciseClick = onAddExerciseClick,
            )
        } else {
            IdleWorkout(
                state = state,
                onStart = viewModel::startWorkout,
                onStartPlan = viewModel::startPlannedWorkout,
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun IdleWorkout(
    state: WorkoutUiState,
    onStart: () -> Unit,
    onStartPlan: () -> Unit,
) {
    state.plan?.let { plan -> PlanCard(plan = plan, onStartPlan = onStartPlan) }

    Spacer(Modifier.height(4.dp))
    OutlinedButton(
        onClick = onStart,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text(stringResource(R.string.workout_start_empty))
    }

    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.workout_history),
        style = MaterialTheme.typography.titleMedium,
    )

    if (state.history.isEmpty()) {
        Text(
            text = stringResource(R.string.workout_history_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    state.history.forEach { workout ->
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    text = workout.startedAt?.let { formatDate(it) } ?: "—",
                    style = MaterialTheme.typography.titleSmall,
                )
                val duration = durationLabel(workout.startedAt, workout.finishedAt)
                if (duration != null) {
                    Text(
                        text = duration,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Bugunun plani. Kural tabanli uretiliyor (AI degil), o yuzden aninda hazir --
 * kullanici uygulamayi actiginda bekleme yok.
 */
@Composable
private fun PlanCard(plan: DailyPlan, onStartPlan: () -> Unit) {
    Card(
        shape = Decor.CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.EventAvailable,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.plan_today_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (!plan.isRestDay) {
                    Badge(text = stringResource(R.string.plan_minutes, plan.estimatedMinutes))
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(focusLabel(plan.focus)),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            if (plan.isRestDay) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.plan_rest_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            Spacer(Modifier.height(10.dp))
            val turkish = isTurkishUi()
            plan.exercises.forEach { planned ->
                Row(
                    modifier = Modifier.padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = planned.exercise.name(turkish),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (planned.isTimeBased) {
                            stringResource(
                                R.string.plan_sets_seconds,
                                planned.sets,
                                planned.repsLow,
                                planned.repsHigh,
                            )
                        } else {
                            stringResource(
                                R.string.plan_sets_reps,
                                planned.sets,
                                planned.repsLow,
                                planned.repsHigh,
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onStartPlan,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Text(
                    text = stringResource(R.string.plan_start),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun focusLabel(focus: PlanFocus): Int = when (focus) {
    PlanFocus.FULL_BODY -> R.string.plan_focus_full_body
    PlanFocus.UPPER -> R.string.plan_focus_upper
    PlanFocus.LOWER -> R.string.plan_focus_lower
    PlanFocus.CORE_CARDIO -> R.string.plan_focus_core
    PlanFocus.REST -> R.string.plan_focus_rest
}

@Composable
private fun ActiveWorkout(
    state: WorkoutUiState,
    viewModel: WorkoutViewModel,
    onAddExerciseClick: () -> Unit,
) {
    val turkish = isTurkishUi()

    MetricRow {
        MetricTile(
            label = stringResource(R.string.workout_elapsed),
            value = ElapsedTime(state.workout?.startedAt),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        MetricTile(
            label = stringResource(R.string.workout_sets),
            value = state.loggedSetCount.toString(),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        MetricTile(
            label = stringResource(R.string.workout_volume),
            value = "${formatVolume(state.totalVolumeKg)} ${stringResource(R.string.unit_kg)}",
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
    }

    if (state.entries.isEmpty()) {
        Text(
            text = stringResource(R.string.workout_no_exercises),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    state.entries.forEach { entry ->
        ExerciseEntryCard(entry = entry, turkish = turkish, viewModel = viewModel)
    }

    OutlinedButton(
        onClick = onAddExerciseClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
    ) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.workout_add_exercise))
    }

    if (state.loggedSetCount > 0) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.workout_effort),
            style = MaterialTheme.typography.titleSmall,
        )
        // 10 çip tek satıra sığmıyordu, son dördü ekran dışında kalıyordu.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            (1..10).forEach { value ->
                FilterChip(
                    selected = state.perceivedEffort == value,
                    onClick = { viewModel.setPerceivedEffort(value) },
                    label = { Text(value.toString()) },
                )
            }
        }
    } else {
        Text(
            text = stringResource(R.string.workout_discard_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Spacer(Modifier.height(8.dp))
    Button(
        onClick = viewModel::finishWorkout,
        enabled = !state.isFinishing,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        if (state.isFinishing) {
            // size, sadece height değil — tek başına yükseklik verilince
            // gösterge 40dp genişlikte kalıp elips gibi dönüyordu.
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(
                stringResource(
                    if (state.loggedSetCount > 0) {
                        R.string.workout_finish
                    } else {
                        R.string.workout_cancel
                    },
                ),
            )
        }
    }
}

@Composable
private fun ExerciseEntryCard(
    entry: WorkoutEntry,
    turkish: Boolean,
    viewModel: WorkoutViewModel,
) {
    val exerciseId = entry.exercise.id

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.exercise.name(turkish),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (entry.sets.isEmpty()) {
                    IconButton(onClick = { viewModel.removeExercise(exerciseId) }) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }
            }

            if (entry.lastTime.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.workout_last_time) + ": " +
                        entry.lastTime.map { describeSet(it) }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))
            entry.sets.forEach { set ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.workout_set_line, set.setIndex),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(56.dp),
                    )
                    Text(
                        text = describeSet(set),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { set.id?.let { viewModel.deleteSet(exerciseId, it) } },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (entry.exercise.isTimeBased) {
                    OutlinedTextField(
                        value = entry.durationInput,
                        onValueChange = { viewModel.onDurationChange(exerciseId, it) },
                        label = { Text(stringResource(R.string.workout_duration)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    OutlinedTextField(
                        value = entry.repsInput,
                        onValueChange = { viewModel.onRepsChange(exerciseId, it) },
                        label = { Text(stringResource(R.string.workout_reps)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = entry.weightInput,
                    onValueChange = { viewModel.onWeightChange(exerciseId, it) },
                    label = { Text(stringResource(R.string.workout_weight)) },
                    suffix = { Text(stringResource(R.string.unit_kg)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.weight(1f),
                )
                FilledIconButton(
                    onClick = { viewModel.saveSet(exerciseId) },
                    enabled = entry.canSave,
                ) {
                    Icon(Icons.Default.Check, contentDescription = stringResource(R.string.workout_save_set))
                }
            }
        }
    }
}

/** Seansın başlangıcından bu yana geçen süre, saniyede bir güncellenir. */
@Composable
private fun ElapsedTime(startedAtIso: String?): String {
    val startMillis = remember(startedAtIso) {
        startedAtIso?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
    } ?: return "—"

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startMillis) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    return formatDuration(((now - startMillis) / 1000).coerceAtLeast(0))
}

@Composable
private fun describeSet(set: WorkoutSet): String {
    val weight = set.weightKg?.takeIf { it > 0 }
    val core = when {
        set.durationSec != null -> stringResource(R.string.workout_seconds_short, set.durationSec)
        else -> stringResource(R.string.workout_reps_short, set.reps ?: 0)
    }
    return if (weight != null) {
        "$core × ${formatVolume(weight)} ${stringResource(R.string.unit_kg)}"
    } else {
        core
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}

private fun formatVolume(value: Double): String =
    if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(Locale.getDefault(), "%.1f", value)
    }

private fun formatDate(iso: String): String = runCatching {
    java.time.Instant.parse(iso)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("d MMMM, HH:mm", Locale.getDefault()))
}.getOrDefault(iso)

private fun durationLabel(startedAt: String?, finishedAt: String?): String? {
    if (startedAt == null || finishedAt == null) return null
    return runCatching {
        val start = java.time.Instant.parse(startedAt)
        val end = java.time.Instant.parse(finishedAt)
        formatDuration(java.time.Duration.between(start, end).seconds.coerceAtLeast(0))
    }.getOrNull()
}
