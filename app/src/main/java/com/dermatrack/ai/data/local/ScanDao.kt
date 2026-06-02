package com.dermatrack.ai.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dermatrack.ai.data.model.PersonaEntity
import com.dermatrack.ai.data.model.PersonaEmbeddingEntity
import com.dermatrack.ai.data.model.ProductEntity
import com.dermatrack.ai.data.model.ScanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {
    @Query("SELECT * FROM scans WHERE personaId = :personaId ORDER BY capturedAtEpochMillis DESC")
    fun observeScansForPersona(personaId: Long): Flow<List<ScanEntity>>

    @Query("SELECT * FROM scans WHERE personaId = :personaId ORDER BY capturedAtEpochMillis DESC")
    suspend fun listScansForPersona(personaId: Long): List<ScanEntity>

    @Query("SELECT * FROM scans WHERE capturePose = 'Front' ORDER BY capturedAtEpochMillis DESC")
    fun observeFrontScans(): Flow<List<ScanEntity>>

    @Query("SELECT * FROM products ORDER BY addedAtEpochMillis DESC")
    fun observeProducts(): Flow<List<ProductEntity>>

    @Insert
    suspend fun insertScan(scan: ScanEntity): Long

    @Insert
    suspend fun insertProduct(product: ProductEntity): Long

    @Query("SELECT * FROM personas ORDER BY createdAtEpochMillis ASC")
    fun observePersonas(): Flow<List<PersonaEntity>>

    @Query("SELECT * FROM personas ORDER BY createdAtEpochMillis ASC")
    suspend fun listPersonas(): List<PersonaEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPersona(persona: PersonaEntity): Long

    @Query("SELECT * FROM persona_embeddings")
    fun observePersonaEmbeddings(): Flow<List<PersonaEmbeddingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPersonaEmbedding(embedding: PersonaEmbeddingEntity): Long

    @Query("DELETE FROM persona_embeddings")
    suspend fun clearPersonaEmbeddings()

    @Query("DELETE FROM persona_embeddings WHERE personaId = :personaId")
    suspend fun clearPersonaEmbeddingForPersona(personaId: Long)

    @Query("DELETE FROM scans WHERE personaId = :personaId")
    suspend fun clearScansForPersona(personaId: Long)

    @Query("DELETE FROM personas WHERE id = :personaId")
    suspend fun deletePersonaById(personaId: Long)

    @Query("DELETE FROM scans")
    suspend fun clearScans()

    @Query("DELETE FROM products")
    suspend fun clearProducts()
}
