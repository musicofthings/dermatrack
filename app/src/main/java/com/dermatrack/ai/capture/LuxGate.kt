package com.dermatrack.ai.capture

data class LuxGate(
    val baselineLux: Float,
    val currentLux: Float,
) {
    val deviationPercent: Float
        get() = if (baselineLux <= 0f) 0f else kotlin.math.abs(currentLux - baselineLux) / baselineLux

    val isAcceptable: Boolean
        get() = baselineLux <= 0f || deviationPercent <= 0.20f
}
