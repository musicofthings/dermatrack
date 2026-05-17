package com.dermatrack.ai.capture

import android.graphics.RectF
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.dermatrack.ai.ui.CaptureWorkflowStage
import java.util.concurrent.atomic.AtomicBoolean

class FaceDetectionFrameAnalyzer(
    private val stage: () -> CaptureWorkflowStage = { CaptureWorkflowStage.Setup },
    private val onFaceTracking: (FaceTrackingState) -> Unit,
) : ImageAnalysis.Analyzer {
    private val detector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.18f)
            .enableTracking()
            .build(),
    )
    private val frameInFlight = AtomicBoolean(false)
    private var lastAnalyzedAt = 0L

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastAnalyzedAt < 250L || !frameInFlight.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        lastAnalyzedAt = now

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            frameInFlight.set(false)
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                onFaceTracking(faces.toFaceTrackingState(inputImage.width, inputImage.height, stage()))
            }
            .addOnFailureListener {
                onFaceTracking(FaceTrackingState(guidance = listOf("Face detector warming up. Hold steady.")))
            }
            .addOnCompleteListener {
                frameInFlight.set(false)
                imageProxy.close()
            }
    }
}

private fun List<Face>.toFaceTrackingState(imageWidth: Int, imageHeight: Int, stage: CaptureWorkflowStage): FaceTrackingState {
    val face = maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
        ?: return FaceTrackingState(guidance = listOf("No face detected. Move into the frame."))

    val bounds = face.boundingBox
    val normalizedBounds = RectF(
        1f - (bounds.right.toFloat() / imageWidth.toFloat()),
        bounds.top.toFloat() / imageHeight.toFloat(),
        1f - (bounds.left.toFloat() / imageWidth.toFloat()),
        bounds.bottom.toFloat() / imageHeight.toFloat(),
    )
    val contourLines = face.allContours.map { contour ->
        contour.points.map { point ->
            NormalizedPoint(
                x = 1f - (point.x / imageWidth.toFloat()),
                y = point.y / imageHeight.toFloat(),
            )
        }
    }.filter { it.size > 1 }
    val faceOutline = face.getContour(FaceContour.FACE)?.points.orEmpty().map { point ->
        NormalizedPoint(
            x = 1f - (point.x / imageWidth.toFloat()),
            y = point.y / imageHeight.toFloat(),
        )
    }
    val meshPoints = contourLines.flatten()

    return FaceTrackingState(
        detected = true,
        faceBounds = normalizedBounds,
        meshPoints = meshPoints,
        contourLines = contourLines,
        faceOutline = faceOutline,
        sourceAspectRatio = imageWidth.toFloat() / imageHeight.toFloat(),
        yawDegrees = face.headEulerAngleY,
        rollDegrees = face.headEulerAngleZ,
        leftEyeOpenProbability = face.leftEyeOpenProbability,
        rightEyeOpenProbability = face.rightEyeOpenProbability,
        guidance = face.guidance(meshPoints, stage),
    )
}

private fun Face.guidance(meshPoints: List<NormalizedPoint>, stage: CaptureWorkflowStage): List<String> {
    val guidance = mutableListOf<String>()
    val isFrontalStage = stage == CaptureWorkflowStage.MeshLock || 
                         stage == CaptureWorkflowStage.FrontTopDown || 
                         stage == CaptureWorkflowStage.FrontLeftRight

    if (kotlin.math.abs(headEulerAngleZ) > 8f) guidance += "Sit straight and keep your head level."
    
    if (isFrontalStage) {
        if (kotlin.math.abs(headEulerAngleY) > 10f) guidance += "Look straight at the camera."
    } else {
        // Profile guidance
        val isLeft = stage == CaptureWorkflowStage.TurnLeftPrompt || stage == CaptureWorkflowStage.LeftTopDown
        val isRight = stage == CaptureWorkflowStage.TurnRightPrompt || stage == CaptureWorkflowStage.RightTopDown
        if (isLeft && headEulerAngleY > -18f) guidance += "Turn more to the left."
        if (isRight && headEulerAngleY < 18f) guidance += "Turn more to the right."
    }

    if ((leftEyeOpenProbability ?: 1f) < 0.45f || (rightEyeOpenProbability ?: 1f) < 0.45f) {
        guidance += "Remove glasses or headwear if it blocks the eyes."
    }
    if (meshPoints.size < 48) guidance += "Move closer and keep hair/headwear away from the face."
    
    return if (guidance.isEmpty()) {
        listOf(if (isFrontalStage) "Face mesh locked. Keep light falling evenly." else "Profile pose locked. Hold steady.")
    } else {
        guidance
    }
}
