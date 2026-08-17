package com.repzy.app.core

/**
 * Yasal metinlerin adresleri. Metinler repodaki `docs/` klasöründe duruyor ve
 * GitHub Pages ile yayınlanıyor — Play Console gizlilik politikası ve veri silme
 * URL'i zorunlu kıldığı için uygulama içi ekran yeterli değil.
 *
 * Pages açıldıktan sonra [BASE] güncellenecek. Adres değişirse tek yer burası.
 */
object Legal {

    /** Örnek: "https://kullaniciadi.github.io/repzy/" — sonundaki eğik çizgi kalsın. */
    const val BASE = "https://repzy.github.io/repzy/"

    private const val TURKISH_PRIVACY = "gizlilik.html"
    private const val ENGLISH_PRIVACY = "privacy.html"
    private const val TURKISH_DELETION = "hesap-silme.html"
    private const val ENGLISH_DELETION = "delete-account.html"
    private const val KVKK_NOTICE = "kvkk-aydinlatma.html"

    fun privacyUrl(turkish: Boolean): String =
        BASE + if (turkish) TURKISH_PRIVACY else ENGLISH_PRIVACY

    fun deletionUrl(turkish: Boolean): String =
        BASE + if (turkish) TURKISH_DELETION else ENGLISH_DELETION

    /** Aydınlatma metni yalnızca Türkçe — KVKK'ya özgü bir belge. */
    fun kvkkNoticeUrl(): String = BASE + KVKK_NOTICE
}
