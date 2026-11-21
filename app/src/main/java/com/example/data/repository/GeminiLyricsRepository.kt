package com.example.data.repository

import android.util.Log
import com.example.model.DuetLine
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.UUID

data class GeneratedLyricsPackage(
    val title: String,
    val content: String,
    val productionPrompt: String,
    val duetLines: List<DuetLine>
)

class GeminiLyricsRepository {

    private val streamingModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.8-flash")
    }

    private val structuredLyricsSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Evocative song title"),
            "fullLyrics" to Schema.string(description = "Complete song lyrics with [Verse 1], [Chorus], [Verse 2], [Bridge], [Outro] tags and singer labels"),
            "productionPrompt" to Schema.string(description = "Detailed music production prompt specifying instrumentation, tempo, vocal tone, mix texture, and arrangement"),
            "duetTimeline" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "timestampMs" to Schema.integer(description = "Start timestamp in milliseconds"),
                        "durationMs" to Schema.integer(description = "Line duration in milliseconds"),
                        "singer" to Schema.enumeration(
                            values = listOf("RAVEN", "BREEZE", "BOTH", "INSTRUMENTAL"),
                            description = "Assigned performer for this section or line"
                        ),
                        "lyric" to Schema.string(description = "Lyric line or instrumental cue")
                    )
                ),
                description = "Timestamped duet vocal assignments"
            )
        )
    )

    private val structuredModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = structuredLyricsSchema
            }
        )
    }

    fun streamLyricsDraft(
        genre: String,
        mood: String,
        topic: String,
        duetMode: Boolean,
        singer1Name: String,
        singer2Name: String
    ): Flow<String> = flow {
        val duetInstruction = if (duetMode) {
            "Write this as a rich duet arrangement assigning clear vocal parts to [$singer1Name] (Singer 1), [$singer2Name] (Singer 2), and [BOTH] for shared harmonies and choruses."
        } else {
            "Write a complete song with [Verse 1], [Chorus], [Verse 2], [Bridge], and [Outro]."
        }
        val prompt = """
            You are a Nashville and Atlanta hit songwriter and music producer for Raven AI Music Studio.
            Genre: $genre
            Mood: $mood
            Concept / Theme: $topic
            $duetInstruction
            Write deeply authentic, singable, original lyrics with strong rhythm, vivid imagery, and memorable hooks.
        """.trimIndent()

        val sb = StringBuilder()
        withTimeout(60_000L) {
            streamingModel.generateContentStream(prompt).collect { chunk ->
                val text = chunk.text.orEmpty()
                if (text.isNotEmpty()) {
                    sb.append(text)
                    emit(sb.toString())
                }
            }
        }
    }

    suspend fun generateStructuredLyrics(
        genre: String,
        mood: String,
        topic: String,
        duetMode: Boolean,
        singer1Name: String,
        singer2Name: String,
        bpm: Int = 96,
        durationMs: Long = 160_000L
    ): Result<GeneratedLyricsPackage> {
        return try {
            val prompt = """
                Compose an original $genre song with a $mood atmosphere at ~$bpm BPM (target length ~${durationMs / 1000}s).
                Theme: $topic.
                Duet mode: $duetMode (Singer 1 is $singer1Name, Singer 2 is $singer2Name).
                Include [Verse 1], [Chorus], [Verse 2], [Bridge], and [Outro] in fullLyrics.
                In duetTimeline, map sequential lyric lines and instrumental breaks across 0ms to ${durationMs}ms, using RAVEN for $singer1Name, BREEZE for $singer2Name, BOTH for shared harmonies/choruses, and INSTRUMENTAL for musical solos/intros/outros.
                Also craft a detailed productionPrompt for an AI music generator.
            """.trimIndent()

            val response = withTimeout(60_000L) {
                structuredModel.generateContent(prompt)
            }
            val rawJson = response.text ?: error("Gemini returned an empty response.")
            Result.success(parseLyricsJson(rawJson))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini lyrics generation timed out", e)
            Result.failure(Exception("Lyrics generation timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini structured lyrics failed", e)
            Result.failure(e)
        }
    }

    suspend fun rewriteAndImproveLyrics(
        existingLyrics: String,
        instruction: String,
        genre: String,
        duetMode: Boolean,
        singer1Name: String,
        singer2Name: String
    ): Result<GeneratedLyricsPackage> {
        return try {
            val prompt = """
                Rewrite and elevate these $genre lyrics according to the producer note: "$instruction".
                Duet mode: $duetMode (Singer 1 = $singer1Name, Singer 2 = $singer2Name).
                Preserve strong lines while tightening meter, rhyme, emotional arc, and chorus lift.
                Original lyrics:
                $existingLyrics
            """.trimIndent()

            val response = withTimeout(60_000L) {
                structuredModel.generateContent(prompt)
            }
            val rawJson = response.text ?: error("Gemini returned an empty response.")
            Result.success(parseLyricsJson(rawJson))
        } catch (e: TimeoutCancellationException) {
            Result.failure(Exception("Rewrite timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini rewrite failed", e)
            Result.failure(e)
        }
    }

    suspend fun analyzeDuetTimingFromLyrics(
        lyricsText: String,
        audioDurationMs: Long,
        singer1Name: String,
        singer2Name: String
    ): Result<List<DuetLine>> {
        return try {
            val safeDuration = audioDurationMs.coerceAtLeast(30_000L)
            val prompt = """
                Analyze the song lyrics below and map every line or section into a realistic duet vocal timeline spanning 0ms to ${safeDuration}ms.
                Assign RAVEN for $singer1Name's parts, BREEZE for $singer2Name's parts, BOTH for shared harmonies/choruses, and INSTRUMENTAL for intro/solo/outro cues.
                Lyrics:
                $lyricsText
            """.trimIndent()

            val response = withTimeout(60_000L) {
                structuredModel.generateContent(prompt)
            }
            val rawJson = response.text ?: error("Gemini returned an empty timeline.")
            Result.success(parseLyricsJson(rawJson).duetLines)
        } catch (e: TimeoutCancellationException) {
            Result.failure(Exception("Duet timing analysis timed out after 60 seconds."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini duet analysis failed", e)
            Result.failure(e)
        }
    }

    private fun parseLyricsJson(jsonString: String): GeneratedLyricsPackage {
        val json = JSONObject(jsonString)
        val title = json.optString("title", "Midnight Horizon")
        val content = json.optString("fullLyrics", "")
        val productionPrompt = json.optString("productionPrompt", "")
        val timelineArr = json.optJSONArray("duetTimeline")
        val duetLines = mutableListOf<DuetLine>()
        if (timelineArr != null) {
            for (i in 0 until timelineArr.length()) {
                val obj = timelineArr.optJSONObject(i) ?: continue
                val singerRaw = obj.optString("singer", "RAVEN")
                val singer = if (singerRaw in listOf("RAVEN", "BREEZE", "BOTH", "INSTRUMENTAL")) singerRaw else "RAVEN"
                duetLines.add(
                    DuetLine(
                        id = UUID.randomUUID().toString(),
                        timestampMs = obj.optLong("timestampMs", i * 4000L),
                        durationMs = obj.optLong("durationMs", 4000L).coerceAtLeast(1000L),
                        singer = singer,
                        lyric = obj.optString("lyric", "")
                    )
                )
            }
        }
        return GeneratedLyricsPackage(
            title = title,
            content = content,
            productionPrompt = productionPrompt,
            duetLines = duetLines
        )
    }

    private companion object {
        const val TAG = "GeminiLyricsRepo"
    }
}
