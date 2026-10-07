package com.example.petcollection.ui

import android.app.Activity
import android.content.res.Configuration
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.core.view.WindowCompat
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.petcollection.R
import com.example.petcollection.ui.collection.CollectionScreen
import com.example.petcollection.ui.collection.rememberCollectionUnlockState
import com.example.petcollection.ui.home.HomePreviewData
import com.example.petcollection.ui.home.HomeScreen
import com.example.petcollection.ui.theme.PetCollectionTheme
import java.util.Locale

private enum class Destination(@param:StringRes val label: Int) {
    HOME(R.string.home), COLLECTION(R.string.collection), FOOTPRINTS(R.string.footprints), SETTINGS(R.string.settings)
}
private enum class Appearance(@param:StringRes val label: Int) {
    SYSTEM(R.string.follow_system), LIGHT(R.string.light_mode), DARK(R.string.dark_mode)
}

@Composable
fun PetCollectionApp() {
    val collectionUnlockState = rememberCollectionUnlockState()
    // UI prototype only: retained across rotation, reset on a fresh app launch.
    var language by rememberSaveable { mutableStateOf("zh-Hant") }
    var appearance by rememberSaveable { mutableStateOf(Appearance.SYSTEM.name) }
    var destination by rememberSaveable { mutableStateOf(Destination.HOME.name) }
    val baseContext = LocalContext.current
    val systemConfiguration = LocalConfiguration.current
    val configuration = remember(systemConfiguration, language) {
        Configuration(systemConfiguration).apply {
            setLocales(LocaleList(Locale.forLanguageTag(language)))
        }
    }
    val localizedContext = remember(baseContext, configuration) {
        baseContext.createConfigurationContext(configuration)
    }
    val dark = when (Appearance.valueOf(appearance)) {
        Appearance.SYSTEM -> isSystemInDarkTheme()
        Appearance.LIGHT -> false
        Appearance.DARK -> true
    }
    val view = LocalView.current
    SideEffect {
        (baseContext as? Activity)?.let { activity ->
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalContext provides localizedContext, LocalConfiguration provides configuration) {
        PetCollectionTheme(darkTheme = dark) {
            Scaffold(
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        Destination.entries.forEach { item ->
                            NavigationBarItem(
                                selected = destination == item.name,
                                onClick = { destination = item.name },
                                icon = { DestinationIcon(item) },
                                label = { Text(stringResource(item.label)) }
                            )
                        }
                    }
                }
            ) { padding ->
                val contentModifier = Modifier.padding(padding).consumeWindowInsets(padding)
                when (Destination.valueOf(destination)) {
                    Destination.HOME -> HomeScreen(contentModifier)
                    Destination.COLLECTION -> CollectionScreen(contentModifier, unlockState = collectionUnlockState)
                    Destination.FOOTPRINTS -> FootprintsScreen(contentModifier)
                    Destination.SETTINGS -> SettingsScreen(
                        language, { language = it }, Appearance.valueOf(appearance), { appearance = it.name }, contentModifier
                    )
                }
            }
        }
    }
}

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
    modifier: Modifier
) {
    Page(R.string.settings, modifier) {
        SettingsGroup(R.string.language) {
            SettingOption(R.string.language_zh, language == "zh-Hant") { onLanguageChange("zh-Hant") }
            SettingOption(R.string.language_en, language == "en") { onLanguageChange("en") }
        }
        SettingsGroup(R.string.appearance) {
            Appearance.entries.forEach { item ->
                SettingOption(item.label, appearance == item) { onAppearanceChange(item) }
            }
        }
        Text(stringResource(R.string.settings_hint), style = MaterialTheme.typography.bodySmall,
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
