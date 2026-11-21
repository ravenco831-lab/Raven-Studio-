package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.GenerationJob
import com.example.model.LyricSheet
import com.example.model.MediaAsset
import com.example.model.SongRecord
import com.example.model.StudioProject
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val prompt: String,
    val lyrics: String,
    val genre: String,
    val mood: String,
    val bpm: Long,
    val vocalType: String,
    val mode: String,
    val status: String,
    val aiGenerated: Boolean,
    val durationMs: Long,
    val versionNumber: Long,
    val audioUrl: String?,
    val wavUrl: String?,
    val storagePath: String?,
    val providerName: String?,
    val estimatedCostUsd: Double?,
    val instrumentsJson: String,
    val vocalStyle: String?,
    val errorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "lyric_sheets")
data class LyricSheetEntity(
    @PrimaryKey val id: String,
    val title: String,
    val genre: String,
    val duetMode: Boolean,
    val content: String,
    val productionPrompt: String,
    val singer1Name: String,
    val singer2Name: String,
    val aiGenerated: Boolean,
    val theme: String?,
    val partsJson: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "media_assets")
data class MediaAssetEntity(
    @PrimaryKey val id: String,
    val mediaType: String,
    val title: String,
    val mimeType: String,
    val durationMs: Long,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val splitPointMs: Long,
    val volume: Double,
    val fadeInMs: Long,
    val fadeOutMs: Long,
    val syncOffsetMs: Long,
    val faceDetected: Boolean,
    val faceCount: Long,
    val aiGenerated: Boolean,
    val localUri: String?,
    val remoteUrl: String?,
    val storagePath: String?,
    val fileSizeBytes: Long,
    val waveformJson: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "studio_projects")
data class StudioProjectEntity(
    @PrimaryKey val id: String,
    val projectType: String,
    val title: String,
    val status: String,
    val aspectRatio: String,
    val resolution: String,
    val watermarkEnabled: Boolean,
    val aiGenerated: Boolean,
    val consentConfirmed: Boolean,
    val singer1Name: String?,
    val singer1VocalRole: String?,
    val singer1PortraitUri: String?,
    val singer1FaceDetected: Boolean?,
    val singer2Name: String?,
    val singer2VocalRole: String?,
    val singer2PortraitUri: String?,
    val singer2FaceDetected: Boolean?,
    val selectedSongTitle: String?,
    val selectedAudioUri: String?,
    val audioDurationMs: Long?,
    val performanceStyle: String?,
    val performanceIntensity: Double?,
    val useVocalSeparation: Boolean?,
    val lipSyncOffsetMs: Long?,
    val cameraMovement: String?,
    val animationStyle: String?,
    val animationPrompt: String?,
    val textOverlay: String?,
    val lyricsOverlayEnabled: Boolean?,
    val albumArtUri: String?,
    val transitionStyle: String?,
    val outputVideoUrl: String?,
    val errorMessage: String?,
    val duetLinesJson: String,
    val timelineClipsJson: String,
    val waveformJson: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "generation_jobs")
data class GenerationJobEntity(
    @PrimaryKey val id: String,
    val jobType: String,
    val title: String,
    val providerName: String,
    val status: String,
    val progressPercent: Long,
    val estimatedCostUsd: Double,
    val externalJobId: String?,
    val projectId: String?,
    val resultUrl: String?,
    val errorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long
)

internal object StudioJsonCodec {
    fun encodeStringList(list: List<String>): String = JSONArray(list).toString()

    fun decodeStringList(raw: String): List<String> = runCatching {
        val arr = JSONArray(raw)
        List(arr.length()) { idx -> arr.optString(idx) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

    fun encodeLongList(list: List<Long>): String = JSONArray().apply {
        list.forEach { put(it) }
    }.toString()

    fun decodeLongList(raw: String): List<Long> = runCatching {
        val arr = JSONArray(raw)
        List(arr.length()) { idx -> arr.optLong(idx) }
    }.getOrDefault(emptyList())

    fun encodeMapList(list: List<Map<String, Any>>): String = JSONArray().apply {
        list.forEach { map ->
            put(JSONObject().apply {
                map.forEach { (k, v) -> put(k, v) }
            })
        }
    }.toString()

    fun decodeMapList(raw: String): List<Map<String, Any>> = runCatching {
        val arr = JSONArray(raw)
        List(arr.length()) { idx ->
            val obj = arr.optJSONObject(idx) ?: JSONObject()
            buildMap {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = obj.opt(key)
                    if (value != null && value != JSONObject.NULL) {
                        put(key, value)
                    }
                }
            }
        }
    }.getOrDefault(emptyList())
}

fun SongEntity.toDomain(): SongRecord = SongRecord(
    id = id,
    title = title,
    prompt = prompt,
    lyrics = lyrics,
    genre = genre,
    mood = mood,
    bpm = bpm,
    vocalType = vocalType,
    mode = mode,
    status = status,
    aiGenerated = aiGenerated,
    durationMs = durationMs,
    versionNumber = versionNumber,
    audioUrl = audioUrl,
    wavUrl = wavUrl,
    storagePath = storagePath,
    providerName = providerName,
    estimatedCostUsd = estimatedCostUsd,
    instruments = StudioJsonCodec.decodeStringList(instrumentsJson),
    vocalStyle = vocalStyle,
    errorMessage = errorMessage,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun SongRecord.toEntity(resolvedId: String, createdAtMs: Long, updatedAtMs: Long): SongEntity = SongEntity(
    id = resolvedId,
    title = title.trim().ifBlank { "Untitled Track" }.take(120),
    prompt = prompt.take(2000),
    lyrics = lyrics.take(10000),
    genre = genre.ifBlank { "Country" }.take(60),
    mood = mood.ifBlank { "Soulful" }.take(60),
    bpm = bpm.coerceIn(40L, 240L),
    vocalType = vocalType,
    mode = mode,
    status = status,
    aiGenerated = aiGenerated,
    durationMs = durationMs.coerceIn(0L, 3600000L),
    versionNumber = versionNumber.coerceIn(1L, 50L),
    audioUrl = audioUrl?.take(1000),
    wavUrl = wavUrl?.take(1000),
    storagePath = storagePath?.take(500),
    providerName = providerName?.take(100),
    estimatedCostUsd = estimatedCostUsd?.coerceAtLeast(0.0),
    instrumentsJson = StudioJsonCodec.encodeStringList(instruments.take(12)),
    vocalStyle = vocalStyle?.take(120),
    errorMessage = errorMessage?.take(1000),
    createdAt = createdAtMs,
    updatedAt = updatedAtMs
)

fun LyricSheetEntity.toDomain(): LyricSheet = LyricSheet(
    id = id,
    title = title,
    genre = genre,
    duetMode = duetMode,
    content = content,
    productionPrompt = productionPrompt,
    singer1Name = singer1Name,
    singer2Name = singer2Name,
    aiGenerated = aiGenerated,
    theme = theme,
    parts = StudioJsonCodec.decodeMapList(partsJson),
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun LyricSheet.toEntity(resolvedId: String, createdAtMs: Long, updatedAtMs: Long): LyricSheetEntity = LyricSheetEntity(
    id = resolvedId,
    title = title.trim().ifBlank { "Untitled Lyrics" }.take(120),
    genre = genre.ifBlank { "Country" }.take(60),
    duetMode = duetMode,
    content = content.trim().ifBlank { "[Verse 1]\nWrite your story here..." }.take(15000),
    productionPrompt = productionPrompt.take(2000),
    singer1Name = singer1Name.ifBlank { "Raven" }.take(60),
    singer2Name = singer2Name.ifBlank { "Breeze" }.take(60),
    aiGenerated = aiGenerated,
    theme = theme?.take(500),
    partsJson = StudioJsonCodec.encodeMapList(parts.take(120)),
    createdAt = createdAtMs,
    updatedAt = updatedAtMs
)

fun MediaAssetEntity.toDomain(): MediaAsset = MediaAsset(
    id = id,
    mediaType = mediaType,
    title = title,
    mimeType = mimeType,
    durationMs = durationMs,
    trimStartMs = trimStartMs,
    trimEndMs = trimEndMs,
    splitPointMs = splitPointMs,
    volume = volume,
    fadeInMs = fadeInMs,
    fadeOutMs = fadeOutMs,
    syncOffsetMs = syncOffsetMs,
    faceDetected = faceDetected,
    faceCount = faceCount,
    aiGenerated = aiGenerated,
    localUri = localUri,
    remoteUrl = remoteUrl,
    storagePath = storagePath,
    fileSizeBytes = fileSizeBytes,
    waveformSamples = StudioJsonCodec.decodeLongList(waveformJson),
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun MediaAsset.toEntity(resolvedId: String, createdAtMs: Long, updatedAtMs: Long): MediaAssetEntity = MediaAssetEntity(
    id = resolvedId,
    mediaType = mediaType,
    title = title.trim().ifBlank { "Studio Asset" }.take(120),
    mimeType = mimeType.ifBlank { "application/octet-stream" }.take(80),
    durationMs = durationMs.coerceIn(0L, 3600000L),
    trimStartMs = trimStartMs.coerceIn(0L, 3600000L),
    trimEndMs = trimEndMs.coerceIn(0L, 3600000L),
    splitPointMs = splitPointMs.coerceIn(0L, 3600000L),
    volume = volume.coerceIn(0.0, 2.0),
    fadeInMs = fadeInMs.coerceIn(0L, 30000L),
    fadeOutMs = fadeOutMs.coerceIn(0L, 30000L),
    syncOffsetMs = syncOffsetMs.coerceIn(-30000L, 30000L),
    faceDetected = faceDetected,
    faceCount = faceCount.coerceIn(0L, 50L),
    aiGenerated = aiGenerated,
    localUri = localUri?.take(1000),
    remoteUrl = remoteUrl?.take(1000),
    storagePath = storagePath?.take(500),
    fileSizeBytes = fileSizeBytes.coerceAtLeast(0L),
    waveformJson = StudioJsonCodec.encodeLongList(waveformSamples.take(128)),
    createdAt = createdAtMs,
    updatedAt = updatedAtMs
)

fun StudioProjectEntity.toDomain(): StudioProject = StudioProject(
    id = id,
    projectType = projectType,
    title = title,
    status = status,
    aspectRatio = aspectRatio,
    resolution = resolution,
    watermarkEnabled = watermarkEnabled,
    aiGenerated = aiGenerated,
    consentConfirmed = consentConfirmed,
    singer1Name = singer1Name,
    singer1VocalRole = singer1VocalRole,
    singer1PortraitUri = singer1PortraitUri,
    singer1FaceDetected = singer1FaceDetected,
    singer2Name = singer2Name,
    singer2VocalRole = singer2VocalRole,
    singer2PortraitUri = singer2PortraitUri,
    singer2FaceDetected = singer2FaceDetected,
    selectedSongTitle = selectedSongTitle,
    selectedAudioUri = selectedAudioUri,
    audioDurationMs = audioDurationMs,
    performanceStyle = performanceStyle,
    performanceIntensity = performanceIntensity,
    useVocalSeparation = useVocalSeparation,
    lipSyncOffsetMs = lipSyncOffsetMs,
    cameraMovement = cameraMovement,
    animationStyle = animationStyle,
    animationPrompt = animationPrompt,
    textOverlay = textOverlay,
    lyricsOverlayEnabled = lyricsOverlayEnabled,
    albumArtUri = albumArtUri,
    transitionStyle = transitionStyle,
    outputVideoUrl = outputVideoUrl,
    errorMessage = errorMessage,
    duetLines = StudioJsonCodec.decodeMapList(duetLinesJson),
    timelineClips = StudioJsonCodec.decodeMapList(timelineClipsJson),
    waveformSamples = StudioJsonCodec.decodeLongList(waveformJson),
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun StudioProject.toEntity(resolvedId: String, createdAtMs: Long, updatedAtMs: Long): StudioProjectEntity = StudioProjectEntity(
    id = resolvedId,
    projectType = projectType,
    title = title.trim().ifBlank { "Untitled Studio Project" }.take(120),
    status = status,
    aspectRatio = aspectRatio,
    resolution = resolution,
    watermarkEnabled = watermarkEnabled,
    aiGenerated = aiGenerated,
    consentConfirmed = consentConfirmed,
    singer1Name = singer1Name?.take(60),
    singer1VocalRole = singer1VocalRole?.take(40),
    singer1PortraitUri = singer1PortraitUri?.take(1000),
    singer1FaceDetected = singer1FaceDetected,
    singer2Name = singer2Name?.take(60),
    singer2VocalRole = singer2VocalRole?.take(40),
    singer2PortraitUri = singer2PortraitUri?.take(1000),
    singer2FaceDetected = singer2FaceDetected,
    selectedSongTitle = selectedSongTitle?.take(120),
    selectedAudioUri = selectedAudioUri?.take(1000),
    audioDurationMs = audioDurationMs?.coerceIn(0L, 3600000L),
    performanceStyle = performanceStyle?.take(60),
    performanceIntensity = performanceIntensity?.coerceIn(0.0, 1.0),
    useVocalSeparation = useVocalSeparation,
    lipSyncOffsetMs = lipSyncOffsetMs?.coerceIn(-10000L, 10000L),
    cameraMovement = cameraMovement?.take(60),
    animationStyle = animationStyle?.take(60),
    animationPrompt = animationPrompt?.take(1000),
    textOverlay = textOverlay?.take(200),
    lyricsOverlayEnabled = lyricsOverlayEnabled,
    albumArtUri = albumArtUri?.take(1000),
    transitionStyle = transitionStyle?.take(40),
    outputVideoUrl = outputVideoUrl?.take(1000),
    errorMessage = errorMessage?.take(1000),
    duetLinesJson = StudioJsonCodec.encodeMapList(duetLines.take(120)),
    timelineClipsJson = StudioJsonCodec.encodeMapList(timelineClips.take(50)),
    waveformJson = StudioJsonCodec.encodeLongList(waveformSamples.take(128)),
    createdAt = createdAtMs,
    updatedAt = updatedAtMs
)

fun GenerationJobEntity.toDomain(): GenerationJob = GenerationJob(
    id = id,
    jobType = jobType,
    title = title,
    providerName = providerName,
    status = status,
    progressPercent = progressPercent,
    estimatedCostUsd = estimatedCostUsd,
    externalJobId = externalJobId,
    projectId = projectId,
    resultUrl = resultUrl,
    errorMessage = errorMessage,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun GenerationJob.toEntity(resolvedId: String, createdAtMs: Long, updatedAtMs: Long): GenerationJobEntity = GenerationJobEntity(
    id = resolvedId,
    jobType = jobType,
    title = title.trim().ifBlank { "Studio Job" }.take(120),
    providerName = providerName.ifBlank { "RavenStudio" }.take(80),
    status = status,
    progressPercent = progressPercent.coerceIn(0L, 100L),
    estimatedCostUsd = estimatedCostUsd.coerceAtLeast(0.0),
    externalJobId = externalJobId?.take(200),
    projectId = projectId?.take(128),
    resultUrl = resultUrl?.take(1000),
    errorMessage = errorMessage?.take(1000),
    createdAt = createdAtMs,
    updatedAt = updatedAtMs
)
