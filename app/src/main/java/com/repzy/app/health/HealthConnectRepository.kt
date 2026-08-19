package com.repzy.app.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** Cihazda Health Connect'in durumu. Arayüz üç durumu ayrı ayrı anlatmak zorunda. */
enum class HealthAvailability {
    AVAILABLE,

    /** Uygulama kurulu değil ya da güncellenmesi gerekiyor — Play'e yönlendiriyoruz. */
    NEEDS_INSTALL,

    /** Cihaz hiç desteklemiyor (API 26 altı bazı cihazlar). */
    UNSUPPORTED,
}

/** Saatten/telefondan gelen günlük özet. Ham kayıt tutmuyoruz, sadece toplamlar. */
data class HealthSnapshot(
    val steps: Long? = null,
    val activeCalories: Int? = null,
    val exerciseMinutes: Int? = null,
) {
    val hasData: Boolean get() = steps != null || activeCalories != null || exerciseMinutes != null
}

/**
 * Health Connect okuması. **Sadece okuma** yapıyoruz, yazma izni istemiyoruz:
 * daha az izin, daha kolay Play onayı ve kullanıcı için daha az risk.
 *
 * Ham kayıt saklanmıyor; yalnızca günün toplamları okunuyor. Bu toplamlar iki yerde
 * kullanılıyor: ana sayfadaki kutular ve **günlük koç brief'i** — `daily-brief` Edge
 * Function'ına `device_activity` olarak gidiyor, yani cihazda kalmıyorlar.
 * Gizlilik politikası ve KVKK metni bunu açıkça yazıyor (18 Ağu 2026); okunan alan
 * kümesi değişirse o metinler de değişmeli.
 *
 * TDEE düzeltmesinde henüz kullanılmıyor — kullanılırsa `BodyMath` ve testleri değişir.
 */
@Singleton
class HealthConnectRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** İstediğimiz izinler — hepsi okuma. */
    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
    )

    fun availability(): HealthAvailability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.NEEDS_INSTALL
        else -> HealthAvailability.UNSUPPORTED
    }

    private fun clientOrNull(): HealthConnectClient? =
        if (availability() == HealthAvailability.AVAILABLE) {
            runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull()
        } else {
            null
        }

    suspend fun hasPermissions(): Boolean {
        val client = clientOrNull() ?: return false
        return runCatching {
            client.permissionController.getGrantedPermissions().containsAll(permissions)
        }.getOrDefault(false)
    }

    /**
     * Bugünün toplamları. Kısmi izin verilmiş olabilir (kullanıcı sadece adımı
     * onaylamış olabilir), o yüzden her metrik ayrı ayrı ve hataya dayanıklı okunuyor.
     */
    suspend fun today(date: LocalDate = LocalDate.now()): Result<HealthSnapshot> = runCatching {
        val client = clientOrNull() ?: return@runCatching HealthSnapshot()

        val range = TimeRangeFilter.between(
            date.atStartOfDay(),
            LocalDateTime.now(),
        )

        val result: AggregationResult? = runCatching {
            client.aggregate(
                AggregateRequest(
                    metrics = setOf(
                        StepsRecord.COUNT_TOTAL,
                        ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                        ExerciseSessionRecord.EXERCISE_DURATION_TOTAL,
                    ),
                    timeRangeFilter = range,
                ),
            )
        }.getOrNull()

        HealthSnapshot(
            steps = result?.get(StepsRecord.COUNT_TOTAL),
            activeCalories = result?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
                ?.inKilocalories?.toInt(),
            exerciseMinutes = result?.get(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL)
                ?.toMinutes()?.toInt(),
        )
    }
}
