package com.example

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.StudioDatabase
import com.example.data.repository.StudioRepository
import com.example.model.LyricSheet
import com.example.model.MediaAsset
import com.example.model.SongRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StudioLocalPersistenceTest {

    private lateinit var context: Context
    private lateinit var database: StudioDatabase
    private lateinit var repository: StudioRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, StudioDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = StudioRepository(context, database.studioDao())
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `songs lyrics and media assets persist rename and delete locally`() {
        runBlocking {
            val songId = repository.saveSong(
                SongRecord(title = "Midnight Highway", genre = "Country", bpm = 104L)
            ).getOrThrow()
            assertTrue(songId.isNotBlank())

            val songs = repository.observeSongs().first()
            assertEquals(1, songs.size)
            assertEquals("Midnight Highway", songs.first().title)

            repository.renameItem("songs", songId, "Neon Highway").getOrThrow()
            assertEquals("Neon Highway", repository.observeSongs().first().first().title)

            val lyricId = repository.saveLyricSheet(
                LyricSheet(title = "Duet Draft", content = "[Verse 1 - Raven]\nHello", duetMode = true)
            ).getOrThrow()
            assertEquals(1, repository.observeLyrics().first().size)

            repository.deleteItem("lyrics", lyricId).getOrThrow()
            assertTrue(repository.observeLyrics().first().isEmpty())
        }
    }

    @Test
    fun `importUserMediaToDeviceStorage copies file into internal studio_media and delete removes it`() {
        runBlocking {
            val tempSource = File(context.cacheDir, "sample_track.mp3").apply {
                writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
            }
            val importResult = repository.importUserMediaToDeviceStorage(
                uri = Uri.fromFile(tempSource),
                mediaType = "AUDIO",
                folder = "audio"
            )
            assertNotNull(importResult.storagePath)
            val copiedFile = File(importResult.storagePath!!)
            assertTrue(copiedFile.exists())
            assertEquals(8L, copiedFile.length())

            val assetId = repository.saveMediaAsset(
                MediaAsset(
                    mediaType = "AUDIO",
                    title = importResult.displayName ?: "sample_track",
                    mimeType = "audio/mpeg",
                    localUri = importResult.localUri,
                    storagePath = importResult.storagePath,
                    fileSizeBytes = importResult.sizeBytes
                )
            ).getOrThrow()

            assertEquals(1, repository.observeMediaAssets().first().size)

            repository.deleteItem("mediaAssets", assetId).getOrThrow()
            assertTrue(repository.observeMediaAssets().first().isEmpty())
            assertFalse(copiedFile.exists())
            tempSource.delete()
        }
    }
}
