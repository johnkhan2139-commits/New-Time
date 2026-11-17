package com.example.data.media

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class SavedMediaFile(
    val absolutePath: String,
    val isVideo: Boolean
)

class MediaStorageHelper(private val context: Context) {

    private val mediaDir: File
        get() = File(context.filesDir, "time_media").apply {
            if (!exists()) mkdirs()
        }

    suspend fun persistMediaFromUri(uri: Uri): Result<SavedMediaFile> = withContext(Dispatchers.IO) {
        try {
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            val isVideo = mimeType.startsWith("video/")
            val ext = MimeTypeMap.getSingleton()
                .getExtensionFromMimeType(mimeType)
                ?.takeIf { it.isNotBlank() }
                ?: if (isVideo) "mp4" else "jpg"

            val destFile = File(mediaDir, "${UUID.randomUUID()}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(IllegalStateException("Unable to read selected media"))

            Result.success(SavedMediaFile(absolutePath = destFile.absolutePath, isVideo = isVideo))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearAllMedia() = withContext(Dispatchers.IO) {
        mediaDir.deleteRecursively()
    }
}
