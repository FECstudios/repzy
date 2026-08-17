package com.repzy.app.core

import com.repzy.app.data.model.ActivityLevel
import com.repzy.app.data.model.Goal
import com.repzy.app.data.model.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyMathTest {

    @Test
    fun `bmi hesabi bilinen degerle uyusur`() {
        val bmi = BodyMath.bmi(weightKg = 80.0, heightCm = 180.0)!!
        assertEquals(24.69, bmi, 0.01)
        assertEquals(BodyMath.BmiBand.NORMAL, BodyMath.bmiBand(bmi))
    }

    @Test
    fun `gecersiz olcu null doner`() {
        assertNull(BodyMath.bmi(0.0, 180.0))
        assertNull(BodyMath.bmi(80.0, 0.0))
    }

    @Test
    fun `navy yontemi erkek icin makul aralikta sonuc verir`() {
        val pct = BodyMath.navyBodyFatPct(
            sex = Sex.MALE,
            heightCm = 180.0,
            neckCm = 38.0,
            waistCm = 90.0,
        )
        assertNotNull(pct)
        assertTrue("beklenmeyen deger: $pct", pct!! in 15.0..25.0)
    }

    @Test
    fun `navy yontemi kadin icin kalca olcusu olmadan hesaplanamaz`() {
        assertNull(
            BodyMath.navyBodyFatPct(
                sex = Sex.FEMALE,
                heightCm = 165.0,
                neckCm = 32.0,
                waistCm = 74.0,
                hipCm = null,
            ),
        )
    }

    @Test
    fun `bel boyundan kucukse navy hesabi yapilmaz`() {
        assertNull(
            BodyMath.navyBodyFatPct(
                sex = Sex.MALE,
                heightCm = 180.0,
                neckCm = 40.0,
                waistCm = 38.0,
            ),
        )
    }

    @Test
    fun `mifflin st jeor bilinen degeri uretir`() {
        // 80 kg, 180 cm, 30 yas erkek: 10*80 + 6.25*180 - 5*30 + 5 = 1780
        assertEquals(1780, BodyMath.bmr(Sex.MALE, 80.0, 180.0, 30))
        // ayni olculerde kadin: 1775 - 161 = 1614
        assertEquals(1614, BodyMath.bmr(Sex.FEMALE, 80.0, 180.0, 30))
    }

    @Test
    fun `yag yakma hedefi tdee nin altinda ama bmr in altinda degil`() {
        val bmr = BodyMath.bmr(Sex.MALE, 80.0, 180.0, 30)
        val tdee = BodyMath.tdee(bmr, ActivityLevel.MODERATE)
        val target = BodyMath.calorieTarget(Goal.LOSE_FAT, tdee, bmr, Sex.MALE)

        assertTrue("hedef tdee altinda olmali", target < tdee)
        assertTrue("hedef bmr altina inmemeli", target >= bmr)
    }

    @Test
    fun `cok dusuk tdee de kalori tabani korunur`() {
        // Kucuk vucutlu, hareketsiz kullanicida %20 acik BMR altina inerdi; taban devreye girer.
        val bmr = BodyMath.bmr(Sex.FEMALE, 48.0, 155.0, 25)
        val tdee = BodyMath.tdee(bmr, ActivityLevel.SEDENTARY)
        val target = BodyMath.calorieTarget(Goal.LOSE_FAT, tdee, bmr, Sex.FEMALE)

        assertTrue("kalori tabani asildi: $target < $bmr", target >= bmr)
        assertTrue("kadin icin 1200 kcal altina inilmemeli", target >= 1200)
    }

    @Test
    fun `kas yapma hedefi tdee uzerinde`() {
        val bmr = BodyMath.bmr(Sex.MALE, 70.0, 175.0, 22)
        val tdee = BodyMath.tdee(bmr, ActivityLevel.ACTIVE)
        assertTrue(BodyMath.calorieTarget(Goal.BUILD_MUSCLE, tdee, bmr, Sex.MALE) > tdee)
    }

    @Test
    fun `makrolar kalori toplamini yaklasik tutar`() {
        val calories = 2200
        val macros = BodyMath.macros(Goal.BUILD_MUSCLE, calories, 75.0)
        val sum = macros.proteinG * 4 + macros.carbsG * 4 + macros.fatG * 9

        assertTrue("makro toplami $sum, hedef $calories", kotlin.math.abs(sum - calories) <= 20)
        assertTrue(macros.proteinG > 0 && macros.carbsG > 0 && macros.fatG > 0)
    }

    @Test
    fun `dusuk kaloride karbonhidrat taban altina dusmez yag kisilir`() {
        // Agir kullanici + dusuk kalori: protein+yag tek basina kaloriyi doldurur.
        val macros = BodyMath.macros(Goal.LOSE_FAT, calories = 1500, weightKg = 120.0)

        assertTrue("karbonhidrat negatif olmamali: ${macros.carbsG}", macros.carbsG >= 0)
        assertTrue("yag hormonal taban altina inmemeli", macros.fatG >= (120 * 0.4).toInt())
    }

    @Test
    fun `protein hedefi kanita dayali araligin icinde kalir`() {
        // ISSN: kas icin 1.4-2.0 g/kg, kalori acigi icin 2.3-3.1 g/kg.
        // Pratik konsensus 1.6-2.2 g/kg → hicbir hedefte 1.5 alti veya 2.5 ustu olmamali.
        val weight = 57.0
        Goal.entries.forEach { goal ->
            val bmr = BodyMath.bmr(Sex.MALE, weight, 175.0, 17)
            val tdee = BodyMath.tdee(bmr, ActivityLevel.LIGHT)
            val calories = BodyMath.calorieTarget(goal, tdee, bmr, Sex.MALE)
            val perKg = BodyMath.macros(goal, calories, weight).proteinG / weight

            assertTrue("$goal icin protein cok dusuk: $perKg g/kg", perKg >= 1.5)
            assertTrue("$goal icin protein cok yuksek: $perKg g/kg", perKg <= 2.5)
        }
    }

    @Test
    fun `su hedefi kilo ve aktiviteyle artar`() {
        val low = BodyMath.waterTargetMl(70.0, ActivityLevel.SEDENTARY)
        val high = BodyMath.waterTargetMl(70.0, ActivityLevel.VERY_ACTIVE)
        val heavier = BodyMath.waterTargetMl(95.0, ActivityLevel.SEDENTARY)

        assertTrue(high > low)
        assertTrue(heavier > low)
        assertEquals(0, low % 50)
    }

    @Test
    fun `haftalik degisim acikta negatif fazlada pozitif`() {
        assertTrue(BodyMath.weeklyWeightChangeKg(calorieTarget = 2000, tdee = 2500) < 0)
        assertTrue(BodyMath.weeklyWeightChangeKg(calorieTarget = 2700, tdee = 2500) > 0)
        assertEquals(0.0, BodyMath.weeklyWeightChangeKg(2500, 2500), 0.001)
    }
}
