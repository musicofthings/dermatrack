package com.dermatrack.ai.data

import android.net.Uri
import com.dermatrack.ai.analysis.BiomarkerAnalysisSource
import com.dermatrack.ai.analysis.BiomarkerResult
import com.dermatrack.ai.analysis.FitzpatrickGroup
import com.dermatrack.ai.data.local.ScanDao
import com.dermatrack.ai.data.model.CapturePose
import com.dermatrack.ai.data.model.PersonaEntity
import com.dermatrack.ai.data.model.PersonaEmbeddingEntity
import com.dermatrack.ai.data.model.ProductEntity
import com.dermatrack.ai.data.model.ScanEntity
import org.json.JSONArray

class ScanRepository(private val scanDao: ScanDao) {
    fun observeScans(personaId: Long) = scanDao.observeScansForPersona(personaId)
    fun observeFrontScans() = scanDao.observeFrontScans()

    fun observeProducts() = scanDao.observeProducts()
    fun observePersonas() = scanDao.observePersonas()
    fun observePersonaEmbeddings() = scanDao.observePersonaEmbeddings()

    suspend fun insertScan(
        imageUri: Uri,
        lux: Float,
        biomarkers: BiomarkerResult,
        alignmentScore: Float,
        analysisSource: BiomarkerAnalysisSource,
        fitzpatrickGroup: FitzpatrickGroup,
        capturePose: CapturePose,
        personaId: Long,
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
            personaId = personaId,
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

    suspend fun createPersona(name: String): Long {
        return scanDao.insertPersona(
            PersonaEntity(
                name = name.trim(),
                createdAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun listPersonas(): List<PersonaEntity> = scanDao.listPersonas()

    suspend fun upsertPersonaEmbedding(personaId: Long, embedding: FloatArray) {
        scanDao.upsertPersonaEmbedding(
            PersonaEmbeddingEntity(
                personaId = personaId,
                embeddingJson = JSONArray(embedding.map { it.toDouble() }).toString(),
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    fun decodeEmbedding(entity: PersonaEmbeddingEntity): FloatArray {
        val json = JSONArray(entity.embeddingJson)
        return FloatArray(json.length()) { index -> json.getDouble(index).toFloat() }
    }

    suspend fun listScansForPersona(personaId: Long): List<ScanEntity> =
        scanDao.listScansForPersona(personaId)

    suspend fun clearHistoryForPersona(personaId: Long) {
        scanDao.clearScansForPersona(personaId)
        scanDao.clearPersonaEmbeddingForPersona(personaId)
    }

    suspend fun deletePersona(personaId: Long) {
        clearHistoryForPersona(personaId)
        scanDao.deletePersonaById(personaId)
    }

    suspend fun clearHistory() {
        scanDao.clearScans()
        scanDao.clearProducts()
        scanDao.clearPersonaEmbeddings()
    }
}
