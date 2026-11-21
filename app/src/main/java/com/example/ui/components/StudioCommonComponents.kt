package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.core.MediaPlaybackState
import com.example.ui.theme.DeepPurple
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.MidnightBlack
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioError
import com.example.ui.theme.StudioSuccess
import com.example.ui.theme.StudioWarning
import java.util.Locale

fun formatTimeMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}

@Composable
fun StudioTopHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Navigate back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            Column(modifier = Modifier.padding(start = if (onBack != null) 4.dp else 0.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MetallicGold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun AudioWaveformCanvas(
    samples: List<Long>,
    progressFraction: Float,
    trimStartFraction: Float = 0f,
    trimEndFraction: Float = 1f,
    splitFraction: Float? = null,
    onSeekFraction: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val displayBars = if (samples.isNotEmpty()) samples else List(48) { idx ->
        (24L + ((idx * 23) % 62)).coerceIn(12L, 95L)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(78.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MidnightBlack)
            .border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
            .pointerInput(onSeekFraction) {
                if (onSeekFraction != null) {
                    detectTapGestures { offset ->
                        val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onSeekFraction(frac)
                    }
                }
            }
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        val barCount = displayBars.size
        val stepX = size.width / barCount.toFloat()
        val barWidth = (stepX * 0.56f).coerceAtLeast(3f)
        val centerY = size.height / 2f

        // Shaded trim window
        val trimLeft = trimStartFraction.coerceIn(0f, 1f) * size.width
        val trimRight = trimEndFraction.coerceIn(trimStartFraction, 1f) * size.width
        drawRect(
            color = DeepPurple.copy(alpha = 0.28f),
            topLeft = Offset(trimLeft, 0f),
            size = Size((trimRight - trimLeft).coerceAtLeast(0f), size.height)
        )

        displayBars.forEachIndexed { index, rawAmp ->
            val norm = (rawAmp.coerceIn(5L, 100L) / 100f)
            val barHeight = (size.height * 0.85f * norm).coerceAtLeast(6f)
            val x = index * stepX + (stepX - barWidth) / 2f
            val barPos = index.toFloat() / barCount.toFloat()

            val inTrim = barPos in trimStartFraction..trimEndFraction
            val barColor = when {
                !inTrim -> Color(0xFF343746)
                barPos <= progressFraction -> MetallicGold
                else -> ElectricBlue.copy(alpha = 0.72f)
            }

            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, centerY - barHeight / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }

        // Split marker
        if (splitFraction != null && splitFraction in 0.01f..0.99f) {
            val sx = splitFraction * size.width
            drawLine(
                color = StudioWarning,
                start = Offset(sx, 0f),
                end = Offset(sx, size.height),
                strokeWidth = 3f
            )
        }

        // Playhead line
        val px = progressFraction.coerceIn(0f, 1f) * size.width
        drawLine(
            color = Color.White,
            start = Offset(px, 0f),
            end = Offset(px, size.height),
            strokeWidth = 3f
        )
    }
}

@Composable
fun Media3AudioPlayerBar(
    title: String,
    audioUri: String,
    playbackState: MediaPlaybackState,
    waveformSamples: List<Long> = emptyList(),
    durationHintMs: Long = 0L,
    modifier: Modifier = Modifier
) {
    val isThisLoaded = playbackState.currentUri == audioUri && audioUri.isNotBlank()
    val totalMs = if (isThisLoaded && playbackState.durationMs > 0L) playbackState.durationMs else durationHintMs
    val currentMs = if (isThisLoaded) playbackState.currentPositionMs else 0L
    val progress = if (totalMs > 0L) (currentMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f) else 0f

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, StudioBorder, MaterialTheme.shapes.medium)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (audioUri.isNotBlank()) MetallicGold else MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                        .clickable(enabled = audioUri.isNotBlank()) {
                            playbackState.togglePlayPause(audioUri)
                        }
                        .testTag("audio_play_pause_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isThisLoaded && playbackState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isThisLoaded && playbackState.isPlaying) "Pause audio" else "Play audio",
                        tint = if (audioUri.isNotBlank()) MidnightBlack else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title.ifBlank { "No Audio Track Loaded" },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${formatTimeMs(currentMs)} / ${formatTimeMs(totalMs)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricBlue
                    )
                }
            }

            AudioWaveformCanvas(
                samples = waveformSamples,
                progressFraction = progress,
                onSeekFraction = { frac ->
                    if (audioUri.isNotBlank() && totalMs > 0L) {
                        if (!isThisLoaded) playbackState.loadAndPlay(audioUri)
                        playbackState.seekTo((frac * totalMs).toLong())
                    }
                }
            )

            if (totalMs > 0L && audioUri.isNotBlank()) {
                Slider(
                    value = progress,
                    onValueChange = { frac ->
                        if (!isThisLoaded) playbackState.loadAndPlay(audioUri)
                        playbackState.seekTo((frac * totalMs).toLong())
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = MetallicGold,
                        activeTrackColor = MetallicGold,
                        inactiveTrackColor = StudioBorder
                    )
                )
            }
        }
    }
}

@Composable
fun Media3VideoPreview(
    videoUrl: String,
    aspectRatio: String = "16:9",
    playbackState: MediaPlaybackState,
    modifier: Modifier = Modifier
) {
    val ratioFloat = when (aspectRatio) {
        "9:16" -> 9f / 16f
        "1:1" -> 1f
        else -> 16f / 9f
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(ratioFloat.coerceIn(0.56f, 1.8f))
            .clip(MaterialTheme.shapes.medium)
            .background(MidnightBlack)
            .border(1.dp, StudioBorder, MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center
    ) {
        if (videoUrl.isBlank()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MetallicGold,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = "Video Preview ($aspectRatio)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Rendered MP4 videos play here via Media3 ExoPlayer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = playbackState.player
                        useController = true
                    }
                },
                update = { view ->
                    if (playbackState.currentUri != videoUrl) {
                        playbackState.player.setMediaItem(MediaItem.fromUri(videoUrl))
                        playbackState.player.prepare()
                    }
                    view.player = playbackState.player
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun AiGeneratedMediaBadge(
    label: String = "AI Generated Media",
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(DeepPurple.copy(alpha = 0.85f))
            .border(1.dp, ElectricBlue.copy(alpha = 0.55f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = MetallicGold,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun ApiConfigurationNotice(
    featureName: String,
    localCapabilities: String = "Drafting, Gemini AI, on-device preview & Cloud Firestore saving are active now.",
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF231D15)),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, StudioWarning.copy(alpha = 0.55f), MaterialTheme.shapes.medium)
            .testTag("api_config_notice")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = "API configuration needed",
                tint = StudioWarning,
                modifier = Modifier.size(20.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "Needs API Configuration • Preview Mode Active",
                    style = MaterialTheme.typography.labelLarge,
                    color = StudioWarning
                )
                Text(
                    text = "$featureName requires the /backend service (STUDIO_BACKEND_BASE_URL + STUDIO_BACKEND_API_KEY) for live rendering. $localCapabilities",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SetupRequiredBanner(
    message: String,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2214)),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MetallicGold.copy(alpha = 0.6f), MaterialTheme.shapes.medium)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = "API configuration required",
                tint = MetallicGold,
                modifier = Modifier.size(22.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Backend API Configuration Required",
                    style = MaterialTheme.typography.titleSmall,
                    color = MetallicGold
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun StatusBanner(
    statusMessage: String? = null,
    errorMessage: String? = null,
    progressPercent: Int? = null
) {
    if (progressPercent != null && progressPercent in 1..99) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LinearProgressIndicator(
                progress = { progressPercent / 100f },
                color = MetallicGold,
                trackColor = StudioBorder,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
            Text(
                text = "Processing: $progressPercent%",
                style = MaterialTheme.typography.labelMedium,
                color = ElectricBlue
            )
        }
    }
    if (!errorMessage.isNullOrBlank()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF321618)),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, StudioError.copy(alpha = 0.6f), MaterialTheme.shapes.small)
        ) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = StudioError,
                modifier = Modifier.padding(12.dp)
            )
        }
    } else if (!statusMessage.isNullOrBlank()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF132924)),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, StudioSuccess.copy(alpha = 0.5f), MaterialTheme.shapes.small)
        ) {
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = StudioSuccess,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
fun ConsentConfirmationCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    text: String = "I confirm I have permission to animate the uploaded portrait(s) and use the selected audio, and I will not impersonate anyone without authorization."
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (checked) StudioSuccess.copy(alpha = 0.6f) else MetallicGold.copy(alpha = 0.4f),
                MaterialTheme.shapes.medium
            )
            .clickable { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = MetallicGold,
                    checkmarkColor = MidnightBlack
                ),
                modifier = Modifier.testTag("consent_checkbox")
            )
            Icon(
                imageVector = Icons.Filled.VerifiedUser,
                contentDescription = null,
                tint = if (checked) StudioSuccess else MetallicGold,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
