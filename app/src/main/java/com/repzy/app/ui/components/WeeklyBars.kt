package com.repzy.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate

/**
 * Son 7 günün çubuk grafiği. Harici kütüphane yok, tek Canvas — WeightChart ile
 * aynı yaklaşım.
 *
 * Hedef çizgisi kesikli olarak çiziliyor: kullanıcı "az mı yedim çok mu"yu
 * sayıya bakmadan görsün. Kaydı olmayan gün sıfır değil, soluk bir taban olarak
 * duruyor; boş gün ile "0 kcal yedim" farklı şeyler.
 */
@Composable
fun WeeklyBars(
    values: Map<LocalDate, Int>,
    today: LocalDate,
    target: Int,
    dayLabels: List<String>,
    modifier: Modifier = Modifier,
) {
    val days = (6 downTo 0).map { offset ->
        LocalDate.fromEpochDays(today.toEpochDays() - offset)
    }
    val maxValue = maxOf(values.values.maxOrNull() ?: 0, target, 1)

    val barColor = MaterialTheme.colorScheme.primary
    val emptyColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val targetColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

    Column(modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
        ) {
            val slot = size.width / days.size
            val barWidth = slot * 0.5f

            // Hedef çizgisi
            if (target > 0) {
                val y = size.height - (target.toFloat() / maxValue) * size.height
                var x = 0f
                while (x < size.width) {
                    drawLine(
                        color = targetColor,
                        start = Offset(x, y),
                        end = Offset((x + 10f).coerceAtMost(size.width), y),
                        strokeWidth = 2f,
                        cap = StrokeCap.Round,
                    )
                    x += 18f
                }
            }

            days.forEachIndexed { index, day ->
                val value = values[day]
                val left = slot * index + (slot - barWidth) / 2f

                if (value == null || value == 0) {
                    // Kayıt yok: ince taban çizgisi
                    drawRect(
                        color = emptyColor,
                        topLeft = Offset(left, size.height - 4f),
                        size = Size(barWidth, 4f),
                    )
                } else {
                    val barHeight = (value.toFloat() / maxValue) * size.height
                    drawRect(
                        color = barColor,
                        topLeft = Offset(left, size.height - barHeight),
                        size = Size(barWidth, barHeight),
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            days.forEach { day ->
                Text(
                    // Pazartesi=1 ... Pazar=7; etiketler çağıran taraftan çevrili geliyor.
                    text = dayLabels.getOrElse(day.dayOfWeek.isoDayNumber - 1) { "" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** kotlinx-datetime DayOfWeek'i ISO numarasına çevirmek için küçük yardımcı. */
private val kotlinx.datetime.DayOfWeek.isoDayNumber: Int
    get() = ordinal + 1
