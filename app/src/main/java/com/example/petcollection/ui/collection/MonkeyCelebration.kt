package com.example.petcollection.ui.collection

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.petcollection.R
import com.example.petcollection.data.Breed
import com.example.petcollection.data.BreedPhoto
import com.example.petcollection.data.EncounterRecord
import com.example.petcollection.ui.effects.Banana
import com.example.petcollection.ui.effects.RecognitionEffectAnchor
import com.example.petcollection.ui.effects.RecognitionEffectsState

@Composable
internal fun monkeyContainerColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < .5f) Color(0xFF493B16) else Color(0xFFFFF2CC)

@Composable
internal fun monkeyContentColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < .5f) Color(0xFFFFE5A0) else Color(0xFF4C3900)

@Composable
internal fun monkeyCardColors() = CardDefaults.cardColors(
    containerColor = monkeyContainerColor(), contentColor = monkeyContentColor())

@Composable
internal fun MonkeyLegendaryBadge() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Banana(Modifier.size(24.dp))
        Text(stringResource(R.string.monkey_rarity), style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold)
    }
}
/** Finite vector animation confined to the photo: it never covers the dialog controls. */
@Composable
internal fun MonkeyRecognitionPolaroid(
    breed: Breed, draft: EncounterRecord, firstDiscovery: Boolean,
    effects: RecognitionEffectsState? = null
) {
    var played by rememberSaveable(draft.id) { mutableStateOf(false) }
    val reveal = remember(draft.id) { Animatable(if (played) 1f else 0f) }
    LaunchedEffect(draft.id) {
        if (!played) {
            played = true
            reveal.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        }
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(8.dp)) {
        Card(
            modifier = Modifier.graphicsLayer {
                scaleX = .72f + .28f * reveal.value
                scaleY = scaleX
                rotationZ = -6f + 5f * reveal.value
            },
            shape = RoundedCornerShape(6.dp), colors = monkeyCardColors(),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RecognitionEffectAnchor(effects, draft.id, Modifier.fillMaxWidth().aspectRatio(1f)) {
                    BreedImageArea(breed, BreedPhoto(draft.id, draft.photoUri), locked = false, modifier = Modifier.fillMaxSize())
                }
                Column(Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(if (firstDiscovery) R.string.monkey_first else R.string.monkey_again),
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (firstDiscovery) {
                        Text(breed.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.monkey_discovery), style = MaterialTheme.typography.labelLarge)
                        Text(stringResource(R.string.monkey_congratulations), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.monkey_legend), style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium)
                    } else MonkeyLegendaryBadge()
                }
            }
        }
    }
}
