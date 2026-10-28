package com.example.instamediasaver.data.downloader

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.instamediasaver.model.MediaItem
import com.example.instamediasaver.model.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

data class SavedMediaResult(
    val contentUri: Uri,
    val fileName: String,
    val mimeType: String,
    val bytesWritten: Long
)

/**
 * Downloads original-quality Instagram image/video streams directly into the user's phone Gallery
 * (`Pictures/InstaMediaSaver` or `Movies/InstaMediaSaver`) using Android's official `MediaStore` APIs.
 *
 * - On Android 10+ (API 29+), uses `MediaStore.MediaColumns.IS_PENDING = 1` during transfer and
 *   requires ZERO runtime storage permissions.
 * - Reports byte-level progress via [onProgressPercent].
 */
class GalleryMediaDownloader(
    private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    suspend fun downloadItemToGallery(
        shortcode: String,
        item: MediaItem,
        onProgressPercent: (Int) -> Unit
    ): Result<SavedMediaResult> = withContext(Dispatchers.IO) {
        val downloadUrl = item.mediaUrl
        if (downloadUrl.isNullOrBlank() || !item.isDownloadable) {
            return@withContext Result.failure(
                IllegalStateException(item.restrictionReason ?: "Media URL is not available for download.")
            )
        }

        val isVideo = item.kind == MediaKind.VIDEO
        val extension = if (isVideo) "mp4" else "jpg"
        val mimeType = if (isVideo) "video/mp4" else "image/jpeg"
        val fileName = "IG_${shortcode}_${item.index}_${System.currentTimeMillis()}.$extension"

        val resolver = context.contentResolver
        val collectionUri: Uri = if (isVideo) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
        }

        val relativeSubfolder = if (isVideo) {
            "${Environment.DIRECTORY_MOVIES}/InstaMediaSaver"
        } else {
            "${Environment.DIRECTORY_PICTURES}/InstaMediaSaver"
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeSubfolder)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            } else {
                val baseDir = Environment.getExternalStoragePublicDirectory(
                    if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
                )
                val appDir = File(baseDir, "InstaMediaSaver").apply { mkdirs() }
                put(MediaStore.MediaColumns.DATA, File(appDir, fileName).absolutePath)
            }
        }

        val targetUri = resolver.insert(collectionUri, values)
            ?: return@withContext Result.failure(IllegalStateException("Could not create MediaStore entry."))

        try {
            val request = Request.Builder().url(downloadUrl).get().build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    resolver.delete(targetUri, null, null)
                    return@withContext Result.failure(
                        IllegalStateException("Download failed with HTTP ${response.code}")
                    )
                }

                val body = response.body
                    ?: run {
                        resolver.delete(targetUri, null, null)
                        return@withContext Result.failure(IllegalStateException("Empty media stream."))
                    }

                val totalBytes = body.contentLength()
                var totalRead = 0L

                resolver.openOutputStream(targetUri)?.use { outStream ->
                    body.byteStream().use { inStream ->
                        totalRead = copyStreamWithProgress(
                            input = inStream,
                            output = outStream,
                            totalBytes = totalBytes,
                            onProgressPercent = onProgressPercent
                        )
                    }
                } ?: run {
                    resolver.delete(targetUri, null, null)
                    return@withContext Result.failure(IllegalStateException("Could not open gallery output stream."))
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val completeValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    resolver.update(targetUri, completeValues, null, null)
                }

                Result.success(
                    SavedMediaResult(
                        contentUri = targetUri,
                        fileName = fileName,
                        mimeType = mimeType,
                        bytesWritten = totalRead
                    )
                )
            }
        } catch (e: Exception) {
            runCatching { resolver.delete(targetUri, null, null) }
            Result.failure(e)
        }
    }

    private fun copyStreamWithProgress(
        input: InputStream,
        output: OutputStream,
        totalBytes: Long,
        onProgressPercent: (Int) -> Unit
    ): Long {
        val buffer = ByteArray(16 * 1024)
        var bytesCopied = 0L
        var lastReportedPercent = -1
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            output.write(buffer, 0, read)
            bytesCopied += read
            if (totalBytes > 0) {
                val percent = ((bytesCopied * 100L) / totalBytes).toInt().coerceIn(0, 100)
                if (percent != lastReportedPercent) {
                    lastReportedPercent = percent
                    onProgressPercent(percent)
                }
            }
        }
        output.flush()
        onProgressPercent(100)
        return bytesCopied
    }
}
