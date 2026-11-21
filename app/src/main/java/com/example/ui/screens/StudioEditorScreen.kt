package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.MediaPlaybackState
import com.example.ui.components.ApiConfigurationNotice
import com.example.ui.components.Media3VideoPreview
import com.example.ui.components.SetupRequiredBanner
import com.example.ui.components.StatusBanner
import com.example.ui.components.StudioTopHeader
import com.example.ui.components.formatTimeMs
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.MidnightBlack
import com.example.ui.theme.StudioBorder
import com.example.viewmodel.StudioViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioEditorScreen(
    viewModel: StudioViewModel,
    playbackState: MediaPlaybackState,
    onOpenDuetMode: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.videoTimeline.collectAsStateWithLifecycle()

    val imageClipPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.onTimelineClipPicked(context, uri, "IMAGE")
        }
    }

    val videoClipPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.onTimelineClipPicked(context, uri, "VIDEO")
        }
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.onTimelineAudioPicked(context, uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBlack)
    ) {
        StudioTopHeader(
            title = "Music Video Editor",
            subtitle = "Multi-track timeline & MP4 export",
            trailing = {
                OutlinedButton(onClick = onOpenDuetMode) {
                    Text("Duet Mode")
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!viewModel.backendConfigured) {
                item {
                    ApiConfigurationNotice(
                        featureName = "Server-Side FFmpeg MP4 Export",
                        localCapabilities = "Adding clips, attaching audio, configuring transitions/aspect ratios, and saving timeline projects work in Preview Mode."
                    )
                }
            }
            item {
                Media3VideoPreview(
                    videoUrl = state.previewVideoUrl,
                    aspectRatio = state.aspectRatio,
                    playbackState = playbackState
                )
            }

            item {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = { v -> viewModel.updateTimeline { it.copy(title = v) } },
                    label = { Text("Project Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            videoClipPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.VideoLibrary, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Video")
                    }
                    OutlinedButton(
                        onClick = {
                            imageClipPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Image")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { audioPicker.launch(arrayOf("audio/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (state.audioTitle.isBlank()) "Attach Background Audio Track" else "Audio: ${state.audioTitle}")
                }
            }

            if (state.clips.isNotEmpty()) {
                item {
                    Text("Timeline Clips (${state.clips.size})", style = MaterialTheme.typography.titleSmall)
                }
                items(state.clips, key = { it.id }) { clip ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, StudioBorder, MaterialTheme.shapes.small)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(clip.label, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${clip.mediaType} • ${formatTimeMs(clip.startMs)} - ${formatTimeMs(clip.endMs)} • ${clip.transition}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricBlue
                                )
                            }
                            IconButton(onClick = { viewModel.removeClipFromTimeline(clip.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove clip", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = state.textOverlay,
                    onValueChange = { v -> viewModel.updateTimeline { it.copy(textOverlay = v) } },
                    label = { Text("Text / Title Overlay") },
                    placeholder = { Text("Artist Name — Song Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Text("Scene Transition", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Cut", "Crossfade", "Dip to Gold", "Slide").forEach { t ->
                        FilterChip(
                            selected = state.transitionStyle == t,
                            onClick = { viewModel.updateTimeline { it.copy(transitionStyle = t) } },
                            label = { Text(t) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Aspect Ratio & Export Quality", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("9:16", "16:9", "1:1").forEach { ratio ->
                        FilterChip(
                            selected = state.aspectRatio == ratio,
                            onClick = { viewModel.updateTimeline { it.copy(aspectRatio = ratio) } },
                            label = { Text(ratio) }
                        )
                    }
                    listOf("720p", "1080p").forEach { res ->
                        FilterChip(
                            selected = state.resolution == res,
                            onClick = { viewModel.updateTimeline { it.copy(resolution = res) } },
                            label = { Text(res) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Synchronized Lyrics Overlay", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = state.lyricsOverlayEnabled,
                        onCheckedChange = { l -> viewModel.updateTimeline { it.copy(lyricsOverlayEnabled = l) } }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Raven & Co. Watermark", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = state.watermarkEnabled,
                        onCheckedChange = { w -> viewModel.updateTimeline { it.copy(watermarkEnabled = w) } }
                    )
                }
            }

            item {
                StatusBanner(
                    statusMessage = state.statusMessage,
                    errorMessage = state.errorMessage,
                    progressPercent = if (state.isExporting) state.progressPercent else null
                )
                state.setupRequiredMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    SetupRequiredBanner(message = msg)
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.saveAndExportTimeline(exportMp4 = false) },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Project")
                    }
                    Button(
                        onClick = { viewModel.saveAndExportTimeline(exportMp4 = true) },
                        enabled = !state.isExporting,
                        colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = MidnightBlack),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("timeline_export_button")
                    ) {
                        Icon(Icons.Filled.MovieCreation, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (state.isExporting) "Exporting..." else "Export MP4")
                    }
                }
                if (state.previewVideoUrl.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.exportTimelineVideoToDevice() },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("timeline_save_to_device_button")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save MP4")
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.shareUriOrUrl(
                                    context = context,
                                    uriOrUrl = state.previewVideoUrl,
                                    title = state.title,
                                    mimeType = "video/mp4"
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("timeline_share_button")
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
