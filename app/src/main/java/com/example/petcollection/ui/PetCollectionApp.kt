package com.example.petcollection.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivityResultRegistryOwner
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.content.res.Configuration
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.core.view.WindowCompat
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.example.petcollection.R
import com.example.petcollection.data.MockBreedRepository
import com.example.petcollection.data.Species
import com.example.petcollection.ui.collection.CollectionEncounterHost
import com.example.petcollection.ui.collection.CollectionScreen
import com.example.petcollection.ui.collection.CollectionUnlockState
import com.example.petcollection.ui.collection.rememberCollectionUnlockState
import com.example.petcollection.ui.home.CollectionCategory
import com.example.petcollection.ui.home.HomeScreen
import com.example.petcollection.ui.home.AchievementsScreen
import com.example.petcollection.ui.home.AchievementNotifications
import com.example.petcollection.ui.theme.PetCollectionTheme
import com.example.petcollection.ui.settings.EffectsSettingRow
import com.example.petcollection.ui.settings.EffectsSettingsUiState
import com.example.petcollection.ui.settings.LocalEffectsEnabled
import com.example.petcollection.ui.settings.rememberEffectsSettingsState
import java.util.Locale
import kotlinx.coroutines.launch

private enum class Destination(@param:StringRes val label: Int) {
    HOME(R.string.home), COLLECTION(R.string.collection), CAMERA(R.string.camera),
    FOOTPRINTS(R.string.footprints), ACHIEVEMENTS(R.string.nav_achievements), SETTINGS(R.string.settings)
}
private val bottomDestinations = listOf(Destination.HOME, Destination.COLLECTION, Destination.CAMERA,
    Destination.FOOTPRINTS, Destination.ACHIEVEMENTS)
private enum class Appearance(@param:StringRes val label: Int) {
    SYSTEM(R.string.follow_system), LIGHT(R.string.light_mode), DARK(R.string.dark_mode)
}

@Composable
fun PetCollectionApp(collectionUnlockState: CollectionUnlockState = rememberCollectionUnlockState()) {
    val effectsSettingsState = rememberEffectsSettingsState()
    val effectsSettings by effectsSettingsState.state.collectAsState()
    // UI prototype only: retained across rotation, reset on a fresh app launch.
    var language by rememberSaveable { mutableStateOf("zh-Hant") }
    var appearance by rememberSaveable { mutableStateOf(Appearance.SYSTEM.name) }
    var destination by rememberSaveable { mutableStateOf(Destination.HOME.name) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var collectionSpecies by rememberSaveable { mutableStateOf(Species.CAT.name) }
    // Reselecting Collection discards only its local detail/album routes, not stored photos.
    var collectionRootRequest by rememberSaveable { mutableIntStateOf(0) }
    val photoInput = collectionUnlockState.photoInput
    val recognitionState = photoInput.recognition
    val baseContext = LocalContext.current
    val view = LocalView.current
    val settingsBackEnabled = destination == Destination.SETTINGS.name && collectionUnlockState.pendingEncounter == null
    val activity = baseContext.activityOrNull() ?: view.context.activityOrNull()
    BackHandler(settingsBackEnabled) {
        destination = Destination.HOME.name
    }
    val collectionData by collectionUnlockState.collection.collectAsState()
    val achievementData by collectionUnlockState.achievements.collectAsState()
    val activityResultOwner = LocalActivityResultRegistryOwner.current
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            photoInput.begin(uri)
        }
    }
    val systemConfiguration = LocalConfiguration.current
    val configuration = remember(systemConfiguration, language) {
        Configuration(systemConfiguration).apply {
            setLocales(LocaleList(Locale.forLanguageTag(language)))
        }
    }
    val localizedContext = remember(baseContext, configuration) {
        baseContext.createConfigurationContext(configuration)
    }
    val entries = remember(localizedContext) { MockBreedRepository(localizedContext.resources).getCollectionEntries() }
    val breeds = entries.map { collectionData.applyTo(it) }
    val dark = when (Appearance.valueOf(appearance)) {
        Appearance.SYSTEM -> isSystemInDarkTheme()
        Appearance.LIGHT -> false
        Appearance.DARK -> true
    }
    SideEffect {
        activity?.let { host ->
            WindowCompat.getInsetsController(host.window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalContext provides localizedContext, LocalConfiguration provides configuration,
        LocalEffectsEnabled provides effectsSettings.enabled.takeIf { effectsSettings.loaded },
        *listOfNotNull(activityResultOwner?.let { LocalActivityResultRegistryOwner provides it }).toTypedArray()) {
        PetCollectionTheme(darkTheme = dark) {
            val cameraMessage = stringResource(R.string.camera_coming_soon)
            // Collection already mounts this same host. Mount it here for Home and other destinations.
            if (destination != Destination.COLLECTION.name) CollectionEncounterHost(collectionUnlockState, breeds)
            Box(Modifier.fillMaxSize()) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    AppBottomBar(destination, onNavigate = { item ->
                        if (item == Destination.COLLECTION && destination == item.name) {
                            collectionRootRequest++
                        }
                        destination = item.name
                    }, onCamera = {
                        scope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar(cameraMessage)
                        }
                    })
                }
            ) { padding ->
                val contentModifier = Modifier.padding(padding).consumeWindowInsets(padding)
                when (Destination.valueOf(destination)) {
                    Destination.HOME, Destination.CAMERA -> HomeScreen(
                        modifier = contentModifier,
                        categories = Species.entries.map { species ->
                            val stats = collectionData.statistics(entries, species)
                            CollectionCategory(if (species == Species.CAT) "cat" else "dog",
                                if (species == Species.CAT) R.string.cat_collection else R.string.dog_collection,
                                stats.totalBreeds, stats.discovered, stats.encounterCount)
                        },
                        encounterCount = collectionData.statistics(entries).encounterCount,
                        selectedPhotoUri = photoInput.displayedUri?.let(Uri::parse),
                        onAddPhoto = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onOpenCollection = { species ->
                            collectionSpecies = species.name
                            destination = Destination.COLLECTION.name
                        },
                        recognitionState = recognitionState,
                        recognizedBreedName = breeds.firstOrNull { it.id == recognitionState.breedId }?.name,
                        onRetryRecognition = photoInput::retry,
                        collectionStatistics = collectionData.statistics(entries),
                        onOpenSettings = { destination = Destination.SETTINGS.name }
                    )
                    Destination.COLLECTION -> key(collectionRootRequest) {
                        CollectionScreen(contentModifier, unlockState = collectionUnlockState,
                            initialSpecies = Species.valueOf(collectionSpecies))
                    }
                    Destination.FOOTPRINTS -> FootprintsScreen(contentModifier)
                    Destination.ACHIEVEMENTS -> AchievementsScreen(achievementData, contentModifier)
                    Destination.SETTINGS -> {
                        SettingsScreen(
                            language, { language = it }, Appearance.valueOf(appearance), { appearance = it.name },
                            effectsSettings, effectsSettingsState::setEnabled, contentModifier,
                            onBack = { destination = Destination.HOME.name }
                        )
                    }
                }
            }
            AchievementNotifications(collectionUnlockState, Modifier.align(Alignment.TopCenter)
                .statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth())
            }
        }
    }
}

private tailrec fun Context.activityOrNull(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activityOrNull()
    else -> null
}



@Composable
private fun AppBottomBar(destination: String, onNavigate: (Destination) -> Unit, onCamera: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))) {
        // Reserve the raised camera's footprint so it cannot cover page content.
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().height(80.dp).background(colors.surfaceContainer).selectableGroup()) {
            bottomDestinations.forEach { item ->
                val camera = item == Destination.CAMERA
                val selected = destination == item.name ||
                    (item == Destination.HOME && destination == Destination.SETTINGS.name)
                val label = stringResource(item.label)
                Column(Modifier.weight(1f).fillMaxHeight().then(
                    if (camera) Modifier else Modifier.selectable(selected, role = Role.Tab,
                        onClick = { onNavigate(item) }).testTag("navigation-${item.name}")
                ).padding(top = 12.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
                        if (camera) FloatingActionButton(onClick = onCamera, shape = CircleShape,
                            containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer,
                            modifier = Modifier.requiredSize(64.dp).offset(y = (-12).dp)
                                .testTag("navigation-CAMERA").semantics { contentDescription = label }) {
                            DestinationIcon(item, Modifier.size(28.dp))
                        } else Surface(shape = RoundedCornerShape(16.dp),
                            color = if (selected) colors.secondaryContainer else colors.surfaceContainer,
                            contentColor = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
                            modifier = Modifier.size(56.dp, 32.dp)) {
                            Box(contentAlignment = Alignment.Center) { DestinationIcon(item) }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (selected) colors.primary else colors.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.fillMaxWidth().height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
            .background(colors.surfaceContainer))
    }
}

@Composable
internal fun SettingsGearIcon(modifier: Modifier = Modifier) = DestinationIcon(Destination.SETTINGS, modifier)

@Composable
private fun Page(title: Int, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        content()
    }
}


@Composable
private fun FootprintsScreen(modifier: Modifier) {
    Page(R.string.footprints, modifier) {
        OutlinedCard(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                DestinationIcon(Destination.FOOTPRINTS, Modifier.size(40.dp))
                Text(stringResource(R.string.no_footprints), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.footprints_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.footprints_future), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    language: String, onLanguageChange: (String) -> Unit,
    appearance: Appearance, onAppearanceChange: (Appearance) -> Unit,
    effects: EffectsSettingsUiState, onEffectsChange: (Boolean) -> Unit,
    modifier: Modifier, onBack: () -> Unit
) {
    Page(R.string.settings, modifier) {
        TextButton(onClick = onBack, modifier = Modifier.testTag("settings-back-home")) {
            Text(stringResource(R.string.back_home))
        }
        SettingsGroup(R.string.language) {
            SettingOption(R.string.language_zh, language == "zh-Hant") { onLanguageChange("zh-Hant") }
            SettingOption(R.string.language_en, language == "en") { onLanguageChange("en") }
        }
        SettingsGroup(R.string.appearance) {
            Appearance.entries.forEach { item ->
                SettingOption(item.label, appearance == item) { onAppearanceChange(item) }
            }
        }
        SettingsGroup(R.string.effects) {
            EffectsSettingRow(effects, onEffectsChange)
        }
        Text(stringResource(R.string.settings_hint_with_effects), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsGroup(title: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.fillMaxWidth().selectableGroup().padding(vertical = 8.dp), content = content)
        }
    }
}

@Composable
private fun SettingOption(label: Int, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
        .heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RadioButton(selected = selected, onClick = null)
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
    }
}

// Decorative icons; NavigationBarItem labels supply accessible destination names.
@Composable
private fun DestinationIcon(destination: Destination, modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * .075f)
        when (destination) {
            Destination.HOME -> drawPath(Path().apply {
                moveTo(w * .12f, h * .45f); lineTo(w * .5f, h * .12f); lineTo(w * .88f, h * .45f)
                lineTo(w * .88f, h * .88f); lineTo(w * .6f, h * .88f); lineTo(w * .6f, h * .6f)
                lineTo(w * .4f, h * .6f); lineTo(w * .4f, h * .88f); lineTo(w * .12f, h * .88f); close()
            }, color, style = stroke)
            Destination.COLLECTION -> {
                drawPath(Path().apply {
                    moveTo(w * .5f, h * .24f); lineTo(w * .12f, h * .14f); lineTo(w * .12f, h * .78f)
                    lineTo(w * .5f, h * .88f); lineTo(w * .88f, h * .78f); lineTo(w * .88f, h * .14f); close()
                }, color, style = stroke)
                drawLine(color, Offset(w * .5f, h * .24f), Offset(w * .5f, h * .88f), stroke.width)
            }
            Destination.FOOTPRINTS -> {
                drawOval(color, Offset(w * .16f, h * .35f), Size(w * .26f, h * .4f), style = stroke)
                drawOval(color, Offset(w * .58f, h * .5f), Size(w * .26f, h * .4f), style = stroke)
                drawCircle(color, w * .08f, Offset(w * .29f, h * .19f))
                drawCircle(color, w * .08f, Offset(w * .71f, h * .34f))
            }
            Destination.CAMERA -> {
                drawRoundRect(color, Offset(w * .1f, h * .26f), Size(w * .8f, h * .6f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .1f), style = stroke)
                drawCircle(color, w * .18f, Offset(w * .5f, h * .56f), style = stroke)
                drawPath(Path().apply {
                    moveTo(w * .3f, h * .26f); lineTo(w * .38f, h * .13f)
                    lineTo(w * .62f, h * .13f); lineTo(w * .7f, h * .26f)
                }, color, style = stroke)
            }
            Destination.ACHIEVEMENTS -> {
                drawPath(Path().apply {
                    moveTo(w * .25f, h * .13f); lineTo(w * .75f, h * .13f); lineTo(w * .7f, h * .46f)
                    quadraticTo(w * .5f, h * .72f, w * .3f, h * .46f); close()
                }, color, style = stroke)
                drawLine(color, Offset(w * .5f, h * .58f), Offset(w * .5f, h * .83f), stroke.width)
                drawLine(color, Offset(w * .29f, h * .88f), Offset(w * .71f, h * .88f), stroke.width)
                drawPath(Path().apply {
                    moveTo(w * .25f, h * .2f); lineTo(w * .1f, h * .2f)
                    quadraticTo(w * .08f, h * .5f, w * .32f, h * .46f)
                    moveTo(w * .75f, h * .2f); lineTo(w * .9f, h * .2f)
                    quadraticTo(w * .92f, h * .5f, w * .68f, h * .46f)
                }, color, style = stroke)
            }
            Destination.SETTINGS -> {
                drawCircle(color, w * .28f, style = stroke)
                drawCircle(color, w * .1f, style = stroke)
                for (i in 0..7) {
                    val angle = Math.PI * i / 4
                    val direction = Offset(kotlin.math.cos(angle).toFloat(), kotlin.math.sin(angle).toFloat())
                    drawLine(color, center + direction * (w * .3f), center + direction * (w * .43f), stroke.width)
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun AppPreview() { PetCollectionApp() }
