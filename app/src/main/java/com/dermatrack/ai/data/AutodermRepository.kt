package com.dermatrack.ai.data

import com.dermatrack.ai.data.local.AutodermScreeningDao
import com.dermatrack.ai.data.model.AutodermScreeningEntity
import com.dermatrack.ai.data.model.AutodermScreeningStatus
import com.dermatrack.ai.integration.autoderm.AutodermApiClient
import com.dermatrack.ai.integration.autoderm.toJson
import kotlinx.coroutines.flow.Flow
import java.io.File

class AutodermRepository(
    private val autodermScreeningDao: AutodermScreeningDao,
    private val apiClient: AutodermApiClient?,
) {
    fun observeScreenings(): Flow<List<AutodermScreeningEntity>> = autodermScreeningDao.observeScreenings()

    fun observeForScan(scanId: Long): Flow<AutodermScreeningEntity?> = autodermScreeningDao.observeForScan(scanId)

    suspend fun analyzeScanIfConfigured(scanId: Long, imageFile: File) {
        val client = apiClient ?: run {
            upsertFailure(scanId, "Autoderm API key is not configured.")
            return
        }

        runCatching {
            val result = client.analyzeImage(imageFile = imageFile, includeSkinTone = true, faceCapture = true)
            autodermScreeningDao.upsert(
                AutodermScreeningEntity(
                    scanId = scanId,
                    modelVersion = result.modelVersion,
                    status = AutodermScreeningStatus.Success.name,
                    errorMessage = null,
                    predictionsJson = result.predictions.toJson(),
                    skinToneFitzpatrick = result.skinTone?.fitzpatrick,
                    skinToneConfidence = result.skinTone?.confidence,
                    analyzedAtEpochMillis = System.currentTimeMillis(),
                ),
            )
        }.onFailure { error ->
            upsertFailure(scanId, error.message ?: "Autoderm request failed.")
        }
    }

    private suspend fun upsertFailure(scanId: Long, message: String) {
        autodermScreeningDao.upsert(
            AutodermScreeningEntity(
                scanId = scanId,
                modelVersion = "",
                status = AutodermScreeningStatus.Failed.name,
                errorMessage = message,
                predictionsJson = "[]",
                analyzedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun clearAll() {
        autodermScreeningDao.clearAll()
    }
}
