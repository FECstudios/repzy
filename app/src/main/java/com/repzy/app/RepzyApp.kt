package com.repzy.app

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import com.repzy.app.core.Perf
import com.repzy.app.notifications.Reminders
import dagger.hilt.android.HiltAndroidApp
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider

@HiltAndroidApp
class RepzyApp : Application(), SingletonImageLoader.Factory {

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

        // Kanal olmadan Android 8+ bildirim göstermiyor; ucuz bir çağrı, açılışta yapılır.
        Reminders.ensureChannel(this)

        // Supabase istemcisinin kurulumu (Ktor + OkHttp motoru) ~yarım saniye tutuyor ve
        // ilk kompozisyonda ana thread'de oluyordu. Singleton olduğu için burada
        // önden kurulursa arayüz ona hazır olarak ulaşıyor.
        warmUpScope.launch {
            Perf.time("supabase.warmUp") { supabaseClient.get() }
        }

        Perf.mark("Application.onCreate bitti")
    }

    /**
     * Animasyonlu WebP/GIF egzersiz gorselleri icin cozucu. Coil varsayilan
     * olarak animasyonlu formatlari acmiyor, ilk kareyi gosteriyordu.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
}
