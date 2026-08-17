plugins {
    // AGP 9'da Kotlin derlemesi AGP'nin içinde (built-in Kotlin) —
    // org.jetbrains.kotlin.android artık uygulanmıyor.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
