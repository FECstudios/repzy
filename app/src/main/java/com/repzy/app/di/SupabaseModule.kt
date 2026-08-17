package com.repzy.app.di

import com.repzy.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideSupabaseClient(json: Json): SupabaseClient {
        require(BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()) {
            "SUPABASE_URL / SUPABASE_ANON_KEY local.properties içinde tanımlı değil."
        }
        return createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
        ) {
            defaultSerializer = KotlinXSerializer(json)
            install(Auth) {
                flowType = FlowType.PKCE
                autoLoadFromStorage = true
                alwaysAutoRefresh = true
            }
            install(Postgrest)
            install(Storage)
            // Fotoğraftan kalori tahmini Edge Function üzerinden gider — AI key uygulamada yok.
            install(Functions)
        }
    }
}
