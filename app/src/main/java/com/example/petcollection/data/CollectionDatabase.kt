package com.example.petcollection.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "other_albums")
data class OtherAlbum(@PrimaryKey val id: String, val name: String, val position: Int,
    val coverId: String? = null)

@Entity(tableName = "other_album_members", foreignKeys = [
    ForeignKey(entity = OtherAlbum::class, parentColumns = ["id"], childColumns = ["albumId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = EncounterRecord::class, parentColumns = ["id"], childColumns = ["encounterId"], onDelete = ForeignKey.CASCADE)
], indices = [Index("albumId")])
data class OtherAlbumMember(@PrimaryKey val encounterId: String, val albumId: String)

@Entity(tableName = "breed_covers", foreignKeys = [ForeignKey(
    entity = EncounterRecord::class, parentColumns = ["id"], childColumns = ["encounterId"],
    onDelete = ForeignKey.CASCADE
)], indices = [Index("encounterId")])
data class BreedCover(@PrimaryKey val breedId: String, val encounterId: String)

data class EncounterCount(val breedId: String, val count: Int)

class EncounterConverters {
    @TypeConverter fun encode(value: LocalDateTime?): String? = value?.toString()
    @TypeConverter fun decode(value: String?): LocalDateTime? = value?.let(LocalDateTime::parse)
}

@Dao
abstract class EncounterDao {
    @Query("SELECT * FROM achievement_unlocks ORDER BY unlockedAt, id")
    abstract fun observeAchievementUnlocks(): Flow<List<AchievementUnlock>>
    @Query("SELECT * FROM achievement_photos")
    abstract fun observeAchievementPhotos(): Flow<List<AchievementPhoto>>
    @Query("SELECT * FROM achievement_unlocks")
    abstract suspend fun achievementUnlocks(): List<AchievementUnlock>
    @Query("SELECT * FROM achievement_photos")
    abstract suspend fun achievementPhotos(): List<AchievementPhoto>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertAchievement(unlock: AchievementUnlock)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertAchievementPhoto(photo: AchievementPhoto)
    @Query("UPDATE achievement_unlocks SET announced = 1 WHERE id = :id AND announced = 0")
    abstract suspend fun claimAchievementAnnouncement(id: String): Int

    @Transaction
    open suspend fun reconcileAchievements(catalog: List<Breed>, unlockedAt: Long? = null, announce: Boolean = false) {
        val existing = achievementUnlocks().map { it.id }.toSet()
        AchievementRules.evaluate(catalog, allRecords(), achievementPhotos()).filter { it.fulfilled && it.achievement.name !in existing }
            .forEach { insertAchievement(AchievementUnlock(it.achievement.name, unlockedAt, !announce)) }
    }

    @Transaction
    open suspend fun createTracked(record: EncounterRecord, photo: AchievementPhoto?, catalog: List<Breed>, savedAt: Long): Boolean {
        val first = create(record)
        photo?.let { insertAchievementPhoto(it) }
        reconcileAchievements(catalog, savedAt, true)
        return first
    }

    @Transaction
    open suspend fun updateTracked(record: EncounterRecord, catalog: List<Breed>, savedAt: Long) {
        updateAndRepairCovers(record)
        reconcileAchievements(catalog, savedAt, true)
    }
    @Query("SELECT * FROM photo_sort_modes")
    abstract fun observePhotoSortModes(): Flow<List<PhotoSortSetting>>
    @Query("SELECT * FROM photo_custom_order ORDER BY position, encounterId")
    abstract fun observePhotoOrder(): Flow<List<PhotoOrder>>
    @Query("SELECT * FROM photo_custom_order WHERE albumKey = :key ORDER BY position, encounterId")
    abstract suspend fun photoOrder(key: String): List<PhotoOrder>
    @Query("SELECT * FROM photo_custom_order ORDER BY position, encounterId")
    abstract suspend fun allPhotoOrder(): List<PhotoOrder>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putPhotoSortMode(setting: PhotoSortSetting)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putPhotoOrder(order: List<PhotoOrder>)
    @Query("DELETE FROM photo_custom_order WHERE albumKey = :key")
    abstract suspend fun clearPhotoOrder(key: String)
    @Query("DELETE FROM photo_sort_modes WHERE albumKey = :key")
    abstract suspend fun clearPhotoSortMode(key: String)
    @Query("SELECT * FROM encounters ORDER BY createdAt, id")
    abstract suspend fun allRecords(): List<EncounterRecord>
    @Query("SELECT * FROM other_album_members")
    abstract suspend fun allMembers(): List<OtherAlbumMember>

    private suspend fun albumRecords(key: String): List<EncounterRecord> {
        val records = allRecords()
        return when {
            key.startsWith("breed:") -> records.filter { it.breedId == key.removePrefix("breed:") }
            key == PhotoAlbumKey.UNASSIGNED -> {
                val assigned = allMembers().map { it.encounterId }.toSet()
                records.filter { it.breedId == "unregistered" && it.photoUri != null && it.id !in assigned }
            }
            key.startsWith("other:") -> {
                val id = key.removePrefix("other:")
                require(albums().any { it.id == id })
                val members = members(id).map { it.encounterId }.toSet()
                records.filter { it.breedId == "unregistered" && it.photoUri != null && it.id in members }
            }
            else -> error("Unknown album")
        }
    }

    /** Run in the same transaction as membership changes. Preserve survivors and append arrivals. */
    private suspend fun reconcilePhotoOrders() {
        val records = allRecords()
        val memberAlbums = allMembers().associate { it.encounterId to it.albumId }
        val grouped = allPhotoOrder().map { it.albumKey }.distinct()
            .associateWith { emptyList<EncounterRecord>() }.toMutableMap()
        records.groupBy { PhotoAlbumKey.breed(it.breedId) }.forEach { (key, photos) -> grouped[key] = photos }
        grouped[PhotoAlbumKey.UNASSIGNED] = emptyList()
        albums().forEach { grouped[PhotoAlbumKey.other(it.id)] = emptyList() }
        records.filter { it.breedId == "unregistered" && it.photoUri != null }.groupBy {
            memberAlbums[it.id]?.let(PhotoAlbumKey::other) ?: PhotoAlbumKey.UNASSIGNED
        }.forEach { (key, photos) -> grouped[key] = photos }
        for ((key, photos) in grouped) {
            val valid = photos.map { it.id }.toSet()
            val current = photoOrder(key).map { it.encounterId }
            val existing = current.filter { it in valid }
            val existingIds = existing.toSet()
            val ids = existing + photos.map { it.id }.filter { it !in existingIds }
            if (ids != current) {
                clearPhotoOrder(key)
                putPhotoOrder(ids.mapIndexed { index, id -> PhotoOrder(key, id, index) })
            }
        }
    }

    @Transaction open suspend fun setPhotoSortMode(key: String, mode: PhotoSortMode) {
        albumRecords(key) // Validate the album before persisting its mode.
        putPhotoSortMode(PhotoSortSetting(key, mode.name))
    }

    @Transaction open suspend fun reorderPhotos(key: String, ids: List<String>) {
        val valid = albumRecords(key).map { it.id }.toSet()
        require(ids.size == valid.size && ids.toSet() == valid)
        clearPhotoOrder(key)
        putPhotoOrder(ids.mapIndexed { index, id -> PhotoOrder(key, id, index) })
        putPhotoSortMode(PhotoSortSetting(key, PhotoSortMode.CUSTOM.name))
    }
    @Query("SELECT * FROM other_albums ORDER BY position, id")
    abstract fun observeAlbums(): Flow<List<OtherAlbum>>
    @Query("SELECT * FROM other_album_members")
    abstract fun observeMembers(): Flow<List<OtherAlbumMember>>
    @Query("SELECT * FROM other_albums ORDER BY position, id")
    abstract suspend fun albums(): List<OtherAlbum>
    @Query("SELECT * FROM other_album_members WHERE albumId = :id")
    abstract suspend fun members(id: String): List<OtherAlbumMember>
    @Insert abstract suspend fun insertAlbum(album: OtherAlbum)
    @Update abstract suspend fun updateAlbum(album: OtherAlbum)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun putMember(member: OtherAlbumMember)
    @Query("DELETE FROM other_album_members WHERE encounterId = :id")
    abstract suspend fun removeMember(id: String)
    @Query("DELETE FROM other_albums WHERE id = :id")
    abstract suspend fun removeAlbum(id: String)
    @Query("UPDATE other_albums SET coverId = NULL WHERE coverId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM other_album_members m INNER JOIN encounters e ON e.id = m.encounterId WHERE m.albumId = other_albums.id AND m.encounterId = other_albums.coverId AND e.breedId = 'unregistered' AND e.photoUri IS NOT NULL)")
    abstract suspend fun repairAlbumCovers()
    @Transaction open suspend fun createAlbum(id: String, name: String) {
        require(name.isNotBlank())
        insertAlbum(OtherAlbum(id, name.trim(), (albums().maxOfOrNull { it.position } ?: -1) + 1))
    }
    @Transaction open suspend fun renameAlbum(id: String, name: String) {
        require(name.isNotBlank())
        updateAlbum(requireNotNull(albums().find { it.id == id }).copy(name = name.trim()))
    }
    @Transaction open suspend fun reorderAlbum(id: String, offset: Int) {
        val ordered = albums().toMutableList()
        val index = ordered.indexOfFirst { it.id == id }
        require(index >= 0)
        val target = (index + offset).coerceIn(0, ordered.lastIndex)
        ordered.add(target, ordered.removeAt(index))
        ordered.forEachIndexed { position, album -> updateAlbum(album.copy(position = position)) }
    }
    @Transaction open suspend fun reorderAlbums(ids: List<String>) {
        val current = albums()
        require(ids.size == current.size && ids.toSet() == current.map { it.id }.toSet())
        val byId = current.associateBy { it.id }
        ids.forEachIndexed { index, id -> updateAlbum(byId.getValue(id).copy(position = index)) }
    }
    private suspend fun requireOtherPhotos(ids: List<String>) {
        ids.forEach { id ->
            val record = requireNotNull(get(id))
            require(record.breedId == "unregistered" && record.photoUri != null)
        }
    }
    @Transaction open suspend fun unassignPhotos(ids: List<String>) {
        requireOtherPhotos(ids)
        ids.forEach { removeMember(it) }
        repairAlbumCovers()
        reconcilePhotoOrders()
    }
    @Transaction open suspend fun deleteOtherPhotos(ids: List<String>): List<EncounterRecord> {
        requireOtherPhotos(ids)
        return ids.distinct().mapNotNull { deleteAndRepairCover(it) }
    }
    @Transaction open suspend fun movePhotos(ids: List<String>, albumId: String) {
        require(albums().any { it.id == albumId })
        requireOtherPhotos(ids)
        ids.forEach { putMember(OtherAlbumMember(it, albumId)) }
        repairAlbumCovers()
        reconcilePhotoOrders()
    }
    @Transaction open suspend fun albumCover(id: String, encounterId: String) {
        require(members(id).any { it.encounterId == encounterId })
        require(get(encounterId)?.photoUri != null)
        updateAlbum(requireNotNull(albums().find { it.id == id }).copy(coverId = encounterId))
    }
    @Transaction open suspend fun deleteAlbum(id: String, deletePhotos: Boolean): List<EncounterRecord> {
        require(albums().any { it.id == id })
        val deleted = if (deletePhotos) members(id).mapNotNull { deleteAndRepairCover(it.encounterId) } else emptyList()
        removeAlbum(id)
        clearPhotoOrder(PhotoAlbumKey.other(id))
        clearPhotoSortMode(PhotoAlbumKey.other(id))
        reconcilePhotoOrders()
        return deleted
    }
    @Query("SELECT * FROM encounters ORDER BY createdAt, id")
    abstract fun observeAll(): Flow<List<EncounterRecord>>
    @Query("SELECT * FROM encounters WHERE breedId = :breedId ORDER BY encounteredAt DESC, createdAt DESC, id")
    abstract fun observeByBreed(breedId: String): Flow<List<EncounterRecord>>
    @Query("SELECT * FROM encounters WHERE id = :id")
    abstract fun observeById(id: String): Flow<EncounterRecord?>
    @Query("SELECT breedId, COUNT(*) AS count FROM encounters GROUP BY breedId")
    abstract fun observeCounts(): Flow<List<EncounterCount>>
    @Query("SELECT DISTINCT breedId FROM encounters WHERE breedId IN (:normalIds)")
    abstract fun observeDiscovered(normalIds: List<String>): Flow<List<String>>
    @Query("SELECT * FROM breed_covers")
    abstract fun observeCovers(): Flow<List<BreedCover>>
    @Query("SELECT * FROM encounters WHERE id = :id")
    abstract suspend fun get(id: String): EncounterRecord?
    @Query("SELECT COUNT(*) FROM encounters WHERE breedId = :breedId")
    abstract suspend fun count(breedId: String): Int
    @Query("SELECT COUNT(*) FROM encounters WHERE photoUri = :uri")
    abstract suspend fun photoReferences(uri: String): Int
    @Insert abstract suspend fun insert(record: EncounterRecord)
    @Update abstract suspend fun update(record: EncounterRecord): Int
    @Query("DELETE FROM encounters WHERE id = :id")
    abstract suspend fun delete(id: String)
    @Query("SELECT * FROM breed_covers WHERE breedId = :breedId")
    abstract suspend fun cover(breedId: String): BreedCover?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putCover(cover: BreedCover)
    @Query("DELETE FROM breed_covers WHERE breedId = :breedId")
    abstract suspend fun deleteCover(breedId: String)
    @Query("SELECT * FROM encounters WHERE breedId = :breedId AND photoUri IS NOT NULL ORDER BY createdAt, id LIMIT 1")
    abstract suspend fun firstPhoto(breedId: String): EncounterRecord?

    @Transaction
    open suspend fun updateAndRepairCovers(record: EncounterRecord) {
        val old = requireNotNull(get(record.id))
        check(update(record) == 1)
        if (old.breedId != record.breedId) {
            removeMember(record.id)
            repairAlbumCovers()
            if (cover(old.breedId)?.encounterId == record.id) {
                deleteCover(old.breedId)
                firstPhoto(old.breedId)?.let { putCover(BreedCover(old.breedId, it.id)) }
            }
            if (record.photoUri != null && cover(record.breedId) == null) {
                putCover(BreedCover(record.breedId, record.id))
            }
        }
        reconcilePhotoOrders()
    }

    @Transaction
    open suspend fun create(record: EncounterRecord): Boolean {
        val first = count(record.breedId) == 0
        insert(record)
        reconcilePhotoOrders()
        if (record.photoUri != null && cover(record.breedId) == null) putCover(BreedCover(record.breedId, record.id))
        return first
    }

    @Transaction
    open suspend fun selectCover(breedId: String, encounterId: String) {
        val encounter = requireNotNull(get(encounterId))
        require(encounter.breedId == breedId && encounter.photoUri != null)
        putCover(BreedCover(breedId, encounterId))
    }

    @Transaction
    open suspend fun deleteAndRepairCover(id: String): EncounterRecord? {
        val record = get(id) ?: return null
        val wasCover = cover(record.breedId)?.encounterId == id
        delete(id) // The foreign key clears any cover that points at this row.
        repairAlbumCovers()
        if (wasCover) firstPhoto(record.breedId)?.let { putCover(BreedCover(record.breedId, it.id)) }
        return record
    }
}

@Database(entities = [EncounterRecord::class, BreedCover::class, OtherAlbum::class, OtherAlbumMember::class,
    PhotoSortSetting::class, PhotoOrder::class, AchievementUnlock::class, AchievementPhoto::class], version = 4, exportSchema = true)
@TypeConverters(EncounterConverters::class)
abstract class CollectionDatabase : RoomDatabase() {
    abstract fun encounters(): EncounterDao
    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE achievement_unlocks (id TEXT NOT NULL PRIMARY KEY, unlockedAt INTEGER, announced INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE achievement_photos (encounterId TEXT NOT NULL PRIMARY KEY, fingerprint TEXT, recognizedSavedAt INTEGER, savedZoneId TEXT)")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Back up FK children before rebuilding the existing date column as nullable.
                // Dropping encounters with live children would cascade-delete their contents.
                db.execSQL("CREATE TEMP TABLE saved_covers AS SELECT * FROM breed_covers")
                db.execSQL("CREATE TEMP TABLE saved_members AS SELECT * FROM other_album_members")
                db.execSQL("DROP TABLE breed_covers")
                db.execSQL("DROP TABLE other_album_members")
                db.execSQL("CREATE TABLE encounters_new (breedId TEXT NOT NULL, photoUri TEXT, encounteredAt TEXT, timeZoneId TEXT NOT NULL, id TEXT NOT NULL PRIMARY KEY, petName TEXT, location TEXT, note TEXT, createdAt INTEGER NOT NULL)")
                db.execSQL("INSERT INTO encounters_new SELECT breedId, photoUri, encounteredAt, timeZoneId, id, petName, location, note, createdAt FROM encounters")
                db.execSQL("DROP TABLE encounters")
                db.execSQL("ALTER TABLE encounters_new RENAME TO encounters")
                db.execSQL("CREATE INDEX index_encounters_breedId ON encounters(breedId)")
                db.execSQL("CREATE TABLE breed_covers (breedId TEXT NOT NULL PRIMARY KEY, encounterId TEXT NOT NULL, FOREIGN KEY(encounterId) REFERENCES encounters(id) ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX index_breed_covers_encounterId ON breed_covers(encounterId)")
                db.execSQL("INSERT INTO breed_covers SELECT * FROM saved_covers")
                db.execSQL("CREATE TABLE other_album_members (encounterId TEXT NOT NULL PRIMARY KEY, albumId TEXT NOT NULL, FOREIGN KEY(albumId) REFERENCES other_albums(id) ON DELETE CASCADE, FOREIGN KEY(encounterId) REFERENCES encounters(id) ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX index_other_album_members_albumId ON other_album_members(albumId)")
                db.execSQL("INSERT INTO other_album_members SELECT * FROM saved_members")
                db.execSQL("DROP TABLE saved_covers")
                db.execSQL("DROP TABLE saved_members")
                db.execSQL("CREATE TABLE photo_sort_modes (albumKey TEXT NOT NULL PRIMARY KEY, mode TEXT NOT NULL)")
                db.execSQL("CREATE TABLE photo_custom_order (albumKey TEXT NOT NULL, encounterId TEXT NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(albumKey, encounterId), FOREIGN KEY(encounterId) REFERENCES encounters(id) ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX index_photo_custom_order_encounterId ON photo_custom_order(encounterId)")
                // Seed the old visible order once; later photos are appended by DAO transactions.
                db.query("SELECT id, breedId FROM encounters ORDER BY encounteredAt DESC, createdAt DESC, id").use { cursor ->
                    val positions = mutableMapOf<String, Int>()
                    while (cursor.moveToNext()) {
                        val key = PhotoAlbumKey.breed(cursor.getString(1))
                        val position = positions[key] ?: 0
                        db.execSQL("INSERT INTO photo_custom_order VALUES (?, ?, ?)", arrayOf(key, cursor.getString(0), position))
                        positions[key] = position + 1
                    }
                }
                db.query("SELECT e.id, m.albumId FROM encounters e LEFT JOIN other_album_members m ON m.encounterId = e.id WHERE e.breedId = 'unregistered' AND e.photoUri IS NOT NULL ORDER BY e.encounteredAt DESC, e.createdAt DESC, e.id").use { cursor ->
                    val positions = mutableMapOf<String, Int>()
                    while (cursor.moveToNext()) {
                        val key = if (cursor.isNull(1)) PhotoAlbumKey.UNASSIGNED else PhotoAlbumKey.other(cursor.getString(1))
                        val position = positions[key] ?: 0
                        db.execSQL("INSERT INTO photo_custom_order VALUES (?, ?, ?)", arrayOf(key, cursor.getString(0), position))
                        positions[key] = position + 1
                    }
                }
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS other_albums (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, position INTEGER NOT NULL, coverId TEXT)")
                db.execSQL("CREATE TABLE IF NOT EXISTS other_album_members (encounterId TEXT NOT NULL PRIMARY KEY, albumId TEXT NOT NULL, FOREIGN KEY(albumId) REFERENCES other_albums(id) ON DELETE CASCADE, FOREIGN KEY(encounterId) REFERENCES encounters(id) ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_other_album_members_albumId ON other_album_members(albumId)")
            }
        }
        @Volatile private var instance: CollectionDatabase? = null
        fun getInstance(context: Context): CollectionDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, CollectionDatabase::class.java,
                "collection.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
        }
    }
}
