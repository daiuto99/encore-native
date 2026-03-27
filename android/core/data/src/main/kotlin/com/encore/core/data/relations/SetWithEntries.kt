package com.encore.core.data.relations

import androidx.room.Embedded
import androidx.room.Relation
import com.encore.core.data.entities.SetEntity

/**
 * Room relationship class for a Set with all its SetEntries and Songs.
 *
 * This uses nested @Relation to fetch SetEntryWithSong objects
 * (entries with their associated songs), maintaining position ordering.
 */
data class SetWithEntries(
    @Embedded
    val set: SetEntity,

    @Relation(
        entity = com.encore.core.data.entities.SetEntryEntity::class,
        parentColumn = "id",
        entityColumn = "set_id"
    )
    val entries: List<SetEntryWithSong>
)
