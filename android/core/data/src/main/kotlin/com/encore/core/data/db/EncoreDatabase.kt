package com.encore.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
    version = 1,
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
                    currentKey = "G",
                    markdownBody = AMAZING_GRACE_MARKDOWN,
                    originalImportBody = AMAZING_GRACE_FULL_MARKDOWN,
                    leadMarker = null,
                    harmonyMarkup = null,
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
