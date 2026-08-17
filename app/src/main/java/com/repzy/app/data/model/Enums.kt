package com.repzy.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Postgres enum değerleriyle birebir eşleşir. [wire] ile @SerialName aynı olmak zorunda —
 * biri değişirse migration'daki enum da değişmeli.
 */

@Serializable
enum class Sex(val wire: String) {
    @SerialName("male") MALE("male"),
    @SerialName("female") FEMALE("female"),
}

@Serializable
enum class Goal(val wire: String) {
    @SerialName("lose_fat") LOSE_FAT("lose_fat"),
    @SerialName("build_muscle") BUILD_MUSCLE("build_muscle"),
    @SerialName("endurance") ENDURANCE("endurance"),
    @SerialName("general_fitness") GENERAL_FITNESS("general_fitness"),
}

@Serializable
enum class ExperienceLevel(val wire: String) {
    @SerialName("beginner") BEGINNER("beginner"),
    @SerialName("intermediate") INTERMEDIATE("intermediate"),
    @SerialName("advanced") ADVANCED("advanced"),
}

@Serializable
enum class EquipmentAccess(val wire: String) {
    @SerialName("gym") GYM("gym"),
    @SerialName("home") HOME("home"),
    @SerialName("both") BOTH("both"),
}

@Serializable
enum class ActivityLevel(val wire: String, val factor: Double) {
    @SerialName("sedentary") SEDENTARY("sedentary", 1.2),
    @SerialName("light") LIGHT("light", 1.375),
    @SerialName("moderate") MODERATE("moderate", 1.55),
    @SerialName("active") ACTIVE("active", 1.725),
    @SerialName("very_active") VERY_ACTIVE("very_active", 1.9),
}

@Serializable
enum class UnitSystem(val wire: String) {
    @SerialName("metric") METRIC("metric"),
    @SerialName("imperial") IMPERIAL("imperial"),
}

@Serializable
enum class BodyFatSource(val wire: String) {
    @SerialName("user") USER("user"),
    @SerialName("navy") NAVY("navy"),
    @SerialName("device") DEVICE("device"),
}

@Serializable
enum class PhotoPose(val wire: String) {
    @SerialName("front") FRONT("front"),
    @SerialName("side") SIDE("side"),
    @SerialName("back") BACK("back"),
}

@Serializable
enum class MealType(val wire: String) {
    @SerialName("breakfast") BREAKFAST("breakfast"),
    @SerialName("lunch") LUNCH("lunch"),
    @SerialName("dinner") DINNER("dinner"),
    @SerialName("snack") SNACK("snack"),
}

@Serializable
enum class FoodLogSource(val wire: String) {
    @SerialName("ai_photo") AI_PHOTO("ai_photo"),
    @SerialName("manual") MANUAL("manual"),
    @SerialName("search") SEARCH("search"),
    @SerialName("barcode") BARCODE("barcode"),
}
