package com.repzy.app.core

/**
 * Yasal metinlerin adresleri. Metinler repodaki `docs/` klasöründe duruyor ve
 * GitHub Pages ile yayınlanıyor — Play Console gizlilik politikası ve veri silme
 * URL'i zorunlu kıldığı için uygulama içi ekran yeterli değil.
 *
 * Adres `github.com/FECstudios/repzy` deposundan türüyor. Depo adı ya da sahibi
 * değişirse tek değişecek yer burası.
 *
 * **Çalışması için Pages'in açık olması gerekiyor:** GitHub → Settings → Pages →
 * Source: "Deploy from a branch", Branch: `master`, Folder: `/docs`. Açılmadan
 * uygulamadaki "Gizlilik Politikası" bağlantıları 404 döner.
 */
object Legal {

    /** Sonundaki eğik çizgi kalsın — dosya adları buna ekleniyor. */
    const val BASE = "https://fecstudios.github.io/repzy/"

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
