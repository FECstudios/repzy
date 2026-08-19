# PROJE: Repzy — AI Fitness & Beslenme Koçu

> Bu dosya projenin tek doğruluk kaynağıdır. Yeni bir oturuma başlarken önce bunu oku.
> Kararlar değiştikçe bu dosyayı güncelle.

---

## 1. Ürün nedir

Spor salonuna yeni başlayan (ama ileri seviyeyi de destekleyen) kullanıcı için,
**hem antrenmanı hem beslenmeyi birlikte yöneten, kullanıcıyla birlikte gelişen
Türkçe/İngilizce kişisel koç uygulaması.**

**Ana vaat:** "Sıfırdan başlıyorsun, ne yapacağını bilmiyorsun — bu uygulama
antrenmanını da beslenmeni de senin verine göre yönetir ve sen geliştikçe adapte olur."

### Pozisyon notu (ÖNEMLİ)
"Gym aleti kullanımını anlatan uygulama" tek başına farklılaştırıcı DEĞİL.
Play Store'da "Spotter — Your Gym Reference" adlı ücretsiz bir uygulama bunu
zaten yapıyor (1300+ egzersiz, animasyon, kameradan alet tanıma, offline, abonelik yok).

Bizim farkımız o değil, **kişiselleştirme + kalıcı profil + adaptif program +
beslenme entegrasyonu + Türkçe içerik.** O uygulama bir sözlük, bizimki bir koç.
Alet rehberi bizde bir özellik, ana vaat değil.

### Hedef kullanıcı
1. **Birincil:** Spor salonundan çekinen, ne yapacağını bilmeyen yeni başlayan
2. **İkincil:** Evde çalışan, salona gitmeyen kişi
3. **Üçüncül:** İleri seviye (programını kendisi ayarlamak isteyen)

---

## 2. Teknik kararlar

| Konu | Karar | Neden |
|---|---|---|
| İsim | **Repzy** (paket `com.repzy.app`) | 2026-08-17'de kesinleşti; Spotter elendi |
| Platform | **Android-first, native Kotlin + Jetpack Compose** | Kamera, pose estimation, Health Connect için native gerekli |
| Build zinciri | Gradle 9.7 + **AGP 9.3.1 (built-in Kotlin)** + Kotlin 2.3.21 + KSP 2.3.11 | Hilt 2.60 AGP 9 zorunlu kılıyor; AGP 9'da `org.jetbrains.kotlin.android` uygulanmaz |
| SDK | compileSdk **37**, targetSdk 36, minSdk 26 | androidx 1.19 / Compose 1.12 API 37'ye derlenmeyi şart koşuyor |
| WebView? | **HAYIR** | OutfitMind'daki WebView yaklaşımı burada kullanılmayacak |
| Backend | **Supabase** (Free tier → Pro $25/ay) | Auth + Postgres + Storage + Edge Functions bir arada, öngörülebilir maliyet |
| Firebase? | Hayır | Pay-as-you-go harcama tavanı yok, indie için riskli |
| AI (geliştirme) | **Hack Club AI** — `https://ai.hackclub.com/proxy/v1` | Ücretsiz, OpenAI uyumlu, vision modelleri var |
| AI key nerede | Sadece Supabase Edge Function secret'ı (`AI_API_KEY`) | Repoda, `local.properties`'te ve APK'da yok |
| AI (production) | **Kendi Gemini/OpenAI key'i** | Hack Club AI öğrenme amaçlı, ticari production için uygun değil |
| Pose estimation | **MediaPipe / Google ML Kit** (on-device, ücretsiz) | v2'ye ertelendi |
| Besin DB | **USDA FoodData Central + Open Food Facts** (ücretsiz) | Başlangıç için yeterli |
| Egzersiz içeriği | Ücretsiz/açık kaynak: wger, free-exercise-db, ExerciseDB | Sonra tek seferlik satın alma değerlendirilir |

### AI mimarisi — KRİTİK
```
Android app  →  Supabase Edge Function  →  AI sağlayıcı
                (API key BURADA durur)
```
**API key ASLA Android uygulamasının içine konulmaz.** APK decompile edilir,
key çalınır, günlük limit sıfırlanır. Edge Function ayrıca sağlayıcı değiştirmeyi
de uygulama güncellemesi gerektirmeden mümkün kılar.

### Hack Club AI vision modelleri
- `google/gemini-2.5-flash`
- `qwen/qwen3-vl-235b-a22b-instruct`
- `nvidia/nemotron-nano-12b-v2-vl`
- `openai/gpt-5-mini`

Endpoint OpenAI uyumlu → sağlayıcı geçişi sadece base URL + key değişikliği.
Günlük limit: $3 (yaklaşık 1.000–3.000 yemek fotoğrafı analizi). Limit dolunca 429 döner.

---

## 3. Kapsam — faz faz

### FAZ 1 — MVP (buradan başla)
Amaç: uçtan uca çalışan ince bir dikey dilim. Önce bu bitecek.

- [x] Supabase şeması + auth (e-posta/şifre; migration'lar `supabase/migrations/`)
- [x] **Google ile giriş**: `data/repo/GoogleAuthRepository.kt` — tarayıcı OAuth değil,
      **Credential Manager** (kullanıcı uygulamadan çıkmıyor). Alınan kimlik token'ı
      `signInWith(IDToken)` ile Supabase'e veriliyor; ham nonce istemcide, SHA-256'lısı
      Google'a gidiyor. `GOOGLE_WEB_CLIENT_ID` `local.properties`'ten BuildConfig'e
      geçiyor — boşsa buton hiç çizilmiyor. Derin bağlantı: `repzy://login-callback`
      (manifest + `SupabaseModule` scheme/host + Supabase Redirect URLs).
      **Gerçek cihazda doğrulandı** (18 Ağu 2026): çıkış → "Google ile devam et" →
      e-posta eşleştiği için mevcut hesaba bağlandı, veri aynen döndü (yeni kullanıcı açmadı).
      _Google Cloud'da hem `com.repzy.app.debug` hem `com.repzy.app` için Android
      istemcisi gerekiyor; SHA-1 farklı olduğu için release imzası ayrıca eklenecek._
- [x] Onboarding akışı: 10 adım — açık rıza, isim, cinsiyet+doğum yılı, boy/kilo,
      hedef, deneyim, ekipman, aktivite, çevre ölçüleri, özet.
      Kayıt tek RPC ile atomik: `complete_onboarding(jsonb)`
- [x] Vücut yağ %: Navy Method (boyun + bel + kalça) — `BodyMath.navyBodyFatPct`
      _Eksik: kullanıcının elle girmesi (cihaz/kaliper değeri) henüz yok_
- [x] BMI: girilmez, kilo/boydan türetilir (DB'de saklanmaz)
- [x] **Vücut fotoğrafı + before/after karşılaştırma**: `ui/photos/` + `data/repo/BodyPhotoRepository.kt`.
      Ayarlar > İlerleme fotoğrafları. Ön/yan/arka pozu, kameradan veya galeriden,
      1440 px'e küçültülüp `body-photos` bucket'ına yükleniyor (base64 değil ham JPEG).
      Bucket public değil — gösterim için 1 saatlik **imzalı URL** üretiliyor.
      Fotoğraf **AI'ya gönderilmiyor**; yemek fotoğrafından farkı bu.
      Ayrı açık rıza kapısı: `photo_consent_at` dolu değilse çekme/seçme düğmeleri hiç
      çizilmiyor (onboarding rızası bunu kapsamıyor, KVKK spesifik rıza istiyor).
      Karşılaştırma aynı poz içinde iki fotoğraf seçilerek açılıyor; eski "önce",
      yeni "sonra" oluyor. Silme önce dosyayı, sonra satırı siliyor.
      **Gerçek cihazda doğrulandı** (18 Ağu 2026): iki fotoğraf yüklendi, imzalı URL'lerle
      göründü, slider sürüklendi, ikisi de silindi.
      _Slider'da bir tuzak var: "önce" katmanını dar bir kutuya koyup kırpmak iki
      fotoğrafı hizasız bırakıyor (dar kutu Crop'un ölçeğini ve merkezini değiştiriyor).
      Kırpma layout'ta değil `drawWithContent` + `clipRect` ile ÇİZİMDE yapılıyor._
- [x] Kalıcı profil + **Ayarlar sekmesi**: onboarding'de girilen her şey düzenlenebiliyor
      (isim, cinsiyet, doğum yılı, boy, hedef, deneyim, ekipman, aktivite), ölçüler
      (kilo + boyun/bel/kalça) bugünün tarihine yazılıyor, Navy vücut yağı yeniden hesaplanıyor.
      Hedefler elle girilebiliyor (`nutrition_targets.source = 'user'`) ya da
      **"Planımı yeniden hesapla"** ile otomatik hesaplanıyor (`source = 'rule'`).
      Yeniden hesap önce önizleme diyaloğunda gösteriliyor, onaylanmadan yazılmıyor.
      Çıkış yap buraya taşındı.
- [x] Ana sayfada ilerleme: kalori "yenen / hedef" + ilerleme çubuğu, makrolar
      "37 / 113 g" biçiminde, son 7 günün kalori çubuk grafiği (hedef çizgisi kesikli,
      kayıt olmayan gün sıfır değil soluk taban), kilo grafiği ve BMI bandı.
      Önceden sadece hedef yazıyordu, ne kadarının yendiği görünmüyordu.
- [x] İlerleme grafiği: Ayarlar'daki "Kilo geçmişi" kartı (`ui/components/WeightChart.kt`,
      harici kütüphane yok, tek Canvas). İki ölçümden az varsa bilgilendirme gösteriyor.
- [x] Hesap silme (Play zorunluluğu): Ayarlar → Hesabımı sil, onay kelimesi yazılmadan
      çalışmaz (`SİL` / `DELETE`). `delete_my_account()` RPC'sini çağırıyor; öncesinde
      Storage klasörleri boşaltılıyor (satır silmek dosyayı silmiyor). Uygulama dışından
      talep için `docs/hesap-silme.html` sayfası var — Play "veri silme URL'i" istiyor.
      _Silme akışı canlı hesapta bilerek denenmedi (geri alınamaz); tek kullanımlık bir
      hesapla test edilmeli._
- [x] Gizlilik politikası uygulama içinde erişilebilir: Ayarlar → Yasal ve onboarding'in
      rıza adımında bağlantı var (rızanın "bilgilendirilmiş" olması için şart).
- [x] Egzersiz kütüphanesi: 16 hareket, liste + arama + filtre + detay ekranı
      (form anlatımı numaralı adımlar, sık hatalar). _Görsel/animasyon hâlâ yok_
- [x] Gym / ev alternatifi: detay ekranının altında, tıklanabilir
- [x] Seviyeye göre filtre (ortam ve kas grubu filtresiyle birlikte; onboarding'deki
      ekipman/seviye seçimi filtrelere varsayılan olarak uygulanıyor)
- [x] Antrenman kaydı: seans başlat → egzersiz ekle (kütüphane seçici olarak yeniden
      kullanılıyor) → set/tekrar/ağırlık veya süre gir → zorluk 1-10 → bitir.
      Setler girildiği anda yazılır; aktif seans `finished_at is null` satırı olduğu için
      uygulama kapansa da kaldığı yerden devam eder. "Geçen sefer" referansı gösterilir.
- [x] Fotoğraftan AI kalori/makro tahmini: Edge Function `supabase/functions/analyze-meal`
      (API key sadece orada, günlük tarama limiti `ai_usage` üzerinden sayılıyor) + Beslenme
      sekmesi (kamera/galeri → onay sayfası → `food_logs`).
      **Gerçek cihazda doğrulandı** (17 Ağu 2026): kullanıcının çektiği fotoğraf → iki kalem
      Türkçe döndü, öğün saatten çıkarıldı, kayıt `food_logs`'a yazıldı, "4 tarama kaldı" göründü.
- [x] Su takibi (Home'daki su kartı: +200/+330/+500 ml, geri al, hedefe göre ilerleme)
- [x] Streak (sunucuda `current_streak()`; aktif gün = su, yemek veya antrenman kaydı)
- [x] Before/after fotoğraf karşılaştırma slider'ı (`ui/components/BeforeAfterSlider.kt`)

### FAZ 2 — Kişiselleştirme
- [ ] Egzersiz animasyonları (Lottie tercih — dosya boyutu küçük, native entegrasyon kolay)
- [x] **Günlük plan üreticisi** (kural tabanlı, AI değil): `core/DailyPlanner.kt`.
      Girdi hedef + seviye + ekipman + son 7 günün antrenman günleri. Yeni başlayana
      tam vücut, ileri seviyeye üst/alt/karın dönüşümü; haftalık hedef dolduysa
      dinlenme günü öneriyor. Aynı kas grubundan en fazla iki hareket.
      Set/tekrar/dinlenme hedefe göre değişiyor. Antrenman sekmesinde "Bugünün planı"
      kartı + "Bu planla başla" (planı hareketleriyle seansa çeviriyor).
      **9 birim testi** bu kuralları koruyor. AI çağrısı yok — anında ve ücretsiz.
- [x] **"Planın hazır" bildirimi**: Ayarlar'dan açılan üçüncü hatırlatıcı, varsayılan 08:00.
- [ ] Sakatlık/ağrı modu (belirli kas grubunu programdan çıkar)
- [x] **Bildirimler**: WorkManager + `notifications/Reminders.kt`. Su (11:00/15:00/19:00) ve
      antrenman (saat seçilebilir) hatırlatıcıları, Ayarlar'dan açılıp kapanıyor.
      Android 13+ izni anahtar açılınca isteniyor, açılışta değil. Veri okumuyorlar
      (saate dayalı) — işçi arka planda oturum yenilemek zorunda kalmasın diye.
      **Gerçek cihazda doğrulandı**: 19:00 hatırlatıcısı kendiliğinden tetiklendi.
- [x] **Ana ekran widget'ı** (Glance): streak, su ve kalan kalori; dokunmak uygulamayı açıyor.
      Widget ağa çıkmıyor — uygulama her Home yüklemesinde `WidgetSnapshotStore`'a
      anlık görüntü yazıyor, widget onu okuyor. Çıkışta temizleniyor.
- [x] **Çoklu dil altyapısı**: `res/xml/locales_config.xml` + manifest `localeConfig`.
      Android cihaz diline göre otomatik seçiyor; Ayarlar > Dil bölümünden sistemin
      uygulama-başına dil ekranı açılıyor (API 33+, eskilerde uygulama ayarlarına düşüyor).
      **Şu an çevrili: tr, en, ru** (values-ru tam çeviri; Rusça sekme etiketleri
      beş sekmede iki satıra sardığı için kısaltıldı: Тренинг / Каталог). Yeni dil eklemek = `values-<kod>/strings.xml` çevirisi +
      locales_config'e satır. Çevrilmemiş dili listeye EKLEME: kullanıcı İngilizce görür
      ama uygulama o dili destekliyor sanılır.
      _Egzersiz içeriği (isim/anlatım) DB'de sadece tr/en kolonlarında — yeni dilde
      arayüz çevrilir ama egzersiz metinleri İngilizce kalır._
- [~] **Paywall + abonelik**: `PaywallScreen` onboarding sonrası bir kez ve Ayarlar'dan
      açılıyor (kapatılabilir, ücretsiz katman çalışmaya devam eder). Play Billing 9
      bağlı; ürün Play Console'da tanımlı olmadığı için "satışa açık değil" durumu
      gösteriliyor (cihazda doğrulandı, çökmüyor).
      **Premium yetkisi SUNUCUDAN geliyor** (`is_premium()`, migration 0009):
      `subscriptions` tablosuna kullanıcı yazamaz. Eksik olan tek parça
      `verify-purchase` Edge Function'ı — Google Play Developer API ile satın alma
      doğrulaması. O kurulmadan kimse premium olamıyor (bilinçli "fail closed").

### FAZ 3 — AI katmanı
- [x] **Günlük koç brief'i**: Edge Function `daily-brief` + `coach_context()` RPC +
      `ai_briefs` tablosu (migration 0007) + Home'daki "Bugünün koçu" kartı.
      Koç kullanıcının kendi verisini görüyor: bugünün kalori/su/antrenman durumu,
      son 7 günün ortalamaları ve çalışılan kas grupları, 60 günlük kilo değişimi,
      streak, kayıt tutma sıklığı ve **dünkü brief** (kendini tekrar etmesin diye).
      Günde bir üretilip saklanıyor — aynı gün tekrar açılışta AI'ya gidilmiyor;
      elle yenileme günde 2 ile sınırlı. Fotoğraf/isim gibi kişisel alan AI'ya gitmiyor.
      **Gerçek cihazda doğrulandı** (17 Ağu 2026): brief üretildi, yemek kaydı girildikten
      sonra yenilemede tavsiye değişti ("bir öğün kaydet" → "kaydetmeye devam et"),
      3. yenilemede AI'ya gitmeden `limitReached` döndü.

#### AI kullanım kaydı — dikkat
`ai_usage`'a yazma **`record_ai_usage()` RPC'si ile** yapılır (migration 0008).
Tabloda bilerek sadece SELECT politikası var; Edge Function da kullanıcının JWT'siyle
çalıştığı için doğrudan insert RLS'e takılıyor ve **kullanım sessizce kaydedilmiyordu** —
sayaç 0 kalıyor, günlük limit fiilen uygulanmıyordu. Yeni bir AI özelliği eklerken
kullanımı bu RPC ile kaydet, `.from("ai_usage").insert()` kullanma.
- [ ] Chat tabanlı diyetisyen asistanı (kullanıcının geçmiş verisine göre)
- [ ] Otomatik program adaptasyonu (hedefe ulaşılmıyorsa kalori/antrenman güncelle)
- [ ] Haftalık AI özet raporu
- [ ] Kalan makrolara göre yemek tarifi önerisi

### Gelişmiş takip (Health Connect) — kod hazır, YAYINA HAZIR DEĞİL
`health/HealthConnectRepository.kt` + Ayarlar > Gelişmiş takip anahtarı + Ana sayfada
adım / aktif kalori / aktif süre kutuları. **Sadece okuma izni** isteniyor (READ_STEPS,
READ_ACTIVE_CALORIES_BURNED, READ_EXERCISE); yazma yok. Veri sunucuya gitmiyor,
sadece ekranda gösteriliyor. Health Connect kurulu değilse Play bağlantısı,
desteklenmiyorsa açıklama gösteriliyor. Cihazda anahtar göründü (Android 16).

**Yayından önce şart olanlar:**
1. Gizlilik politikası + KVKK metni: saat/sensör verisi şu an metinlerde geçmiyor.
2. Bu veriler de özel nitelikli → **ayrı açık rıza** gerekiyor, mevcut rıza kapsamıyor.
3. Play, Health Connect izinleri için ayrı bir bildirim formu istiyor.
4. TDEE düzeltmesinde kullanılacaksa `BodyMath` ve testleri değişir — henüz kullanılmıyor.

### Play Store görselleri
`store/` — 6 ekran görüntüsü (1080x1920) + feature graphic (1024x500) + **icon-512.png**.
Üreten betik `store/make_store_assets.py`: `store/src/` altındaki ham cihaz ekran
görüntülerini marka zemininde çerçeveliyor. Uydurma mockup yok.

**Metinler İngilizce** — listeleme global/İngilizce öncelikli (bölüm 5). Ekranlar
uygulamayı İngilizceye alarak çekiliyor:
`adb shell cmd locale set-app-locales com.repzy.app.debug --locales en-US`
(bitince `--locales ""` ile geri al, yoksa cihaz İngilizce kalıyor).

Betik durum çubuğunu ve hareket çubuğunu kırpıyor (`STATUS_BAR_PX` / `GESTURE_BAR_PX`):
ham ekran görüntüsü gerçek bildirim ikonlarını ve pil yüzdesini sızdırıyordu.
Samsung OneUI `sysui_demo` modunu engellediği için temiz durum çubuğu üretilemiyor.

`icon-512.png`, `ic_launcher_foreground.xml`'deki halter geometrisinin birebir
kopyası — ikon değişirse `app_icon()` içindeki koordinatlar da değişmeli.

Ekranlar değişince: `store/src/` altına yeniden çek, sonra betiği çalıştır.

_Bilinen kusur: Beslenme ekranındaki yemek adları Türkçe (Pankek, Yoğurt, Zeytin) —
kayıtlar Türkçe arayüzdeyken girilmişti. İngilizce arayüzde bir öğün kaydedip
o ekranı yeniden çekmek gerekiyor._

### Gelişmiş takip / saat verisi (İSTENDİ — YAPILMADI)
Kullanıcı "advanced tracking" anahtarı ve saatten veri toplama istedi. Yapılmadı çünkü
tek başına bir anahtar değil, ayrı bir dikey dilim:
1. **Health Connect** bağımlılığı + okuma izinleri (adım, aktif kalori, nabız, uyku).
   Play, Health Connect izinleri için ayrı bir form onayı istiyor.
2. Yeni veri türleri = gizlilik politikası + KVKK aydınlatma metni + Data Safety
   formu güncellemesi. Şu anki metinlerde saat/sensör verisi geçmiyor.
3. Bu veriler de özel nitelikli — **ayrı açık rıza** gerekiyor, mevcut rızanın kapsamında değil.
4. DB tarafında günlük özet tablosu (adım, aktif kalori) + TDEE'yi bunlarla düzeltme kararı.
Yarım bir anahtar koymak ("aç ama hiçbir şey toplamıyor") kullanıcıyı yanıltırdı.

### FAZ 4 — Ertelenenler
- [ ] Kamerayla canlı form kontrolü (MediaPipe) — doğruluk/destek yükü riski var
- [ ] Wearable: önce Health Connect (Android), Apple Health iOS'a kadar bekler
- [ ] Uyku takibi
- [ ] Supplement rehberi (sağlık iddiası uyumluluk riski — dikkatli)
- [ ] Sosyal / yarışma özellikleri (ancak ölçekte değerli)

#### Upgrade girişleri
Ana sayfa (üstte, koç kartının hemen üzerinde) ve Beslenme (tarama hakkı 2 veya altına
düşünce) — `ui/components/UpgradeCard.kt`. Premium kullanıcıda hiç çizilmiyor.
Antrenman ve Egzersizler sekmesine bilerek KONMADI: günlük plan ücretsiz, orada
premium vurgusu yapmak yanlış beyan olurdu (`upsell_workout` / `upsell_library`
dizeleri şimdilik kullanılmıyor).

### Premium limitleri (sunucuda)
| | Ücretsiz | Premium |
|---|---|---|
| Fotoğraf taraması / gün | 5 | 40 |
| Koç brief yenileme / gün | 2 | 10 |

Limitler Edge Function içinde `is_premium()` sonucuna göre seçiliyor; istemci söylemiyor.

### Reklam kararı (17 Ağu 2026 — BEKLEMEDE)
Ücretsiz katmanda hafif reklam istendi. Kodlanmadı, çünkü tek başına teknik bir iş değil:
1. Gizlilik politikası şu an **"reklam yok, reklam kimliği toplanmıyor"** diyor —
   reklam eklenirse metin ve Play Data Safety formu değişmek zorunda.
2. GDPR/KVKK için **UMP (User Messaging Platform) rıza akışı** şart.
3. Play sağlık verisini reklam hedeflemede kullanmayı yasaklıyor — reklam SDK'sına
   hiçbir sağlık sinyali gitmemeli.
4. Yaş 16+ olduğu için çocuk kitle kuralları devrede değil ama yaş beyanı tutarlı olmalı.
Önerilen minimum: tek yerleşim (Egzersizler listesinin altında banner), interstitial YOK,
premium'da tamamen kapalı. Karar kullanıcıya bırakıldı.

### YAPILMAYACAKLAR
- Çekirdek döngü retention sağlamadan Faz 3-4'e geçmek
- Teşhis, tedavi veya doğrulanamayan sensör iddiaları (store reddi sebebi)
- Sağlık verisini reklam için kullanmak (Apple/Google açık yasak)

---

## 4. Yasal ve uyumluluk — kod yazmadan önce hallolması gerekenler

### Hesap
- Geliştirici Türkiye'de reşit değil. Google Play 18+ şartı koyuyor,
  TMK Md. 16 gereği reşit olmayanın sözleşmesi bağlayıcı değil.
- **Play Console hesabı, payments profili ve banka hesabı ebeveyn adına.** ✅ (hallolmuş)
- Gelir düzenli hale gelince şahıs şirketi de ebeveyn adına açılır.
  Yurt dışı kullanıcı geliri hizmet ihracı → KDV %0, KVK 10/1-ğ ile %80'e kadar indirim.

### KVKK (henüz yapılmadı)
- **Vücut fotoğrafı ve sağlık metrikleri "özel nitelikli kişisel veri"** (KVKK Md. 6).
- **Açık rıza şart** — genel kullanım koşulu yeterli değil, ayrı ve spesifik olmalı.
- Md. 10 aydınlatma metni gerekli.
- **VERBİS kaydı muhtemelen gerekli** — küçük ölçek muafiyeti, özel nitelikli veri
  işlemek ana faaliyet olduğunda uygulanmıyor. Avukata sorulacak.
- Vücut fotoğrafını mümkün olduğunca **cihazda işle**, minimum sakla, şifrele,
  hesap/veri silme seçeneği sun.

### Store reddi önlemleri
- Her yerde **wellness/eğitim** çerçevesi, **medikal değil**.
- Görünür uyarı: "Tıbbi tavsiye değildir, doktorunuza danışın."
- Gizlilik politikası hem uygulama içinde hem URL olarak erişilebilir olmalı (zorunlu).
- Data Safety formu doğru doldurulmalı, hesap silme sunulmalı.
- Yaş grubu 13+ (ya da 16+) olarak ayarla — COPPA/Families karmaşasından kaçın.

---

## 5. Para kazanma

- **Global/İngilizce öncelikli, fiyat ülkeye göre lokalize.**
  Türkiye fiyatları ABD'nin ~%71 altında — Türkiye gelir kaynağı değil,
  geri bildirim ve organik büyüme üssü.
- Freemium + onboarding paywall + ücretsiz deneme.
  Deneme süresi **14+ gün** test edilecek (7 günden belirgin daha iyi dönüşüyor).
- **Yıllık plan öne çıkarılır**, aylık yedek, haftalık düşük gelirli pazarlar için.
- Ücretsiz katmanda **AI tarama limiti** olacak — maliyet kontrolü için zorunlu.
- Hedef metrikler: deneme başlatma %12+, denemeden ödemeye %35+.
- Fitness kategorisinde churn en yüksek (aylık ~%9,2) → onboarding'de
  **hızlı bir "ilk kazanım"** yaşatmak retention için kritik.

### Maliyet beklentisi
| Kullanıcı | Aylık maliyet |
|---|---|
| 100 | ~$0 |
| 1.000 | ~$25–75 |
| 10.000 | ~$150–400 |

Ana değişken: kullanıcı başına vision AI maliyeti. Tarama limitiyle kontrol edilir.

---

## 6. Gerçekçi beklenti

Abonelik uygulamalarının %57,7'si toplamda $1.000'ı geçemiyor; medyan ~$492/ay.
İyi giderse 12–18 ayda $3.000–15.000/ay mümkün. Cal AI ($30M+, iki lise öğrencisi)
istisna — ve zirvede ayda ~$770.000 reklam harcıyorlardı. Onların moat'ı
teknoloji değil dağıtımdı.

**Başarısızlığın ana sebebi özellik eksikliği değil, dağıtım ve güncelleme
sürekliliğinin olmaması.** (Fitwell: 1M indirme, güncellenmedi, 2025'te Play'den kaldırıldı.)

---

## 7. Pazarlama (sıfır bütçe)

- Kendi çektiğin kısa video: TikTok / Reels / Shorts, **Türkçe + İngilizce**
  (alet anlatımları, yeni başlayan ipuçları, dönüşümler). En yüksek ROI'li kanal.
- ASO: niyet odaklı anahtar kelimeler ("beginner gym workout", "AI kalori sayacı",
  "gym aleti nasıl kullanılır"). Google Play tam açıklamayı indeksliyor.
- Mikro-influencer'a ücretsiz üyelik karşılığı paylaşım (10K–100K takipçi).
- Organik bir format tuttuktan SONRA küçük bütçeyle boost.

---

## 8. Sıradaki adım

Yapıldı: isim (Repzy), Supabase şeması + RLS + RPC, proje iskeleti, auth, onboarding,
`BodyMath` hesap motoru (19 birim testi geçiyor). Ayrıntı: [README.md](README.md).

Migration 0001–0008 canlı projeye uygulandı. Beş sekme (Ana sayfa, Antrenman, Beslenme,
Egzersizler, Ayarlar) **gerçek cihazda uçtan uca doğrulandı** (Galaxy A35, Android 16):
antrenman seansı force-stop'tan sonra kaldığı yerden döndü; kullanıcının çektiği yemek
fotoğrafı iki kalem olarak dönüp kaydedildi; koç brief'i üretildi, veri değişince
tavsiyesi değişti, limit dolunca AI'ya gitmedi; Ayarlar'da yeniden hesap onboarding'le
birebir aynı sayıları verdi ve `nutrition_targets` satırını üzerine yazdı.
28 birim testi geçiyor.

1. **Yasal metinleri yayına al** — metinler yazıldı (`docs/`: gizlilik.html, privacy.html,
   kvkk-aydinlatma.html, hesap-silme.html, delete-account.html, index.html).
   Ad, adres ve iletişim e-postası dolduruldu. `core/Legal.kt` içindeki `BASE` artık
   gerçek adres: `https://fecstudios.github.io/repzy/` (depo `FECstudios/repzy`).
   ~~GitHub Pages'i aç~~ **YAPILDI** — `https://fecstudios.github.io/repzy/` canlı,
   dört sayfa da (gizlilik/privacy/hesap-silme/delete-account) 200 dönüyor.
   Kalan iş: (a) VERBİS kayıt numarasını `kvkk-aydinlatma.html` içine yazmak,
   (b) Play Console'a gizlilik politikası (`.../gizlilik.html`) + veri silme
   (`.../hesap-silme.html`) URL'lerini girmek.
   **Metinler avukat kontrolünden geçmeden yayına çıkarılmamalı.**
2. ~~Gizlilik/KVKK metinlerini üç yeni veri akışıyla güncelle~~ **YAPILDI (18 Ağu 2026)**
   — `gizlilik.html`, `privacy.html`, `kvkk-aydinlatma.html` üçü de güncellendi:
   Health Connect'ten okunan adım/aktif kalori/antrenman süresi (ve bunun koç brief'inde
   **AI'ya gönderildiği** açıkça yazıyor), Google girişinden gelen ad + e-posta,
   vücut fotoğrafı saklama. KVKK metnindeki "rızanın geri çekilmesi" bölümü de düzeltildi:
   artık **üç ayrı rıza** var ve ikisi (gelişmiş takip, fotoğraflar) hesap silmeden
   geri çekilebiliyor — eski metin hepsini hesap silmeye bağlıyordu, bu yanlıştı.
3. **Ham hata metni kullanıcıya gösteriliyor** (18 Ağu 2026'da cihazda görüldü).
   Koç yenilemesi `ai_failed` dönünce kart, Ktor'un istisna metnini olduğu gibi bastı:
   gövde + `URL:` + `Headers: {Authorization=[Bearer ey... (len=1370)], apikey=[sb...]}`.
   Sebep: `functions.invoke` non-2xx'te kendi istisnasını fırlatıyor, `CoachRepository`
   içindeki `status != OK` dalına hiç girilmiyor; `HomeViewModel` de `e.message`'ı
   doğrudan `briefError`'a yazıyor. Token kısaltılmış hâlde ama ham istek dökümünü
   arayüzde göstermek yanlış. Aynı kalıp `NutritionViewModel`'de de var (`error = e.message`).
   Yapılacak: istisnayı yakalayıp gövdeyi ayrıştır, kullanıcıya çevrilmiş mesaj göster.
4. **Egzersiz görselleri/animasyonları** — `image_url` / `animation_url` kolonları boş.
5. **Koç kartını eyleme bağla** — brief'teki eylemler şu an sadece metin; su/antrenman
   kartına tıklamayla gitmek ya da "yaptım" işaretlemek retention için değerli olur.
6. **Vücut yağını elle girme** — şu an sadece Navy hesabı var, kaliper/tartı değeri
   girilemiyor (`body_fat_source` enum'u hazır).
7. **Baseline Profile** — açılışın ilk 660 ms'si dex doğrulaması.

### Canlı Supabase durumu (18 Ağu 2026)
Migration 0001–0008 uygulandı. Edge Function'lar yayında: `analyze-meal`, `daily-brief`.
Secret'lar: `AI_API_KEY` (Hack Club), varsayılanlar kodda (`google/gemini-2.5-flash`,
günde 5 tarama, 2 brief yenileme).
Sağlayıcılar: e-posta **ve Google** açık. Redirect URLs'te `repzy://login-callback` var.
Deploy komutu (Docker gerekmez):
`npx supabase functions deploy <isim> --project-ref rngtgvnllrasrmlskzaw --use-api`


### Release derlemesi doğrulandı (19 Ağu 2026)
İmzalı release APK (R8 + shrinkResources açık) Galaxy A35'te çalıştırıldı:
açılış, Hilt, Compose, **Supabase postgrest + kotlinx.serialization** ve Play Billing
bağlantısı sorunsuz. Dex'te 22 `$$serializer` sınıfının hepsi duruyor —
`proguard-rules.pro` yeterli. Yükleme anahtarı `E:/projects/repzy-upload.jks`
(local.properties'ten okunuyor, repoya girmiyor); upload key SHA-1:
`9E:DB:75:D5:70:7E:EF:F2:E9:47:16:B9:54:2A:DB:8A:72:E0:E6:64` — Google Cloud'a
app signing SHA-1'inin YANINA eklenmeli, yoksa yerel release derlemesinde
Google girişi çalışmaz.

**Açık kalan hata: koç brief'i `kuzey` hesabında hata veriyor, diğer hesaplarda
çalışıyor.** Hesaba özgü olması R8'i eleyip sunucu tarafını işaret ediyor —
muhtemelen `coach_context()` o hesabın verisinde beklenmedik bir şey döndürüyor
ya da AI cevabı ayrıştırılamıyor. İstemci hatayı yutuyor (`AiFailure.Other("network")`),
o yüzden gerçek sebep görünmüyor; teşhis için `daily-brief` Edge Function log'larına
bakmak gerekiyor. Ertelendi.

### verify-purchase (19 Ağu 2026)
`supabase/functions/verify-purchase/index.ts` yazıldı — premium yetkisini açan tek yol.
Servis hesabı JWT'siyle Google'dan erişim token'ı alıp Play Developer API'sinin
`subscriptionsv2` uç noktasına soruyor, sonucu **service_role ile** `subscriptions`
tablosuna yazıyor (kullanıcının insert politikası yok, yazamaz).

İki güvenlik ayrıntısı:
- Satın alma `obfuscatedAccountId` ile hesaba bağlanıyor (`BillingRepository`), sunucu
  da aynı token başka bir `user_id`'ye yazılıysa 409 dönüyor. Fişin ikinci hesapta
  kullanılmasını iki yerden engelliyoruz.
- `SUBSCRIPTION_STATE_CANCELED` erişim VERİYOR: Play'de iptal = otomatik yenileme
  kapatıldı, kullanıcı ödediği dönemin sonuna kadar premium. Migration 0010
  `is_premium()`'a 'canceled' ekliyor — 0009'daki hâli parasını ödemiş kullanıcıyı
  iptale bastığı anda ücretsiz katmana düşürüyordu.

Gereken secret'lar: `GOOGLE_SERVICE_ACCOUNT_JSON` (Play Console'a bağlı servis hesabı),
`ANDROID_PACKAGE_NAME` (varsayılan com.repzy.app). Deploy edilmedi, cihazda denenmedi.

**Migration 0009 (`subscriptions` + `is_premium()`) canlıda YOK** — REST `PGRST205` dönüyor.
Edge Function'lar `const { data: premium } = await supabase.rpc("is_premium")` yazdığı için
hata yutuluyor, `premium` null kalıyor ve herkes ücretsiz limitlere düşüyor ("fail closed",
çökme yok). Yine de uygulanmalı; yoksa her AI çağrısında boşa bir RPC turu gidiyor.

### Açılış performansı (17 Ağustos 2026 ölçümü, debug build)
`adb logcat -s RepzyPerf` ile ölçülüyor (`core/Perf.kt`, sadece debug'da çalışır).
İçerik ekrana ~2,6 s'de geliyor: 660 ms süreç/dex, ~300 ms splash+setContent,
~430 ms ilk kompozisyon, ~1,0 s ilk ağ turu (TLS el sıkışması dahil).
Yapılanlar: Home'un 5 sorgusu paralel, profil sorgusu bellekte önbellekli (açılışta iki kez
istenmiyor), cihazda `profile_ready` işareti varsa Home profil cevabını beklemiyor,
Supabase istemcisi Application içinde arka planda ısıtılıyor.
Sıradaki kaldıraç **Baseline Profile** — ilk 660 ms'nin büyük kısmı dex doğrulaması.

### Bu makineye özel build notu
Avast'ın HTTPS taraması TLS'i kendi kök sertifikasıyla imzalıyor; JDK bunu tanımadığı için
Gradle ve `sdkmanager` indirme yapamıyordu (`PKIX path building failed`).
Çözüm: `~/.gradle/mitm-cacerts.jks` (JDK cacerts + Avast kökü) ve
`~/.gradle/gradle.properties` içinde `systemProp.javax.net.ssl.trustStore`.
Android Studio kendi JBR'sini kullanırsa aynı ayar orada da gerekir.

---

## 9. Çalışma tarzı

- Kullanıcı Türkçe konuşuyor, Türkçe/İngilizce karışık. Yanıtlar Türkçe.
- Doğrudan ve kısa. Uzun açıklama yerine çalışan kod.
- Her seferinde ince bir dikey dilim bitir — uçtan uca çalışsın, sonra genişlet.
- Bir özellik bitince bu dosyadaki checkbox'ı işaretle.