package com.repzy.app

import android.app.Application
import com.repzy.app.core.Perf
import dagger.hilt.android.HiltAndroidApp
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider

@HiltAndroidApp
class RepzyApp : Application() {

    /**
     * Provider olarak alınıyor: istemci Application kurulurken değil, aşağıdaki
     * arka plan işinde yaratılsın. Doğrudan enjekte edilse ana thread'de kurulurdu.
     */
    @Inject
    lateinit var supabaseClient: Provider<SupabaseClient>

    private val warmUpScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        Perf.mark("Application.onCreate")
        super.onCreate()

        // Supabase istemcisinin kurulumu (Ktor + OkHttp motoru) ~yarım saniye tutuyor ve
        // ilk kompozisyonda ana thread'de oluyordu. Singleton olduğu için burada
        // önden kurulursa arayüz ona hazır olarak ulaşıyor.
        warmUpScope.launch {
            Perf.time("supabase.warmUp") { supabaseClient.get() }
        }

        Perf.mark("Application.onCreate bitti")
    }
}
