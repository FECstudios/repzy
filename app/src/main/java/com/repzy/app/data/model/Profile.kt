package com.repzy.app.data.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Doğum tarihi yerine sadece doğum yılı tutulur — yaş hesabı için yeterli,
 * KVKK veri minimizasyonu açısından daha az hassas.
 */
@Serializable
data class Profile(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    val sex: Sex? = null,
    @SerialName("birth_year") val birthYear: Int? = null,
    @SerialName("height_cm") val heightCm: Double? = null,
    val goal: Goal? = null,
    @SerialName("experience_level") val experienceLevel: ExperienceLevel? = null,
    @SerialName("equipment_access") val equipmentAccess: EquipmentAccess? = null,
    @SerialName("activity_level") val activityLevel: ActivityLevel? = null,
    @SerialName("unit_system") val unitSystem: UnitSystem = UnitSystem.METRIC,
    val locale: String = "tr",
    // timestamptz'ler ham ISO-8601 metin olarak tutuluyor — istemci sadece null/dolu ayrımına bakıyor.
    @SerialName("onboarding_completed_at") val onboardingCompletedAt: String? = null,
    @SerialName("health_data_consent_at") val healthDataConsentAt: String? = null,
    @SerialName("photo_consent_at") val photoConsentAt: String? = null,
) {
    val isOnboardingComplete: Boolean get() = onboardingCompletedAt != null
}

@Serializable
data class BodyMetric(
    val id: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("measured_on") val measuredOn: LocalDate,
    @SerialName("weight_kg") val weightKg: Double? = null,
    @SerialName("body_fat_pct") val bodyFatPct: Double? = null,
    @SerialName("muscle_mass_pct") val muscleMassPct: Double? = null,
    @SerialName("neck_cm") val neckCm: Double? = null,
    @SerialName("waist_cm") val waistCm: Double? = null,
    @SerialName("hip_cm") val hipCm: Double? = null,
    @SerialName("chest_cm") val chestCm: Double? = null,
    @SerialName("arm_cm") val armCm: Double? = null,
    @SerialName("thigh_cm") val thighCm: Double? = null,
    @SerialName("body_fat_source") val bodyFatSource: BodyFatSource? = null,
    val note: String? = null,
)

@Serializable
data class WaterLog(
    val id: String? = null,
    @SerialName("user_id") val userId: String? = null,
    /** Cihazın yerel tarihi — sunucunun UTC günü değil. */
    @SerialName("log_date") val logDate: LocalDate,
    @SerialName("amount_ml") val amountMl: Int,
)

@Serializable
data class NutritionTarget(
    val id: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("effective_from") val effectiveFrom: LocalDate,
    val calories: Int,
    @SerialName("protein_g") val proteinG: Int,
    @SerialName("carbs_g") val carbsG: Int,
    @SerialName("fat_g") val fatG: Int,
    @SerialName("water_ml") val waterMl: Int,
    val source: String = "rule",
)
