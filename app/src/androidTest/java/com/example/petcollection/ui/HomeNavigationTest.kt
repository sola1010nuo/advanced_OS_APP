package com.example.petcollection.ui

import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.petcollection.R
import com.example.petcollection.data.MockBreedRepository
import com.example.petcollection.data.Species
import java.util.Locale
import org.junit.Rule
import org.junit.Test

class HomeNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun homeCategoriesAndBottomNavigationOpenExistingGridInBothLanguages() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent { PetCollectionApp() }
        for (language in listOf("zh-Hant", "en")) {
            val resources = context.createConfigurationContext(
                Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
            ).resources
            fun click(id: Int) = compose.onNode(hasText(resources.getString(id)) and hasClickAction()).performClick()
            fun assertGrid(species: Species) {
                val filter = if (species == Species.CAT) R.string.filter_cats else R.string.filter_dogs
                compose.onNodeWithText(resources.getString(filter)).assertIsOn()
                compose.onNode(hasText(resources.getString(R.string.collection)) and hasClickAction()).assertIsSelected()
                val breeds = MockBreedRepository(resources).getBreeds().filter { it.species == species }
                compose.onNodeWithText(breeds.first().name).assertIsDisplayed()
                compose.onNodeWithText(breeds[1].name).assertIsDisplayed()
                compose.onNode(hasScrollAction()).performScrollToNode(hasText(breeds.last().name))
                compose.onNodeWithText(breeds.last().name).assertIsDisplayed()
            }
            for ((card, species) in listOf(R.string.cat_collection to Species.CAT, R.string.dog_collection to Species.DOG)) {
                compose.onNodeWithText(resources.getString(card)).performScrollTo().performClick()
                assertGrid(species)
                click(R.string.home)
            }
            click(R.string.collection)
            assertGrid(Species.DOG)
            click(R.string.home)
            if (language == "zh-Hant") {
                click(R.string.settings)
                compose.onNodeWithText("English").performClick()
                compose.onNode(hasText("Home") and hasClickAction()).performClick()
            }
        }
    }
}
