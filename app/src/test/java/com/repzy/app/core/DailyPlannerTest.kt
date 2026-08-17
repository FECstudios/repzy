package com.repzy.app.core

import com.repzy.app.data.model.EquipmentAccess
import com.repzy.app.data.model.Exercise
import com.repzy.app.data.model.ExperienceLevel
import com.repzy.app.data.model.Goal
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyPlannerTest {

    private val today = LocalDate(2026, 8, 17)

    private fun exercise(
        id: String,
        muscle: String,
        setting: EquipmentAccess = EquipmentAccess.BOTH,
        level: ExperienceLevel = ExperienceLevel.BEGINNER,
        timeBased: Boolean = false,
    ) = Exercise(
        id = id,
        nameTr = id,
        nameEn = id,
        primaryMuscle = muscle,
        equipment = "bodyweight",
        setting = setting,
        level = level,
        isTimeBased = timeBased,
    )

    private val library = listOf(
        exercise("push", "chest"),
        exercise("row", "mid_back"),
        exercise("curl", "biceps"),
        exercise("press", "front_delts"),
        exercise("squat", "quadriceps"),
        exercise("hinge", "hamstrings"),
        exercise("bridge", "glutes"),
        exercise("plank", "core", timeBased = true),
        exercise("gym_machine", "chest", setting = EquipmentAccess.GYM),
        exercise("home_band", "lats", setting = EquipmentAccess.HOME),
    )

    private fun plan(
        goal: Goal = Goal.BUILD_MUSCLE,
        level: ExperienceLevel = ExperienceLevel.BEGINNER,
        equipment: EquipmentAccess = EquipmentAccess.BOTH,
        trained: Set<LocalDate> = emptySet(),
    ) = DailyPlanner.planFor(today, goal, level, equipment, trained, library)

    @Test
    fun `yeni baslayana tam vucut plani verir`() {
        val result = plan()

        assertEquals(PlanFocus.FULL_BODY, result.focus)
        assertEquals(4, result.exercises.size)
        assertFalse(result.isRestDay)
    }

    @Test
    fun `bugun zaten calisildiysa dinlenme gunu doner`() {
        val result = plan(trained = setOf(today))

        assertTrue(result.isRestDay)
        assertTrue(result.exercises.isEmpty())
    }

    @Test
    fun `haftalik hedef dolduysa ve dun calisildiysa dinlenme onerir`() {
        val trained = setOf(
            LocalDate(2026, 8, 16),
            LocalDate(2026, 8, 15),
            LocalDate(2026, 8, 13),
        )
        val result = plan(level = ExperienceLevel.BEGINNER, trained = trained)

        assertTrue(result.isRestDay)
    }

    @Test
    fun `evde calisan gym hareketi almaz`() {
        val result = plan(equipment = EquipmentAccess.HOME)

        assertTrue(
            "Plana sadece salon hareketi girmemeli",
            result.exercises.none { it.exercise.id == "gym_machine" },
        )
    }

    @Test
    fun `hedef set ve tekrar aralligini degistirir`() {
        val muscle = plan(goal = Goal.BUILD_MUSCLE).exercises.first { !it.isTimeBased }
        val fatLoss = plan(goal = Goal.LOSE_FAT).exercises.first { !it.isTimeBased }

        assertTrue("Kas hedefi daha az tekrar", muscle.repsHigh < fatLoss.repsHigh)
        assertTrue("Yağ yakmada dinlenme daha kısa", fatLoss.restSeconds < muscle.restSeconds)
    }

    @Test
    fun `sure bazli hareket saniye olarak recete edilir`() {
        val timeBased = plan(level = ExperienceLevel.ADVANCED)
            .exercises.firstOrNull { it.isTimeBased }

        if (timeBased != null) {
            assertTrue("Saniye aralığı makul olmalı", timeBased.repsLow >= 20)
            assertTrue(timeBased.repsHigh <= 60)
        }
    }

    @Test
    fun `ileri seviye ust ve alt vucudu ayirir`() {
        val result = plan(level = ExperienceLevel.ADVANCED)

        assertTrue(result.focus in listOf(PlanFocus.UPPER, PlanFocus.LOWER, PlanFocus.CORE_CARDIO))
        assertEquals(6, result.exercises.size)
    }

    @Test
    fun `ayni kas grubundan ikiden fazla hareket secilmez`() {
        val many = List(6) { exercise("chest_$it", "chest") } + library
        val result = DailyPlanner.planFor(
            today = today,
            goal = Goal.BUILD_MUSCLE,
            level = ExperienceLevel.BEGINNER,
            equipment = EquipmentAccess.BOTH,
            trainedDatesLast7 = emptySet(),
            library = many,
        )

        val chestCount = result.exercises.count { it.exercise.primaryMuscle == "chest" }
        assertTrue("En fazla iki göğüs hareketi olmalı, bulundu: $chestCount", chestCount <= 2)
    }

    @Test
    fun `tahmini sure makul araliktadir`() {
        val result = plan()

        assertTrue("Süre pozitif olmalı", result.estimatedMinutes > 0)
        assertTrue("Bir saati aşmamalı", result.estimatedMinutes < 60)
    }
}
