package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.MediaPlaybackState
import com.example.model.StudioUiState
import com.example.ui.components.AiGeneratedMediaBadge
import com.example.ui.components.ApiConfigurationNotice
import com.example.ui.components.ConsentConfirmationCard
import com.example.ui.components.DuetSingerPortraitPanel
import com.example.ui.components.DuetVocalTimelineRow
import com.example.ui.components.Media3AudioPlayerBar
import com.example.ui.components.Media3VideoPreview
import com.example.ui.components.SetupRequiredBanner
import com.example.ui.components.StatusBanner
import com.example.ui.components.StudioTopHeader
import com.example.ui.theme.DeepPurple
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.MidnightBlack
import com.example.ui.theme.StudioBorder
import com.example.viewmodel.DuetModeViewModel
import com.example.viewmodel.DuetPerformanceStyle
import com.example.viewmodel.StudioViewModel

private val STEP_TITLES = listOf(
    1 to "1. Raven",
    2 to "2. Breeze",
    3 to "3. Song",
    4 to "4. Vocals",
    5 to "5. Scene",
    6 to "6. Review",
    7 to "7. Render",
    8 to "8. Edit/Export"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DuetModeScreen(
    duetViewModel: DuetModeViewModel,
    studioViewModel: StudioViewModel,
    playbackState: MediaPlaybackState,
    onBack: () -> Unit,
    onNavigateToAiSong: () -> Unit
) {
    val context = LocalContext.current
    val state by duetViewModel.state.collectAsStateWithLifecycle()
    val songsState by studioViewModel.songsState.collectAsStateWithLifecycle()
    val songs = (songsState as? StudioUiState.Success)?.data.orEmpty()

    val singer1Picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            duetViewModel.onSingerPortraitPicked(context, 1, uri)
            if (state.currentStep == 1) duetViewModel.setStep(2)
        }
    }

    val singer2Picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            duetViewModel.onSingerPortraitPicked(context, 2, uri)
            if (state.currentStep == 2) duetViewModel.setStep(3)
        }
    }

    val duetAudioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            duetViewModel.selectUploadedAudio(context, uri)
            duetViewModel.setStep(4)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBlack)
    ) {
        StudioTopHeader(
            title = "Raven & Breeze Duet Mode",
            subtitle = "Guided 8-Step AI Duet Performance",
            onBack = onBack,
            trailing = {
                OutlinedButton(
                    onClick = { duetViewModel.saveDuetProject() },
                    modifier = Modifier.testTag("duet_save_project_button")
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.width(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save")
                }
            }
        )

        // 8-Step Guided Workflow Stepper
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            STEP_TITLES.forEach { (stepNum, label) ->
                val active = state.currentStep == stepNum
                FilterChip(
                    selected = active,
                    onClick = { duetViewModel.setStep(stepNum) },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MetallicGold,
                        selectedLabelColor = MidnightBlack
                    )
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!studioViewModel.backendConfigured) {
                item {
                    ApiConfigurationNotice(
                        featureName = "Duet Lip-Sync & Video Rendering",
                        localCapabilities = "All 8 Raven & Breeze setup steps, ML Kit portrait scans, local audio upload, Gemini vocal mapping, and saving projects work now."
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = state.projectTitle,
                    onValueChange = { v -> duetViewModel.updateState { it.copy(projectTitle = v) } },
                    label = { Text("Duet Project Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Side-by-Side Portrait Panels (Always visible for Raven & Breeze Duet Studio)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DuetSingerPortraitPanel(
                        singerIndex = 1,
                        name = state.singer1Name,
                        vocalRole = state.singer1VocalRole,
                        portraitUri = state.singer1PortraitUri,
                        placeholderDrawableRes = R.drawable.img_avatar_raven,
                        faceResult = state.singer1FaceResult,
                        isScanning = state.singer1Scanning,
                        buttonLabel = if (state.singer1Name == "Raven") "Upload Raven" else "Upload ${state.singer1Name}",
                        accentColor = MetallicGold,
                        onNameChange = { n -> duetViewModel.updateState { it.copy(singer1Name = n) } },
                        onRoleChange = { r -> duetViewModel.updateState { it.copy(singer1VocalRole = r) } },
                        onUploadClick = {
                            singer1Picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.weight(1f)
                    )

                    DuetSingerPortraitPanel(
                        singerIndex = 2,
                        name = state.singer2Name,
                        vocalRole = state.singer2VocalRole,
                        portraitUri = state.singer2PortraitUri,
                        placeholderDrawableRes = R.drawable.img_avatar_breeze,
                        faceResult = state.singer2FaceResult,
                        isScanning = state.singer2Scanning,
                        buttonLabel = if (state.singer2Name == "Breeze") "Upload Breeze" else "Upload ${state.singer2Name}",
                        accentColor = ElectricBlue,
                        onNameChange = { n -> duetViewModel.updateState { it.copy(singer2Name = n) } },
                        onRoleChange = { r -> duetViewModel.updateState { it.copy(singer2VocalRole = r) } },
                        onUploadClick = {
                            singer2Picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Quick Action Ribbon matching requested button labels
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { duetViewModel.setStep(3) },
                        modifier = Modifier.testTag("duet_select_song_button")
                    ) {
                        Text("Select Song")
                    }
                    OutlinedButton(
                        onClick = { duetViewModel.setStep(4) },
                        modifier = Modifier.testTag("duet_assign_vocals_button")
                    ) {
                        Text("Assign Vocals")
                    }
                    OutlinedButton(
                        onClick = { duetViewModel.setStep(5) },
                        modifier = Modifier.testTag("duet_choose_scene_button")
                    ) {
                        Text("Choose Scene")
                    }
                }
            }

            // Music Waveform & Playback below portraits
            item {
                Media3AudioPlayerBar(
                    title = state.selectedSongTitle.ifBlank { "Step 3: Select or Upload Duet Song" },
                    audioUri = state.selectedAudioUri,
                    playbackState = playbackState,
                    waveformSamples = state.waveformSamples,
                    durationHintMs = state.audioDurationMs
                )
            }

            // Step 3: Select Duet Song
            if (state.currentStep <= 3) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, StudioBorder, MaterialTheme.shapes.large)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Select Duet Song", style = MaterialTheme.typography.titleMedium, color = MetallicGold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        duetAudioPicker.launch(arrayOf("audio/mpeg", "audio/wav", "audio/x-wav", "audio/mp4", "audio/*"))
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = MidnightBlack),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(if (state.isAnalyzingAudio) "Decoding..." else "Upload MP3/WAV/M4A")
                                }
                                OutlinedButton(
                                    onClick = onNavigateToAiSong,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.width(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Generate AI Duet")
                                }
                            }
                            if (songs.isNotEmpty()) {
                                Text("Or pick from My Songs:", style = MaterialTheme.typography.labelMedium)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    songs.take(6).forEach { song ->
                                        FilterChip(
                                            selected = state.selectedSongTitle == song.title,
                                            onClick = { duetViewModel.selectSongFromLibrary(song) },
                                            label = { Text(song.title) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Step 4: Vocal Assignment & Lip Sync Timeline
            if (state.currentStep == 4 || state.currentStep == 6 || state.currentStep == 8) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, StudioBorder, MaterialTheme.shapes.large)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Vocal Assignment & Lip Sync", style = MaterialTheme.typography.titleMedium)
                                AiGeneratedMediaBadge(label = "Color-Coded")
                            }

                            OutlinedTextField(
                                value = state.rawLyricsDraft,
                                onValueChange = { v -> duetViewModel.updateState { it.copy(rawLyricsDraft = v) } },
                                label = { Text("Duet Lyrics Draft for Auto-Analysis") },
                                minLines = 3,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { duetViewModel.autoAnalyzeVocalsWithGemini() },
                                    enabled = !state.isAnalyzingVocals,
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = MidnightBlack),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.width(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (state.isAnalyzingVocals) "Analyzing..." else "Auto-Assign Vocals")
                                }
                                OutlinedButton(
                                    onClick = { duetViewModel.addDuetLine("RAVEN") },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.width(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Line")
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Vocal Stem Isolation", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "Separates overlapping vocals when supported. If vocal isolation is unavailable on mixed harmonies, timing falls back to manual line cues.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = state.useVocalSeparation,
                                    onCheckedChange = { u -> duetViewModel.updateState { it.copy(useVocalSeparation = u) } }
                                )
                            }
                        }
                    }
                }

                items(state.duetLines, key = { it.id }) { line ->
                    DuetVocalTimelineRow(
                        line = line,
                        singer1Name = state.singer1Name,
                        singer2Name = state.singer2Name,
                        onSelectSinger = { s -> duetViewModel.updateLineSinger(line.id, s) },
                        onLyricChange = { txt -> duetViewModel.updateLineLyric(line.id, txt) },
                        onRemove = { duetViewModel.removeDuetLine(line.id) }
                    )
                }
            }

            // Step 5: Performance Styles & Camera Animation
            if (state.currentStep == 5 || state.currentStep == 6) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, StudioBorder, MaterialTheme.shapes.large)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Choose Performance Style", style = MaterialTheme.typography.titleMedium, color = MetallicGold)
                            DuetPerformanceStyle.entries.forEach { style ->
                                val selected = state.performanceStyle == style
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (selected) DeepPurple else MidnightBlack)
                                        .border(1.dp, if (selected) MetallicGold else StudioBorder, RoundedCornerShape(12.dp))
                                        .clickable { duetViewModel.updateState { it.copy(performanceStyle = style) } }
                                        .padding(12.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = style.label,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = if (selected) MetallicGold else Color.White
                                        )
                                        Text(
                                            text = style.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Performance Intensity", style = MaterialTheme.typography.titleSmall)
                                Text("${(state.performanceIntensity * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = MetallicGold)
                            }
                            Slider(
                                value = state.performanceIntensity,
                                onValueChange = { v -> duetViewModel.updateState { it.copy(performanceIntensity = v) } },
                                colors = SliderDefaults.colors(thumbColor = MetallicGold, activeTrackColor = MetallicGold)
                            )

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Active Singer Close-Ups & Auto Switching", style = MaterialTheme.typography.bodySmall)
                                Switch(
                                    checked = state.closeUpSwitching,
                                    onCheckedChange = { c -> duetViewModel.updateState { it.copy(closeUpSwitching = c) } }
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Two-Person Chorus Shots", style = MaterialTheme.typography.bodySmall)
                                Switch(
                                    checked = state.twoPersonChorusShots,
                                    onCheckedChange = { c -> duetViewModel.updateState { it.copy(twoPersonChorusShots = c) } }
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Natural Blinking & Instrumental Breathing", style = MaterialTheme.typography.bodySmall)
                                Switch(
                                    checked = state.naturalBlinkingAndBreathing,
                                    onCheckedChange = { c -> duetViewModel.updateState { it.copy(naturalBlinkingAndBreathing = c) } }
                                )
                            }
                        }
                    }
                }
            }

            // Step 6 & 7: Consent Confirmation + Generate Duet
            if (state.currentStep >= 6) {
                item {
                    ConsentConfirmationCard(
                        checked = state.consentConfirmed,
                        onCheckedChange = { c -> duetViewModel.updateState { it.copy(consentConfirmed = c) } },
                        text = "I confirm I have permission to animate both portraits (${state.singer1Name} & ${state.singer2Name}) and use the selected duet audio."
                    )
                }
            }

            // Step 8: Post-Generation Music Video Editor & Export
            if (state.currentStep == 8 || state.outputVideoUrl.isNotBlank()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MetallicGold.copy(alpha = 0.5f), MaterialTheme.shapes.large)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Duet Video Preview & Editor", style = MaterialTheme.typography.titleMedium, color = MetallicGold)
                            Media3VideoPreview(
                                videoUrl = state.outputVideoUrl,
                                aspectRatio = state.aspectRatio,
                                playbackState = playbackState
                            )
                            if (state.outputVideoUrl.isNotBlank()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedButton(
                                        onClick = { duetViewModel.saveDuetVideoToDevice() },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("duet_save_to_device_button")
                                    ) {
                                        Icon(Icons.Filled.Download, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Save MP4")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            studioViewModel.shareUriOrUrl(
                                                context = context,
                                                uriOrUrl = state.outputVideoUrl,
                                                title = state.projectTitle,
                                                mimeType = "video/mp4"
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("duet_share_video_button")
                                    ) {
                                        Icon(Icons.Filled.Share, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Share")
                                    }
                                }
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Lip-Sync Timing Offset", style = MaterialTheme.typography.bodySmall)
                                Text("${state.lipSyncOffsetMs} ms", style = MaterialTheme.typography.labelMedium, color = ElectricBlue)
                            }
                            Slider(
                                value = state.lipSyncOffsetMs.toFloat(),
                                onValueChange = { ms -> duetViewModel.updateState { it.copy(lipSyncOffsetMs = ms.toLong()) } },
                                valueRange = -1500f..1500f
                            )

                            OutlinedTextField(
                                value = state.artistTitleOverlay,
                                onValueChange = { v -> duetViewModel.updateState { it.copy(artistTitleOverlay = v) } },
                                label = { Text("Artist Names & Song Title Overlay") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("9:16", "16:9", "1:1").forEach { ratio ->
                                    FilterChip(
                                        selected = state.aspectRatio == ratio,
                                        onClick = { duetViewModel.updateState { it.copy(aspectRatio = ratio) } },
                                        label = { Text(ratio) }
                                    )
                                }
                                listOf("720p", "1080p").forEach { res ->
                                    FilterChip(
                                        selected = state.resolution == res,
                                        onClick = { duetViewModel.updateState { it.copy(resolution = res) } },
                                        label = { Text(res) }
                                    )
                                }
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Synchronized Lyrics Overlay", style = MaterialTheme.typography.bodySmall)
                                Switch(
                                    checked = state.lyricsOverlayEnabled,
                                    onCheckedChange = { l -> duetViewModel.updateState { it.copy(lyricsOverlayEnabled = l) } }
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Include Raven & Co. Watermark", style = MaterialTheme.typography.bodySmall)
                                Switch(
                                    checked = state.watermarkEnabled,
                                    onCheckedChange = { w -> duetViewModel.updateState { it.copy(watermarkEnabled = w) } }
                                )
                            }
                        }
                    }
                }
            }

            item {
                StatusBanner(
                    statusMessage = state.statusMessage,
                    errorMessage = state.errorMessage,
                    progressPercent = if (state.isGenerating) state.progressPercent else null
                )
                state.setupRequiredMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    SetupRequiredBanner(message = msg)
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { duetViewModel.generateDuetPerformance() },
                        enabled = !state.isGenerating,
                        colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = MidnightBlack),
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .testTag("generate_duet_button")
                    ) {
                        Icon(Icons.Filled.Groups, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (state.isGenerating) "Rendering..." else "Generate Duet")
                    }

                    Button(
                        onClick = { duetViewModel.exportFinalDuetMp4() },
                        enabled = !state.isGenerating,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = MidnightBlack),
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .testTag("export_video_button")
                    ) {
                        Icon(Icons.Filled.MovieCreation, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export Video")
                    }
                }
            }
        }
    }
}
