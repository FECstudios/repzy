package com.repzy.app.core

import com.repzy.app.data.local.OnboardingDraft
import com.repzy.app.data.model.ActivityLevel
import com.repzy.app.data.model.EquipmentAccess
import com.repzy.app.data.model.ExperienceLevel
import com.repzy.app.data.model.Goal
import com.repzy.app.data.model.Sex
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `planFor` (onboarding) ile `planFrom` (Ayarlar'daki yeniden hesapla) aynı çekirdeği
 * kullanmak zorunda: iki yerde farklı kalori çıkarsa kullanıcı hangi sayıya güveneceğini
 * bilemez. Bu testler o sözleşmeyi koruyor.
 */
class PlanCalculatorTest {

    private val today = LocalDate(2026, 8, 17)

    private val draft = OnboardingDraft(
        healthConsent = true,
        name = "Test",
        sex = Sex.MALE,
        birthYear = "2000",
        heightCm = "180",
        weightKg = "80",
        goal = Goal.BUILD_MUSCLE,
        experience = ExperienceLevel.BEGINNER,
        equipment = EquipmentAccess.GYM,
        activity = ActivityLevel.MODERATE,
        neckCm = "38",
        waistCm = "85",
    )

    @Test
    fun `planFor ve planFrom ayni sonucu verir`() {
        val fromDraft = planFor(draft, today)
        val fromFields = planFrom(
            sex = draft.sex,
            goal = draft.goal,
            activity = draft.activity,
            heightCm = draft.heightValue,
            weightKg = draft.weightValue,
            birthYear = draft.birthYearValue,
            neckCm = draft.neckValue,
            waistCm = draft.waistValue,
            hipCm = draft.hipValue,
            today = today,
        )

        assertNotNull(fromDraft)
        assertEquals(fromDraft, fromFields)
    }

    @Test
    fun `kilo artinca kalori hedefi artar`() {
        val lighter = planFor(draft.copy(weightKg = "70"), today)!!
        val heavier = planFor(draft.copy(weightKg = "90"), today)!!

        assertTrue(
            "Daha ağır kullanıcı daha yüksek kalori almalı",
            heavier.calories > lighter.calories,
        )
        assertTrue(
            "Protein hedefi kiloya bağlı, ağır kullanıcıda daha yüksek olmalı",
            heavier.macros.proteinG > lighter.macros.proteinG,
        )
    }

    @Test
    fun `hedef degisince kalori degisir`() {
        val muscle = planFor(draft.copy(goal = Goal.BUILD_MUSCLE), today)!!
        val fatLoss = planFor(draft.copy(goal = Goal.LOSE_FAT), today)!!

        assertTrue("Yağ yakma hedefi kas hedefinden az kalori vermeli", fatLoss.calories < muscle.calories)
    }

    @Test
    fun `zorunlu alan eksikse null doner`() {
        assertNull(planFor(draft.copy(weightKg = ""), today))
        assertNull(planFor(draft.copy(sex = null), today))
        assertNull(planFor(draft.copy(birthYear = ""), today))
        assertNull(planFor(draft.copy(activity = null), today))
    }

    @Test
    fun `cevre olculeri yoksa vucut yagi bos kalir ama plan uretilir`() {
        val plan = planFor(draft.copy(neckCm = "", waistCm = ""), today)

        assertNotNull(plan)
        assertNull(plan!!.bodyFatPct)
        assertNull(plan.bodyFatSource)
        assertTrue(plan.calories > 0)
    }
}
