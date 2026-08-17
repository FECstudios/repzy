package com.repzy.app.data.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `food_logs` satırı. Kalori/makro alanları DB'de numeric — Double olarak okunur,
 * arayüzde yuvarlanır. `source` AI tahmini mi elle mi girildiğini ayırır.
 */
@Serializable
data class FoodLog(
    val id: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("log_date") val logDate: LocalDate? = null,
    @SerialName("logged_at") val loggedAt: String? = null,
    val meal: MealType,
    val name: String,
    @SerialName("serving_desc") val servingDesc: String? = null,
    val grams: Double? = null,
    val calories: Double,
    @SerialName("protein_g") val proteinG: Double = 0.0,
    @SerialName("carbs_g") val carbsG: Double = 0.0,
    @SerialName("fat_g") val fatG: Double = 0.0,
    @SerialName("fiber_g") val fiberG: Double? = null,
    val source: FoodLogSource,
    @SerialName("ai_confidence") val aiConfidence: Double? = null,
    @SerialName("photo_path") val photoPath: String? = null,
)

/** Edge Function'ın döndürdüğü tek yemek kalemi. Kullanıcı kaydetmeden önce düzenleyebilir. */
@Serializable
data class MealItem(
    val name: String,
    val grams: Double? = null,
    val calories: Double,
    @SerialName("protein_g") val proteinG: Double = 0.0,
    @SerialName("carbs_g") val carbsG: Double = 0.0,
    @SerialName("fat_g") val fatG: Double = 0.0,
    @SerialName("fiber_g") val fiberG: Double? = null,
)

@Serializable
data class MealTotal(
    val calories: Double = 0.0,
    @SerialName("protein_g") val proteinG: Double = 0.0,
    @SerialName("carbs_g") val carbsG: Double = 0.0,
    @SerialName("fat_g") val fatG: Double = 0.0,
)

@Serializable
data class MealAnalysis(
    val items: List<MealItem> = emptyList(),
    val total: MealTotal = MealTotal(),
    val confidence: Double = 0.0,
    val note: String? = null,
    val model: String? = null,
    @SerialName("scansRemaining") val scansRemaining: Int = 0,
) {
    val isEmpty: Boolean get() = items.isEmpty()

    /** 0,5'in altı tahmin "düşük güven" — arayüzde uyarı gösterilir. */
    val isLowConfidence: Boolean get() = confidence < 0.5
}

/** Edge Function'ların ortak hata gövdesi. */
@Serializable
data class EdgeFunctionError(
    val error: String,
    val limit: Int? = null,
    val used: Int? = null,
)

/** Tahmin isteği gövdesi. */
@Serializable
data class MealAnalysisRequest(
    val image: String,
    val mimeType: String,
    val locale: String,
)

/** Günün beslenme toplamı — hedefle karşılaştırmak için. */
data class DayNutrition(
    val logs: List<FoodLog> = emptyList(),
) {
    val calories: Int get() = logs.sumOf { it.calories }.toInt()
    val proteinG: Int get() = logs.sumOf { it.proteinG }.toInt()
    val carbsG: Int get() = logs.sumOf { it.carbsG }.toInt()
    val fatG: Int get() = logs.sumOf { it.fatG }.toInt()

    fun byMeal(meal: MealType): List<FoodLog> = logs.filter { it.meal == meal }
}
