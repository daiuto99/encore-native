package com.encore.core.data.relations

import androidx.room.Embedded
import androidx.room.Relation
import com.encore.core.data.entities.SetEntity
import com.encore.core.data.entities.SetEntryEntity

/**
 * Room relationship class for a Set with all its SetEntries.
 *
 * SetEntries maintain song ordering via the position field.
 * Use this with SetEntryWithSong to get full ordered song data.
 */
data class SetWithEntries(
    @Embedded
    val set: SetEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "set_id"
    )
    val entries: List<SetEntryEntity>
)
