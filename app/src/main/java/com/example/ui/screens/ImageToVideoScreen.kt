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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.example.ui.components.AiGeneratedMediaBadge
import com.example.ui.components.ApiConfigurationNotice
import com.example.ui.components.Media3VideoPreview
import com.example.ui.components.SetupRequiredBanner
import com.example.ui.components.StatusBanner
import com.example.ui.components.StudioTopHeader
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.MidnightBlack
import com.example.ui.theme.StudioBorder
import com.example.viewmodel.StudioViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImageToVideoScreen(
    viewModel: StudioViewModel,
    playbackState: MediaPlaybackState,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.imageToVideo.collectAsStateWithLifecycle()

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.onImageToVideoImagePicked(context, uri)
        }
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.onImageToVideoAudioPicked(context, uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBlack)
    ) {
        StudioTopHeader(
            title = "Image-to-Video Generator",
            subtitle = "Cinematic motion & audio sync",
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
                        featureName = "Image-to-Video Motion Generation",
                        localCapabilities = "Image selection, motion/camera controls, and local audio attachment work in Preview Mode."
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = { v -> viewModel.updateImageToVideo { it.copy(title = v) } },
                    label = { Text("Scene Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(1.dp, StudioBorder, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.imageUri.isNotBlank()) {
                        AsyncImage(
                            model = state.imageUri,
                            contentDescription = "Source frame for video generation",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Image, contentDescription = null, tint = MetallicGold, modifier = Modifier.size(40.dp))
                            Text("Select a source image", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = MidnightBlack),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Upload Image")
                }
            }

            item {
                OutlinedTextField(
                    value = state.prompt,
                    onValueChange = { v -> viewModel.updateImageToVideo { it.copy(prompt = v) } },
                    label = { Text("Animation Prompt") },
                    placeholder = { Text("e.g. Golden stage embers drifting in slow motion under warm spotlights") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Text("Camera Movement", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Static", "Slow Zoom In", "Pan Left", "Pan Right", "Orbit", "Dolly Push").forEach { move ->
                        FilterChip(
                            selected = state.cameraMovement == move,
                            onClick = { viewModel.updateImageToVideo { it.copy(cameraMovement = move) } },
                            label = { Text(move) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Animation Style", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Cinematic", "Realistic").forEach { style ->
                        FilterChip(
                            selected = state.animationStyle == style,
                            onClick = { viewModel.updateImageToVideo { it.copy(animationStyle = style) } },
                            label = { Text(style) }
                        )
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = { audioPicker.launch(arrayOf("audio/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.AudioFile, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (state.selectedAudioTitle.isBlank()) "Attach Optional Audio Track"
                        else "Audio: ${state.selectedAudioTitle}"
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Optional Lip-Sync Post-Processing", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Run dedicated audio lip-sync pass after video motion generation",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.applyLipSyncPost,
                        onCheckedChange = { enabled ->
                            viewModel.updateImageToVideo { it.copy(applyLipSyncPost = enabled) }
                        }
                    )
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
                Button(
                    onClick = { viewModel.submitImageToVideo() },
                    enabled = !state.isGenerating,
                    colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = MidnightBlack),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("image_to_video_generate_button")
                ) {
                    Icon(Icons.Filled.MovieCreation, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (state.isGenerating) "Generating Video..." else "Generate & Export MP4",
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
                            Text("Rendered Image-to-Video MP4", style = MaterialTheme.typography.titleMedium, color = MetallicGold)
                            Media3VideoPreview(
                                videoUrl = state.outputVideoUrl,
                                aspectRatio = "16:9",
                                playbackState = playbackState
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = { viewModel.exportImageToVideoToDevice() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("i2v_save_mp4")
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
                                        .testTag("i2v_share_mp4")
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
