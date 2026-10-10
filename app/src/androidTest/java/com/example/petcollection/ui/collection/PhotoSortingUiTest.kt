package com.example.petcollection.ui.collection

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.petcollection.data.*
import com.example.petcollection.ui.theme.PetCollectionTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime
import java.util.Locale

class PhotoSortingUiTest {
    @get:Rule val compose = createComposeRule()
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = Room.inMemoryDatabaseBuilder(base, CollectionDatabase::class.java).build()
    private val state = CollectionUnlockState(EncounterRepository(db.encounters(), base))
    private var route by mutableStateOf("abyssinian")
    private var language by mutableStateOf("en")
    private var dark by mutableStateOf(false)
    private var opened by mutableStateOf<String?>(null)

    private fun mount() {
        runBlocking {
            db.encounters().createAlbum("custom", "Custom album")
            for (breed in listOf("abyssinian", "corgi", "monkey", "other_cat", "other_dog", "unregistered", "custom")) {
                val category = if (breed == "custom") "unregistered" else breed
                val ids = (0..19).map { "$breed-$it" }
                ids.forEachIndexed { i, id -> db.encounters().create(EncounterRecord(category, "content://fixture/$id",
                    id = id, petName = id, location = "Fixture location", note = "Fixture note",
                    encounteredAt = if (i == 19) null else LocalDateTime.of(2026, 10, i + 1, 0, 0), createdAt = i.toLong())) }
                if (breed == "custom") db.encounters().movePhotos(ids, "custom")
                db.encounters().reorderPhotos(when (breed) {
                    "custom" -> PhotoAlbumKey.other("custom")
                    "unregistered" -> PhotoAlbumKey.UNASSIGNED
                    else -> PhotoAlbumKey.breed(breed)
                }, ids)
            }
        }
        compose.setContent {
            val context = remember(language) { base.createConfigurationContext(Configuration(base.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language))
            }) }
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides context.resources.configuration,
                LocalResources provides context.resources) {
                PetCollectionTheme(darkTheme = dark) {
                    val data by state.collection.collectAsState()
                    val catalog = remember(context) { MockBreedRepository(context.resources).getCollectionEntries() }
                    val breeds = catalog.map { data.applyTo(it) }
                    if (data.loaded) key(route) {
                        if (route == "unregistered" || route == "custom") {
                            OtherAlbumsScreen(data, breeds, state, Modifier.fillMaxSize(), {}, {})
                        } else {
                            val breed = breeds.first { it.id == route }
                            val albumKey = PhotoAlbumKey.breed(route)
                            BreedDetailScreen(breed, {}, encounters = data.sortedPhotos(albumKey, data.forBreed(route)),
                                sortMode = data.photoSortMode(albumKey), busy = state.isWorking,
                                onSortMode = { state.setPhotoSortMode(albumKey, it) },
                                onReorder = { ids, done -> state.reorderPhotos(albumKey, ids, done) },
                                onOpenEncounter = { opened = it })
                        }
                    }
                }
            }
        }
        compose.waitUntil(10000) { state.collection.value.loaded && state.encounters.size == 140 }
    }

    private fun order(key: String) = state.collection.value.photoOrder.filter { it.albumKey == key }.sortedBy { it.position }.map { it.encounterId }
    private fun scrollTag(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("album-photo-grid").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag)
    }
    private fun dragFourthToFirst(prefix: String, key: String, firstIndex: Int) {
        val grid = compose.onNodeWithTag("album-photo-grid")
        grid.performScrollToIndex(firstIndex)
        compose.waitForIdle()
        val gridBounds = grid.fetchSemanticsNode().boundsInRoot
        val first = compose.onNodeWithTag("photo_$prefix-0").fetchSemanticsNode().boundsInRoot.center - gridBounds.topLeft
        val fourth = compose.onNodeWithTag("photo_$prefix-3").fetchSemanticsNode().boundsInRoot.center - gridBounds.topLeft
        compose.mainClock.autoAdvance = false
        grid.performTouchInput { down(fourth) }
        compose.mainClock.advanceTimeBy(700)
        grid.performTouchInput {
            advanceEventTime(700)
            moveTo(first, delayMillis = 400)
            up()
        }
        compose.mainClock.autoAdvance = true
        compose.waitUntil(10000) { !state.isWorking && order(key).firstOrNull() == "$prefix-3" }
        assertEquals(listOf("$prefix-3", "$prefix-0", "$prefix-1", "$prefix-2"), order(key).take(4))
        assertNull(opened)
    }

    @Test fun catDogMonkeyAndOtherBreedAlbumsSupportCrossRowDragAndShortTap() {
        mount()
        for (breed in listOf("abyssinian", "corgi", "monkey", "other_cat", "other_dog")) {
            compose.runOnIdle { route = breed; opened = null }
            scrollTag("photo-sort").assertIsDisplayed().performClick()
            compose.onNodeWithTag("photo-sort-CUSTOM").assertIsSelected().performClick()
            val key = PhotoAlbumKey.breed(breed)
            dragFourthToFirst(breed, key, if (breed in listOf("abyssinian", "corgi")) 5 else 4)
            compose.onNodeWithTag("photo_$breed-3").performClick()
            compose.runOnIdle { assertEquals("$breed-3", opened) }
        }
    }

    @Test fun managePhotosMovesMultipleRecordsWithoutChangingMetadataOrCustomOrder() {
        mount()
        compose.runOnIdle { route = "unregistered" }
        val ids = listOf("unregistered-0", "unregistered-1")
        val before = runBlocking { ids.map { requireNotNull(db.encounters().get(it)) } }
        val targetOrder = order(PhotoAlbumKey.other("custom"))
        scrollTag("manage-photos").assertIsDisplayed().performClick()
        compose.onNodeWithTag("photo-sort").assertIsNotEnabled()
        ids.forEach { scrollTag("photo_$it").performClick() }
        compose.onNodeWithTag("album-photo-grid").performScrollToNode(hasText("2 photos selected"))
        compose.onNodeWithText("2 photos selected").assertIsDisplayed()
        compose.onNodeWithText("Move to album").performClick()
        compose.onNode(hasText("Custom album") and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(10000) {
            !state.isWorking && state.collection.value.members.count { it.albumId == "custom" && it.encounterId in ids } == 2
        }
        assertEquals(before, runBlocking { ids.map { db.encounters().get(it) } })
        assertEquals((0..19).map { "unregistered-$it" }.drop(2), order(PhotoAlbumKey.UNASSIGNED))
        assertEquals(targetOrder + ids, order(PhotoAlbumKey.other("custom")))
        compose.onNodeWithTag("album-photo-grid").performScrollToIndex(0)
        compose.onNodeWithTag("album_custom").performClick()
        scrollTag("manage-photos").assertIsEnabled()
        compose.onNodeWithTag("photo-sort").assertIsDisplayed().performClick()
        compose.onNodeWithTag("photo-sort-CUSTOM").assertIsSelected().performClick()
        ids.forEach { scrollTag("photo_$it").assertIsDisplayed() }
        scrollTag("manage-photos").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("photo-sort").assertIsEnabled()
    }

    @Test fun otherUnassignedAndCustomAlbumPhotosUseSameDragAndDateModes() {
        mount()
        compose.runOnIdle { route = "unregistered" }
        dragFourthToFirst("unregistered", PhotoAlbumKey.UNASSIGNED, 4)
        compose.onNodeWithTag("album-photo-grid").performScrollToIndex(0)
        compose.onNodeWithTag("album_custom").performClick()
        dragFourthToFirst("custom", PhotoAlbumKey.other("custom"), 2)
        assertEquals("unregistered-3", order(PhotoAlbumKey.UNASSIGNED).first())
        val saved = order(PhotoAlbumKey.other("custom"))
        compose.onNodeWithTag("album-photo-grid").performScrollToIndex(0)
        scrollTag("photo-sort").performClick()
        compose.onNodeWithTag("photo-sort-NEWEST").performClick()
        compose.waitUntil(10000) { state.collection.value.photoSortMode(PhotoAlbumKey.other("custom")) == PhotoSortMode.NEWEST }
        assertEquals(saved, order(PhotoAlbumKey.other("custom")))
        compose.onNodeWithTag("photo-sort").performClick()
        compose.onNodeWithTag("photo-sort-CUSTOM").performClick()
        compose.waitUntil(10000) { state.collection.value.photoSortMode(PhotoAlbumKey.other("custom")) == PhotoSortMode.CUSTOM }
        assertEquals(saved, order(PhotoAlbumKey.other("custom")))
    }

    @Test fun sortMenuShowsSelectionInBothLanguagesAndThemesAndDateDragSelectsCustom() {
        mount()
        for ((locale, night) in listOf("en" to false, "zh-Hant" to true)) {
            compose.runOnIdle { language = locale; dark = night }
            compose.onNodeWithTag("album-photo-grid").performScrollToIndex(0)
            scrollTag("photo-sort").performClick()
            compose.onNodeWithText(if (locale == "en") "Oldest First" else "從舊到新").performClick()
            compose.waitUntil(10000) { state.collection.value.photoSortMode(PhotoAlbumKey.breed("abyssinian")) == PhotoSortMode.OLDEST }
            compose.onNodeWithTag("photo-sort").performClick()
            compose.onNodeWithTag("photo-sort-OLDEST").assertIsSelected()
            compose.waitForIdle()
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
                java.io.File(base.externalCacheDir, "photo-sort-$locale.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
            compose.onNodeWithTag("photo-sort-OLDEST").performClick()
        }
        dragFourthToFirst("abyssinian", PhotoAlbumKey.breed("abyssinian"), 5)
        assertEquals(PhotoSortMode.CUSTOM, state.collection.value.photoSortMode(PhotoAlbumKey.breed("abyssinian")))
    }

    @Test fun holdingAtBottomEdgeScrollsToLaterRows() {
        mount()
        compose.mainClock.autoAdvance = false
        val grid = compose.onNodeWithTag("album-photo-grid")
        grid.performScrollToIndex(5)
        val scrollBefore = grid.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        val bounds = grid.fetchSemanticsNode().boundsInRoot
        val start = compose.onNodeWithTag("photo_abyssinian-0").fetchSemanticsNode().boundsInRoot.center - bounds.topLeft
        grid.performTouchInput { down(start) }
        compose.mainClock.advanceTimeBy(700)
        grid.performTouchInput { advanceEventTime(700); moveTo(Offset(start.x, bounds.height - 8f), delayMillis = 300) }
        compose.mainClock.advanceTimeBy(2500)
        grid.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitUntil(10000) { !state.isWorking }
        val scrollAfter = grid.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue("Viewport must scroll to later rows: $scrollBefore -> $scrollAfter", scrollAfter > scrollBefore + 4)
        assertTrue("Order after edge scroll: ${order(PhotoAlbumKey.breed("abyssinian"))}",
            order(PhotoAlbumKey.breed("abyssinian")).indexOf("abyssinian-0") > 5)
        assertEquals(20, order(PhotoAlbumKey.breed("abyssinian")).toSet().size)
    }

    @Test fun existingPhotoDateCanBeClearedWithoutChangingOtherInformation() {
        var saved: EncounterRecord? = null
        val original = EncounterRecord("corgi", null, petName = "Keep name", note = "Keep note")
        val context = base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(Locale.ENGLISH) })
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides context.resources.configuration,
                LocalResources provides context.resources) {
                PetCollectionTheme {
                    EncounterEditorDialog(MockBreedRepository(context.resources).getBreed("corgi")!!,
                        original, onSave = { saved = it }, onDismiss = {})
                }
            }
        }
        compose.onNode(hasText(context.getString(com.example.petcollection.R.string.encounter_date)) and hasSetTextAction())
            .performScrollTo().performTextClearance()
        compose.onNodeWithText(context.getString(com.example.petcollection.R.string.encounter_save)).assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(original.copy(encounteredAt = null), saved) }
    }

    @After fun close() {
        val store = ViewModelStore(); store.put("state", state); store.clear()
        db.close()
    }
}
