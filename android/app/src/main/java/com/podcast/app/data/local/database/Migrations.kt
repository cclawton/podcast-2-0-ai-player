package com.podcast.app.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database migrations for the Podcast app.
 */
object Migrations {
    /**
     * Migration from version 1 to 2:
     * - Add auto_download column to podcasts table
     * - Add auto_delete_enabled and auto_delete_only_played to settings
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Add auto_download column to podcasts table with default value false
            db.execSQL("ALTER TABLE podcasts ADD COLUMN auto_download INTEGER NOT NULL DEFAULT 0")
        }
    }

    /**
     * Migration from version 2 to 3:
     * - Add Podcast 2.0 Value4Value / Funding columns to podcasts table
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Add Value4Value / Funding columns
            db.execSQL("ALTER TABLE podcasts ADD COLUMN funding_url TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE podcasts ADD COLUMN funding_message TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE podcasts ADD COLUMN value_model TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE podcasts ADD COLUMN value_type TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE podcasts ADD COLUMN value_recipients_json TEXT DEFAULT NULL")
        }
    }
}
