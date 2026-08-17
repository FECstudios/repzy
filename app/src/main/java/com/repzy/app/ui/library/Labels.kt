package com.repzy.app.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.repzy.app.R
import com.repzy.app.data.model.EquipmentAccess
import com.repzy.app.data.model.ExperienceLevel

/**
 * Veritabanındaki slug'ları (kas, ekipman) kullanıcıya gösterilecek metne çevirir.
 * Tanınmayan slug olduğu gibi gösterilir — yeni içerik eklenince ekran boş kalmasın.
 */
@Composable
fun muscleLabel(slug: String): String = when (slug) {
    "chest" -> stringResource(R.string.muscle_chest)
    "lats" -> stringResource(R.string.muscle_lats)
    "mid_back" -> stringResource(R.string.muscle_mid_back)
    "lower_back" -> stringResource(R.string.muscle_lower_back)
    "front_delts" -> stringResource(R.string.muscle_front_delts)
    "rear_delts" -> stringResource(R.string.muscle_rear_delts)
    "biceps" -> stringResource(R.string.muscle_biceps)
    "triceps" -> stringResource(R.string.muscle_triceps)
    "forearms" -> stringResource(R.string.muscle_forearms)
    "quadriceps" -> stringResource(R.string.muscle_quadriceps)
    "hamstrings" -> stringResource(R.string.muscle_hamstrings)
    "glutes" -> stringResource(R.string.muscle_glutes)
    "core" -> stringResource(R.string.muscle_core)
    else -> slug
}

@Composable
fun equipmentLabel(slug: String): String = when (slug) {
    "barbell" -> stringResource(R.string.equip_barbell)
    "dumbbell" -> stringResource(R.string.equip_dumbbell)
    "machine" -> stringResource(R.string.equip_machine)
    "cable" -> stringResource(R.string.equip_cable)
    "band" -> stringResource(R.string.equip_band)
    "bodyweight" -> stringResource(R.string.equip_bodyweight)
    else -> slug
}

@Composable
fun settingLabel(setting: EquipmentAccess): String = when (setting) {
    EquipmentAccess.GYM -> stringResource(R.string.equipment_gym)
    EquipmentAccess.HOME -> stringResource(R.string.equipment_home)
    EquipmentAccess.BOTH -> stringResource(R.string.equipment_both)
}

@Composable
fun levelLabel(level: ExperienceLevel): String = when (level) {
    ExperienceLevel.BEGINNER -> stringResource(R.string.level_beginner_short)
    ExperienceLevel.INTERMEDIATE -> stringResource(R.string.level_intermediate)
    ExperienceLevel.ADVANCED -> stringResource(R.string.level_advanced)
}
