package com.dermatrack.ai.integration.amazon

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class AmazonCatalogClient(
    private val baseUrl: String,
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun searchProducts(query: String): List<AmazonCatalogProduct> {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val url = "${baseUrl.trimEnd('/')}/amazon/search?q=$encoded"
        val request = Request.Builder().url(url).get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Amazon search failed: HTTP ${response.code}")
            }
            val payload = response.body?.string().orEmpty()
            return parseItems(payload)
        }
    }

    private fun parseItems(json: String): List<AmazonCatalogProduct> {
        val root = JSONObject(json)
        val items = root.optJSONArray("items") ?: JSONArray()
        return buildList {
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                add(
                    AmazonCatalogProduct(
                        asin = item.optString("asin"),
                        title = item.optString("title"),
                        rating = item.optDouble("rating").takeIf { !it.isNaN() }?.toFloat(),
                        ratingCount = item.optInt("rating_count").takeIf { it > 0 },
                        price = item.optString("price").takeIf { it.isNotBlank() },
                        url = item.optString("url").takeIf { it.isNotBlank() },
                    )
                )
            }
        }
    }
}
