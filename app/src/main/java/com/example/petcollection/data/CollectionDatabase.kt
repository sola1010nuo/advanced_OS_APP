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
    @TypeConverter fun encode(value: LocalDateTime): String = value.toString()
    @TypeConverter fun decode(value: String): LocalDateTime = LocalDateTime.parse(value)
}

@Dao
abstract class EncounterDao {
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
    }

    @Transaction
    open suspend fun create(record: EncounterRecord): Boolean {
        val first = count(record.breedId) == 0
        insert(record)
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

@Database(entities = [EncounterRecord::class, BreedCover::class, OtherAlbum::class, OtherAlbumMember::class], version = 2, exportSchema = true)
@TypeConverters(EncounterConverters::class)
abstract class CollectionDatabase : RoomDatabase() {
    abstract fun encounters(): EncounterDao
    companion object {
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
                "collection.db").addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
