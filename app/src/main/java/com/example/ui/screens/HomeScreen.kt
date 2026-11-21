package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FaceRetouchingNatural
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.StudioUiState
import com.example.ui.components.AiGeneratedMediaBadge
import com.example.ui.components.ApiConfigurationNotice
import com.example.ui.components.StatusBanner
import com.example.ui.theme.DeepPurple
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.MidnightBlack
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioWarning
import com.example.viewmodel.StudioViewModel

@Composable
fun HomeScreen(
    viewModel: StudioViewModel,
    onNavigateToDuetRavenBreeze: () -> Unit,
    onNavigateToCreateSong: () -> Unit,
    onNavigateToWriteLyrics: () -> Unit,
    onNavigateToPhotoSing: () -> Unit,
    onNavigateToImageToVideo: () -> Unit,
    onNavigateToCreateDuet: () -> Unit,
    onNavigateToUploadAudio: () -> Unit
) {
    val context = LocalContext.current
    val jobsState by viewModel.jobsState.collectAsStateWithLifecycle()
    val songsState by viewModel.songsState.collectAsStateWithLifecycle()
    val projectsState by viewModel.projectsState.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImportingMedia.collectAsStateWithLifecycle()
    val libraryStatus by viewModel.libraryStatusMessage.collectAsStateWithLifecycle()

    val songsCount = (songsState as? StudioUiState.Success)?.data?.size ?: 0
    val projectsCount = (projectsState as? StudioUiState.Success)?.data?.size ?: 0
    val recentJobs = (jobsState as? StudioUiState.Success)?.data.orEmpty().take(2)

    val homeAudioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importDeviceMedia(context, uri, "AUDIO")
    }
    val homeImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.importDeviceMedia(context, uri, "IMAGE")
    }
    val homeVideoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.importDeviceMedia(context, uri, "VIDEO")
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBlack),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Studio Hero Banner
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, StudioBorder, MaterialTheme.shapes.large)
            ) {
                Box(modifier = Modifier.height(190.dp)) {
                    Image(
                        painter = painterResource(id = R.drawable.img_studio_hero),
                        contentDescription = "Raven AI Music Studio console",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MidnightBlack.copy(alpha = 0.25f),
                                        MidnightBlack.copy(alpha = 0.92f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AiGeneratedMediaBadge(label = "RAVEN & CO. STUDIO")
                            Text(
                                text = "$songsCount Tracks • $projectsCount Projects",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricBlue
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_launcher_art),
                                contentDescription = "Raven AI Music Studio logo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White
                                )
                                Text(
                                    text = stringResource(R.string.app_tagline),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MetallicGold
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!viewModel.backendConfigured) {
            item {
                ApiConfigurationNotice(
                    featureName = "AI Music, Image-to-Video & Lip-Sync rendering",
                    localCapabilities = "Gemini AI Lyrics, Face Scan, Waveform Audio Lab & Raven & Breeze Duet drafts are ready in Preview Mode."
                )
            }
        }

        // Featured Flagship Card: Raven & Breeze Duet Mode
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = DeepPurple.copy(alpha = 0.72f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, MetallicGold, MaterialTheme.shapes.large)
                    .clickable(onClick = onNavigateToDuetRavenBreeze)
                    .testTag("home_raven_breeze_duet_card")
            ) {
                Box {
                    Image(
                        painter = painterResource(id = R.drawable.img_duet_stage),
                        contentDescription = "Raven and Breeze duet stage",
                        contentScale = ContentScale.Crop,
                        alpha = 0.36f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(176.dp)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Groups,
                                contentDescription = null,
                                tint = MetallicGold,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = if (viewModel.backendConfigured) {
                                    "FEATURED • DUET PERFORMANCE ENGINE"
                                } else {
                                    "FEATURED • PREVIEW READY (RENDER NEEDS API)"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MetallicGold
                            )
                        }
                        Text(
                            text = "Raven & Breeze Duet Mode",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White
                        )
                        Text(
                            text = "Upload two portraits, map female & male vocal timelines, pick 6 stage styles, and render realistic lip-synced duet music videos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onNavigateToDuetRavenBreeze,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MetallicGold,
                                contentColor = MidnightBlack
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("launch_raven_breeze_button")
                        ) {
                            Text("Open Duet Studio", style = MaterialTheme.typography.labelLarge)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Quick Device Storage Upload Card
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, StudioBorder, MaterialTheme.shapes.large)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Upload from Device Storage",
                        style = MaterialTheme.typography.titleMedium,
                        color = MetallicGold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                homeAudioPicker.launch(
                                    arrayOf("audio/mpeg", "audio/wav", "audio/x-wav", "audio/mp4", "audio/aac", "audio/*")
                                )
                            },
                            enabled = !isImporting,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("home_upload_audio")
                        ) {
                            Icon(Icons.Filled.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Audio", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = {
                                homeImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            enabled = !isImporting,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("home_upload_image")
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Photo", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = {
                                homeVideoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                            },
                            enabled = !isImporting,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("home_upload_video")
                        ) {
                            Icon(Icons.Filled.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Video", maxLines = 1)
                        }
                    }
                    if (isImporting) {
                        LinearProgressIndicator(color = MetallicGold, modifier = Modifier.fillMaxWidth())
                    }
                    StatusBanner(statusMessage = libraryStatus, errorMessage = null)
                }
            }
        }

        // Large Creation Cards Grid (2-column pairs)
        item {
            Text(
                text = "Creation Suite",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HomeCreationCard(
                    title = "Create AI Song",
                    subtitle = "Vocals, duet, or instrumental",
                    icon = Icons.Filled.MusicNote,
                    accent = MetallicGold,
                    apiBadge = if (!viewModel.backendConfigured) "Needs API" else null,
                    onClick = onNavigateToCreateSong,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("card_create_ai_song")
                )
                HomeCreationCard(
                    title = "Write Lyrics",
                    subtitle = "Gemini AI songwriter & prompts",
                    icon = Icons.Filled.EditNote,
                    accent = ElectricBlue,
                    apiBadge = "Gemini Ready",
                    onClick = onNavigateToWriteLyrics,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("card_write_lyrics")
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HomeCreationCard(
                    title = "Make Photo Sing",
                    subtitle = "Audio-driven facial lip-sync",
                    icon = Icons.Filled.RecordVoiceOver,
                    accent = ElectricBlue,
                    apiBadge = if (!viewModel.backendConfigured) "Needs API" else null,
                    onClick = onNavigateToPhotoSing,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("card_make_photo_sing")
                )
                HomeCreationCard(
                    title = "Image to Video",
                    subtitle = "Cinematic camera motion",
                    icon = Icons.Filled.MovieCreation,
                    accent = MetallicGold,
                    apiBadge = if (!viewModel.backendConfigured) "Needs API" else null,
                    onClick = onNavigateToImageToVideo,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("card_image_to_video")
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HomeCreationCard(
                    title = "Create Duet",
                    subtitle = "Any two consenting singers",
                    icon = Icons.Filled.FaceRetouchingNatural,
                    accent = MetallicGold,
                    apiBadge = if (!viewModel.backendConfigured) "Draft Ready" else null,
                    onClick = onNavigateToCreateDuet,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("card_create_duet")
                )
                HomeCreationCard(
                    title = "Upload Audio",
                    subtitle = "Waveform, trim, fade & sync",
                    icon = Icons.Filled.AudioFile,
                    accent = ElectricBlue,
                    apiBadge = "Lab Ready",
                    onClick = onNavigateToUploadAudio,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("card_upload_audio")
                )
            }
        }

        // Recent Generation Jobs Strip
        if (recentJobs.isNotEmpty()) {
            item {
                Text(
                    text = "Recent Studio Jobs",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            items(recentJobs.size) { idx ->
                val job = recentJobs[idx]
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, StudioBorder, MaterialTheme.shapes.medium)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = job.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${job.jobType} • ${job.status} • Est. $${String.format("%.2f", job.estimatedCostUsd)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${job.progressPercent}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MetallicGold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeCreationCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    apiBadge: String? = null
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier
            .height(148.dp)
            .border(1.dp, StudioBorder, MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent.copy(alpha = 0.16f))
                        .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
                if (!apiBadge.isNullOrBlank()) {
                    val isNeedsApi = apiBadge.contains("Needs API")
                    Text(
                        text = apiBadge,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isNeedsApi) StudioWarning else ElectricBlue,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background((if (isNeedsApi) StudioWarning else ElectricBlue).copy(alpha = 0.14f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
