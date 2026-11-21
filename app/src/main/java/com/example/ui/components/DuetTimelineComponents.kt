package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.core.FaceAnalysisResult
import com.example.model.DuetLine
import com.example.ui.theme.DeepPurple
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.MidnightBlack
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSuccess
import com.example.ui.theme.StudioWarning

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DuetSingerPortraitPanel(
    singerIndex: Int,
    name: String,
    vocalRole: String,
    portraitUri: String,
    placeholderDrawableRes: Int,
    faceResult: FaceAnalysisResult?,
    isScanning: Boolean,
    buttonLabel: String,
    accentColor: Color,
    onNameChange: (String) -> Unit,
    onRoleChange: (String) -> Unit,
    onUploadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .border(1.dp, accentColor.copy(alpha = 0.55f), MaterialTheme.shapes.large)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Singer $singerIndex • $vocalRole",
                style = MaterialTheme.typography.labelSmall,
                color = accentColor
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(145.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MidnightBlack)
                    .border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
                    .clickable(onClick = onUploadClick),
                contentAlignment = Alignment.Center
            ) {
                if (portraitUri.isNotBlank()) {
                    AsyncImage(
                        model = portraitUri,
                        contentDescription = "$name portrait",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Image(
                        painter = painterResource(id = placeholderDrawableRes),
                        contentDescription = "$name studio placeholder",
                        contentScale = ContentScale.Crop,
                        alpha = 0.72f,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (isScanning) {
                    CircularProgressIndicator(color = accentColor, modifier = Modifier.size(28.dp))
                }
            }

            if (faceResult != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (faceResult.faceDetected) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                        contentDescription = null,
                        tint = if (faceResult.faceDetected) StudioSuccess else StudioWarning,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (faceResult.faceDetected) "Face locked (${faceResult.faceCount})" else "No face found",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (faceResult.faceDetected) StudioSuccess else StudioWarning,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("FEMALE", "MALE", "HARMONY").forEach { role ->
                    FilterChip(
                        selected = vocalRole == role,
                        onClick = { onRoleChange(role) },
                        label = { Text(role, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accentColor,
                            selectedLabelColor = MidnightBlack
                        )
                    )
                }
            }

            Button(
                onClick = onUploadClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = MidnightBlack
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("upload_singer_${singerIndex}_button")
            ) {
                Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(buttonLabel, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DuetVocalTimelineRow(
    line: DuetLine,
    singer1Name: String,
    singer2Name: String,
    onSelectSinger: (String) -> Unit,
    onLyricChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    val badgeColor = when (line.singer) {
        "RAVEN" -> MetallicGold
        "BREEZE" -> ElectricBlue
        "BOTH" -> ElectricViolet
        else -> Color(0xFF64748B)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, badgeColor.copy(alpha = 0.55f), MaterialTheme.shapes.medium)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${formatTimeMs(line.timestampMs)} - ${formatTimeMs(line.timestampMs + line.durationMs)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = badgeColor
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(badgeColor.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        val label = when (line.singer) {
                            "RAVEN" -> "$singer1Name (Female)"
                            "BREEZE" -> "$singer2Name (Male)"
                            "BOTH" -> "Both (Harmony)"
                            else -> "Instrumental"
                        }
                        Text(label, style = MaterialTheme.typography.labelSmall, color = badgeColor)
                    }
                    IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Remove vocal line",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            OutlinedTextField(
                value = line.lyric,
                onValueChange = onLyricChange,
                label = { Text("Lyric / Cue") },
                modifier = Modifier.fillMaxWidth()
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "RAVEN" to singer1Name,
                    "BREEZE" to singer2Name,
                    "BOTH" to "Both",
                    "INSTRUMENTAL" to "Instrumental"
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = line.singer == key,
                        onClick = { onSelectSinger(key) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = when (key) {
                                "RAVEN" -> MetallicGold
                                "BREEZE" -> ElectricBlue
                                "BOTH" -> DeepPurple
                                else -> Color(0xFF475569)
                            },
                            selectedLabelColor = if (key == "BOTH" || key == "INSTRUMENTAL") Color.White else MidnightBlack
                        )
                    )
                }
            }
        }
    }
}
