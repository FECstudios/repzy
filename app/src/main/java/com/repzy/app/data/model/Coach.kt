package com.repzy.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Koçun bugün için verdiği tek somut iş. */
@Serializable
data class CoachAction(
    val title: String,
    val why: String = "",
)

/**
 * Günlük koç brief'i. Sunucuda günde bir üretilip `ai_briefs`'e yazılıyor;
 * aynı gün tekrar istenirse `cached = true` ile kayıtlı hâli dönüyor.
 */
@Serializable
data class DailyBrief(
    val headline: String,
    val focus: String,
    val actions: List<CoachAction> = emptyList(),
    @SerialName("progress_note") val progressNote: String? = null,
    val model: String? = null,
    @SerialName("brief_date") val briefDate: String? = null,
    val cached: Boolean = false,
    /** Yenileme hakkı bittiyse sunucu kayıtlı brief'i bu işaretle döndürüyor. */
    val limitReached: Boolean = false,
)

@Serializable
data class DailyBriefRequest(
    val force: Boolean = false,
    val locale: String = "tr",
)
