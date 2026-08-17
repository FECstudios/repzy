package com.repzy.app.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream

/**
 * Yemek fotoğrafını AI'ya göndermeye hazırlar.
 *
 * Ham telefon fotoğrafı 4–8 MB; vision maliyeti ve yükleme süresi doğrudan
 * boyuta bağlı olduğu için uzun kenarı [MAX_EDGE_PX]'e indirip JPEG'e çeviriyoruz.
 * 1024 px, yemek tanımak için fazlasıyla yeterli.
 */
object ImagePrep {

    const val MAX_EDGE_PX = 1024
    private const val JPEG_QUALITY = 82

    /** Edge Function'ın kabul ettiği base64 üst sınırıyla aynı — orada 413 dönmesin. */
    private const val MAX_BASE64_LENGTH = 1_500_000

    /**
     * Uri'yi ölçeklenmiş, EXIF yönüne göre döndürülmüş base64 JPEG'e çevirir.
     * Çok büyük kalırsa kaliteyi düşürerek tekrar dener.
     */
    fun toBase64Jpeg(context: Context, uri: Uri): Result<String> = runCatching {
        val bitmap = decodeScaled(context, uri) ?: error("Fotoğraf okunamadı.")
        val oriented = applyExifRotation(context, uri, bitmap)

        var quality = JPEG_QUALITY
        var encoded = encode(oriented, quality)
        // Kalabalık tabak fotoğrafı yüksek entropili — 1024 px bile sınırı aşabiliyor.
        while (encoded.length > MAX_BASE64_LENGTH && quality > 45) {
            quality -= 15
            encoded = encode(oriented, quality)
        }
        oriented.recycle()

        require(encoded.length <= MAX_BASE64_LENGTH) { "Fotoğraf çok büyük." }
        encoded
    }

    private fun encode(bitmap: Bitmap, quality: Int): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    /** inSampleSize ile okur: tam boyutu belleğe hiç almadan küçültür. */
    private fun decodeScaled(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (
            bounds.outWidth / (sample * 2) >= MAX_EDGE_PX ||
            bounds.outHeight / (sample * 2) >= MAX_EDGE_PX
        ) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        val longEdge = maxOf(decoded.width, decoded.height)
        if (longEdge <= MAX_EDGE_PX) return decoded

        val scale = MAX_EDGE_PX.toFloat() / longEdge
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled != decoded) decoded.recycle()
        return scaled
    }

    /** Kameradan gelen fotoğraf sık sık yan yatık gelir; model yatık tabağı tanımakta zorlanıyor. */
    private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }

        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }
}
