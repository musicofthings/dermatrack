package com.dermatrack.ai.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.dermatrack.ai.data.model.ProductEntity
import com.dermatrack.ai.data.model.ScanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {
    @Query("SELECT * FROM scans ORDER BY capturedAtEpochMillis DESC")
    fun observeScans(): Flow<List<ScanEntity>>

    @Query("SELECT * FROM products ORDER BY addedAtEpochMillis DESC")
    fun observeProducts(): Flow<List<ProductEntity>>

    @Insert
    suspend fun insertScan(scan: ScanEntity): Long

    @Insert
    suspend fun insertProduct(product: ProductEntity): Long
}
