package com.example.petcollection.ui.home

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.petcollection.R
import com.example.petcollection.data.*
import com.example.petcollection.ui.collection.CollectionUnlockState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine

@Composable
fun AchievementsScreen(data: AchievementData, modifier: Modifier = Modifier) {
    val total = Achievement.entries.size
    val percent = data.unlockedCount * 100 / total
    LazyColumn(modifier.fillMaxSize().testTag("achievements-page"), contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.nav_achievements), style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold)
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.achievement_summary, data.unlockedCount, total),
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        LinearProgressIndicator(progress = { data.unlockedCount.toFloat() / total }, modifier = Modifier.fillMaxWidth(),
                            trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = .15f))
                        Text(stringResource(R.string.achievement_percent, percent), style = MaterialTheme.typography.labelLarge)
                    }
                }
                if (!data.loaded) Text(stringResource(R.string.achievement_loading))
            }
        }
        item { AchievementSectionHeading(R.string.achievement_public) }
        itemsIndexed(Achievement.entries.filter { !it.hidden }, key = { _, it -> it.name }) { _, achievement ->
            AchievementRow(achievement, data)
        }
        item { AchievementSectionHeading(R.string.achievement_hidden) }
        itemsIndexed(Achievement.entries.filter { it.hidden }, key = { _, it -> it.name }) { index, achievement ->
            AchievementRow(achievement, data, index)
        }
        if (data.unreadablePhotos > 0) item {
            Text(stringResource(R.string.achievement_photo_unavailable, data.unreadablePhotos),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Text(stringResource(R.string.achievement_history_note), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AchievementSectionHeading(title: Int) {
    Text(stringResource(title), Modifier.padding(top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun AchievementRow(achievement: Achievement, data: AchievementData, hiddenIndex: Int = 0) {
    val unlock = data.unlock(achievement)
    val concealed = achievement.hidden && unlock == null
    val progress = data.progress.find { it.achievement == achievement }
    val locale = LocalConfiguration.current.locales[0]
    OutlinedCard(Modifier.fillMaxWidth().testTag(if (concealed) "achievement-hidden-$hiddenIndex" else "achievement-${achievement.name}"),
        shape = RoundedCornerShape(20.dp), colors = CardDefaults.outlinedCardColors(
            containerColor = if (unlock != null) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AchievementIcon(achievement, concealed)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(if (concealed) "???" else stringResource(achievement.title),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (!concealed) Text(stringResource(achievement.description), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!concealed) {
                    Text(stringResource(if (unlock != null) R.string.achievement_earned else R.string.achievement_locked),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (unlock != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (unlock?.unlockedAt != null) {
                        val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
                            .format(Instant.ofEpochMilli(unlock.unlockedAt).atZone(ZoneId.systemDefault()))
                        Text(stringResource(R.string.achievement_date, date), style = MaterialTheme.typography.labelSmall)
                    } else if (unlock == null && !achievement.hidden && data.loaded) {
                        if (progress?.current != null) {
                            LinearProgressIndicator(progress = { progress.current.toFloat() / progress.target.coerceAtLeast(1) },
                                modifier = Modifier.fillMaxWidth().height(4.dp))
                            Text(stringResource(R.string.achievement_progress, progress.current, progress.target),
                                style = MaterialTheme.typography.labelSmall)
                        } else if (achievement == Achievement.PLACES) {
                            Text(stringResource(R.string.achievement_place_unavailable), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AchievementIcon(achievement: Achievement, concealed: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val tint = when (achievement.ordinal % 3) { 0 -> colors.primary; 1 -> colors.secondary; else -> colors.tertiary }
    Box(Modifier.size(52.dp).background(if (concealed) colors.surfaceContainerHighest else tint.copy(alpha = .13f),
        RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
        if (concealed) Canvas(Modifier.size(26.dp)) {
            val stroke = Stroke(2.dp.toPx())
            drawRoundRect(colors.onSurfaceVariant, Offset(size.width * .15f, size.height * .42f),
                Size(size.width * .7f, size.height * .52f), style = stroke)
            drawArc(colors.onSurfaceVariant, 180f, 180f, false, Offset(size.width * .3f, size.height * .08f),
                Size(size.width * .4f, size.height * .65f), style = stroke)
        } else {
            val illustration = achievementIllustration(achievement)
            if (illustration != null) Image(painterResource(illustration), contentDescription = null,
                modifier = Modifier.size(46.dp).testTag("achievement-art-${achievement.name}"))
            else Text(achievement.glyph, color = tint, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

/** Only the requested six achievements replace their original glyphs. Concealed rows never load these assets. */
internal fun achievementIllustration(achievement: Achievement): Int? = when (achievement) {
    Achievement.CATS -> R.drawable.achievement_cat_cozy
    Achievement.TORTOISESHELL -> R.drawable.achievement_cat_tortoiseshell
    Achievement.CAT_OBSERVER -> R.drawable.achievement_cat_siamese
    Achievement.DOGS -> R.drawable.achievement_dog_corgi
    Achievement.DOG_FRIEND -> R.drawable.achievement_dog_retriever
    Achievement.MONKEY -> R.drawable.achievement_banana
    else -> null
}

/** Persistent claims serialize simultaneous unlocks; collection stays independent of the current screen. */
@Composable
fun AchievementNotifications(state: CollectionUnlockState, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf<Achievement?>(null) }
    val canShow = state.pendingEncounter == null
    LaunchedEffect(state) {
        combine(state.achievements, snapshotFlow { state.pendingEncounter == null }) { data, ready -> data to ready }
            .collect { (data, ready) ->
                if (ready) for (unlock in data.unlocks.filter { !it.announced }) {
                    if (state.pendingEncounter != null) break
                    val achievement = Achievement.entries.find { it.name == unlock.id } ?: continue
                    if (state.claimAchievementAnnouncement(unlock.id)) {
                        shown = achievement
                        delay(2300)
                        shown = null
                        delay(250)
                    }
                }
            }
    }
    AnimatedVisibility(shown != null && canShow, modifier,
        enter = fadeIn() + slideInVertically { -it / 2 }, exit = fadeOut()) {
        shown?.let { achievement ->
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 4.dp, shadowElevation = 4.dp) {
                Row(Modifier.padding(12.dp).testTag("achievement-notification").semantics { liveRegion = LiveRegionMode.Polite },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AchievementIcon(achievement)
                    Text(stringResource(R.string.achievement_notice, stringResource(achievement.title)),
                        Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}
