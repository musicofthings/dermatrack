package com.dermatrack.ai.capture

import android.graphics.RectF
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.PreviewView
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.dermatrack.ai.ui.CaptureWorkflowStage
import java.util.concurrent.Executor

/**
 * ML Kit face mesh in [PreviewView] pixel space via [MlKitAnalyzer] and
 * [ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED] (CameraX + ML Kit integration guide).
 */
class FaceMlKitAnalyzer(
    private val previewViewProvider: () -> PreviewView?,
    private val stage: () -> CaptureWorkflowStage = { CaptureWorkflowStage.Setup },
    private val onFaceTracking: (FaceTrackingState) -> Unit,
    resultExecutor: Executor,
) : AutoCloseable {
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
    @Volatile
    private var lastEmittedAt = 0L

    val analyzer: MlKitAnalyzer = MlKitAnalyzer(
        listOf(detector),
        ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED,
        resultExecutor,
    ) { result ->
        val now = System.currentTimeMillis()
        if (now - lastEmittedAt < 250L) return@MlKitAnalyzer
        lastEmittedAt = now
        @Suppress("UNCHECKED_CAST")
        val faces = result.getValue(detector) as? List<Face> ?: emptyList()
        onFaceTracking(
            faces.toFaceTrackingState(
                previewView = previewViewProvider(),
                stage = stage(),
            ),
        )
    }

    override fun close() {
        detector.close()
    }
}

private fun List<Face>.toFaceTrackingState(
    previewView: PreviewView?,
    stage: CaptureWorkflowStage,
): FaceTrackingState {
    val viewWidth = previewView?.width ?: 0
    val viewHeight = previewView?.height ?: 0
    if (viewWidth <= 0 || viewHeight <= 0) {
        return FaceTrackingState(guidance = listOf("Camera preview initializing…"))
    }

    val face = maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
        ?: return FaceTrackingState(
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            guidance = listOf("No face detected. Move into the frame."),
        )

    fun normPoint(x: Float, y: Float) = NormalizedPoint(
        x = (x / viewWidth).coerceIn(0f, 1f),
        y = (y / viewHeight).coerceIn(0f, 1f),
    )

    val boundsRect = RectF(face.boundingBox)
    val contourLines = face.allContours.mapNotNull { contour ->
        contour.points.map { point -> normPoint(point.x, point.y) }.takeIf { it.size > 1 }
    }
    val faceOutline = face.getContour(FaceContour.FACE)?.points.orEmpty().map { point ->
        normPoint(point.x, point.y)
    }
    val meshPoints = contourLines.flatten()
    val centerOffsetX = ((boundsRect.left + boundsRect.right) / 2f) / viewWidth - 0.5f

    return FaceTrackingState(
        detected = true,
        viewWidth = viewWidth,
        viewHeight = viewHeight,
        faceBounds = boundsRect,
        meshPoints = meshPoints,
        contourLines = contourLines,
        faceOutline = faceOutline,
        yawDegrees = face.headEulerAngleY,
        rollDegrees = face.headEulerAngleZ,
        leftEyeOpenProbability = face.leftEyeOpenProbability,
        rightEyeOpenProbability = face.rightEyeOpenProbability,
        guidance = face.guidance(meshPoints, stage, centerOffsetX),
    )
}

private fun Face.guidance(
    meshPoints: List<NormalizedPoint>,
    stage: CaptureWorkflowStage,
    centerOffsetX: Float,
): List<String> {
    val guidance = mutableListOf<String>()
    val isLeftProfile = stage == CaptureWorkflowStage.LeftProfilePrompt ||
        stage == CaptureWorkflowStage.LeftProfileCapture
    val isRightProfile = stage == CaptureWorkflowStage.RightProfilePrompt ||
        stage == CaptureWorkflowStage.RightProfileCapture
    val isFrontalStage = !isLeftProfile && !isRightProfile

    if (kotlin.math.abs(headEulerAngleZ) > 8f) guidance += "Sit straight and keep your head level."

    // Turn direction is detected sign-agnostically by yaw magnitude (the front
    // camera's yaw sign is not assumed); the spoken/stage prompt says which way.
    val turnedFar = kotlin.math.abs(headEulerAngleY) >= 14f
    when {
        isLeftProfile -> if (!turnedFar) guidance += "Turn your face further to the right."
        isRightProfile -> if (!turnedFar) guidance += "Turn your face further to the left."
        else -> if (kotlin.math.abs(headEulerAngleY) > 10f) guidance += "Look straight at the camera."
    }

    if (isFrontalStage) {
        if (kotlin.math.abs(centerOffsetX) > 0.18f) guidance += "Center your face inside the guide."
        val eyesClosed = (leftEyeOpenProbability ?: 1f) < 0.45f || (rightEyeOpenProbability ?: 1f) < 0.45f
        if (eyesClosed) guidance += "Open your eyes; remove glasses if they block them."
        if (meshPoints.size < 48) guidance += "Move closer and keep hair/headwear away from the face."
    }

    return if (guidance.isEmpty()) {
        listOf(
            if (isFrontalStage) {
                "Face mesh locked. Hold steady and keep light even."
            } else {
                "Profile pose locked. Hold steady."
            },
        )
    } else {
        guidance
    }
}
