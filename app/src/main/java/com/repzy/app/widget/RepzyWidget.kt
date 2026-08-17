package com.repzy.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.repzy.app.MainActivity
import com.repzy.app.R
import java.util.Locale

/**
 * Ana ekran widget'ı: streak, su ve kalan kalori. Dokunmak uygulamayı açıyor.
 *
 * Veri [WidgetSnapshotStore]'dan okunuyor — widget ağa çıkmıyor. Bu yüzden
 * "+200 ml" gibi bir eylem koymadım: widget'tan yazıp sunucuya gönderemeyeceğimiz
 * için kullanıcıya yalan söyleyen bir buton olurdu.
 */
class RepzyWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = readWidgetData(context)

        provideContent {
            GlanceTheme {
                WidgetContent(context, data)
            }
        }
    }
}

@Composable
private fun WidgetContent(context: Context, data: WidgetData) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(20.dp)
            .padding(14.dp)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = context.getString(R.string.app_name),
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontSize = 13.sp(),
                    fontWeight = FontWeight.Bold,
                ),
                modifier = GlanceModifier.defaultWeight(),
            )
            if (data.streakDays > 0) {
                Text(
                    text = context.getString(R.string.home_streak, data.streakDays),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp(),
                    ),
                )
            }
        }

        Spacer(GlanceModifier.height(8.dp))

        // Veri hiç yazılmamışsa (kullanıcı henüz girmemiş) boş sayı göstermek yerine davet et.
        if (data.waterTargetMl <= 0 && data.calorieTarget <= 0) {
            Text(
                text = context.getString(R.string.widget_empty),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 13.sp(),
                ),
            )
            return@Column
        }

        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Stat(
                label = context.getString(R.string.metric_water),
                value = "${liters(data.waterMl)} / ${liters(data.waterTargetMl)} L",
            )
            Spacer(GlanceModifier.width(12.dp))
            Stat(
                label = context.getString(R.string.metric_calories),
                value = "${data.caloriesLeft} kcal",
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 11.sp(),
            ),
        )
        Text(
            text = value,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 16.sp(),
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

class RepzyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = RepzyWidget()
}

private fun liters(ml: Int): String = String.format(Locale.getDefault(), "%.2f", ml / 1000.0)

/** Glance TextStyle sp bekliyor; tek yerde çevirip tekrarı azaltıyoruz. */
private fun Int.sp() = androidx.compose.ui.unit.TextUnit(
    toFloat(),
    androidx.compose.ui.unit.TextUnitType.Sp,
)
