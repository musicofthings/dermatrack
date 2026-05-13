package com.dermatrack.ai.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val activeIngredients: String,
    val addedAtEpochMillis: Long,
)
