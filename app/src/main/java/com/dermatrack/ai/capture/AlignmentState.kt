package com.dermatrack.ai.capture

data class AlignmentState(
    val score: Float,
    val headTiltDegrees: Float,
    val distanceDelta: Float,
) {
    val isAcceptable: Boolean
        get() = score >= 0.82f && kotlin.math.abs(headTiltDegrees) <= 6f && kotlin.math.abs(distanceDelta) <= 0.12f

    /**
     * Looser bound used to auto-capture once the face is *reasonably* aligned. Capture quality is
     * still recorded via [score]/[ScanEntity.alignmentScore]; it is not folded into biomarkers,
     * so a slightly off-center or near/far face should not block the capture indefinitely.
     */
    val isReasonable: Boolean
        get() = score >= 0.62f && kotlin.math.abs(headTiltDegrees) <= 8f && distanceDelta in -0.23f..0.20f

    companion object {
        val BaselineReady = AlignmentState(
            score = 0.91f,
            headTiltDegrees = 2.5f,
            distanceDelta = 0.04f,
        )
    }
}
