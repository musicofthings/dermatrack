package com.dermatrack.ai.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dermatrack.ai.ClinicalReport
import com.dermatrack.ai.MainUiState
import com.dermatrack.ai.MainViewModel
import com.dermatrack.ai.capture.AlignmentState
import com.dermatrack.ai.capture.LightMeter
import com.dermatrack.ai.capture.LuxGate
import com.dermatrack.ai.data.model.ProductEntity
import com.dermatrack.ai.data.model.ScanEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class AppTab { Report, Capture, Inventory }

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
                    onRecord = viewModel::recordDemoScan,
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
            Divider()
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
                            y = size.height - ((scan.melaninDistribution / 100f).coerceIn(0f, 1f) * size.height),
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
    onRecord: (Float, AlignmentState) -> Unit,
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
    val alignment = AlignmentState.BaselineReady
    val luxGate = LuxGate(baselineLux = baselineLux, currentLux = lux)

    LaunchedEffect(Unit) {
        lightMeter.observeLux().collect { if (it > 0f) lux = it }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .background(Color(0xFF121816), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            CameraPreview(hasCameraPermission = hasCameraPermission)
            GhostOverlay()
            AlignmentReticle(alignment)
        }
        CaptureGatePanel(luxGate = luxGate, alignment = alignment)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (hasCameraPermission) "Camera Ready" else "Grant Camera")
            }
            Button(
                onClick = { onRecord(lux, alignment) },
                enabled = hasCameraPermission && luxGate.isAcceptable && alignment.isAcceptable,
            ) {
                Text("Record Scan")
            }
        }
    }
}

@Composable
private fun CameraPreview(hasCameraPermission: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
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
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        update = { previewView ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener(
                {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    runCatching {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_FRONT_CAMERA,
                            preview,
                        )
                    }.onFailure { Log.w("DermaTrackCamera", "Unable to bind preview", it) }
                },
                ContextCompat.getMainExecutor(context),
            )
        },
    )
}

@Composable
private fun GhostOverlay() {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .alpha(0.30f),
    ) {
        val faceWidth = size.width * 0.58f
        val faceHeight = size.height * 0.66f
        drawOval(
            color = Color.White,
            topLeft = Offset((size.width - faceWidth) / 2f, size.height * 0.15f),
            size = Size(faceWidth, faceHeight),
            style = Stroke(width = 8f),
        )
        drawCircle(Color.White, radius = 8f, center = Offset(size.width * 0.42f, size.height * 0.38f))
        drawCircle(Color.White, radius = 8f, center = Offset(size.width * 0.58f, size.height * 0.38f))
        drawLine(
            color = Color.White,
            start = Offset(size.width * 0.44f, size.height * 0.58f),
            end = Offset(size.width * 0.56f, size.height * 0.58f),
            strokeWidth = 6f,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun AlignmentReticle(alignment: AlignmentState) {
    val color = if (alignment.isAcceptable) Color(0xFF73D6B5) else Color(0xFFE2B05B)
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawCircle(
            color = color,
            radius = size.minDimension * 0.33f,
            center = center,
            style = Stroke(width = 3.dp.toPx()),
        )
        drawLine(color, Offset(center.x - 28f, center.y), Offset(center.x + 28f, center.y), strokeWidth = 3f)
        drawLine(color, Offset(center.x, center.y - 28f), Offset(center.x, center.y + 28f), strokeWidth = 3f)
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
