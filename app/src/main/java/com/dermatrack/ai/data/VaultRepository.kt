package com.dermatrack.ai.data

import android.content.Context
import android.net.Uri
import com.dermatrack.ai.analysis.BiomarkerAnalysisSource
import com.dermatrack.ai.analysis.BiomarkerResult
import com.dermatrack.ai.analysis.FitzpatrickGroup
import java.io.File

class VaultRepository(private val context: Context) {
    private val imageDir: File
        get() = File(context.filesDir, "vault/images").apply { mkdirs() }

    private val exportDir: File
        get() = File(context.filesDir, "vault/exports").apply { mkdirs() }

    fun createPrivateImageFile(): File {
        return File(imageDir, "scan_${System.currentTimeMillis()}.jpg")
    }

    fun privateImageUri(file: File): Uri {
        return Uri.fromFile(file)
    }

    fun exportMarkdownMetadata(
        scanId: Long,
        biomarkers: BiomarkerResult,
        lux: Float,
        analysisSource: BiomarkerAnalysisSource,
        fitzpatrickGroup: FitzpatrickGroup,
    ): File {
        val file = File(exportDir, "scan_$scanId.md")
        file.writeText(
            """
            |# DermaTrack AI Scan $scanId
            |
            |Raw biometric image: stored in private app storage only.
            |Analysis source: ${analysisSource.name}
            |Fitzpatrick group used: ${fitzpatrickGroup.name}
            |
            || Biomarker | Value |
            || --- | ---: |
            || Erythema Index | ${biomarkers.erythemaIndex} |
            || Melanin Distribution | ${biomarkers.melaninDistribution} |
            || Pore Texture Density | ${biomarkers.poreTextureDensity} |
            || Acne Lesion Count | ${biomarkers.acneLesionCount} |
            || Capture LUX | $lux |
            |
            |Clinical note: DermaTrack AI is a grooming and health utility. It is not a medical diagnosis.
            |""".trimMargin(),
        )
        return file
    }

    fun clearVaultData() {
        imageDir.listFiles()?.forEach { it.delete() }
        exportDir.listFiles()?.forEach { it.delete() }
    }

    fun deleteScanArtifacts(scanId: Long, imagePath: String) {
        File(imagePath).takeIf { it.exists() }?.delete()
        File(exportDir, "scan_${scanId}.md").takeIf { it.exists() }?.delete()
    }
}
