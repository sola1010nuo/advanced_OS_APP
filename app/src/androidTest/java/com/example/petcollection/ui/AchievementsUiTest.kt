package com.example.petcollection.ui

import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.petcollection.R
import com.example.petcollection.data.*
import com.example.petcollection.ui.collection.CollectionUnlockState
import com.example.petcollection.ui.home.AchievementNotifications
import com.example.petcollection.ui.home.AchievementsScreen
import com.example.petcollection.ui.home.AchievementIcon
import com.example.petcollection.ui.home.achievementIllustration
import com.example.petcollection.ui.theme.PetCollectionTheme
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AchievementsUiTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun capture(name: String) {
        val file = File(context.getExternalFilesDir(null), name)
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("cp ${file.absolutePath} /data/local/tmp/$name")).use { it.readBytes() }
    }

    @Test fun publicProgressAndHiddenConditionsAreLocalizedInBothThemes() {
        var language by mutableStateOf("zh-Hant")
        var dark by mutableStateOf(false)
        val catalog = MockBreedRepository(context.resources).getCollectionEntries()
        val data = AchievementData(AchievementRules.evaluate(catalog, emptyList(), emptyList()), loaded = true)
        compose.setContent {
            val configuration = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
            CompositionLocalProvider(LocalContext provides context.createConfigurationContext(configuration), LocalConfiguration provides configuration) {
                PetCollectionTheme(darkTheme = dark) { Surface { AchievementsScreen(data) } }
            }
        }
        for (lang in listOf("zh-Hant", "en")) for (night in listOf(false, true)) {
            compose.runOnIdle { language = lang; dark = night }
            val resources = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(lang)) }).resources
            compose.onNodeWithTag("achievements-page").performScrollToIndex(0)
            compose.onNodeWithText(resources.getString(R.string.achievement_summary, 0, 15)).assertExists()
            capture("achievements-$lang-${if (night) "dark" else "light"}.png")
            Achievement.entries.filter { !it.hidden }.forEach { achievement ->
                compose.onNodeWithTag("achievements-page").performScrollToNode(hasTestTag("achievement-${achievement.name}"))
                compose.onNodeWithText(resources.getString(achievement.title)).assertExists()
                if (achievement == Achievement.PLACES) compose.onNodeWithText(resources.getString(R.string.achievement_place_unavailable)).assertExists()
            }
            Achievement.entries.filter { it.hidden }.forEachIndexed { index, achievement ->
                compose.onNodeWithTag("achievements-page").performScrollToNode(hasTestTag("achievement-hidden-$index"))
                compose.onNodeWithTag("achievement-hidden-$index").assert(hasAnyDescendant(hasText("???")))
                compose.onNodeWithText(resources.getString(achievement.title)).assertDoesNotExist()
                compose.onNodeWithText(resources.getString(achievement.description)).assertDoesNotExist()
                compose.onNodeWithTag("achievement-art-${achievement.name}").assertDoesNotExist()
            }
            capture("achievements-hidden-$lang-${if (night) "dark" else "light"}.png")
        }
    }

    @Test fun unlockedHiddenAchievementsRevealNamesAndOnlyReliableDates() {
        val timestamp = Instant.parse("2026-10-15T03:00:00Z").toEpochMilli()
        val unlocks = listOf(AchievementUnlock(Achievement.MONKEY.name, null, true),
            AchievementUnlock(Achievement.NIGHT.name, timestamp, true))
        val data = AchievementData(unlocks = unlocks, loaded = true)
        compose.setContent { PetCollectionTheme(darkTheme = false) { Surface { AchievementsScreen(data) } } }
        compose.onNodeWithTag("achievements-page").performScrollToNode(hasTestTag("achievement-MONKEY"))
        compose.onNodeWithText(context.getString(R.string.ach_monkey)).assertExists()
        compose.onNodeWithTag("achievement-art-MONKEY").assertExists()
        compose.onNodeWithTag("achievement-MONKEY").assert(!hasAnyDescendant(hasText("2026", substring = true)))
        compose.onNodeWithTag("achievements-page").performScrollToNode(hasTestTag("achievement-NIGHT"))
        val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(context.resources.configuration.locales[0])
            .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
        compose.onNodeWithText(context.getString(R.string.achievement_date, date)).assertExists()
        compose.onNodeWithText(context.getString(R.string.ach_night)).assertExists()
    }

    @Test fun sixDistinctLocalAnimalIllustrationsRemainClearAndAlignedInBothThemes() {
        var dark by mutableStateOf(false)
        val animals = listOf(Achievement.CATS, Achievement.CAT_OBSERVER, Achievement.TORTOISESHELL,
            Achievement.DOGS, Achievement.DOG_FRIEND, Achievement.MONKEY)
        assertEquals(6, animals.map { achievementIllustration(it) }.filterNotNull().toSet().size)
        Achievement.entries.filter { it !in animals }.forEach { assertNull(achievementIllustration(it)) }
        compose.setContent {
            PetCollectionTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    LazyVerticalGrid(GridCells.Fixed(2), contentPadding = PaddingValues(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        items(animals) { achievement ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                AchievementIcon(achievement)
                                Text(context.getString(achievement.title))
                            }
                        }
                    }
                }
            }
        }
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark = night }
            val bounds = animals.map { compose.onNodeWithTag("achievement-art-${it.name}").fetchSemanticsNode().boundsInRoot }
            bounds.forEach { assertEquals(bounds.first().width, it.width, .1f); assertEquals(it.width, it.height, .1f) }
            capture("achievement-animals-${if (night) "dark" else "light"}.png")
        }
    }

    @Test fun simultaneousUnlockNotificationsAreSerializedAndClaimedOnce() {
        val db = Room.inMemoryDatabaseBuilder(context, CollectionDatabase::class.java).build()
        val repo = EncounterRepository(db.encounters(), context)
        val state = CollectionUnlockState(repo)
        val source = File(context.cacheDir, "notice-${UUID.randomUUID()}").apply { writeText("unique notice photo") }
        try {
            compose.setContent { PetCollectionTheme(darkTheme = false) { AchievementNotifications(state, Modifier.fillMaxWidth()) } }
            compose.waitUntil(10000) { state.achievements.value.loaded }
            // Freeze auto-advancement so a semantics assertion cannot skip a short-lived queued banner.
            compose.mainClock.autoAdvance = false
            runBlocking { repo.create(EncounterRecord("bombay", Uri.fromFile(source).toString())) }
            val firstNotice = context.getString(R.string.achievement_notice, context.getString(R.string.ach_first))
            val rareNotice = context.getString(R.string.achievement_notice, context.getString(R.string.ach_rare))
            compose.waitUntil(10000) {
                compose.mainClock.advanceTimeByFrame()
                state.achievements.value.unlock(Achievement.FIRST)?.announced == true
            }
            compose.mainClock.advanceTimeBy(400)
            compose.onNodeWithText(firstNotice).assertExists()
            compose.onAllNodesWithTag("achievement-notification").assertCountEquals(1)
            compose.waitUntil(10000) {
                compose.mainClock.advanceTimeByFrame()
                state.achievements.value.unlock(Achievement.RARE)?.announced == true
            }
            compose.mainClock.advanceTimeBy(400)
            compose.onNodeWithText(rareNotice).assertExists()
            compose.onNodeWithText(firstNotice).assertDoesNotExist()
            compose.onAllNodesWithTag("achievement-notification").assertCountEquals(1)
            compose.mainClock.advanceTimeBy(3000)
            compose.waitUntil(10000) {
                compose.mainClock.advanceTimeByFrame()
                compose.onAllNodesWithTag("achievement-notification").fetchSemanticsNodes().isEmpty()
            }
            runBlocking {
                assertFalse(repo.claimAchievementAnnouncement(Achievement.FIRST.name))
                assertFalse(repo.claimAchievementAnnouncement(Achievement.RARE.name))
                repo.collection.first().encounters.forEach { repo.delete(it.id) }
            }
        } finally {
            compose.mainClock.autoAdvance = true
            val store = ViewModelStore(); store.put("achievements", state); store.clear()
            db.close(); source.delete()
        }
    }
}
