package com.dermatrack.ai.capture

data class AlignmentState(
    val score: Float,
    val headTiltDegrees: Float,
    val distanceDelta: Float,
) {
    val isAcceptable: Boolean
        get() = score >= 0.82f && kotlin.math.abs(headTiltDegrees) <= 6f && kotlin.math.abs(distanceDelta) <= 0.12f

    companion object {
        val BaselineReady = AlignmentState(
            score = 0.91f,
            headTiltDegrees = 2.5f,
            distanceDelta = 0.04f,
        )
    }
}
