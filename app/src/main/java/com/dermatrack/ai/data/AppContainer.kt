package com.dermatrack.ai.data

import android.content.Context
import androidx.room.Room
import com.dermatrack.ai.agent.RegimenEngine
import com.dermatrack.ai.analysis.BiomarkerAnalyzer
import com.dermatrack.ai.data.local.DermaTrackDatabase

class AppContainer(context: Context) {
    private val database = Room.databaseBuilder(
        context = context,
        klass = DermaTrackDatabase::class.java,
        name = "dermatrack_biomarkers.db",
    ).build()

    val scanRepository = ScanRepository(database.scanDao())
    val vaultRepository = VaultRepository(context)
    val biomarkerAnalyzer = BiomarkerAnalyzer()
    val regimenEngine = RegimenEngine()
}
