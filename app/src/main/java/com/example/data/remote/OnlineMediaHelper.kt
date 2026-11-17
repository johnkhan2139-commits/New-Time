package com.example.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

data class EncodedMediaPayload(
    val mediaType: String,
    val base64: String,
    val note: String
)

class OnlineMediaHelper(private val context: Context) {

    fun isVideoUri(uri: Uri): Boolean {
        val mime = context.contentResolver.getType(uri) ?: ""
        return mime.startsWith("video/")
    }

    suspend fun encodeAvatarToBase64(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decodeScaledBitmap(uri, maxDimension = 360) ?: return@runCatching null
            compressBitmapToBase64(bitmap, maxBytes = 180_000)
        }.getOrNull()
    }

    suspend fun encodeChatMedia(uri: Uri): EncodedMediaPayload? = withContext(Dispatchers.IO) {
        if (isVideoUri(uri)) {
            encodeVideoThumbnail(uri)
        } else {
            val bitmap = decodeScaledBitmap(uri, maxDimension = 960) ?: return@withContext null
            val encoded = compressBitmapToBase64(bitmap, maxBytes = 350_000) ?: return@withContext null
            EncodedMediaPayload(
                mediaType = "IMAGE",
                base64 = encoded,
                note = "Photo shared via TIME"
            )
        }
    }

    private fun encodeVideoThumbnail(uri: Uri): EncodedMediaPayload? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
                ?: return null
            val scaled = scaleBitmap(frame, maxDimension = 720)
            val encoded = compressBitmapToBase64(scaled, maxBytes = 280_000) ?: return null
            val secs = (durationMs / 1000L).coerceAtLeast(0L)
            val durationLabel = String.format("%02d:%02d", secs / 60L, secs % 60L)
            EncodedMediaPayload(
                mediaType = "VIDEO",
                base64 = encoded,
                note = "Video preview ($durationLabel) • Enable Firebase Storage for full clip download"
            )
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun decodeScaledBitmap(uri: Uri, maxDimension: Int): Bitmap? {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOpts)
        }
        val maxSide = max(boundsOpts.outWidth, boundsOpts.outHeight)
        if (maxSide <= 0) return null

        var sampleSize = 1
        while (maxSide / sampleSize > maxDimension * 2) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: return null

        return scaleBitmap(decoded, maxDimension)
    }

    private fun scaleBitmap(source: Bitmap, maxDimension: Int): Bitmap {
        val maxSide = max(source.width, source.height)
        if (maxSide <= maxDimension) return source
        val ratio = maxDimension.toFloat() / maxSide.toFloat()
        val targetW = (source.width * ratio).roundToInt().coerceAtLeast(1)
        val targetH = (source.height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, targetW, targetH, true)
    }

    private fun compressBitmapToBase64(bitmap: Bitmap, maxBytes: Int): String? {
        var quality = 84
        var bytes: ByteArray
        do {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bytes = out.toByteArray()
            quality -= 12
        } while (bytes.size > maxBytes && quality >= 24)

        if (bytes.size > maxBytes) return null
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    companion object {
        fun decodeBase64Bytes(base64: String?): ByteArray? {
            if (base64.isNullOrBlank()) return null
            return runCatching { Base64.decode(base64, Base64.DEFAULT) }.getOrNull()
        }
    }
}
