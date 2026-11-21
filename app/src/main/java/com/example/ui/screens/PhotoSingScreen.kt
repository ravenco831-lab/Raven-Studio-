package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.core.MediaPlaybackState
import com.example.model.StudioUiState
import com.example.ui.components.AiGeneratedMediaBadge
import com.example.ui.components.ApiConfigurationNotice
import com.example.ui.components.ConsentConfirmationCard
import com.example.ui.components.Media3AudioPlayerBar
import com.example.ui.components.Media3VideoPreview
import com.example.ui.components.SetupRequiredBanner
import com.example.ui.components.StatusBanner
import com.example.ui.components.StudioTopHeader
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.MidnightBlack
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSuccess
import com.example.ui.theme.StudioWarning
import com.example.viewmodel.StudioViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhotoSingScreen(
    viewModel: StudioViewModel,
    playbackState: MediaPlaybackState,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.photoSing.collectAsStateWithLifecycle()
    val songsState by viewModel.songsState.collectAsStateWithLifecycle()
    val assetsState by viewModel.mediaAssetsState.collectAsStateWithLifecycle()

    val songs = (songsState as? StudioUiState.Success)?.data.orEmpty()
    val audioAssets = (assetsState as? StudioUiState.Success)?.data.orEmpty().filter { it.mediaType == "AUDIO" }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.onPhotoSingPortraitPicked(context, uri)
        }
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.onPhotoSingAudioPicked(context, uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBlack)
    ) {
        StudioTopHeader(
            title = "Photo-to-Singing Video",
            subtitle = "Audio-driven facial lip-sync",
            onBack = onBack,
            trailing = { AiGeneratedMediaBadge() }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!viewModel.backendConfigured) {
                item {
                    ApiConfigurationNotice(
                        featureName = "Audio-Driven Facial Lip-Sync Video",
                        localCapabilities = "Portrait picking, ML Kit face detection, and local audio playback work in Preview Mode."
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = { v -> viewModel.updatePhotoSing { it.copy(title = v) } },
                    label = { Text("Performance Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

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
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("1. Portrait Image (JPG, PNG, WEBP)", style = MaterialTheme.typography.titleMedium)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MidnightBlack)
                                .border(1.dp, StudioBorder, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (state.portraitUri.isNotBlank()) {
                                AsyncImage(
                                    model = state.portraitUri,
                                    contentDescription = "Selected portrait for singing",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Filled.Face, contentDescription = null, tint = MetallicGold, modifier = Modifier.size(44.dp))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("No portrait selected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (state.isScanningFace) {
                                CircularProgressIndicator(color = MetallicGold)
                            }
                        }

                        state.faceAnalysis?.let { analysis ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (analysis.faceDetected) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = if (analysis.faceDetected) StudioSuccess else StudioWarning,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = analysis.statusMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (analysis.faceDetected) StudioSuccess else StudioWarning
                                )
                            }
                        }

                        Button(
                            onClick = {
                                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricBlue,
                                contentColor = MidnightBlack
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("photo_sing_pick_image")
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Upload Portrait via Photo Picker")
                        }
                    }
                }
            }

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
                        Text("2. Singing Audio Track", style = MaterialTheme.typography.titleMedium)

                        if (state.selectedAudioUri.isNotBlank()) {
                            Media3AudioPlayerBar(
                                title = state.selectedAudioTitle,
                                audioUri = state.selectedAudioUri,
                                playbackState = playbackState
                            )
                        }

                        OutlinedButton(
                            onClick = { audioPicker.launch(arrayOf("audio/*")) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.AudioFile, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Upload MP3 / WAV / M4A / AAC")
                        }

                        if (songs.isNotEmpty() || audioAssets.isNotEmpty()) {
                            Text("Or pick from your Library:", style = MaterialTheme.typography.labelMedium, color = MetallicGold)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                songs.filter { !it.audioUrl.isNullOrBlank() }.take(4).forEach { song ->
                                    FilterChip(
                                        selected = state.selectedAudioTitle == song.title,
                                        onClick = {
                                            viewModel.updatePhotoSing {
                                                it.copy(
                                                    selectedAudioTitle = song.title,
                                                    selectedAudioUri = song.audioUrl.orEmpty()
                                                )
                                            }
                                        },
                                        label = { Text(song.title) }
                                    )
                                }
                                audioAssets.take(4).forEach { asset ->
                                    val uri = asset.remoteUrl ?: asset.localUri.orEmpty()
                                    if (uri.isNotBlank()) {
                                        FilterChip(
                                            selected = state.selectedAudioUri == uri,
                                            onClick = {
                                                viewModel.updatePhotoSing {
                                                    it.copy(selectedAudioTitle = asset.title, selectedAudioUri = uri)
                                                }
                                            },
                                            label = { Text(asset.title) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text("Output Format & Facial Animation", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("9:16" to "Portrait (9:16)", "16:9" to "Landscape (16:9)").forEach { (ratio, label) ->
                        FilterChip(
                            selected = state.aspectRatio == ratio,
                            onClick = { viewModel.updatePhotoSing { it.copy(aspectRatio = ratio) } },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Expression, Blink & Head Motion", style = MaterialTheme.typography.bodySmall)
                    Text("${(state.expressionIntensity * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = MetallicGold)
                }
                Slider(
                    value = state.expressionIntensity,
                    onValueChange = { v -> viewModel.updatePhotoSing { it.copy(expressionIntensity = v) } },
                    colors = SliderDefaults.colors(thumbColor = MetallicGold, activeTrackColor = MetallicGold)
                )
            }

            item {
                ConsentConfirmationCard(
                    checked = state.consentConfirmed,
                    onCheckedChange = { c -> viewModel.updatePhotoSing { it.copy(consentConfirmed = c) } }
                )
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
                Button(
                    onClick = { viewModel.submitPhotoToSinging() },
                    enabled = !state.isGenerating,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MetallicGold,
                        contentColor = MidnightBlack
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("photo_sing_generate_button")
                ) {
                    Icon(Icons.Filled.RecordVoiceOver, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (state.isGenerating) "Rendering Lip-Sync..." else "Generate Singing Video",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            if (state.outputVideoUrl.isNotBlank()) {
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
                            Text("Rendered Singing Video", style = MaterialTheme.typography.titleMedium, color = MetallicGold)
                            Media3VideoPreview(
                                videoUrl = state.outputVideoUrl,
                                aspectRatio = state.aspectRatio,
                                playbackState = playbackState
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = { viewModel.exportPhotoSingVideoToDevice() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("photo_sing_save_mp4")
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Save MP4")
                                }
                                OutlinedButton(
                                    onClick = {
                                        viewModel.shareUriOrUrl(context, state.outputVideoUrl, state.title, "video/mp4")
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("photo_sing_share_mp4")
                                ) {
                                    Icon(Icons.Filled.Share, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Share")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
