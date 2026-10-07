package com.example.petcollection.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import android.net.Uri
import com.example.petcollection.data.Species
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.example.petcollection.R
import com.example.petcollection.ui.theme.PetCollectionTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    selectedPhotoUri: Uri? = null,
    onAddPhoto: () -> Unit = {},
    onOpenCollection: (Species) -> Unit = {},
    recognitionState: PhotoRecognitionState = remember { PhotoRecognitionState() },
    recognizedBreedName: String? = null,
    onRetryRecognition: () -> Unit = {},
    categories: List<CollectionCategory> = HomePreviewData.categories
) {
    val photoPreview = remember { BringIntoViewRequester() }
    LaunchedEffect(selectedPhotoUri, recognitionState.processing) {
        if (selectedPhotoUri != null) {
            withFrameNanos { }
            photoPreview.bringIntoView()
        }
    }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        HomeHeader()
        CollectionProgressCard(categories.sumOf { it.collected }, categories.sumOf { it.total })
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(stringResource(R.string.your_collections))
            categories.forEach { category ->
                CollectionCategoryCard(category, onClick = {
                    onOpenCollection(if (category.id == "cat") Species.CAT else Species.DOG)
                })
            }
        }
        Button(onClick = onAddPhoto, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(16.dp)) {
            Text(stringResource(R.string.add_photo), style = MaterialTheme.typography.titleMedium)
        }
        if (selectedPhotoUri != null) {
            Box(Modifier.bringIntoViewRequester(photoPreview)) {
                SelectedPhotoPreview(recognitionState, recognizedBreedName, onRetryRecognition)
            }
        }
        AchievementSection(HomePreviewData.achievements, HomePreviewData.total)
        RecentDiscoveriesSection()
        RecentFootprintsSection()
    }
}
@Composable
private fun HomeHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.home_eyebrow), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.home_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun CollectionProgressCard(collected: Int, total: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.my_collection), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.collection_count, collected, total), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.breeds_discovered), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(
                progress = { if (total > 0) (collected.toFloat() / total).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().height(8.dp), color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant
            )
            Text(stringResource(R.string.collection_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun CollectionCategoryCard(category: CollectionCategory, onClick: () -> Unit = {}) {
    val isCat = category.id == "cat"
    val tint = if (isCat) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PetIllustration(isCat, Modifier.size(64.dp).background(tint, RoundedCornerShape(18.dp)).padding(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(stringResource(category.nameRes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.category_count, category.collected, category.total), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("\u203A", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

// Simple vector artwork needs no extra image or icon dependencies.
@Composable
private fun PetIllustration(cat: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val fur = if (cat) colors.secondary else colors.tertiary
        if (cat) {
            drawPath(Path().apply {
                moveTo(w * .12f, h * .48f); lineTo(w * .12f, h * .08f); lineTo(w * .45f, h * .32f); close()
                moveTo(w * .55f, h * .32f); lineTo(w * .88f, h * .08f); lineTo(w * .88f, h * .48f); close()
            }, fur)
        } else {
            drawOval(fur, Offset(0f, h * .16f), Size(w * .3f, h * .64f))
            drawOval(fur, Offset(w * .7f, h * .16f), Size(w * .3f, h * .64f))
        }
        drawOval(fur, Offset(w * .12f, h * .23f), Size(w * .76f, h * .68f))
        drawCircle(if (cat) colors.onSecondary else colors.onTertiary, w * .035f, Offset(w * .35f, h * .53f))
        drawCircle(if (cat) colors.onSecondary else colors.onTertiary, w * .035f, Offset(w * .65f, h * .53f))
        drawOval(colors.surface, Offset(w * .36f, h * .62f), Size(w * .28f, h * .2f))
        drawCircle(if (cat) colors.onSecondary else colors.onTertiary, w * .045f, Offset(w * .5f, h * .66f))
    }
}

@Composable
fun AchievementSection(achievements: List<AchievementPreview>, total: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(stringResource(R.string.achievements))
        Text(stringResource(R.string.achievement_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(achievements, key = { it.nameRes }) { AchievementBadge(it, total) }
        }
    }
}

@Composable
fun AchievementBadge(achievement: AchievementPreview, total: Int) {
    val threshold = achievement.breedThreshold ?: total
    OutlinedCard(
        modifier = Modifier.width(144.dp), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("\u2606", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(achievement.nameRes), modifier = Modifier.heightIn(min = 40.dp), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
            Text(pluralStringResource(R.plurals.breed_threshold, threshold, threshold), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.locked), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun RecentDiscoveriesSection() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(stringResource(R.string.recent_discoveries))
        OutlinedCard(shape = RoundedCornerShape(24.dp), colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent)) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PetIllustration(true, Modifier.size(52.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(16.dp)).padding(8.dp))
                Text(stringResource(R.string.no_discoveries), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.discoveries_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun HomeScreenPreview() { PetCollectionTheme(darkTheme = false) { HomeScreen() } }

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun DarkHomeScreenPreview() { PetCollectionTheme(darkTheme = true) { HomeScreen() } }

@Composable
fun RecentFootprintsSection() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(stringResource(R.string.recent_footprints))
        OutlinedCard(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.no_footprints), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.footprints_hint), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
