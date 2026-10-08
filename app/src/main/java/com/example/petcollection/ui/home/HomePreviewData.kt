package com.example.petcollection.ui.home

import androidx.annotation.StringRes
import com.example.petcollection.R
import com.example.petcollection.data.MockBreedRepository
import com.example.petcollection.data.Species

// Screen input; production values come from CollectionData, not preview fixtures.
data class CollectionCategory(val id: String, @param:StringRes val nameRes: Int, val total: Int,
    val collected: Int = 0, val encounterCount: Int = 0)
data class AchievementPreview(@param:StringRes val nameRes: Int, val breedThreshold: Int? = null)

object HomePreviewData {
    val categories: List<CollectionCategory> = listOf(
        CollectionCategory("cat", R.string.cat_collection, MockBreedRepository.total(Species.CAT)),
        CollectionCategory("dog", R.string.dog_collection, MockBreedRepository.total(Species.DOG))
    )
    val total: Int get() = MockBreedRepository.normalBreedIds.size
    val collected: Int get() = categories.sumOf { it.collected }
    val achievements = listOf(
        AchievementPreview(R.string.first_discovery, 1),
        AchievementPreview(R.string.beginner_collector, 5),
        AchievementPreview(R.string.pet_explorer, 10),
        AchievementPreview(R.string.experienced_collector, 20),
        AchievementPreview(R.string.master_collector, 30),
        AchievementPreview(R.string.complete_collection)
    )
}


