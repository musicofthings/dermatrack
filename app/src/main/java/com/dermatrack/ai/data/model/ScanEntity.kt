package com.dermatrack.ai.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Pose a captured frame represents within a guided multi-pose session. */
enum class CapturePose(val spokenLabel: String, val shortLabel: String) {
    Front("front", "Front"),
    LeftProfile("left profile", "Left profile"),
    RightProfile("right profile", "Right profile"),
}

@Entity(tableName = "scans")
data class ScanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val imagePath: String,
    val capturedAtEpochMillis: Long,
    val baselineLux: Float,
    val erythemaIndex: Float,
    val melaninDistribution: Float,
    val poreTextureDensity: Float,
    val acneLesionCount: Int,
    val inflammatoryAcneCount: Int,
    val nonInflammatoryAcneCount: Int,
    val alignmentScore: Float,
    val analysisSource: String,
    val fitzpatrickGroup: String,
    val capturePose: String = CapturePose.Front.name,
    val personaId: Long = 1L,
)
