# kotlinx.serialization — @Serializable sınıfların serializer'ları reflection ile bulunur.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.repzy.app.**$$serializer { *; }
-keepclassmembers class com.repzy.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.repzy.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor / OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn io.ktor.**
-keepclassmembers class io.ktor.** { volatile <fields>; }
