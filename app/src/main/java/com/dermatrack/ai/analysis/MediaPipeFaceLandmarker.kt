package com.dermatrack.ai.analysis

import android.content.Context
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker.FaceLandmarkerOptions

class MediaPipeFaceLandmarker(private val context: Context) : FaceLandmarkModel {
    private var faceLandmarker: FaceLandmarker? = null

    init {
        setupFaceLandmarker()
    }

    private fun setupFaceLandmarker() {
        val baseOptionsBuilder = BaseOptions.builder().setModelAssetPath("models/face_landmarker.task")
        val optionsBuilder = FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptionsBuilder.build())
            .setMinFaceDetectionConfidence(0.5f)
            .setMinFacePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setNumFaces(1)
            .setRunningMode(com.google.mediapipe.tasks.vision.core.RunningMode.IMAGE)

        runCatching {
            faceLandmarker = FaceLandmarker.createFromOptions(context, optionsBuilder.build())
        }
    }

    override suspend fun estimateAlignment(frame: ByteArray): Float {
        // Real landmark-based alignment scoring is not yet implemented.
        // Returning 0f (no signal) until the .task asset ships AND inference is wired —
        // CLAUDE.md forbids fabricated model outputs, so do NOT return a placeholder confidence.
        return 0f
    }
}
