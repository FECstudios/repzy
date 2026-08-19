package com.repzy.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val Green = Color(0xFF2E7D52)
private val GreenLight = Color(0xFF7BE495)
private val Ink = Color(0xFF0F1B2A)

// internal: widget'ın kendi renk şeması (RepzyGlanceTheme.kt) aynı marka
// paletini kullanıyor — cihazın duvar kağıdına göre değişen dinamik renk yerine.
internal val LightScheme = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8F2C8),
    onPrimaryContainer = Color(0xFF00210F),
    secondary = Color(0xFF4F6354),
    onSecondary = Color.White,
    tertiary = Color(0xFF3A6470),
    background = Color(0xFFFFFBFF),
    onBackground = Ink,
    surface = Color(0xFFFFFBFF),
    onSurface = Ink,
    surfaceVariant = Color(0xFFDDE5DC),
    onSurfaceVariant = Color(0xFF414942),
    outline = Color(0xFF717972),
    error = Color(0xFFBA1A1A),
)

internal val DarkScheme = darkColorScheme(
    primary = GreenLight,
    onPrimary = Color(0xFF003920),
    primaryContainer = Color(0xFF00522F),
    onPrimaryContainer = Color(0xFFB8F2C8),
    secondary = Color(0xFFB6CCB9),
    onSecondary = Color(0xFF223527),
    tertiary = Color(0xFFA2CDDB),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE1E3DE),
    surface = Color(0xFF101418),
    onSurface = Color(0xFFE1E3DE),
    surfaceVariant = Color(0xFF414942),
    onSurfaceVariant = Color(0xFFC1C9C0),
    outline = Color(0xFF8B938B),
    error = Color(0xFFFFB4AB),
)

@Composable
fun RepzyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // Çubuk renkleri enableEdgeToEdge() ile şeffaf; burada sadece ikon rengi ayarlanır.
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(colorScheme = scheme, typography = RepzyTypography, content = content)
}
