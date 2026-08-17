package com.repzy.app.core

import com.repzy.app.data.model.ActivityLevel
import com.repzy.app.data.model.Goal
import com.repzy.app.data.model.Sex
import kotlinx.datetime.LocalDate
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Kural tabanlı vücut/beslenme hesapları. AI yok — deterministik, test edilebilir.
 * Hepsi wellness tahmini; tıbbi ölçüm değil.
 */
object BodyMath {

    /** Ücretsiz kullanıcıya bile gösterilecek en düşük güvenli kalori tabanı. */
    private const val MIN_CALORIES_MALE = 1500
    private const val MIN_CALORIES_FEMALE = 1200

    /** Sadece doğum yılı tutulduğu için yaş yıl farkı olarak hesaplanır (±1 yıl sapma kabul). */
    fun age(birthYear: Int, today: LocalDate): Int = (today.year - birthYear).coerceIn(0, 120)

    /** kg / m² */
    fun bmi(weightKg: Double, heightCm: Double): Double? {
        if (weightKg <= 0 || heightCm <= 0) return null
        val m = heightCm / 100.0
        return weightKg / (m * m)
    }

    enum class BmiBand { UNDERWEIGHT, NORMAL, OVERWEIGHT, OBESE }

    fun bmiBand(bmi: Double): BmiBand = when {
        bmi < 18.5 -> BmiBand.UNDERWEIGHT
        bmi < 25.0 -> BmiBand.NORMAL
        bmi < 30.0 -> BmiBand.OVERWEIGHT
        else -> BmiBand.OBESE
    }

    /**
     * US Navy çevre ölçümü yöntemi — cihaz gerekmez, ±3-4% hata payı.
     * Kadın için kalça ölçüsü zorunlu. Geçersiz ölçümde null döner.
     */
    fun navyBodyFatPct(
        sex: Sex,
        heightCm: Double,
        neckCm: Double,
        waistCm: Double,
        hipCm: Double? = null,
    ): Double? {
        if (heightCm <= 0 || neckCm <= 0 || waistCm <= 0) return null
        val pct = when (sex) {
            Sex.MALE -> {
                val girth = waistCm - neckCm
                if (girth <= 0) return null
                495.0 / (1.0324 - 0.19077 * log10(girth) + 0.15456 * log10(heightCm)) - 450.0
            }

            Sex.FEMALE -> {
                val hip = hipCm ?: return null
                if (hip <= 0) return null
                val girth = waistCm + hip - neckCm
                if (girth <= 0) return null
                495.0 / (1.29579 - 0.35004 * log10(girth) + 0.22100 * log10(heightCm)) - 450.0
            }
        }
        return if (pct.isFinite() && pct in 3.0..70.0) (pct * 10).roundToInt() / 10.0 else null
    }

    /** Mifflin-St Jeor — bazal metabolizma (kcal/gün). */
    fun bmr(sex: Sex, weightKg: Double, heightCm: Double, ageYears: Int): Int {
        val base = 10 * weightKg + 6.25 * heightCm - 5 * ageYears
        val adjusted = when (sex) {
            Sex.MALE -> base + 5
            Sex.FEMALE -> base - 161
        }
        return adjusted.roundToInt().coerceAtLeast(800)
    }

    fun tdee(bmr: Int, activityLevel: ActivityLevel): Int =
        (bmr * activityLevel.factor).roundToInt()

    /** Hedefe göre günlük kalori. Alt sınır BMR'ın altına inmez. */
    fun calorieTarget(goal: Goal, tdee: Int, bmr: Int, sex: Sex): Int {
        val raw = when (goal) {
            Goal.LOSE_FAT -> tdee * 0.80
            Goal.BUILD_MUSCLE -> tdee * 1.10
            Goal.ENDURANCE -> tdee * 1.05
            Goal.GENERAL_FITNESS -> tdee.toDouble()
        }
        val floor = max(
            bmr.toDouble(),
            if (sex == Sex.MALE) MIN_CALORIES_MALE.toDouble() else MIN_CALORIES_FEMALE.toDouble(),
        )
        // 25 kcal'e yuvarla — hedef "1847" değil "1850" gibi görünsün.
        return (max(raw, floor) / 25.0).roundToInt() * 25
    }

    data class Macros(val proteinG: Int, val carbsG: Int, val fatG: Int)

    /**
     * Protein ve yağ vücut ağırlığından (g/kg) belirlenir, karbonhidrat kalan kaloriden.
     * Karbonhidrat 50 g altına düşerse yağ kısılır — protein hiç kısılmaz.
     *
     * Protein katsayıları kanıta dayalı aralıkların içinde:
     * - ISSN pozisyon bildirisi: kas kütlesi için 1.4–2.0 g/kg/gün, kalori açığında
     *   yağsız kütleyi korumak için 2.3–3.1 g/kg.
     * - Morton 2018 meta-analizi: kazanç ~1.6 g/kg'da platoluyor, ancak kırılma
     *   noktasının güven aralığı 1.03–2.20 → pratik konsensüs 1.6–2.2 g/kg.
     * Üst banda yakın durmak sağlıklı böbrek fonksiyonunda risksiz, eksik proteinden
     * kaynaklanan kayıp ise geri alınamaz — bu yüzden aralığın üst yarısı seçildi.
     */
    fun macros(goal: Goal, calories: Int, weightKg: Double): Macros {
        val (proteinPerKg, fatPerKg) = when (goal) {
            Goal.LOSE_FAT -> 2.2 to 0.8
            Goal.BUILD_MUSCLE -> 2.0 to 0.9
            Goal.ENDURANCE -> 1.6 to 0.8
            Goal.GENERAL_FITNESS -> 1.6 to 0.9
        }
        val protein = (weightKg * proteinPerKg).roundToInt()
        var fat = (weightKg * fatPerKg).roundToInt()
        var carbs = ((calories - protein * 4 - fat * 9) / 4.0).roundToInt()

        if (carbs < 50) {
            val deficitKcal = (50 - carbs) * 4
            val fatCut = (deficitKcal / 9.0).roundToInt()
            // Yağ 0.4 g/kg altına inmez (hormon sağlığı).
            val minFat = (weightKg * 0.4).roundToInt()
            fat = (fat - fatCut).coerceAtLeast(minFat)
            carbs = ((calories - protein * 4 - fat * 9) / 4.0).roundToInt().coerceAtLeast(0)
        }
        return Macros(proteinG = protein, carbsG = carbs, fatG = fat)
    }

    /** 35 ml/kg + aktiviteye göre ek. 50 ml'ye yuvarlanır. */
    fun waterTargetMl(weightKg: Double, activityLevel: ActivityLevel): Int {
        val bonus = when (activityLevel) {
            ActivityLevel.SEDENTARY -> 0
            ActivityLevel.LIGHT -> 150
            ActivityLevel.MODERATE -> 300
            ActivityLevel.ACTIVE -> 500
            ActivityLevel.VERY_ACTIVE -> 750
        }
        val raw = weightKg * 35 + bonus
        return (raw / 50.0).roundToInt() * 50
    }

    /** Haftalık gerçekçi kilo değişimi (kg). Negatif = kayıp, pozitif = alım. */
    fun weeklyWeightChangeKg(calorieTarget: Int, tdee: Int): Double {
        val weeklyDelta = (calorieTarget - tdee) * 7
        val perWeek = weeklyDelta / 7700.0 // ~7700 kcal ≈ 1 kg yağ
        return (perWeek * 100).roundToInt() / 100.0
    }
}
