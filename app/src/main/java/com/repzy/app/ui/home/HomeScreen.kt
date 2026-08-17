package com.repzy.app.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repzy.app.R
import com.repzy.app.data.model.DailyBrief
import com.repzy.app.ui.components.MetricRow
import com.repzy.app.ui.components.UpgradeCard
import com.repzy.app.ui.components.MetricTile
import java.util.Locale

private val WATER_PRESETS_ML = listOf(200, 330, 500)

@Composable
fun HomeScreen(
    onUpgradeClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // Scaffold ve pencere boşlukları AppNavHost'ta — burada tekrarlanmaz.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.home_greeting, state.profile?.displayName ?: ""),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            StreakChip(days = state.streakDays)
        }

        state.error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (!state.isPremium) {
            UpgradeCard(onClick = onUpgradeClick)
        }

        CoachCard(
            brief = state.brief,
            isLoading = state.isBriefLoading,
            error = state.briefError,
            expanded = state.isCoachExpanded,
            onToggle = viewModel::toggleCoachCard,
            onRefresh = { viewModel.loadBrief(force = true) },
            onDismissError = viewModel::dismissBriefError,
        )

        WaterCard(
            waterMl = state.waterMl,
            targetMl = state.waterTargetMl,
            progress = state.waterProgress,
            goalReached = state.waterGoalReached,
            canUndo = state.waterMl > 0 && !state.isWaterUpdating,
            onAdd = viewModel::addWater,
            onUndo = viewModel::undoWater,
        )

        val target = state.target
        if (target != null) {
            Text(
                text = stringResource(R.string.home_targets_title),
                style = MaterialTheme.typography.titleMedium,
            )
            MetricRow {
                MetricTile(
                    label = stringResource(R.string.metric_calories),
                    value = target.calories.toString(),
                    hint = stringResource(R.string.unit_kcal_per_day),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                state.bmi?.let { bmi ->
                    MetricTile(
                        label = stringResource(R.string.metric_bmi),
                        value = String.format(Locale.getDefault(), "%.1f", bmi),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
            MetricRow {
                MetricTile(
                    label = stringResource(R.string.metric_protein),
                    value = "${target.proteinG}${stringResource(R.string.unit_gram)}",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                MetricTile(
                    label = stringResource(R.string.metric_carbs),
                    value = "${target.carbsG}${stringResource(R.string.unit_gram)}",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                MetricTile(
                    label = stringResource(R.string.metric_fat),
                    value = "${target.fatG}${stringResource(R.string.unit_gram)}",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.home_next_up),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.disclaimer_short),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StreakChip(days: Int) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = {
            Text(
                if (days > 0) {
                    stringResource(R.string.home_streak, days)
                } else {
                    stringResource(R.string.home_streak_none)
                },
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.LocalFireDepartment,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = if (days > 0) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            disabledLabelColor = MaterialTheme.colorScheme.onSurface,
            disabledLeadingIconContentColor = if (days > 0) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
    )
}

/**
 * Günün koç kartı. Brief sunucuda günde bir üretiliyor; yenileme butonu
 * ayrıca sınırlı (sunucu sayıyor), o yüzden hata mesajı kartın içinde gösteriliyor.
 */
@Composable
private fun CoachCard(
    brief: DailyBrief?,
    isLoading: Boolean,
    error: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRefresh: () -> Unit,
    onDismissError: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.coach_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                )
                // Brief varken yenileme sırasında hiç geri bildirim yoktu; butonun
                // yerinde dönen gösterge, basıldığı belli olsun.
                if (isLoading && brief != null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.size(12.dp))
                } else if (brief != null) {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.coach_refresh),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                if (brief != null) {
                    IconButton(onClick = onToggle) {
                        Icon(
                            imageVector = if (expanded) {
                                Icons.Default.ExpandLess
                            } else {
                                Icons.Default.ExpandMore
                            },
                            contentDescription = stringResource(
                                if (expanded) R.string.coach_collapse else R.string.coach_expand,
                            ),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }

            when {
                isLoading && brief == null -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.size(10.dp))
                        Text(
                            text = stringResource(R.string.coach_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                brief != null && !expanded -> {
                    // Kapali durum: tek satir baslik. Karta dokunmak da aciyor,
                    // kucuk chevron'u bulmak zorunda kalmasin.
                    Text(
                        text = brief.headline,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onToggle),
                    )
                }

                brief != null -> {
                    Text(
                        text = brief.headline,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = brief.focus,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )

                    brief.actions.forEach { action ->
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.CheckCircleOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(16.dp),
                            )
                            Spacer(Modifier.size(8.dp))
                            Column {
                                Text(
                                    text = action.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                if (action.why.isNotBlank()) {
                                    Text(
                                        text = action.why,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                        }
                    }

                    brief.progressNote?.takeIf { it.isNotBlank() }?.let { note ->
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }

                    if (brief.limitReached) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.coach_refresh_limit),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }

                    // Yenileme hatası: brief zaten ekranda olduğu için aşağıdaki
                    // error dalına hiç düşmüyordu, hata kullanıcıya görünmüyordu.
                    error?.let { message ->
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onDismissError) {
                                Text(stringResource(R.string.common_close))
                            }
                        }
                    }
                }

                error != null -> {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row {
                        TextButton(onClick = onRefresh) {
                            Text(stringResource(R.string.common_retry))
                        }
                        TextButton(onClick = onDismissError) {
                            Text(stringResource(R.string.common_cancel))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WaterCard(
    waterMl: Int,
    targetMl: Int,
    progress: Float,
    goalReached: Boolean,
    canUndo: Boolean,
    onAdd: (Int) -> Unit,
    onUndo: () -> Unit,
) {
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "waterProgress")

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.metric_water),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onUndo, enabled = canUndo) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = stringResource(R.string.home_undo),
                    )
                }
            }

            Text(
                text = stringResource(
                    R.string.home_water_amount,
                    formatLiters(waterMl),
                    formatLiters(targetMl),
                ),
                style = MaterialTheme.typography.headlineMedium,
            )

            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
            )

            if (goalReached) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.home_water_goal_reached),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WATER_PRESETS_ML.forEach { amount ->
                    OutlinedButton(
                        onClick = { onAdd(amount) },
                        // Varsayılan iç boşluk üç butonu yan yana sığdırmıyor, etiket sarıyordu.
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = stringResource(R.string.home_add_water, amount),
                            maxLines = 1,
                            softWrap = false,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

private fun formatLiters(ml: Int): String =
    String.format(Locale.getDefault(), "%.2f", ml / 1000.0)
