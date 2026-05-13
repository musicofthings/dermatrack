package com.dermatrack.ai.analysis

interface FaceLandmarkModel {
    suspend fun estimateAlignment(frame: ByteArray): Float
}

interface SkinBiomarkerModel {
    suspend fun infer(frame: ByteArray, fitzpatrickGroup: FitzpatrickGroup): BiomarkerResult
}

enum class FitzpatrickGroup {
    IV,
    V,
    VI,
}
