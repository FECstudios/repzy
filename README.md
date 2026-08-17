# Repzy

Türkçe/İngilizce, antrenman + beslenmeyi birlikte yöneten Android koç uygulaması.
Ürün kararları ve yol haritası için [CLAUDE.md](CLAUDE.md).

## Yığın

| Katman | Seçim |
|---|---|
| İstemci | Android, Kotlin + Jetpack Compose (Material 3) |
| DI | Hilt |
| Backend | Supabase — Auth + Postgres + Storage |
| Serileştirme | kotlinx.serialization |
| Build | Gradle 9.7 + AGP 9.3 + Kotlin 2.3 |
| minSdk / targetSdk | 26 / 36 |

## Kurulum

1. **Supabase şeması.** Supabase panelinde SQL Editor'ı aç, `supabase/migrations/`
   altındaki dosyaları sırayla çalıştır:
   - `0001_init.sql` — tablolar, enum'lar, RLS politikaları, `complete_onboarding` RPC'si
   - `0002_storage.sql` — `body-photos` / `food-photos` bucket'ları ve erişim politikaları
   - `0003_seed_exercises.sql` — başlangıç egzersiz kütüphanesi (16 hareket + alternatifleri)
   - `0004_consent_timestamp.sql` — rıza zamanı istemciden alınır (onboarding hesap öncesi)
   - `0005_streak.sql` — `current_streak()` fonksiyonu

2. **`local.properties`** (repoya girmez) şu iki satırı içermeli:

   ```properties
   SUPABASE_URL=https://<proje-ref>.supabase.co
   SUPABASE_ANON_KEY=<publishable-key>
   ```

   Buraya **service/secret key yazılmaz.** Secret key sadece Edge Function tarafında durur.

3. Derle:

   ```bash
   ./gradlew :app:assembleDebug
   ```

## Şu an çalışan akış

Onboarding **hesap açılmadan önce** çalışır — kullanıcı planını görmeden kayıt istenmez.

```
Cihazda kayıtlı oturum varsa → doğrudan uygulama (onboarding hiç görünmez)

İlk açılış → Onboarding (hesap YOK, cevaplar DataStore'da)
   ilk adımda "Hesabın var mı? Oturum aç" → giriş formu (geri dönülebilir)
   açık rıza → isim → cinsiyet/doğum yılı → boy/kilo → hedef →
   deneyim → ekipman → aktivite → çevre ölçüleri → "planın kuruluyor" → özet
   └─ "Planımı kaydet" → Auth ("Planını kaydet" çerçevesi)
        └─ hesap açılınca taslak tek RPC ile yazılır → uygulama
Uygulama (alt sekmeler)
   ├─ Ana sayfa: streak, su kartı (+200/+330/+500 ml, geri al), kalori/makro/BMI
   └─ Egzersizler: arama + ortam/seviye/kas filtreleri → detay
        (numaralı form anlatımı, sık hatalar, salon↔ev alternatifi)
```

Yönlendirmenin tek karar noktası [RootViewModel](app/src/main/java/com/repzy/app/ui/RootViewModel.kt):
oturum durumu + cihazdaki taslağın bileşimi. E-posta doğrulaması beklenirken taslak
cihazda durur, giriş yapılınca yazılır.

## Hesaplamalar

`app/src/main/java/com/repzy/app/core/BodyMath.kt` — hepsi kural tabanlı ve
deterministik, AI yok. Testleri: `app/src/test/java/com/repzy/app/core/BodyMathTest.kt`.

- BMI: kilo/boydan türetilir, veritabanında saklanmaz
- Vücut yağı: US Navy çevre ölçümü yöntemi (cihaz gerekmez)
- Kalori: Mifflin-St Jeor BMR → aktivite katsayısı → hedefe göre açık/fazla
- Güvenlik tabanı: hedef kalori asla BMR'ın veya cinsiyet bazlı alt sınırın altına inmez
- Makro: protein ve yağ g/kg, karbonhidrat kalan kaloriden; yağ 0.4 g/kg altına inmez
- Protein katsayıları kanıta dayalı: ISSN pozisyon bildirisi 1.4–2.0 g/kg (kalori
  açığında 2.3–3.1), Morton 2018 meta-analizi ~1.6 g/kg platosu ama güven aralığı
  1.03–2.20 → pratik konsensüs 1.6–2.2. Seçilen: kas 2.0, yağ yakma 2.2, diğer 1.6.

Özet ekranındaki sayı ile veritabanına yazılan sayı aynı fonksiyondan gelir
([PlanCalculator.kt](app/src/main/java/com/repzy/app/core/PlanCalculator.kt)).

## Bu makinede build notu

Avast'ın HTTPS taraması TLS'i kendi kök sertifikasıyla imzalıyor, JDK bunu tanımadığı
için Gradle bağımlılık indirirken `PKIX path building failed` veriyordu. Çözüm
`~/.gradle/gradle.properties` içinde: JDK cacerts + Avast kök sertifikasını içeren
ayrı bir truststore gösteriliyor. Android Studio kendi JDK'sını (JBR) kullanırsa
aynı ayarı orada da yapmak gerekir — ya da Avast'ta HTTPS taramasını kapatmak.

## Yapılmadı / sırada

- KVKK aydınlatma metni + gizlilik politikası URL'i (kod içinde yer tutucu yok, eklenmeli)
- Egzersiz kütüphanesi ekranı, antrenman kaydı, su takibi, streak
- Fotoğraftan kalori tahmini → Supabase Edge Function (API key ASLA APK içinde durmaz)
- Vücut fotoğrafı akışı — ayrı açık rıza ekranı şart
