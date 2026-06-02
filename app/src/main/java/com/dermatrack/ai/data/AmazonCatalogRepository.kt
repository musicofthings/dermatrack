package com.dermatrack.ai.data

import com.dermatrack.ai.integration.amazon.AmazonCatalogClient
import com.dermatrack.ai.integration.amazon.AmazonCatalogProduct

class AmazonCatalogRepository(
    private val client: AmazonCatalogClient?,
) {
    suspend fun search(query: String): Result<List<AmazonCatalogProduct>> = runCatching {
        val api = client ?: error("Amazon backend is not configured.")
        api.searchProducts(query.trim())
    }
}
