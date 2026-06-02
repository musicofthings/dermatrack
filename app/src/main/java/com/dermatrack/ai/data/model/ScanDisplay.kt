package com.dermatrack.ai.data.model

import com.dermatrack.ai.analysis.BiomarkerAnalysisSource
import com.dermatrack.ai.analysis.FitzpatrickGroup

fun ScanEntity.resolvedAnalysisSource(): BiomarkerAnalysisSource =
    runCatching { BiomarkerAnalysisSource.valueOf(analysisSource) }
        .getOrDefault(BiomarkerAnalysisSource.ImageDerivedHeuristic)

fun ScanEntity.analysisSourceUserLabel(): String = when (resolvedAnalysisSource()) {
    BiomarkerAnalysisSource.OnDeviceModel -> "On-device model"
    BiomarkerAnalysisSource.ImageDerivedHeuristic ->
        "Image-derived heuristic — not clinically validated"
    BiomarkerAnalysisSource.DeterministicFallback ->
        "Capture-quality estimate only (image analysis unavailable)"
}

fun ScanEntity.capturePoseLabel(): String {
    val pose = runCatching { CapturePose.valueOf(capturePose) }
        .getOrDefault(CapturePose.Front)
    return pose.shortLabel
}

fun ScanEntity.fitzpatrickUserLabel(): String {
    val group = runCatching { FitzpatrickGroup.valueOf(fitzpatrickGroup) }
        .getOrDefault(FitzpatrickGroup.V)
    return "Tone thresholds: Fitzpatrick ${group.name} (auto-detection not yet available)"
}
