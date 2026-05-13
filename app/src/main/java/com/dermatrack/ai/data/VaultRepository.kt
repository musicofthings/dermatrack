package com.dermatrack.ai.data

import android.content.Context
import android.net.Uri
import com.dermatrack.ai.analysis.BiomarkerResult
import java.io.File

class VaultRepository(private val context: Context) {
    private val imageDir: File
        get() = File(context.filesDir, "vault/images").apply { mkdirs() }

    private val exportDir: File
        get() = File(context.filesDir, "vault/exports").apply { mkdirs() }

    fun createPrivateImageSlot(): Uri {
        val file = File(imageDir, "scan_${System.currentTimeMillis()}.jpg")
        if (!file.exists()) file.createNewFile()
        return Uri.fromFile(file)
    }

    fun exportMarkdownMetadata(scanId: Long, biomarkers: BiomarkerResult, lux: Float): File {
        val file = File(exportDir, "scan_$scanId.md")
        file.writeText(
            """
            |# DermaTrack AI Scan $scanId
            |
            |Raw biometric image: stored in private app storage only.
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
}
