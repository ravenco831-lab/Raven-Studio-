package com.example.model

data class SongRecord(
    val id: String = "",
    val title: String = "",
    val prompt: String = "",
    val lyrics: String = "",
    val genre: String = "Country",
    val mood: String = "Soulful",
    val bpm: Long = 100L,
    val vocalType: String = "DUET",
    val mode: String = "TEXT_TO_SONG",
    val status: String = "DRAFT",
    val aiGenerated: Boolean = true,
    val durationMs: Long = 0L,
    val versionNumber: Long = 1L,
    val audioUrl: String? = null,
    val wavUrl: String? = null,
    val storagePath: String? = null,
    val providerName: String? = null,
    val estimatedCostUsd: Double? = null,
    val instruments: List<String> = emptyList(),
    val vocalStyle: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class DuetLine(
    val id: String = "",
    val timestampMs: Long = 0L,
    val durationMs: Long = 4000L,
    val singer: String = "RAVEN", // RAVEN, BREEZE, BOTH, INSTRUMENTAL
    val lyric: String = ""
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "timestampMs" to timestampMs,
        "durationMs" to durationMs,
        "singer" to singer,
        "lyric" to lyric
    )
}

data class TimelineClip(
    val id: String = "",
    val label: String = "",
    val mediaType: String = "IMAGE", // VIDEO, IMAGE
    val uri: String = "",
    val startMs: Long = 0L,
    val endMs: Long = 5000L,
    val transition: String = "Crossfade"
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "label" to label,
        "mediaType" to mediaType,
        "uri" to uri,
        "startMs" to startMs,
        "endMs" to endMs,
        "transition" to transition
    )
}

data class LyricSheet(
    val id: String = "",
    val title: String = "",
    val genre: String = "Country",
    val duetMode: Boolean = false,
    val content: String = "",
    val productionPrompt: String = "",
    val singer1Name: String = "Raven",
    val singer2Name: String = "Breeze",
    val aiGenerated: Boolean = true,
    val theme: String? = null,
    val parts: List<Map<String, Any>> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class MediaAsset(
    val id: String = "",
    val mediaType: String = "AUDIO", // IMAGE, VIDEO, AUDIO
    val title: String = "",
    val mimeType: String = "audio/mpeg",
    val durationMs: Long = 0L,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val splitPointMs: Long = 0L,
    val volume: Double = 1.0,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L,
    val syncOffsetMs: Long = 0L,
    val faceDetected: Boolean = false,
    val faceCount: Long = 0L,
    val aiGenerated: Boolean = false,
    val localUri: String? = null,
    val remoteUrl: String? = null,
    val storagePath: String? = null,
    val fileSizeBytes: Long = 0L,
    val waveformSamples: List<Long> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class StudioProject(
    val id: String = "",
    val projectType: String = "DUET_RAVEN_BREEZE",
    val title: String = "",
    val status: String = "DRAFT",
    val aspectRatio: String = "9:16",
    val resolution: String = "1080p",
    val watermarkEnabled: Boolean = false,
    val aiGenerated: Boolean = true,
    val consentConfirmed: Boolean = false,
    val singer1Name: String? = "Raven",
    val singer1VocalRole: String? = "FEMALE",
    val singer1PortraitUri: String? = null,
    val singer1FaceDetected: Boolean? = false,
    val singer2Name: String? = "Breeze",
    val singer2VocalRole: String? = "MALE",
    val singer2PortraitUri: String? = null,
    val singer2FaceDetected: Boolean? = false,
    val selectedSongTitle: String? = null,
    val selectedAudioUri: String? = null,
    val audioDurationMs: Long? = 0L,
    val performanceStyle: String? = "Live Concert",
    val performanceIntensity: Double? = 0.75,
    val useVocalSeparation: Boolean? = true,
    val lipSyncOffsetMs: Long? = 0L,
    val cameraMovement: String? = null,
    val animationStyle: String? = null,
    val animationPrompt: String? = null,
    val textOverlay: String? = null,
    val lyricsOverlayEnabled: Boolean? = true,
    val albumArtUri: String? = null,
    val transitionStyle: String? = "Crossfade",
    val outputVideoUrl: String? = null,
    val errorMessage: String? = null,
    val duetLines: List<Map<String, Any>> = emptyList(),
    val timelineClips: List<Map<String, Any>> = emptyList(),
    val waveformSamples: List<Long> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun parsedDuetLines(): List<DuetLine> = duetLines.map { map ->
        DuetLine(
            id = map["id"] as? String ?: "",
            timestampMs = (map["timestampMs"] as? Number)?.toLong() ?: 0L,
            durationMs = (map["durationMs"] as? Number)?.toLong() ?: 4000L,
            singer = map["singer"] as? String ?: "RAVEN",
            lyric = map["lyric"] as? String ?: ""
        )
    }

    fun parsedTimelineClips(): List<TimelineClip> = timelineClips.map { map ->
        TimelineClip(
            id = map["id"] as? String ?: "",
            label = map["label"] as? String ?: "",
            mediaType = map["mediaType"] as? String ?: "IMAGE",
            uri = map["uri"] as? String ?: "",
            startMs = (map["startMs"] as? Number)?.toLong() ?: 0L,
            endMs = (map["endMs"] as? Number)?.toLong() ?: 5000L,
            transition = map["transition"] as? String ?: "Crossfade"
        )
    }
}

data class GenerationJob(
    val id: String = "",
    val jobType: String = "MUSIC_GEN",
    val title: String = "",
    val providerName: String = "",
    val status: String = "QUEUED",
    val progressPercent: Long = 0L,
    val estimatedCostUsd: Double = 0.0,
    val externalJobId: String? = null,
    val projectId: String? = null,
    val resultUrl: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

sealed interface StudioUiState<out T> {
    data object Loading : StudioUiState<Nothing>
    data class Success<T>(val data: T) : StudioUiState<T>
    data class Error(val message: String) : StudioUiState<Nothing>
}
