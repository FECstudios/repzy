package com.repzy.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import android.content.Intent
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.net.toUri
import com.repzy.app.R
import com.repzy.app.core.BodyMath
import com.repzy.app.data.model.ActivityLevel
import com.repzy.app.data.model.EquipmentAccess
import com.repzy.app.data.model.ExperienceLevel
import com.repzy.app.data.model.Goal
import com.repzy.app.data.model.Sex
import com.repzy.app.ui.components.MeasureField
import com.repzy.app.ui.components.MetricTile
import com.repzy.app.core.Legal
import com.repzy.app.ui.components.OptionCard
import com.repzy.app.ui.isTurkishUi
import kotlinx.coroutines.delay

@Composable
fun OnboardingScreen(
    onSignInClick: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BackHandler(enabled = state.step != OnboardingStep.CONSENT) { viewModel.back() }

    Scaffold(
        topBar = {
            Column {
                Spacer(Modifier.height(36.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.back() },
                        enabled = state.step != OnboardingStep.CONSENT,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 24.dp),
                    )
                }
            }
        },
        bottomBar = {
            // Hesaplama ekranında buton yok — kullanıcı bekler, ekran kendisi geçer.
            if (state.step != OnboardingStep.CALCULATING) {
                OnboardingBottomBar(
                    state = state,
                    onClick = {
                        if (state.step == OnboardingStep.SUMMARY) {
                            viewModel.finish()
                        } else {
                            viewModel.next()
                        }
                    },
                )
            }
        },
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (state.step) {
                OnboardingStep.CONSENT -> ConsentStep(state, viewModel, onSignInClick)
                OnboardingStep.NAME -> NameStep(state, viewModel)
                OnboardingStep.SEX_AGE -> SexAgeStep(state, viewModel)
                OnboardingStep.BODY -> BodyStep(state, viewModel)
                OnboardingStep.GOAL -> GoalStep(state, viewModel)
                OnboardingStep.EXPERIENCE -> ExperienceStep(state, viewModel)
                OnboardingStep.EQUIPMENT -> EquipmentStep(state, viewModel)
                OnboardingStep.ACTIVITY -> ActivityStep(state, viewModel)
                OnboardingStep.MEASUREMENTS -> MeasurementsStep(state, viewModel)
                OnboardingStep.CALCULATING -> CalculatingStep(onDone = viewModel::onCalculationShown)
                OnboardingStep.SUMMARY -> SummaryStep(viewModel)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun OnboardingBottomBar(state: OnboardingState, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        state.error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(8.dp))
        }
        Button(
            onClick = onClick,
            enabled = state.canContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(
                    stringResource(
                        if (state.step == OnboardingStep.SUMMARY) {
                            R.string.onb_save_plan
                        } else {
                            R.string.common_continue
                        },
                    ),
                )
            }
        }
    }
}

/**
 * Plan kurulurken gösterilen ara ekran. Hesap zaten milisaniyeler içinde bitiyor;
 * buradaki bekleme kullanıcının ne hesaplandığını okuyabilmesi için — satırlar
 * uydurma değil, gerçekten yapılan adımları sırayla anlatıyor.
 */
@Composable
private fun CalculatingStep(onDone: () -> Unit) {
    val stages = listOf(
        R.string.calc_stage_bmr,
        R.string.calc_stage_tdee,
        R.string.calc_stage_macros,
        R.string.calc_stage_plan,
    )
    var current by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        stages.indices.forEach { index ->
            current = index
            delay(STAGE_DURATION_MS)
        }
        current = stages.size
        delay(350)
        onDone()
    }

    val progress by animateFloatAsState(
        targetValue = current.toFloat() / stages.size,
        animationSpec = tween(durationMillis = STAGE_DURATION_MS.toInt()),
        label = "calcProgress",
    )

    Spacer(Modifier.height(48.dp))
    Text(
        text = stringResource(R.string.calc_title),
        style = MaterialTheme.typography.headlineMedium,
    )
    Spacer(Modifier.height(24.dp))
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(28.dp))

    stages.forEachIndexed { index, label ->
        val done = index < current
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    done -> Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )

                    index == current -> CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.bodyLarge,
                color = if (index <= current) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
            )
        }
    }
}

private const val STAGE_DURATION_MS = 700L

@Composable
private fun StepHeader(title: String, subtitle: String? = null) {
    Spacer(Modifier.height(8.dp))
    Text(text = title, style = MaterialTheme.typography.headlineMedium)
    if (subtitle != null) {
        Spacer(Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun ConsentStep(
    state: OnboardingState,
    vm: OnboardingViewModel,
    onSignInClick: () -> Unit,
) {
    StepHeader(
        title = stringResource(R.string.onb_consent_title),
        subtitle = stringResource(R.string.onb_consent_body),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(
            checked = state.healthConsent,
            onCheckedChange = vm::setHealthConsent,
        )
        Text(
            text = stringResource(R.string.onb_consent_checkbox),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 14.dp),
        )
    }
    // Rıza vermeden önce metinlere erişilebilmesi şart: rıza "bilgilendirilmiş" olmalı.
    Spacer(Modifier.height(4.dp))
    val context = LocalContext.current
    val turkish = isTurkishUi()
    Row {
        TextButton(onClick = {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Legal.privacyUrl(turkish).toUri()),
                )
            }
        }) {
            Text(stringResource(R.string.settings_privacy_policy))
        }
        if (turkish) {
            TextButton(onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Legal.kvkkNoticeUrl().toUri()),
                    )
                }
            }) {
                Text(stringResource(R.string.settings_kvkk_notice))
            }
        }
    }

    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.disclaimer_long),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // Cihaz değiştiren ya da yeniden kuran kullanıcı onboarding'i tekrar geçmesin.
    Spacer(Modifier.height(12.dp))
    TextButton(
        onClick = onSignInClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.onb_have_account))
    }
}

@Composable
private fun NameStep(state: OnboardingState, vm: OnboardingViewModel) {
    StepHeader(title = stringResource(R.string.onb_name_title))
    OutlinedTextField(
        value = state.name,
        onValueChange = vm::setName,
        label = { Text(stringResource(R.string.onb_name_label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SexAgeStep(state: OnboardingState, vm: OnboardingViewModel) {
    StepHeader(
        title = stringResource(R.string.onb_sex_title),
        subtitle = stringResource(R.string.onb_sex_why),
    )
    OptionCard(
        title = stringResource(R.string.onb_sex_female),
        selected = state.sex == Sex.FEMALE,
        onClick = { vm.setSex(Sex.FEMALE) },
    )
    OptionCard(
        title = stringResource(R.string.onb_sex_male),
        selected = state.sex == Sex.MALE,
        onClick = { vm.setSex(Sex.MALE) },
    )
    Spacer(Modifier.height(8.dp))
    MeasureField(
        value = state.birthYear,
        onValueChange = vm::setBirthYear,
        label = stringResource(R.string.onb_birth_year_label),
        suffix = "",
        isError = state.birthYear.length == 4 && state.birthYearValue == null,
        imeAction = ImeAction.Done,
    )
}

@Composable
private fun BodyStep(state: OnboardingState, vm: OnboardingViewModel) {
    StepHeader(title = stringResource(R.string.onb_body_title))
    MeasureField(
        value = state.heightCm,
        onValueChange = vm::setHeight,
        label = stringResource(R.string.onb_height_label),
        suffix = stringResource(R.string.unit_cm),
        isError = state.heightCm.isNotEmpty() && state.heightValue == null,
    )
    MeasureField(
        value = state.weightKg,
        onValueChange = vm::setWeight,
        label = stringResource(R.string.onb_weight_label),
        suffix = stringResource(R.string.unit_kg),
        isError = state.weightKg.isNotEmpty() && state.weightValue == null,
        imeAction = ImeAction.Done,
    )
}

@Composable
private fun GoalStep(state: OnboardingState, vm: OnboardingViewModel) {
    StepHeader(title = stringResource(R.string.onb_goal_title))
    OptionCard(
        title = stringResource(R.string.goal_lose_fat),
        description = stringResource(R.string.goal_lose_fat_desc),
        selected = state.goal == Goal.LOSE_FAT,
        onClick = { vm.setGoal(Goal.LOSE_FAT) },
    )
    OptionCard(
        title = stringResource(R.string.goal_build_muscle),
        description = stringResource(R.string.goal_build_muscle_desc),
        selected = state.goal == Goal.BUILD_MUSCLE,
        onClick = { vm.setGoal(Goal.BUILD_MUSCLE) },
    )
    OptionCard(
        title = stringResource(R.string.goal_endurance),
        description = stringResource(R.string.goal_endurance_desc),
        selected = state.goal == Goal.ENDURANCE,
        onClick = { vm.setGoal(Goal.ENDURANCE) },
    )
    OptionCard(
        title = stringResource(R.string.goal_general),
        description = stringResource(R.string.goal_general_desc),
        selected = state.goal == Goal.GENERAL_FITNESS,
        onClick = { vm.setGoal(Goal.GENERAL_FITNESS) },
    )
}

@Composable
private fun ExperienceStep(state: OnboardingState, vm: OnboardingViewModel) {
    StepHeader(title = stringResource(R.string.onb_experience_title))
    OptionCard(
        title = stringResource(R.string.level_beginner),
        description = stringResource(R.string.level_beginner_desc),
        selected = state.experience == ExperienceLevel.BEGINNER,
        onClick = { vm.setExperience(ExperienceLevel.BEGINNER) },
    )
    OptionCard(
        title = stringResource(R.string.level_intermediate),
        description = stringResource(R.string.level_intermediate_desc),
        selected = state.experience == ExperienceLevel.INTERMEDIATE,
        onClick = { vm.setExperience(ExperienceLevel.INTERMEDIATE) },
    )
    OptionCard(
        title = stringResource(R.string.level_advanced),
        description = stringResource(R.string.level_advanced_desc),
        selected = state.experience == ExperienceLevel.ADVANCED,
        onClick = { vm.setExperience(ExperienceLevel.ADVANCED) },
    )
}

@Composable
private fun EquipmentStep(state: OnboardingState, vm: OnboardingViewModel) {
    StepHeader(title = stringResource(R.string.onb_equipment_title))
    OptionCard(
        title = stringResource(R.string.equipment_gym),
        selected = state.equipment == EquipmentAccess.GYM,
        onClick = { vm.setEquipment(EquipmentAccess.GYM) },
    )
    OptionCard(
        title = stringResource(R.string.equipment_home),
        selected = state.equipment == EquipmentAccess.HOME,
        onClick = { vm.setEquipment(EquipmentAccess.HOME) },
    )
    OptionCard(
        title = stringResource(R.string.equipment_both),
        selected = state.equipment == EquipmentAccess.BOTH,
        onClick = { vm.setEquipment(EquipmentAccess.BOTH) },
    )
}

@Composable
private fun ActivityStep(state: OnboardingState, vm: OnboardingViewModel) {
    StepHeader(
        title = stringResource(R.string.onb_activity_title),
        subtitle = stringResource(R.string.onb_activity_sub),
    )
    OptionCard(
        title = stringResource(R.string.activity_sedentary),
        description = stringResource(R.string.activity_sedentary_desc),
        selected = state.activity == ActivityLevel.SEDENTARY,
        onClick = { vm.setActivity(ActivityLevel.SEDENTARY) },
    )
    OptionCard(
        title = stringResource(R.string.activity_light),
        description = stringResource(R.string.activity_light_desc),
        selected = state.activity == ActivityLevel.LIGHT,
        onClick = { vm.setActivity(ActivityLevel.LIGHT) },
    )
    OptionCard(
        title = stringResource(R.string.activity_moderate),
        description = stringResource(R.string.activity_moderate_desc),
        selected = state.activity == ActivityLevel.MODERATE,
        onClick = { vm.setActivity(ActivityLevel.MODERATE) },
    )
    OptionCard(
        title = stringResource(R.string.activity_active),
        description = stringResource(R.string.activity_active_desc),
        selected = state.activity == ActivityLevel.ACTIVE,
        onClick = { vm.setActivity(ActivityLevel.ACTIVE) },
    )
    OptionCard(
        title = stringResource(R.string.activity_very_active),
        description = stringResource(R.string.activity_very_active_desc),
        selected = state.activity == ActivityLevel.VERY_ACTIVE,
        onClick = { vm.setActivity(ActivityLevel.VERY_ACTIVE) },
    )
}

@Composable
private fun MeasurementsStep(state: OnboardingState, vm: OnboardingViewModel) {
    StepHeader(
        title = stringResource(R.string.onb_measure_title),
        subtitle = stringResource(R.string.onb_measure_sub),
    )
    MeasureField(
        value = state.neckCm,
        onValueChange = vm::setNeck,
        label = stringResource(R.string.onb_neck_label),
        suffix = stringResource(R.string.unit_cm),
        isError = state.neckCm.isNotEmpty() && state.neckValue == null,
    )
    MeasureField(
        value = state.waistCm,
        onValueChange = vm::setWaist,
        label = stringResource(R.string.onb_waist_label),
        suffix = stringResource(R.string.unit_cm),
        isError = state.waistCm.isNotEmpty() && state.waistValue == null,
    )
    if (state.sex == Sex.FEMALE) {
        MeasureField(
            value = state.hipCm,
            onValueChange = vm::setHip,
            label = stringResource(R.string.onb_hip_label),
            suffix = stringResource(R.string.unit_cm),
            isError = state.hipCm.isNotEmpty() && state.hipValue == null,
            imeAction = ImeAction.Done,
        )
    }
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.onb_measure_optional),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SummaryStep(vm: OnboardingViewModel) {
    val summary = vm.summary()
    StepHeader(
        title = stringResource(R.string.onb_summary_title),
        subtitle = stringResource(R.string.onb_summary_sub),
    )
    if (summary == null) {
        Text(
            text = stringResource(R.string.onb_summary_missing),
            color = MaterialTheme.colorScheme.error,
        )
        return
    }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        MetricTile(
            label = stringResource(R.string.metric_calories),
            value = summary.calories.toString(),
            hint = stringResource(R.string.unit_kcal_per_day),
            modifier = Modifier.weight(1f),
        )
        MetricTile(
            label = stringResource(R.string.metric_water),
            value = "${summary.waterMl / 1000.0}",
            hint = stringResource(R.string.unit_liter_per_day),
            modifier = Modifier.weight(1f),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        MetricTile(
            label = stringResource(R.string.metric_bmi),
            value = summary.bmi?.let { String.format("%.1f", it) } ?: "—",
            hint = summary.bmiBand?.let { stringResource(bmiBandLabel(it)) },
            modifier = Modifier.weight(1f),
        )
        MetricTile(
            label = stringResource(R.string.metric_body_fat),
            value = summary.bodyFatPct?.let { "%${it}" } ?: "—",
            hint = stringResource(
                if (summary.bodyFatPct == null) {
                    R.string.metric_body_fat_missing
                } else {
                    R.string.metric_body_fat_navy
                },
            ),
            modifier = Modifier.weight(1f),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        MetricTile(
            label = stringResource(R.string.metric_protein),
            value = "${summary.macros.proteinG}${stringResource(R.string.unit_gram)}",
            modifier = Modifier.weight(1f),
        )
        MetricTile(
            label = stringResource(R.string.metric_carbs),
            value = "${summary.macros.carbsG}${stringResource(R.string.unit_gram)}",
            modifier = Modifier.weight(1f),
        )
        MetricTile(
            label = stringResource(R.string.metric_fat),
            value = "${summary.macros.fatG}${stringResource(R.string.unit_gram)}",
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.metric_bmr_tdee, summary.bmr, summary.tdee),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = stringResource(R.string.disclaimer_short),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun bmiBandLabel(band: BodyMath.BmiBand): Int = when (band) {
    BodyMath.BmiBand.UNDERWEIGHT -> R.string.bmi_underweight
    BodyMath.BmiBand.NORMAL -> R.string.bmi_normal
    BodyMath.BmiBand.OVERWEIGHT -> R.string.bmi_overweight
    BodyMath.BmiBand.OBESE -> R.string.bmi_obese
}
