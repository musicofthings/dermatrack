package com.dermatrack.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.dermatrack.ai.agent.RegimenEngine
import com.dermatrack.ai.analysis.BiomarkerAnalyzer
import com.dermatrack.ai.analysis.FaceEmbeddingEngine
import com.dermatrack.ai.analysis.FitzpatrickGroup
import com.dermatrack.ai.capture.AlignmentState
import com.dermatrack.ai.data.AppContainer
import com.dermatrack.ai.data.AmazonCatalogRepository
import com.dermatrack.ai.data.AutodermRepository
import com.dermatrack.ai.data.CloudAnalysisPreferences
import com.dermatrack.ai.data.PrimaryUserPreferences
import com.dermatrack.ai.data.ScanRepository
import com.dermatrack.ai.data.VaultRepository
import com.dermatrack.ai.data.model.AutodermScreeningEntity
import com.dermatrack.ai.data.model.CapturePose
import com.dermatrack.ai.data.model.PersonaEntity
import com.dermatrack.ai.data.model.ProductEntity
import com.dermatrack.ai.data.model.ScanEntity
import com.dermatrack.ai.integration.amazon.AmazonCatalogProduct
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class MainUiState(
    val scans: List<ScanEntity> = emptyList(),
    val personas: List<PersonaEntity> = emptyList(),
    val latestFrontImagePathByPersonaId: Map<Long, String> = emptyMap(),
    val selectedPersonaId: Long? = null,
    val products: List<ProductEntity> = emptyList(),
    val latestReport: ClinicalReport? = null,
    val autodermCloudEnabled: Boolean = false,
    val autodermApiConfigured: Boolean = false,
    val faceMatchingAvailable: Boolean = false,
    val suggestedPersona: PersonaSuggestion? = null,
    val amazonResults: List<AmazonCatalogProduct> = emptyList(),
    val amazonSearchLoading: Boolean = false,
    val amazonSearchError: String? = null,
    val primaryUserEmail: String? = null,
    val primaryPersonaId: Long? = null,
)

data class PersonaSuggestion(
    val personaId: Long,
    val personaName: String,
    val confidence: Float,
)

data class ClinicalReport(
    val scan: ScanEntity,
    val recommendation: String,
    val riskNote: String,
    val acneSeverityGrade: AcneSeverityGrade,
    val progress: LongitudinalProgress?,
    val autodermScreening: AutodermScreeningEntity? = null,
)

data class LongitudinalProgress(
    val daysSinceBaseline: Long,
    val lesionCountDeltaAbsolute: Int,
    val lesionCountDeltaPercent: Float?,
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

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val scanRepository: ScanRepository,
    private val vaultRepository: VaultRepository,
    private val analyzer: BiomarkerAnalyzer,
    private val faceEmbeddingEngine: FaceEmbeddingEngine,
    private val regimenEngine: RegimenEngine,
    private val amazonCatalogRepository: AmazonCatalogRepository,
    private val autodermRepository: AutodermRepository,
    private val cloudAnalysisPreferences: CloudAnalysisPreferences,
    private val primaryUserPreferences: PrimaryUserPreferences,
    autodermApiConfigured: Boolean,
) : ViewModel() {
    private val autodermCloudEnabled = MutableStateFlow(cloudAnalysisPreferences.isAutodermEnabled())
    private val selectedPersonaId = MutableStateFlow<Long?>(1L)
    private val rawSuggestion = MutableStateFlow<FaceEmbeddingEngine.PersonaMatchSuggestion?>(null)
    private val amazonResults = MutableStateFlow<List<AmazonCatalogProduct>>(emptyList())
    private val amazonSearchLoading = MutableStateFlow(false)
    private val amazonSearchError = MutableStateFlow<String?>(null)
    private val primaryUserEmail = MutableStateFlow(primaryUserPreferences.primaryUserEmail())
    private val primaryPersonaId = MutableStateFlow(primaryUserPreferences.primaryPersonaId())
    private val personaScopedScans = selectedPersonaId.filterNotNull().flatMapLatest { personaId ->
        scanRepository.observeScans(personaId)
    }
    private val supplementaryData = combine(
        scanRepository.observeProducts(),
        autodermRepository.observeScreenings(),
    ) { products, screenings ->
        products to screenings
    }
    private val amazonUiState = combine(
        amazonResults,
        amazonSearchLoading,
        amazonSearchError,
    ) { items, loading, error ->
        Triple(items, loading, error)
    }
    private val uiFlags = combine(
        supplementaryData,
        amazonUiState,
        autodermCloudEnabled,
        primaryUserEmail,
        primaryPersonaId,
    ) { supplementary, amazonState, cloudEnabled, primaryEmail, primaryPersona ->
        Quintuple(supplementary, amazonState, cloudEnabled, primaryEmail, primaryPersona)
    }

    val uiState: StateFlow<MainUiState> = combine(
        personaScopedScans,
        scanRepository.observeFrontScans(),
        scanRepository.observePersonas(),
        uiFlags,
    ) { scans, frontScans, personas, flags ->
        val (supplementary, amazonState, cloudEnabled, primaryEmail, primaryPersona) = flags
        val (products, autodermScreenings) = supplementary
        val (amazonItems, amazonLoading, amazonError) = amazonState
        val autodermByScanId = autodermScreenings.associateBy { it.scanId }
        val latestFrontByPersonaId = frontScans
            .groupBy { it.personaId }
            .mapValues { (_, personaScans) -> personaScans.first().imagePath }
        val latest = scans.firstOrNull()
        val suggestionUi = rawSuggestion.value?.let { suggestion ->
            personas.firstOrNull { it.id == suggestion.personaId }?.let { persona ->
                PersonaSuggestion(
                    personaId = persona.id,
                    personaName = persona.name,
                    confidence = suggestion.confidence,
                )
            }
        }
        MainUiState(
            scans = scans,
            personas = personas,
            latestFrontImagePathByPersonaId = latestFrontByPersonaId,
            selectedPersonaId = selectedPersonaId.value,
            products = products,
            latestReport = latest?.let { scan ->
                ClinicalReport(
                    scan = scan,
                    recommendation = regimenEngine.evaluate(scans = scans, products = products),
                    riskNote = "Grooming and health utility. Not a diagnosis or replacement for dermatologist care.",
                    acneSeverityGrade = scan.toAcneSeverityGrade(),
                    progress = scans.toLongitudinalProgress(),
                    autodermScreening = autodermByScanId[scan.id],
                )
            },
            autodermCloudEnabled = cloudEnabled,
            autodermApiConfigured = autodermApiConfigured,
            faceMatchingAvailable = faceEmbeddingEngine.isModelAvailable(),
            suggestedPersona = suggestionUi,
            amazonResults = amazonItems,
            amazonSearchLoading = amazonLoading,
            amazonSearchError = amazonError,
            primaryUserEmail = primaryEmail,
            primaryPersonaId = primaryPersona,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    init {
        viewModelScope.launch {
            val personas = scanRepository.observePersonas().first()
            if (personas.isEmpty()) {
                val defaultId = scanRepository.createPersona("Default")
                selectedPersonaId.value = defaultId
            } else if (selectedPersonaId.value !in personas.map { it.id }) {
                selectedPersonaId.value = personas.first().id
            }
        }
    }

    fun selectPersona(personaId: Long) {
        selectedPersonaId.value = personaId
        rawSuggestion.value = null
    }

    fun setPrimaryUserEmail(email: String) {
        val personaId = selectedPersonaId.value ?: 1L
        primaryUserPreferences.setPrimaryUser(email = email, primaryPersonaId = personaId)
        primaryUserEmail.value = email
        primaryPersonaId.value = personaId
    }

    fun clearPrimaryUser() {
        primaryUserPreferences.clearPrimaryUser()
        primaryUserEmail.value = null
        primaryPersonaId.value = null
    }

    fun createPersona(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val id = scanRepository.createPersona(trimmed)
            selectedPersonaId.value = id
            rawSuggestion.value = null
        }
    }

    fun acceptSuggestedPersona() {
        val suggestion = rawSuggestion.value ?: return
        selectedPersonaId.value = suggestion.personaId
        rawSuggestion.value = null
    }

    fun suggestPersonaFromImage(imageFile: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val embedding = faceEmbeddingEngine.computeEmbeddingOrNull(imageFile) ?: run {
                rawSuggestion.value = null
                return@launch
            }
            val prototypes = scanRepository.observePersonaEmbeddings()
                .first()
                .associate { entity -> entity.personaId to scanRepository.decodeEmbedding(entity) }
            val suggestion = faceEmbeddingEngine.suggestPersona(embedding, prototypes)
            rawSuggestion.value = suggestion
        }
    }

    fun setAutodermCloudEnabled(enabled: Boolean) {
        cloudAnalysisPreferences.setAutodermEnabled(enabled)
        autodermCloudEnabled.value = enabled
    }

    fun createPrivateScanImageFile(): File = vaultRepository.createPrivateImageFile()

    fun recordCapturedScan(
        lux: Float,
        alignmentState: AlignmentState,
        imageFile: File,
        fitzpatrickGroup: FitzpatrickGroup,
        capturePose: CapturePose,
        onComplete: () -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        viewModelScope.launch {
            val scanCommit = runCatching {
                val analysis = withContext(Dispatchers.IO) {
                    analyzer.analyzeCapturedFrame(
                        frame = imageFile.readBytes(),
                        lux = lux,
                        fitzpatrickGroup = fitzpatrickGroup,
                    )
                }
                val imageUri = vaultRepository.privateImageUri(imageFile)
                val personaId = selectedPersonaId.value ?: 1L
                val scanId = scanRepository.insertScan(
                    imageUri = imageUri,
                    lux = lux,
                    biomarkers = analysis.result,
                    alignmentScore = alignmentState.score,
                    analysisSource = analysis.source,
                    fitzpatrickGroup = analysis.fitzpatrickGroup,
                    capturePose = capturePose,
                    personaId = personaId,
                )
                if (capturePose == CapturePose.Front) {
                    launch(Dispatchers.IO) {
                        val embedding = faceEmbeddingEngine.computeEmbeddingOrNull(imageFile) ?: return@launch
                        scanRepository.upsertPersonaEmbedding(personaId = personaId, embedding = embedding)
                    }
                }
                ScanCommit(scanId = scanId, analysis = analysis)
            }

            val commit = scanCommit.getOrElse { throwable ->
                imageFile.delete()
                onError(throwable)
                return@launch
            }

            runCatching {
                withContext(Dispatchers.IO) {
                    vaultRepository.exportMarkdownMetadata(
                        scanId = commit.scanId,
                        biomarkers = commit.analysis.result,
                        lux = lux,
                        analysisSource = commit.analysis.source,
                        fitzpatrickGroup = commit.analysis.fitzpatrickGroup,
                    )
                }
            }.onFailure {
                Log.w("MainViewModel", "Markdown export failed for scan ${commit.scanId}", it)
            }

            if (cloudAnalysisPreferences.isAutodermEnabled()) {
                launch(Dispatchers.IO) {
                    autodermRepository.analyzeScanIfConfigured(
                        scanId = commit.scanId,
                        imageFile = imageFile,
                    )
                }
            }

            onComplete()
        }
    }

    private data class ScanCommit(
        val scanId: Long,
        val analysis: com.dermatrack.ai.analysis.BiomarkerAnalysis,
    )

    fun addInventoryItem(name: String, ingredients: String) {
        viewModelScope.launch {
            scanRepository.insertProduct(name = name, ingredients = ingredients)
        }
    }

    fun searchAmazonCatalog(query: String) {
        val q = query.trim()
        if (q.length < 2) {
            amazonSearchError.value = "Type at least 2 characters."
            amazonResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            amazonSearchLoading.value = true
            amazonSearchError.value = null
            val result = amazonCatalogRepository.search(q)
            amazonSearchLoading.value = false
            result.onSuccess {
                amazonResults.value = it
                if (it.isEmpty()) amazonSearchError.value = "No Amazon products found."
            }.onFailure {
                amazonResults.value = emptyList()
                amazonSearchError.value = it.message ?: "Amazon search failed."
            }
        }
    }

    fun addAmazonToWishlist(product: AmazonCatalogProduct) {
        val ingredients = buildString {
            append("Amazon")
            append(" · ASIN ${product.asin}")
            product.rating?.let { append(" · Rating ${"%.1f".format(it)}") }
            product.ratingCount?.let { append(" (${it} reviews)") }
            product.price?.let { append(" · $it") }
            product.url?.let { append(" · $it") }
        }
        addInventoryItem(name = product.title, ingredients = ingredients)
    }

    fun clearSelectedPersonaHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val personaId = selectedPersonaId.value ?: return@launch
            val scans = scanRepository.listScansForPersona(personaId)
            scans.forEach { scan ->
                vaultRepository.deleteScanArtifacts(
                    scanId = scan.id,
                    imagePath = scan.imagePath,
                )
            }
            // Autoderm rows tied to these scans are removed via ON DELETE CASCADE.
            scanRepository.clearHistoryForPersona(personaId)
        }
    }

    fun removePersona(personaId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val email = primaryUserEmail.value
            val primaryPersona = primaryPersonaId.value
            if (email.isNullOrBlank() || primaryPersona == null) return@launch
            if (personaId == primaryPersona) return@launch
            val personas = scanRepository.listPersonas()
            if (personas.size <= 1) return@launch
            val scans = scanRepository.listScansForPersona(personaId)
            scans.forEach { scan ->
                vaultRepository.deleteScanArtifacts(scanId = scan.id, imagePath = scan.imagePath)
            }
            scanRepository.deletePersona(personaId)
            if (selectedPersonaId.value == personaId) {
                selectedPersonaId.value = primaryPersona
            }
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
                        faceEmbeddingEngine = container.faceEmbeddingEngine,
                        regimenEngine = container.regimenEngine,
                        amazonCatalogRepository = container.amazonCatalogRepository,
                        autodermRepository = container.autodermRepository,
                        cloudAnalysisPreferences = container.cloudAnalysisPreferences,
                        primaryUserPreferences = container.primaryUserPreferences,
                        autodermApiConfigured = container.autodermApiConfigured,
                    ) as T
                }
            }
    }
}

private data class Quintuple<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E,
)

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
    val days = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(
        (latest.capturedAtEpochMillis - baseline.capturedAtEpochMillis).coerceAtLeast(0L),
    )
    val lesionDelta = latest.acneLesionCount - baseline.acneLesionCount
    val erythemaDelta = latest.erythemaIndex - baseline.erythemaIndex

    val trend = when {
        lesionDelta < 0 || erythemaDelta < -3f -> ScanTrend.Improving
        lesionDelta > 2 || erythemaDelta > 3f -> ScanTrend.Worsening
        else -> ScanTrend.Stable
    }

    val lesionPercent = if (baseline.acneLesionCount > 0) {
        (lesionDelta.toFloat() / baseline.acneLesionCount) * 100f
    } else {
        null
    }

    return LongitudinalProgress(
        daysSinceBaseline = days,
        lesionCountDeltaAbsolute = lesionDelta,
        lesionCountDeltaPercent = lesionPercent,
        erythemaDelta = erythemaDelta,
        pigmentationDelta = latest.melaninDistribution - baseline.melaninDistribution,
        trend = trend,
    )
}
