package com.repzy.app.core

import com.repzy.app.data.local.OnboardingDraft
import com.repzy.app.data.model.ActivityLevel
import com.repzy.app.data.model.BodyFatSource
import com.repzy.app.data.model.Goal
import com.repzy.app.data.model.Sex
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Onboarding sonunda gösterilen ve kaydedilen plan. */
data class PlanSummary(
    val ageYears: Int,
    val bmi: Double?,
    val bmiBand: BodyMath.BmiBand?,
    val bodyFatPct: Double?,
    val bodyFatSource: BodyFatSource?,
    val bmr: Int,
    val tdee: Int,
    val calories: Int,
    val macros: BodyMath.Macros,
    val waterMl: Int,
    val weeklyChangeKg: Double,
)

/**
 * Taslaktan planı hesaplar. Hem özet ekranı hem de hesap oluşturulduktan sonraki
 * senkron aynı fonksiyonu kullanır — iki yerde farklı sayı çıkma riski yok.
 */
fun planFor(draft: OnboardingDraft, today: LocalDate): PlanSummary? = planFrom(
    sex = draft.sex,
    goal = draft.goal,
    activity = draft.activity,
    heightCm = draft.heightValue,
    weightKg = draft.weightValue,
    birthYear = draft.birthYearValue,
    neckCm = draft.neckValue,
    waistCm = draft.waistValue,
    hipCm = draft.hipValue,
    today = today,
)

/**
 * Planın çekirdek hesabı. Onboarding taslağı ve Ayarlar'daki "yeniden hesapla"
 * bu tek fonksiyonu çağırıyor; kilo değişince aynı formülden geçilsin diye.
 * Zorunlu alanlardan biri eksikse null döner.
 */
fun planFrom(
    sex: Sex?,
    goal: Goal?,
    activity: ActivityLevel?,
    heightCm: Double?,
    weightKg: Double?,
    birthYear: Int?,
    neckCm: Double? = null,
    waistCm: Double? = null,
    hipCm: Double? = null,
    today: LocalDate,
): PlanSummary? {
    if (sex == null || goal == null || activity == null) return null
    val height = heightCm ?: return null
    val weight = weightKg ?: return null
    if (birthYear == null) return null

    val age = BodyMath.age(birthYear, today)
    val bmr = BodyMath.bmr(sex, weight, height, age)
    val tdee = BodyMath.tdee(bmr, activity)
    val calories = BodyMath.calorieTarget(goal, tdee, bmr, sex)
    val bmi = BodyMath.bmi(weight, height)
    val bodyFat = BodyMath.navyBodyFatPct(
        sex = sex,
        heightCm = height,
        neckCm = neckCm ?: 0.0,
        waistCm = waistCm ?: 0.0,
        hipCm = hipCm,
    )

    return PlanSummary(
        ageYears = age,
        bmi = bmi,
        bmiBand = bmi?.let { BodyMath.bmiBand(it) },
        bodyFatPct = bodyFat,
        bodyFatSource = bodyFat?.let { BodyFatSource.NAVY },
        bmr = bmr,
        tdee = tdee,
        calories = calories,
        macros = BodyMath.macros(goal, calories, weight),
        waterMl = BodyMath.waterTargetMl(weight, activity),
        weeklyChangeKg = BodyMath.weeklyWeightChangeKg(calories, tdee),
    )
}

/** `complete_onboarding(p jsonb)` RPC'sinin beklediği gövde. */
fun onboardingPayload(
    draft: OnboardingDraft,
    summary: PlanSummary,
    today: LocalDate,
    locale: String,
): JsonObject = buildJsonObject {
    put("p", buildJsonObject {
        put("display_name", draft.name.trim())
        put("sex", draft.sex?.wire)
        put("birth_year", draft.birthYearValue)
        put("height_cm", draft.heightValue)
        put("goal", draft.goal?.wire)
        put("experience_level", draft.experience?.wire)
        put("equipment_access", draft.equipment?.wire)
        put("activity_level", draft.activity?.wire)
        put("locale", locale)
        put("health_consent_at", draft.consentAtIso)

        put("measured_on", today.toString())
        put("weight_kg", draft.weightValue)
        put("body_fat_pct", summary.bodyFatPct)
        put("body_fat_source", summary.bodyFatSource?.wire)
        put("neck_cm", draft.neckValue)
        put("waist_cm", draft.waistValue)
        put("hip_cm", draft.hipValue)

        put("calories", summary.calories)
        put("protein_g", summary.macros.proteinG)
        put("carbs_g", summary.macros.carbsG)
        put("fat_g", summary.macros.fatG)
        put("water_ml", summary.waterMl)
    })
}
