package com.repzy.app.data.local

import com.repzy.app.data.model.ActivityLevel
import com.repzy.app.data.model.EquipmentAccess
import com.repzy.app.data.model.ExperienceLevel
import com.repzy.app.data.model.Goal
import com.repzy.app.data.model.Sex
import kotlinx.serialization.Serializable

/**
 * Onboarding cevapları — hesap AÇILMADAN ÖNCE toplanır ve sadece cihazda tutulur.
 * Sunucuya ancak kullanıcı hesap oluşturduğunda tek seferde yazılır.
 *
 * KVKK notu: sağlık verisi hesap oluşana kadar cihazdan çıkmıyor; rıza zaman damgası
 * [consentAtIso] ile kullanıcının onay verdiği an olarak kaydedilir, kayıt anı değil.
 *
 * Sayısal alanlar ham metin olarak tutuluyor ki kullanıcı geri döndüğünde
 * yazdığı şey birebir aynı görünsün ("70" vs "70.0").
 */
@Serializable
data class OnboardingDraft(
    val healthConsent: Boolean = false,
    val consentAtIso: String? = null,
    val name: String = "",
    val sex: Sex? = null,
    val birthYear: String = "",
    val heightCm: String = "",
    val weightKg: String = "",
    val goal: Goal? = null,
    val experience: ExperienceLevel? = null,
    val equipment: EquipmentAccess? = null,
    val activity: ActivityLevel? = null,
    val neckCm: String = "",
    val waistCm: String = "",
    val hipCm: String = "",
    /** Kullanıcı özet ekranını görüp "planımı kaydet"e bastı — artık hesap bekliyor. */
    val completed: Boolean = false,
) {
    val heightValue: Double? get() = heightCm.toDoubleOrNull()?.takeIf { it in 100.0..250.0 }
    val weightValue: Double? get() = weightKg.toDoubleOrNull()?.takeIf { it in 30.0..300.0 }
    val birthYearValue: Int? get() = birthYear.toIntOrNull()?.takeIf { it in 1920..2020 }
    val neckValue: Double? get() = neckCm.toDoubleOrNull()?.takeIf { it in 20.0..80.0 }
    val waistValue: Double? get() = waistCm.toDoubleOrNull()?.takeIf { it in 40.0..200.0 }
    val hipValue: Double? get() = hipCm.toDoubleOrNull()?.takeIf { it in 50.0..200.0 }

    /** Plan hesaplanabilmesi için gereken minimum set. */
    val hasRequiredFields: Boolean
        get() = healthConsent &&
            sex != null &&
            goal != null &&
            activity != null &&
            experience != null &&
            equipment != null &&
            heightValue != null &&
            weightValue != null &&
            birthYearValue != null
}
