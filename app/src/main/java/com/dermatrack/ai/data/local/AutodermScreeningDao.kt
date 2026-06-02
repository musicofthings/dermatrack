package com.dermatrack.ai.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dermatrack.ai.data.model.AutodermScreeningEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AutodermScreeningDao {
    @Query("SELECT * FROM autoderm_screenings ORDER BY analyzedAtEpochMillis DESC")
    fun observeScreenings(): Flow<List<AutodermScreeningEntity>>

    @Query("SELECT * FROM autoderm_screenings WHERE scanId = :scanId LIMIT 1")
    fun observeForScan(scanId: Long): Flow<AutodermScreeningEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(screening: AutodermScreeningEntity): Long

    @Query("DELETE FROM autoderm_screenings")
    suspend fun clearAll()
}
