package com.repzy.app.ui.settings

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repzy.app.R
import com.repzy.app.core.Legal
import com.repzy.app.data.model.ActivityLevel
import com.repzy.app.data.model.EquipmentAccess
import com.repzy.app.data.model.ExperienceLevel
import com.repzy.app.data.model.Goal
import com.repzy.app.data.model.Sex
import com.repzy.app.ui.components.MeasureField
import com.repzy.app.ui.components.WeightChart
import com.repzy.app.ui.isTurkishUi

@Composable
fun SettingsScreen(
    onSignOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val turkish = isTurkishUi()
    // Onay kelimesi çevrilebilir: İngilizce arayüzde DELETE, Türkçede SİL yazılır.
    val confirmationWord = stringResource(R.string.settings_delete_word)

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
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
        )

        state.error?.let { message ->
            FeedbackCard(message, isError = true, onDismiss = viewModel::dismissMessage)
        }
        state.savedMessage?.let { code ->
            FeedbackCard(
                message = stringResource(
                    when (code) {
                        SavedMessage.PROFILE -> R.string.settings_saved_profile
                        SavedMessage.MEASUREMENTS -> R.string.settings_saved_measurements
                        SavedMessage.TARGETS -> R.string.settings_saved_targets
                        else -> R.string.settings_recalc_missing
                    },
                ),
                isError = code == SavedMessage.RECALC_MISSING,
                onDismiss = viewModel::dismissMessage,
            )
        }

        // --- Profil ---
        SectionTitle(stringResource(R.string.settings_section_profile))

        MeasureField(
            value = state.name,
            onValueChange = viewModel::onName,
            label = stringResource(R.string.onb_name_label),
            suffix = "",
            keyboardType = KeyboardType.Text,
        )

        ChipRow(
            label = stringResource(R.string.onb_sex_title),
            options = Sex.entries,
            selected = state.sex,
            labelOf = { sexLabel(it) },
            onSelect = viewModel::onSex,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MeasureField(
                value = state.birthYear,
                onValueChange = viewModel::onBirthYear,
                label = stringResource(R.string.onb_birth_year_label),
                suffix = "",
                isError = state.birthYear.isNotEmpty() && state.birthYearValue == null,
                modifier = Modifier.weight(1f),
            )
            MeasureField(
                value = state.height,
                onValueChange = viewModel::onHeight,
                label = stringResource(R.string.onb_height_label),
                suffix = stringResource(R.string.unit_cm),
                isError = state.height.isNotEmpty() && state.heightValue == null,
                modifier = Modifier.weight(1f),
            )
        }

        ChipRow(
            label = stringResource(R.string.onb_goal_title),
            options = Goal.entries,
            selected = state.goal,
            labelOf = { goalLabel(it) },
            onSelect = viewModel::onGoal,
        )
        ChipRow(
            label = stringResource(R.string.onb_experience_title),
            options = ExperienceLevel.entries,
            selected = state.experience,
            labelOf = { experienceLabel(it) },
            onSelect = viewModel::onExperience,
        )
        ChipRow(
            label = stringResource(R.string.onb_equipment_title),
            options = EquipmentAccess.entries,
            selected = state.equipment,
            labelOf = { equipmentLabel(it) },
            onSelect = viewModel::onEquipment,
        )
        ChipRow(
            label = stringResource(R.string.onb_activity_title),
            options = ActivityLevel.entries,
            selected = state.activity,
            labelOf = { activityLabel(it) },
            onSelect = viewModel::onActivity,
        )

        Button(
            onClick = viewModel::saveProfile,
            enabled = state.canSaveProfile,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_save_profile))
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        // --- Ölçüler ---
        SectionTitle(stringResource(R.string.settings_section_measurements))
        Text(
            text = stringResource(R.string.settings_measurements_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MeasureField(
                value = state.weight,
                onValueChange = viewModel::onWeight,
                label = stringResource(R.string.onb_weight_label),
                suffix = stringResource(R.string.unit_kg),
                isError = state.weight.isNotEmpty() && state.weightValue == null,
                modifier = Modifier.weight(1f),
            )
            MeasureField(
                value = state.neck,
                onValueChange = viewModel::onNeck,
                label = stringResource(R.string.onb_neck_label),
                suffix = stringResource(R.string.unit_cm),
                isError = state.neck.isNotEmpty() && state.neckValue == null,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MeasureField(
                value = state.waist,
                onValueChange = viewModel::onWaist,
                label = stringResource(R.string.onb_waist_label),
                suffix = stringResource(R.string.unit_cm),
                isError = state.waist.isNotEmpty() && state.waistValue == null,
                modifier = Modifier.weight(1f),
            )
            MeasureField(
                value = state.hip,
                onValueChange = viewModel::onHip,
                label = stringResource(R.string.onb_hip_label),
                suffix = stringResource(R.string.unit_cm),
                isError = state.hip.isNotEmpty() && state.hipValue == null,
                imeAction = ImeAction.Done,
                modifier = Modifier.weight(1f),
            )
        }

        Button(
            onClick = viewModel::saveMeasurements,
            enabled = state.canSaveMeasurements,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_save_measurements))
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_weight_history),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(8.dp))
                WeightChart(metrics = state.history)
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        // --- Hedefler ---
        SectionTitle(stringResource(R.string.settings_section_targets))
        Text(
            text = stringResource(
                if (state.isTargetManual) {
                    R.string.settings_targets_manual
                } else {
                    R.string.settings_targets_auto
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedButton(
            onClick = viewModel::recalculate,
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_recalculate))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MeasureField(
                value = state.calories,
                onValueChange = viewModel::onCalories,
                label = stringResource(R.string.metric_calories),
                suffix = "kcal",
                isError = state.calories.isNotEmpty() && state.caloriesValue == null,
                modifier = Modifier.weight(1f),
            )
            MeasureField(
                value = state.waterMl,
                onValueChange = viewModel::onWater,
                label = stringResource(R.string.metric_water),
                suffix = "ml",
                isError = state.waterMl.isNotEmpty() && state.waterValue == null,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MeasureField(
                value = state.proteinG,
                onValueChange = viewModel::onProtein,
                label = stringResource(R.string.metric_protein),
                suffix = stringResource(R.string.unit_gram),
                isError = state.proteinG.isNotEmpty() && state.proteinValue == null,
                modifier = Modifier.weight(1f),
            )
            MeasureField(
                value = state.carbsG,
                onValueChange = viewModel::onCarbs,
                label = stringResource(R.string.metric_carbs),
                suffix = stringResource(R.string.unit_gram),
                isError = state.carbsG.isNotEmpty() && state.carbsValue == null,
                modifier = Modifier.weight(1f),
            )
            MeasureField(
                value = state.fatG,
                onValueChange = viewModel::onFat,
                label = stringResource(R.string.metric_fat),
                suffix = stringResource(R.string.unit_gram),
                isError = state.fatG.isNotEmpty() && state.fatValue == null,
                imeAction = ImeAction.Done,
                modifier = Modifier.weight(1f),
            )
        }

        Button(
            onClick = viewModel::saveTargets,
            enabled = state.canSaveTargets,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_save_targets))
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        // --- Yasal ---
        SectionTitle(stringResource(R.string.settings_section_legal))

        OutlinedButton(
            onClick = { openUrl(context, Legal.privacyUrl(turkish)) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_privacy_policy))
        }
        if (turkish) {
            OutlinedButton(
                onClick = { openUrl(context, Legal.kvkkNoticeUrl()) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_kvkk_notice))
            }
        }

        Text(
            text = stringResource(R.string.disclaimer_long),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onSignOut) {
            Text(stringResource(R.string.common_sign_out))
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        // --- Hesap silme (Play Store zorunluluğu) ---
        SectionTitle(stringResource(R.string.settings_section_danger))
        Text(
            text = stringResource(R.string.settings_delete_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = viewModel::openDeleteDialog,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_delete_account))
        }
        Spacer(Modifier.height(24.dp))
    }

    if (state.isDeleteDialogOpen) {
        AlertDialog(
            onDismissRequest = viewModel::closeDeleteDialog,
            title = { Text(stringResource(R.string.settings_delete_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.settings_delete_warning))
                    MeasureField(
                        value = state.deleteConfirmation,
                        onValueChange = viewModel::onDeleteConfirmation,
                        label = stringResource(R.string.settings_delete_hint, confirmationWord),
                        suffix = "",
                        imeAction = ImeAction.Done,
                        keyboardType = KeyboardType.Text,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAccount(confirmationWord, onDeleted = onSignOut)
                    },
                    enabled = !state.isDeleting &&
                        state.deleteConfirmation.equals(confirmationWord, ignoreCase = true),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text(stringResource(R.string.settings_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::closeDeleteDialog) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    // Yeniden hesaplama önizlemesi: kullanıcı yeni sayıyı görmeden hedefi değişmiyor.
    state.preview?.let { preview ->
        AlertDialog(
            onDismissRequest = viewModel::dismissPreview,
            title = { Text(stringResource(R.string.settings_recalc_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val gram = stringResource(R.string.unit_gram)
                    Text(
                        text = stringResource(R.string.settings_recalc_calories, preview.calories),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "P ${preview.macros.proteinG}$gram · " +
                            "K ${preview.macros.carbsG}$gram · " +
                            "Y ${preview.macros.fatG}$gram",
                    )
                    Text(stringResource(R.string.settings_recalc_water, preview.waterMl))
                    preview.bodyFatPct?.let {
                        Text(
                            stringResource(
                                R.string.settings_recalc_body_fat,
                                String.format(java.util.Locale.getDefault(), "%.1f", it),
                            ),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            R.string.metric_bmr_tdee,
                            preview.bmr,
                            preview.tdee,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(onClick = viewModel::applyPreview, enabled = !state.isSaving) {
                    Text(stringResource(R.string.settings_recalc_apply))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissPreview) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium)
}

/**
 * Yasal metni tarayıcıda açar. Play Console hem URL hem uygulama içi erişim istiyor;
 * metinler `docs/` klasöründen yayınlandığı için tek kaynak korunuyor.
 * Tarayıcı yoksa (nadir) çökmemesi için hata yutuluyor.
 */
private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }
}

/** Seçenek sayısı az olduğu için chip; OptionCard'lar ayarlar ekranını çok uzatıyordu. */
@Composable
private fun <T> ChipRow(
    label: String,
    options: List<T>,
    selected: T?,
    labelOf: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = selected == option,
                    onClick = { onSelect(option) },
                    label = { Text(labelOf(option)) },
                )
            }
        }
    }
}

@Composable
private fun FeedbackCard(message: String, isError: Boolean, onDismiss: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        }
    }
}

@Composable
private fun sexLabel(sex: Sex): String = stringResource(
    when (sex) {
        Sex.FEMALE -> R.string.onb_sex_female
        Sex.MALE -> R.string.onb_sex_male
    },
)

@Composable
private fun goalLabel(goal: Goal): String = stringResource(
    when (goal) {
        Goal.LOSE_FAT -> R.string.goal_lose_fat
        Goal.BUILD_MUSCLE -> R.string.goal_build_muscle
        Goal.ENDURANCE -> R.string.goal_endurance
        Goal.GENERAL_FITNESS -> R.string.goal_general
    },
)

@Composable
private fun experienceLabel(level: ExperienceLevel): String = stringResource(
    when (level) {
        ExperienceLevel.BEGINNER -> R.string.level_beginner
        ExperienceLevel.INTERMEDIATE -> R.string.level_intermediate
        ExperienceLevel.ADVANCED -> R.string.level_advanced
    },
)

@Composable
private fun equipmentLabel(access: EquipmentAccess): String = stringResource(
    when (access) {
        EquipmentAccess.GYM -> R.string.equipment_gym
        EquipmentAccess.HOME -> R.string.equipment_home
        EquipmentAccess.BOTH -> R.string.equipment_both
    },
)

@Composable
private fun activityLabel(level: ActivityLevel): String = stringResource(
    when (level) {
        ActivityLevel.SEDENTARY -> R.string.activity_sedentary
        ActivityLevel.LIGHT -> R.string.activity_light
        ActivityLevel.MODERATE -> R.string.activity_moderate
        ActivityLevel.ACTIVE -> R.string.activity_active
        ActivityLevel.VERY_ACTIVE -> R.string.activity_very_active
    },
)
