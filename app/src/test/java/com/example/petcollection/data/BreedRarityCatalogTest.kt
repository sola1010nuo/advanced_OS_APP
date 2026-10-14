package com.example.petcollection.data

import com.example.petcollection.ui.home.HomePreviewData
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class BreedRarityCatalogTest {
    @Test fun profilesCoverExactlyTheCurrentModelCatalogAndAllTiers() {
        val root = File("src/main/assets").takeIf { it.isDirectory } ?: File("app/src/main/assets")
        for ((file, profiles) in listOf("cat_labels.txt" to BreedRarityCatalog.cats, "dog_labels.txt" to BreedRarityCatalog.dogs)) {
            val ids = File(root, file).readLines().map(ModelBreedMapping::canonicalId)
            assertEquals(ids.toSet(), profiles.keys)
            assertEquals(ids.size, profiles.size)
            assertEquals(BreedRarity.entries.toSet(), profiles.values.map { it.rarity }.toSet())
            profiles.values.groupBy { it.rarity }.forEach { (_, values) ->
                assertEquals(values.size, values.map { it.order }.distinct().size)
            }
        }
        assertEquals(BreedRarity.EPIC, BreedRarityCatalog.dogs.getValue("boxer").rarity)
        assertEquals(BreedRarity.LEGENDARY, BreedRarityCatalog.dogs.getValue("german_shorthaired_pointer").rarity)
        assertTrue(BreedRarityCatalog.dogs.keys.intersect(MockBreedRepository.legacyBreedIds).isEmpty())
    }

    @Test fun orderingDoesNotDependOnLanguageOrDiscoveryState() {
        val breeds = BreedRarityCatalog.dogs.map { (id, profile) ->
            Breed(id, id.reversed(), Species.DOG, "", "", "", "", "", rarity = profile.rarity, rarityOrder = profile.order)
        }
        val expected = breeds.sortedWith(BreedRarityCatalog.order).map { it.id }
        val changed = breeds.reversed().mapIndexed { index, breed -> breed.copy(name = "名稱$index", isDiscovered = index % 2 == 0) }
        assertEquals(expected, changed.sortedWith(BreedRarityCatalog.order).map { it.id })
        assertEquals("poodle", expected.first())
        assertEquals("german_shorthaired_pointer", expected.last())
    }

    @Test fun archivedPhotosRemainAccessibleWithoutContributingToCompletion() {
        val active = Breed("boxer", "Boxer", Species.DOG, "", "", "", "", "")
        val legacy = active.copy(id = "english_setter", isCollectible = false, isLegacyBreed = true, isHiddenUntilDiscovered = true)
        val data = CollectionData(listOf(EncounterRecord(active.id, "file:///active"), EncounterRecord(legacy.id, "file:///legacy")))
        assertFalse(legacy.isVisibleInCollection)
        assertTrue(data.applyTo(legacy).isVisibleInCollection)
        assertEquals("file:///legacy", data.applyTo(legacy).coverPhoto!!.uri)
        assertEquals(CollectionStatistics(1, 1, 2), data.statistics(listOf(active, legacy), Species.DOG))
        assertTrue(legacy.id in MockBreedRepository.collectionIds)
        assertFalse(legacy.id in MockBreedRepository.normalBreedIds)
    }

    @Test fun badgesFollowTheCurrentCompletionTotal() {
        val badges = HomePreviewData.achievements
        assertFalse(badges.first().isUnlocked(0, 65))
        assertTrue(badges.first().isUnlocked(1, 65))
        assertFalse(badges.last().isUnlocked(64, 65))
        assertTrue(badges.last().isUnlocked(65, 65))
    }
}
