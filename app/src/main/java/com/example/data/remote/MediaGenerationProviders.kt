package com.example.data.remote

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

sealed interface ProviderResult {
    data class Success(
        val jobId: String,
        val providerName: String,
        val audioUrl: String? = null,
        val wavUrl: String? = null,
        val videoUrl: String? = null,
        val durationMs: Long = 0L,
        val estimatedCostUsd: Double = 0.0
    ) : ProviderResult

    data class SetupRequired(
        val providerName: String,
        val capability: String,
        val message: String,
        val estimatedCostUsd: Double = 0.0
    ) : ProviderResult

    data class Failure(
        val providerName: String,
        val message: String
    ) : ProviderResult
}

data class BackendHealthCheckResult(
    val reachable: Boolean = false,
    val authConfigured: Boolean = false,
    val ffmpegAvailable: Boolean = false,
    val huggingFaceReady: Boolean = false,
    val replicateReady: Boolean = false,
    val imageToVideoReady: Boolean = false,
    val lipSyncReady: Boolean = false,
    val summaryMessage: String = ""
)

interface MusicGenerationProvider {
    val providerName: String
    suspend fun submitAndPoll(
        request: MusicGenRequestDto,
        onProgress: suspend (Int) -> Unit
    ): ProviderResult
}

interface LipSyncVideoProvider {
    val providerName: String
    suspend fun submitSingleLipSync(
        request: LipSyncRequestDto,
        onProgress: suspend (Int) -> Unit
    ): ProviderResult

    suspend fun submitDuetPerformance(
        request: DuetVideoRequestDto,
        onProgress: suspend (Int) -> Unit
    ): ProviderResult
}

interface ImageToVideoProvider {
    val providerName: String
    suspend fun submitImageToVideo(
        request: ImageToVideoRequestDto,
        onProgress: suspend (Int) -> Unit
    ): ProviderResult
}

interface MediaProcessingBackend {
    val providerName: String
    suspend fun submitFfmpegExport(
        request: FfmpegExportRequestDto,
        onProgress: suspend (Int) -> Unit
    ): ProviderResult
}

class BackendMediaProviderClient(
    private val appContext: Context
) :
    MusicGenerationProvider,
    LipSyncVideoProvider,
    ImageToVideoProvider,
    MediaProcessingBackend {

    override val providerName: String = "RavenStudioBackend"

    private val baseUrl: String = BuildConfig.STUDIO_BACKEND_BASE_URL.trim()
    private val apiKey: String = BuildConfig.STUDIO_BACKEND_API_KEY.trim()

    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() &&
            baseUrl != "UNCONFIGURED" &&
            (baseUrl.startsWith("https://") || baseUrl.startsWith("http://")) &&
            apiKey.isNotBlank() &&
            apiKey != "UNCONFIGURED"

    private val api: MediaGenerationApi? by lazy {
        if (!isConfigured) return@lazy null
        val normalizedBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val okHttp = OkHttpClient.Builder()
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
        Retrofit.Builder()
            .baseUrl(normalizedBase)
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(MediaGenerationApi::class.java)
    }

    suspend fun checkBackendHealth(): BackendHealthCheckResult {
        val service = api ?: return BackendHealthCheckResult(
            reachable = false,
            summaryMessage = "Backend not configured. Set STUDIO_BACKEND_BASE_URL and STUDIO_BACKEND_API_KEY in AI Studio Secrets."
        )
        return try {
            val health = service.getHealth()
            val summary = buildString {
                append(if (health.ok) "Connected to Render backend" else "Backend responded with error")
                append(" • FFmpeg: ${if (health.ffmpegAvailable) "Ready" else "Missing"}")
                append(" • Replicate (Video/Lip-Sync): ${if (health.providers.replicate) "Ready" else "Missing REPLICATE_API_TOKEN"}")
                append(" • HuggingFace (Music): ${if (health.providers.huggingFaceMusic) "Ready" else "Optional HF_API_TOKEN"}")
            }
            BackendHealthCheckResult(
                reachable = health.ok,
                authConfigured = health.authConfigured,
                ffmpegAvailable = health.ffmpegAvailable,
                huggingFaceReady = health.providers.huggingFaceMusic,
                replicateReady = health.providers.replicate,
                imageToVideoReady = health.capabilities.imageToVideo,
                lipSyncReady = health.capabilities.lipSync,
                summaryMessage = summary
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            BackendHealthCheckResult(
                reachable = false,
                summaryMessage = "Could not reach backend (/health): ${e.localizedMessage ?: "network error"}"
            )
        }
    }

    override suspend fun submitAndPoll(
        request: MusicGenRequestDto,
        onProgress: suspend (Int) -> Unit
    ): ProviderResult {
        val service = api ?: return unconfiguredResult(
            capability = "Licensed AI Music Generation (${request.genre} • ${request.vocalType})",
            estimatedCost = 0.12 * request.versionCount
        )
        return executeAndPoll(
            capabilityName = "AI Music Generation",
            submitCall = { service.generateMusic("Bearer $apiKey", request) },
            service = service,
            onProgress = onProgress
        )
    }

    override suspend fun submitSingleLipSync(
        request: LipSyncRequestDto,
        onProgress: suspend (Int) -> Unit
    ): ProviderResult {
        val service = api ?: return unconfiguredResult(
            capability = "Audio-Driven Facial Lip-Sync Video (${request.aspectRatio})",
            estimatedCost = 0.35
        )
        return executeAndPoll(
            capabilityName = "Photo-to-Singing Lip-Sync",
            submitCall = {
                onProgress(5)
                val remotePortrait = ensureReachableMediaUrl(service, request.portraitUrl, "portrait.jpg", "image/jpeg")
                val remoteAudio = ensureReachableMediaUrl(service, request.audioUrl, "audio.mp3", "audio/mpeg")
                service.generateLipSyncVideo(
                    "Bearer $apiKey",
                    request.copy(portraitUrl = remotePortrait, audioUrl = remoteAudio)
                )
            },
            service = service,
            onProgress = onProgress
        )
    }

    override suspend fun submitDuetPerformance(
        request: DuetVideoRequestDto,
        onProgress: suspend (Int) -> Unit
    ): ProviderResult {
        val service = api ?: return unconfiguredResult(
            capability = "Raven & Breeze Duet Multi-Scene Lip-Sync & FFmpeg Compositing (${request.performanceStyle})",
            estimatedCost = 0.65
        )
        return executeAndPoll(
            capabilityName = "Raven & Breeze Duet Generation",
            submitCall = {
                onProgress(5)
                val remoteP1 = ensureReachableMediaUrl(service, request.singer1PortraitUrl, "singer1.jpg", "image/jpeg")
                val remoteP2 = ensureReachableMediaUrl(service, request.singer2PortraitUrl, "singer2.jpg", "image/jpeg")
                val remoteAudio = ensureReachableMediaUrl(service, request.audioUrl, "duet.mp3", "audio/mpeg")
                service.generateDuetPerformance(
                    "Bearer $apiKey",
                    request.copy(
                        singer1PortraitUrl = remoteP1,
                        singer2PortraitUrl = remoteP2,
                        audioUrl = remoteAudio
                    )
                )
            },
            service = service,
            onProgress = onProgress
        )
    }

    override suspend fun submitImageToVideo(
        request: ImageToVideoRequestDto,
        onProgress: suspend (Int) -> Unit
    ): ProviderResult {
        val service = api ?: return unconfiguredResult(
            capability = "Image-to-Video Generation (${request.cameraMovement} • ${request.animationStyle})",
            estimatedCost = 0.40
        )
        return executeAndPoll(
            capabilityName = "Image-to-Video",
            submitCall = {
                onProgress(5)
                val remoteImage = ensureReachableMediaUrl(service, request.imageUrl, "frame.jpg", "image/jpeg")
                val remoteAudio = request.audioUrl
                    ?.takeIf { it.isNotBlank() }
                    ?.let { ensureReachableMediaUrl(service, it, "track.mp3", "audio/mpeg") }
                service.generateImageToVideo(
                    "Bearer $apiKey",
                    request.copy(imageUrl = remoteImage, audioUrl = remoteAudio)
                )
            },
            service = service,
            onProgress = onProgress
        )
    }

    override suspend fun submitFfmpegExport(
        request: FfmpegExportRequestDto,
        onProgress: suspend (Int) -> Unit
    ): ProviderResult {
        val service = api ?: return unconfiguredResult(
            capability = "Server-Side FFmpeg Audio/Video Export (${request.resolution} • ${request.aspectRatio})",
            estimatedCost = 0.05
        )
        return executeAndPoll(
            capabilityName = "FFmpeg Studio Export",
            submitCall = {
                onProgress(5)
                val remoteAudio = request.audioUrl
                    ?.takeIf { it.isNotBlank() }
                    ?.let { ensureReachableMediaUrl(service, it, "audio.mp3", "audio/mpeg") }
                service.exportFfmpegProject(
                    "Bearer $apiKey",
                    request.copy(audioUrl = remoteAudio)
                )
            },
            service = service,
            onProgress = onProgress
        )
    }

    private suspend fun ensureReachableMediaUrl(
        service: MediaGenerationApi,
        uriOrUrl: String,
        fallbackFileName: String,
        fallbackMimeType: String
    ): String {
        val trimmed = uriOrUrl.trim()
        if (trimmed.startsWith("https://") || trimmed.startsWith("http://")) {
            return trimmed
        }

        val (bytes, fileName, mimeType) = withContext(Dispatchers.IO) {
            val parsed = Uri.parse(trimmed)
            val resolvedBytes: ByteArray
            val resolvedName: String
            val resolvedMime: String

            when {
                trimmed.startsWith("file://") || trimmed.startsWith("/") -> {
                    val file = File(if (trimmed.startsWith("file://")) parsed.path.orEmpty() else trimmed)
                    if (!file.exists()) {
                        throw IllegalStateException("Local media file does not exist: ${file.name}")
                    }
                    if (file.length() > MAX_UPLOAD_BYTES) {
                        throw IllegalStateException("File '${file.name}' exceeds 24 MB remote upload limit.")
                    }
                    resolvedBytes = file.readBytes()
                    resolvedName = file.name.ifBlank { fallbackFileName }
                    val ext = file.extension.lowercase()
                    resolvedMime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: fallbackMimeType
                }
                else -> {
                    resolvedBytes = appContext.contentResolver.openInputStream(parsed)?.use { input ->
                        val data = input.readBytes()
                        if (data.size > MAX_UPLOAD_BYTES) {
                            throw IllegalStateException("Selected media file exceeds 24 MB remote upload limit.")
                        }
                        data
                    } ?: throw IllegalStateException("Cannot read selected local media URI.")
                    resolvedMime = appContext.contentResolver.getType(parsed) ?: fallbackMimeType
                    resolvedName = queryDisplayName(parsed) ?: fallbackFileName
                }
            }
            Triple(resolvedBytes, resolvedName, resolvedMime)
        }

        val sanitizedName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80).ifBlank { fallbackFileName }
        val requestBody = bytes.toRequestBody(mimeType.toMediaType())
        val response = service.uploadMediaBytes(
            authHeader = "Bearer $apiKey",
            fileName = sanitizedName,
            body = requestBody
        )
        return response.url
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
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

    private fun unconfiguredResult(capability: String, estimatedCost: Double): ProviderResult.SetupRequired {
        return ProviderResult.SetupRequired(
            providerName = providerName,
            capability = capability,
            estimatedCostUsd = estimatedCost,
            message = "API Configuration Required: $capability needs your Render backend URL & bearer key. Deploy /backend on Render Free Tier (root render.yaml) and enter STUDIO_BACKEND_BASE_URL + STUDIO_BACKEND_API_KEY in AI Studio Secrets."
        )
    }

    private suspend fun executeAndPoll(
        capabilityName: String,
        submitCall: suspend () -> RemoteJobResponseDto,
        service: MediaGenerationApi,
        onProgress: suspend (Int) -> Unit
    ): ProviderResult {
        return try {
            onProgress(10)
            var current = submitCall()
            onProgress(current.progressPercent.coerceIn(10, 95))

            var attempts = 0
            while (current.status in listOf("QUEUED", "PROCESSING") && attempts < 90) {
                delay(2000L)
                attempts++
                current = service.getJobStatus("Bearer $apiKey", current.jobId)
                onProgress(current.progressPercent.coerceIn(15, 95))
            }

            when (current.status) {
                "COMPLETED" -> {
                    onProgress(100)
                    ProviderResult.Success(
                        jobId = current.jobId,
                        providerName = current.providerName,
                        audioUrl = current.resultAudioUrl,
                        wavUrl = current.resultWavUrl,
                        videoUrl = current.resultVideoUrl,
                        durationMs = current.durationMs,
                        estimatedCostUsd = current.estimatedCostUsd
                    )
                }
                "FAILED" -> ProviderResult.Failure(
                    providerName = current.providerName,
                    message = current.errorMessage ?: "$capabilityName failed on remote provider."
                )
                else -> ProviderResult.Failure(
                    providerName = current.providerName,
                    message = "$capabilityName timed out waiting for remote rendering."
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "$capabilityName request failed", e)
            ProviderResult.Failure(
                providerName = providerName,
                message = e.localizedMessage ?: "$capabilityName network error."
            )
        }
    }

    private companion object {
        const val TAG = "MediaProviderClient"
        const val MAX_UPLOAD_BYTES = 24 * 1024 * 1024
    }
}
