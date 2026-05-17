package com.dermatrack.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.dermatrack.ai.agent.RegimenEngine
import com.dermatrack.ai.analysis.BiomarkerAnalyzer
import com.dermatrack.ai.capture.AlignmentState
import com.dermatrack.ai.data.AppContainer
import com.dermatrack.ai.data.ScanRepository
import com.dermatrack.ai.data.VaultRepository
import com.dermatrack.ai.data.model.ProductEntity
import com.dermatrack.ai.data.model.ScanEntity
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class MainUiState(
    val scans: List<ScanEntity> = emptyList(),
    val products: List<ProductEntity> = emptyList(),
    val latestReport: ClinicalReport? = null,
)

data class ClinicalReport(
    val scan: ScanEntity,
    val recommendation: String,
    val riskNote: String,
    val acneSeverityGrade: AcneSeverityGrade,
    val progress: LongitudinalProgress?,
)

data class LongitudinalProgress(
    val daysSinceBaseline: Long,
    val lesionCountDeltaPercent: Float,
    val erythemaDelta: Float,
    val pigmentationDelta: Float,
    val trend: ScanTrend = ScanTrend.Stable,
)

enum class ScanTrend(val label: String, val color: Long) {
    Improving("Improving", 0xFF73D6B5),
    Stable("Stable", 0xFFE2B05B),
    Worsening("Worsening", 0xFFB3261E),
}

enum class AcneSeverityGrade(val label: String) {
    Clear("Clear"),
    AlmostClear("Almost clear"),
    Mild("Mild"),
    Moderate("Moderate"),
    Severe("Severe"),
}

class MainViewModel(
    private val scanRepository: ScanRepository,
    private val vaultRepository: VaultRepository,
    private val analyzer: BiomarkerAnalyzer,
    private val regimenEngine: RegimenEngine,
) : ViewModel() {
    val uiState: StateFlow<MainUiState> = combine(
        scanRepository.observeScans(),
        scanRepository.observeProducts(),
    ) { scans, products ->
            val latest = scans.firstOrNull()
            MainUiState(
                scans = scans,
                products = products,
                latestReport = latest?.let {
                    ClinicalReport(
                        scan = it,
                        recommendation = regimenEngine.evaluate(scans = scans, products = products),
                        riskNote = "Grooming and health utility. Not a diagnosis or replacement for dermatologist care.",
                        acneSeverityGrade = it.toAcneSeverityGrade(),
                        progress = scans.toLongitudinalProgress(),
                    )
                },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    fun createPrivateScanImageFile(): File = vaultRepository.createPrivateImageFile()

    fun recordCapturedScan(
        lux: Float,
        alignmentState: AlignmentState,
        imageFile: File,
        onComplete: () -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                val analysis = withContext(Dispatchers.IO) {
                    analyzer.analyzeCapturedFrame(
                        frame = imageFile.readBytes(),
                        lux = lux,
                        alignmentState = alignmentState,
                    )
                }
                val imageUri = vaultRepository.privateImageUri(imageFile)
                val scanId = scanRepository.insertScan(
                    imageUri = imageUri,
                    lux = lux,
                    biomarkers = analysis.result,
                    alignmentScore = alignmentState.score,
                )
                withContext(Dispatchers.IO) {
                    vaultRepository.exportMarkdownMetadata(
                        scanId = scanId,
                        biomarkers = analysis.result,
                        lux = lux,
                        analysisSource = analysis.source,
                    )
                }
            }.onSuccess {
                onComplete()
            }.onFailure { throwable ->
                imageFile.delete()
                onError(throwable)
            }
        }
    }

    fun addInventoryItem(name: String, ingredients: String) {
        viewModelScope.launch {
            scanRepository.insertProduct(name = name, ingredients = ingredients)
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    return MainViewModel(
                        scanRepository = container.scanRepository,
                        vaultRepository = container.vaultRepository,
                        analyzer = container.biomarkerAnalyzer,
                        regimenEngine = container.regimenEngine,
                    ) as T
                }
            }
    }
}

private fun ScanEntity.toAcneSeverityGrade(): AcneSeverityGrade = when {
    acneLesionCount == 0 -> AcneSeverityGrade.Clear
    acneLesionCount <= 2 -> AcneSeverityGrade.AlmostClear
    acneLesionCount <= 6 -> AcneSeverityGrade.Mild
    acneLesionCount <= 12 -> AcneSeverityGrade.Moderate
    else -> AcneSeverityGrade.Severe
}

private fun List<ScanEntity>.toLongitudinalProgress(): LongitudinalProgress? {
    if (size < 2) return null
    val latest = first()
    val baseline = last()
    val baselineLesions = baseline.acneLesionCount.coerceAtLeast(1)
    val days = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(
        latest.capturedAtEpochMillis - baseline.capturedAtEpochMillis,
    )
    val lesionDelta = latest.acneLesionCount - baseline.acneLesionCount
    val erythemaDelta = latest.erythemaIndex - baseline.erythemaIndex
    
    val trend = when {
        lesionDelta < 0 || erythemaDelta < -3f -> ScanTrend.Improving
        lesionDelta > 2 || erythemaDelta > 3f -> ScanTrend.Worsening
        else -> ScanTrend.Stable
    }

    return LongitudinalProgress(
        daysSinceBaseline = days,
        lesionCountDeltaPercent = ((lesionDelta).toFloat() / baselineLesions) * 100f,
        erythemaDelta = erythemaDelta,
        pigmentationDelta = latest.melaninDistribution - baseline.melaninDistribution,
        trend = trend
    )
}
