package com.encore.core.data.relations

import androidx.room.Embedded
import androidx.room.Relation
import com.encore.core.data.entities.SetEntryEntity
import com.encore.core.data.entities.SongEntity

/**
 * Room relationship class for a SetEntry with its associated Song.
 *
 * Preserves position ordering from SetEntryEntity.
 */
data class SetEntryWithSong(
    @Embedded
    val entry: SetEntryEntity,

    @Relation(
        parentColumn = "song_id",
        entityColumn = "id"
    )
    val song: SongEntity
)
