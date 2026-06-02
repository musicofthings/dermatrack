package com.dermatrack.ai.integration.autoderm

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject

/**
 * Autoderm v1 disease-inference client.
 *
 * `POST {baseUrl}/v1/infer-diseases/v1?include_skin_tone=...&require_anonymous=...` with HTTP
 * Bearer auth and a multipart `image` field, per the official OpenAPI spec
 * (`https://api.autoderm.ai`).
 */
class AutodermApiClient(
    private val apiKey: String,
    private val baseUrl: String,
    private val analyzePath: String = INFER_DISEASES_PATH,
    private val httpClient: OkHttpClient = defaultClient(),
) {
    fun analyzeImage(
        imageFile: File,
        includeSkinTone: Boolean = true,
        faceCapture: Boolean = true,
    ): AutodermQueryResult {
        require(apiKey.isNotBlank()) { "Autoderm API key is not configured." }
        require(imageFile.exists()) { "Capture image is missing." }

        val path = analyzePath.trim().let { if (it.startsWith("/")) it else "/$it" }
        val url = "${baseUrl.trimEnd('/')}$path".toHttpUrl().newBuilder()
            .addQueryParameter("include_skin_tone", includeSkinTone.toString())
            .addQueryParameter("require_anonymous", faceCapture.toString())
            .build()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "image",
                imageFile.name,
                imageFile.asRequestBody("image/jpeg".toMediaType()),
            )
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Autoderm HTTP ${response.code}: ${extractDetail(body) ?: body}")
            }
            return parseAutodermQueryResponse(body)
        }
    }

    companion object {
        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build()

        private fun extractDetail(body: String): String? =
            runCatching { JSONObject(body).optString("detail").takeIf { it.isNotBlank() } }.getOrNull()
    }
}
