package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StudioDao {

    // --- Songs ---
    @Query("SELECT * FROM songs ORDER BY updatedAt DESC")
    fun observeSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :id LIMIT 1")
    suspend fun getSongById(id: String): SongEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSong(entity: SongEntity)

    @Query("UPDATE songs SET title = :newTitle, updatedAt = :updatedAt WHERE id = :id")
    suspend fun renameSong(id: String, newTitle: String, updatedAt: Long)

    @Query("DELETE FROM songs WHERE id = :id")
    suspend fun deleteSong(id: String)

    // --- Lyric Sheets ---
    @Query("SELECT * FROM lyric_sheets ORDER BY updatedAt DESC")
    fun observeLyricSheets(): Flow<List<LyricSheetEntity>>

    @Query("SELECT * FROM lyric_sheets WHERE id = :id LIMIT 1")
    suspend fun getLyricSheetById(id: String): LyricSheetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLyricSheet(entity: LyricSheetEntity)

    @Query("UPDATE lyric_sheets SET title = :newTitle, updatedAt = :updatedAt WHERE id = :id")
    suspend fun renameLyricSheet(id: String, newTitle: String, updatedAt: Long)

    @Query("DELETE FROM lyric_sheets WHERE id = :id")
    suspend fun deleteLyricSheet(id: String)

    // --- Media Assets ---
    @Query("SELECT * FROM media_assets ORDER BY updatedAt DESC")
    fun observeMediaAssets(): Flow<List<MediaAssetEntity>>

    @Query("SELECT * FROM media_assets WHERE id = :id LIMIT 1")
    suspend fun getMediaAssetById(id: String): MediaAssetEntity?

    @Query("SELECT storagePath FROM media_assets WHERE storagePath IS NOT NULL")
    suspend fun getAllReferencedStoragePaths(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMediaAsset(entity: MediaAssetEntity)

    @Query("UPDATE media_assets SET title = :newTitle, updatedAt = :updatedAt WHERE id = :id")
    suspend fun renameMediaAsset(id: String, newTitle: String, updatedAt: Long)

    @Query("DELETE FROM media_assets WHERE id = :id")
    suspend fun deleteMediaAsset(id: String)

    // --- Studio Projects ---
    @Query("SELECT * FROM studio_projects ORDER BY updatedAt DESC")
    fun observeProjects(): Flow<List<StudioProjectEntity>>

    @Query("SELECT * FROM studio_projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: String): StudioProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(entity: StudioProjectEntity)

    @Query("UPDATE studio_projects SET title = :newTitle, updatedAt = :updatedAt WHERE id = :id")
    suspend fun renameProject(id: String, newTitle: String, updatedAt: Long)

    @Query("DELETE FROM studio_projects WHERE id = :id")
    suspend fun deleteProject(id: String)

    // --- Generation Jobs ---
    @Query("SELECT * FROM generation_jobs ORDER BY updatedAt DESC")
    fun observeJobs(): Flow<List<GenerationJobEntity>>

    @Query("SELECT * FROM generation_jobs WHERE id = :id LIMIT 1")
    suspend fun getJobById(id: String): GenerationJobEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertJob(entity: GenerationJobEntity)

    @Query("UPDATE generation_jobs SET title = :newTitle, updatedAt = :updatedAt WHERE id = :id")
    suspend fun renameJob(id: String, newTitle: String, updatedAt: Long)

    @Query("DELETE FROM generation_jobs WHERE id = :id")
    suspend fun deleteJob(id: String)
}
