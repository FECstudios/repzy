package com.repzy.app.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.repzy.app.R
import com.repzy.app.data.model.BodyMetric
import java.util.Locale

/**
 * Kilo geçmişi grafiği. Harici grafik kütüphanesi eklemeye değmeyecek kadar basit:
 * tek bir Canvas'ta çizgi + noktalar. Nokta sayısı azken bile anlamlı görünmesi için
 * dikey eksen veriye göre ölçekleniyor, sıfırdan başlamıyor.
 */
@Composable
fun WeightChart(
    metrics: List<BodyMetric>,
    modifier: Modifier = Modifier,
) {
    // Sunucu tarihe göre azalan döndürüyor; grafik soldan sağa artan olmalı.
    val points = metrics
        .mapNotNull { metric -> metric.weightKg?.let { metric.measuredOn to it } }
        .sortedBy { it.first }

    if (points.size < 2) {
        Text(
            text = stringResource(
                if (points.isEmpty()) R.string.chart_no_metrics else R.string.chart_need_two,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val values = points.map { it.second }
    val min = values.min()
    val max = values.max()
    // Tüm değerler eşitse sıfıra bölmeyi engelle, çizgi ortada düz geçsin.
    val span = (max - min).takeIf { it > 0.01 } ?: 1.0

    val lineColor = MaterialTheme.colorScheme.primary
    val dotColor = MaterialTheme.colorScheme.onPrimaryContainer

    Column(modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
        ) {
            val stepX = if (points.size == 1) 0f else size.width / (points.size - 1)
            val path = Path()

            points.forEachIndexed { index, (_, value) ->
                val x = stepX * index
                // Yukarı doğru artsın: yüksek kilo yukarıda.
                val y = size.height - ((value - min) / span).toFloat() * size.height
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 6f, cap = StrokeCap.Round),
            )

            points.forEachIndexed { index, (_, value) ->
                val x = stepX * index
                val y = size.height - ((value - min) / span).toFloat() * size.height
                drawCircle(color = dotColor, radius = 7f, center = Offset(x, y))
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = "${points.first().first} · ${format(values.first())} kg",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.fillMaxWidth().weight(1f))
            Text(
                text = "${format(values.last())} kg",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val delta = values.last() - values.first()
        if (kotlin.math.abs(delta) >= 0.1) {
            Text(
                text = (if (delta > 0) "+" else "") + format(delta) + " kg",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun format(value: Double): String =
    String.format(Locale.getDefault(), "%.1f", value)
