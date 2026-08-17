package com.repzy.app.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.repzy.app.MainActivity
import com.repzy.app.R

/**
 * Hatırlatma bildirimini gösterir. Hilt kullanmıyor: hiçbir bağımlılığı yok,
 * bu yüzden `hilt-work` eklemek gerekmedi.
 */
class ReminderWorker(
    private val context: Context,
    params: WorkerParameters,
) : Worker(context, params) {

    override fun doWork(): Result {
        val kind = inputData.getString(KEY_KIND) ?: return Result.success()

        // İzin sonradan geri alınmış olabilir — bildirim atmayı denemeden kontrol et.
        if (!Reminders.hasPermission(context)) return Result.success()

        val (titleRes, bodyRes) = when (kind) {
            KIND_WATER -> R.string.notif_water_title to R.string.notif_water_body
            KIND_WORKOUT -> R.string.notif_workout_title to R.string.notif_workout_body
            KIND_PLAN -> R.string.notif_plan_title to R.string.notif_plan_body
            else -> return Result.success()
        }

        Reminders.ensureChannel(context)

        val openApp = PendingIntent.getActivity(
            context,
            kind.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, Reminders.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(titleRes))
            .setContentText(context.getString(bodyRes))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()

        // İzin varken de sistem kapatmış olabilir; NotificationManagerCompat sessizce yutar.
        runCatching {
            NotificationManagerCompat.from(context).notify(kind.hashCode(), notification)
        }
        return Result.success()
    }

    companion object {
        const val KEY_KIND = "kind"
        const val KIND_WATER = "water"
        const val KIND_WORKOUT = "workout"
        const val KIND_PLAN = "plan"

        fun inputFor(kind: String) = workDataOf(KEY_KIND to kind)
    }
}
