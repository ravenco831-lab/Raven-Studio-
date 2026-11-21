package com.example.core

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

class MediaPlaybackState(
    val player: ExoPlayer
) {
    var isPlaying by mutableStateOf(false)
        internal set
    var currentPositionMs by mutableLongStateOf(0L)
        internal set
    var durationMs by mutableLongStateOf(0L)
        internal set
    var currentUri by mutableStateOf<String?>(null)
        internal set

    fun loadAndPlay(uriString: String) {
        val uri = Uri.parse(uriString)
        if (currentUri != uriString) {
            currentUri = uriString
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
        }
        player.play()
    }

    fun togglePlayPause(uriString: String? = null) {
        if (uriString != null && currentUri != uriString) {
            loadAndPlay(uriString)
            return
        }
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(0L)
            }
            player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0L))
        currentPositionMs = player.currentPosition
    }

    fun setVolume(volume: Float) {
        player.volume = volume.coerceIn(0f, 1f)
    }

    fun stop() {
        player.stop()
        isPlaying = false
    }
}

@Composable
fun rememberMediaPlaybackState(): MediaPlaybackState {
    val context: Context = LocalContext.current
    val player = remember(context) {
        ExoPlayer.Builder(context).build()
    }
    val state = remember(player) { MediaPlaybackState(player) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                state.isPlaying = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val dur = player.duration
                    if (dur > 0) state.durationMs = dur
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(state.isPlaying) {
        while (state.isPlaying) {
            state.currentPositionMs = player.currentPosition.coerceAtLeast(0L)
            val dur = player.duration
            if (dur > 0) state.durationMs = dur
            delay(150L)
        }
    }

    return state
}
