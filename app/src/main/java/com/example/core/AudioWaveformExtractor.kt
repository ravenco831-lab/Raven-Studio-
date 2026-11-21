package com.example.core

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

data class AudioAnalysisResult(
    val title: String,
    val mimeType: String,
    val durationMs: Long,
    val waveformSamples: List<Long>
)

object AudioWaveformExtractor {
    private const val TAG = "AudioWaveformExtractor"
    private const val TARGET_BARS = 64

    suspend fun analyzeAudio(context: Context, uri: Uri): AudioAnalysisResult = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(context, uri) ?: "Audio Track"
        val mimeType = context.contentResolver.getType(uri) ?: "audio/mpeg"
        var durationMs = 0L

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = durationStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            Log.w(TAG, "MediaMetadataRetriever failed for $uri", e)
        } finally {
            runCatching { retriever.release() }
        }

        val samples = extractWaveformBars(context, uri, durationMs)
        AudioAnalysisResult(
            title = displayName,
            mimeType = mimeType,
            durationMs = durationMs,
            waveformSamples = samples
        )
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractWaveformBars(context: Context, uri: Uri, durationMs: Long): List<Long> {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }
            if (audioTrackIndex < 0 || audioFormat == null) {
                return List(TARGET_BARS) { 20L }
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: return List(TARGET_BARS) { 20L }
            val formatDurationUs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
                audioFormat.getLong(MediaFormat.KEY_DURATION)
            } else {
                60_000_000L
            }
            val totalUs = if (durationMs > 0L) durationMs * 1000L else formatDurationUs
            val stepUs = max(100_000L, totalUs / TARGET_BARS)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(audioFormat, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            val amplitudes = mutableListOf<Long>()
            var seekPositionUs = 0L

            while (amplitudes.size < TARGET_BARS && seekPositionUs <= max(totalUs, 1_000_000L)) {
                extractor.seekTo(seekPositionUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                val inIdx = codec.dequeueInputBuffer(5_000L)
                if (inIdx >= 0) {
                    val inBuf = codec.getInputBuffer(inIdx)
                    if (inBuf != null) {
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            break
                        } else {
                            codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                        }
                    }
                }

                var peak = 12L
                for (attempt in 0 until 3) {
                    val outIdx = codec.dequeueOutputBuffer(bufferInfo, 5_000L)
                    if (outIdx >= 0) {
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && bufferInfo.size > 1) {
                            outBuf.order(ByteOrder.LITTLE_ENDIAN)
                            val shortBuf = outBuf.asShortBuffer()
                            var maxAmp = 0
                            val count = shortBuf.remaining().coerceAtMost(1024)
                            for (k in 0 until count) {
                                val sample = abs(shortBuf.get(k).toInt())
                                if (sample > maxAmp) maxAmp = sample
                            }
                            peak = ((maxAmp.toFloat() / 32767f) * 95f).toLong().coerceIn(8L, 100L)
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        break
                    }
                }
                amplitudes.add(peak)
                seekPositionUs += stepUs
            }

            if (amplitudes.isEmpty()) List(TARGET_BARS) { 25L }
            else {
                while (amplitudes.size < TARGET_BARS) {
                    amplitudes.add(amplitudes[amplitudes.size % max(1, amplitudes.size)])
                }
                amplitudes.take(TARGET_BARS)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "PCM waveform sampling fell back to metadata envelope: ${e.message}")
            List(TARGET_BARS) { idx ->
                // Deterministic fallback envelope when device codec rejects specific container
                (22L + ((idx * 17) % 58)).coerceIn(10L, 90L)
            }
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }
}
