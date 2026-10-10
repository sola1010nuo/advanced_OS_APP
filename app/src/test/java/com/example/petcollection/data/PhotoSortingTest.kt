package com.example.petcollection.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class PhotoSortingTest {
    private fun photo(id: String, day: Int?, month: Int = 10) = EncounterRecord("corgi", "file:///$id",
        encounteredAt = day?.let { LocalDateTime.of(2026, month, it, 12, 0) }, id = id, createdAt = 1)
    private val records = listOf(photo("A", 1), photo("B", 8), photo("C", 5), photo("D", 20, 9), photo("E", null))
    private val order = listOf("C", "A", "B", "E", "D").mapIndexed { i, id -> PhotoOrder("breed:corgi", id, i) }
    private fun ids(mode: PhotoSortMode, photos: List<EncounterRecord> = records) = sortAlbumPhotos(photos, mode, order).map { it.id }

    @Test fun datesUseCalendarValuesWithMissingLastInBothDirections() {
        assertEquals(listOf("B", "C", "A", "D", "E"), ids(PhotoSortMode.NEWEST))
        assertEquals(listOf("D", "A", "C", "B", "E"), ids(PhotoSortMode.OLDEST))
    }
    @Test fun dateSortDoesNotOverwriteCustomOrderOrDates() {
        val snapshot = records.toList()
        repeat(4) { ids(PhotoSortMode.NEWEST); ids(PhotoSortMode.OLDEST) }
        assertEquals(listOf("C", "A", "B", "E", "D"), ids(PhotoSortMode.CUSTOM))
        assertEquals(snapshot, records)
    }
    @Test fun equalDatesIgnoreTimeOfDayAndKeepStableCustomOrder() {
        val sameDay = records.map { it.copy(encounteredAt = LocalDateTime.of(2026, 10, 1, if (it.id == "B") 23 else 0, 0)) }
        assertEquals(ids(PhotoSortMode.CUSTOM), ids(PhotoSortMode.NEWEST, sameDay))
        assertEquals(ids(PhotoSortMode.CUSTOM), ids(PhotoSortMode.OLDEST, sameDay))
    }
    @Test fun changingDatesOnlyMovesDateViewsAndNewPhotosAppendToCustom() {
        val updated = records.map { if (it.id == "A") it.copy(encounteredAt = LocalDateTime.of(2027, 1, 1, 0, 0)) else it }
        assertEquals("A", ids(PhotoSortMode.NEWEST, updated).first())
        assertEquals(ids(PhotoSortMode.CUSTOM), ids(PhotoSortMode.CUSTOM, updated))
        assertEquals("F", ids(PhotoSortMode.CUSTOM, updated + photo("F", 9)).last())
        assertEquals(listOf("C", "A", "E", "D"), ids(PhotoSortMode.CUSTOM, updated.filter { it.id != "B" }))
    }
    @Test fun albumKeysAndModesAreIndependent() {
        assertNotEquals(PhotoAlbumKey.breed("corgi"), PhotoAlbumKey.other("corgi"))
        val data = CollectionData(photoSortModes = listOf(PhotoSortSetting(PhotoAlbumKey.breed("corgi"), "OLDEST")))
        assertEquals(PhotoSortMode.OLDEST, data.photoSortMode(PhotoAlbumKey.breed("corgi")))
        assertEquals(PhotoSortMode.NEWEST, data.photoSortMode(PhotoAlbumKey.breed("monkey")))
    }
}
