package com.repzy.app.data.repo

import android.content.Context
import android.net.Uri
import com.repzy.app.core.ImagePrep
import com.repzy.app.data.model.BodyPhoto
import com.repzy.app.data.model.PhotoPose
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinLocalDate
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.hours

/**
 * Vücut fotoğrafları.
 *
 * KVKK Md. 6 açısından bunlar **özel nitelikli kişisel veri** — o yüzden:
 *  - `body-photos` bucket'ı public değil; gösterim için kısa ömürlü imzalı URL üretiliyor,
 *  - fotoğraf AI'ya **gönderilmiyor** (yemek fotoğrafından farkı bu),
 *  - yükleme, profilde `photo_consent_at` dolu değilse hiç başlamıyor.
 */
@Singleton
class BodyPhotoRepository @Inject constructor(
    private val client: SupabaseClient,
    private val auth: AuthRepository,
    private val profiles: ProfileRepository,
) {
    private val bucket get() = client.storage.from(BUCKET)

    private fun requireUserId(): String =
        auth.currentUserId ?: error("Oturum yok — fotoğraf işlemi yapılamaz.")

    suspend fun list(): Result<List<BodyPhoto>> = runCatching {
        val uid = requireUserId()
        client.from("body_photos")
            .select {
                filter { eq("user_id", uid) }
                order("taken_on", Order.DESCENDING)
            }
            .decodeList<BodyPhoto>()
    }

    /**
     * Fotoğrafı küçültüp yükler, sonra satırı yazar. Sıra önemli: satır önce yazılsaydı
     * yükleme patladığında galeride bozuk bir kayıt kalırdı.
     */
    suspend fun add(
        context: Context,
        uri: Uri,
        pose: PhotoPose,
        takenOn: LocalDate = java.time.LocalDate.now().toKotlinLocalDate(),
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(hasConsent()) { "Fotoğraf rızası verilmemiş." }

        val bytes = withContext(Dispatchers.Default) {
            ImagePrep.toJpegBytes(context, uri).getOrThrow()
        }

        // Aynı gün aynı poza ikinci fotoğraf çekilebilsin diye yola rastgele son ek.
        val path = "$uid/${pose.wire}_${takenOn}_${UUID.randomUUID()}.jpg"
        bucket.upload(path, bytes)

        client.from("body_photos").insert(
            BodyPhoto(userId = uid, storagePath = path, pose = pose, takenOn = takenOn),
        )
    }

    /**
     * Önce dosya, sonra satır. Ters sırada olsaydı satır gidip dosya kalabilir,
     * kullanıcının sildiğini sandığı fotoğraf Storage'da durmaya devam ederdi.
     */
    suspend fun delete(photo: BodyPhoto): Result<Unit> = runCatching {
        val uid = requireUserId()
        val id = photo.id ?: error("Fotoğraf kimliği yok.")

        bucket.delete(photo.storagePath)
        client.from("body_photos").delete {
            filter {
                eq("id", id)
                eq("user_id", uid)
            }
        }
    }

    /** Bucket public olmadığı için her gösterimde imzalı URL gerekiyor. */
    suspend fun signedUrl(storagePath: String): Result<String> = runCatching {
        bucket.createSignedUrl(storagePath, SIGNED_URL_TTL)
    }

    suspend fun hasConsent(): Boolean =
        profiles.getProfile().getOrNull()?.photoConsentAt != null

    /**
     * Ayrı açık rıza. Sağlık verisi rızası fotoğrafı kapsamıyor — KVKK spesifik rıza istiyor.
     * Damga istemcide üretiliyor: kullanıcının kutuyu işaretlediği an, sunucuya yazma anı değil.
     */
    suspend fun grantConsent(): Result<Unit> = runCatching {
        val uid = requireUserId()
        client.from("profiles").update({
            set("photo_consent_at", Instant.now().toString())
        }) {
            filter { eq("id", uid) }
        }
        profiles.invalidateProfile()
    }

    private companion object {
        const val BUCKET = "body-photos"
        val SIGNED_URL_TTL = 1.hours
    }
}
