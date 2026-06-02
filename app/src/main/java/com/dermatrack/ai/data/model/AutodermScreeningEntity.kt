package com.dermatrack.ai.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "autoderm_screenings",
    foreignKeys = [
        ForeignKey(
            entity = ScanEntity::class,
            parentColumns = ["id"],
            childColumns = ["scanId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["scanId"], unique = true)],
)
data class AutodermScreeningEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val scanId: Long,
    val modelVersion: String,
    val status: String,
    val errorMessage: String?,
    val predictionsJson: String,
    val skinToneFitzpatrick: Int? = null,
    val skinToneConfidence: Float? = null,
    val analyzedAtEpochMillis: Long,
)

enum class AutodermScreeningStatus {
    Success,
    Failed,
    ;

    companion object {
        fun fromStorage(value: String): AutodermScreeningStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: Failed
    }
}
