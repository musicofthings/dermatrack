package com.dermatrack.ai.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "persona_embeddings")
data class PersonaEmbeddingEntity(
    @PrimaryKey
    val personaId: Long,
    val embeddingJson: String,
    val updatedAtEpochMillis: Long,
)
