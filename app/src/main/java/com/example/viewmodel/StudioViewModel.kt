package com.example.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.AudioAnalysisResult
import com.example.core.AudioWaveformExtractor
import com.example.core.FaceAnalysisResult
import com.example.core.FaceDetectorHelper
import com.example.core.LocalStorageStats
import com.example.core.StudioPreferences
import com.example.core.StudioSettings
import com.example.data.remote.BackendHealthCheckResult
import com.example.data.remote.BackendMediaProviderClient
import com.example.data.remote.FfmpegExportRequestDto
import com.example.data.remote.ImageToVideoRequestDto
import com.example.data.remote.LipSyncRequestDto
import com.example.data.remote.MusicGenRequestDto
import com.example.data.remote.ProviderResult
import com.example.data.repository.GeminiLyricsRepository
import com.example.data.repository.StudioRepository
import com.example.model.GenerationJob
import com.example.model.LyricSheet
import com.example.model.MediaAsset
import com.example.model.SongRecord
import com.example.model.StudioProject
import com.example.model.StudioUiState
import com.example.model.TimelineClip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class LyricsStudioState(
    val editingId: String = "",
    val title: String = "",
    val genre: String = "Country",
    val mood: String = "Soulful",
    val topic: String = "",
    val duetMode: Boolean = false,
    val singer1Name: String = "Raven",
    val singer2Name: String = "Breeze",
    val content: String = "",
    val productionPrompt: String = "",
    val rewriteInstruction: String = "",
    val isGenerating: Boolean = false,
    val streamingPreview: String = "",
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

data class MusicGeneratorState(
    val mode: String = "TEXT_TO_SONG",
    val title: String = "",
    val prompt: String = "",
    val lyrics: String = "",
    val genre: String = "Country",
    val mood: String = "Anthemic",
    val bpm: Int = 104,
    val vocalType: String = "DUET",
    val vocalStyle: String = "Warm Nashville Duet Harmonies",
    val instruments: List<String> = listOf("Acoustic Guitar", "Pedal Steel", "Live Drums"),
    val versionCount: Int = 2,
    val extendFromSongId: String? = null,
    val isGenerating: Boolean = false,
    val progressPercent: Int = 0,
    val statusMessage: String? = null,
    val setupRequiredMessage: String? = null,
    val errorMessage: String? = null
)

data class PhotoSingState(
    val title: String = "Portrait Singing Performance",
    val portraitUri: String = "",
    val faceAnalysis: FaceAnalysisResult? = null,
    val isScanningFace: Boolean = false,
    val selectedAudioTitle: String = "",
    val selectedAudioUri: String = "",
    val aspectRatio: String = "9:16",
    val expressionIntensity: Float = 0.75f,
    val consentConfirmed: Boolean = false,
    val outputVideoUrl: String = "",
    val isGenerating: Boolean = false,
    val progressPercent: Int = 0,
    val setupRequiredMessage: String? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

data class ImageToVideoState(
    val title: String = "Cinematic Visual",
    val imageUri: String = "",
    val prompt: String = "",
    val cameraMovement: String = "Slow Zoom In",
    val animationStyle: String = "Cinematic",
    val selectedAudioTitle: String = "",
    val selectedAudioUri: String = "",
    val audioTrimStartMs: Long = 0L,
    val audioTrimEndMs: Long = 30_000L,
    val applyLipSyncPost: Boolean = false,
    val outputVideoUrl: String = "",
    val isGenerating: Boolean = false,
    val progressPercent: Int = 0,
    val setupRequiredMessage: String? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

data class AudioEditorState(
    val editingAssetId: String = "",
    val title: String = "",
    val mimeType: String = "audio/mpeg",
    val uriString: String = "",
    val durationMs: Long = 0L,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val splitPointMs: Long = 0L,
    val volume: Float = 1.0f,
    val fadeInMs: Long = 500L,
    val fadeOutMs: Long = 1000L,
    val syncOffsetMs: Long = 0L,
    val replaceVideoAudio: Boolean = true,
    val waveformSamples: List<Long> = emptyList(),
    val outputVideoUrl: String = "",
    val isAnalyzing: Boolean = false,
    val isExporting: Boolean = false,
    val statusMessage: String? = null,
    val setupRequiredMessage: String? = null,
    val errorMessage: String? = null
)

data class VideoTimelineState(
    val editingProjectId: String = "",
    val title: String = "Raven Studio Cut",
    val clips: List<TimelineClip> = emptyList(),
    val audioTitle: String = "",
    val audioUri: String = "",
    val albumArtUri: String = "",
    val textOverlay: String = "",
    val lyricsOverlayEnabled: Boolean = true,
    val transitionStyle: String = "Crossfade",
    val aspectRatio: String = "9:16",
    val resolution: String = "1080p",
    val watermarkEnabled: Boolean = true,
    val previewVideoUrl: String = "",
    val isExporting: Boolean = false,
    val progressPercent: Int = 0,
    val statusMessage: String? = null,
    val setupRequiredMessage: String? = null,
    val errorMessage: String? = null
)

class StudioViewModel(
    private val repository: StudioRepository,
    private val geminiRepo: GeminiLyricsRepository,
    private val backendClient: BackendMediaProviderClient,
    private val preferences: StudioPreferences
) : ViewModel() {

    val backendConfigured: Boolean
        get() = backendClient.isConfigured

    val settings: StateFlow<StudioSettings> = preferences.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), StudioSettings())

    val songsState: StateFlow<StudioUiState<List<SongRecord>>> =
        repository.observeSongs().toUiState("Failed to load songs")

    val lyricsState: StateFlow<StudioUiState<List<LyricSheet>>> =
        repository.observeLyrics().toUiState("Failed to load lyrics")

    val mediaAssetsState: StateFlow<StudioUiState<List<MediaAsset>>> =
        repository.observeMediaAssets().toUiState("Failed to load media library")

    val projectsState: StateFlow<StudioUiState<List<StudioProject>>> =
        repository.observeProjects().toUiState("Failed to load projects")

    val jobsState: StateFlow<StudioUiState<List<GenerationJob>>> =
        repository.observeJobs().toUiState("Failed to load jobs")

    private val _storageStats = MutableStateFlow(LocalStorageStats())
    val storageStats: StateFlow<LocalStorageStats> = _storageStats.asStateFlow()

    private val _isImportingMedia = MutableStateFlow(false)
    val isImportingMedia: StateFlow<Boolean> = _isImportingMedia.asStateFlow()

    private val _libraryStatusMessage = MutableStateFlow<String?>(null)
    val libraryStatusMessage: StateFlow<String?> = _libraryStatusMessage.asStateFlow()

    private val _backendHealth = MutableStateFlow<BackendHealthCheckResult?>(null)
    val backendHealth: StateFlow<BackendHealthCheckResult?> = _backendHealth.asStateFlow()

    private val _isCheckingBackend = MutableStateFlow(false)
    val isCheckingBackend: StateFlow<Boolean> = _isCheckingBackend.asStateFlow()

    private val _lyricsStudio = MutableStateFlow(LyricsStudioState())
    val lyricsStudio: StateFlow<LyricsStudioState> = _lyricsStudio.asStateFlow()

    private val _musicGenerator = MutableStateFlow(MusicGeneratorState())
    val musicGenerator: StateFlow<MusicGeneratorState> = _musicGenerator.asStateFlow()

    private val _photoSing = MutableStateFlow(PhotoSingState())
    val photoSing: StateFlow<PhotoSingState> = _photoSing.asStateFlow()

    private val _imageToVideo = MutableStateFlow(ImageToVideoState())
    val imageToVideo: StateFlow<ImageToVideoState> = _imageToVideo.asStateFlow()

    private val _audioEditor = MutableStateFlow(AudioEditorState())
    val audioEditor: StateFlow<AudioEditorState> = _audioEditor.asStateFlow()

    private val _videoTimeline = MutableStateFlow(VideoTimelineState())
    val videoTimeline: StateFlow<VideoTimelineState> = _videoTimeline.asStateFlow()

    init {
        refreshStorageStats()
    }

    fun refreshStorageStats() {
        viewModelScope.launch {
            _storageStats.value = repository.getLocalStorageStats()
        }
    }

    fun clearLibraryStatus() {
        _libraryStatusMessage.value = null
    }

    fun testBackendConnection() {
        viewModelScope.launch {
            _isCheckingBackend.value = true
            _backendHealth.value = backendClient.checkBackendHealth()
            _isCheckingBackend.value = false
        }
    }

    private fun <T> Flow<List<T>>.toUiState(fallbackMsg: String): StateFlow<StudioUiState<List<T>>> =
        this.map<List<T>, StudioUiState<List<T>>> { StudioUiState.Success(it) }
            .catch { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, fallbackMsg, error)
                emit(StudioUiState.Error(error.localizedMessage ?: fallbackMsg))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = StudioUiState.Loading
            )

    // --- Unified Local Device Storage Import & Export ---
    fun importDeviceMedia(context: Context, uri: Uri, mediaType: String) {
        viewModelScope.launch {
            _isImportingMedia.value = true
            _libraryStatusMessage.value = "Importing $mediaType into local device vault..."
            try {
                when (mediaType.uppercase()) {
                    "AUDIO" -> {
                        val imported = repository.importUserMediaToDeviceStorage(uri, "AUDIO", "audio")
                        val localUri = imported.localUri ?: uri.toString()
                        val analysis = AudioWaveformExtractor.analyzeAudio(context, Uri.parse(localUri))
                        val title = imported.displayName ?: analysis.title
                        val duration = analysis.durationMs.coerceAtLeast(imported.durationMs)
                        repository.saveMediaAsset(
                            MediaAsset(
                                mediaType = "AUDIO",
                                title = title,
                                mimeType = imported.mimeType ?: analysis.mimeType,
                                durationMs = duration,
                                trimEndMs = duration,
                                aiGenerated = false,
                                localUri = localUri,
                                storagePath = imported.storagePath,
                                fileSizeBytes = imported.sizeBytes,
                                waveformSamples = analysis.waveformSamples
                            )
                        )
                        _libraryStatusMessage.value = imported.warningMessage ?: "Imported audio \"$title\" to My Audio."
                    }
                    "IMAGE" -> {
                        val imported = repository.importUserMediaToDeviceStorage(uri, "IMAGE", "images", "jpg")
                        val localUri = imported.localUri ?: uri.toString()
                        val faceResult = FaceDetectorHelper.detectFaces(context, Uri.parse(localUri))
                        val title = imported.displayName ?: "Studio Portrait"
                        repository.saveMediaAsset(
                            MediaAsset(
                                mediaType = "IMAGE",
                                title = title,
                                mimeType = imported.mimeType ?: context.contentResolver.getType(uri) ?: "image/jpeg",
                                faceDetected = faceResult.faceDetected,
                                faceCount = faceResult.faceCount.toLong(),
                                aiGenerated = false,
                                localUri = localUri,
                                storagePath = imported.storagePath,
                                fileSizeBytes = imported.sizeBytes
                            )
                        )
                        _libraryStatusMessage.value = imported.warningMessage
                            ?: "Imported image \"$title\" (${faceResult.statusMessage})."
                    }
                    else -> {
                        val imported = repository.importUserMediaToDeviceStorage(uri, "VIDEO", "videos", "mp4")
                        val localUri = imported.localUri ?: uri.toString()
                        val title = imported.displayName ?: "Studio Video Clip"
                        repository.saveMediaAsset(
                            MediaAsset(
                                mediaType = "VIDEO",
                                title = title,
                                mimeType = imported.mimeType ?: context.contentResolver.getType(uri) ?: "video/mp4",
                                durationMs = imported.durationMs,
                                trimEndMs = imported.durationMs,
                                aiGenerated = false,
                                localUri = localUri,
                                storagePath = imported.storagePath,
                                fileSizeBytes = imported.sizeBytes
                            )
                        )
                        _libraryStatusMessage.value = imported.warningMessage ?: "Imported video \"$title\" to My Videos."
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _libraryStatusMessage.value = "Import failed: ${e.localizedMessage ?: "unknown error"}"
            } finally {
                _isImportingMedia.value = false
                refreshStorageStats()
            }
        }
    }

    fun exportMediaAssetToDevice(asset: MediaAsset) {
        val source = asset.localUri ?: asset.remoteUrl
        if (source.isNullOrBlank()) {
            _libraryStatusMessage.value = "No local or remote file available for \"${asset.title}\"."
            return
        }
        viewModelScope.launch {
            _libraryStatusMessage.value = "Saving \"${asset.title}\" to device storage..."
            repository.exportMediaToDevice(
                sourceUriOrUrl = source,
                title = asset.title,
                mediaType = asset.mediaType,
                mimeType = asset.mimeType
            ).onSuccess { msg ->
                _libraryStatusMessage.value = msg
            }.onFailure { err ->
                _libraryStatusMessage.value = "Save failed: ${err.localizedMessage ?: "unknown error"}"
            }
        }
    }

    fun exportSongToDevice(song: SongRecord) {
        val source = song.wavUrl ?: song.audioUrl
        if (source.isNullOrBlank()) {
            _libraryStatusMessage.value = "No audio file generated yet for \"${song.title}\"."
            return
        }
        val mime = if (source.endsWith(".wav", ignoreCase = true)) "audio/wav" else "audio/mpeg"
        viewModelScope.launch {
            _libraryStatusMessage.value = "Exporting \"${song.title}\"..."
            repository.exportMediaToDevice(source, song.title, "AUDIO", mime)
                .onSuccess { msg -> _libraryStatusMessage.value = msg }
                .onFailure { err -> _libraryStatusMessage.value = "Export failed: ${err.localizedMessage}" }
        }
    }

    fun exportLyricsToDevice(sheet: LyricSheet) {
        viewModelScope.launch {
            val fullText = buildString {
                appendLine(sheet.title)
                appendLine("${sheet.genre} • ${if (sheet.duetMode) "${sheet.singer1Name} & ${sheet.singer2Name}" else "Solo"}")
                appendLine()
                appendLine(sheet.content)
                if (sheet.productionPrompt.isNotBlank()) {
                    appendLine()
                    appendLine("--- Production Prompt ---")
                    appendLine(sheet.productionPrompt)
                }
            }
            repository.exportLyricsToDevice(sheet.title, fullText)
                .onSuccess { msg -> _libraryStatusMessage.value = msg }
                .onFailure { err -> _libraryStatusMessage.value = "Export failed: ${err.localizedMessage}" }
        }
    }

    fun shareMediaAsset(context: Context, asset: MediaAsset) {
        val source = asset.localUri ?: asset.remoteUrl
        if (source.isNullOrBlank()) {
            _libraryStatusMessage.value = "No file available to share."
            return
        }
        viewModelScope.launch {
            repository.prepareShareIntent(source, asset.title, asset.mimeType)
                .onSuccess { chooser ->
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)
                }
                .onFailure { err ->
                    _libraryStatusMessage.value = "Share failed: ${err.localizedMessage}"
                }
        }
    }

    fun shareLyricsSheet(context: Context, sheet: LyricSheet) {
        val chooser = repository.prepareShareTextIntent(sheet.title, sheet.content)
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun shareUriOrUrl(context: Context, uriOrUrl: String, title: String, mimeType: String) {
        if (uriOrUrl.isBlank()) return
        viewModelScope.launch {
            repository.prepareShareIntent(uriOrUrl, title, mimeType)
                .onSuccess { chooser ->
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)
                }
                .onFailure { err ->
                    _libraryStatusMessage.value = "Share failed: ${err.localizedMessage}"
                }
        }
    }

    fun cleanupUnusedLocalStorage() {
        viewModelScope.launch {
            val removed = repository.cleanupUnusedMediaFiles()
            refreshStorageStats()
            _libraryStatusMessage.value = if (removed == 0) {
                "Local studio storage is already clean."
            } else {
                "Removed $removed unreferenced media file(s)."
            }
        }
    }

    // --- Lyrics Studio Actions ---
    fun updateLyricsForm(update: (LyricsStudioState) -> LyricsStudioState) {
        _lyricsStudio.update(update)
    }

    fun loadLyricSheet(sheet: LyricSheet) {
        _lyricsStudio.value = LyricsStudioState(
            editingId = sheet.id,
            title = sheet.title,
            genre = sheet.genre,
            mood = "Soulful",
            topic = sheet.theme.orEmpty(),
            duetMode = sheet.duetMode,
            singer1Name = sheet.singer1Name,
            singer2Name = sheet.singer2Name,
            content = sheet.content,
            productionPrompt = sheet.productionPrompt
        )
    }

    fun streamAndGenerateLyrics() {
        val state = _lyricsStudio.value
        val topic = state.topic.ifBlank { "late-night highway dreams and redemption" }
        viewModelScope.launch {
            _lyricsStudio.update {
                it.copy(
                    isGenerating = true,
                    streamingPreview = "",
                    errorMessage = null,
                    statusMessage = "Composing with Gemini AI..."
                )
            }
            try {
                geminiRepo.streamLyricsDraft(
                    genre = state.genre,
                    mood = state.mood,
                    topic = topic,
                    duetMode = state.duetMode,
                    singer1Name = state.singer1Name,
                    singer2Name = state.singer2Name
                ).collect { partial ->
                    _lyricsStudio.update { it.copy(streamingPreview = partial) }
                }

                val result = geminiRepo.generateStructuredLyrics(
                    genre = state.genre,
                    mood = state.mood,
                    topic = topic,
                    duetMode = state.duetMode,
                    singer1Name = state.singer1Name,
                    singer2Name = state.singer2Name
                )
                result.onSuccess { pkg ->
                    _lyricsStudio.update {
                        it.copy(
                            title = it.title.ifBlank { pkg.title },
                            content = pkg.content.ifBlank { it.streamingPreview },
                            productionPrompt = pkg.productionPrompt,
                            isGenerating = false,
                            streamingPreview = "",
                            statusMessage = "Original lyrics & production prompt ready."
                        )
                    }
                }.onFailure { err ->
                    _lyricsStudio.update {
                        it.copy(
                            content = it.streamingPreview.ifBlank { it.content },
                            isGenerating = false,
                            errorMessage = err.localizedMessage ?: "Lyrics generation failed."
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _lyricsStudio.update {
                    it.copy(
                        isGenerating = false,
                        errorMessage = e.localizedMessage ?: "Gemini streaming failed."
                    )
                }
            }
        }
    }

    fun rewriteLyricsWithGemini() {
        val state = _lyricsStudio.value
        if (state.content.isBlank()) {
            _lyricsStudio.update { it.copy(errorMessage = "Enter or generate lyrics before rewriting.") }
            return
        }
        val instruction = state.rewriteInstruction.ifBlank {
            "Sharpen emotional imagery, tighten meter, and make the chorus soar."
        }
        viewModelScope.launch {
            _lyricsStudio.update {
                it.copy(isGenerating = true, errorMessage = null, statusMessage = "Rewriting lyrics with Gemini...")
            }
            val result = geminiRepo.rewriteAndImproveLyrics(
                existingLyrics = state.content,
                instruction = instruction,
                genre = state.genre,
                duetMode = state.duetMode,
                singer1Name = state.singer1Name,
                singer2Name = state.singer2Name
            )
            result.onSuccess { pkg ->
                _lyricsStudio.update {
                    it.copy(
                        title = it.title.ifBlank { pkg.title },
                        content = pkg.content,
                        productionPrompt = pkg.productionPrompt.ifBlank { it.productionPrompt },
                        isGenerating = false,
                        statusMessage = "Lyrics rewritten and polished."
                    )
                }
            }.onFailure { err ->
                _lyricsStudio.update {
                    it.copy(
                        isGenerating = false,
                        errorMessage = err.localizedMessage ?: "Rewrite failed."
                    )
                }
            }
        }
    }

    fun saveCurrentLyrics() {
        val state = _lyricsStudio.value
        if (state.content.isBlank()) {
            _lyricsStudio.update { it.copy(errorMessage = "Nothing to save yet.") }
            return
        }
        viewModelScope.launch {
            val sheet = LyricSheet(
                id = state.editingId,
                title = state.title.ifBlank { "Untitled Lyrics" },
                genre = state.genre,
                duetMode = state.duetMode,
                content = state.content,
                productionPrompt = state.productionPrompt,
                singer1Name = state.singer1Name.ifBlank { "Raven" },
                singer2Name = state.singer2Name.ifBlank { "Breeze" },
                aiGenerated = true,
                theme = state.topic.ifBlank { null }
            )
            repository.saveLyricSheet(sheet)
                .onSuccess { savedId ->
                    _lyricsStudio.update {
                        it.copy(
                            editingId = savedId,
                            statusMessage = "Lyrics saved to My Lyrics library.",
                            errorMessage = null
                        )
                    }
                }
                .onFailure { err ->
                    _lyricsStudio.update {
                        it.copy(errorMessage = err.localizedMessage ?: "Failed to save lyrics.")
                    }
                }
        }
    }

    fun sendLyricsToMusicGenerator() {
        val state = _lyricsStudio.value
        _musicGenerator.update {
            it.copy(
                mode = "LYRICS_TO_SONG",
                title = state.title.ifBlank { "New Studio Track" },
                lyrics = state.content,
                prompt = state.productionPrompt.ifBlank { "${state.genre} ${state.mood} song" },
                genre = state.genre,
                mood = state.mood,
                vocalType = if (state.duetMode) "DUET" else "FEMALE"
            )
        }
    }

    // --- AI Music Generator Actions ---
    fun updateMusicForm(update: (MusicGeneratorState) -> MusicGeneratorState) {
        _musicGenerator.update(update)
    }

    fun enhanceMusicPromptWithGemini() {
        val state = _musicGenerator.value
        val topic = state.prompt.ifBlank { state.title.ifBlank { "A heartfelt ${state.genre} anthem" } }
        viewModelScope.launch {
            _musicGenerator.update { it.copy(isGenerating = true, errorMessage = null) }
            val result = geminiRepo.generateStructuredLyrics(
                genre = state.genre,
                mood = state.mood,
                topic = topic,
                duetMode = state.vocalType == "DUET",
                singer1Name = "Raven",
                singer2Name = "Breeze",
                bpm = state.bpm
            )
            result.onSuccess { pkg ->
                _musicGenerator.update {
                    it.copy(
                        title = it.title.ifBlank { pkg.title },
                        prompt = pkg.productionPrompt,
                        lyrics = if (it.mode != "INSTRUMENTAL" && it.lyrics.isBlank()) pkg.content else it.lyrics,
                        isGenerating = false,
                        statusMessage = "Gemini crafted a studio production brief."
                    )
                }
            }.onFailure { err ->
                _musicGenerator.update {
                    it.copy(isGenerating = false, errorMessage = err.localizedMessage ?: "Prompt helper failed.")
                }
            }
        }
    }

    fun submitMusicGeneration(regenerateSong: SongRecord? = null) {
        val state = _musicGenerator.value
        val title = regenerateSong?.title ?: state.title.ifBlank { "${state.genre} Session" }
        val prompt = regenerateSong?.prompt ?: state.prompt.ifBlank { "${state.mood} ${state.genre} at ${state.bpm} BPM" }
        val lyrics = regenerateSong?.lyrics ?: state.lyrics
        if (state.mode == "LYRICS_TO_SONG" && lyrics.isBlank()) {
            _musicGenerator.update { it.copy(errorMessage = "Please enter or generate lyrics first.") }
            return
        }

        viewModelScope.launch {
            _musicGenerator.update {
                it.copy(
                    isGenerating = true,
                    progressPercent = 5,
                    errorMessage = null,
                    setupRequiredMessage = null,
                    statusMessage = "Submitting song generation job..."
                )
            }

            val songId = repository.saveSong(
                SongRecord(
                    title = title,
                    prompt = prompt,
                    lyrics = lyrics,
                    genre = state.genre,
                    mood = state.mood,
                    bpm = state.bpm.toLong(),
                    vocalType = state.vocalType,
                    mode = state.mode,
                    status = "QUEUED",
                    aiGenerated = true,
                    versionNumber = (regenerateSong?.versionNumber ?: 0L) + 1L,
                    instruments = state.instruments,
                    vocalStyle = state.vocalStyle,
                    providerName = backendClient.providerName
                )
            ).getOrNull().orEmpty()

            val jobId = repository.saveJob(
                GenerationJob(
                    jobType = "MUSIC_GEN",
                    title = title,
                    providerName = backendClient.providerName,
                    status = "QUEUED",
                    progressPercent = 5L,
                    estimatedCostUsd = 0.12 * state.versionCount,
                    projectId = songId.ifBlank { null }
                )
            ).getOrNull().orEmpty()

            val result = backendClient.submitAndPoll(
                MusicGenRequestDto(
                    mode = state.mode,
                    title = title,
                    prompt = prompt,
                    lyrics = lyrics,
                    genre = state.genre,
                    mood = state.mood,
                    bpm = state.bpm,
                    vocalType = state.vocalType,
                    vocalStyle = state.vocalStyle,
                    instruments = state.instruments,
                    versionCount = state.versionCount,
                    extendFromSongId = regenerateSong?.id
                )
            ) { progress ->
                _musicGenerator.update { it.copy(progressPercent = progress) }
                if (jobId.isNotBlank()) {
                    repository.saveJob(
                        GenerationJob(
                            id = jobId,
                            jobType = "MUSIC_GEN",
                            title = title,
                            providerName = backendClient.providerName,
                            status = "PROCESSING",
                            progressPercent = progress.toLong(),
                            estimatedCostUsd = 0.12 * state.versionCount,
                            projectId = songId.ifBlank { null }
                        )
                    )
                }
            }

            when (result) {
                is ProviderResult.Success -> {
                    if (songId.isNotBlank()) {
                        repository.saveSong(
                            SongRecord(
                                id = songId,
                                title = title,
                                prompt = prompt,
                                lyrics = lyrics,
                                genre = state.genre,
                                mood = state.mood,
                                bpm = state.bpm.toLong(),
                                vocalType = state.vocalType,
                                mode = state.mode,
                                status = "READY",
                                aiGenerated = true,
                                durationMs = result.durationMs,
                                versionNumber = (regenerateSong?.versionNumber ?: 0L) + 1L,
                                audioUrl = result.audioUrl,
                                wavUrl = result.wavUrl,
                                providerName = result.providerName,
                                estimatedCostUsd = result.estimatedCostUsd,
                                instruments = state.instruments,
                                vocalStyle = state.vocalStyle
                            )
                        )
                    }
                    if (jobId.isNotBlank()) {
                        repository.saveJob(
                            GenerationJob(
                                id = jobId,
                                jobType = "MUSIC_GEN",
                                title = title,
                                providerName = result.providerName,
                                status = "COMPLETED",
                                progressPercent = 100L,
                                estimatedCostUsd = result.estimatedCostUsd,
                                externalJobId = result.jobId,
                                resultUrl = result.audioUrl
                            )
                        )
                    }
                    _musicGenerator.update {
                        it.copy(
                            isGenerating = false,
                            progressPercent = 100,
                            statusMessage = "Track ready in My Songs!"
                        )
                    }
                }
                is ProviderResult.SetupRequired -> {
                    if (songId.isNotBlank()) {
                        repository.saveSong(
                            SongRecord(
                                id = songId,
                                title = title,
                                prompt = prompt,
                                lyrics = lyrics,
                                genre = state.genre,
                                mood = state.mood,
                                bpm = state.bpm.toLong(),
                                vocalType = state.vocalType,
                                mode = state.mode,
                                status = "SETUP_REQUIRED",
                                aiGenerated = true,
                                versionNumber = (regenerateSong?.versionNumber ?: 0L) + 1L,
                                providerName = result.providerName,
                                estimatedCostUsd = result.estimatedCostUsd,
                                instruments = state.instruments,
                                vocalStyle = state.vocalStyle,
                                errorMessage = result.message
                            )
                        )
                    }
                    if (jobId.isNotBlank()) {
                        repository.saveJob(
                            GenerationJob(
                                id = jobId,
                                jobType = "MUSIC_GEN",
                                title = title,
                                providerName = result.providerName,
                                status = "SETUP_REQUIRED",
                                progressPercent = 0L,
                                estimatedCostUsd = result.estimatedCostUsd,
                                errorMessage = result.message
                            )
                        )
                    }
                    _musicGenerator.update {
                        it.copy(
                            isGenerating = false,
                            progressPercent = 0,
                            setupRequiredMessage = result.message,
                            statusMessage = "Song brief saved as draft in My Songs."
                        )
                    }
                }
                is ProviderResult.Failure -> {
                    if (jobId.isNotBlank()) {
                        repository.saveJob(
                            GenerationJob(
                                id = jobId,
                                jobType = "MUSIC_GEN",
                                title = title,
                                providerName = result.providerName,
                                status = "FAILED",
                                progressPercent = 0L,
                                estimatedCostUsd = 0.0,
                                errorMessage = result.message
                            )
                        )
                    }
                    _musicGenerator.update {
                        it.copy(isGenerating = false, errorMessage = result.message)
                    }
                }
            }
        }
    }

    // --- Photo-to-Singing Actions ---
    fun updatePhotoSing(update: (PhotoSingState) -> PhotoSingState) {
        _photoSing.update(update)
    }

    fun onPhotoSingPortraitPicked(context: Context, uri: Uri) {
        viewModelScope.launch {
            _photoSing.update {
                it.copy(
                    portraitUri = uri.toString(),
                    isScanningFace = true,
                    errorMessage = null,
                    statusMessage = "Copying portrait & scanning face with ML Kit..."
                )
            }
            val imported = repository.importUserMediaToDeviceStorage(uri, "IMAGE", "portraits", "jpg")
            val finalUri = imported.localUri ?: uri.toString()
            val faceResult = FaceDetectorHelper.detectFaces(context, Uri.parse(finalUri))
            repository.saveMediaAsset(
                MediaAsset(
                    mediaType = "IMAGE",
                    title = imported.displayName ?: "Singing Portrait",
                    mimeType = imported.mimeType ?: context.contentResolver.getType(uri) ?: "image/jpeg",
                    faceDetected = faceResult.faceDetected,
                    faceCount = faceResult.faceCount.toLong(),
                    aiGenerated = false,
                    localUri = finalUri,
                    storagePath = imported.storagePath,
                    fileSizeBytes = imported.sizeBytes
                )
            )
            refreshStorageStats()
            _photoSing.update {
                it.copy(
                    portraitUri = finalUri,
                    faceAnalysis = faceResult,
                    isScanningFace = false,
                    statusMessage = imported.warningMessage ?: faceResult.statusMessage
                )
            }
        }
    }

    fun onPhotoSingAudioPicked(context: Context, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val imported = repository.importUserMediaToDeviceStorage(uri, "AUDIO", "audio")
            val finalUri = imported.localUri ?: uri.toString()
            val info = AudioWaveformExtractor.analyzeAudio(context, Uri.parse(finalUri))
            val title = imported.displayName ?: info.title
            repository.saveMediaAsset(
                MediaAsset(
                    mediaType = "AUDIO",
                    title = title,
                    mimeType = imported.mimeType ?: info.mimeType,
                    durationMs = info.durationMs.coerceAtLeast(imported.durationMs),
                    trimEndMs = info.durationMs.coerceAtLeast(imported.durationMs),
                    aiGenerated = false,
                    localUri = finalUri,
                    storagePath = imported.storagePath,
                    fileSizeBytes = imported.sizeBytes,
                    waveformSamples = info.waveformSamples
                )
            )
            refreshStorageStats()
            _photoSing.update {
                it.copy(
                    selectedAudioTitle = title,
                    selectedAudioUri = finalUri,
                    statusMessage = imported.warningMessage ?: "Audio saved & loaded: $title"
                )
            }
        }
    }

    fun submitPhotoToSinging() {
        val state = _photoSing.value
        if (state.portraitUri.isBlank()) {
            _photoSing.update { it.copy(errorMessage = "Upload a portrait image first.") }
            return
        }
        if (state.selectedAudioUri.isBlank()) {
            _photoSing.update { it.copy(errorMessage = "Select a song or upload an audio track.") }
            return
        }
        if (!state.consentConfirmed) {
            _photoSing.update { it.copy(errorMessage = "Please confirm portrait & audio animation consent before generating.") }
            return
        }

        viewModelScope.launch {
            _photoSing.update {
                it.copy(isGenerating = true, progressPercent = 10, errorMessage = null, setupRequiredMessage = null)
            }
            val projectId = repository.saveProject(
                StudioProject(
                    projectType = "PHOTO_SING",
                    title = state.title,
                    status = "QUEUED",
                    aspectRatio = state.aspectRatio,
                    resolution = settings.value.defaultResolution,
                    watermarkEnabled = settings.value.defaultWatermark,
                    aiGenerated = true,
                    consentConfirmed = true,
                    singer1PortraitUri = state.portraitUri,
                    singer1FaceDetected = state.faceAnalysis?.faceDetected ?: false,
                    selectedSongTitle = state.selectedAudioTitle,
                    selectedAudioUri = state.selectedAudioUri,
                    performanceIntensity = state.expressionIntensity.toDouble()
                )
            ).getOrNull().orEmpty()

            val result = backendClient.submitSingleLipSync(
                LipSyncRequestDto(
                    portraitUrl = state.portraitUri,
                    audioUrl = state.selectedAudioUri,
                    aspectRatio = state.aspectRatio,
                    expressionIntensity = state.expressionIntensity,
                    consentConfirmed = true
                )
            ) { p -> _photoSing.update { it.copy(progressPercent = p) } }

            when (result) {
                is ProviderResult.Success -> {
                    val videoUrl = result.videoUrl.orEmpty()
                    if (projectId.isNotBlank()) {
                        repository.saveProject(
                            StudioProject(
                                id = projectId,
                                projectType = "PHOTO_SING",
                                title = state.title,
                                status = "READY",
                                aspectRatio = state.aspectRatio,
                                resolution = settings.value.defaultResolution,
                                watermarkEnabled = settings.value.defaultWatermark,
                                aiGenerated = true,
                                consentConfirmed = true,
                                singer1PortraitUri = state.portraitUri,
                                singer1FaceDetected = state.faceAnalysis?.faceDetected ?: false,
                                selectedSongTitle = state.selectedAudioTitle,
                                selectedAudioUri = state.selectedAudioUri,
                                performanceIntensity = state.expressionIntensity.toDouble(),
                                outputVideoUrl = videoUrl.ifBlank { null }
                            )
                        )
                    }
                    if (videoUrl.isNotBlank()) {
                        repository.saveMediaAsset(
                            MediaAsset(
                                mediaType = "VIDEO",
                                title = state.title,
                                mimeType = "video/mp4",
                                aiGenerated = true,
                                remoteUrl = videoUrl
                            )
                        )
                    }
                    _photoSing.update {
                        it.copy(
                            isGenerating = false,
                            progressPercent = 100,
                            outputVideoUrl = videoUrl,
                            statusMessage = "Singing video ready! Saved to My Videos."
                        )
                    }
                }
                is ProviderResult.SetupRequired -> {
                    repository.saveJob(
                        GenerationJob(
                            jobType = "PHOTO_LIPSYNC",
                            title = state.title,
                            providerName = result.providerName,
                            status = "SETUP_REQUIRED",
                            progressPercent = 0L,
                            estimatedCostUsd = result.estimatedCostUsd,
                            projectId = projectId.ifBlank { null },
                            errorMessage = result.message
                        )
                    )
                    _photoSing.update {
                        it.copy(
                            isGenerating = false,
                            setupRequiredMessage = result.message,
                            statusMessage = "Project saved to My Projects."
                        )
                    }
                }
                is ProviderResult.Failure -> {
                    _photoSing.update { it.copy(isGenerating = false, errorMessage = result.message) }
                }
            }
        }
    }

    fun exportPhotoSingVideoToDevice() {
        val state = _photoSing.value
        if (state.outputVideoUrl.isBlank()) {
            _photoSing.update { it.copy(errorMessage = "Generate a singing video before saving.") }
            return
        }
        viewModelScope.launch {
            repository.exportMediaToDevice(
                sourceUriOrUrl = state.outputVideoUrl,
                title = state.title,
                mediaType = "VIDEO",
                mimeType = "video/mp4"
            ).onSuccess { msg ->
                _photoSing.update { it.copy(statusMessage = msg, errorMessage = null) }
            }.onFailure { err ->
                _photoSing.update { it.copy(errorMessage = err.localizedMessage ?: "Export failed.") }
            }
        }
    }

    // --- Image-to-Video Actions ---
    fun updateImageToVideo(update: (ImageToVideoState) -> ImageToVideoState) {
        _imageToVideo.update(update)
    }

    fun onImageToVideoImagePicked(context: Context, uri: Uri) {
        viewModelScope.launch {
            val imported = repository.importUserMediaToDeviceStorage(uri, "IMAGE", "images", "jpg")
            val finalUri = imported.localUri ?: uri.toString()
            val title = imported.displayName ?: "Video Source Frame"
            repository.saveMediaAsset(
                MediaAsset(
                    mediaType = "IMAGE",
                    title = title,
                    mimeType = imported.mimeType ?: context.contentResolver.getType(uri) ?: "image/jpeg",
                    aiGenerated = false,
                    localUri = finalUri,
                    storagePath = imported.storagePath,
                    fileSizeBytes = imported.sizeBytes
                )
            )
            refreshStorageStats()
            _imageToVideo.update {
                it.copy(
                    imageUri = finalUri,
                    statusMessage = imported.warningMessage ?: "Source image saved to My Images."
                )
            }
        }
    }

    fun onImageToVideoAudioPicked(context: Context, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val imported = repository.importUserMediaToDeviceStorage(uri, "AUDIO", "audio")
            val finalUri = imported.localUri ?: uri.toString()
            val info = AudioWaveformExtractor.analyzeAudio(context, Uri.parse(finalUri))
            val title = imported.displayName ?: info.title
            val duration = info.durationMs.coerceAtLeast(imported.durationMs)
            repository.saveMediaAsset(
                MediaAsset(
                    mediaType = "AUDIO",
                    title = title,
                    mimeType = imported.mimeType ?: info.mimeType,
                    durationMs = duration,
                    trimEndMs = duration,
                    aiGenerated = false,
                    localUri = finalUri,
                    storagePath = imported.storagePath,
                    fileSizeBytes = imported.sizeBytes,
                    waveformSamples = info.waveformSamples
                )
            )
            refreshStorageStats()
            _imageToVideo.update {
                it.copy(
                    selectedAudioTitle = title,
                    selectedAudioUri = finalUri,
                    audioTrimEndMs = duration.coerceAtLeast(5_000L),
                    statusMessage = imported.warningMessage ?: "Attached & saved audio: $title"
                )
            }
        }
    }

    fun submitImageToVideo() {
        val state = _imageToVideo.value
        if (state.imageUri.isBlank()) {
            _imageToVideo.update { it.copy(errorMessage = "Please upload a source image.") }
            return
        }
        if (state.prompt.isBlank()) {
            _imageToVideo.update { it.copy(errorMessage = "Please describe the animation motion.") }
            return
        }
        viewModelScope.launch {
            _imageToVideo.update {
                it.copy(isGenerating = true, progressPercent = 10, errorMessage = null, setupRequiredMessage = null)
            }
            val projectId = repository.saveProject(
                StudioProject(
                    projectType = "IMAGE_TO_VIDEO",
                    title = state.title,
                    status = "QUEUED",
                    aspectRatio = settings.value.defaultAspectRatio,
                    resolution = settings.value.defaultResolution,
                    watermarkEnabled = settings.value.defaultWatermark,
                    aiGenerated = true,
                    consentConfirmed = true,
                    singer1PortraitUri = state.imageUri,
                    selectedSongTitle = state.selectedAudioTitle.ifBlank { null },
                    selectedAudioUri = state.selectedAudioUri.ifBlank { null },
                    cameraMovement = state.cameraMovement,
                    animationStyle = state.animationStyle,
                    animationPrompt = state.prompt
                )
            ).getOrNull().orEmpty()

            val result = backendClient.submitImageToVideo(
                ImageToVideoRequestDto(
                    imageUrl = state.imageUri,
                    prompt = state.prompt,
                    cameraMovement = state.cameraMovement,
                    animationStyle = state.animationStyle,
                    audioUrl = state.selectedAudioUri.ifBlank { null },
                    audioTrimStartMs = state.audioTrimStartMs,
                    audioTrimEndMs = state.audioTrimEndMs,
                    applyLipSyncPostProcess = state.applyLipSyncPost
                )
            ) { p -> _imageToVideo.update { it.copy(progressPercent = p) } }

            when (result) {
                is ProviderResult.Success -> {
                    val videoUrl = result.videoUrl.orEmpty()
                    if (projectId.isNotBlank()) {
                        repository.saveProject(
                            StudioProject(
                                id = projectId,
                                projectType = "IMAGE_TO_VIDEO",
                                title = state.title,
                                status = "READY",
                                aspectRatio = settings.value.defaultAspectRatio,
                                resolution = settings.value.defaultResolution,
                                watermarkEnabled = settings.value.defaultWatermark,
                                aiGenerated = true,
                                consentConfirmed = true,
                                singer1PortraitUri = state.imageUri,
                                selectedSongTitle = state.selectedAudioTitle.ifBlank { null },
                                selectedAudioUri = state.selectedAudioUri.ifBlank { null },
                                cameraMovement = state.cameraMovement,
                                animationStyle = state.animationStyle,
                                animationPrompt = state.prompt,
                                outputVideoUrl = videoUrl.ifBlank { null }
                            )
                        )
                    }
                    if (videoUrl.isNotBlank()) {
                        repository.saveMediaAsset(
                            MediaAsset(
                                mediaType = "VIDEO",
                                title = state.title,
                                mimeType = "video/mp4",
                                aiGenerated = true,
                                remoteUrl = videoUrl
                            )
                        )
                    }
                    _imageToVideo.update {
                        it.copy(
                            isGenerating = false,
                            progressPercent = 100,
                            outputVideoUrl = videoUrl,
                            statusMessage = "MP4 video ready! Saved to My Videos."
                        )
                    }
                }
                is ProviderResult.SetupRequired -> {
                    repository.saveJob(
                        GenerationJob(
                            jobType = "IMAGE_TO_VIDEO",
                            title = state.title,
                            providerName = result.providerName,
                            status = "SETUP_REQUIRED",
                            progressPercent = 0L,
                            estimatedCostUsd = result.estimatedCostUsd,
                            projectId = projectId.ifBlank { null },
                            errorMessage = result.message
                        )
                    )
                    _imageToVideo.update {
                        it.copy(
                            isGenerating = false,
                            setupRequiredMessage = result.message,
                            statusMessage = "Project saved to My Projects."
                        )
                    }
                }
                is ProviderResult.Failure -> {
                    _imageToVideo.update { it.copy(isGenerating = false, errorMessage = result.message) }
                }
            }
        }
    }

    fun exportImageToVideoToDevice() {
        val state = _imageToVideo.value
        if (state.outputVideoUrl.isBlank()) {
            _imageToVideo.update { it.copy(errorMessage = "Generate a video before saving.") }
            return
        }
        viewModelScope.launch {
            repository.exportMediaToDevice(
                sourceUriOrUrl = state.outputVideoUrl,
                title = state.title,
                mediaType = "VIDEO",
                mimeType = "video/mp4"
            ).onSuccess { msg ->
                _imageToVideo.update { it.copy(statusMessage = msg, errorMessage = null) }
            }.onFailure { err ->
                _imageToVideo.update { it.copy(errorMessage = err.localizedMessage ?: "Export failed.") }
            }
        }
    }

    // --- Audio Upload & Editor Actions ---
    fun updateAudioEditor(update: (AudioEditorState) -> AudioEditorState) {
        _audioEditor.update(update)
    }

    fun onAudioFilePicked(context: Context, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            _audioEditor.update {
                it.copy(isAnalyzing = true, errorMessage = null, statusMessage = "Copying & decoding audio waveform...")
            }
            val imported = repository.importUserMediaToDeviceStorage(uri, "AUDIO", "audio")
            val finalUri = imported.localUri ?: uri.toString()
            val analysis: AudioAnalysisResult = AudioWaveformExtractor.analyzeAudio(context, Uri.parse(finalUri))
            val title = (imported.displayName ?: analysis.title).take(120)
            val duration = analysis.durationMs.coerceAtLeast(imported.durationMs)
            val asset = MediaAsset(
                mediaType = "AUDIO",
                title = title,
                mimeType = imported.mimeType ?: analysis.mimeType,
                durationMs = duration,
                trimStartMs = 0L,
                trimEndMs = duration,
                splitPointMs = duration / 2L,
                volume = 1.0,
                fadeInMs = 500L,
                fadeOutMs = 1000L,
                syncOffsetMs = 0L,
                aiGenerated = false,
                localUri = finalUri,
                storagePath = imported.storagePath,
                fileSizeBytes = imported.sizeBytes,
                waveformSamples = analysis.waveformSamples
            )
            val savedId = repository.saveMediaAsset(asset).getOrNull().orEmpty()
            refreshStorageStats()
            _audioEditor.update {
                it.copy(
                    editingAssetId = savedId,
                    title = asset.title,
                    mimeType = asset.mimeType,
                    uriString = finalUri,
                    durationMs = duration,
                    trimStartMs = 0L,
                    trimEndMs = duration,
                    splitPointMs = duration / 2L,
                    waveformSamples = analysis.waveformSamples,
                    isAnalyzing = false,
                    statusMessage = imported.warningMessage ?: "Saved & loaded $title (${duration / 1000}s)"
                )
            }
        }
    }

    fun loadAudioAssetForEditing(asset: MediaAsset) {
        _audioEditor.value = AudioEditorState(
            editingAssetId = asset.id,
            title = asset.title,
            mimeType = asset.mimeType,
            uriString = asset.localUri ?: asset.remoteUrl.orEmpty(),
            durationMs = asset.durationMs,
            trimStartMs = asset.trimStartMs,
            trimEndMs = if (asset.trimEndMs > 0L) asset.trimEndMs else asset.durationMs,
            splitPointMs = asset.splitPointMs,
            volume = asset.volume.toFloat(),
            fadeInMs = asset.fadeInMs,
            fadeOutMs = asset.fadeOutMs,
            syncOffsetMs = asset.syncOffsetMs,
            waveformSamples = asset.waveformSamples
        )
    }

    fun saveAudioEdits() {
        val state = _audioEditor.value
        if (state.uriString.isBlank()) {
            _audioEditor.update { it.copy(errorMessage = "Upload or select an audio track first.") }
            return
        }
        viewModelScope.launch {
            repository.saveMediaAsset(
                MediaAsset(
                    id = state.editingAssetId,
                    mediaType = "AUDIO",
                    title = state.title.ifBlank { "Edited Audio" },
                    mimeType = state.mimeType,
                    durationMs = state.durationMs,
                    trimStartMs = state.trimStartMs,
                    trimEndMs = state.trimEndMs,
                    splitPointMs = state.splitPointMs,
                    volume = state.volume.toDouble(),
                    fadeInMs = state.fadeInMs,
                    fadeOutMs = state.fadeOutMs,
                    syncOffsetMs = state.syncOffsetMs,
                    localUri = state.uriString,
                    waveformSamples = state.waveformSamples
                )
            ).onSuccess { id ->
                _audioEditor.update {
                    it.copy(editingAssetId = id, statusMessage = "Audio trim, fade, and sync settings saved.")
                }
            }.onFailure { err ->
                _audioEditor.update { it.copy(errorMessage = err.localizedMessage) }
            }
        }
    }

    fun exportAudioEditorCopyToDevice() {
        val state = _audioEditor.value
        if (state.uriString.isBlank()) {
            _audioEditor.update { it.copy(errorMessage = "Upload an audio file first.") }
            return
        }
        viewModelScope.launch {
            repository.exportMediaToDevice(
                sourceUriOrUrl = state.uriString,
                title = state.title.ifBlank { "Raven Audio Track" },
                mediaType = "AUDIO",
                mimeType = state.mimeType
            ).onSuccess { msg ->
                _audioEditor.update { it.copy(statusMessage = msg, errorMessage = null) }
            }.onFailure { err ->
                _audioEditor.update { it.copy(errorMessage = err.localizedMessage ?: "Export failed.") }
            }
        }
    }

    fun exportAudioWithFfmpeg() {
        val state = _audioEditor.value
        if (state.uriString.isBlank()) {
            _audioEditor.update { it.copy(errorMessage = "Upload an audio file first.") }
            return
        }
        viewModelScope.launch {
            _audioEditor.update { it.copy(isExporting = true, setupRequiredMessage = null, errorMessage = null) }
            val result = backendClient.submitFfmpegExport(
                FfmpegExportRequestDto(
                    projectId = state.editingAssetId.ifBlank { UUID.randomUUID().toString() },
                    aspectRatio = settings.value.defaultAspectRatio,
                    resolution = settings.value.defaultResolution,
                    audioUrl = state.uriString,
                    trimStartMs = state.trimStartMs,
                    trimEndMs = state.trimEndMs,
                    fadeInMs = state.fadeInMs,
                    fadeOutMs = state.fadeOutMs,
                    volume = state.volume.toDouble(),
                    syncOffsetMs = state.syncOffsetMs
                )
            ) {}
            when (result) {
                is ProviderResult.Success -> {
                    val videoUrl = result.videoUrl.orEmpty()
                    if (videoUrl.isNotBlank()) {
                        repository.saveMediaAsset(
                            MediaAsset(
                                mediaType = "VIDEO",
                                title = "${state.title.ifBlank { "Raven Audio" }} Mix",
                                mimeType = "video/mp4",
                                aiGenerated = false,
                                remoteUrl = videoUrl
                            )
                        )
                    }
                    _audioEditor.update {
                        it.copy(
                            isExporting = false,
                            outputVideoUrl = videoUrl,
                            statusMessage = "Audio/video mix exported! Ready to preview or save MP4."
                        )
                    }
                }
                is ProviderResult.SetupRequired -> _audioEditor.update {
                    it.copy(isExporting = false, setupRequiredMessage = result.message)
                }
                is ProviderResult.Failure -> _audioEditor.update {
                    it.copy(isExporting = false, errorMessage = result.message)
                }
            }
        }
    }

    fun exportAudioEditorVideoToDevice() {
        val state = _audioEditor.value
        if (state.outputVideoUrl.isBlank()) {
            _audioEditor.update { it.copy(errorMessage = "Export an audio/video mix first.") }
            return
        }
        viewModelScope.launch {
            repository.exportMediaToDevice(
                sourceUriOrUrl = state.outputVideoUrl,
                title = "${state.title.ifBlank { "Raven Audio" }} Mix",
                mediaType = "VIDEO",
                mimeType = "video/mp4"
            ).onSuccess { msg ->
                _audioEditor.update { it.copy(statusMessage = msg, errorMessage = null) }
            }.onFailure { err ->
                _audioEditor.update { it.copy(errorMessage = err.localizedMessage ?: "Export failed.") }
            }
        }
    }

    // --- Studio Timeline Editor Actions ---
    fun updateTimeline(update: (VideoTimelineState) -> VideoTimelineState) {
        _videoTimeline.update(update)
    }

    fun onTimelineClipPicked(context: Context, uri: Uri, mediaType: String) {
        viewModelScope.launch {
            val folder = if (mediaType == "VIDEO") "videos" else "images"
            val defaultExt = if (mediaType == "VIDEO") "mp4" else "jpg"
            val imported = repository.importUserMediaToDeviceStorage(uri, mediaType, folder, defaultExt)
            val localUri = imported.localUri ?: uri.toString()
            val clipLabel = imported.displayName ?: if (mediaType == "VIDEO") {
                "Video Clip ${_videoTimeline.value.clips.size + 1}"
            } else {
                "Image Frame ${_videoTimeline.value.clips.size + 1}"
            }

            repository.saveMediaAsset(
                MediaAsset(
                    mediaType = mediaType,
                    title = clipLabel,
                    mimeType = imported.mimeType ?: context.contentResolver.getType(uri)
                        ?: if (mediaType == "VIDEO") "video/mp4" else "image/jpeg",
                    durationMs = imported.durationMs,
                    trimEndMs = imported.durationMs,
                    aiGenerated = false,
                    localUri = localUri,
                    storagePath = imported.storagePath,
                    fileSizeBytes = imported.sizeBytes
                )
            )
            refreshStorageStats()
            addClipToTimeline(
                label = clipLabel,
                mediaType = mediaType,
                uri = localUri,
                durationMs = imported.durationMs.takeIf { it > 0L } ?: 5000L
            )
            _videoTimeline.update {
                it.copy(statusMessage = imported.warningMessage ?: "Added \"$clipLabel\" to timeline & My Library.")
            }
        }
    }

    fun onTimelineAudioPicked(context: Context, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val imported = repository.importUserMediaToDeviceStorage(uri, "AUDIO", "audio")
            val finalUri = imported.localUri ?: uri.toString()
            val info = AudioWaveformExtractor.analyzeAudio(context, Uri.parse(finalUri))
            val title = imported.displayName ?: info.title
            val duration = info.durationMs.coerceAtLeast(imported.durationMs)
            repository.saveMediaAsset(
                MediaAsset(
                    mediaType = "AUDIO",
                    title = title,
                    mimeType = imported.mimeType ?: info.mimeType,
                    durationMs = duration,
                    trimEndMs = duration,
                    aiGenerated = false,
                    localUri = finalUri,
                    storagePath = imported.storagePath,
                    fileSizeBytes = imported.sizeBytes,
                    waveformSamples = info.waveformSamples
                )
            )
            refreshStorageStats()
            _videoTimeline.update {
                it.copy(
                    audioTitle = title,
                    audioUri = finalUri,
                    statusMessage = imported.warningMessage ?: "Attached & saved \"$title\"."
                )
            }
        }
    }

    fun addClipToTimeline(label: String, mediaType: String, uri: String, durationMs: Long = 5000L) {
        _videoTimeline.update { state ->
            val lastEnd = state.clips.maxOfOrNull { it.endMs } ?: 0L
            val newClip = TimelineClip(
                id = UUID.randomUUID().toString(),
                label = label,
                mediaType = mediaType,
                uri = uri,
                startMs = lastEnd,
                endMs = lastEnd + durationMs.coerceAtLeast(1000L),
                transition = state.transitionStyle
            )
            state.copy(clips = state.clips + newClip)
        }
    }

    fun removeClipFromTimeline(clipId: String) {
        _videoTimeline.update { state ->
            state.copy(clips = state.clips.filterNot { it.id == clipId })
        }
    }

    fun loadProjectIntoTimeline(project: StudioProject) {
        _videoTimeline.value = VideoTimelineState(
            editingProjectId = project.id,
            title = project.title,
            clips = project.parsedTimelineClips(),
            audioTitle = project.selectedSongTitle.orEmpty(),
            audioUri = project.selectedAudioUri.orEmpty(),
            albumArtUri = project.albumArtUri.orEmpty(),
            textOverlay = project.textOverlay.orEmpty(),
            lyricsOverlayEnabled = project.lyricsOverlayEnabled ?: true,
            transitionStyle = project.transitionStyle ?: "Crossfade",
            aspectRatio = project.aspectRatio,
            resolution = project.resolution,
            watermarkEnabled = project.watermarkEnabled,
            previewVideoUrl = project.outputVideoUrl.orEmpty()
        )
    }

    fun saveAndExportTimeline(exportMp4: Boolean) {
        val state = _videoTimeline.value
        viewModelScope.launch {
            val savedId = repository.saveProject(
                StudioProject(
                    id = state.editingProjectId,
                    projectType = "MUSIC_VIDEO_TIMELINE",
                    title = state.title.ifBlank { "Studio Timeline" },
                    status = if (exportMp4) "QUEUED" else "DRAFT",
                    aspectRatio = state.aspectRatio,
                    resolution = state.resolution,
                    watermarkEnabled = state.watermarkEnabled,
                    aiGenerated = true,
                    consentConfirmed = true,
                    selectedSongTitle = state.audioTitle.ifBlank { null },
                    selectedAudioUri = state.audioUri.ifBlank { null },
                    textOverlay = state.textOverlay.ifBlank { null },
                    lyricsOverlayEnabled = state.lyricsOverlayEnabled,
                    albumArtUri = state.albumArtUri.ifBlank { null },
                    transitionStyle = state.transitionStyle,
                    timelineClips = StudioRepository.timelineClipsToMaps(state.clips)
                )
            ).getOrElse { err ->
                _videoTimeline.update { it.copy(errorMessage = err.localizedMessage) }
                return@launch
            }

            _videoTimeline.update { it.copy(editingProjectId = savedId, statusMessage = "Timeline project saved locally.") }
            if (!exportMp4) return@launch

            _videoTimeline.update { it.copy(isExporting = true, progressPercent = 15, setupRequiredMessage = null) }
            val result = backendClient.submitFfmpegExport(
                FfmpegExportRequestDto(
                    projectId = savedId,
                    aspectRatio = state.aspectRatio,
                    resolution = state.resolution,
                    watermarkBrand = if (state.watermarkEnabled) "Raven & Co." else null,
                    audioUrl = state.audioUri.ifBlank { null },
                    textOverlay = state.textOverlay.ifBlank { null },
                    transitionStyle = state.transitionStyle
                )
            ) { p -> _videoTimeline.update { it.copy(progressPercent = p) } }

            when (result) {
                is ProviderResult.Success -> _videoTimeline.update {
                    it.copy(
                        isExporting = false,
                        progressPercent = 100,
                        previewVideoUrl = result.videoUrl.orEmpty(),
                        statusMessage = "MP4 exported (${state.resolution} • ${state.aspectRatio})!"
                    )
                }
                is ProviderResult.SetupRequired -> {
                    repository.saveJob(
                        GenerationJob(
                            jobType = "AUDIO_VIDEO_EXPORT",
                            title = state.title,
                            providerName = result.providerName,
                            status = "SETUP_REQUIRED",
                            progressPercent = 0L,
                            estimatedCostUsd = result.estimatedCostUsd,
                            projectId = savedId,
                            errorMessage = result.message
                        )
                    )
                    _videoTimeline.update {
                        it.copy(isExporting = false, setupRequiredMessage = result.message)
                    }
                }
                is ProviderResult.Failure -> _videoTimeline.update {
                    it.copy(isExporting = false, errorMessage = result.message)
                }
            }
        }
    }

    fun exportTimelineVideoToDevice() {
        val state = _videoTimeline.value
        if (state.previewVideoUrl.isBlank()) {
            _videoTimeline.update { it.copy(errorMessage = "Render an MP4 video before saving to device.") }
            return
        }
        viewModelScope.launch {
            repository.exportMediaToDevice(
                sourceUriOrUrl = state.previewVideoUrl,
                title = state.title.ifBlank { "Raven Studio Cut" },
                mediaType = "VIDEO",
                mimeType = "video/mp4"
            ).onSuccess { msg ->
                _videoTimeline.update { it.copy(statusMessage = msg, errorMessage = null) }
            }.onFailure { err ->
                _videoTimeline.update { it.copy(errorMessage = err.localizedMessage ?: "Export failed.") }
            }
        }
    }

    // --- Library & Preferences ---
    fun renameLibraryItem(subcollection: String, itemId: String, newTitle: String) {
        viewModelScope.launch {
            repository.renameItem(subcollection, itemId, newTitle)
        }
    }

    fun deleteLibraryItem(subcollection: String, itemId: String) {
        viewModelScope.launch {
            repository.deleteItem(subcollection, itemId)
            refreshStorageStats()
        }
    }

    fun updateStudioSetting(
        aspectRatio: String? = null,
        resolution: String? = null,
        watermark: Boolean? = null,
        vocalSeparation: Boolean? = null
    ) {
        viewModelScope.launch {
            aspectRatio?.let { preferences.updateAspectRatio(it) }
            resolution?.let { preferences.updateResolution(it) }
            watermark?.let { preferences.updateWatermark(it) }
            vocalSeparation?.let { preferences.updateVocalSeparation(it) }
        }
    }

    private companion object {
        const val TAG = "StudioViewModel"
    }
}
