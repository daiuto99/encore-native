package com.encore.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.encore.core.data.dao.SetDao
import com.encore.core.data.dao.SetEntryDao
import com.encore.core.data.dao.SetlistDao
import com.encore.core.data.dao.SongDao
import com.encore.core.data.entities.SetEntity
import com.encore.core.data.entities.SetEntryEntity
import com.encore.core.data.entities.SetlistEntity
import com.encore.core.data.entities.SongEntity
import com.encore.core.data.entities.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Encore Room Database - Local offline-first persistence layer.
 *
 * Version 1: Initial schema with Song, Setlist, Set, and SetEntry entities.
 * Version 2: Added lastZoomLevel field to SongEntity for Performance Mode.
 * Version 3: Added ownerId field to SongEntity for Milestone 4 auth integration.
 * Pre-populates with Amazing Grace demo song on first launch.
 *
 * Based on: docs/architecture/data-model.md
 */
@Database(
    entities = [
        SongEntity::class,
        SetlistEntity::class,
        SetEntity::class,
        SetEntryEntity::class
    ],
    version = 7,
    exportSchema = true
)
@TypeConverters(EncoreTypeConverters::class)
abstract class EncoreDatabase : RoomDatabase() {

    // DAO interfaces
    abstract fun songDao(): SongDao
    abstract fun setlistDao(): SetlistDao
    abstract fun setDao(): SetDao
    abstract fun setEntryDao(): SetEntryDao

    companion object {
        @Volatile
        private var INSTANCE: EncoreDatabase? = null

        private const val DATABASE_NAME = "encore_database"

        /**
         * Migration from version 1 to 2: Add lastZoomLevel column to songs table.
         * Default value is 1.0 (100% zoom).
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE songs ADD COLUMN last_zoom_level REAL NOT NULL DEFAULT 1.0"
                )
            }
        }

        /**
         * Migration from version 2 to 3: Add ownerId column to songs table.
         * Nullable — null until user signs in with Google.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE songs ADD COLUMN owner_id TEXT"
                )
            }
        }

        /**
         * Migration from version 3 to 4: Add isHarmonyMode and highlightStyle to songs.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE songs ADD COLUMN is_harmony_mode INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE songs ADD COLUMN highlight_style INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Migration from version 4 to 5:
         * - Rename current_key → display_key
         * - Add original_key (nullable)
         * - Add is_lead_guitar (Boolean, default 0)
         * - Add is_verified (Boolean, default 0)
         * - Add last_verified_at (Long, default current time)
         * - Drop lead_marker and harmony_markup
         *
         * SQLite does not support DROP COLUMN before 3.35 or RENAME COLUMN portably,
         * so we recreate the table, copy data, drop old, and rename.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val now = System.currentTimeMillis()
                database.execSQL("""
                    CREATE TABLE songs_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        user_id TEXT NOT NULL,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        display_key TEXT,
                        original_key TEXT,
                        markdown_body TEXT NOT NULL,
                        original_import_body TEXT,
                        is_lead_guitar INTEGER NOT NULL DEFAULT 0,
                        is_verified INTEGER NOT NULL DEFAULT 0,
                        last_verified_at INTEGER NOT NULL DEFAULT 0,
                        last_zoom_level REAL NOT NULL DEFAULT 1.0,
                        owner_id TEXT,
                        version INTEGER NOT NULL DEFAULT 1,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        sync_status TEXT NOT NULL DEFAULT 'SYNCED',
                        local_updated_at INTEGER NOT NULL,
                        last_synced_at INTEGER,
                        is_harmony_mode INTEGER NOT NULL DEFAULT 0,
                        highlight_style INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                database.execSQL("""
                    INSERT INTO songs_new (
                        id, user_id, title, artist, display_key, original_key,
                        markdown_body, original_import_body,
                        is_lead_guitar, is_verified, last_verified_at,
                        last_zoom_level, owner_id, version, created_at, updated_at,
                        sync_status, local_updated_at, last_synced_at,
                        is_harmony_mode, highlight_style
                    )
                    SELECT
                        id, user_id, title, artist, current_key, NULL,
                        markdown_body, original_import_body,
                        0, 0, $now,
                        last_zoom_level, owner_id, version, created_at, updated_at,
                        sync_status, local_updated_at, last_synced_at,
                        is_harmony_mode, highlight_style
                    FROM songs
                """.trimIndent())
                database.execSQL("DROP TABLE songs")
                database.execSQL("ALTER TABLE songs_new RENAME TO songs")
                // Recreate indexes
                database.execSQL("CREATE INDEX index_songs_user_id_title ON songs (user_id, title)")
                database.execSQL("CREATE INDEX index_songs_user_id_artist ON songs (user_id, artist)")
                database.execSQL("CREATE UNIQUE INDEX index_songs_user_id_title_artist ON songs (user_id, title, artist)")
                database.execSQL("CREATE INDEX index_songs_sync_status ON songs (sync_status)")
            }
        }

        /**
         * Migration from version 5 to 6: Add validation_errors column for Library Health Tool.
         * Nullable TEXT — null means unscanned or clean; a non-null value is a semicolon-separated
         * list of issues found by LibraryAuditWorker.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE songs ADD COLUMN validation_errors TEXT"
                )
            }
        }

        /**
         * Migration from version 6 to 7: Add sync hash fields for Milestone 4 sync engine.
         * - last_synced_hash: MD5 of markdownBody at last successful sync (null = never synced)
         * - is_dirty: 1 when local edits haven't been pushed to server (default 0)
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE songs ADD COLUMN last_synced_hash TEXT")
                database.execSQL("ALTER TABLE songs ADD COLUMN is_dirty INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Get singleton database instance.
         *
         * @param context Application context
         * @return Encore database instance
         */
        fun getDatabase(context: Context): EncoreDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    EncoreDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigration()
                    .addCallback(DatabaseCallback(context.applicationContext))
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Room database callback for pre-population and migration logic.
         */
        private class DatabaseCallback(
            private val context: Context
        ) : RoomDatabase.Callback() {

            /**
             * Called when the database is created for the first time.
             * Pre-populates with Amazing Grace demo song.
             */
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        prepopulateDatabase(database.songDao())
                    }
                }
            }

            /**
             * Pre-populate database with Amazing Grace sample song.
             * Ensures app isn't empty on first launch.
             */
            private suspend fun prepopulateDatabase(songDao: SongDao) {
                val now = System.currentTimeMillis()

                val amazingGraceSong = SongEntity(
                    id = UUID.randomUUID().toString(),
                    userId = "local-user", // Hardcoded for Milestone 2
                    title = "Amazing Grace",
                    artist = "John Newton",
                    displayKey = "G",
                    originalKey = "G",
                    markdownBody = AMAZING_GRACE_MARKDOWN,
                    originalImportBody = AMAZING_GRACE_FULL_MARKDOWN,
                    lastZoomLevel = 1.0f,
                    version = 1,
                    createdAt = now,
                    updatedAt = now,
                    syncStatus = SyncStatus.SYNCED,
                    localUpdatedAt = now,
                    lastSyncedAt = now
                )

                songDao.insert(amazingGraceSong)
            }
        }
    }
}

/**
 * Amazing Grace markdown content (body only, without YAML front matter).
 */
private const val AMAZING_GRACE_MARKDOWN = """# Verse 1

    G              C         G
Amazing grace, how sweet the sound
     D              G
That saved a wretch like me
  G                 C           G
I once was lost, but now I'm found
    D           G
Was blind but now I see

# Verse 2

    G                C           G
'Twas grace that taught my heart to fear
    D                G
And grace my fears relieved
    G              C         G
How precious did that grace appear
    D              G
The hour I first believed

# Verse 3

    G                  C            G
Through many dangers, toils and snares
  D                 G
I have already come
     G              C         G
'Tis grace has brought me safe thus far
    D                 G
And grace will lead me home

# Verse 4

    G               C          G
When we've been here ten thousand years
  D                G
Bright shining as the sun
     G                C          G
We've no less days to sing God's praise
     D              G
Than when we first begun"""

/**
 * Full Amazing Grace markdown including YAML front matter.
 */
private const val AMAZING_GRACE_FULL_MARKDOWN = """---
title: Amazing Grace
artist: John Newton
key: G
---

$AMAZING_GRACE_MARKDOWN"""
