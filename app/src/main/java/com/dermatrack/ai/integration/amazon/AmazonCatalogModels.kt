package com.dermatrack.ai.integration.amazon

data class AmazonCatalogProduct(
    val asin: String,
    val title: String,
    val rating: Float?,
    val ratingCount: Int?,
    val price: String?,
    val url: String?,
)
