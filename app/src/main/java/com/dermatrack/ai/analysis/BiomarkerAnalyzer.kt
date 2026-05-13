package com.dermatrack.ai.analysis

import com.dermatrack.ai.capture.AlignmentState
import kotlin.math.roundToInt

data class BiomarkerResult(
    val erythemaIndex: Float,
    val melaninDistribution: Float,
    val poreTextureDensity: Float,
    val acneLesionCount: Int,
    val inflammatoryAcneCount: Int,
    val nonInflammatoryAcneCount: Int,
)

class BiomarkerAnalyzer {
    fun estimateFromCaptureQualityFallback(lux: Float, alignmentState: AlignmentState): BiomarkerResult {
        val lightPenalty = when {
            lux <= 0f -> 1f
            lux < 250f -> 0.92f
            lux > 950f -> 1.08f
            else -> 1f
        }
        val alignmentPenalty = 1f + ((1f - alignmentState.score).coerceIn(0f, 1f) * 0.08f)
        val erythema = (31.5f * lightPenalty * alignmentPenalty).round1()
        val melanin = (46.8f * alignmentPenalty).round1()
        val texture = (22.4f * lightPenalty).round1()
        val acne = (3 + ((1f - alignmentState.score) * 2)).roundToInt()

        return BiomarkerResult(
            erythemaIndex = erythema,
            melaninDistribution = melanin,
            poreTextureDensity = texture,
            acneLesionCount = acne,
            inflammatoryAcneCount = (acne * 0.6f).roundToInt(),
            nonInflammatoryAcneCount = acne - (acne * 0.6f).roundToInt(),
        )
    }

    private fun Float.round1(): Float = (this * 10f).roundToInt() / 10f
}
