package com.dermatrack.ai.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.media.AudioManager
import android.media.ExifInterface
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import android.util.Log
import android.util.Size as CameraSize
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dermatrack.ai.ClinicalReport
import com.dermatrack.ai.LongitudinalProgress
import com.dermatrack.ai.MainUiState
import com.dermatrack.ai.MainViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.dermatrack.ai.capture.AlignmentState
import com.dermatrack.ai.capture.FaceMlKitAnalyzer
import com.dermatrack.ai.capture.FaceTrackingState
import com.dermatrack.ai.capture.NormalizedPoint
import com.dermatrack.ai.capture.LightMeter
import com.dermatrack.ai.capture.LuxGate
import com.dermatrack.ai.analysis.FitzpatrickGroup
import com.dermatrack.ai.data.model.CapturePose
import com.dermatrack.ai.data.model.PersonaEntity
import com.dermatrack.ai.data.model.ProductEntity
import com.dermatrack.ai.data.model.ScanEntity
import com.dermatrack.ai.data.model.analysisSourceUserLabel
import com.dermatrack.ai.data.model.capturePoseLabel
import com.dermatrack.ai.data.model.fitzpatrickUserLabel
import com.dermatrack.ai.data.model.isSuccess
import com.dermatrack.ai.data.model.predictions
import com.dermatrack.ai.data.model.AutodermScreeningEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max

private enum class AppTab { Home, Report, Capture, Inventory, Regulatory, Privacy }

enum class CaptureWorkflowStage {
    Setup,
    MeshLock,
    FrontVertical,
    FrontHorizontal,
    FrontCapture,
    LeftProfilePrompt,
    LeftProfileCapture,
    RightProfilePrompt,
    RightProfileCapture,
}

private enum class ScanDirection { None, TopDown, LeftRight }

/** A captured-but-not-yet-analyzed frame, held until the user confirms in review. */
private data class PendingCapture(
    val pose: CapturePose,
    val file: File,
    val alignment: AlignmentState,
    val lux: Float,
    val fitzpatrick: FitzpatrickGroup,
)

private val CameraStreamResolutionSelector = ResolutionSelector.Builder()
    .setResolutionStrategy(
        ResolutionStrategy(
            CameraSize(640, 480),
            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
        ),
    )
    .build()

@Composable
fun DermaTrackAppRoot(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(AppTab.Home) }
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val googleSignInClient = remember(context) {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = StartActivityForResult(),
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        val account = runCatching { task.result }.getOrNull()
        val email = account?.email
        if (!email.isNullOrBlank()) {
            viewModel.setPrimaryUserEmail(email)
        }
    }

    Scaffold(
        topBar = {
            ClinicalTopBar(
                onMenuClick = { menuExpanded = true },
                menuExpanded = menuExpanded,
                onDismissMenu = { menuExpanded = false },
                onSelectTab = {
                    selectedTab = it
                    menuExpanded = false
                },
            )
        },
        bottomBar = {
            Column {
                ComplianceFooterBadges()
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == AppTab.Home,
                        onClick = { selectedTab = AppTab.Home },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                    )
                    NavigationBarItem(
                        selected = selectedTab == AppTab.Report,
                        onClick = { selectedTab = AppTab.Report },
                        icon = { Icon(Icons.Default.Science, contentDescription = "Report") },
                        label = { Text("Report") },
                    )
                    NavigationBarItem(
                        selected = selectedTab == AppTab.Capture,
                        onClick = { selectedTab = AppTab.Capture },
                        icon = { Icon(Icons.Default.CameraAlt, contentDescription = "Capture") },
                        label = { Text("Capture") },
                    )
                    NavigationBarItem(
                        selected = selectedTab == AppTab.Inventory,
                        onClick = { selectedTab = AppTab.Inventory },
                        icon = { Icon(Icons.Default.Inventory2, contentDescription = "Inventory") },
                        label = { Text("Inventory") },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
        ) {
            when (selectedTab) {
                AppTab.Home -> PersonaHomeScreen(
                    personas = uiState.personas,
                    latestFrontImagePathByPersonaId = uiState.latestFrontImagePathByPersonaId,
                    selectedPersonaId = uiState.selectedPersonaId,
                    faceMatchingAvailable = uiState.faceMatchingAvailable,
                    suggestedPersona = uiState.suggestedPersona,
                    primaryUserEmail = uiState.primaryUserEmail,
                    primaryPersonaId = uiState.primaryPersonaId,
                    onSelectPersona = viewModel::selectPersona,
                    onCreatePersona = viewModel::createPersona,
                    onAcceptSuggestion = viewModel::acceptSuggestedPersona,
                    onSignInPrimary = { googleSignInLauncher.launch(googleSignInClient.signInIntent) },
                    onSignOutPrimary = {
                        googleSignInClient.signOut()
                        viewModel.clearPrimaryUser()
                    },
                    onRemovePersona = viewModel::removePersona,
                    onGoToCapture = { selectedTab = AppTab.Capture },
                )
                AppTab.Report -> ReportScreen(uiState)
                AppTab.Capture -> CaptureScreen(
                    baselineLux = uiState.scans.firstOrNull()?.baselineLux ?: 520f,
                    autodermCloudEnabled = uiState.autodermCloudEnabled,
                    autodermApiConfigured = uiState.autodermApiConfigured,
                    onAutodermCloudEnabledChange = viewModel::setAutodermCloudEnabled,
                    onCreateImageFile = viewModel::createPrivateScanImageFile,
                    onRecord = { lux, alignmentState, imageFile, fitzpatrickGroup, capturePose, onComplete, onError ->
                        viewModel.recordCapturedScan(
                            lux = lux,
                            alignmentState = alignmentState,
                            imageFile = imageFile,
                            fitzpatrickGroup = fitzpatrickGroup,
                            capturePose = capturePose,
                            onComplete = onComplete,
                            onError = onError,
                        )
                    },
                    onClearHistory = viewModel::clearSelectedPersonaHistory,
                    onSuggestPersonaFromImage = viewModel::suggestPersonaFromImage,
                    onSessionComplete = { selectedTab = AppTab.Report },
                )
                AppTab.Inventory -> InventoryScreen(
                    products = uiState.products,
                    onAdd = viewModel::addInventoryItem,
                    amazonResults = uiState.amazonResults,
                    amazonLoading = uiState.amazonSearchLoading,
                    amazonError = uiState.amazonSearchError,
                    onAmazonSearch = viewModel::searchAmazonCatalog,
                    onAddAmazonWishlist = viewModel::addAmazonToWishlist,
                )
                AppTab.Regulatory -> RegulatoryScreen()
                AppTab.Privacy -> PrivacyFirstScreen()
            }
        }
    }
}

@Composable
private fun ComplianceFooterBadges() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ComplianceBadge(text = "CE Marked")
        ComplianceBadge(text = "HIPAA Compliant")
        ComplianceBadge(text = "GDPR Compliant")
    }
}

@Composable
private fun ComplianceBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClinicalTopBar(
    onMenuClick: () -> Unit,
    menuExpanded: Boolean,
    onDismissMenu: () -> Unit,
    onSelectTab: (AppTab) -> Unit,
) {
    TopAppBar(
        title = {
            Column {
                Text("DermaTrack AI", fontWeight = FontWeight.SemiBold)
                Text(
                    "Skin biomarker tracking (research utility)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        actions = {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.Menu, contentDescription = "Open menu")
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = onDismissMenu) {
                DropdownMenuItem(text = { Text("Home") }, onClick = { onSelectTab(AppTab.Home) })
                DropdownMenuItem(text = { Text("Report") }, onClick = { onSelectTab(AppTab.Report) })
                DropdownMenuItem(text = { Text("Capture") }, onClick = { onSelectTab(AppTab.Capture) })
                DropdownMenuItem(text = { Text("Inventory") }, onClick = { onSelectTab(AppTab.Inventory) })
                DropdownMenuItem(text = { Text("Regulatory") }, onClick = { onSelectTab(AppTab.Regulatory) })
                DropdownMenuItem(text = { Text("Privacy First") }, onClick = { onSelectTab(AppTab.Privacy) })
            }
        },
    )
}

@Composable
private fun RegulatoryScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Regulatory", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Placeholder regulatory content. Replace with approved legal/compliance copy before production release.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("HIPAA Information", fontWeight = FontWeight.SemiBold)
                    Text(
                        "DermaTrack AI is designed to support privacy-forward handling of user health-related data with local processing defaults and constrained access pathways.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("GDPR Information", fontWeight = FontWeight.SemiBold)
                    Text(
                        "DermaTrack AI follows principles of minimization, transparency, and user control. Users can manage and clear stored persona history from within the app.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacyFirstScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Privacy First Policy", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Generic placeholder privacy text. Replace with finalized policy language before shipping.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Local-only by default", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Face images and scan artifacts are kept in app-private local storage unless optional cloud features are explicitly enabled.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No cloud images by default", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Biometric images are not uploaded by default. Optional cloud screening is opt-in and clearly labeled.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("User controls", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Users can clear selected persona history, retake captures before analysis, and keep persona-specific data separated.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonaHomeScreen(
    personas: List<PersonaEntity>,
    latestFrontImagePathByPersonaId: Map<Long, String>,
    selectedPersonaId: Long?,
    faceMatchingAvailable: Boolean,
    suggestedPersona: com.dermatrack.ai.PersonaSuggestion?,
    primaryUserEmail: String?,
    primaryPersonaId: Long?,
    onSelectPersona: (Long) -> Unit,
    onCreatePersona: (String) -> Unit,
    onAcceptSuggestion: () -> Unit,
    onSignInPrimary: () -> Unit,
    onSignOutPrimary: () -> Unit,
    onRemovePersona: (Long) -> Unit,
    onGoToCapture: () -> Unit,
) {
    var newPersonaName by remember { mutableStateOf("") }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        item {
            Card {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Who is being scanned?", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Select a persona before capture so trends and reports stay person-specific.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = newPersonaName,
                        onValueChange = { newPersonaName = it },
                        label = { Text("Create new persona") },
                        singleLine = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                onCreatePersona(newPersonaName)
                                newPersonaName = ""
                            },
                            enabled = newPersonaName.isNotBlank(),
                        ) { Text("Add Persona") }
                        OutlinedButton(
                            onClick = onGoToCapture,
                            enabled = selectedPersonaId != null,
                        ) { Text("Start Capture") }
                    }
                    Text(
                        if (faceMatchingAvailable) {
                            "Face matching model available: persona auto-suggestion can be enabled next."
                        } else {
                            "Face matching model not installed yet (add assets/models/mobile_facenet.tflite)."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider()
                    Text("Primary User Access", style = MaterialTheme.typography.labelLarge)
                    Text(
                        primaryUserEmail?.let { "Signed in as $it" }
                            ?: "Sign in with Google to enable remove-user controls.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (primaryUserEmail == null) {
                            OutlinedButton(onClick = onSignInPrimary) { Text("Sign in with Google") }
                        } else {
                            OutlinedButton(onClick = onSignOutPrimary) { Text("Sign out") }
                        }
                    }
                }
            }
        }
        suggestedPersona?.let { suggestion ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Suggested persona", fontWeight = FontWeight.SemiBold)
                            Text(
                                "${suggestion.personaName} · ${(suggestion.confidence * 100).toInt()}% match",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(onClick = onAcceptSuggestion) { Text("Use Suggestion") }
                    }
                }
            }
        }
        items(personas) { persona ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (persona.id == selectedPersonaId) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        latestFrontImagePathByPersonaId[persona.id]?.let { imagePath ->
                            val imageFile = remember(imagePath) { File(imagePath) }
                            if (imageFile.exists()) {
                                CapturedImage(
                                    file = imageFile,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF121816)),
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF121816)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.7f),
                                    )
                                }
                            }
                        } ?: Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF121816)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.7f),
                            )
                        }
                        Column {
                        Text(persona.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Created ${formatDate(persona.createdAtEpochMillis)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        }
                    }
                    OutlinedButton(onClick = { onSelectPersona(persona.id) }) {
                        Text(if (persona.id == selectedPersonaId) "Selected" else "Select")
                    }
                }
                if (primaryUserEmail != null && primaryPersonaId != null && persona.id != primaryPersonaId) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        OutlinedButton(onClick = { onRemovePersona(persona.id) }) {
                            Text("Remove User")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportScreen(uiState: MainUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        item {
            ClinicalDisclaimer()
        }
        item {
            uiState.latestReport?.let { ClinicalReportCard(it) } ?: EmptyReportCard()
        }
        item {
            TrendPanel(scans = uiState.scans)
        }
        items(uiState.scans) { scan ->
            ScanRow(scan)
        }
    }
}

@Composable
private fun ClinicalDisclaimer() {
    Text(
        text = "Grooming and health utility — not a diagnosis. Biomarker values may be image heuristics until on-device models are validated. For persistent acne, melasma, PIH, pain, bleeding, or rapid changes, consult a dermatologist.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun EmptyReportCard() {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("No baseline scan yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "Capture Day 0 under stable light. Future scans will be compared against this baseline.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ClinicalReportCard(report: ClinicalReport) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Latest Report", style = MaterialTheme.typography.titleMedium)
            Text(
                report.scan.analysisSourceUserLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                report.scan.fitzpatrickUserLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BiomarkerGrid(report.scan)
            Text(
                "Lesion-count severity proxy (not validated IGA): ${report.acneSeverityGrade.label}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            report.progress?.let {
                HorizontalDivider()
                ProgressMetrics(it)
            }
            report.autodermScreening?.let { screening ->
                HorizontalDivider()
                AutodermScreeningSection(screening)
            }
            HorizontalDivider()
            Text("Decision Logic", style = MaterialTheme.typography.labelLarge)
            Text(report.recommendation, style = MaterialTheme.typography.bodyMedium)
            Text(
                report.riskNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProgressMetrics(progress: LongitudinalProgress) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Baseline Change", style = MaterialTheme.typography.labelLarge)
            Text(
                text = progress.trend.label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = Color(progress.trend.color),
                fontWeight = FontWeight.Bold,
            )
        }
        MetricRow("Follow-up window", "${progress.daysSinceBaseline} days")
        MetricRow("Acne lesion count", lesionDeltaLabel(progress))
        MetricRow("Erythema index", progress.erythemaDelta.signedDecimal())
        MetricRow("Pigmentation evenness", progress.pigmentationDelta.signedDecimal())
    }
}

@Composable
private fun AutodermScreeningSection(screening: AutodermScreeningEntity) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Autoderm cloud screening", style = MaterialTheme.typography.labelLarge)
        Text(
            "Third-party CE-marked model (autoderm.ai). Not a diagnosis — for clinician review only.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (screening.isSuccess()) {
            Text(
                "Model: ${screening.modelVersion}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            screening.skinToneFitzpatrick?.let { fitz ->
                val conf = screening.skinToneConfidence?.let { " (${"%.0f".format(it * 100f)}%)" }.orEmpty()
                Text(
                    "Autoderm Fitzpatrick estimate: type $fitz$conf",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            screening.predictions().take(5).forEachIndexed { index, prediction ->
                val confidence = (prediction.confidence * 100f).coerceIn(0f, 100f)
                Text(
                    "${index + 1}. ${prediction.name} — ${"%.0f".format(confidence)}%",
                    style = MaterialTheme.typography.bodyMedium,
                )
                prediction.icd?.let { icd ->
                    Text(
                        "ICD: $icd",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Text(
                screening.errorMessage ?: "Autoderm screening failed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun AutodermCloudOptIn(
    enabled: Boolean,
    apiConfigured: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Autoderm cloud screening", style = MaterialTheme.typography.bodyMedium)
            Text(
                if (apiConfigured) {
                    "Optional: send each saved capture to Autoderm disease inference (image leaves device)."
                } else {
                    "Add AUTODERM_API_KEY to local.properties to enable."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = enabled && apiConfigured,
            onCheckedChange = onEnabledChange,
            enabled = apiConfigured,
        )
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BiomarkerGrid(scan: ScanEntity) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BiomarkerBar("Erythema Index", scan.erythemaIndex, 100f, Color(0xFFB7534A))
        BiomarkerBar("Melanin Distribution", scan.melaninDistribution, 100f, Color(0xFF7C5C43))
        BiomarkerBar("Pore Texture Density", scan.poreTextureDensity, 100f, Color(0xFF4F7468))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Acne Lesion Count", style = MaterialTheme.typography.bodyMedium)
            Text("${scan.acneLesionCount}", fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun Float.percentDelta(): String {
    val prefix = if (this > 0f) "+" else ""
    return "$prefix${"%.0f".format(this)}%"
}

private fun lesionDeltaLabel(progress: LongitudinalProgress): String {
    val abs = progress.lesionCountDeltaAbsolute
    val absPrefix = if (abs > 0) "+" else ""
    val absText = "$absPrefix$abs"
    return when (val pct = progress.lesionCountDeltaPercent) {
        null -> "$absText (baseline 0)"
        else -> "$absText  (${pct.percentDelta()})"
    }
}

private fun Float.signedDecimal(): String {
    val prefix = if (this > 0f) "+" else ""
    return "$prefix${"%.1f".format(this)}"
}

@Composable
private fun BiomarkerBar(label: String, value: Float, max: Float, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("%.1f".format(value), fontWeight = FontWeight.SemiBold)
        }
        LinearProgressIndicator(
            progress = { (value / max).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun TrendPanel(scans: List<ScanEntity>) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Longitudinal Trend", style = MaterialTheme.typography.titleMedium)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
            ) {
                val ordered = scans.reversed()
                val axisColor = Color(0xFFCAD7D2)
                drawLine(axisColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2f)
                drawLine(axisColor, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 2f)
                if (ordered.size > 1) {
                    val step = size.width / (ordered.lastIndex)
                    val points = ordered.mapIndexed { index, scan ->
                        Offset(
                            x = index * step,
                            y = size.height - ((scan.melaninDistribution / 100f).coerceIn(0.1f, 0.9f) * size.height),
                        )
                    }
                    points.zipWithNext().forEach { (a, b) ->
                        drawLine(Color(0xFF7C5C43), a, b, strokeWidth = 5f, cap = StrokeCap.Round)
                    }
                    points.forEach { drawCircle(Color(0xFF176B5D), radius = 7f, center = it) }
                }
            }
            Text(
                "Primary plotted marker: melanin distribution for PIH and melasma follow-up.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ScanRow(scan: ScanEntity) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val imageFile = remember(scan.imagePath) { File(scan.imagePath) }
            if (imageFile.exists()) {
                CapturedImage(
                    file = imageFile,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF121816)),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(formatDate(scan.capturedAtEpochMillis), fontWeight = FontWeight.SemiBold)
                Text(
                    "${scan.capturePoseLabel()} · Lux ${scan.baselineLux.toInt()} · Alignment ${(scan.alignmentScore * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    scan.analysisSourceUserLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("M %.1f".format(scan.melaninDistribution), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Lifecycle-aware [TextToSpeech] handle. Returns a `speak(text)` lambda that
 * de-duplicates consecutive identical prompts so guidance is not repeated every
 * analysis frame, and releases the engine on disposal (CLAUDE.md rule 6).
 */
@Composable
private fun rememberSpeechAnnouncer(): (String) -> Unit {
    val context = LocalContext.current
    val engineState = remember { mutableStateOf<TextToSpeech?>(null) }
    val ready = remember { mutableStateOf(false) }
    val lastSpokenAt = remember { mutableLongStateOf(0L) }

    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.US
                engine?.setSpeechRate(0.96f)
                ready.value = true
            }
        }
        engineState.value = engine
        onDispose {
            engine?.stop()
            engine?.shutdown()
            engineState.value = null
            ready.value = false
        }
    }

    // Don't interrupt an in-progress utterance, and keep a quiet gap after each
    // one so prompts don't run together / stutter.
    return remember {
        { message: String ->
            val engine = engineState.value
            if (engine != null && ready.value && message.isNotBlank()) {
                val now = System.currentTimeMillis()
                if (!engine.isSpeaking && now - lastSpokenAt.value >= MIN_ANNOUNCE_GAP_MS) {
                    lastSpokenAt.value = now
                    engine.speak(message, TextToSpeech.QUEUE_FLUSH, null, "dt-guidance")
                }
            }
        }
    }
}

@Composable
private fun rememberScanTonePlayer(): Pair<() -> Unit, () -> Unit> {
    val tone = remember { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 75) }
    DisposableEffect(Unit) {
        onDispose { tone.release() }
    }
    val tick = remember { { tone.startTone(ToneGenerator.TONE_PROP_BEEP, 70); Unit } }
    val complete = remember { { tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 260); Unit } }
    return tick to complete
}

private const val MIN_ANNOUNCE_GAP_MS = 1_800L

// Profile auto-capture tuning: the face must first pass near-center (NEUTRAL),
// then turn past TRIGGER and hold briefly. After MAX_WAIT the trigger relaxes to
// RELAX so a comfortable (not full) turn still qualifies. Capture NEVER fires on a
// near-front frame — a profile must be genuinely turned, or we keep prompting.
private const val PROFILE_YAW_TRIGGER_DEG = 12f
private const val PROFILE_YAW_RELAX_DEG = 9f
private const val PROFILE_NEUTRAL_DEG = 6f
private const val PROFILE_POLL_MS = 150L
private const val PROFILE_HOLD_MS = 450L
private const val PROFILE_MAX_WAIT_MS = 2_500L
private const val PROFILE_POSITIONING_MS = 6_000L
private const val FRONT_PASS_SECONDS = 3
private const val FRONT_TOTAL_SECONDS = FRONT_PASS_SECONDS * 2
private const val SCAN_TRANSITION_SECONDS = 3

/**
 * Short spoken coaching cue for the current pose/stage, or empty when idle.
 * Ordered by priority: lighting, then obstruction, then pose, then framing,
 * so the user hears the single most useful instruction at a time.
 */
private fun spokenGuidanceFor(
    stage: CaptureWorkflowStage,
    tracking: FaceTrackingState,
    luxAcceptable: Boolean,
    firstProfileYawSign: Int,
): String {
    if (stage == CaptureWorkflowStage.Setup) return ""
    if (!tracking.detected) return "Move into the frame so I can see your face."

    if (!luxAcceptable) return "Find brighter, even lighting on your face."

    // Profile stages: only the turn direction matters (face is intentionally turned).
    // Confirm the turn as soon as it crosses the relaxed angle so the cue flips
    // promptly from "turn" to "hold".
    val turnedMagnitude = kotlin.math.abs(tracking.yawDegrees) >= PROFILE_YAW_RELAX_DEG
    when (stage) {
        CaptureWorkflowStage.LeftProfilePrompt,
        CaptureWorkflowStage.LeftProfileCapture,
        -> return if (!turnedMagnitude) "Turn your face to the right." else "Hold still."
        CaptureWorkflowStage.RightProfilePrompt,
        CaptureWorkflowStage.RightProfileCapture,
        -> {
            val oppositeSign = firstProfileYawSign == 0 ||
                (tracking.yawDegrees >= 0f) != (firstProfileYawSign == 1)
            val turnedRightPose = turnedMagnitude && oppositeSign
            return if (!turnedRightPose) "Turn your face to the left." else "Hold still."
        }
        else -> Unit
    }

    val leftEye = tracking.leftEyeOpenProbability ?: 1f
    val rightEye = tracking.rightEyeOpenProbability ?: 1f
    if (leftEye < 0.45f || rightEye < 0.45f) return "Open your eyes and clear glasses or hair from your face."
    if (tracking.possibleObstruction) return "Keep hair and headwear away from your face."

    val alignment = tracking.toAlignmentState()
    if (alignment.distanceDelta < -0.23f) return "Move closer to the camera."
    if (alignment.distanceDelta > 0.20f) return "Move back a little."

    val center = tracking.faceBounds?.let { (it.left + it.right) / 2f }
    if (center != null && tracking.viewWidth > 0) {
        val offset = kotlin.math.abs(center / tracking.viewWidth - 0.5f)
        if (offset > 0.18f) return "Center your face in the frame."
    }

    if (kotlin.math.abs(tracking.yawDegrees) > 10f) return "Face the camera straight on."
    if (kotlin.math.abs(tracking.rollDegrees) > 8f) return "Keep your head level."

    return "Looks good. Hold still."
}

@Composable
private fun CaptureScreen(
    baselineLux: Float,
    autodermCloudEnabled: Boolean,
    autodermApiConfigured: Boolean,
    onAutodermCloudEnabledChange: (Boolean) -> Unit,
    onCreateImageFile: () -> File,
    onRecord: (
        Float,
        AlignmentState,
        File,
        FitzpatrickGroup,
        CapturePose,
        () -> Unit,
        (Throwable) -> Unit,
    ) -> Unit,
    onClearHistory: () -> Unit,
    onSuggestPersonaFromImage: (File) -> Unit,
    onSessionComplete: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasCameraPermission = it
    }
    var lux by remember { mutableFloatStateOf(baselineLux) }
    val lightMeter = remember { LightMeter(context) }
    var faceTracking by remember { mutableStateOf(FaceTrackingState()) }
    val alignment = faceTracking.toAlignmentState()
    val luxGate = LuxGate(baselineLux = baselineLux, currentLux = lux)
    val cameraController = remember(context) {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS)
            previewResolutionSelector = CameraStreamResolutionSelector
            imageCaptureResolutionSelector = CameraStreamResolutionSelector
            imageAnalysisResolutionSelector = CameraStreamResolutionSelector
        }
    }
    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            controller = cameraController
        }
    }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    var captureInProgress by remember { mutableStateOf(false) }
    var captureStatus by remember { mutableStateOf<String?>(null) }
    var saveTriggered by remember { mutableStateOf(false) }
    var workflowStage by remember { mutableStateOf(CaptureWorkflowStage.Setup) }
    var stageSecondsRemaining by remember { mutableIntStateOf(0) }
    // Sign of the yaw captured for the first (left) profile; the second (right)
    // profile must be the opposite direction. Sign-agnostic so it works whatever
    // the front-camera yaw convention is.
    var firstProfileYawSign by remember { mutableIntStateOf(0) }
    // Frames captured this session, held for review before any analysis/DB write.
    val pendingCaptures = remember { mutableStateListOf<PendingCapture>() }
    var reviewMode by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    var clearingHistory by remember { mutableStateOf(false) }
    val faceMlKitAnalyzer = remember {
        FaceMlKitAnalyzer(
            previewViewProvider = { previewView },
            stage = { workflowStage },
            onFaceTracking = { faceTracking = it },
            resultExecutor = mainExecutor,
        )
    }
    val latestFaceTracking by rememberUpdatedState(faceTracking)
    val latestLux by rememberUpdatedState(lux)
    val announce = rememberSpeechAnnouncer()
    val scope = rememberCoroutineScope()
    val (playTick, playCompleteBeep) = rememberScanTonePlayer()
    var lastTickSecond by remember { mutableIntStateOf(-1) }

    val spokenCue = if (saveTriggered) {
        ""
    } else {
        spokenGuidanceFor(
            stage = workflowStage,
            tracking = faceTracking,
            luxAcceptable = luxGate.isAcceptable,
            firstProfileYawSign = firstProfileYawSign,
        )
    }
    val latestSpokenCue by rememberUpdatedState(spokenCue)
    LaunchedEffect(Unit) {
        var lastCue = ""
        while (true) {
            val cue = latestSpokenCue
            when {
                cue.isBlank() -> lastCue = ""
                cue != lastCue -> {
                    announce(cue)
                    lastCue = cue
                }
            }
            delay(1_500)
        }
    }
    LaunchedEffect(captureStatus) {
        when (captureStatus) {
            "All scans saved" -> announce("All scans complete.")
            "Review your captures, then confirm." ->
                announce("All poses captured. Review your photos, then confirm or retake.")
            "Scan failed", "Capture failed" -> announce("Scan failed. Please try again.")
        }
    }
    LaunchedEffect(workflowStage) {
        lastTickSecond = -1
    }
    LaunchedEffect(workflowStage, stageSecondsRemaining) {
        val timedStage = when (workflowStage) {
            CaptureWorkflowStage.FrontVertical,
            CaptureWorkflowStage.FrontHorizontal,
            CaptureWorkflowStage.LeftProfilePrompt,
            CaptureWorkflowStage.RightProfilePrompt,
            -> true
            else -> false
        }
        if (timedStage && stageSecondsRemaining > 0 && stageSecondsRemaining != lastTickSecond) {
            lastTickSecond = stageSecondsRemaining
            playTick()
        }
    }

    fun captureReadiness(): Pair<Boolean, String> {
        val gate = LuxGate(baselineLux = baselineLux, currentLux = latestLux)
        val captureAlignment = latestFaceTracking.toAlignmentState()
        return when {
            !latestFaceTracking.detected ->
                false to "Position your face inside the guide."
            !gate.isAcceptable ->
                false to "Match your Day 0 light (within 20% of baseline lux)."
            latestFaceTracking.possibleObstruction ->
                false to "Remove glasses/headwear and keep hair away from the face."
            !latestFaceTracking.isStraightEnough ->
                false to "Face the camera straight with head level."
            captureAlignment.distanceDelta < -0.23f ->
                false to "Move a little closer to the camera."
            captureAlignment.distanceDelta > 0.20f ->
                false to "Ease back slightly from the camera."
            !captureAlignment.isReasonable ->
                false to "Center your face in the guide and hold steady."
            else -> true to ""
        }
    }

    fun finishCapture(
        pose: CapturePose,
        force: Boolean = false,
        onSaved: () -> Unit,
    ) {
        if (saveTriggered) return
        if (!force) {
            val (ready, reason) = captureReadiness()
            if (!ready) {
                captureStatus = reason
                return
            }
        }

        saveTriggered = true
        captureStatus = "Saving ${pose.spokenLabel} frame…"
        val imageFile = onCreateImageFile()
        val captureAlignment = latestFaceTracking.toAlignmentState()
        val captureFitzpatrick = latestFaceTracking.skinToneCategory
        val outputOptions = ImageCapture.OutputFileOptions.Builder(imageFile).build()
        cameraController.takePicture(
            outputOptions,
            mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    // Image is persisted to private storage now; analysis/DB write
                    // is deferred until the user confirms the captures in review.
                    pendingCaptures.add(
                        PendingCapture(
                            pose = pose,
                            file = imageFile,
                            alignment = captureAlignment,
                            lux = latestLux,
                            fitzpatrick = captureFitzpatrick,
                        ),
                    )
                    if (pose == CapturePose.Front) {
                        onSuggestPersonaFromImage(imageFile)
                    }
                    playCompleteBeep()
                    captureStatus = "${pose.shortLabel} captured."
                    saveTriggered = false
                    onSaved()
                }

                override fun onError(exception: ImageCaptureException) {
                    captureInProgress = false
                    saveTriggered = false
                    workflowStage = CaptureWorkflowStage.Setup
                    imageFile.delete()
                    captureStatus = "Capture failed"
                    Log.w("DermaTrackCamera", "Unable to save private scan image", exception)
                }
            },
        )
    }

    fun confirmCaptures() {
        if (confirming) return
        val items = pendingCaptures.toList()
        if (items.isEmpty()) {
            reviewMode = false
            return
        }
        confirming = true
        captureStatus = "Analyzing ${items.size} captures…"
        var settled = 0
        val finishIfDone = {
            settled += 1
            if (settled == items.size) {
                pendingCaptures.clear()
                confirming = false
                reviewMode = false
                captureStatus = "All scans saved"
                onSessionComplete()
            }
        }
        items.forEach { capture ->
            onRecord(
                capture.lux,
                capture.alignment,
                capture.file,
                capture.fitzpatrick,
                capture.pose,
                { finishIfDone() },
                {
                    Log.w("DermaTrackCamera", "Unable to record ${capture.pose} scan", it)
                    finishIfDone()
                },
            )
        }
    }

    fun retakeCaptures() {
        if (confirming) return
        pendingCaptures.forEach { it.file.delete() }
        pendingCaptures.clear()
        reviewMode = false
        captureInProgress = false
        workflowStage = CaptureWorkflowStage.Setup
        captureStatus = "Let's recapture. Tap Start Guided Scan."
    }

    fun clearHistory() {
        if (confirming || clearingHistory) return
        clearingHistory = true
        pendingCaptures.forEach { it.file.delete() }
        pendingCaptures.clear()
        onClearHistory()
        reviewMode = false
        captureInProgress = false
        workflowStage = CaptureWorkflowStage.Setup
        captureStatus = "Selected persona history cleared. Start a new guided scan."
        scope.launch {
            delay(500)
            clearingHistory = false
        }
    }

    fun transitionTo(nextStage: CaptureWorkflowStage, status: String) {
        scope.launch {
            stageSecondsRemaining = SCAN_TRANSITION_SECONDS
            repeat(SCAN_TRANSITION_SECONDS) {
                captureStatus = "$status in ${stageSecondsRemaining}s"
                delay(1_000)
                stageSecondsRemaining -= 1
            }
            stageSecondsRemaining = 0
            workflowStage = nextStage
        }
    }

    val currentScanDirection = when (workflowStage) {
        CaptureWorkflowStage.FrontVertical,
        CaptureWorkflowStage.LeftProfileCapture,
        CaptureWorkflowStage.RightProfileCapture,
        -> ScanDirection.TopDown
        CaptureWorkflowStage.FrontHorizontal,
        CaptureWorkflowStage.FrontCapture,
        -> ScanDirection.LeftRight
        else -> ScanDirection.None
    }
    val isSweeping = captureInProgress && when (workflowStage) {
        CaptureWorkflowStage.FrontVertical,
        CaptureWorkflowStage.FrontHorizontal,
        CaptureWorkflowStage.FrontCapture,
        CaptureWorkflowStage.LeftProfileCapture,
        CaptureWorkflowStage.RightProfileCapture,
        -> true
        else -> false
    }

    LaunchedEffect(Unit) {
        lightMeter.observeLux().collect { if (it > 0f) lux = it }
    }

    DisposableEffect(hasCameraPermission, lifecycleOwner) {
        if (!hasCameraPermission) {
            onDispose { }
        } else {
            cameraController.bindToLifecycle(lifecycleOwner)
            cameraController.setImageAnalysisAnalyzer(analysisExecutor, faceMlKitAnalyzer.analyzer)
            onDispose {
                cameraController.clearImageAnalysisAnalyzer()
                cameraController.unbind()
                faceMlKitAnalyzer.close()
                analysisExecutor.shutdown()
            }
        }
    }

    LaunchedEffect(workflowStage) {
        when (workflowStage) {
            CaptureWorkflowStage.Setup -> Unit
            CaptureWorkflowStage.MeshLock -> {
                captureStatus = "Mesh calibration: remove glasses/headwear, sit straight, face well-lit."
                while (workflowStage == CaptureWorkflowStage.MeshLock) {
                    if (latestFaceTracking.isStraightEnough && !latestFaceTracking.possibleObstruction) {
                        stageSecondsRemaining = 3
                        repeat(3) {
                            delay(1_000)
                            stageSecondsRemaining -= 1
                        }
                        workflowStage = CaptureWorkflowStage.FrontVertical
                    } else {
                        delay(350)
                    }
                }
            }
            CaptureWorkflowStage.FrontVertical -> {
                captureStatus = "Front pass 1 of 2: top-to-bottom sweep. Hold still and look straight."
                stageSecondsRemaining = FRONT_PASS_SECONDS
                repeat(FRONT_PASS_SECONDS) {
                    delay(1_000)
                    stageSecondsRemaining -= 1
                }
                workflowStage = CaptureWorkflowStage.FrontHorizontal
            }
            CaptureWorkflowStage.FrontHorizontal -> {
                captureStatus = "Front pass 2 of 2: left-to-right sweep. Keep light even across the face."
                stageSecondsRemaining = FRONT_PASS_SECONDS
                repeat(FRONT_PASS_SECONDS) {
                    delay(1_000)
                    stageSecondsRemaining -= 1
                }
                workflowStage = CaptureWorkflowStage.FrontCapture
                captureStatus = "Hold still — saving the front frame when pose and light are ready."
            }
            CaptureWorkflowStage.FrontCapture -> {
                var waitedMs = 0L
                val maxWaitMs = FRONT_TOTAL_SECONDS * 1_000L
                while (workflowStage == CaptureWorkflowStage.FrontCapture) {
                    val (ready, reason) = captureReadiness()
                    val timedOut = waitedMs >= maxWaitMs
                    val canBestEffort = timedOut &&
                        latestFaceTracking.detected &&
                        !latestFaceTracking.possibleObstruction &&
                        LuxGate(baselineLux = baselineLux, currentLux = latestLux).isAcceptable
                    if (ready || canBestEffort) {
                        finishCapture(pose = CapturePose.Front, force = canBestEffort && !ready) {
                            announce("Front captured.")
                            transitionTo(
                                nextStage = CaptureWorkflowStage.LeftProfilePrompt,
                                status = "Left profile starts",
                            )
                        }
                        break
                    }
                    captureStatus = reason
                    stageSecondsRemaining =
                        (((maxWaitMs - waitedMs) + 999L) / 1000L).toInt().coerceAtLeast(0)
                    delay(350)
                    waitedMs += 350
                }
            }
            CaptureWorkflowStage.LeftProfilePrompt -> {
                // Left profile = user turns their face to the right (first profile turn).
                captureStatus = "Left profile: slowly turn your face to the right and hold."
                var stableMs = 0L
                var waitedMs = 0L
                var sawNeutral = false
                while (workflowStage == CaptureWorkflowStage.LeftProfilePrompt) {
                    val yaw = latestFaceTracking.yawDegrees
                    val luxOk = LuxGate(baselineLux = baselineLux, currentLux = latestLux).isAcceptable
                    if (!sawNeutral && latestFaceTracking.detected &&
                        kotlin.math.abs(yaw) < PROFILE_NEUTRAL_DEG
                    ) {
                        sawNeutral = true
                    }
                    val trigger = if (waitedMs >= PROFILE_MAX_WAIT_MS) {
                        PROFILE_YAW_RELAX_DEG
                    } else {
                        PROFILE_YAW_TRIGGER_DEG
                    }
                    val turned = sawNeutral && latestFaceTracking.detected &&
                        kotlin.math.abs(yaw) >= trigger
                    Log.d(
                        "DermaTrackProfile",
                        "left yaw=$yaw detected=${latestFaceTracking.detected} neutral=$sawNeutral " +
                            "trigger=$trigger turned=$turned lux=$luxOk stable=$stableMs",
                    )
                    if (turned && luxOk) stableMs += PROFILE_POLL_MS else stableMs = 0L
                    val held = stableMs >= PROFILE_HOLD_MS
                    val positioningDone = waitedMs >= PROFILE_POSITIONING_MS
                    if (held && positioningDone) {
                        firstProfileYawSign = if (yaw >= 0f) 1 else -1
                        stageSecondsRemaining = 0
                        workflowStage = CaptureWorkflowStage.LeftProfileCapture
                        break
                    }
                    if (!positioningDone) {
                        captureStatus = "Left profile: keep positioning... ${((PROFILE_POSITIONING_MS - waitedMs + 999L) / 1000L).toInt()}s"
                    } else if (turned) {
                        captureStatus = "Hold the left profile…"
                    } else if (sawNeutral) {
                        captureStatus = "Turn your face to the right and hold."
                    } else {
                        captureStatus = "Face the camera for a moment, then turn right."
                    }
                    val holdRemaining = if (stableMs > 0L) {
                        (((PROFILE_HOLD_MS - stableMs) + 999L) / 1000L).toInt().coerceAtLeast(0)
                    } else 0
                    val positioningRemaining =
                        (((PROFILE_POSITIONING_MS - waitedMs) + 999L) / 1000L).toInt().coerceAtLeast(0)
                    stageSecondsRemaining = maxOf(holdRemaining, positioningRemaining)
                    delay(PROFILE_POLL_MS)
                    waitedMs += PROFILE_POLL_MS
                }
            }
            CaptureWorkflowStage.LeftProfileCapture -> {
                captureStatus = "Hold the left profile still — capturing."
                finishCapture(pose = CapturePose.LeftProfile, force = true) {
                    announce("Left profile captured.")
                    transitionTo(
                        nextStage = CaptureWorkflowStage.RightProfilePrompt,
                        status = "Right profile starts",
                    )
                    scope.launch {
                        delay((SCAN_TRANSITION_SECONDS * 1_000L) + 250L)
                        announce("Turn your face to the left.")
                    }
                }
            }
            CaptureWorkflowStage.RightProfilePrompt -> {
                // Right profile = user turns their face to the left: must first return
                // near-center, then turn the OPPOSITE way from the left profile, so the
                // residual left-profile turn can't immediately re-trigger a capture.
                captureStatus = "Right profile: turn your face to the left and hold."
                var stableMs = 0L
                var waitedMs = 0L
                while (workflowStage == CaptureWorkflowStage.RightProfilePrompt) {
                    val yaw = latestFaceTracking.yawDegrees
                    val luxOk = LuxGate(baselineLux = baselineLux, currentLux = latestLux).isAcceptable
                    // The opposite-direction check (vs. the left profile's turn) already
                    // rejects residual left-profile yaw, so no return-to-center is needed.
                    val oppositeSign = firstProfileYawSign == 0 ||
                        (yaw >= 0f) != (firstProfileYawSign == 1)
                    val trigger = if (waitedMs >= PROFILE_MAX_WAIT_MS) {
                        PROFILE_YAW_RELAX_DEG
                    } else {
                        PROFILE_YAW_TRIGGER_DEG
                    }
                    val turned = latestFaceTracking.detected &&
                        kotlin.math.abs(yaw) >= trigger &&
                        oppositeSign
                    Log.d(
                        "DermaTrackProfile",
                        "right yaw=$yaw detected=${latestFaceTracking.detected} " +
                            "opp=$oppositeSign trigger=$trigger turned=$turned lux=$luxOk stable=$stableMs",
                    )
                    if (turned && luxOk) stableMs += PROFILE_POLL_MS else stableMs = 0L
                    val held = stableMs >= PROFILE_HOLD_MS
                    val positioningDone = waitedMs >= PROFILE_POSITIONING_MS
                    if (held && positioningDone) {
                        stageSecondsRemaining = 0
                        workflowStage = CaptureWorkflowStage.RightProfileCapture
                        break
                    }
                    captureStatus = if (!positioningDone) {
                        "Right profile: keep positioning... ${((PROFILE_POSITIONING_MS - waitedMs + 999L) / 1000L).toInt()}s"
                    } else if (turned) {
                        "Hold the right profile…"
                    } else {
                        "Turn your face to the left and hold."
                    }
                    val holdRemaining = if (stableMs > 0L) {
                        (((PROFILE_HOLD_MS - stableMs) + 999L) / 1000L).toInt().coerceAtLeast(0)
                    } else 0
                    val positioningRemaining =
                        (((PROFILE_POSITIONING_MS - waitedMs) + 999L) / 1000L).toInt().coerceAtLeast(0)
                    stageSecondsRemaining = maxOf(holdRemaining, positioningRemaining)
                    delay(PROFILE_POLL_MS)
                    waitedMs += PROFILE_POLL_MS
                }
            }
            CaptureWorkflowStage.RightProfileCapture -> {
                captureStatus = "Hold the right profile still — capturing."
                finishCapture(pose = CapturePose.RightProfile, force = true) {
                    announce("Right profile captured.")
                    captureInProgress = false
                    workflowStage = CaptureWorkflowStage.Setup
                    reviewMode = true
                    captureStatus = "Review your captures, then confirm."
                }
            }
        }
    }

    val scrollState = rememberScrollState()

    if (reviewMode) {
        CaptureReviewSection(
            captures = pendingCaptures.toList(),
            confirming = confirming,
            clearingHistory = clearingHistory,
            onConfirm = { confirmCaptures() },
            onRetake = { retakeCaptures() },
            onClearHistory = { clearHistory() },
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(start = 16.dp, top = 16.dp, end = 48.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .background(Color(0xFF121816), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                CaptureCameraViewport(
                    hasCameraPermission = hasCameraPermission,
                    previewView = previewView,
                )
                FaceMeshOverlay(
                    faceTracking = faceTracking,
                    scanDirection = currentScanDirection,
                    isScanning = isSweeping,
                )
            }
            CaptureInstructionPanel(
                stage = workflowStage,
                faceTracking = faceTracking,
                secondsRemaining = stageSecondsRemaining,
                status = captureStatus,
            )
            Text(
                text = "Tone thresholds use Fitzpatrick V defaults until auto-detection ships. A full session saves three scans: front, left profile, and right profile.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AutodermCloudOptIn(
                enabled = autodermCloudEnabled,
                apiConfigured = autodermApiConfigured,
                onEnabledChange = onAutodermCloudEnabledChange,
            )
            val isFrontStage = workflowStage == CaptureWorkflowStage.MeshLock ||
                workflowStage == CaptureWorkflowStage.FrontVertical ||
                workflowStage == CaptureWorkflowStage.FrontHorizontal ||
                workflowStage == CaptureWorkflowStage.FrontCapture
            val isProfileStage = workflowStage == CaptureWorkflowStage.LeftProfilePrompt ||
                workflowStage == CaptureWorkflowStage.LeftProfileCapture ||
                workflowStage == CaptureWorkflowStage.RightProfilePrompt ||
                workflowStage == CaptureWorkflowStage.RightProfileCapture
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (hasCameraPermission) "Camera Ready" else "Grant Camera")
                }
                Button(
                    onClick = {
                        when (workflowStage) {
                            CaptureWorkflowStage.Setup -> {
                                captureInProgress = true
                                saveTriggered = false
                                firstProfileYawSign = 0
                                workflowStage = CaptureWorkflowStage.MeshLock
                            }
                            // Manual override: the user decides the front frame is good and
                            // captures immediately, bypassing the auto-capture wait/timeout.
                            // Profile poses auto-capture (screen not visible when turned).
                            CaptureWorkflowStage.MeshLock,
                            CaptureWorkflowStage.FrontVertical,
                            CaptureWorkflowStage.FrontHorizontal,
                            CaptureWorkflowStage.FrontCapture,
                            -> finishCapture(pose = CapturePose.Front, force = true) {
                                announce("Front captured.")
                                transitionTo(
                                    nextStage = CaptureWorkflowStage.LeftProfilePrompt,
                                    status = "Left profile starts",
                                )
                            }
                            else -> Unit
                        }
                    },
                    enabled = hasCameraPermission && when (workflowStage) {
                        CaptureWorkflowStage.Setup -> luxGate.isAcceptable
                        else -> isFrontStage &&
                            !saveTriggered &&
                            faceTracking.detected &&
                            luxGate.isAcceptable
                    },
                ) {
                    Text(
                        when {
                            workflowStage == CaptureWorkflowStage.Setup -> "Start Guided Scan"
                            saveTriggered -> "Saving…"
                            isProfileStage -> "Auto-capturing…"
                            isFrontStage -> "Capture Front"
                            else -> "Scanning…"
                        },
                    )
                }
            }
            if (isFrontStage) {
                Text(
                    text = "Front capture runs automatically when your pose is steady — or tap Capture Front once it looks good. Then you'll be guided through left and right profiles (those auto-capture since the screen isn't visible when you turn).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isProfileStage) {
                Text(
                    text = "Profile capture is automatic — follow the spoken prompt to turn, then hold steady.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CaptureGatePanel(luxGate = luxGate, alignment = alignment)
        }
        CaptureScrollButtons(
            scrollState = scrollState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp),
        )
    }
}

@Composable
private fun CaptureReviewSection(
    captures: List<PendingCapture>,
    confirming: Boolean,
    clearingHistory: Boolean,
    onConfirm: () -> Unit,
    onRetake: () -> Unit,
    onClearHistory: () -> Unit,
) {
    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(start = 16.dp, top = 16.dp, end = 48.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Review your captures",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Check that each pose is framed and in focus. Confirm to analyze and save them, or retake the whole session.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            captures.forEach { capture ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = capture.pose.shortLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    CapturedImage(
                        file = capture.file,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF121816)),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onRetake, enabled = !confirming && !clearingHistory) {
                    Text("Retake all")
                }
                Button(onClick = onConfirm, enabled = !confirming && !clearingHistory) {
                    Text(if (confirming) "Analyzing…" else "Looks good — Analyze")
                }
            }
            OutlinedButton(
                onClick = onClearHistory,
                enabled = !confirming && !clearingHistory,
            ) {
                Text(if (clearingHistory) "Clearing history..." else "Clear history")
            }
            Text(
                text = "Images are stored only in this app's private storage and attached to your scan history.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CaptureScrollButtons(
            scrollState = scrollState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp),
        )
    }
}

@Composable
private fun CapturedImage(
    file: File,
    modifier: Modifier = Modifier,
) {
    val path = file.absolutePath
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) { loadScaledBitmap(path, maxDimension = 1024) }
    }
    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "Loading…",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Decode a downscaled, EXIF-rotated bitmap from a private capture file for display. */
private fun loadScaledBitmap(path: String, maxDimension: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    val largest = maxOf(bounds.outWidth, bounds.outHeight)
    while (largest / sample > maxDimension) sample *= 2
    val decoded = BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply { inSampleSize = sample },
    ) ?: return null
    val rotation = runCatching {
        when (ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }.getOrDefault(0f)
    var oriented = if (rotation != 0f) {
        val matrix = Matrix().apply { postRotate(rotation) }
        Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    } else {
        decoded
    }
    // Some emulator/front-camera captures arrive without reliable EXIF orientation.
    // For face portraits shown in UI cards, normalize to upright portrait.
    // On this capture pipeline, landscape frames need a 90-degree right rotation.
    if (oriented.width > oriented.height) {
        val portraitMatrix = Matrix().apply { postRotate(-90f) }
        oriented = Bitmap.createBitmap(
            oriented,
            0,
            0,
            oriented.width,
            oriented.height,
            portraitMatrix,
            true,
        )
    }
    val masked = applyFaceMask(oriented)
    if (masked !== oriented) oriented.recycle()
    return masked.asImageBitmap()
}

private fun applyFaceMask(source: Bitmap): Bitmap {
    val width = source.width
    val height = source.height
    if (width <= 0 || height <= 0) return source
    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Slightly inset oval keeps facial region and removes most background.
    val insetX = width * 0.08f
    val insetTop = height * 0.06f
    val insetBottom = height * 0.14f
    val faceOval = RectF(insetX, insetTop, width - insetX, height - insetBottom)
    canvas.drawOval(faceOval, paint)

    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    canvas.drawBitmap(source, 0f, 0f, paint)
    paint.xfermode = null
    return output
}

@Composable
private fun CaptureCameraViewport(
    hasCameraPermission: Boolean,
    previewView: PreviewView,
) {
    if (!hasCameraPermission) {
        Text(
            text = "Camera permission required",
            color = Color.White.copy(alpha = 0.78f),
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { previewView },
    )
}

@Composable
private fun CaptureScrollButtons(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledIconButton(
            onClick = {
                scope.launch {
                    scrollState.animateScrollBy(-240f)
                }
            },
            enabled = scrollState.value > 0,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Scroll up")
        }
        FilledIconButton(
            onClick = {
                scope.launch {
                    scrollState.animateScrollBy(240f)
                }
            },
            enabled = scrollState.value < scrollState.maxValue,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Scroll down")
        }
    }
}

@Composable
private fun CaptureInstructionPanel(
    stage: CaptureWorkflowStage,
    faceTracking: FaceTrackingState,
    secondsRemaining: Int,
    status: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
            Text(stage.title(), style = MaterialTheme.typography.titleMedium)
            Text(
                text = status ?: stage.defaultInstruction(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (stage == CaptureWorkflowStage.Setup) {
                Text(
                    text = "Validated capture controls: frontal baseline, neutral expression, matched distance, even light, plain background.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (secondsRemaining > 0) {
                Text(
                    text = "$secondsRemaining sec remaining",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            faceTracking.guidance.forEach { guidance ->
                Text(
                    text = guidance,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (faceTracking.possibleObstruction) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (faceTracking.detected) {
                Text(
                    text = "Yaw %.1f° · Roll %.1f° · Mesh points ${faceTracking.meshPoints.size}".format(
                        faceTracking.yawDegrees,
                        faceTracking.rollDegrees,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
    }
}

private fun CaptureWorkflowStage.title(): String = when (this) {
    CaptureWorkflowStage.Setup -> "Guided Face Scan"
    CaptureWorkflowStage.MeshLock -> "Face Mesh Lock"
    CaptureWorkflowStage.FrontVertical -> "Front Pass 1 · Vertical"
    CaptureWorkflowStage.FrontHorizontal -> "Front Pass 2 · Horizontal"
    CaptureWorkflowStage.FrontCapture -> "Front Capture"
    CaptureWorkflowStage.LeftProfilePrompt -> "Left Profile · Turn Right"
    CaptureWorkflowStage.LeftProfileCapture -> "Left Profile Capture"
    CaptureWorkflowStage.RightProfilePrompt -> "Right Profile · Turn Left"
    CaptureWorkflowStage.RightProfileCapture -> "Right Profile Capture"
}

private fun CaptureWorkflowStage.defaultInstruction(): String = when (this) {
    CaptureWorkflowStage.Setup -> "Remove glasses/headwear, sit straight, and keep light falling evenly on face skin."
    CaptureWorkflowStage.MeshLock -> "Hold still while the face mesh locks onto your features."
    CaptureWorkflowStage.FrontVertical -> "Hold straight. The scanner is sweeping from forehead to chin."
    CaptureWorkflowStage.FrontHorizontal -> "Hold straight. The scanner is sweeping left to right."
    CaptureWorkflowStage.FrontCapture -> "Saving the front frame when pose and light are ready."
    CaptureWorkflowStage.LeftProfilePrompt -> "Slowly turn your face to the right and hold for the left profile."
    CaptureWorkflowStage.LeftProfileCapture -> "Hold the left profile still — capturing."
    CaptureWorkflowStage.RightProfilePrompt -> "Slowly turn your face to the left and hold for the right profile."
    CaptureWorkflowStage.RightProfileCapture -> "Hold the right profile still — capturing."
}

@Composable
private fun FaceMeshOverlay(
    faceTracking: FaceTrackingState,
    scanDirection: ScanDirection,
    isScanning: Boolean,
) {
    val transition = rememberInfiniteTransition(label = "face-scan-overlay")
    val scanProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "scan-progress",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.40f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "mesh-pulse",
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val guideColor = if (faceTracking.detected) Color(0xFF73D6B5) else Color(0xFFE2B05B)
        val viewWidth = faceTracking.viewWidth
        val viewHeight = faceTracking.viewHeight
        val bounds = faceTracking.faceBounds?.let { viewBounds ->
            if (viewWidth <= 0 || viewHeight <= 0) null else {
                RectF(
                    viewBounds.left / viewWidth,
                    viewBounds.top / viewHeight,
                    viewBounds.right / viewWidth,
                    viewBounds.bottom / viewHeight,
                )
            }
        }
        if (bounds == null) {
            val faceWidth = size.width * 0.58f
            val faceHeight = size.height * 0.66f
            drawOval(
                color = guideColor.copy(alpha = 0.52f),
                topLeft = Offset((size.width - faceWidth) / 2f, size.height * 0.15f),
                size = Size(faceWidth, faceHeight),
                style = Stroke(width = 5f),
            )
            return@Canvas
        }

        val left = bounds.left * size.width
        val top = bounds.top * size.height
        val right = bounds.right * size.width
        val bottom = bounds.bottom * size.height
        val width = (right - left).coerceAtLeast(1f)
        val height = (bottom - top).coerceAtLeast(1f)

        // ML Kit's FACE contour stops at the hairline, so the upper forehead and
        // scalp fall outside it. Draw a full-head coverage oval that extends above
        // the detected face box so the guide visibly covers the whole head.
        val headPadX = width * 0.10f
        val headExtendUp = height * 0.55f
        val headLeft = (left - headPadX).coerceAtLeast(0f)
        val headRight = (right + headPadX).coerceAtMost(size.width)
        val headTop = (top - headExtendUp).coerceAtLeast(0f)
        drawOval(
            color = guideColor.copy(alpha = 0.55f),
            topLeft = Offset(headLeft, headTop),
            size = Size(headRight - headLeft, bottom - headTop),
            style = Stroke(width = 4f),
        )

        // Draw Clinical Reticle (Crosshairs)
        val reticleColor = if (faceTracking.isStraightEnough) Color(0xFF73D6B5) else Color(0xFFE2B05B).copy(alpha = 0.6f)
        val centerX = left + (width / 2f)
        val centerY = top + (height / 2f)
        drawLine(reticleColor, Offset(centerX - 40f, centerY), Offset(centerX + 40f, centerY), strokeWidth = 2f)
        drawLine(reticleColor, Offset(centerX, centerY - 40f), Offset(centerX, centerY + 40f), strokeWidth = 2f)
        drawCircle(reticleColor, radius = 50f, center = Offset(centerX, centerY), style = Stroke(width = 1.5f))

        val outline = faceTracking.faceOutline.map { it.toCanvasOffset(size.width, size.height) }
        if (outline.size >= 4) {
            drawPath(
                path = outline.toClosedPath(),
                color = guideColor.copy(alpha = 0.88f),
                style = Stroke(width = 4.5f),
            )
        } else {
            drawRect(
                color = guideColor.copy(alpha = 0.70f),
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = Stroke(width = 3f),
            )
        }

        val gridColor = guideColor.copy(alpha = 0.16f)
        faceTracking.contourLines.forEach { contour ->
            val points = contour.map { it.toCanvasOffset(size.width, size.height) }
            if (points.size > 1) {
                drawPath(
                    path = points.toOpenPath(),
                    color = gridColor,
                    style = Stroke(width = 1.5f),
                )
            }
        }

        faceTracking.meshPoints.forEachIndexed { index, point ->
            val mapped = point.toCanvasOffset(size.width, size.height)
            val x = mapped.x
            val y = mapped.y
            drawCircle(
                color = guideColor.copy(alpha = if (isScanning) pulse else 0.45f),
                radius = if (index % 5 == 0) 2.6f else 1.7f,
                center = Offset(x, y),
            )
        }

        if (isScanning) {
            val scanPolygon = outline.takeIf { it.size >= 4 } ?: listOf(
                Offset(left, top),
                Offset(right, top),
                Offset(right, bottom),
                Offset(left, bottom),
            )
            when (scanDirection) {
                ScanDirection.TopDown -> {
                    repeat(6) { index ->
                        val trail = index * 0.035f
                        val yProgress = (scanProgress - trail).floorModOne()
                        val y = top + (height * yProgress)
                        drawHorizontalSegmentsInsidePolygon(
                            polygon = scanPolygon,
                            y = y,
                            color = Color(0xFF73D6B5).copy(alpha = 0.95f - (index * 0.12f)),
                            strokeWidth = if (index == 0) 5f else 2.5f,
                        )
                    }
                }
                ScanDirection.LeftRight -> {
                    repeat(6) { index ->
                        val trail = index * 0.035f
                        val xProgress = (scanProgress - trail).floorModOne()
                        val x = left + (width * xProgress)
                        drawVerticalSegmentsInsidePolygon(
                            polygon = scanPolygon,
                            x = x,
                            color = Color(0xFF73D6B5).copy(alpha = 0.95f - (index * 0.12f)),
                            strokeWidth = if (index == 0) 5f else 2.5f,
                        )
                    }
                }
                ScanDirection.None -> Unit
            }
        }
    }
}

private fun List<Offset>.toOpenPath(): Path = Path().also { path ->
    firstOrNull()?.let { first ->
        path.moveTo(first.x, first.y)
        drop(1).forEach { point -> path.lineTo(point.x, point.y) }
    }
}

private fun List<Offset>.toClosedPath(): Path = toOpenPath().also { it.close() }

private fun Float.floorModOne(): Float {
    val remainder = this % 1f
    return if (remainder < 0f) remainder + 1f else remainder
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHorizontalSegmentsInsidePolygon(
    polygon: List<Offset>,
    y: Float,
    color: Color,
    strokeWidth: Float,
) {
    val intersections = mutableListOf<Float>()
    polygon.forEachIndexed { index, a ->
        val b = polygon[(index + 1) % polygon.size]
        if ((a.y <= y && b.y > y) || (b.y <= y && a.y > y)) {
            val t = (y - a.y) / (b.y - a.y)
            intersections += a.x + (t * (b.x - a.x))
        }
    }
    intersections.sorted().chunked(2).forEach { pair ->
        if (pair.size == 2 && abs(pair[1] - pair[0]) > 1f) {
            drawLine(
                color = color,
                start = Offset(pair[0], y),
                end = Offset(pair[1], y),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawVerticalSegmentsInsidePolygon(
    polygon: List<Offset>,
    x: Float,
    color: Color,
    strokeWidth: Float,
) {
    val intersections = mutableListOf<Float>()
    polygon.forEachIndexed { index, a ->
        val b = polygon[(index + 1) % polygon.size]
        if ((a.x <= x && b.x > x) || (b.x <= x && a.x > x)) {
            val t = (x - a.x) / (b.x - a.x)
            intersections += a.y + (t * (b.y - a.y))
        }
    }
    intersections.sorted().chunked(2).forEach { pair ->
        if (pair.size == 2 && abs(pair[1] - pair[0]) > 1f) {
            drawLine(
                color = color,
                start = Offset(x, pair[0]),
                end = Offset(x, pair[1]),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun NormalizedPoint.toCanvasOffset(canvasWidth: Float, canvasHeight: Float): Offset =
    Offset(x = x * canvasWidth, y = y * canvasHeight)

@Composable
private fun CaptureGatePanel(luxGate: LuxGate, alignment: AlignmentState) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Capture Standardization", style = MaterialTheme.typography.titleMedium)
            GateRow("Light delta", "${(luxGate.deviationPercent * 100).toInt()}%", luxGate.isAcceptable)
            GateRow("Head alignment", "${(alignment.score * 100).toInt()}%", alignment.isAcceptable)
            GateRow("Tilt", "%.1f°".format(alignment.headTiltDegrees), kotlin.math.abs(alignment.headTiltDegrees) <= 6f)
        }
    }
}

@Composable
private fun GateRow(label: String, value: String, passed: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            "$value ${if (passed) "PASS" else "HOLD"}",
            color = if (passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun InventoryScreen(
    products: List<ProductEntity>,
    onAdd: (String, String) -> Unit,
    amazonResults: List<com.dermatrack.ai.integration.amazon.AmazonCatalogProduct>,
    amazonLoading: Boolean,
    amazonError: String?,
    onAmazonSearch: (String) -> Unit,
    onAddAmazonWishlist: (com.dermatrack.ai.integration.amazon.AmazonCatalogProduct) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var actives by remember { mutableStateOf("") }
    var amazonQuery by remember { mutableStateOf("") }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        item {
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Amazon Skin Care Search", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = amazonQuery,
                        onValueChange = { amazonQuery = it },
                        label = { Text("Search Amazon products") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onAmazonSearch(amazonQuery) },
                            enabled = amazonQuery.trim().length >= 2 && !amazonLoading,
                        ) {
                            Text(if (amazonLoading) "Searching..." else "Search Amazon")
                        }
                    }
                    amazonError?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    amazonResults.take(8).forEach { item ->
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    buildString {
                                        append("ASIN ${item.asin}")
                                        item.rating?.let { append(" · ${"%.1f".format(it)}★") }
                                        item.ratingCount?.let { append(" (${it} ratings)") }
                                        item.price?.let { append(" · $it") }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                OutlinedButton(onClick = { onAddAmazonWishlist(item) }) {
                                    Text("Add to Wishlist")
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Inventory Logger", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Product name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = actives,
                        onValueChange = { actives = it },
                        label = { Text("Active ingredients") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            onAdd(name.trim(), actives.trim())
                            name = ""
                            actives = ""
                        },
                        enabled = name.isNotBlank() && actives.isNotBlank(),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add Product")
                    }
                }
            }
        }
        items(products) { product ->
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(product.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        product.activeIngredients,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun formatDate(epochMillis: Long): String {
    return SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(epochMillis))
}
