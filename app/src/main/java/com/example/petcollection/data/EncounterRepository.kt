package com.example.petcollection.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.time.ZoneId
import java.util.UUID

data class CollectionData(
    val encounters: List<EncounterRecord> = emptyList(),
    val covers: List<BreedCover> = emptyList(),
    val loaded: Boolean = false,
    val albums: List<OtherAlbum> = emptyList(),
    val members: List<OtherAlbumMember> = emptyList(),
    val photoSortModes: List<PhotoSortSetting> = emptyList(),
    val photoOrder: List<PhotoOrder> = emptyList()
) {
    fun photoSortMode(key: String) = photoSortModes.find { it.albumKey == key }?.mode
        ?.let { runCatching { PhotoSortMode.valueOf(it) }.getOrNull() } ?: PhotoSortMode.NEWEST
    fun sortedPhotos(key: String, records: List<EncounterRecord>) = sortAlbumPhotos(records,
        photoSortMode(key), photoOrder.filter { it.albumKey == key })
    fun forBreed(id: String) = encounters.filter { it.breedId == id }
        .sortedWith(compareByDescending<EncounterRecord> { it.encounteredAt }.thenByDescending { it.createdAt }.thenBy { it.id })
    fun applyTo(breed: Breed): Breed {
        val records = forBreed(breed.id)
        val photos = records.mapNotNull { record -> record.photoUri?.let { BreedPhoto(record.id, it) } }
        val selected = covers.firstOrNull { it.breedId == breed.id }
        return breed.copy(isDiscovered = records.isNotEmpty(), encounterCount = records.size,
            collectedPhotos = photos, coverPhoto = photos.firstOrNull { it.id == selected?.encounterId }
                ?.copy(cropRegion = selected?.cropRegion) ?: photos.lastOrNull())
    }
    fun discoveredCount(ids: Set<String>) = encounters.map { it.breedId }.filter { it in ids }.toSet().size

    /** Breeds, coat entries and the secret monkey share discovery totals from persisted rows. */
    fun statistics(catalog: List<Breed>, species: Species? = null): CollectionStatistics {
        val catalogIds = catalog.filter { it.countsInCollection && (species == null || it.species == species) }
            .map { it.id }.toSet()
        val encounterIds = catalog.filter { species == null || it.species == species }.map { it.id }.toSet()
        return CollectionStatistics(discoveredCount(catalogIds), catalogIds.size,
            encounters.count { it.breedId in encounterIds })
    }
}

data class CollectionStatistics(val discovered: Int, val totalBreeds: Int, val encounterCount: Int)

/** One persistent encounter store shared by all normal breeds and special albums. */
class EncounterRepository(private val dao: EncounterDao, private val context: Context) {
    internal val photoContext: Context get() = context.applicationContext
    private val achievementCatalog by lazy { MockBreedRepository(context.resources).getCollectionEntries() }
    private val initialization = Mutex()
    private var achievementsInitialized = false
    val achievements = flow {
        initializeAchievements()
        emitAll(combine(dao.observeAll(), dao.observeAchievementPhotos(), dao.observeAchievementUnlocks()) { records, photos, unlocks ->
            val byId = photos.associateBy { it.encounterId }
            AchievementData(AchievementRules.evaluate(achievementCatalog, records, photos), unlocks,
                records.count { it.photoUri != null && byId[it.id]?.fingerprint == null }, true)
        })
    }

    /** Existing badge state was derived from encounters, not separately persisted. Backfill quietly, without invented dates. */
    suspend fun initializeAchievements() = initialization.withLock {
        if (!achievementsInitialized) {
            val known = dao.achievementPhotos().map { it.encounterId }.toSet()
            dao.allRecords().filter { it.photoUri != null && it.id !in known }.forEach { record ->
                dao.insertAchievementPhoto(AchievementPhoto(record.id, fingerprint(requireNotNull(record.photoUri))))
            }
            dao.reconcileAchievements(achievementCatalog)
            achievementsInitialized = true
        }
    }

    suspend fun claimAchievementAnnouncement(id: String): Boolean = dao.claimAchievementAnnouncement(id) == 1

    private suspend fun fingerprint(reference: String): String? = withContext(Dispatchers.IO) {
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            requireNotNull(context.contentResolver.openInputStream(Uri.parse(reference))).use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val size = input.read(buffer)
                    if (size < 0) break
                    digest.update(buffer, 0, size)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null } // A missing historical photo contributes no fabricated unique-photo progress.
    }
    val collection = combine(
        combine(dao.observeAll(), dao.observeCovers(), dao.observeAlbums(), dao.observeMembers()) { records, covers, albums, members -> CollectionData(records, covers, true, albums, members) },
        dao.observePhotoSortModes(), dao.observePhotoOrder()
    ) { data, modes, order -> data.copy(photoSortModes = modes, photoOrder = order) }
    suspend fun setPhotoSortMode(key: String, mode: PhotoSortMode) = dao.setPhotoSortMode(key, mode)
    suspend fun reorderPhotos(key: String, ids: List<String>) = dao.reorderPhotos(key, ids)
    suspend fun createAlbum(name: String): String {
        val id = UUID.randomUUID().toString()
        dao.createAlbum(id, name)
        return id
    }
    suspend fun renameAlbum(id: String, name: String) = dao.renameAlbum(id, name)
    suspend fun reorderAlbum(id: String, offset: Int) = dao.reorderAlbum(id, offset)
    suspend fun reorderAlbums(ids: List<String>) = dao.reorderAlbums(ids)
    suspend fun unassignPhotos(ids: List<String>) = dao.unassignPhotos(ids)
    suspend fun deletePhotos(ids: List<String>) = cleanDeletedPhotos(dao.deleteOtherPhotos(ids))
    suspend fun movePhotos(ids: List<String>, albumId: String) = dao.movePhotos(ids, albumId)
    suspend fun albumCover(id: String, encounterId: String) = dao.albumCover(id, encounterId)
    suspend fun deleteAlbum(id: String, deletePhotos: Boolean) {
        cleanDeletedPhotos(dao.deleteAlbum(id, deletePhotos))
    }
    private suspend fun cleanDeletedPhotos(records: List<EncounterRecord>) {
        records.mapNotNull { it.photoUri }.distinct().forEach {
            if (dao.photoReferences(it) == 0) deletePrivatePhoto(it)
        }
    }
    fun observeEncounter(id: String) = dao.observeById(id)
    fun observeByBreed(id: String) = dao.observeByBreed(id)
    fun observeCounts() = dao.observeCounts()

    suspend fun create(record: EncounterRecord, recognized: Boolean = false): Pair<EncounterRecord, Boolean> {
        require(record.breedId in MockBreedRepository.collectionIds)
        initializeAchievements()
        val stored = record.copy(photoUri = record.photoUri?.let { retainPhoto(it) })
        try {
            val hash = stored.photoUri?.let { fingerprint(it) }
            val savedAt = System.currentTimeMillis()
            val recognizedCatDog = recognized && achievementCatalog.any { it.id == stored.breedId && it.species != null }
            val metadata = stored.photoUri?.let { AchievementPhoto(stored.id, hash,
                savedAt.takeIf { recognizedCatDog }, ZoneId.systemDefault().id.takeIf { recognizedCatDog }) }
            return stored to dao.createTracked(stored, metadata, achievementCatalog, savedAt)
        } catch (error: Exception) {
            if (stored.photoUri != record.photoUri) stored.photoUri?.let { deletePrivatePhoto(it) }
            throw error
        }
    }

    suspend fun update(record: EncounterRecord) {
        initializeAchievements()
        val old = requireNotNull(dao.get(record.id))
        require(record.breedId in MockBreedRepository.collectionIds)
        dao.updateTracked(record.copy(photoUri = old.photoUri, createdAt = old.createdAt), achievementCatalog, System.currentTimeMillis())
    }
    suspend fun selectCover(breedId: String, encounterId: String, region: CropRegion? = null) = dao.selectCover(breedId, encounterId, region)
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
