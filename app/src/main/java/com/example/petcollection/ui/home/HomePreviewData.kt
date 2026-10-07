package com.example.petcollection.ui.home

import androidx.annotation.StringRes
import com.example.petcollection.R

// Temporary UI-only data. Add collectible categories here as the design evolves.
data class CollectionCategory(val id: String, @param:StringRes val nameRes: Int, val total: Int, val collected: Int = 0)
data class AchievementPreview(@param:StringRes val nameRes: Int, val breedThreshold: Int? = null)

object HomePreviewData {
    val categories = listOf(
        CollectionCategory("cat", R.string.cat_collection, 22),
        CollectionCategory("dog", R.string.dog_collection, 27)
    )
    val total get() = categories.sumOf { it.total }
    val collected get() = categories.sumOf { it.collected }
    val achievements = listOf(
        AchievementPreview(R.string.first_discovery, 1),
        AchievementPreview(R.string.beginner_collector, 5),
        AchievementPreview(R.string.pet_explorer, 10),
        AchievementPreview(R.string.experienced_collector, 20),
        AchievementPreview(R.string.master_collector, 30),
        AchievementPreview(R.string.complete_collection)
    )
}


