package com.example.petcollection.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.petcollection.R
import java.time.Instant
import java.time.ZoneId

/** Stable IDs are independent of translated names and the legacy preview badges. */
enum class Achievement(val title: Int, val description: Int, val glyph: String, val hidden: Boolean = false) {
    FIRST(R.string.ach_first, R.string.ach_first_description, "✦"),
    EXPLORER(R.string.ach_explorer, R.string.ach_explorer_description, "⌖"),
    SHUTTER(R.string.ach_shutter, R.string.ach_shutter_description, "▣"),
    COMPLETE(R.string.ach_complete, R.string.ach_complete_description, "♛"),
    COLORS(R.string.ach_colors, R.string.ach_colors_description, "✿"),
    CATS(R.string.ach_cats, R.string.ach_cats_description, "♧"),
    DOGS(R.string.ach_dogs, R.string.ach_dogs_description, "♜"),
    CAT_OBSERVER(R.string.ach_cat_observer, R.string.ach_cat_observer_description, "♡"),
    DOG_FRIEND(R.string.ach_dog_friend, R.string.ach_dog_friend_description, "♬"),
    RARE(R.string.ach_rare, R.string.ach_rare_description, "★"),
    PLACES(R.string.ach_places, R.string.ach_places_description, "◎"),
    MONKEY(R.string.ach_monkey, R.string.ach_monkey_description, "☀", true),
    NIGHT(R.string.ach_night, R.string.ach_night_description, "☾", true),
    REPEAT(R.string.ach_repeat, R.string.ach_repeat_description, "↻", true),
    TORTOISESHELL(R.string.ach_tortoiseshell, R.string.ach_tortoiseshell_description, "❀", true)
}

@Entity(tableName = "achievement_unlocks")
data class AchievementUnlock(@PrimaryKey val id: String, val unlockedAt: Long?, val announced: Boolean)

/** Separate metadata leaves every existing encounter and its editable/EXIF date intact. */
@Entity(tableName = "achievement_photos")
data class AchievementPhoto(@PrimaryKey val encounterId: String, val fingerprint: String?,
    val recognizedSavedAt: Long? = null, val savedZoneId: String? = null)

data class AchievementProgress(val achievement: Achievement, val current: Int?, val target: Int) {
    val fulfilled: Boolean get() = current != null && target > 0 && current >= target
}

data class AchievementData(val progress: List<AchievementProgress> = emptyList(),
    val unlocks: List<AchievementUnlock> = emptyList(), val unreadablePhotos: Int = 0, val loaded: Boolean = false) {
    val unlockedCount: Int get() = unlocks.count { row -> Achievement.entries.any { it.name == row.id } }
    fun unlock(achievement: Achievement) = unlocks.find { it.id == achievement.name }
}

/** All completion conditions use the live catalog, never a historical hard-coded total. */
object AchievementRules {
    fun evaluate(catalog: List<Breed>, records: List<EncounterRecord>, photos: List<AchievementPhoto>): List<AchievementProgress> {
        val cats = catalog.filter { it.isCollectible && !it.isLegacyBreed && it.species == Species.CAT && !it.isCoatCategory }.map { it.id }.toSet()
        val dogs = catalog.filter { it.isCollectible && !it.isLegacyBreed && it.species == Species.DOG && !it.isCoatCategory }.map { it.id }.toSet()
        val breeds = cats + dogs
        val coats = catalog.filter { it.isCoatCategory }.map { it.id }.toSet()
        val catEntries = cats + coats
        val complete = catalog.filter { it.countsInCollection }.map { it.id }.toSet()
        val discovered = records.map { it.breedId }.toSet()
        val byId = photos.associateBy { it.encounterId }
        val photoRecords = records.filter { it.photoUri != null }
        val unique = photoRecords.mapNotNull { byId[it.id]?.fingerprint }.toSet().size
        val repeats = photoRecords.filter { it.breedId in breeds }.groupBy { it.breedId }
            .values.maxOfOrNull { group -> group.mapNotNull { byId[it.id]?.fingerprint }.toSet().size } ?: 0
        // Only a successful recognized save writes these fields. Opening a page cannot create an event.
        val night = photoRecords.any { record ->
            val photo = byId[record.id]
            val time = photo?.recognizedSavedAt
            record.breedId in (breeds + coats + setOf("other_cat", "other_dog")) && time != null &&
                runCatching { Instant.ofEpochMilli(time).atZone(ZoneId.of(requireNotNull(photo.savedZoneId))).hour < 5 }.getOrDefault(false)
        }
        fun count(ids: Set<String>) = (ids intersect discovered).size
        return Achievement.entries.map { achievement ->
            val (current, target) = when (achievement) {
                Achievement.FIRST -> count(complete) to 1
                Achievement.EXPLORER -> count(breeds) to 10
                Achievement.SHUTTER -> unique to 50
                Achievement.COMPLETE -> count(complete) to complete.size
                Achievement.COLORS -> count(coats) to coats.size
                Achievement.CATS -> count(catEntries) to catEntries.size
                Achievement.DOGS -> count(dogs) to dogs.size
                Achievement.CAT_OBSERVER -> count(catEntries) to 5
                Achievement.DOG_FRIEND -> count(dogs) to 5
                Achievement.RARE -> catalog.count { it.id in breeds && it.rarity?.stars == 5 && it.id in discovered } to 1
                Achievement.PLACES -> null to 5 // Free-form location text cannot establish distinct real places.
                Achievement.MONKEY -> (if (MockBreedRepository.MONKEY_ID in discovered) 1 else 0) to 1
                Achievement.NIGHT -> (if (night) 1 else 0) to 1
                Achievement.REPEAT -> repeats to 10
                Achievement.TORTOISESHELL -> (if ("coat_tortoiseshell" in discovered) 1 else 0) to 1
            }
            AchievementProgress(achievement, current?.coerceAtMost(target), target)
        }
    }
}
