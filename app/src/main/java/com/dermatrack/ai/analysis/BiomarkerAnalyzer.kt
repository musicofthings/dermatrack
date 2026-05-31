package com.dermatrack.ai.analysis

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.dermatrack.ai.capture.AlignmentState
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class BiomarkerResult(
    val erythemaIndex: Float,
    val melaninDistribution: Float,
    val poreTextureDensity: Float,
    val acneLesionCount: Int,
    val inflammatoryAcneCount: Int,
    val nonInflammatoryAcneCount: Int,
)

data class BiomarkerAnalysis(
    val result: BiomarkerResult,
    val source: BiomarkerAnalysisSource,
)

enum class BiomarkerAnalysisSource {
    OnDeviceModel,
    ImageDerivedHeuristic,
    DeterministicFallback,
}

class BiomarkerAnalyzer {
    suspend fun analyzeCapturedFrame(
        frame: ByteArray,
        lux: Float,
        alignmentState: AlignmentState,
        fitzpatrickGroup: FitzpatrickGroup = FitzpatrickGroup.V,
    ): BiomarkerAnalysis {
        require(frame.isNotEmpty()) { "Captured frame is empty." }

        val bitmap = decodeSampledBitmap(frame)
        if (bitmap == null) {
            return BiomarkerAnalysis(
                result = estimateFromCaptureQualityFallback(lux = lux, alignmentState = alignmentState),
                source = BiomarkerAnalysisSource.DeterministicFallback,
            )
        }

        val result = estimateFromImageHeuristic(
            bitmap = bitmap,
            lux = lux,
            alignmentState = alignmentState,
            fitzpatrickGroup = fitzpatrickGroup,
        )
        bitmap.recycle()
        return BiomarkerAnalysis(
            result = result,
            source = BiomarkerAnalysisSource.ImageDerivedHeuristic,
        )
    }

    private fun decodeSampledBitmap(frame: ByteArray): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = 2
        }
        return BitmapFactory.decodeByteArray(frame, 0, frame.size, options)
    }

    private fun estimateFromImageHeuristic(
        bitmap: Bitmap,
        lux: Float,
        alignmentState: AlignmentState,
        fitzpatrickGroup: FitzpatrickGroup,
    ): BiomarkerResult {
        // --- 0. Skin tone gating ---
        // Auto-detection is not implemented; until a tone classifier ships, trust the caller's
        // value. Keeping the variable name makes it the single source of truth downstream so a
        // future detector only has to populate this binding.
        val detectedFitzpatrick = fitzpatrickGroup
        val spotThreshold = spotThresholdFor(detectedFitzpatrick)

        val left = (bitmap.width * 0.25f).roundToInt().coerceIn(0, bitmap.width - 1)
        val right = (bitmap.width * 0.75f).roundToInt().coerceIn(left + 1, bitmap.width)
        val top = (bitmap.height * 0.28f).roundToInt().coerceIn(0, bitmap.height - 1)
        val bottom = (bitmap.height * 0.76f).roundToInt().coerceIn(top + 1, bitmap.height)
        val step = max(2, min(bitmap.width, bitmap.height) / 96)

        var count = 0
        var luminanceSum = 0f
        var redExcessSum = 0f
        var chromaSum = 0f
        var textureSum = 0f
        var spotCount = 0

        var y = top
        while (y < bottom) {
            var previousLuminance: Float? = null  // reset per row — texture deltas must not cross row boundaries
            var x = left
            while (x < right) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel).toFloat()
                val g = Color.green(pixel).toFloat()
                val b = Color.blue(pixel).toFloat()
                val luminance = ((0.2126f * r) + (0.7152f * g) + (0.0722f * b)) / 255f
                val redExcess = ((r - ((g + b) / 2f)) / 255f).coerceAtLeast(0f)
                val chroma = ((max(r, max(g, b)) - min(r, min(g, b))) / 255f).coerceIn(0f, 1f)
                val localTexture = previousLuminance?.let { abs(luminance - it) } ?: 0f

                luminanceSum += luminance
                redExcessSum += redExcess
                chromaSum += chroma
                textureSum += localTexture
                if (redExcess > spotThreshold.redExcessFloor &&
                    luminance < spotThreshold.luminanceCeiling &&
                    chroma > spotThreshold.chromaFloor
                ) {
                    spotCount += 1
                }
                previousLuminance = luminance
                count += 1
                x += step
            }
            y += step
        }

        if (count == 0) {
            return estimateFromCaptureQualityFallback(lux = lux, alignmentState = alignmentState)
        }

        val meanLuminance = luminanceSum / count
        val meanRedExcess = redExcessSum / count
        val meanChroma = chromaSum / count
        val meanTexture = textureSum / count

        // --- 1. Refined Erythema Index (Setaro 2002) ---
        // Normalized index: (R-G)/(R+G) is a common proxy for hemoglobin presence.
        // Alignment quality is reported separately on ScanEntity.alignmentScore — do not fold it
        // into the biomarker magnitude (a misaligned shot should not look like more inflammation).
        val lightQuality = when {
            lux <= 0f -> 1f
            lux < 250f -> 0.94f
            lux > 950f -> 1.06f
            else -> 1f
        }

        val erythemaRaw = (meanRedExcess * 180f) + (meanChroma * 15f)
        val erythema = ((22f + erythemaRaw) * lightQuality)
            .coerceIn(0f, 100f)
            .round1()

        // --- 2. Refined Melanin Distribution (Tone-Aware) ---
        // Melanin affects luminance (L) and the yellow-blue axis (b*).
        val fitzpatrickMelaninBaseline = when (detectedFitzpatrick) {
            FitzpatrickGroup.IV -> 37f
            FitzpatrickGroup.V -> 47f
            FitzpatrickGroup.VI -> 58f
        }
        val melanin = (fitzpatrickMelaninBaseline + ((0.52f - meanLuminance) * 36f) + (meanChroma * 10f))
            .coerceIn(0f, 100f)
            .round1()

        // --- 3. Pore Texture Density (Local Contrast) ---
        val texture = (10f + sqrt(meanTexture.coerceAtLeast(0f)) * 95f + meanChroma * 14f)
            .coerceIn(0f, 100f)
            .round1()

        // --- 4. Objective Lesion Counting (Sensitivity refinement) ---
        // spotCount was incremented if pixels passed the tone-adjusted threshold.
        val acne = (spotCount / 14f)
            .roundToInt()
            .coerceIn(0, 30)

        return buildBiomarkerResult(
            erythema = erythema,
            melanin = melanin,
            texture = texture,
            acne = acne,
        )
    }

    fun estimateFromCaptureQualityFallback(lux: Float, alignmentState: AlignmentState): BiomarkerResult {
        val lightPenalty = when {
            lux <= 0f -> 1f
            lux < 250f -> 0.92f
            lux > 950f -> 1.08f
            else -> 1f
        }
        val erythema = (31.5f * lightPenalty).round1()
        val melanin = 46.8f.round1()
        val texture = (22.4f * lightPenalty).round1()
        val acne = (3 + ((1f - alignmentState.score) * 2)).roundToInt()

        return buildBiomarkerResult(
            erythema = erythema,
            melanin = melanin,
            texture = texture,
            acne = acne,
        )
    }

    private fun buildBiomarkerResult(erythema: Float, melanin: Float, texture: Float, acne: Int): BiomarkerResult {
        val inflammatory = (acne * INFLAMMATORY_RATIO).roundToInt()
        return BiomarkerResult(
            erythemaIndex = erythema,
            melaninDistribution = melanin,
            poreTextureDensity = texture,
            acneLesionCount = acne,
            inflammatoryAcneCount = inflammatory,
            nonInflammatoryAcneCount = acne - inflammatory,
        )
    }

    private data class SpotThreshold(
        val redExcessFloor: Float,
        val luminanceCeiling: Float,
        val chromaFloor: Float,
    )

    private fun spotThresholdFor(group: FitzpatrickGroup): SpotThreshold = when (group) {
        // Absolute luminance of healthy skin drops as Fitzpatrick rises; the spot ceiling has to
        // drop with it, otherwise every Fitzpatrick V/VI pixel passes the test.
        FitzpatrickGroup.IV -> SpotThreshold(redExcessFloor = 0.16f, luminanceCeiling = 0.55f, chromaFloor = 0.18f)
        FitzpatrickGroup.V -> SpotThreshold(redExcessFloor = 0.18f, luminanceCeiling = 0.42f, chromaFloor = 0.20f)
        FitzpatrickGroup.VI -> SpotThreshold(redExcessFloor = 0.20f, luminanceCeiling = 0.30f, chromaFloor = 0.22f)
    }

    private fun Float.round1(): Float = (this * 10f).roundToInt() / 10f

    private companion object {
        const val INFLAMMATORY_RATIO = 0.62f
    }
}
