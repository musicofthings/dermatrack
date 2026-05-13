package com.dermatrack.ai.affiliate

data class ProductRecommendation(
    val ingredient: String,
    val title: String,
    val price: String,
    val availability: String,
    val affiliateUrl: String,
)

interface AmazonIndiaProductGateway {
    suspend fun searchByIngredient(ingredient: String): List<ProductRecommendation>
}

class PaApiAmazonIndiaGateway : AmazonIndiaProductGateway {
    override suspend fun searchByIngredient(ingredient: String): List<ProductRecommendation> {
        // Wire this to Amazon PA-API India after credentials and associate tag are provided.
        return emptyList()
    }
}
