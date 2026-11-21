package com.example.core

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class LocalImportResult(
    val localUri: String,
    val storagePath: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMs: Long = 0L
)

data class LocalStorageStats(
    val fileCount: Int = 0,
    val totalBytes: Long = 0L
) {
    val formattedSize: String
        get() = when {
            totalBytes >= 1024L * 1024L -> String.format("%.1f MB", totalBytes / (1024.0 * 1024.0))
            totalBytes >= 1024L -> "${totalBytes / 1024L} KB"
            else -> "$totalBytes B"
        }
}

object LocalMediaStorageManager {

    private const val MEDIA_DIR = "studio_media"
    private const val EXPORTS_DIR = "studio_exports"

    suspend fun importMediaToLocalStorage(
        context: Context,
        sourceUri: Uri,
        mediaType: String,
        subfolder: String,
        preferredExtension: String? = null
    ): Result<LocalImportResult> = withContext(Dispatchers.IO) {
        try {
            val appContext = context.applicationContext
            val rootDir = File(appContext.filesDir, "$MEDIA_DIR/$subfolder").apply { mkdirs() }

            // If the URI already points to a file inside our managed studio_media directory, reuse it directly
            if (sourceUri.scheme == "file") {
                val existing = sourceUri.path?.let { File(it) }
                if (existing != null && existing.exists() && existing.absolutePath.startsWith(appContext.filesDir.absolutePath)) {
                    val ext = existing.extension.ifBlank { preferredExtension ?: defaultExtForType(mediaType) }
                    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: defaultMimeForType(mediaType)
                    val durationMs = if (mediaType == "VIDEO" || mediaType == "AUDIO") extractDurationMs(appContext, sourceUri) else 0L
                    return@withContext Result.success(
                        LocalImportResult(
                            localUri = Uri.fromFile(existing).toString(),
                            storagePath = existing.absolutePath,
                            displayName = existing.nameWithoutExtension,
                            mimeType = mime,
                            sizeBytes = existing.length(),
                            durationMs = durationMs
                        )
                    )
                }
            }

            val resolver = appContext.contentResolver
            val resolvedMime = resolver.getType(sourceUri) ?: defaultMimeForType(mediaType)
            val resolvedDisplayName = queryDisplayName(appContext, sourceUri)
                ?: "${mediaType.lowercase()}_${System.currentTimeMillis()}"
            val extension = resolveExtension(resolvedDisplayName, resolvedMime, preferredExtension, mediaType)

            val cleanBaseName = resolvedDisplayName
                .substringBeforeLast('.')
                .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                .take(40)
                .ifBlank { mediaType.lowercase() }

            val destFile = File(rootDir, "${cleanBaseName}_${UUID.randomUUID().toString().take(8)}.$extension")

            resolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 16 * 1024)
                }
            } ?: return@withContext Result.failure(IllegalStateException("Could not open selected device file."))

            val fileUri = Uri.fromFile(destFile)
            val durationMs = if (mediaType == "VIDEO" || mediaType == "AUDIO") {
                extractDurationMs(appContext, fileUri)
            } else {
                0L
            }

            Result.success(
                LocalImportResult(
                    localUri = fileUri.toString(),
                    storagePath = destFile.absolutePath,
                    displayName = resolvedDisplayName.substringBeforeLast('.').ifBlank { cleanBaseName },
                    mimeType = resolvedMime,
                    sizeBytes = destFile.length(),
                    durationMs = durationMs
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportMediaToDevice(
        context: Context,
        sourceUriOrUrl: String,
        title: String,
        mediaType: String,
        mimeType: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val appContext = context.applicationContext
            val safeTitle = title
                .replace(Regex("[^a-zA-Z0-9._ -]"), "_")
                .trim()
                .ifBlank { "raven_studio_export" }
                .take(60)
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                ?: defaultExtForType(mediaType)
            val fileName = if (safeTitle.endsWith(".$ext", ignoreCase = true)) safeTitle else "$safeTitle.$ext"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val (collection, relativePath) = when (mediaType.uppercase()) {
                    "AUDIO" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_MUSIC}/Raven Studio"
                    "IMAGE" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_PICTURES}/Raven Studio"
                    "VIDEO" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_MOVIES}/Raven Studio"
                    else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_DOWNLOADS}/Raven Studio"
                }

                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val resolver = appContext.contentResolver
                val destUri = resolver.insert(collection, values)
                    ?: return@withContext Result.failure(IllegalStateException("Could not create MediaStore destination."))

                try {
                    openSourceStream(appContext, sourceUriOrUrl).use { input ->
                        resolver.openOutputStream(destUri)?.use { output ->
                            input.copyTo(output, bufferSize = 16 * 1024)
                        } ?: throw IllegalStateException("Could not open output stream.")
                    }
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(destUri, values, null, null)
                    Result.success("Saved \"$fileName\" to $relativePath")
                } catch (e: Exception) {
                    resolver.delete(destUri, null, null)
                    throw e
                }
            } else {
                // On API < 29, export into FileProvider-backed studio_exports so no WRITE_EXTERNAL_STORAGE permission is required
                val exportDir = File(appContext.filesDir, EXPORTS_DIR).apply { mkdirs() }
                val destFile = File(exportDir, fileName)
                openSourceStream(appContext, sourceUriOrUrl).use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output, bufferSize = 16 * 1024)
                    }
                }
                Result.success("Prepared \"$fileName\" for sharing/saving (${destFile.name})")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportLyricsTextToDevice(
        context: Context,
        title: String,
        content: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val appContext = context.applicationContext
            val safeTitle = title
                .replace(Regex("[^a-zA-Z0-9._ -]"), "_")
                .trim()
                .ifBlank { "raven_lyrics" }
                .take(60)
            val fileName = "$safeTitle.txt"
            val exportDir = File(appContext.filesDir, EXPORTS_DIR).apply { mkdirs() }
            val tempFile = File(exportDir, fileName)
            tempFile.writeText(content)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/Raven Studio"
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val resolver = appContext.contentResolver
                val destUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext Result.failure(IllegalStateException("Unable to create Downloads entry."))
                try {
                    resolver.openOutputStream(destUri)?.use { out ->
                        out.write(content.toByteArray(Charsets.UTF_8))
                    }
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(destUri, values, null, null)
                    Result.success("Saved \"$fileName\" to $relativePath")
                } catch (e: Exception) {
                    resolver.delete(destUri, null, null)
                    throw e
                }
            } else {
                Result.success("Lyrics prepared in ${tempFile.name} (use Share to save externally)")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun prepareShareIntent(
        context: Context,
        uriOrUrl: String,
        title: String,
        mimeType: String
    ): Result<Intent> = withContext(Dispatchers.IO) {
        try {
            val appContext = context.applicationContext
            val fileToShare: File = when {
                uriOrUrl.startsWith("http://") || uriOrUrl.startsWith("https://") -> {
                    val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "bin"
                    val exportDir = File(appContext.filesDir, EXPORTS_DIR).apply { mkdirs() }
                    val safeName = title.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(40).ifBlank { "media" }
                    File(exportDir, "$safeName.$ext").also { dest ->
                        openSourceStream(appContext, uriOrUrl).use { input ->
                            dest.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                }
                uriOrUrl.startsWith("file://") -> {
                    val raw = File(Uri.parse(uriOrUrl).path.orEmpty())
                    if (raw.absolutePath.startsWith(appContext.filesDir.absolutePath)) {
                        raw
                    } else {
                        copyIntoExports(appContext, uriOrUrl, title, mimeType)
                    }
                }
                uriOrUrl.startsWith("/") -> {
                    val raw = File(uriOrUrl)
                    if (raw.absolutePath.startsWith(appContext.filesDir.absolutePath)) {
                        raw
                    } else {
                        copyIntoExports(appContext, Uri.fromFile(raw).toString(), title, mimeType)
                    }
                }
                else -> copyIntoExports(appContext, uriOrUrl, title, mimeType)
            }

            if (!fileToShare.exists()) {
                return@withContext Result.failure(IllegalStateException("Media file no longer exists on device."))
            }

            val contentUri = FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                fileToShare
            )
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            Result.success(Intent.createChooser(sendIntent, "Share $title"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun prepareShareTextIntent(title: String, content: String): Intent {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "$title\n\n$content")
        }
        return Intent.createChooser(sendIntent, "Share $title")
    }

    suspend fun computeStorageStats(context: Context): LocalStorageStats = withContext(Dispatchers.IO) {
        val mediaRoot = File(context.applicationContext.filesDir, MEDIA_DIR)
        if (!mediaRoot.exists()) return@withContext LocalStorageStats()
        val files = mediaRoot.walkTopDown().filter { it.isFile }.toList()
        LocalStorageStats(
            fileCount = files.size,
            totalBytes = files.sumOf { it.length() }
        )
    }

    suspend fun cleanupOrphanFiles(context: Context, referencedPaths: Set<String>): Int = withContext(Dispatchers.IO) {
        val appFilesRoot = context.applicationContext.filesDir
        val mediaRoot = File(appFilesRoot, MEDIA_DIR)
        if (!mediaRoot.exists()) return@withContext 0
        var removed = 0
        mediaRoot.walkTopDown().filter { it.isFile }.forEach { file ->
            if (file.absolutePath !in referencedPaths) {
                if (file.delete()) removed++
            }
        }
        removed
    }

    fun deleteLocalFileIfManaged(context: Context, storagePath: String?) {
        if (storagePath.isNullOrBlank()) return
        runCatching {
            val file = File(storagePath)
            val managedRoot = File(context.applicationContext.filesDir, MEDIA_DIR)
            if (file.exists() && file.absolutePath.startsWith(managedRoot.absolutePath)) {
                file.delete()
            }
        }
    }

    private fun copyIntoExports(context: Context, uriStr: String, title: String, mimeType: String): File {
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "bin"
        val exportDir = File(context.filesDir, EXPORTS_DIR).apply { mkdirs() }
        val safeName = title.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(40).ifBlank { "media" }
        return File(exportDir, "$safeName.$ext").also { dest ->
            openSourceStream(context, uriStr).use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    private fun openSourceStream(context: Context, uriOrUrl: String): InputStream {
        return when {
            uriOrUrl.startsWith("http://") || uriOrUrl.startsWith("https://") -> {
                val conn = URL(uriOrUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.inputStream
            }
            uriOrUrl.startsWith("/") -> File(uriOrUrl).inputStream()
            else -> {
                val uri = Uri.parse(uriOrUrl)
                context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Cannot open local media file.")
            }
        }
    }

    private fun extractDurationMs(context: Context, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveExtension(
        displayName: String,
        mimeType: String,
        preferredExtension: String?,
        mediaType: String
    ): String {
        val fromName = displayName.substringAfterLast('.', "").lowercase()
        if (fromName.length in 2..5 && fromName.all { it.isLetterOrDigit() }) return fromName
        val fromMime = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        if (!fromMime.isNullOrBlank()) return fromMime
        return preferredExtension ?: defaultExtForType(mediaType)
    }

    private fun defaultExtForType(mediaType: String): String = when (mediaType.uppercase()) {
        "AUDIO" -> "mp3"
        "IMAGE" -> "jpg"
        "VIDEO" -> "mp4"
        else -> "bin"
    }

    private fun defaultMimeForType(mediaType: String): String = when (mediaType.uppercase()) {
        "AUDIO" -> "audio/mpeg"
        "IMAGE" -> "image/jpeg"
        "VIDEO" -> "video/mp4"
        else -> "application/octet-stream"
    }
}
