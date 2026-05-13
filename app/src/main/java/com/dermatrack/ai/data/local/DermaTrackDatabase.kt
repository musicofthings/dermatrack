package com.dermatrack.ai.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.dermatrack.ai.data.model.ProductEntity
import com.dermatrack.ai.data.model.ScanEntity

@Database(
    entities = [ScanEntity::class, ProductEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class DermaTrackDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao
}
