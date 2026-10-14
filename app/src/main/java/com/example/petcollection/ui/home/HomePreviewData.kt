package com.example.petcollection.ui.home

import androidx.annotation.StringRes
import com.example.petcollection.R
import com.example.petcollection.data.Breed
import com.example.petcollection.data.CollectionData
import com.example.petcollection.data.Species

// Screen input; production values come from CollectionData, not preview fixtures.
data class CollectionCategory(val id: String, @param:StringRes val nameRes: Int, val total: Int,
    val collected: Int = 0, val encounterCount: Int = 0)
data class AchievementPreview(@param:StringRes val nameRes: Int, val breedThreshold: Int? = null) {
    fun isUnlocked(collected: Int, total: Int) = total > 0 && collected >= (breedThreshold ?: total)
}

object HomePreviewData {
    fun categories(catalog: List<Breed>): List<CollectionCategory> = Species.entries.map { species ->
        val statistics = CollectionData().statistics(catalog, species)
        CollectionCategory(if (species == Species.CAT) "cat" else "dog",
            if (species == Species.CAT) R.string.cat_collection else R.string.dog_collection,
            statistics.totalBreeds)
    }
    val achievements = listOf(
        AchievementPreview(R.string.first_discovery, 1),
        AchievementPreview(R.string.beginner_collector, 5),
        AchievementPreview(R.string.pet_explorer, 10),
        AchievementPreview(R.string.experienced_collector, 20),
        AchievementPreview(R.string.master_collector, 30),
        AchievementPreview(R.string.complete_collection)
    )
}


