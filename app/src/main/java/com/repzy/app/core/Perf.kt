package com.repzy.app.core

import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.repzy.app.BuildConfig

/**
 * Açılış gecikmesini ölçmek için ince bir iz. Sadece debug'da çalışır —
 * release'te `BuildConfig.DEBUG` sabiti false olduğu için gövde tamamen elenir.
 *
 * Okumak için:  adb logcat -s RepzyPerf
 */
object Perf {

    private const val TAG = "RepzyPerf"

    /** Süreç başlangıcından bu yana geçen ms — farklı bileşenlerin ölçümü karşılaştırılabilir olsun. */
    private fun sinceProcessStart(): Long =
        SystemClock.uptimeMillis() - Process.getStartUptimeMillis()

    fun mark(label: String) {
        if (!BuildConfig.DEBUG) return
        Log.d(TAG, "$label @${sinceProcessStart()}ms")
    }

    suspend fun <T> time(label: String, block: suspend () -> T): T {
        if (!BuildConfig.DEBUG) return block()
        val start = SystemClock.uptimeMillis()
        val result = block()
        val took = SystemClock.uptimeMillis() - start
        Log.d(TAG, "$label took ${took}ms (bitiş @${sinceProcessStart()}ms)")
        return result
    }
}
