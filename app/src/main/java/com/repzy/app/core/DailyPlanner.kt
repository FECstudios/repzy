package com.repzy.app.core

import com.repzy.app.data.model.EquipmentAccess
import com.repzy.app.data.model.Exercise
import com.repzy.app.data.model.ExperienceLevel
import com.repzy.app.data.model.Goal
import kotlinx.datetime.LocalDate

/** Günün planındaki tek hareket: kaç set, kaç tekrar, ne kadar dinlenme. */
data class PlannedExercise(
    val exercise: Exercise,
    val sets: Int,
    val repsLow: Int,
    val repsHigh: Int,
    val restSeconds: Int,
) {
    /** Süre bazlı hareketlerde tekrar yerine saniye gösterilir. */
    val isTimeBased: Boolean get() = exercise.isTimeBased
}

/** Günün planı. Kas grubu odağı, hareketler ve tahmini süre. */
data class DailyPlan(
    val date: LocalDate,
    val focus: PlanFocus,
    val exercises: List<PlannedExercise>,
) {
    val isRestDay: Boolean get() = focus == PlanFocus.REST

    /** Kaba tahmin: set başına çalışma + dinlenme. Kullanıcıya "ne kadar sürer" demek için. */
    val estimatedMinutes: Int
        get() = exercises.sumOf { planned ->
            planned.sets * (40 + planned.restSeconds)
        } / 60
}

/**
 * Günün odağı. Dönüşümlü çalışma: aynı kas grubunu iki gün üst üste vermiyoruz,
 * yeni başlayan için tam vücut daha verimli ve daha az kafa karıştırıcı.
 */
enum class PlanFocus {
    FULL_BODY,
    UPPER,
    LOWER,
    CORE_CARDIO,
    REST,
}

/**
 * Kural tabanlı günlük plan üreticisi. **AI kullanmıyor** — CLAUDE.md'de program
 * önerisinin kural tabanlı olması kararlaştırıldı: sonuç öngörülebilir, ücretsiz,
 * anında ve test edilebilir oluyor.
 *
 * Girdi kullanıcının kendi verisi: hedef, seviye, ekipman ve son 7 günde
 * hangi günlerde antrenman yaptığı.
 */
object DailyPlanner {

    /** Seviyeye göre haftalık antrenman günü sayısı. Fazlası yeni başlayanı yakıyor. */
    private fun weeklyTarget(level: ExperienceLevel): Int = when (level) {
        ExperienceLevel.BEGINNER -> 3
        ExperienceLevel.INTERMEDIATE -> 4
        ExperienceLevel.ADVANCED -> 5
    }

    /**
     * @param trainedDatesLast7 son 7 gün içinde antrenman yapılan günler
     * @param library filtrelenmemiş egzersiz kütüphanesi
     */
    fun planFor(
        today: LocalDate,
        goal: Goal,
        level: ExperienceLevel,
        equipment: EquipmentAccess,
        trainedDatesLast7: Set<LocalDate>,
        library: List<Exercise>,
    ): DailyPlan {
        // Dün çalıştıysa ve haftalık hedefe ulaşıldıysa dinlenme günü öner.
        val trainedYesterday = trainedDatesLast7.contains(today.minusDaysCompat(1))
        val weeklyDone = trainedDatesLast7.count { it != today }

        if (trainedDatesLast7.contains(today)) {
            // Bugün zaten çalışıldı — plan üretmek yerine bunu söylüyoruz.
            return DailyPlan(today, PlanFocus.REST, emptyList())
        }
        if (trainedYesterday && weeklyDone >= weeklyTarget(level)) {
            return DailyPlan(today, PlanFocus.REST, emptyList())
        }

        val focus = pickFocus(level, weeklyDone, trainedYesterday)
        val pool = library.filter { it.matches(equipment) }
        val exercises = selectExercises(focus, level, pool)

        return DailyPlan(
            date = today,
            focus = focus,
            exercises = exercises.map { exercise ->
                val (sets, low, high, rest) = prescription(goal, level, exercise)
                PlannedExercise(exercise, sets, low, high, rest)
            },
        )
    }

    /** Yeni başlayan tam vücut yapar; ileri seviye üst/alt böler. */
    private fun pickFocus(
        level: ExperienceLevel,
        weeklyDone: Int,
        trainedYesterday: Boolean,
    ): PlanFocus {
        if (level == ExperienceLevel.BEGINNER) {
            // Dün çalıştıysa hafif bir gün: karın + kondisyon.
            return if (trainedYesterday) PlanFocus.CORE_CARDIO else PlanFocus.FULL_BODY
        }
        // Sırayla dönüyor: üst → alt → karın/kondisyon.
        return when (weeklyDone % 3) {
            0 -> PlanFocus.UPPER
            1 -> PlanFocus.LOWER
            else -> PlanFocus.CORE_CARDIO
        }
    }

    // Slug'lar `exercises.primary_muscle` ile birebir aynı olmalı (migration 0003).
    // Uydurma bir slug sessizce boş plan üretir, o yüzden Labels.kt ile eşleşiyorlar.
    private val upperMuscles = setOf(
        "chest", "lats", "mid_back", "front_delts", "rear_delts",
        "biceps", "triceps", "forearms",
    )
    private val lowerMuscles = setOf("quadriceps", "hamstrings", "glutes")
    private val coreMuscles = setOf("core", "lower_back")

    private fun selectExercises(
        focus: PlanFocus,
        level: ExperienceLevel,
        pool: List<Exercise>,
    ): List<Exercise> {
        val wanted = when (focus) {
            PlanFocus.UPPER -> upperMuscles
            PlanFocus.LOWER -> lowerMuscles
            PlanFocus.CORE_CARDIO -> coreMuscles
            PlanFocus.FULL_BODY, PlanFocus.REST -> upperMuscles + lowerMuscles + coreMuscles
        }

        val count = when (level) {
            ExperienceLevel.BEGINNER -> 4
            ExperienceLevel.INTERMEDIATE -> 5
            ExperienceLevel.ADVANCED -> 6
        }

        // Seviyeye uygun hareketler önce; kas grubu tekrarını azaltmak için
        // her kas grubundan en fazla iki hareket alıyoruz.
        val byLevel = pool.sortedBy { exercise ->
            if (exercise.level == level) 0 else 1
        }

        val picked = mutableListOf<Exercise>()
        val perMuscle = mutableMapOf<String, Int>()

        for (exercise in byLevel) {
            if (picked.size >= count) break
            val muscle = exercise.primaryMuscle
            if (focus != PlanFocus.FULL_BODY && muscle !in wanted) continue
            if ((perMuscle[muscle] ?: 0) >= 2) continue
            picked += exercise
            perMuscle[muscle] = (perMuscle[muscle] ?: 0) + 1
        }

        // Havuz yetersizse (filtre çok daralttıysa) seviye şartını gevşetiyoruz.
        if (picked.size < count) {
            for (exercise in pool) {
                if (picked.size >= count) break
                if (exercise in picked) continue
                picked += exercise
            }
        }
        return picked
    }

    /** set / tekrar aralığı / dinlenme — hedefe ve seviyeye göre. */
    private fun prescription(
        goal: Goal,
        level: ExperienceLevel,
        exercise: Exercise,
    ): Prescription {
        if (exercise.isTimeBased) {
            // Süre bazlı hareketlerde "tekrar" saniye olarak yorumlanıyor.
            val seconds = when (level) {
                ExperienceLevel.BEGINNER -> 20 to 30
                ExperienceLevel.INTERMEDIATE -> 30 to 45
                ExperienceLevel.ADVANCED -> 45 to 60
            }
            return Prescription(3, seconds.first, seconds.second, 45)
        }

        return when (goal) {
            // Kas: orta tekrar, uzun dinlenme.
            Goal.BUILD_MUSCLE -> Prescription(if (level == ExperienceLevel.BEGINNER) 3 else 4, 8, 12, 90)
            // Yağ yakma: tempo yüksek, dinlenme kısa.
            Goal.LOSE_FAT -> Prescription(3, 12, 15, 60)
            // Dayanıklılık: yüksek tekrar, çok kısa dinlenme.
            Goal.ENDURANCE -> Prescription(3, 15, 20, 40)
            Goal.GENERAL_FITNESS -> Prescription(3, 10, 12, 75)
        }
    }

    private data class Prescription(
        val sets: Int,
        val repsLow: Int,
        val repsHigh: Int,
        val restSeconds: Int,
    )
}

/** Ekipman filtresi: "ikisi de" seçen kullanıcıya her şey uygun. */
private fun Exercise.matches(access: EquipmentAccess): Boolean = when (access) {
    EquipmentAccess.BOTH -> true
    EquipmentAccess.GYM -> setting == EquipmentAccess.GYM || setting == EquipmentAccess.BOTH
    EquipmentAccess.HOME -> setting == EquipmentAccess.HOME || setting == EquipmentAccess.BOTH
}

/** kotlinx-datetime 0.8'de LocalDate.minus için period gerekiyor; tek yerde sarmalıyoruz. */
private fun LocalDate.minusDaysCompat(days: Int): LocalDate =
    LocalDate.fromEpochDays(toEpochDays() - days)
