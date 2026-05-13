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
import kotlinx.coroutines.launch

data class MainUiState(
    val scans: List<ScanEntity> = emptyList(),
    val products: List<ProductEntity> = emptyList(),
    val latestReport: ClinicalReport? = null,
)

data class ClinicalReport(
    val scan: ScanEntity,
    val recommendation: String,
    val riskNote: String,
)

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
                    )
                },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    fun recordDemoScan(lux: Float, alignmentState: AlignmentState) {
        viewModelScope.launch {
            val imageUri = vaultRepository.createPrivateImageSlot()
            val biomarkers = analyzer.estimateFromCaptureQualityFallback(lux = lux, alignmentState = alignmentState)
            val scanId = scanRepository.insertScan(
                imageUri = imageUri,
                lux = lux,
                biomarkers = biomarkers,
                alignmentScore = alignmentState.score,
            )
            vaultRepository.exportMarkdownMetadata(scanId = scanId, biomarkers = biomarkers, lux = lux)
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
