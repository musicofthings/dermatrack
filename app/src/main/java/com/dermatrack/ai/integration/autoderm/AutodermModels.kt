package com.dermatrack.ai.integration.autoderm

import org.json.JSONArray
import org.json.JSONObject

data class AutodermPrediction(
    val name: String,
    val icd: String?,
    val confidence: Float,
    val readMoreUrl: String?,
)

data class AutodermSkinTone(
    val fitzpatrick: Int,
    val confidence: Float,
)

data class AutodermQueryResult(
    val modelVersion: String,
    val predictions: List<AutodermPrediction>,
    val skinTone: AutodermSkinTone? = null,
)

fun List<AutodermPrediction>.toJson(): String {
    val array = JSONArray()
    forEach { prediction ->
        array.put(
            JSONObject()
                .put("name", prediction.name)
                .put("icd", prediction.icd)
                .put("confidence", prediction.confidence.toDouble())
                .put("readMoreUrl", prediction.readMoreUrl),
        )
    }
    return array.toString()
}

fun parseAutodermPredictionsJson(json: String): List<AutodermPrediction> {
    if (json.isBlank()) return emptyList()
    val root = runCatching { JSONObject(json) }.getOrNull()
    val array = when {
        root?.has("predictions") == true -> root.optJSONArray("predictions")
        else -> JSONArray(json)
    } ?: JSONArray()
    return parsePredictionArray(array)
}

fun parseAutodermQueryResponse(body: String): AutodermQueryResult {
    val root = JSONObject(body)
    val predictions = parsePredictionArray(root.optJSONArray("predictions") ?: JSONArray())
    val skinTone = root.optJSONObject("skin_tone")?.let { tone ->
        AutodermSkinTone(
            fitzpatrick = tone.optInt("fitzpatrick", 0),
            confidence = tone.optDouble("confidence", 0.0).toFloat(),
        ).takeIf { it.fitzpatrick in 1..6 }
    }

    val modelVersion = root.optString("model", root.optString("model_version", DEFAULT_MODEL))
    return AutodermQueryResult(
        modelVersion = modelVersion.ifBlank { DEFAULT_MODEL },
        predictions = predictions,
        skinTone = skinTone,
    )
}

private fun parsePredictionArray(array: JSONArray): List<AutodermPrediction> =
    buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val name = item.optString("name")
                .ifBlank { item.optString("disease") }
                .ifBlank { item.optString("disease_name") }
            if (name.isBlank()) continue
            add(
                AutodermPrediction(
                    name = name,
                    icd = item.optString("icd_10")
                        .ifBlank { item.optString("icd") }
                        .takeIf { it.isNotBlank() },
                    confidence = item.optDouble("confidence", 0.0).toFloat(),
                    readMoreUrl = item.optString("readMoreUrl").takeIf { it.isNotBlank() },
                ),
            )
        }
    }.sortedByDescending { it.confidence }

const val DEFAULT_MODEL = "autoderm_v2_2"

const val INFER_DISEASES_PATH = "/v1/infer-diseases/v1"
