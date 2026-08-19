package com.repzy.app.widget

import androidx.glance.material3.ColorProviders
import com.repzy.app.ui.theme.DarkScheme
import com.repzy.app.ui.theme.LightScheme

/**
 * Widget'ın renk şeması. Varsayılan [androidx.glance.GlanceTheme] Android 12+'ta
 * cihazın duvar kağıdından türeyen dinamik rengi kullanıyor — widget cihazdan
 * cihaza farklı, markasız görünüyordu. Bunun yerine uygulamanın kendi yeşil
 * paletini (Theme.kt) veriyoruz; widget her zaman Repzy gibi görünsün diye.
 */
internal val RepzyGlanceColors = ColorProviders(light = LightScheme, dark = DarkScheme)
