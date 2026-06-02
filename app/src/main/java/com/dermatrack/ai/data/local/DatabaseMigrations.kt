package com.dermatrack.ai.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                ALTER TABLE scans ADD COLUMN analysisSource TEXT NOT NULL DEFAULT 'ImageDerivedHeuristic'
                """.trimIndent(),
            )
            db.execSQL(
                """
                ALTER TABLE scans ADD COLUMN fitzpatrickGroup TEXT NOT NULL DEFAULT 'V'
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS autoderm_screenings (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    scanId INTEGER NOT NULL,
                    modelVersion TEXT NOT NULL,
                    status TEXT NOT NULL,
                    errorMessage TEXT,
                    predictionsJson TEXT NOT NULL,
                    analyzedAtEpochMillis INTEGER NOT NULL,
                    FOREIGN KEY(scanId) REFERENCES scans(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS index_autoderm_screenings_scanId
                ON autoderm_screenings (scanId)
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                ALTER TABLE autoderm_screenings ADD COLUMN skinToneFitzpatrick INTEGER
                """.trimIndent(),
            )
            db.execSQL(
                """
                ALTER TABLE autoderm_screenings ADD COLUMN skinToneConfidence REAL
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                ALTER TABLE scans ADD COLUMN capturePose TEXT NOT NULL DEFAULT 'Front'
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS personas (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    createdAtEpochMillis INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO personas (id, name, createdAtEpochMillis)
                VALUES (1, 'Default', strftime('%s','now') * 1000)
                """.trimIndent(),
            )
            db.execSQL(
                """
                ALTER TABLE scans ADD COLUMN personaId INTEGER NOT NULL DEFAULT 1
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS persona_embeddings (
                    personaId INTEGER NOT NULL,
                    embeddingJson TEXT NOT NULL,
                    updatedAtEpochMillis INTEGER NOT NULL,
                    PRIMARY KEY(personaId)
                )
                """.trimIndent(),
            )
        }
    }
}
