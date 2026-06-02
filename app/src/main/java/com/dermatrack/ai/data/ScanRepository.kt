package com.dermatrack.ai.data

import android.net.Uri
import com.dermatrack.ai.analysis.BiomarkerAnalysisSource
import com.dermatrack.ai.analysis.BiomarkerResult
import com.dermatrack.ai.analysis.FitzpatrickGroup
import com.dermatrack.ai.data.local.ScanDao
import com.dermatrack.ai.data.model.CapturePose
import com.dermatrack.ai.data.model.ProductEntity
import com.dermatrack.ai.data.model.ScanEntity

class ScanRepository(private val scanDao: ScanDao) {
    fun observeScans() = scanDao.observeScans()

    fun observeProducts() = scanDao.observeProducts()

    suspend fun insertScan(
        imageUri: Uri,
        lux: Float,
        biomarkers: BiomarkerResult,
        alignmentScore: Float,
        analysisSource: BiomarkerAnalysisSource,
        fitzpatrickGroup: FitzpatrickGroup,
        capturePose: CapturePose,
    ): Long = scanDao.insertScan(
        ScanEntity(
            imagePath = imageUri.path.orEmpty(),
            capturedAtEpochMillis = System.currentTimeMillis(),
            baselineLux = lux,
            erythemaIndex = biomarkers.erythemaIndex,
            melaninDistribution = biomarkers.melaninDistribution,
            poreTextureDensity = biomarkers.poreTextureDensity,
            acneLesionCount = biomarkers.acneLesionCount,
            inflammatoryAcneCount = biomarkers.inflammatoryAcneCount,
            nonInflammatoryAcneCount = biomarkers.nonInflammatoryAcneCount,
            alignmentScore = alignmentScore,
            analysisSource = analysisSource.name,
            fitzpatrickGroup = fitzpatrickGroup.name,
            capturePose = capturePose.name,
        ),
    )

    suspend fun insertProduct(name: String, ingredients: String) {
        scanDao.insertProduct(
            ProductEntity(
                name = name,
                activeIngredients = ingredients,
                addedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }
}
