package com.repzy.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
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
            // Sistemin duvar kağıdına göre değişen dinamik renk yerine sabit marka paleti.
            GlanceTheme(colors = RepzyGlanceColors) {
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
            .cornerRadius(24.dp)
            .padding(16.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_logo),
                contentDescription = null,
                modifier = GlanceModifier.width(22.dp).height(9.dp),
            )
            Spacer(GlanceModifier.width(8.dp))
            Text(
                text = context.getString(R.string.app_name),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 14.sp(),
                    fontWeight = FontWeight.Bold,
                ),
                modifier = GlanceModifier.defaultWeight(),
            )
            if (data.streakDays > 0) {
                StreakBadge(context, data.streakDays)
            }
        }

        Spacer(GlanceModifier.height(14.dp))

        // Veri hiç yazılmamışsa (kullanıcı henüz girmemiş) boş sayı göstermek yerine davet et.
        if (data.waterTargetMl <= 0 && data.calorieTarget <= 0) {
            EmptyState(context)
        } else {
            StatRow(
                icon = R.drawable.ic_widget_water,
                label = context.getString(R.string.metric_water),
                value = "${liters(data.waterMl)} / ${liters(data.waterTargetMl)} L",
                progress = data.waterProgress,
            )
            Spacer(GlanceModifier.height(12.dp))
            StatRow(
                icon = R.drawable.ic_widget_flame,
                label = context.getString(R.string.metric_calories),
                value = "${data.caloriesLeft} kcal",
                progress = data.calorieProgress,
            )
        }
    }
}

@Composable
private fun StreakBadge(context: Context, days: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier
            .background(GlanceTheme.colors.primaryContainer)
            .cornerRadius(20.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_flame),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer),
            modifier = GlanceModifier.size(12.dp),
        )
        Spacer(GlanceModifier.width(4.dp))
        Text(
            text = context.getString(R.string.widget_streak_days, days),
            style = TextStyle(
                color = GlanceTheme.colors.onPrimaryContainer,
                fontSize = 12.sp(),
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
private fun StatRow(icon: Int, label: String, value: String, progress: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
            modifier = GlanceModifier.size(16.dp),
        )
        Spacer(GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    text = label,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 11.sp(),
                    ),
                    modifier = GlanceModifier.defaultWeight(),
                )
                Text(
                    text = value,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 13.sp(),
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
            Spacer(GlanceModifier.height(6.dp))
            LinearProgressIndicator(
                modifier = GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
                progress = progress,
                color = GlanceTheme.colors.primary,
                backgroundColor = GlanceTheme.colors.surfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyState(context: Context) {
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = context.getString(R.string.widget_empty),
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 13.sp(),
                textAlign = androidx.glance.text.TextAlign.Center,
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
