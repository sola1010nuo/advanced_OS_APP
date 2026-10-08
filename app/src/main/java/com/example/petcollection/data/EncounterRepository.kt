package com.example.petcollection.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class CollectionData(
    val encounters: List<EncounterRecord> = emptyList(),
    val covers: List<BreedCover> = emptyList(),
    val loaded: Boolean = false
) {
    fun forBreed(id: String) = encounters.filter { it.breedId == id }
        .sortedWith(compareByDescending<EncounterRecord> { it.encounteredAt }.thenByDescending { it.createdAt }.thenBy { it.id })
    fun applyTo(breed: Breed): Breed {
        val records = forBreed(breed.id)
        val photos = records.mapNotNull { record -> record.photoUri?.let { BreedPhoto(record.id, it) } }
        val selected = covers.firstOrNull { it.breedId == breed.id }?.encounterId
        return breed.copy(isDiscovered = records.isNotEmpty(), encounterCount = records.size,
            collectedPhotos = photos, coverPhoto = photos.firstOrNull { it.id == selected } ?: photos.lastOrNull())
    }
    fun discoveredCount(ids: Set<String>) = encounters.map { it.breedId }.filter { it in ids }.toSet().size

    /** Catalog defines official breeds; only persisted rows contribute to discovery or totals. */
    fun statistics(catalog: List<Breed>, species: Species? = null): CollectionStatistics {
        val officialIds = catalog.filter { it.isCollectible && (species == null || it.species == species) }
            .map { it.id }.toSet()
        val encounterIds = catalog.filter { species == null || it.species == species }.map { it.id }.toSet()
        return CollectionStatistics(discoveredCount(officialIds), officialIds.size,
            encounters.count { it.breedId in encounterIds })
    }
}

data class CollectionStatistics(val discovered: Int, val totalBreeds: Int, val encounterCount: Int)

/** One persistent encounter store shared by all normal breeds and special albums. */
class EncounterRepository(private val dao: EncounterDao, private val context: Context) {
    val collection = combine(dao.observeAll(), dao.observeCovers()) { records, covers -> CollectionData(records, covers, true) }
    fun observeEncounter(id: String) = dao.observeById(id)
    fun observeByBreed(id: String) = dao.observeByBreed(id)
    fun observeCounts() = dao.observeCounts()

    suspend fun create(record: EncounterRecord): Pair<EncounterRecord, Boolean> {
        require(record.breedId in MockBreedRepository.collectionIds)
        val stored = record.copy(photoUri = record.photoUri?.let { retainPhoto(it) })
        try {
            return stored to dao.create(stored)
        } catch (error: Exception) {
            if (stored.photoUri != record.photoUri) stored.photoUri?.let { deletePrivatePhoto(it) }
            throw error
        }
    }

    suspend fun update(record: EncounterRecord) {
        val old = requireNotNull(dao.get(record.id))
        require(record.breedId in MockBreedRepository.collectionIds)
        dao.updateAndRepairCovers(record.copy(photoUri = old.photoUri, createdAt = old.createdAt))
    }
    suspend fun selectCover(breedId: String, encounterId: String) = dao.selectCover(breedId, encounterId)
    suspend fun delete(id: String) {
        val deleted = dao.deleteAndRepairCover(id) ?: return
        deleted.photoUri?.let { if (dao.photoReferences(it) == 0) deletePrivatePhoto(it) }
    }

    private suspend fun retainPhoto(reference: String): String = withContext(Dispatchers.IO) {
        val uri = Uri.parse(reference)
        if (uri.scheme == "content") {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                return@withContext reference
            } catch (_: SecurityException) {
                // Providers without persistable grants are copied to app-private files, never into Room.
            }
        } else if (uri.scheme == "file" && File(requireNotNull(uri.path)).canonicalPath.startsWith(context.filesDir.canonicalPath + File.separator)) {
            return@withContext reference
        }
        val directory = File(context.filesDir, "encounter_photos").apply { mkdirs() }
        val destination = File(directory, UUID.randomUUID().toString())
        try {
            requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
            Uri.fromFile(destination).toString()
        } catch (error: Exception) {
            destination.delete()
            throw error
        }
    }

    private suspend fun deletePrivatePhoto(reference: String) = withContext(Dispatchers.IO) {
        val uri = Uri.parse(reference)
        val directory = File(context.filesDir, "encounter_photos").canonicalPath + File.separator
        if (uri.scheme == "file") uri.path?.let { path ->
            val file = File(path)
            if (file.canonicalPath.startsWith(directory)) file.delete()
        }
        Unit
    }
}
