package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.LyricSheet
import com.example.model.MediaAsset
import com.example.model.SongRecord
import com.example.model.StudioProject
import com.example.model.StudioUiState
import com.example.ui.components.AiGeneratedMediaBadge
import com.example.ui.components.StatusBanner
import com.example.ui.components.StudioTopHeader
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.MidnightBlack
import com.example.ui.theme.StudioBorder
import com.example.viewmodel.StudioViewModel

private val LIBRARY_TABS = listOf(
    "My Songs",
    "My Lyrics",
    "My Images",
    "My Videos",
    "My Audio",
    "My Projects"
)

@Composable
fun LibraryScreen(
    viewModel: StudioViewModel,
    onReopenProject: (StudioProject) -> Unit,
    onReopenLyrics: (LyricSheet) -> Unit,
    onReopenAudio: (MediaAsset) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf("My Projects") }
    var renameTarget by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var renameInput by remember { mutableStateOf("") }

    val songsState by viewModel.songsState.collectAsStateWithLifecycle()
    val lyricsState by viewModel.lyricsState.collectAsStateWithLifecycle()
    val assetsState by viewModel.mediaAssetsState.collectAsStateWithLifecycle()
    val projectsState by viewModel.projectsState.collectAsStateWithLifecycle()
    val storageStats by viewModel.storageStats.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImportingMedia.collectAsStateWithLifecycle()
    val libraryStatus by viewModel.libraryStatusMessage.collectAsStateWithLifecycle()

    val songs = (songsState as? StudioUiState.Success)?.data.orEmpty()
    val lyrics = (lyricsState as? StudioUiState.Success)?.data.orEmpty()
    val assets = (assetsState as? StudioUiState.Success)?.data.orEmpty()
    val projects = (projectsState as? StudioUiState.Success)?.data.orEmpty()

    LaunchedEffect(Unit) {
        viewModel.refreshStorageStats()
    }

    val audioUploadPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedTab = "My Audio"
            viewModel.importDeviceMedia(context, uri, "AUDIO")
        }
    }

    val imageUploadPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedTab = "My Images"
            viewModel.importDeviceMedia(context, uri, "IMAGE")
        }
    }

    val videoUploadPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedTab = "My Videos"
            viewModel.importDeviceMedia(context, uri, "VIDEO")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBlack)
    ) {
        StudioTopHeader(
            title = "My Studio Library",
            subtitle = "On-device studio vault • ${storageStats.fileCount} files (${storageStats.formattedSize})"
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .border(1.dp, StudioBorder, MaterialTheme.shapes.medium)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Upload from Device Storage",
                    style = MaterialTheme.typography.titleSmall,
                    color = MetallicGold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            audioUploadPicker.launch(
                                arrayOf("audio/mpeg", "audio/wav", "audio/x-wav", "audio/mp4", "audio/aac", "audio/*")
                            )
                        },
                        enabled = !isImporting,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("library_upload_audio")
                    ) {
                        Icon(Icons.Filled.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Audio", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = {
                            imageUploadPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        enabled = !isImporting,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("library_upload_image")
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Photo", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = {
                            videoUploadPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                        },
                        enabled = !isImporting,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("library_upload_video")
                    ) {
                        Icon(Icons.Filled.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Video", maxLines = 1)
                    }
                }
                if (isImporting) {
                    LinearProgressIndicator(
                        color = MetallicGold,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                StatusBanner(
                    statusMessage = libraryStatus,
                    errorMessage = null
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LIBRARY_TABS.forEach { tab ->
                FilterChip(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    label = { Text(tab) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MetallicGold,
                        selectedLabelColor = MidnightBlack
                    ),
                    modifier = Modifier.testTag("library_tab_$tab")
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (selectedTab) {
                "My Songs" -> {
                    if (songs.isEmpty()) item { EmptyLibraryTip("No songs yet. Create a song in AI Music Generator.") }
                    items(songs, key = { it.id }) { song: SongRecord ->
                        val source = song.wavUrl ?: song.audioUrl
                        val mime = if (source?.endsWith(".wav", ignoreCase = true) == true) "audio/wav" else "audio/mpeg"
                        LibraryEntryCard(
                            title = song.title,
                            subtitle = "${song.genre} • ${song.vocalType} • ${song.bpm} BPM • ${song.status}",
                            aiGenerated = song.aiGenerated,
                            onSaveToDevice = if (!source.isNullOrBlank()) {
                                { viewModel.exportSongToDevice(song) }
                            } else null,
                            onShare = if (!source.isNullOrBlank()) {
                                { viewModel.shareUriOrUrl(context, source, song.title, mime) }
                            } else null,
                            onRename = {
                                renameTarget = Triple("songs", song.id, song.title)
                                renameInput = song.title
                            },
                            onDelete = { viewModel.deleteLibraryItem("songs", song.id) }
                        )
                    }
                }
                "My Lyrics" -> {
                    if (lyrics.isEmpty()) item { EmptyLibraryTip("No saved lyrics yet. Write with Gemini in AI Lyrics Studio.") }
                    items(lyrics, key = { it.id }) { sheet ->
                        LibraryEntryCard(
                            title = sheet.title,
                            subtitle = "${sheet.genre} • ${if (sheet.duetMode) "Duet (${sheet.singer1Name} & ${sheet.singer2Name})" else "Solo"}",
                            aiGenerated = sheet.aiGenerated,
                            onOpen = { onReopenLyrics(sheet) },
                            onSaveToDevice = { viewModel.exportLyricsToDevice(sheet) },
                            onShare = { viewModel.shareLyricsSheet(context, sheet) },
                            onRename = {
                                renameTarget = Triple("lyrics", sheet.id, sheet.title)
                                renameInput = sheet.title
                            },
                            onDelete = { viewModel.deleteLibraryItem("lyrics", sheet.id) }
                        )
                    }
                }
                "My Images" -> {
                    val images = assets.filter { it.mediaType == "IMAGE" }
                    if (images.isEmpty()) item { EmptyLibraryTip("No uploaded portraits or artwork yet. Tap Photo above to upload from your device.") }
                    items(images, key = { it.id }) { img ->
                        val hasSource = !img.localUri.isNullOrBlank() || !img.remoteUrl.isNullOrBlank()
                        val faceLabel = if (img.faceDetected) "Face Verified (${img.faceCount})" else img.mimeType
                        val sizeLabel = formatFileSizeLabel(img.fileSizeBytes)
                        LibraryEntryCard(
                            title = img.title,
                            subtitle = if (sizeLabel != null) "$faceLabel • $sizeLabel" else faceLabel,
                            aiGenerated = img.aiGenerated,
                            onSaveToDevice = if (hasSource) {
                                { viewModel.exportMediaAssetToDevice(img) }
                            } else null,
                            onShare = if (hasSource) {
                                { viewModel.shareMediaAsset(context, img) }
                            } else null,
                            onRename = {
                                renameTarget = Triple("mediaAssets", img.id, img.title)
                                renameInput = img.title
                            },
                            onDelete = { viewModel.deleteLibraryItem("mediaAssets", img.id) }
                        )
                    }
                }
                "My Videos" -> {
                    val videos = assets.filter { it.mediaType == "VIDEO" }
                    if (videos.isEmpty()) item { EmptyLibraryTip("No uploaded or rendered MP4 videos yet. Tap Video above to upload from your device.") }
                    items(videos, key = { it.id }) { vid ->
                        val hasSource = !vid.localUri.isNullOrBlank() || !vid.remoteUrl.isNullOrBlank()
                        val sizeLabel = formatFileSizeLabel(vid.fileSizeBytes)
                        val durLabel = if (vid.durationMs > 0L) "${vid.durationMs / 1000}s • " else ""
                        LibraryEntryCard(
                            title = vid.title,
                            subtitle = "$durLabel${vid.mimeType}${if (sizeLabel != null) " • $sizeLabel" else ""}",
                            aiGenerated = vid.aiGenerated,
                            onSaveToDevice = if (hasSource) {
                                { viewModel.exportMediaAssetToDevice(vid) }
                            } else null,
                            onShare = if (hasSource) {
                                { viewModel.shareMediaAsset(context, vid) }
                            } else null,
                            onRename = {
                                renameTarget = Triple("mediaAssets", vid.id, vid.title)
                                renameInput = vid.title
                            },
                            onDelete = { viewModel.deleteLibraryItem("mediaAssets", vid.id) }
                        )
                    }
                }
                "My Audio" -> {
                    val audios = assets.filter { it.mediaType == "AUDIO" }
                    if (audios.isEmpty()) item { EmptyLibraryTip("No uploaded audio tracks yet. Tap Audio above to import MP3/WAV/M4A/AAC.") }
                    items(audios, key = { it.id }) { audio ->
                        val hasSource = !audio.localUri.isNullOrBlank() || !audio.remoteUrl.isNullOrBlank()
                        val sizeLabel = formatFileSizeLabel(audio.fileSizeBytes)
                        LibraryEntryCard(
                            title = audio.title,
                            subtitle = "${audio.durationMs / 1000}s • Vol ${(audio.volume * 100).toInt()}% • ${audio.mimeType}${if (sizeLabel != null) " • $sizeLabel" else ""}",
                            aiGenerated = audio.aiGenerated,
                            onOpen = { onReopenAudio(audio) },
                            onSaveToDevice = if (hasSource) {
                                { viewModel.exportMediaAssetToDevice(audio) }
                            } else null,
                            onShare = if (hasSource) {
                                { viewModel.shareMediaAsset(context, audio) }
                            } else null,
                            onRename = {
                                renameTarget = Triple("mediaAssets", audio.id, audio.title)
                                renameInput = audio.title
                            },
                            onDelete = { viewModel.deleteLibraryItem("mediaAssets", audio.id) }
                        )
                    }
                }
                "My Projects" -> {
                    if (projects.isEmpty()) item { EmptyLibraryTip("No saved projects yet. Start a Raven & Breeze Duet or Timeline Cut.") }
                    items(projects, key = { it.id }) { proj ->
                        val outUrl = proj.outputVideoUrl
                        LibraryEntryCard(
                            title = proj.title,
                            subtitle = "${proj.projectType} • ${proj.aspectRatio} • ${proj.resolution} • ${proj.status}",
                            aiGenerated = proj.aiGenerated,
                            onOpen = { onReopenProject(proj) },
                            onSaveToDevice = if (!outUrl.isNullOrBlank()) {
                                {
                                    viewModel.exportMediaAssetToDevice(
                                        MediaAsset(
                                            mediaType = "VIDEO",
                                            title = proj.title,
                                            mimeType = "video/mp4",
                                            remoteUrl = outUrl,
                                            localUri = outUrl
                                        )
                                    )
                                }
                            } else null,
                            onShare = if (!outUrl.isNullOrBlank()) {
                                { viewModel.shareUriOrUrl(context, outUrl, proj.title, "video/mp4") }
                            } else null,
                            onRename = {
                                renameTarget = Triple("projects", proj.id, proj.title)
                                renameInput = proj.title
                            },
                            onDelete = { viewModel.deleteLibraryItem("projects", proj.id) }
                        )
                    }
                }
            }
        }
    }

    renameTarget?.let { (subcollection, id, _) ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Item") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("New Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (renameInput.isNotBlank()) {
                        viewModel.renameLibraryItem(subcollection, id, renameInput)
                    }
                    renameTarget = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun formatFileSizeLabel(bytes: Long): String? = when {
    bytes <= 0L -> null
    bytes >= 1024L * 1024L -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "${bytes / 1024L} KB"
    else -> "$bytes B"
}

@Composable
private fun EmptyLibraryTip(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, StudioBorder, MaterialTheme.shapes.medium)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(20.dp)
        )
    }
}

@Composable
private fun LibraryEntryCard(
    title: String,
    subtitle: String,
    aiGenerated: Boolean,
    onOpen: (() -> Unit)? = null,
    onSaveToDevice: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = ElectricBlue)
                }
                if (aiGenerated) {
                    AiGeneratedMediaBadge()
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onOpen != null) {
                    TextButton(onClick = onOpen) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reopen")
                    }
                }
                if (onSaveToDevice != null) {
                    IconButton(onClick = onSaveToDevice) {
                        Icon(Icons.Filled.Download, contentDescription = "Save to device", tint = ElectricBlue)
                    }
                }
                if (onShare != null) {
                    IconButton(onClick = onShare) {
                        Icon(Icons.Filled.Share, contentDescription = "Share", tint = ElectricBlue)
                    }
                }
                IconButton(onClick = onRename) {
                    Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = "Rename", tint = MetallicGold)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
