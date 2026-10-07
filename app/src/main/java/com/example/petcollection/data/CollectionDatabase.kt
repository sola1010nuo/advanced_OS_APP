package com.example.petcollection.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

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
    @Query("SELECT * FROM encounters WHERE breedId = :breedId AND photoUri IS NOT NULL ORDER BY createdAt, id LIMIT 1")
    abstract suspend fun firstPhoto(breedId: String): EncounterRecord?

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
        if (wasCover) firstPhoto(record.breedId)?.let { putCover(BreedCover(record.breedId, it.id)) }
        return record
    }
}

@Database(entities = [EncounterRecord::class, BreedCover::class], version = 1, exportSchema = true)
@TypeConverters(EncounterConverters::class)
abstract class CollectionDatabase : RoomDatabase() {
    abstract fun encounters(): EncounterDao
    companion object {
        @Volatile private var instance: CollectionDatabase? = null
        fun getInstance(context: Context): CollectionDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, CollectionDatabase::class.java,
                "collection.db").build().also { instance = it }
        }
    }
}
