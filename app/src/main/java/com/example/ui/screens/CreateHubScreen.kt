package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.components.StudioTopHeader
import com.example.ui.theme.DeepPurple
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.MidnightBlack
import com.example.ui.theme.StudioBorder

@Composable
fun CreateHubScreen(
    backendConfigured: Boolean,
    onOpenRavenBreezeDuet: () -> Unit,
    onOpenAiMusic: () -> Unit,
    onOpenLyricsStudio: () -> Unit,
    onOpenPhotoSing: () -> Unit,
    onOpenImageToVideo: () -> Unit,
    onOpenAudioEditor: () -> Unit,
    onOpenVideoTimeline: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBlack)
    ) {
        StudioTopHeader(
            title = "Create Studio",
            subtitle = "Choose a production workflow"
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                CreateToolRow(
                    title = "Raven & Breeze Duet Mode",
                    description = "Two-singer portrait upload, vocal assignment timeline, 6 stage styles & lip-synced duet video.",
                    badge = if (backendConfigured) "DUET STUDIO" else "DUET STUDIO • RENDER NEEDS API",
                    icon = Icons.Filled.Groups,
                    containerColor = DeepPurple.copy(alpha = 0.7f),
                    accent = MetallicGold,
                    onClick = onOpenRavenBreezeDuet,
                    modifier = Modifier.testTag("create_raven_breeze_duet")
                )
            }
            item {
                CreateToolRow(
                    title = "AI Music Generator",
                    description = "Text-to-song, lyrics-to-song, or instrumental across Country, Gospel, Southern Rock, R&B & more.",
                    badge = if (backendConfigured) "AUDIO GEN" else "AUDIO GEN • NEEDS API CONFIG",
                    icon = Icons.Filled.MusicNote,
                    accent = MetallicGold,
                    onClick = onOpenAiMusic,
                    modifier = Modifier.testTag("create_ai_music")
                )
            }
            item {
                CreateToolRow(
                    title = "AI Lyrics Studio (Gemini)",
                    description = "Write verses, choruses, bridges, duet parts, and detailed music production prompts.",
                    badge = "GEMINI AI • ACTIVE",
                    icon = Icons.Filled.EditNote,
                    accent = ElectricBlue,
                    onClick = onOpenLyricsStudio,
                    modifier = Modifier.testTag("create_lyrics_studio")
                )
            }
            item {
                CreateToolRow(
                    title = "Photo-to-Singing Video",
                    description = "Face-verified portrait animation with audio-driven lip-sync and natural blinking.",
                    badge = if (backendConfigured) "LIP-SYNC" else "LIP-SYNC • NEEDS API CONFIG",
                    icon = Icons.Filled.RecordVoiceOver,
                    accent = ElectricBlue,
                    onClick = onOpenPhotoSing,
                    modifier = Modifier.testTag("create_photo_sing")
                )
            }
            item {
                CreateToolRow(
                    title = "Image-to-Video Generator",
                    description = "Animate artwork with camera movements, cinematic motion, and audio synchronization.",
                    badge = if (backendConfigured) "MOTION AI" else "MOTION AI • NEEDS API CONFIG",
                    icon = Icons.Filled.MovieCreation,
                    accent = MetallicGold,
                    onClick = onOpenImageToVideo,
                    modifier = Modifier.testTag("create_image_to_video")
                )
            }
            item {
                CreateToolRow(
                    title = "Audio Upload & Editor",
                    description = "MP3, WAV, M4A, AAC waveform viewer, trim, split, volume, fade-in/out & sync offset.",
                    badge = "AUDIO LAB • ACTIVE",
                    icon = Icons.Filled.AudioFile,
                    accent = ElectricBlue,
                    onClick = onOpenAudioEditor,
                    modifier = Modifier.testTag("create_audio_editor")
                )
            }
            item {
                CreateToolRow(
                    title = "Music Video Timeline Editor",
                    description = "Combine clips, images, lyrics overlays, album art, transitions, and 1080p MP4 export.",
                    badge = if (backendConfigured) "TIMELINE" else "TIMELINE • EXPORT NEEDS API",
                    icon = Icons.Filled.MovieCreation,
                    accent = MetallicGold,
                    onClick = onOpenVideoTimeline,
                    modifier = Modifier.testTag("create_video_timeline")
                )
            }
        }
    }
}

@Composable
private fun CreateToolRow(
    title: String,
    description: String,
    badge: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, if (containerColor != MaterialTheme.colorScheme.surfaceContainer) MetallicGold else StudioBorder, MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = accent,
                modifier = Modifier.size(30.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
