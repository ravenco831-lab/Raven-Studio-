package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.core.LocalImportResult
import com.example.core.LocalMediaStorageManager
import com.example.core.LocalStorageStats
import com.example.data.local.StudioDao
import com.example.data.local.StudioDatabase
import com.example.data.local.toDomain
import com.example.data.local.toEntity
import com.example.model.DuetLine
import com.example.model.GenerationJob
import com.example.model.LyricSheet
import com.example.model.MediaAsset
import com.example.model.SongRecord
import com.example.model.StudioProject
import com.example.model.TimelineClip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

data class LocalUploadResult(
    val localUri: String?,
    val storagePath: String?,
    val displayName: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long = 0L,
    val durationMs: Long = 0L,
    val warningMessage: String? = null
)

class StudioRepository(
    private val appContext: Context,
    private val dao: StudioDao = StudioDatabase.getInstance(appContext).studioDao()
) {
    // --- Real-Time Room Observation Streams ---
    fun observeSongs(): Flow<List<SongRecord>> =
        dao.observeSongs().map { list -> list.map { it.toDomain() } }

    fun observeLyrics(): Flow<List<LyricSheet>> =
        dao.observeLyricSheets().map { list -> list.map { it.toDomain() } }

    fun observeMediaAssets(): Flow<List<MediaAsset>> =
        dao.observeMediaAssets().map { list -> list.map { it.toDomain() } }

    fun observeProjects(): Flow<List<StudioProject>> =
        dao.observeProjects().map { list -> list.map { it.toDomain() } }

    fun observeJobs(): Flow<List<GenerationJob>> =
        dao.observeJobs().map { list -> list.map { it.toDomain() } }

    // --- Local Device Storage Upload & Copy ---
    suspend fun importUserMediaToDeviceStorage(
        uri: Uri,
        mediaType: String,
        folder: String,
        preferredExtension: String? = null
    ): LocalUploadResult {
        val result: Result<LocalImportResult> = LocalMediaStorageManager.importMediaToLocalStorage(
            context = appContext,
            sourceUri = uri,
            mediaType = mediaType,
            subfolder = folder,
            preferredExtension = preferredExtension
        )
        return result.fold(
            onSuccess = { imported ->
                LocalUploadResult(
                    localUri = imported.localUri,
                    storagePath = imported.storagePath,
                    displayName = imported.displayName,
                    mimeType = imported.mimeType,
                    sizeBytes = imported.sizeBytes,
                    durationMs = imported.durationMs
                )
            },
            onFailure = { err ->
                Log.w(TAG, "Local file copy fallback to picked URI: ${err.message}", err)
                LocalUploadResult(
                    localUri = uri.toString(),
                    storagePath = null,
                    warningMessage = "Could not copy into internal vault (${err.localizedMessage ?: "unknown error"}). Using original URI."
                )
            }
        )
    }

    suspend fun exportMediaToDevice(
        sourceUriOrUrl: String,
        title: String,
        mediaType: String,
        mimeType: String
    ): Result<String> = LocalMediaStorageManager.exportMediaToDevice(
        context = appContext,
        sourceUriOrUrl = sourceUriOrUrl,
        title = title,
        mediaType = mediaType,
        mimeType = mimeType
    )

    suspend fun exportLyricsToDevice(
        title: String,
        content: String
    ): Result<String> = LocalMediaStorageManager.exportLyricsTextToDevice(
        context = appContext,
        title = title,
        content = content
    )

    suspend fun prepareShareIntent(
        uriOrUrl: String,
        title: String,
        mimeType: String
    ): Result<Intent> = LocalMediaStorageManager.prepareShareIntent(
        context = appContext,
        uriOrUrl = uriOrUrl,
        title = title,
        mimeType = mimeType
    )

    fun prepareShareTextIntent(title: String, content: String): Intent =
        LocalMediaStorageManager.prepareShareTextIntent(title, content)

    suspend fun getLocalStorageStats(): LocalStorageStats =
        LocalMediaStorageManager.computeStorageStats(appContext)

    suspend fun cleanupUnusedMediaFiles(): Int {
        val referenced = dao.getAllReferencedStoragePaths().toSet()
        return LocalMediaStorageManager.cleanupOrphanFiles(appContext, referenced)
    }

    // --- Songs CRUD ---
    suspend fun saveSong(song: SongRecord): Result<String> {
        val resolvedId = song.id.ifBlank { UUID.randomUUID().toString() }
        return try {
            val now = System.currentTimeMillis()
            val existing = if (song.id.isNotBlank()) dao.getSongById(resolvedId) else null
            val createdAt = existing?.createdAt ?: song.createdAt
            dao.upsertSong(song.toEntity(resolvedId, createdAt, now))
            Result.success(resolvedId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save song", e)
            Result.failure(e)
        }
    }

    // --- Lyrics CRUD ---
    suspend fun saveLyricSheet(sheet: LyricSheet): Result<String> {
        val resolvedId = sheet.id.ifBlank { UUID.randomUUID().toString() }
        return try {
            val now = System.currentTimeMillis()
            val existing = if (sheet.id.isNotBlank()) dao.getLyricSheetById(resolvedId) else null
            val createdAt = existing?.createdAt ?: sheet.createdAt
            dao.upsertLyricSheet(sheet.toEntity(resolvedId, createdAt, now))
            Result.success(resolvedId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save lyric sheet", e)
            Result.failure(e)
        }
    }

    // --- Media Assets CRUD ---
    suspend fun saveMediaAsset(asset: MediaAsset): Result<String> {
        val resolvedId = asset.id.ifBlank { UUID.randomUUID().toString() }
        return try {
            val now = System.currentTimeMillis()
            val existing = if (asset.id.isNotBlank()) dao.getMediaAssetById(resolvedId) else null
            val createdAt = existing?.createdAt ?: asset.createdAt
            val merged = asset.copy(
                localUri = asset.localUri ?: existing?.localUri,
                remoteUrl = asset.remoteUrl ?: existing?.remoteUrl,
                storagePath = asset.storagePath ?: existing?.storagePath,
                fileSizeBytes = if (asset.fileSizeBytes > 0L) asset.fileSizeBytes else (existing?.fileSizeBytes ?: 0L)
            )
            dao.upsertMediaAsset(merged.toEntity(resolvedId, createdAt, now))
            Result.success(resolvedId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save media asset", e)
            Result.failure(e)
        }
    }

    // --- Studio & Duet Projects CRUD ---
    suspend fun saveProject(project: StudioProject): Result<String> {
        val resolvedId = project.id.ifBlank { UUID.randomUUID().toString() }
        return try {
            val now = System.currentTimeMillis()
            val existing = if (project.id.isNotBlank()) dao.getProjectById(resolvedId) else null
            val createdAt = existing?.createdAt ?: project.createdAt
            dao.upsertProject(project.toEntity(resolvedId, createdAt, now))
            Result.success(resolvedId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save studio project", e)
            Result.failure(e)
        }
    }

    // --- Generation Jobs CRUD ---
    suspend fun saveJob(job: GenerationJob): Result<String> {
        val resolvedId = job.id.ifBlank { UUID.randomUUID().toString() }
        return try {
            val now = System.currentTimeMillis()
            val existing = if (job.id.isNotBlank()) dao.getJobById(resolvedId) else null
            val createdAt = existing?.createdAt ?: job.createdAt
            dao.upsertJob(job.toEntity(resolvedId, createdAt, now))
            Result.success(resolvedId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save job", e)
            Result.failure(e)
        }
    }

    // --- Generic Rename & Delete ---
    suspend fun renameItem(subcollection: String, itemId: String, newTitle: String): Result<Unit> {
        return try {
            require(itemId.isNotBlank())
            val sanitized = newTitle.trim().take(120)
            require(sanitized.isNotEmpty()) { "Title cannot be empty" }
            val now = System.currentTimeMillis()
            when (subcollection) {
                "songs" -> dao.renameSong(itemId, sanitized, now)
                "lyrics" -> dao.renameLyricSheet(itemId, sanitized, now)
                "mediaAssets" -> dao.renameMediaAsset(itemId, sanitized, now)
                "projects" -> dao.renameProject(itemId, sanitized, now)
                "jobs" -> dao.renameJob(itemId, sanitized, now)
                else -> throw IllegalArgumentException("Unknown category: $subcollection")
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rename item in $subcollection", e)
            Result.failure(e)
        }
    }

    suspend fun deleteItem(subcollection: String, itemId: String): Result<Unit> {
        return try {
            require(itemId.isNotBlank())
            when (subcollection) {
                "songs" -> dao.deleteSong(itemId)
                "lyrics" -> dao.deleteLyricSheet(itemId)
                "mediaAssets" -> {
                    val existing = dao.getMediaAssetById(itemId)
                    dao.deleteMediaAsset(itemId)
                    LocalMediaStorageManager.deleteLocalFileIfManaged(appContext, existing?.storagePath)
                }
                "projects" -> dao.deleteProject(itemId)
                "jobs" -> dao.deleteJob(itemId)
                else -> throw IllegalArgumentException("Unknown category: $subcollection")
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete item in $subcollection", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "StudioRepository"

        fun duetLinesToMaps(lines: List<DuetLine>): List<Map<String, Any>> =
            lines.take(120).map { it.toMap() }

        fun timelineClipsToMaps(clips: List<TimelineClip>): List<Map<String, Any>> =
            clips.take(50).map { it.toMap() }
    }
}
