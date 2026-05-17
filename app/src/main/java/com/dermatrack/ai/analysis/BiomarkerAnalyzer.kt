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

        var previousLuminance: Float? = null
        var y = top
        while (y < bottom) {
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
                if (redExcess > 0.16f && luminance < 0.58f && chroma > 0.18f) {
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
        val lightQuality = when {
            lux <= 0f -> 1f
            lux < 250f -> 0.94f
            lux > 950f -> 1.06f
            else -> 1f
        }
        val fitzpatrickMelaninBaseline = when (fitzpatrickGroup) {
            FitzpatrickGroup.IV -> 37f
            FitzpatrickGroup.V -> 47f
            FitzpatrickGroup.VI -> 58f
        }
        val alignmentPenalty = 1f + ((1f - alignmentState.score).coerceIn(0f, 1f) * 0.05f)

        val erythema = ((24f + (meanRedExcess * 150f) + (meanChroma * 10f)) * lightQuality * alignmentPenalty)
            .coerceIn(0f, 100f)
            .round1()
        val melanin = (fitzpatrickMelaninBaseline + ((0.54f - meanLuminance) * 34f) + (meanChroma * 8f))
            .coerceIn(0f, 100f)
            .round1()
        val texture = ((12f + sqrt(meanTexture.coerceAtLeast(0f)) * 90f + meanChroma * 12f) * alignmentPenalty)
            .coerceIn(0f, 100f)
            .round1()
        val acne = (spotCount / 18f)
            .roundToInt()
            .coerceIn(0, 18)

        return BiomarkerResult(
            erythemaIndex = erythema,
            melaninDistribution = melanin,
            poreTextureDensity = texture,
            acneLesionCount = acne,
            inflammatoryAcneCount = (acne * 0.62f).roundToInt(),
            nonInflammatoryAcneCount = acne - (acne * 0.62f).roundToInt(),
        )
    }

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
