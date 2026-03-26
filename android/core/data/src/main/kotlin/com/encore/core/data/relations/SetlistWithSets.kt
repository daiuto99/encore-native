package com.encore.core.data.relations

import androidx.room.Embedded
import androidx.room.Relation
import com.encore.core.data.entities.SetEntity
import com.encore.core.data.entities.SetlistEntity

/**
 * Room relationship class for a Setlist with all its Sets.
 *
 * Used for @Transaction queries that fetch complete setlist data.
 */
data class SetlistWithSets(
    @Embedded
    val setlist: SetlistEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "setlist_id"
    )
    val sets: List<SetEntity>
)
