package com.example.core

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class FaceAnalysisResult(
    val faceDetected: Boolean,
    val faceCount: Int,
    val statusMessage: String
)

object FaceDetectorHelper {
    private const val TAG = "FaceDetectorHelper"

    private val options by lazy {
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
    }

    suspend fun detectFaces(context: Context, imageUri: Uri): FaceAnalysisResult = withContext(Dispatchers.IO) {
        try {
            val image = InputImage.fromFilePath(context, imageUri)
            val detector = FaceDetection.getClient(options)
            val faces = suspendCancellableCoroutine { continuation ->
                detector.process(image)
                    .addOnSuccessListener { result ->
                        if (continuation.isActive) continuation.resume(result)
                    }
                    .addOnFailureListener { error ->
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                continuation.invokeOnCancellation {
                    runCatching { detector.close() }
                }
            }
            runCatching { detector.close() }

            when (faces.size) {
                0 -> FaceAnalysisResult(
                    faceDetected = false,
                    faceCount = 0,
                    statusMessage = "No clear face detected. Choose a front-facing portrait with good lighting for accurate lip-sync."
                )
                1 -> FaceAnalysisResult(
                    faceDetected = true,
                    faceCount = 1,
                    statusMessage = "Face & mouth landmarks locked (1 portrait detected). Ready for lip-sync."
                )
                else -> FaceAnalysisResult(
                    faceDetected = true,
                    faceCount = faces.size,
                    statusMessage = "${faces.size} faces detected. Primary centered face will be targeted; single-person portraits give cleaner lip-sync."
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Face detection failed", e)
            FaceAnalysisResult(
                faceDetected = false,
                faceCount = 0,
                statusMessage = "Face scan failed: ${e.localizedMessage ?: "Unsupported image format"}"
            )
        }
    }
}
