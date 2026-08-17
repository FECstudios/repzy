package com.repzy.app.data.repo

import com.repzy.app.data.model.ActivityLevel
import com.repzy.app.data.model.BodyMetric
import com.repzy.app.data.model.EquipmentAccess
import com.repzy.app.data.model.ExperienceLevel
import com.repzy.app.data.model.Goal
import com.repzy.app.data.model.NutritionTarget
import com.repzy.app.data.model.Profile
import com.repzy.app.data.model.Sex
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val client: SupabaseClient,
    private val auth: AuthRepository,
) {
    // Profil açılışta iki kez isteniyordu: RootViewModel yönlendirme için, Home göstermek için.
    // İkinci tur açılışa tam bir ağ gecikmesi ekliyordu — bellekte tutuyoruz.
    private val profileMutex = Mutex()
    private var cachedProfile: Profile? = null
    private var cachedFor: String? = null

    private fun requireUserId(): String =
        auth.currentUserId ?: error("Oturum yok — profil işlemi yapılamaz.")

    suspend fun getProfile(forceRefresh: Boolean = false): Result<Profile?> = runCatching {
        val uid = requireUserId()
        profileMutex.withLock {
            if (!forceRefresh && cachedFor == uid) return@runCatching cachedProfile

            val profile = client.from("profiles")
                .select { filter { eq("id", uid) } }
                .decodeSingleOrNull<Profile>()
            cachedProfile = profile
            cachedFor = uid
            profile
        }
    }

    /** Çıkışta ya da profil değiştiğinde çağrılır — başka hesap eski profili görmesin. */
    suspend fun invalidateProfile() = profileMutex.withLock {
        cachedProfile = null
        cachedFor = null
    }

    suspend fun latestBodyMetric(): Result<BodyMetric?> = runCatching {
        val uid = requireUserId()
        client.from("body_metrics")
            .select {
                filter { eq("user_id", uid) }
                order("measured_on", Order.DESCENDING)
                limit(1)
            }
            .decodeSingleOrNull<BodyMetric>()
    }

    suspend fun bodyMetricHistory(limit: Long = 90): Result<List<BodyMetric>> = runCatching {
        val uid = requireUserId()
        client.from("body_metrics")
            .select {
                filter { eq("user_id", uid) }
                order("measured_on", Order.DESCENDING)
                limit(limit)
            }
            .decodeList<BodyMetric>()
    }

    suspend fun activeNutritionTarget(): Result<NutritionTarget?> = runCatching {
        val uid = requireUserId()
        client.from("nutrition_targets")
            .select {
                filter { eq("user_id", uid) }
                order("effective_from", Order.DESCENDING)
                limit(1)
            }
            .decodeSingleOrNull<NutritionTarget>()
    }

    /**
     * Ayarlardan düzenlenen alanlar. Sadece verilen alanlar yazılır — jsonb yerine
     * tek tek kolon güncellemesi, çünkü null gönderip alanı silmek istemiyoruz.
     */
    suspend fun updateProfile(
        displayName: String? = null,
        sex: Sex? = null,
        birthYear: Int? = null,
        heightCm: Double? = null,
        goal: Goal? = null,
        experienceLevel: ExperienceLevel? = null,
        equipmentAccess: EquipmentAccess? = null,
        activityLevel: ActivityLevel? = null,
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        client.from("profiles").update(
            {
                displayName?.let { set("display_name", it) }
                sex?.let { set("sex", it.wire) }
                birthYear?.let { set("birth_year", it) }
                heightCm?.let { set("height_cm", it) }
                goal?.let { set("goal", it.wire) }
                experienceLevel?.let { set("experience_level", it.wire) }
                equipmentAccess?.let { set("equipment_access", it.wire) }
                activityLevel?.let { set("activity_level", it.wire) }
            },
        ) {
            filter { eq("id", uid) }
        }
        invalidateProfile()
    }

    /**
     * Beslenme hedefi yazar. Aynı güne ikinci kez yazılırsa üzerine biner
     * (user_id + effective_from tekil), yani gün içinde tekrar hesaplamak
     * geçmişi çöpe çevirmiyor.
     *
     * [source]: 'rule' otomatik hesap, 'user' kullanıcının elle girdiği değer.
     */
    suspend fun saveNutritionTarget(target: NutritionTarget): Result<Unit> = runCatching {
        val uid = requireUserId()
        client.from("nutrition_targets").upsert(target.copy(userId = uid)) {
            onConflict = "user_id,effective_from"
        }
    }

    /** Aynı gün tekrar ölçüm girilirse üzerine yazar (user_id + measured_on unique). */
    suspend fun saveBodyMetric(metric: BodyMetric): Result<Unit> = runCatching {
        val uid = requireUserId()
        client.from("body_metrics").upsert(metric.copy(userId = uid)) {
            onConflict = "user_id,measured_on"
        }
    }

    /**
     * Onboarding'in tamamı tek RPC ile yazılır: profil + ilk ölçüm + beslenme hedefi.
     * Atomik — biri patlarsa onboarding "tamamlandı" işaretlenmez.
     */
    suspend fun completeOnboarding(payload: JsonObject): Result<Unit> = runCatching {
        client.postgrest.rpc("complete_onboarding", payload)
        invalidateProfile()
    }

    /**
     * Play Store zorunluluğu: uygulama içinden hesap silme.
     *
     * Storage'daki dosyalar `auth.users` silinince kendiliğinden gitmiyor (satır silmek
     * dosyayı silmiyor), o yüzden önce kullanıcının klasörlerini Storage API ile
     * boşaltıyoruz. Bir bucket boşsa ya da hiç kullanılmadıysa hata yutulur —
     * dosya temizliği yüzünden hesap silme başarısız olmasın.
     */
    suspend fun deleteAccount(): Result<Unit> = runCatching {
        val uid = requireUserId()

        listOf("body-photos", "food-photos").forEach { bucketId ->
            runCatching {
                val bucket = client.storage.from(bucketId)
                val paths = bucket.list(uid).map { "$uid/${it.name}" }
                if (paths.isNotEmpty()) bucket.delete(paths)
            }
        }

        client.postgrest.rpc("delete_my_account")
        invalidateProfile()
    }
}
