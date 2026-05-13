package com.dermatrack.ai.agent

import com.dermatrack.ai.data.model.ProductEntity
import com.dermatrack.ai.data.model.ScanEntity
import java.util.concurrent.TimeUnit

class RegimenEngine {
    fun evaluate(scans: List<ScanEntity>, products: List<ProductEntity>): String {
        if (scans.size < 2) {
            return "Baseline captured. Continue the regimen for 21 days before ingredient pivot logic is applied."
        }

        val latest = scans.first()
        val oldest = scans.last()
        val days = TimeUnit.MILLISECONDS.toDays(latest.capturedAtEpochMillis - oldest.capturedAtEpochMillis)
        val melaninDelta = latest.melaninDistribution - oldest.melaninDistribution
        val currentActives = products.joinToString(" ") { it.activeIngredients }.lowercase()

        return when {
            days >= 21 && melaninDelta >= -1.0f && "niacinamide" in currentActives ->
                "Melanin distribution is stagnant after 21 days. Consider an ingredient pivot toward alpha arbutin, azelaic acid, or tranexamic acid products after patch testing."
            latest.erythemaIndex - oldest.erythemaIndex > 4f ->
                "Erythema has increased. Reduce exfoliating actives and prioritize barrier-supporting ingredients until redness normalizes."
            latest.acneLesionCount > oldest.acneLesionCount ->
                "Acne lesion count is trending upward. Review comedogenic inventory items and consider benzoyl peroxide or salicylic acid under clinician guidance."
            else ->
                "No pivot triggered. Maintain current regimen and capture under matched light for cleaner longitudinal comparison."
        }
    }
}
