package com.example.petcollection.data

import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One pet encounter, independent of whether its breed has already been unlocked.
 * Keep the local time and zone together for future timeline/map storage.
 * Photo metadata time can be supplied by the caller; otherwise use the current time.
 */
@Entity(tableName = "encounters", indices = [Index("breedId")])
data class EncounterRecord(
    val breedId: String,
    val photoUri: String?,
    val encounteredAt: LocalDateTime = LocalDateTime.now(),
    val timeZoneId: String = ZoneId.systemDefault().id,
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val petName: String? = null,
    val location: String? = null,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
