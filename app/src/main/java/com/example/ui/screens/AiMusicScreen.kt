package com.example.ui.screens

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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.MediaPlaybackState
import com.example.model.StudioUiState
import com.example.ui.components.AiGeneratedMediaBadge
import com.example.ui.components.ApiConfigurationNotice
import com.example.ui.components.Media3AudioPlayerBar
import com.example.ui.components.SetupRequiredBanner
import com.example.ui.components.StatusBanner
import com.example.ui.components.StudioTopHeader
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.MidnightBlack
import com.example.ui.theme.StudioBorder
import com.example.viewmodel.StudioViewModel

private val GENRES = listOf(
    "Country", "Country Rap", "Gospel", "Southern Rock",
    "Pop", "R&B", "Blues", "Hip-Hop", "Americana"
)

private val MOODS = listOf("Soulful", "Anthemic", "Heartbreak", "Uplifting", "Gritty", "Romantic")

private val INSTRUMENTS = listOf(
    "Acoustic Guitar", "Pedal Steel", "Electric Guitar", "Piano",
    "808 Bass", "Live Drums", "Fiddle", "Organ", "Strings"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiMusicScreen(
    viewModel: StudioViewModel,
    playbackState: MediaPlaybackState,
    onBack: () -> Unit,
    onUseSongInDuet: (com.example.model.SongRecord) -> Unit
) {
    val form by viewModel.musicGenerator.collectAsStateWithLifecycle()
    val songsState by viewModel.songsState.collectAsStateWithLifecycle()
    val songs = (songsState as? StudioUiState.Success)?.data.orEmpty()
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBlack)
    ) {
        StudioTopHeader(
            title = "AI Music Generator",
            subtitle = "Full vocal tracks, duets & instrumentals",
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
                        featureName = "AI Music Audio Synthesis",
                        localCapabilities = "Gemini AI prompt/lyrics drafting and saving song briefs to My Songs work in Preview Mode."
                    )
                }
            }
            item {
                Text("Generation Mode", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "TEXT_TO_SONG" to "Text-to-Song",
                        "LYRICS_TO_SONG" to "Lyrics-to-Song",
                        "INSTRUMENTAL" to "Instrumental"
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = form.mode == key,
                            onClick = { viewModel.updateMusicForm { it.copy(mode = key) } },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MetallicGold,
                                selectedLabelColor = MidnightBlack
                            )
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = form.title,
                    onValueChange = { v -> viewModel.updateMusicForm { it.copy(title = v) } },
                    label = { Text("Song Title") },
                    placeholder = { Text("e.g. Gravel Road Redemption") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("music_title_input")
                )
            }

            item {
                OutlinedTextField(
                    value = form.prompt,
                    onValueChange = { v -> viewModel.updateMusicForm { it.copy(prompt = v) } },
                    label = { Text("Production Prompt") },
                    placeholder = { Text("Describe groove, instrumentation, vocal grit, and mix...") },
                    minLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("music_prompt_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.enhanceMusicPromptWithGemini() },
                    enabled = !form.isGenerating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = ElectricBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Draft Production Brief & Lyrics with Gemini AI")
                }
            }

            if (form.mode != "INSTRUMENTAL") {
                item {
                    OutlinedTextField(
                        value = form.lyrics,
                        onValueChange = { v -> viewModel.updateMusicForm { it.copy(lyrics = v) } },
                        label = { Text("Original Lyrics") },
                        placeholder = { Text("[Verse 1]\nEnter lyrics or generate them with Gemini...") },
                        minLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("music_lyrics_input")
                    )
                }

                item {
                    Text("Vocal Configuration", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("FEMALE", "MALE", "DUET").forEach { vocal ->
                            FilterChip(
                                selected = form.vocalType == vocal,
                                onClick = { viewModel.updateMusicForm { it.copy(vocalType = vocal) } },
                                label = { Text(vocal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricBlue,
                                    selectedLabelColor = MidnightBlack
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = form.vocalStyle,
                        onValueChange = { v -> viewModel.updateMusicForm { it.copy(vocalStyle = v) } },
                        label = { Text("Vocal Style Notes") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                Text("Genre", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GENRES.forEach { genre ->
                        FilterChip(
                            selected = form.genre == genre,
                            onClick = { viewModel.updateMusicForm { it.copy(genre = genre) } },
                            label = { Text(genre) }
                        )
                    }
                }
            }

            item {
                Text("Mood", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MOODS.forEach { mood ->
                        FilterChip(
                            selected = form.mood == mood,
                            onClick = { viewModel.updateMusicForm { it.copy(mood = mood) } },
                            label = { Text(mood) }
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Tempo", style = MaterialTheme.typography.titleSmall)
                    Text("${form.bpm} BPM", style = MaterialTheme.typography.labelMedium, color = MetallicGold)
                }
                Slider(
                    value = form.bpm.toFloat(),
                    onValueChange = { bpm -> viewModel.updateMusicForm { it.copy(bpm = bpm.toInt()) } },
                    valueRange = 60f..180f,
                    colors = SliderDefaults.colors(thumbColor = MetallicGold, activeTrackColor = MetallicGold)
                )

                Text("Instruments", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    INSTRUMENTS.forEach { inst ->
                        val selected = inst in form.instruments
                        FilterChip(
                            selected = selected,
                            onClick = {
                                viewModel.updateMusicForm { current ->
                                    val updated = if (selected) current.instruments - inst else current.instruments + inst
                                    current.copy(instruments = updated.take(8))
                                }
                            },
                            label = { Text(inst) }
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Versions: ${form.versionCount}", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 2, 3).forEach { count ->
                            FilterChip(
                                selected = form.versionCount == count,
                                onClick = { viewModel.updateMusicForm { it.copy(versionCount = count) } },
                                label = { Text("x$count") }
                            )
                        }
                    }
                }
            }

            item {
                StatusBanner(
                    statusMessage = form.statusMessage,
                    errorMessage = form.errorMessage,
                    progressPercent = if (form.isGenerating) form.progressPercent else null
                )
                form.setupRequiredMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    SetupRequiredBanner(message = msg)
                }
            }

            item {
                Button(
                    onClick = { viewModel.submitMusicGeneration() },
                    enabled = !form.isGenerating,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MetallicGold,
                        contentColor = MidnightBlack
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("generate_song_button")
                ) {
                    Icon(Icons.Filled.MusicNote, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (form.isGenerating) "Generating Song..." else "Generate AI Song",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            if (songs.isNotEmpty()) {
                item {
                    Text(
                        text = "Studio Song Versions",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                items(songs, key = { it.id }) { song ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, StudioBorder, MaterialTheme.shapes.medium)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(song.title, style = MaterialTheme.typography.titleMedium)
                                AiGeneratedMediaBadge(label = "v${song.versionNumber} • ${song.status}")
                            }
                            Text(
                                text = "${song.genre} • ${song.mood} • ${song.bpm} BPM • ${song.vocalType}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val playableUrl = song.audioUrl ?: song.wavUrl.orEmpty()
                            if (playableUrl.isNotBlank()) {
                                Media3AudioPlayerBar(
                                    title = song.title,
                                    audioUri = playableUrl,
                                    playbackState = playbackState,
                                    durationHintMs = song.durationMs
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.submitMusicGeneration(regenerateSong = song) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.width(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Regenerate")
                                }
                                OutlinedButton(
                                    onClick = { onUseSongInDuet(song) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Use in Duet")
                                }
                            }
                            if (!song.audioUrl.isNullOrBlank() || !song.wavUrl.isNullOrBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        val download = song.wavUrl ?: song.audioUrl ?: return@OutlinedButton
                                        runCatching { uriHandler.openUri(download) }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (!song.wavUrl.isNullOrBlank()) "Download WAV / MP3" else "Download MP3")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
