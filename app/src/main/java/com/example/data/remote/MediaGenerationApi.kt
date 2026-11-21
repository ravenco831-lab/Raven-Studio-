package com.example.data.remote

import com.squareup.moshi.JsonClass
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

@JsonClass(generateAdapter = true)
data class MusicGenRequestDto(
    val mode: String,
    val title: String,
    val prompt: String,
    val lyrics: String,
    val genre: String,
    val mood: String,
    val bpm: Int,
    val vocalType: String,
    val vocalStyle: String,
    val instruments: List<String>,
    val versionCount: Int,
    val extendFromSongId: String? = null
)

@JsonClass(generateAdapter = true)
data class LipSyncRequestDto(
    val portraitUrl: String,
    val audioUrl: String,
    val aspectRatio: String,
    val expressionIntensity: Float,
    val preserveIdentity: Boolean = true,
    val consentConfirmed: Boolean
)

@JsonClass(generateAdapter = true)
data class ImageToVideoRequestDto(
    val imageUrl: String,
    val prompt: String,
    val cameraMovement: String,
    val animationStyle: String,
    val audioUrl: String? = null,
    val audioTrimStartMs: Long = 0L,
    val audioTrimEndMs: Long = 0L,
    val applyLipSyncPostProcess: Boolean = false
)

@JsonClass(generateAdapter = true)
data class DuetSegmentDto(
    val startMs: Long,
    val endMs: Long,
    val activeSinger: String, // RAVEN, BREEZE, BOTH, INSTRUMENTAL
    val lyricLine: String
)

@JsonClass(generateAdapter = true)
data class DuetVideoRequestDto(
    val projectTitle: String,
    val singer1Name: String,
    val singer1VocalRole: String,
    val singer1PortraitUrl: String,
    val singer2Name: String,
    val singer2VocalRole: String,
    val singer2PortraitUrl: String,
    val audioUrl: String,
    val performanceStyle: String,
    val performanceIntensity: Float,
    val useVocalSeparation: Boolean,
    val lipSyncOffsetMs: Long,
    val aspectRatio: String,
    val resolution: String,
    val watermarkBrand: String? = null,
    val segments: List<DuetSegmentDto>,
    val consentConfirmed: Boolean
)

@JsonClass(generateAdapter = true)
data class FfmpegExportRequestDto(
    val projectId: String,
    val aspectRatio: String,
    val resolution: String,
    val watermarkBrand: String? = null,
    val audioUrl: String? = null,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L,
    val volume: Double = 1.0,
    val syncOffsetMs: Long = 0L,
    val textOverlay: String? = null,
    val transitionStyle: String = "Crossfade"
)

@JsonClass(generateAdapter = true)
data class RemoteJobResponseDto(
    val jobId: String,
    val providerName: String,
    val status: String, // QUEUED, PROCESSING, COMPLETED, FAILED
    val progressPercent: Int = 0,
    val estimatedCostUsd: Double = 0.0,
    val resultAudioUrl: String? = null,
    val resultWavUrl: String? = null,
    val resultVideoUrl: String? = null,
    val durationMs: Long = 0L,
    val errorMessage: String? = null
)

@JsonClass(generateAdapter = true)
data class MediaUploadResponseDto(
    val url: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long = 0L
)

@JsonClass(generateAdapter = true)
data class BackendProvidersStatusDto(
    val huggingFaceMusic: Boolean = false,
    val replicate: Boolean = false,
    val customMusicApi: Boolean = false
)

@JsonClass(generateAdapter = true)
data class BackendCapabilitiesStatusDto(
    val mediaUpload: Boolean = false,
    val aiMusic: Boolean = false,
    val lipSync: Boolean = false,
    val imageToVideo: Boolean = false,
    val duetPerformance: Boolean = false,
    val ffmpegExport: Boolean = false
)

@JsonClass(generateAdapter = true)
data class BackendHealthResponseDto(
    val ok: Boolean = false,
    val service: String = "",
    val authConfigured: Boolean = false,
    val ffmpegAvailable: Boolean = false,
    val providers: BackendProvidersStatusDto = BackendProvidersStatusDto(),
    val capabilities: BackendCapabilitiesStatusDto = BackendCapabilitiesStatusDto()
)

interface MediaGenerationApi {
    @GET("health")
    suspend fun getHealth(): BackendHealthResponseDto

    @POST("v1/media/upload")
    suspend fun uploadMediaBytes(
        @Header("Authorization") authHeader: String,
        @Header("X-File-Name") fileName: String,
        @Body body: RequestBody
    ): MediaUploadResponseDto

    @POST("v1/music/generate")
    suspend fun generateMusic(
        @Header("Authorization") authHeader: String,
        @Body body: MusicGenRequestDto
    ): RemoteJobResponseDto

    @POST("v1/video/lipsync")
    suspend fun generateLipSyncVideo(
        @Header("Authorization") authHeader: String,
        @Body body: LipSyncRequestDto
    ): RemoteJobResponseDto

    @POST("v1/video/image-to-video")
    suspend fun generateImageToVideo(
        @Header("Authorization") authHeader: String,
        @Body body: ImageToVideoRequestDto
    ): RemoteJobResponseDto

    @POST("v1/video/duet-performance")
    suspend fun generateDuetPerformance(
        @Header("Authorization") authHeader: String,
        @Body body: DuetVideoRequestDto
    ): RemoteJobResponseDto

    @POST("v1/media/ffmpeg-export")
    suspend fun exportFfmpegProject(
        @Header("Authorization") authHeader: String,
        @Body body: FfmpegExportRequestDto
    ): RemoteJobResponseDto

    @GET("v1/jobs/{jobId}")
    suspend fun getJobStatus(
        @Header("Authorization") authHeader: String,
        @Path("jobId") jobId: String
    ): RemoteJobResponseDto
}
