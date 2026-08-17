package com.repzy.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Hatırlatıcılar. Sunucudan veri çekmiyorlar — saate dayalı, tamamen cihazda.
 *
 * Neden veri okumuyorlar: "su içtin mi" kontrolü için ağ isteği gerekir, işçi
 * uygulamayı açmadan çalıştığı için oturum yenilenmesi de gerekir. Hatırlatmanın
 * değeri zamanlamasında; doğruluk için kullanıcı zaten uygulamayı açacak.
 */
object Reminders {

    const val CHANNEL_ID = "repzy_reminders"

    /** WorkManager'da tekil isimler: aynı hatırlatıcı iki kez planlanmasın. */
    private const val WORK_WATER_PREFIX = "reminder_water_"
    private const val WORK_WORKOUT = "reminder_workout"
    private const val WORK_PLAN = "reminder_plan"

    /** Su hatırlatma saatleri — günü kaplayacak kadar sık, rahatsız etmeyecek kadar seyrek. */
    val WATER_HOURS = listOf(11, 15, 19)

    const val DEFAULT_WORKOUT_HOUR = 18

    /** Plan bildirimi sabah gider: kullanicinin gunu planlamasi icin erken olmali. */
    const val DEFAULT_PLAN_HOUR = 8

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(com.repzy.app.R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(com.repzy.app.R.string.notif_channel_desc)
            },
        )
    }

    /** Android 13+ bildirim izni istemeden bildirim göstermiyor. */
    fun hasPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        }

    fun setWaterReminders(context: Context, enabled: Boolean) {
        val manager = WorkManager.getInstance(context)
        WATER_HOURS.forEach { hour ->
            val name = WORK_WATER_PREFIX + hour
            if (!enabled) {
                manager.cancelUniqueWork(name)
                return@forEach
            }
            manager.enqueueUniquePeriodicWork(
                name,
                ExistingPeriodicWorkPolicy.UPDATE,
                dailyRequest(ReminderWorker.KIND_WATER, hour),
            )
        }
    }

    fun setWorkoutReminder(context: Context, enabled: Boolean, hour: Int = DEFAULT_WORKOUT_HOUR) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            manager.cancelUniqueWork(WORK_WORKOUT)
            return
        }
        manager.enqueueUniquePeriodicWork(
            WORK_WORKOUT,
            ExistingPeriodicWorkPolicy.UPDATE,
            dailyRequest(ReminderWorker.KIND_WORKOUT, hour),
        )
    }

    fun setPlanReminder(context: Context, enabled: Boolean, hour: Int = DEFAULT_PLAN_HOUR) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            manager.cancelUniqueWork(WORK_PLAN)
            return
        }
        manager.enqueueUniquePeriodicWork(
            WORK_PLAN,
            ExistingPeriodicWorkPolicy.UPDATE,
            dailyRequest(ReminderWorker.KIND_PLAN, hour),
        )
    }

    private fun dailyRequest(kind: String, hour: Int) =
        PeriodicWorkRequestBuilder<ReminderWorker>(Duration.ofDays(1))
            .setInitialDelay(delayUntil(hour))
            .setInputData(ReminderWorker.inputFor(kind))
            .addTag(kind)
            .build()

    /** Bugünkü saat geçtiyse yarına planlanır. */
    private fun delayUntil(hour: Int): Duration {
        val now = LocalDateTime.now()
        var target = now.with(LocalTime.of(hour, 0))
        if (!target.isAfter(now)) target = target.plusDays(1)
        return Duration.between(now, target)
    }
}
