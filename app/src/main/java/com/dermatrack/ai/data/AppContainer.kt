package com.dermatrack.ai.data

import android.content.Context
import androidx.room.Room
import com.dermatrack.ai.BuildConfig
import com.dermatrack.ai.agent.RegimenEngine
import com.dermatrack.ai.analysis.BiomarkerAnalyzer
import com.dermatrack.ai.data.local.DatabaseMigrations
import com.dermatrack.ai.data.local.DermaTrackDatabase
import com.dermatrack.ai.integration.autoderm.AutodermApiClient

class AppContainer(context: Context) {
    private val database = Room.databaseBuilder(
        context = context,
        klass = DermaTrackDatabase::class.java,
        name = "dermatrack_biomarkers.db",
    )
        .addMigrations(
            DatabaseMigrations.MIGRATION_1_2,
            DatabaseMigrations.MIGRATION_2_3,
            DatabaseMigrations.MIGRATION_3_4,
            DatabaseMigrations.MIGRATION_4_5,
        )
        .build()

    val cloudAnalysisPreferences = CloudAnalysisPreferences(context)
    val scanRepository = ScanRepository(database.scanDao())
    val vaultRepository = VaultRepository(context)
    val biomarkerAnalyzer = BiomarkerAnalyzer()
    val regimenEngine = RegimenEngine()

    private val autodermApiClient: AutodermApiClient? =
        BuildConfig.AUTODERM_API_KEY.takeIf { it.isNotBlank() }?.let { apiKey ->
            AutodermApiClient(
                apiKey = apiKey,
                baseUrl = BuildConfig.AUTODERM_API_BASE_URL,
                analyzePath = BuildConfig.AUTODERM_API_PATH,
            )
        }

    val autodermRepository = AutodermRepository(
        autodermScreeningDao = database.autodermScreeningDao(),
        apiClient = autodermApiClient,
    )

    val autodermApiConfigured: Boolean = autodermApiClient != null
}
