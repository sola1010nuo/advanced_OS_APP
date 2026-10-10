package com.example.petcollection.data

import androidx.room.*

enum class PhotoSortMode { NEWEST, OLDEST, CUSTOM }

/** Namespaces prevent a custom album ID from colliding with a breed ID. */
object PhotoAlbumKey {
    fun breed(id: String) = "breed:$id"
    fun other(id: String) = "other:$id"
    const val UNASSIGNED = "other:unassigned"
}

@Entity(tableName = "photo_sort_modes")
data class PhotoSortSetting(@PrimaryKey val albumKey: String, val mode: String)

@Entity(tableName = "photo_custom_order", primaryKeys = ["albumKey", "encounterId"],
    foreignKeys = [ForeignKey(entity = EncounterRecord::class, parentColumns = ["id"],
        childColumns = ["encounterId"], onDelete = ForeignKey.CASCADE)], indices = [Index("encounterId")])
data class PhotoOrder(val albumKey: String, val encounterId: String, val position: Int)

/** The editor's existing date, not upload/creation/EXIF time; null dates always trail. */
fun sortAlbumPhotos(records: List<EncounterRecord>, mode: PhotoSortMode, positions: List<PhotoOrder>): List<EncounterRecord> {
    val rank = positions.associate { it.encounterId to it.position }
    val custom = records.sortedWith(compareBy<EncounterRecord> { rank[it.id] ?: Int.MAX_VALUE }
        .thenBy { it.createdAt }.thenBy { it.id })
    if (mode == PhotoSortMode.CUSTOM) return custom
    // Kotlin's stable sort retains custom order for equal dates, including missing dates.
    return custom.sortedWith { a, b ->
        val first = a.encounteredAt?.toLocalDate()
        val second = b.encounteredAt?.toLocalDate()
        when {
            first == null && second == null -> 0
            first == null -> 1
            second == null -> -1
            mode == PhotoSortMode.NEWEST -> second.compareTo(first)
            else -> first.compareTo(second)
        }
    }
}
