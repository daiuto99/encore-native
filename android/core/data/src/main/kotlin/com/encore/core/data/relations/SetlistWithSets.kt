package com.encore.core.data.relations

import androidx.room.Embedded
import androidx.room.Relation
import com.encore.core.data.entities.SetlistEntity

/**
 * Room relationship class for a Setlist with all its Sets, Entries, and Songs.
 *
 * This uses nested @Relation to fetch SetWithEntries objects
 * (sets with their entries and songs), maintaining set and position ordering.
 */
data class SetlistWithSets(
    @Embedded
    val setlist: SetlistEntity,

    @Relation(
        entity = com.encore.core.data.entities.SetEntity::class,
        parentColumn = "id",
        entityColumn = "setlist_id"
    )
    val sets: List<SetWithEntries>
)
