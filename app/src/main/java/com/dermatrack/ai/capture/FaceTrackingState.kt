package com.dermatrack.ai.capture

import android.graphics.RectF

data class FaceTrackingState(
    val detected: Boolean = false,
    /** [PreviewView] width when coordinates were produced (view-referenced space). */
    val viewWidth: Int = 0,
    val viewHeight: Int = 0,
    /** Face bounds in [PreviewView] pixels. */
    val faceBounds: RectF? = null,
    val meshPoints: List<NormalizedPoint> = emptyList(),
    val contourLines: List<List<NormalizedPoint>> = emptyList(),
    val faceOutline: List<NormalizedPoint> = emptyList(),
    val yawDegrees: Float = 0f,
    val rollDegrees: Float = 0f,
    val leftEyeOpenProbability: Float? = null,
    val rightEyeOpenProbability: Float? = null,
    val guidance: List<String> = listOf("Position your face inside the guide."),
    val skinToneCategory: com.dermatrack.ai.analysis.FitzpatrickGroup = com.dermatrack.ai.analysis.FitzpatrickGroup.V,
) {
    val isStraightEnough: Boolean
        get() = detected && kotlin.math.abs(yawDegrees) <= 10f && kotlin.math.abs(rollDegrees) <= 8f

    val possibleObstruction: Boolean
        get() = detected && (
            (leftEyeOpenProbability != null && leftEyeOpenProbability < 0.45f) ||
                (rightEyeOpenProbability != null && rightEyeOpenProbability < 0.45f) ||
                meshPoints.size < 48
            )

    fun toAlignmentState(): AlignmentState {
        if (!detected || faceBounds == null) return AlignmentState(0f, 0f, 0f)

        val yawFactor = (1f - (kotlin.math.abs(yawDegrees) / 35f)).coerceIn(0f, 1f)
        val rollFactor = (1f - (kotlin.math.abs(rollDegrees) / 25f)).coerceIn(0f, 1f)
        val score = (yawFactor * 0.7f) + (rollFactor * 0.3f)

        val frameArea = (viewWidth * viewHeight).toFloat().coerceAtLeast(1f)
        val faceArea = (faceBounds.width() * faceBounds.height()) / frameArea
        val idealArea = 0.35f
        val distanceDelta = faceArea - idealArea

        return AlignmentState(
            score = score,
            headTiltDegrees = rollDegrees,
            distanceDelta = distanceDelta,
        )
    }
}

/** 0..1 coordinates in [PreviewView] space (MlKitAnalyzer view-referenced output). */
data class NormalizedPoint(
    val x: Float,
    val y: Float,
)
