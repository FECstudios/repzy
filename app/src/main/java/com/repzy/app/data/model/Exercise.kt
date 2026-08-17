package com.repzy.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Exercise(
    val id: String,
    @SerialName("name_tr") val nameTr: String,
    @SerialName("name_en") val nameEn: String,
    @SerialName("primary_muscle") val primaryMuscle: String,
    @SerialName("secondary_muscles") val secondaryMuscles: List<String> = emptyList(),
    val equipment: String,
    val setting: EquipmentAccess,
    val level: ExperienceLevel,
    val mechanic: String? = null,
    @SerialName("instructions_tr") val instructionsTr: List<String> = emptyList(),
    @SerialName("instructions_en") val instructionsEn: List<String> = emptyList(),
    @SerialName("common_mistakes_tr") val commonMistakesTr: List<String> = emptyList(),
    @SerialName("common_mistakes_en") val commonMistakesEn: List<String> = emptyList(),
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("animation_url") val animationUrl: String? = null,
    /** true ise sette tekrar değil süre (saniye) girilir — plank gibi. */
    @SerialName("is_time_based") val isTimeBased: Boolean = false,
) {
    // İçerik iki dilde aynı satırda tutuluyor; cihaz diline göre seçilir.
    fun name(turkish: Boolean): String = if (turkish) nameTr else nameEn
    fun instructions(turkish: Boolean): List<String> = if (turkish) instructionsTr else instructionsEn
    fun commonMistakes(turkish: Boolean): List<String> =
        if (turkish) commonMistakesTr else commonMistakesEn
}

@Serializable
data class ExerciseAlternative(
    @SerialName("exercise_id") val exerciseId: String,
    @SerialName("alternative_id") val alternativeId: String,
    val reason: String? = null,
)
