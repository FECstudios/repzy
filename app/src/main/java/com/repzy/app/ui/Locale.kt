package com.repzy.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Egzersiz içeriği veritabanında iki dilde tek satırda tutuluyor (name_tr / name_en),
 * bu yüzden hangi kolonun okunacağına çalışma anında karar veriliyor —
 * strings.xml'in aksine res-qualifier ile çözülemiyor.
 */
@Composable
@ReadOnlyComposable
fun isTurkishUi(): Boolean {
    val locales = LocalConfiguration.current.locales
    return if (locales.isEmpty) false else locales.get(0).language == "tr"
}
