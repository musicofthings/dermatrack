package com.dermatrack.ai.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.tensorflow.lite.Interpreter

/**
 * MobileFaceNet/FaceNet scaffold.
 *
 * This intentionally does not fabricate identity outputs. Matching is considered
 * unavailable until a real TFLite model is present in assets/models.
 */
class FaceEmbeddingEngine(context: Context) {
    private val appContext = context.applicationContext
    private val modelPath = "models/mobile_facenet.tflite"
    private val assets = appContext.assets
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .build(),
    )
    @Volatile
    private var cachedInterpreter: Interpreter? = null

    fun isModelAvailable(): Boolean = runCatching {
        assets.open(modelPath).use { true }
    }.getOrDefault(false)

    data class PersonaMatchSuggestion(
        val personaId: Long,
        val confidence: Float,
    )

    suspend fun computeEmbeddingOrNull(imageFile: File): FloatArray? {
        if (!isModelAvailable()) return null
        val bitmap = decodeBitmap(imageFile) ?: return null
        val faceCrop = cropPrimaryFace(bitmap) ?: bitmap
        val modelInput = preprocess(faceCrop)
        if (faceCrop !== bitmap) faceCrop.recycle()
        bitmap.recycle()

        val interpreter = interpreter() ?: return null
        val outputShape = interpreter.getOutputTensor(0).shape()
        val embeddingSize = outputShape.lastOrNull() ?: return null
        if (embeddingSize <= 0) return null
        val output = Array(1) { FloatArray(embeddingSize) }
        interpreter.run(modelInput, output)
        return l2Normalize(output[0])
    }

    fun suggestPersona(
        embedding: FloatArray,
        prototypes: Map<Long, FloatArray>,
    ): PersonaMatchSuggestion? {
        if (prototypes.isEmpty()) return null
        var bestPersonaId: Long? = null
        var bestScore = -1f
        prototypes.forEach { (personaId, prototype) ->
            val score = cosineSimilarity(embedding, prototype)
            if (score > bestScore) {
                bestScore = score
                bestPersonaId = personaId
            }
        }
        val personaId = bestPersonaId ?: return null
        val confidence = ((bestScore + 1f) / 2f).coerceIn(0f, 1f)
        return if (confidence >= MIN_MATCH_CONFIDENCE) {
            PersonaMatchSuggestion(personaId = personaId, confidence = confidence)
        } else {
            null
        }
    }

    private fun decodeBitmap(imageFile: File): Bitmap? {
        val raw = BitmapFactory.decodeFile(imageFile.absolutePath) ?: return null
        val rotation = runCatching {
            when (
                ExifInterface(imageFile.absolutePath).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        if (rotation == 0f) return raw
        val rotated = Bitmap.createBitmap(
            raw,
            0,
            0,
            raw.width,
            raw.height,
            Matrix().apply { postRotate(rotation) },
            true,
        )
        raw.recycle()
        return rotated
    }

    private fun cropPrimaryFace(bitmap: Bitmap): Bitmap? {
        val latch = CountDownLatch(1)
        var bounds: Rect? = null
        detector.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { faces ->
                bounds = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }?.boundingBox
                latch.countDown()
            }
            .addOnFailureListener { latch.countDown() }
        latch.await(800, TimeUnit.MILLISECONDS)
        val faceBounds = bounds ?: return null
        val cx = faceBounds.centerX()
        val cy = faceBounds.centerY()
        val size = (maxOf(faceBounds.width(), faceBounds.height()) * 1.25f).toInt().coerceAtLeast(32)
        val left = (cx - size / 2).coerceAtLeast(0)
        val top = (cy - size / 2).coerceAtLeast(0)
        val right = (left + size).coerceAtMost(bitmap.width)
        val bottom = (top + size).coerceAtMost(bitmap.height)
        if (right - left < 16 || bottom - top < 16) return null
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    private fun preprocess(faceBitmap: Bitmap): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(faceBitmap, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, true)
        val input = ByteBuffer.allocateDirect(4 * MODEL_INPUT_SIZE * MODEL_INPUT_SIZE * 3)
            .order(ByteOrder.nativeOrder())
        for (y in 0 until MODEL_INPUT_SIZE) {
            for (x in 0 until MODEL_INPUT_SIZE) {
                val px = resized.getPixel(x, y)
                val r = ((px shr 16) and 0xFF)
                val g = ((px shr 8) and 0xFF)
                val b = (px and 0xFF)
                input.putFloat((r - 127.5f) / 128f)
                input.putFloat((g - 127.5f) / 128f)
                input.putFloat((b - 127.5f) / 128f)
            }
        }
        resized.recycle()
        input.rewind()
        return input
    }

    private fun interpreter(): Interpreter? {
        cachedInterpreter?.let { return it }
        val bytes = runCatching {
            assets.open(modelPath).use { it.readBytes() }
        }.getOrNull() ?: return null
        val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply {
            put(bytes)
            rewind()
        }
        val interpreter = runCatching {
            Interpreter(buffer, Interpreter.Options().apply { setNumThreads(2) })
        }.getOrNull() ?: return null
        cachedInterpreter = interpreter
        return interpreter
    }

    private fun l2Normalize(vector: FloatArray): FloatArray {
        var sumSq = 0f
        vector.forEach { sumSq += it * it }
        val norm = kotlin.math.sqrt(sumSq.toDouble()).toFloat().coerceAtLeast(1e-6f)
        return FloatArray(vector.size) { i -> vector[i] / norm }
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || b.isEmpty() || a.size != b.size) return -1f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = kotlin.math.sqrt(normA.toDouble()).toFloat() * kotlin.math.sqrt(normB.toDouble()).toFloat()
        if (denom <= 1e-6f) return -1f
        return dot / denom
    }

    private companion object {
        private const val MODEL_INPUT_SIZE = 112
        private const val MIN_MATCH_CONFIDENCE = 0.78f
    }
}
