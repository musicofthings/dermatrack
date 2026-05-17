package com.dermatrack.ai.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.util.Size as CameraSize
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.dermatrack.ai.capture.AlignmentState
import com.dermatrack.ai.capture.FaceDetectionFrameAnalyzer
import com.dermatrack.ai.capture.FaceTrackingState
import com.dermatrack.ai.capture.LightMeter
import com.dermatrack.ai.capture.LuxGate
import com.dermatrack.ai.data.model.ProductEntity
import com.dermatrack.ai.data.model.ScanEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max

private enum class AppTab { Report, Capture, Inventory }

enum class CaptureWorkflowStage {
    Setup,
    MeshLock,
    FrontTopDown,
    FrontLeftRight,
    TurnLeftPrompt,
    LeftTopDown,
    TurnRightPrompt,
    RightTopDown,
    Finalizing,
}

private enum class ScanDirection { None, TopDown, LeftRight }

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
    var selectedTab by remember { mutableStateOf(AppTab.Report) }

    Scaffold(
        topBar = { ClinicalTopBar() },
        bottomBar = {
            NavigationBar {
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
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
        ) {
            when (selectedTab) {
                AppTab.Report -> ReportScreen(uiState)
                AppTab.Capture -> CaptureScreen(
                    baselineLux = uiState.scans.firstOrNull()?.baselineLux ?: 520f,
                    onCreateImageFile = viewModel::createPrivateScanImageFile,
                    onRecord = { lux, alignmentState, imageFile, onComplete, onError ->
                        viewModel.recordCapturedScan(
                            lux = lux,
                            alignmentState = alignmentState,
                            imageFile = imageFile,
                            onComplete = {
                                selectedTab = AppTab.Report
                                onComplete()
                            },
                            onError = onError,
                        )
                    },
                )
                AppTab.Inventory -> InventoryScreen(
                    products = uiState.products,
                    onAdd = viewModel::addInventoryItem,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClinicalTopBar() {
    TopAppBar(
        title = {
            Column {
                Text("DermaTrack AI", fontWeight = FontWeight.SemiBold)
                Text(
                    "Clinical skin health biomarkers",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
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
        text = "Grooming and health utility. For persistent acne, melasma, PIH, pain, bleeding, or rapid changes, consult a dermatologist.",
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
            Text("Latest Clinical Report", style = MaterialTheme.typography.titleMedium)
            BiomarkerGrid(report.scan)
            Text(
                "IGA-style severity proxy: ${report.acneSeverityGrade.label}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            report.progress?.let {
                HorizontalDivider()
                ProgressMetrics(it)
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
        MetricRow("Acne lesion count", progress.lesionCountDeltaPercent.percentDelta())
        MetricRow("Erythema index", progress.erythemaDelta.signedDecimal())
        MetricRow("Pigmentation evenness", progress.pigmentationDelta.signedDecimal())
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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(formatDate(scan.capturedAtEpochMillis), fontWeight = FontWeight.SemiBold)
                Text(
                    "Lux ${scan.baselineLux.toInt()} · Alignment ${(scan.alignmentScore * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("M %.1f".format(scan.melaninDistribution), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun CaptureScreen(
    baselineLux: Float,
    onCreateImageFile: () -> File,
    onRecord: (Float, AlignmentState, File, () -> Unit, (Throwable) -> Unit) -> Unit,
) {
    val context = LocalContext.current
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
    val imageCapture = remember {
        ImageCapture.Builder()
            .setResolutionSelector(CameraStreamResolutionSelector)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            .setResolutionSelector(CameraStreamResolutionSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    var captureInProgress by remember { mutableStateOf(false) }
    var captureStatus by remember { mutableStateOf<String?>(null) }
    var workflowStage by remember { mutableStateOf(CaptureWorkflowStage.Setup) }
    var stageSecondsRemaining by remember { mutableIntStateOf(0) }
    val latestFaceTracking by rememberUpdatedState(faceTracking)
    val currentScanDirection = when (workflowStage) {
        CaptureWorkflowStage.FrontTopDown,
        CaptureWorkflowStage.LeftTopDown,
        CaptureWorkflowStage.RightTopDown,
        -> ScanDirection.TopDown
        CaptureWorkflowStage.FrontLeftRight -> ScanDirection.LeftRight
        else -> ScanDirection.None
    }

    fun finishCapture() {
        captureStatus = "Saving final scan..."
        val imageFile = onCreateImageFile()
        val outputOptions = ImageCapture.OutputFileOptions.Builder(imageFile).build()
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    captureStatus = "Analyzing multi-pose scan..."
                    onRecord(
                        lux,
                        alignment,
                        imageFile,
                        {
                            captureInProgress = false
                            workflowStage = CaptureWorkflowStage.Setup
                            captureStatus = "Scan saved"
                        },
                        {
                            captureInProgress = false
                            workflowStage = CaptureWorkflowStage.Setup
                            captureStatus = "Scan failed"
                            Log.w("DermaTrackCamera", "Unable to record scan", it)
                        },
                    )
                }

                override fun onError(exception: ImageCaptureException) {
                    captureInProgress = false
                    workflowStage = CaptureWorkflowStage.Setup
                    imageFile.delete()
                    captureStatus = "Capture failed"
                    Log.w("DermaTrackCamera", "Unable to save private scan image", exception)
                }
            },
        )
    }

    LaunchedEffect(Unit) {
        lightMeter.observeLux().collect { if (it > 0f) lux = it }
    }

    DisposableEffect(imageAnalysis, analysisExecutor) {
        imageAnalysis.setAnalyzer(
            analysisExecutor,
            FaceDetectionFrameAnalyzer(stage = { workflowStage }) { faceTracking = it },
        )
        onDispose {
            imageAnalysis.clearAnalyzer()
            analysisExecutor.shutdown()
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
                        workflowStage = CaptureWorkflowStage.FrontTopDown
                    } else {
                        delay(350)
                    }
                }
            }
            CaptureWorkflowStage.FrontTopDown -> {
                captureStatus = "Front scan: top-to-bottom pass. Hold still and look straight."
                stageSecondsRemaining = 15
                repeat(15) {
                    delay(1_000)
                    stageSecondsRemaining -= 1
                }
                workflowStage = CaptureWorkflowStage.FrontLeftRight
            }
            CaptureWorkflowStage.FrontLeftRight -> {
                captureStatus = "Front scan: left-to-right pass. Keep light even across the face."
                stageSecondsRemaining = 15
                repeat(15) {
                    delay(1_000)
                    stageSecondsRemaining -= 1
                }
                workflowStage = CaptureWorkflowStage.TurnLeftPrompt
                captureStatus = "Turn your face left and hold. Capture starts after 3 stable seconds."
            }
            CaptureWorkflowStage.LeftTopDown -> {
                captureStatus = "Left profile scan: hold the pose."
                stageSecondsRemaining = 12
                repeat(12) {
                    delay(1_000)
                    stageSecondsRemaining -= 1
                }
                workflowStage = CaptureWorkflowStage.TurnRightPrompt
                captureStatus = "Turn your face right and hold. Capture starts after 3 stable seconds."
            }
            CaptureWorkflowStage.RightTopDown -> {
                captureStatus = "Right profile scan: hold the pose."
                stageSecondsRemaining = 12
                repeat(12) {
                    delay(1_000)
                    stageSecondsRemaining -= 1
                }
                workflowStage = CaptureWorkflowStage.Finalizing
            }
            CaptureWorkflowStage.Finalizing -> finishCapture()
            CaptureWorkflowStage.TurnLeftPrompt,
            CaptureWorkflowStage.TurnRightPrompt,
            -> {
                val leftPose = workflowStage == CaptureWorkflowStage.TurnLeftPrompt
                captureStatus = if (leftPose) {
                    "Turn your face left and hold. Capture starts after 3 stable seconds."
                } else {
                    "Turn your face right and hold. Capture starts after 3 stable seconds."
                }
                var stableSeconds = 0
                while (
                    workflowStage == CaptureWorkflowStage.TurnLeftPrompt ||
                    workflowStage == CaptureWorkflowStage.TurnRightPrompt
                ) {
                    val stable = if (leftPose) latestFaceTracking.isLeftPose else latestFaceTracking.isRightPose
                    if (stable) {
                        stableSeconds += 1
                        stageSecondsRemaining = 3 - stableSeconds
                    } else {
                        stableSeconds = 0
                        stageSecondsRemaining = 0
                    }
                    if (stableSeconds >= 3) {
                        workflowStage = if (leftPose) CaptureWorkflowStage.LeftTopDown else CaptureWorkflowStage.RightTopDown
                    }
                    delay(1_000)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .background(Color(0xFF121816), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            CameraPreview(
                hasCameraPermission = hasCameraPermission,
                imageCapture = imageCapture,
                imageAnalysis = imageAnalysis,
            )
            FaceMeshOverlay(
                faceTracking = faceTracking,
                scanDirection = currentScanDirection,
                isScanning = captureInProgress && workflowStage != CaptureWorkflowStage.MeshLock,
            )
        }
        CaptureInstructionPanel(
            stage = workflowStage,
            faceTracking = faceTracking,
            secondsRemaining = stageSecondsRemaining,
            status = captureStatus,
        )
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
                            workflowStage = CaptureWorkflowStage.MeshLock
                        }
                        CaptureWorkflowStage.TurnLeftPrompt -> workflowStage = CaptureWorkflowStage.LeftTopDown
                        CaptureWorkflowStage.TurnRightPrompt -> workflowStage = CaptureWorkflowStage.RightTopDown
                        else -> Unit
                    }
                },
                enabled = hasCameraPermission && luxGate.isAcceptable && when (workflowStage) {
                    CaptureWorkflowStage.Setup -> true
                    else -> false
                },
            ) {
                Text(
                    when (workflowStage) {
                        CaptureWorkflowStage.Setup -> "Start Guided Scan"
                        CaptureWorkflowStage.TurnLeftPrompt -> "Hold Left Pose"
                        CaptureWorkflowStage.TurnRightPrompt -> "Hold Right Pose"
                        CaptureWorkflowStage.Finalizing -> "Saving..."
                        else -> "Scanning..."
                    },
                )
            }
        }
        CaptureGatePanel(luxGate = luxGate, alignment = alignment)
    }
}

@Composable
private fun CameraPreview(
    hasCameraPermission: Boolean,
    imageCapture: ImageCapture,
    imageAnalysis: ImageAnalysis,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(hasCameraPermission, context, lifecycleOwner, imageCapture, imageAnalysis, previewView) {
        if (!hasCameraPermission) {
            onDispose { }
        } else {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            val listener = Runnable {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder()
                    .setResolutionSelector(CameraStreamResolutionSelector)
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                runCatching {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview,
                        imageAnalysis,
                        imageCapture,
                    )
                }.onFailure { Log.w("DermaTrackCamera", "Unable to bind preview", it) }
            }
            cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))
            onDispose {
                runCatching {
                    if (cameraProviderFuture.isDone) {
                        cameraProviderFuture.get().unbindAll()
                    }
                }
            }
        }
    }

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
private fun CaptureInstructionPanel(
    stage: CaptureWorkflowStage,
    faceTracking: FaceTrackingState,
    secondsRemaining: Int,
    status: String?,
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
}

private fun CaptureWorkflowStage.title(): String = when (this) {
    CaptureWorkflowStage.Setup -> "Guided Face Scan"
    CaptureWorkflowStage.MeshLock -> "Face Mesh Lock"
    CaptureWorkflowStage.FrontTopDown -> "Front Surface Pass"
    CaptureWorkflowStage.FrontLeftRight -> "Front Cross Pass"
    CaptureWorkflowStage.TurnLeftPrompt -> "Left Profile Setup"
    CaptureWorkflowStage.LeftTopDown -> "Left Profile Pass"
    CaptureWorkflowStage.TurnRightPrompt -> "Right Profile Setup"
    CaptureWorkflowStage.RightTopDown -> "Right Profile Pass"
    CaptureWorkflowStage.Finalizing -> "Finalizing Scan"
}

private fun CaptureWorkflowStage.defaultInstruction(): String = when (this) {
    CaptureWorkflowStage.Setup -> "Remove glasses/headwear, sit straight, and keep light falling evenly on face skin."
    CaptureWorkflowStage.MeshLock -> "Hold still while the face mesh locks onto your features."
    CaptureWorkflowStage.FrontTopDown -> "Hold straight. The scanner is moving from forehead to chin."
    CaptureWorkflowStage.FrontLeftRight -> "Hold straight. The scanner is moving left to right."
    CaptureWorkflowStage.TurnLeftPrompt -> "Turn your face left until the pose gate unlocks."
    CaptureWorkflowStage.LeftTopDown -> "Hold the left profile still."
    CaptureWorkflowStage.TurnRightPrompt -> "Turn your face right until the pose gate unlocks."
    CaptureWorkflowStage.RightTopDown -> "Hold the right profile still."
    CaptureWorkflowStage.Finalizing -> "Saving final aligned frame and generating report."
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
        val previewTransform = PreviewCropTransform(
            canvasWidth = size.width,
            canvasHeight = size.height,
            sourceAspectRatio = faceTracking.sourceAspectRatio,
        )
        val bounds = faceTracking.faceBounds
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

        // Expanded clinical reticle and mesh logic
        val topLeft = previewTransform.map(bounds.left, bounds.top)
        val bottomRight = previewTransform.map(bounds.right, bounds.bottom)
        val left = topLeft.x
        val top = topLeft.y
        val right = bottomRight.x
        val bottom = bottomRight.y
        val width = (right - left).coerceAtLeast(1f)
        val height = (bottom - top).coerceAtLeast(1f)

        // Draw Clinical Reticle (Crosshairs)
        val reticleColor = if (faceTracking.isStraightEnough) Color(0xFF73D6B5) else Color(0xFFE2B05B).copy(alpha = 0.6f)
        val centerX = left + (width / 2f)
        val centerY = top + (height / 2f)
        drawLine(reticleColor, Offset(centerX - 40f, centerY), Offset(centerX + 40f, centerY), strokeWidth = 2f)
        drawLine(reticleColor, Offset(centerX, centerY - 40f), Offset(centerX, centerY + 40f), strokeWidth = 2f)
        drawCircle(reticleColor, radius = 50f, center = Offset(centerX, centerY), style = Stroke(width = 1.5f))

        val outline = faceTracking.faceOutline.map { previewTransform.map(it.x, it.y) }
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
            val points = contour.map { previewTransform.map(it.x, it.y) }
            if (points.size > 1) {
                drawPath(
                    path = points.toOpenPath(),
                    color = gridColor,
                    style = Stroke(width = 1.5f),
                )
            }
        }

        faceTracking.meshPoints.forEachIndexed { index, point ->
            val mapped = previewTransform.map(point.x, point.y)
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

private data class PreviewCropTransform(
    val canvasWidth: Float,
    val canvasHeight: Float,
    val sourceAspectRatio: Float,
) {
    private val sourceWidth = sourceAspectRatio.coerceAtLeast(0.01f)
    private val sourceHeight = 1f
    private val scale = max(canvasWidth / sourceWidth, canvasHeight / sourceHeight)
    private val drawnWidth = sourceWidth * scale
    private val drawnHeight = sourceHeight * scale
    private val xOffset = (canvasWidth - drawnWidth) / 2f
    private val yOffset = (canvasHeight - drawnHeight) / 2f
    private val frontCameraXCalibration = canvasWidth * -0.05f
    private val frontCameraYCalibration = canvasHeight * -0.15f

    fun map(normalizedX: Float, normalizedY: Float): Offset {
        return Offset(
            x = xOffset + frontCameraXCalibration + (normalizedX.coerceIn(0f, 1f) * drawnWidth),
            y = yOffset + frontCameraYCalibration + (normalizedY.coerceIn(0f, 1f) * drawnHeight),
        )
    }
}

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
) {
    var name by remember { mutableStateOf("") }
    var actives by remember { mutableStateOf("") }
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
