package com.example.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.AudioWaveformExtractor
import com.example.core.FaceAnalysisResult
import com.example.core.FaceDetectorHelper
import com.example.data.remote.BackendMediaProviderClient
import com.example.data.remote.DuetSegmentDto
import com.example.data.remote.DuetVideoRequestDto
import com.example.data.remote.FfmpegExportRequestDto
import com.example.data.remote.ProviderResult
import com.example.data.repository.GeminiLyricsRepository
import com.example.data.repository.StudioRepository
import com.example.model.DuetLine
import com.example.model.GenerationJob
import com.example.model.MediaAsset
import com.example.model.SongRecord
import com.example.model.StudioProject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class DuetPerformanceStyle(val label: String, val description: String) {
    LIVE_CONCERT("Live Concert", "Both singers on a realistic stage with microphones, spotlights, and audience"),
    RECORDING_STUDIO("Recording Studio", "Both singers performing inside a professional recording studio"),
    SPLIT_SCREEN("Split Screen", "Singer 1 on the left and Singer 2 on the right"),
    COUNTRY_VIDEO("Country Music Video", "Cinematic outdoor performance with Southern scenery"),
    ROMANTIC_DUET("Romantic Duet", "Both singers performing together in a warm sunset setting"),
    GOSPEL_PERFORMANCE("Gospel Performance", "Church stage with warm lighting and a choir-style atmosphere")
}

data class DuetModeState(
    val projectId: String = "",
    val projectTitle: String = "Raven & Breeze Duet",
    val currentStep: Int = 1, // 1..8 guided workflow
    val singer1Name: String = "Raven",
    val singer1VocalRole: String = "FEMALE",
    val singer1PortraitUri: String = "",
    val singer1FaceResult: FaceAnalysisResult? = null,
    val singer1Scanning: Boolean = false,
    val singer2Name: String = "Breeze",
    val singer2VocalRole: String = "MALE",
    val singer2PortraitUri: String = "",
    val singer2FaceResult: FaceAnalysisResult? = null,
    val singer2Scanning: Boolean = false,
    val selectedSongTitle: String = "",
    val selectedAudioUri: String = "",
    val audioDurationMs: Long = 120_000L,
    val waveformSamples: List<Long> = emptyList(),
    val isAnalyzingAudio: Boolean = false,
    val rawLyricsDraft: String = "[Verse 1 - Raven]\nGolden dust on the midnight highway\n[Verse 2 - Breeze]\nYour voice cuts through the radio static\n[Chorus - Both]\nSing it loud till the morning light\n[Instrumental Break]\n[Outro - Both]\nTogether under electric skies",
    val duetLines: List<DuetLine> = defaultDuetLines(),
    val useVocalSeparation: Boolean = true,
    val isAnalyzingVocals: Boolean = false,
    val performanceStyle: DuetPerformanceStyle = DuetPerformanceStyle.LIVE_CONCERT,
    val performanceIntensity: Float = 0.8f,
    val closeUpSwitching: Boolean = true,
    val twoPersonChorusShots: Boolean = true,
    val naturalBlinkingAndBreathing: Boolean = true,
    val consentConfirmed: Boolean = false,
    val lipSyncOffsetMs: Long = 0L,
    val aspectRatio: String = "9:16",
    val resolution: String = "1080p",
    val watermarkEnabled: Boolean = true,
    val lyricsOverlayEnabled: Boolean = true,
    val artistTitleOverlay: String = "Raven & Breeze",
    val outputVideoUrl: String = "",
    val isGenerating: Boolean = false,
    val progressPercent: Int = 0,
    val statusMessage: String? = null,
    val setupRequiredMessage: String? = null,
    val errorMessage: String? = null
)

private fun defaultDuetLines(): List<DuetLine> = listOf(
    DuetLine(id = "d1", timestampMs = 0L, durationMs = 6000L, singer = "RAVEN", lyric = "Golden dust on the midnight highway"),
    DuetLine(id = "d2", timestampMs = 6000L, durationMs = 6000L, singer = "BREEZE", lyric = "Your voice cuts through the radio static"),
    DuetLine(id = "d3", timestampMs = 12000L, durationMs = 8000L, singer = "BOTH", lyric = "Sing it loud till the morning light"),
    DuetLine(id = "d4", timestampMs = 20000L, durationMs = 5000L, singer = "INSTRUMENTAL", lyric = "[Instrumental Solo — Natural Breathing & Blinking]"),
    DuetLine(id = "d5", timestampMs = 25000L, durationMs = 7000L, singer = "BOTH", lyric = "Together under electric skies")
)

class DuetModeViewModel(
    private val repository: StudioRepository,
    private val geminiRepo: GeminiLyricsRepository,
    private val backendClient: BackendMediaProviderClient
) : ViewModel() {

    private val _state = MutableStateFlow(DuetModeState())
    val state: StateFlow<DuetModeState> = _state.asStateFlow()

    fun setStep(step: Int) {
        _state.update { it.copy(currentStep = step.coerceIn(1, 8), errorMessage = null) }
    }

    fun updateState(transform: (DuetModeState) -> DuetModeState) {
        _state.update(transform)
    }

    fun onSingerPortraitPicked(context: Context, singerIndex: Int, uri: Uri) {
        viewModelScope.launch {
            if (singerIndex == 1) {
                _state.update { it.copy(singer1Scanning = true, singer1PortraitUri = uri.toString(), errorMessage = null) }
            } else {
                _state.update { it.copy(singer2Scanning = true, singer2PortraitUri = uri.toString(), errorMessage = null) }
            }

            val imported = repository.importUserMediaToDeviceStorage(uri, "IMAGE", "duet_portraits", "jpg")
            val finalUriString = imported.localUri ?: uri.toString()
            val faceResult = FaceDetectorHelper.detectFaces(context, Uri.parse(finalUriString))

            val singerLabel = if (singerIndex == 1) _state.value.singer1Name else _state.value.singer2Name
            repository.saveMediaAsset(
                MediaAsset(
                    mediaType = "IMAGE",
                    title = "$singerLabel Portrait",
                    mimeType = imported.mimeType ?: context.contentResolver.getType(uri) ?: "image/jpeg",
                    faceDetected = faceResult.faceDetected,
                    faceCount = faceResult.faceCount.toLong(),
                    aiGenerated = false,
                    localUri = finalUriString,
                    storagePath = imported.storagePath,
                    fileSizeBytes = imported.sizeBytes
                )
            )

            _state.update { current ->
                if (singerIndex == 1) {
                    current.copy(
                        singer1PortraitUri = finalUriString,
                        singer1FaceResult = faceResult,
                        singer1Scanning = false,
                        statusMessage = imported.warningMessage ?: "${current.singer1Name}: ${faceResult.statusMessage}"
                    )
                } else {
                    current.copy(
                        singer2PortraitUri = finalUriString,
                        singer2FaceResult = faceResult,
                        singer2Scanning = false,
                        statusMessage = imported.warningMessage ?: "${current.singer2Name}: ${faceResult.statusMessage}"
                    )
                }
            }
        }
    }

    fun selectSongFromLibrary(song: SongRecord) {
        val audioSource = song.audioUrl ?: song.wavUrl ?: ""
        val dur = if (song.durationMs > 0L) song.durationMs else 150_000L
        _state.update {
            it.copy(
                selectedSongTitle = song.title,
                selectedAudioUri = audioSource,
                audioDurationMs = dur,
                rawLyricsDraft = song.lyrics.ifBlank { it.rawLyricsDraft },
                artistTitleOverlay = "${it.singer1Name} & ${it.singer2Name} — ${song.title}",
                statusMessage = if (audioSource.isBlank()) {
                    "Selected '${song.title}' (lyrics loaded; audio file pending music backend)."
                } else {
                    "Selected '${song.title}' from My Songs."
                }
            )
        }
    }

    fun selectUploadedAudio(context: Context, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            _state.update { it.copy(isAnalyzingAudio = true, errorMessage = null) }
            val imported = repository.importUserMediaToDeviceStorage(uri, "AUDIO", "duet_audio")
            val finalUriString = imported.localUri ?: uri.toString()
            val analysis = AudioWaveformExtractor.analyzeAudio(context, Uri.parse(finalUriString))

            repository.saveMediaAsset(
                MediaAsset(
                    mediaType = "AUDIO",
                    title = imported.displayName ?: analysis.title,
                    mimeType = imported.mimeType ?: analysis.mimeType,
                    durationMs = analysis.durationMs.coerceAtLeast(imported.durationMs),
                    trimEndMs = analysis.durationMs.coerceAtLeast(imported.durationMs),
                    aiGenerated = false,
                    localUri = finalUriString,
                    storagePath = imported.storagePath,
                    fileSizeBytes = imported.sizeBytes,
                    waveformSamples = analysis.waveformSamples
                )
            )

            val trackTitle = imported.displayName ?: analysis.title
            _state.update {
                it.copy(
                    selectedSongTitle = trackTitle,
                    selectedAudioUri = finalUriString,
                    audioDurationMs = analysis.durationMs.coerceAtLeast(imported.durationMs).coerceAtLeast(30_000L),
                    waveformSamples = analysis.waveformSamples,
                    isAnalyzingAudio = false,
                    artistTitleOverlay = "${it.singer1Name} & ${it.singer2Name} — $trackTitle",
                    statusMessage = imported.warningMessage ?: "Saved & loaded duet track: $trackTitle"
                )
            }
        }
    }

    fun autoAnalyzeVocalsWithGemini() {
        val current = _state.value
        if (current.rawLyricsDraft.isBlank()) {
            _state.update { it.copy(errorMessage = "Enter duet lyrics or cues before auto-analyzing.") }
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(isAnalyzingVocals = true, errorMessage = null, statusMessage = "Mapping duet vocal lines with Gemini...")
            }
            val result = geminiRepo.analyzeDuetTimingFromLyrics(
                lyricsText = current.rawLyricsDraft,
                audioDurationMs = current.audioDurationMs,
                singer1Name = current.singer1Name,
                singer2Name = current.singer2Name
            )
            result.onSuccess { lines ->
                _state.update {
                    it.copy(
                        duetLines = if (lines.isNotEmpty()) lines else it.duetLines,
                        isAnalyzingVocals = false,
                        statusMessage = "Assigned ${lines.size} timed vocal sections for ${it.singer1Name} & ${it.singer2Name}."
                    )
                }
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        isAnalyzingVocals = false,
                        errorMessage = err.localizedMessage ?: "Vocal assignment analysis failed."
                    )
                }
            }
        }
    }

    fun updateLineSinger(lineId: String, newSinger: String) {
        _state.update { current ->
            current.copy(
                duetLines = current.duetLines.map { line ->
                    if (line.id == lineId) line.copy(singer = newSinger) else line
                }
            )
        }
    }

    fun updateLineLyric(lineId: String, newLyric: String) {
        _state.update { current ->
            current.copy(
                duetLines = current.duetLines.map { line ->
                    if (line.id == lineId) line.copy(lyric = newLyric) else line
                }
            )
        }
    }

    fun addDuetLine(singer: String = "RAVEN") {
        _state.update { current ->
            val lastEnd = current.duetLines.maxOfOrNull { it.timestampMs + it.durationMs } ?: 0L
            val newLine = DuetLine(
                id = UUID.randomUUID().toString(),
                timestampMs = lastEnd,
                durationMs = 5000L,
                singer = singer,
                lyric = if (singer == "INSTRUMENTAL") "[Instrumental Break]" else "New duet lyric line"
            )
            current.copy(duetLines = current.duetLines + newLine)
        }
    }

    fun removeDuetLine(lineId: String) {
        _state.update { current ->
            current.copy(duetLines = current.duetLines.filterNot { it.id == lineId })
        }
    }

    fun saveDuetProject() {
        val s = _state.value
        viewModelScope.launch {
            repository.saveProject(toStudioProject(s, statusOverride = "DRAFT"))
                .onSuccess { savedId ->
                    _state.update {
                        it.copy(projectId = savedId, statusMessage = "Duet project saved to My Projects.")
                    }
                }
                .onFailure { err ->
                    _state.update { it.copy(errorMessage = err.localizedMessage ?: "Failed to save project.") }
                }
        }
    }

    fun loadDuetProject(project: StudioProject) {
        val style = DuetPerformanceStyle.entries.firstOrNull {
            it.label.equals(project.performanceStyle, ignoreCase = true) || it.name == project.performanceStyle
        } ?: DuetPerformanceStyle.LIVE_CONCERT

        _state.value = DuetModeState(
            projectId = project.id,
            projectTitle = project.title,
            currentStep = if (!project.outputVideoUrl.isNullOrBlank()) 8 else 1,
            singer1Name = project.singer1Name ?: "Raven",
            singer1VocalRole = project.singer1VocalRole ?: "FEMALE",
            singer1PortraitUri = project.singer1PortraitUri.orEmpty(),
            singer2Name = project.singer2Name ?: "Breeze",
            singer2VocalRole = project.singer2VocalRole ?: "MALE",
            singer2PortraitUri = project.singer2PortraitUri.orEmpty(),
            selectedSongTitle = project.selectedSongTitle.orEmpty(),
            selectedAudioUri = project.selectedAudioUri.orEmpty(),
            audioDurationMs = project.audioDurationMs ?: 120_000L,
            waveformSamples = project.waveformSamples,
            duetLines = project.parsedDuetLines().ifEmpty { defaultDuetLines() },
            useVocalSeparation = project.useVocalSeparation ?: true,
            performanceStyle = style,
            performanceIntensity = (project.performanceIntensity ?: 0.8).toFloat(),
            consentConfirmed = project.consentConfirmed,
            lipSyncOffsetMs = project.lipSyncOffsetMs ?: 0L,
            aspectRatio = project.aspectRatio,
            resolution = project.resolution,
            watermarkEnabled = project.watermarkEnabled,
            lyricsOverlayEnabled = project.lyricsOverlayEnabled ?: true,
            artistTitleOverlay = project.textOverlay ?: "${project.singer1Name ?: "Raven"} & ${project.singer2Name ?: "Breeze"}",
            outputVideoUrl = project.outputVideoUrl.orEmpty()
        )
    }

    fun generateDuetPerformance() {
        val s = _state.value
        if (s.singer1PortraitUri.isBlank() || s.singer2PortraitUri.isBlank()) {
            _state.update { it.copy(errorMessage = "Upload portraits for both ${s.singer1Name} and ${s.singer2Name}.") }
            return
        }
        if (s.selectedSongTitle.isBlank() && s.selectedAudioUri.isBlank()) {
            _state.update { it.copy(errorMessage = "Select or upload a duet song first.") }
            return
        }
        if (!s.consentConfirmed) {
            _state.update {
                it.copy(errorMessage = "Confirmation required: confirm you have permission to animate both portraits and use the selected audio.")
            }
            return
        }

        viewModelScope.launch {
            _state.update {
                it.copy(
                    currentStep = 7,
                    isGenerating = true,
                    progressPercent = 10,
                    errorMessage = null,
                    setupRequiredMessage = null,
                    statusMessage = "Preparing segmented lip-sync & stage compositing..."
                )
            }

            val savedProjectId = repository.saveProject(toStudioProject(s, statusOverride = "QUEUED"))
                .getOrNull()
                .orEmpty()
            if (savedProjectId.isNotBlank()) {
                _state.update { it.copy(projectId = savedIdOrExisting(savedProjectId, it.projectId)) }
            }

            val request = DuetVideoRequestDto(
                projectTitle = s.projectTitle,
                singer1Name = s.singer1Name,
                singer1VocalRole = s.singer1VocalRole,
                singer1PortraitUrl = s.singer1PortraitUri,
                singer2Name = s.singer2Name,
                singer2VocalRole = s.singer2VocalRole,
                singer2PortraitUrl = s.singer2PortraitUri,
                audioUrl = s.selectedAudioUri,
                performanceStyle = s.performanceStyle.label,
                performanceIntensity = s.performanceIntensity,
                useVocalSeparation = s.useVocalSeparation,
                lipSyncOffsetMs = s.lipSyncOffsetMs,
                aspectRatio = s.aspectRatio,
                resolution = s.resolution,
                watermarkBrand = if (s.watermarkEnabled) "Raven & Co." else null,
                segments = s.duetLines.map { line ->
                    DuetSegmentDto(
                        startMs = line.timestampMs,
                        endMs = line.timestampMs + line.durationMs,
                        activeSinger = line.singer,
                        lyricLine = line.lyric
                    )
                },
                consentConfirmed = true
            )

            val result = backendClient.submitDuetPerformance(request) { progress ->
                _state.update { it.copy(progressPercent = progress) }
            }

            when (result) {
                is ProviderResult.Success -> {
                    val finalProjectId = _state.value.projectId
                    repository.saveProject(
                        toStudioProject(
                            _state.value.copy(outputVideoUrl = result.videoUrl.orEmpty()),
                            statusOverride = "READY"
                        ).copy(id = finalProjectId)
                    )
                    repository.saveJob(
                        GenerationJob(
                            jobType = "DUET_VIDEO",
                            title = s.projectTitle,
                            providerName = result.providerName,
                            status = "COMPLETED",
                            progressPercent = 100L,
                            estimatedCostUsd = result.estimatedCostUsd,
                            externalJobId = result.jobId,
                            projectId = finalProjectId.ifBlank { null },
                            resultUrl = result.videoUrl
                        )
                    )
                    _state.update {
                        it.copy(
                            currentStep = 8,
                            isGenerating = false,
                            progressPercent = 100,
                            outputVideoUrl = result.videoUrl.orEmpty(),
                            statusMessage = "Duet performance rendered! Ready to preview, edit, and export."
                        )
                    }
                }
                is ProviderResult.SetupRequired -> {
                    val finalProjectId = _state.value.projectId
                    if (finalProjectId.isNotBlank()) {
                        repository.saveProject(
                            toStudioProject(_state.value, statusOverride = "SETUP_REQUIRED")
                                .copy(id = finalProjectId, errorMessage = result.message)
                        )
                    }
                    repository.saveJob(
                        GenerationJob(
                            jobType = "DUET_VIDEO",
                            title = s.projectTitle,
                            providerName = result.providerName,
                            status = "SETUP_REQUIRED",
                            progressPercent = 0L,
                            estimatedCostUsd = result.estimatedCostUsd,
                            projectId = finalProjectId.ifBlank { null },
                            errorMessage = result.message
                        )
                    )
                    _state.update {
                        it.copy(
                            isGenerating = false,
                            progressPercent = 0,
                            setupRequiredMessage = result.message,
                            statusMessage = "Duet timeline & portraits saved to My Projects."
                        )
                    }
                }
                is ProviderResult.Failure -> {
                    _state.update {
                        it.copy(isGenerating = false, errorMessage = result.message)
                    }
                }
            }
        }
    }

    fun exportFinalDuetMp4() {
        val s = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isGenerating = true, progressPercent = 15, setupRequiredMessage = null, errorMessage = null) }
            val result = backendClient.submitFfmpegExport(
                FfmpegExportRequestDto(
                    projectId = s.projectId.ifBlank { UUID.randomUUID().toString() },
                    aspectRatio = s.aspectRatio,
                    resolution = s.resolution,
                    watermarkBrand = if (s.watermarkEnabled) "Raven & Co." else null,
                    audioUrl = s.selectedAudioUri.ifBlank { null },
                    syncOffsetMs = s.lipSyncOffsetMs,
                    textOverlay = s.artistTitleOverlay.ifBlank { null }
                )
            ) { p -> _state.update { it.copy(progressPercent = p) } }

            when (result) {
                is ProviderResult.Success -> _state.update {
                    it.copy(
                        isGenerating = false,
                        progressPercent = 100,
                        outputVideoUrl = result.videoUrl ?: it.outputVideoUrl,
                        statusMessage = "Final MP4 exported (${s.resolution} • ${s.aspectRatio})!"
                    )
                }
                is ProviderResult.SetupRequired -> _state.update {
                    it.copy(isGenerating = false, setupRequiredMessage = result.message)
                }
                is ProviderResult.Failure -> _state.update {
                    it.copy(isGenerating = false, errorMessage = result.message)
                }
            }
        }
    }

    fun saveDuetVideoToDevice() {
        val s = _state.value
        if (s.outputVideoUrl.isBlank()) {
            _state.update { it.copy(errorMessage = "Render the duet MP4 before saving to device.") }
            return
        }
        viewModelScope.launch {
            repository.exportMediaToDevice(
                sourceUriOrUrl = s.outputVideoUrl,
                title = s.projectTitle,
                mediaType = "VIDEO",
                mimeType = "video/mp4"
            ).onSuccess { msg ->
                _state.update { it.copy(statusMessage = msg, errorMessage = null) }
            }.onFailure { err ->
                _state.update { it.copy(errorMessage = err.localizedMessage ?: "Export failed.") }
            }
        }
    }

    private fun savedIdOrExisting(newId: String, oldId: String): String = newId.ifBlank { oldId }

    private fun toStudioProject(s: DuetModeState, statusOverride: String): StudioProject {
        return StudioProject(
            id = s.projectId,
            projectType = "DUET_RAVEN_BREEZE",
            title = s.projectTitle.ifBlank { "Raven & Breeze Duet" },
            status = statusOverride,
            aspectRatio = s.aspectRatio,
            resolution = s.resolution,
            watermarkEnabled = s.watermarkEnabled,
            aiGenerated = true,
            consentConfirmed = s.consentConfirmed,
            singer1Name = s.singer1Name,
            singer1VocalRole = s.singer1VocalRole,
            singer1PortraitUri = s.singer1PortraitUri.ifBlank { null },
            singer1FaceDetected = s.singer1FaceResult?.faceDetected ?: false,
            singer2Name = s.singer2Name,
            singer2VocalRole = s.singer2VocalRole,
            singer2PortraitUri = s.singer2PortraitUri.ifBlank { null },
            singer2FaceDetected = s.singer2FaceResult?.faceDetected ?: false,
            selectedSongTitle = s.selectedSongTitle.ifBlank { null },
            selectedAudioUri = s.selectedAudioUri.ifBlank { null },
            audioDurationMs = s.audioDurationMs,
            performanceStyle = s.performanceStyle.label,
            performanceIntensity = s.performanceIntensity.toDouble(),
            useVocalSeparation = s.useVocalSeparation,
            lipSyncOffsetMs = s.lipSyncOffsetMs,
            textOverlay = s.artistTitleOverlay.ifBlank { null },
            lyricsOverlayEnabled = s.lyricsOverlayEnabled,
            outputVideoUrl = s.outputVideoUrl.ifBlank { null },
            duetLines = StudioRepository.duetLinesToMaps(s.duetLines),
            waveformSamples = s.waveformSamples
        )
    }
}
