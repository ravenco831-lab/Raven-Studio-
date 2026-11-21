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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Save
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.AiGeneratedMediaBadge
import com.example.ui.components.StatusBanner
import com.example.ui.components.StudioTopHeader
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.MidnightBlack
import com.example.ui.theme.StudioBorder
import com.example.viewmodel.StudioViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LyricsStudioScreen(
    viewModel: StudioViewModel,
    onBack: () -> Unit,
    onSendToMusicGenerator: () -> Unit
) {
    val state by viewModel.lyricsStudio.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBlack)
    ) {
        StudioTopHeader(
            title = "AI Lyrics Studio",
            subtitle = "Powered by Gemini AI",
            onBack = onBack,
            trailing = { AiGeneratedMediaBadge(label = "Gemini 3.8") }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = { v -> viewModel.updateLyricsForm { it.copy(title = v) } },
                    label = { Text("Song Title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lyrics_title_input")
                )
            }

            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Country", "Country Rap", "Gospel", "Southern Rock", "R&B", "Pop", "Americana").forEach { g ->
                        FilterChip(
                            selected = state.genre == g,
                            onClick = { viewModel.updateLyricsForm { it.copy(genre = g) } },
                            label = { Text(g) }
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = state.topic,
                    onValueChange = { v -> viewModel.updateLyricsForm { it.copy(topic = v) } },
                    label = { Text("Story, Hook, or Theme") },
                    placeholder = { Text("e.g. Two voices meeting in the rain outside Nashville") },
                    minLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lyrics_topic_input")
                )
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, StudioBorder, MaterialTheme.shapes.medium)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Duet Arrangement Mode", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Assign parts to Singer 1, Singer 2 & Shared Harmonies",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.duetMode,
                                onCheckedChange = { enabled ->
                                    viewModel.updateLyricsForm { it.copy(duetMode = enabled) }
                                }
                            )
                        }
                        if (state.duetMode) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = state.singer1Name,
                                    onValueChange = { v -> viewModel.updateLyricsForm { it.copy(singer1Name = v) } },
                                    label = { Text("Singer 1") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = state.singer2Name,
                                    onValueChange = { v -> viewModel.updateLyricsForm { it.copy(singer2Name = v) } },
                                    label = { Text("Singer 2") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = { viewModel.streamAndGenerateLyrics() },
                    enabled = !state.isGenerating,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MetallicGold,
                        contentColor = MidnightBlack
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("generate_lyrics_button")
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (state.isGenerating) "Writing Lyrics..." else "Generate Complete Song & Prompt",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            item {
                StatusBanner(
                    statusMessage = state.statusMessage,
                    errorMessage = state.errorMessage
                )
            }

            if (state.streamingPreview.isNotBlank()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ElectricBlue.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Live Gemini Stream", style = MaterialTheme.typography.labelMedium, color = ElectricBlue)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(state.streamingPreview, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = state.content,
                    onValueChange = { v -> viewModel.updateLyricsForm { it.copy(content = v) } },
                    label = { Text("Lyrics Sheet (Verse, Chorus, Bridge, Outro)") },
                    minLines = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lyrics_content_input")
                )
            }

            item {
                OutlinedTextField(
                    value = state.productionPrompt,
                    onValueChange = { v -> viewModel.updateLyricsForm { it.copy(productionPrompt = v) } },
                    label = { Text("Music Production Prompt") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value = state.rewriteInstruction,
                    onValueChange = { v -> viewModel.updateLyricsForm { it.copy(rewriteInstruction = v) } },
                    label = { Text("Rewrite / Improve Note") },
                    placeholder = { Text("e.g. Make the bridge punchier and add vocal harmonies") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.rewriteLyricsWithGemini() },
                        enabled = !state.isGenerating,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Rewrite")
                    }
                    OutlinedButton(
                        onClick = { viewModel.saveCurrentLyrics() },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_lyrics_button")
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        viewModel.sendLyricsToMusicGenerator()
                        onSendToMusicGenerator()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricBlue,
                        contentColor = MidnightBlack
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.MusicNote, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Send Lyrics to AI Music Generator")
                }
            }
        }
    }
}
