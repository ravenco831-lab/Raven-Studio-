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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.StudioUiState
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
fun ProfileScreen(
    viewModel: StudioViewModel
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val jobsState by viewModel.jobsState.collectAsStateWithLifecycle()
    val storageStats by viewModel.storageStats.collectAsStateWithLifecycle()
    val libraryStatus by viewModel.libraryStatusMessage.collectAsStateWithLifecycle()
    val backendHealth by viewModel.backendHealth.collectAsStateWithLifecycle()
    val isCheckingBackend by viewModel.isCheckingBackend.collectAsStateWithLifecycle()
    val jobs = (jobsState as? StudioUiState.Success)?.data.orEmpty()
    val totalEstimatedCost = jobs.sumOf { it.estimatedCostUsd }

    LaunchedEffect(Unit) {
        viewModel.refreshStorageStats()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBlack)
    ) {
        StudioTopHeader(
            title = "Studio Settings & Storage",
            subtitle = "On-device vault, providers & export defaults"
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
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
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Filled.FolderCopy, contentDescription = null, tint = MetallicGold)
                            Column {
                                Text("Local Studio & Device Storage", style = MaterialTheme.typography.titleLarge)
                                Text(
                                    text = "${storageStats.fileCount} local media file(s) • ${storageStats.formattedSize}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ElectricBlue
                                )
                            }
                        }
                        Text(
                            text = "Songs, lyrics, imported portraits, audio tracks, and studio projects are stored privately on this device with zero login required.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        StatusBanner(
                            statusMessage = libraryStatus,
                            errorMessage = null
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        OutlinedButton(
                            onClick = { viewModel.cleanupUnusedLocalStorage() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MetallicGold),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("cleanup_storage_button")
                        ) {
                            Icon(Icons.Filled.CleaningServices, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Clean Unused Local Media Files")
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
                        Text("Service & Provider Status", style = MaterialTheme.typography.titleMedium, color = MetallicGold)
                        ProviderStatusRow("Local Room Database & Device Storage", true, "Active on-device • no sign-in required")
                        ProviderStatusRow("Gemini AI Lyrics Studio (Firebase AI Logic)", true, "App Check verified")
                        ProviderStatusRow("ML Kit Face Detection & Waveform Decoder", true, "On-device ready")
                        ProviderStatusRow(
                            "External Music / Lip-Sync / FFmpeg Backend",
                            viewModel.backendConfigured,
                            if (viewModel.backendConfigured) {
                                "Secrets configured — tap below to verify /health"
                            } else {
                                "Preview Mode — Optional (deploy /backend on Render + add Secrets)"
                            }
                        )
                        if (viewModel.backendConfigured) {
                            OutlinedButton(
                                onClick = { viewModel.testBackendConnection() },
                                enabled = !isCheckingBackend,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricBlue),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("test_backend_health_button")
                            ) {
                                Icon(Icons.Filled.CloudDone, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (isCheckingBackend) "Pinging /health (waking Render)..." else "Test Render Backend (/health)")
                            }
                            if (isCheckingBackend) {
                                LinearProgressIndicator(color = ElectricBlue, modifier = Modifier.fillMaxWidth())
                            }
                            backendHealth?.let { health ->
                                Text(
                                    text = health.summaryMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (health.reachable) StudioSuccess else StudioWarning
                                )
                            }
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
                        Text("Studio Export Defaults", style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("9:16", "16:9", "1:1").forEach { ratio ->
                                FilterChip(
                                    selected = settings.defaultAspectRatio == ratio,
                                    onClick = { viewModel.updateStudioSetting(aspectRatio = ratio) },
                                    label = { Text(ratio) }
                                )
                            }
                            listOf("720p", "1080p").forEach { res ->
                                FilterChip(
                                    selected = settings.defaultResolution == res,
                                    onClick = { viewModel.updateStudioSetting(resolution = res) },
                                    label = { Text(res) }
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Default Raven & Co. Watermark", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = settings.defaultWatermark,
                                onCheckedChange = { w -> viewModel.updateStudioSetting(watermark = w) }
                            )
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
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = MetallicGold)
                        Column {
                            Text("AI Safety & Anti-Impersonation", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "All AI-generated songs and animations carry AI Generated Media labeling and require explicit portrait & audio consent.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Job History & Usage Costs", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Est. Total: $${String.format("%.2f", totalEstimatedCost)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MetallicGold
                    )
                }
            }

            items(jobs, key = { it.id }) { job ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, StudioBorder, MaterialTheme.shapes.small)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(job.title, style = MaterialTheme.typography.titleSmall)
                            Text("$${String.format("%.2f", job.estimatedCostUsd)}", style = MaterialTheme.typography.labelMedium, color = MetallicGold)
                        }
                        Text(
                            text = "${job.jobType} • ${job.status} (${job.progressPercent}%)",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricBlue
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderStatusRow(label: String, ready: Boolean, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (ready) Icons.Filled.CheckCircle else Icons.Filled.Info,
            contentDescription = null,
            tint = if (ready) StudioSuccess else StudioWarning
        )
        Column {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = if (ready) StudioSuccess else StudioWarning)
        }
    }
}
