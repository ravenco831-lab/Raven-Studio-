package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.example.ui.components.AudioWaveformCanvas
import com.example.ui.components.Media3AudioPlayerBar
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

@Composable
fun AudioEditorScreen(
    viewModel: StudioViewModel,
    playbackState: MediaPlaybackState,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.audioEditor.collectAsStateWithLifecycle()

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.onAudioFilePicked(context, uri)
        }
    }

    val safeDuration = state.durationMs.coerceAtLeast(1000L)
    val trimStartFrac = (state.trimStartMs.toFloat() / safeDuration).coerceIn(0f, 1f)
    val trimEndFrac = (state.trimEndMs.toFloat() / safeDuration).coerceIn(trimStartFrac, 1f)
    val splitFrac = (state.splitPointMs.toFloat() / safeDuration).coerceIn(0f, 1f)
    val progressFrac = if (playbackState.currentUri == state.uriString && safeDuration > 0L) {
        (playbackState.currentPositionMs.toFloat() / safeDuration).coerceIn(0f, 1f)
    } else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBlack)
    ) {
        StudioTopHeader(
            title = "Audio Upload & Editor",
            subtitle = "MP3, WAV, M4A & AAC Mastering",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!viewModel.backendConfigured) {
                item {
                    ApiConfigurationNotice(
                        featureName = "FFmpeg Final Mix Export",
                        localCapabilities = "Uploading MP3/WAV/M4A/AAC, PCM waveform decoding, ExoPlayer playback, trim/fade/sync edits & saving to My Audio work locally."
                    )
                }
            }
            item {
                Button(
                    onClick = {
                        audioPicker.launch(arrayOf("audio/mpeg", "audio/wav", "audio/x-wav", "audio/mp4", "audio/aac", "audio/*"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = MidnightBlack),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("pick_audio_file_button")
                ) {
                    Icon(Icons.Filled.AudioFile, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (state.isAnalyzing) "Analyzing Audio..." else "Upload Audio File (MP3/WAV/M4A/AAC)")
                }
            }

            if (state.uriString.isNotBlank()) {
                item {
                    Media3AudioPlayerBar(
                        title = state.title,
                        audioUri = state.uriString,
                        playbackState = playbackState,
                        waveformSamples = state.waveformSamples,
                        durationHintMs = state.durationMs
                    )
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
                        Text("Trim, Split & Waveform Markers", style = MaterialTheme.typography.titleMedium)
                        AudioWaveformCanvas(
                            samples = state.waveformSamples,
                            progressFraction = progressFrac,
                            trimStartFraction = trimStartFrac,
                            trimEndFraction = trimEndFrac,
                            splitFraction = splitFrac
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Trim In: ${formatTimeMs(state.trimStartMs)}", style = MaterialTheme.typography.labelMedium)
                            Text("Trim Out: ${formatTimeMs(state.trimEndMs)}", style = MaterialTheme.typography.labelMedium, color = MetallicGold)
                        }

                        Slider(
                            value = trimStartFrac,
                            onValueChange = { f ->
                                val newStart = (f * safeDuration).toLong().coerceAtMost(state.trimEndMs - 500L).coerceAtLeast(0L)
                                viewModel.updateAudioEditor { it.copy(trimStartMs = newStart) }
                            },
                            colors = SliderDefaults.colors(thumbColor = ElectricBlue, activeTrackColor = ElectricBlue)
                        )
                        Slider(
                            value = trimEndFrac,
                            onValueChange = { f ->
                                val newEnd = (f * safeDuration).toLong().coerceAtLeast(state.trimStartMs + 500L)
                                viewModel.updateAudioEditor { it.copy(trimEndMs = newEnd) }
                            },
                            colors = SliderDefaults.colors(thumbColor = MetallicGold, activeTrackColor = MetallicGold)
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Split Point: ${formatTimeMs(state.splitPointMs)}", style = MaterialTheme.typography.labelMedium)
                            Icon(Icons.Filled.ContentCut, contentDescription = null, tint = MetallicGold)
                        }
                        Slider(
                            value = splitFrac,
                            onValueChange = { f ->
                                viewModel.updateAudioEditor { it.copy(splitPointMs = (f * safeDuration).toLong()) }
                            }
                        )
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
                        Text("Volume, Fades & Video Sync", style = MaterialTheme.typography.titleMedium)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Gain / Volume", style = MaterialTheme.typography.bodySmall)
                            Text("${(state.volume * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = MetallicGold)
                        }
                        Slider(
                            value = state.volume,
                            onValueChange = { v ->
                                viewModel.updateAudioEditor { it.copy(volume = v) }
                                playbackState.setVolume(v.coerceIn(0f, 1f))
                            },
                            valueRange = 0f..1.5f
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Fade In: ${state.fadeInMs}ms", style = MaterialTheme.typography.bodySmall)
                            Text("Fade Out: ${state.fadeOutMs}ms", style = MaterialTheme.typography.bodySmall)
                        }
                        Slider(
                            value = state.fadeInMs.toFloat(),
                            onValueChange = { ms -> viewModel.updateAudioEditor { it.copy(fadeInMs = ms.toLong()) } },
                            valueRange = 0f..5000f
                        )
                        Slider(
                            value = state.fadeOutMs.toFloat(),
                            onValueChange = { ms -> viewModel.updateAudioEditor { it.copy(fadeOutMs = ms.toLong()) } },
                            valueRange = 0f..5000f
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Audio-to-Video Sync Offset", style = MaterialTheme.typography.bodySmall)
                            Text("${state.syncOffsetMs} ms", style = MaterialTheme.typography.labelMedium, color = ElectricBlue)
                        }
                        Slider(
                            value = state.syncOffsetMs.toFloat(),
                            onValueChange = { ms -> viewModel.updateAudioEditor { it.copy(syncOffsetMs = ms.toLong()) } },
                            valueRange = -2000f..2000f
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Replace Original Video Audio", style = MaterialTheme.typography.bodySmall)
                            Switch(
                                checked = state.replaceVideoAudio,
                                onCheckedChange = { r -> viewModel.updateAudioEditor { it.copy(replaceVideoAudio = r) } }
                            )
                        }
                    }
                }
            }

            item {
                StatusBanner(
                    statusMessage = state.statusMessage,
                    errorMessage = state.errorMessage
                )
                state.setupRequiredMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    SetupRequiredBanner(message = msg)
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.saveAudioEdits() },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save to Library")
                    }
                    Button(
                        onClick = { viewModel.exportAudioWithFfmpeg() },
                        enabled = !state.isExporting,
                        colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = MidnightBlack),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Text(if (state.isExporting) "Exporting..." else "Export Mix")
                    }
                }
                if (state.uriString.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.exportAudioEditorCopyToDevice() },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("audio_save_to_device_button")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save to Device")
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.shareUriOrUrl(
                                    context = context,
                                    uriOrUrl = state.uriString,
                                    title = state.title.ifBlank { "Raven Audio" },
                                    mimeType = state.mimeType
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("audio_share_button")
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share")
                        }
                    }
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
                            Text("Exported FFmpeg MP4 Mix", style = MaterialTheme.typography.titleMedium, color = MetallicGold)
                            Media3VideoPreview(
                                videoUrl = state.outputVideoUrl,
                                aspectRatio = "9:16",
                                playbackState = playbackState
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = { viewModel.exportAudioEditorVideoToDevice() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("audio_save_exported_mp4")
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Save MP4")
                                }
                                OutlinedButton(
                                    onClick = {
                                        viewModel.shareUriOrUrl(
                                            context = context,
                                            uriOrUrl = state.outputVideoUrl,
                                            title = "${state.title.ifBlank { "Raven Audio" }} Mix",
                                            mimeType = "video/mp4"
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("audio_share_exported_mp4")
                                ) {
                                    Icon(Icons.Filled.Share, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Share MP4")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
