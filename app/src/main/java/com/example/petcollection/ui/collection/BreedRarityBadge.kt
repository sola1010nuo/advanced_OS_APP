package com.example.petcollection.ui.collection

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import com.example.petcollection.R
import com.example.petcollection.data.Breed

/** A shared, background-free line for rated breeds; other catalog entries have no stars. */
@Composable
internal fun RarityStars(breed: Breed) {
    val rarity = breed.rarity ?: return
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < .5f
    val filledColor = when (rarity.stars) {
        1 -> scheme.onSurfaceVariant
        2 -> scheme.primary
        3 -> if (dark) Color(0xFFAACFE9) else Color(0xFF4C708D)
        4 -> if (dark) Color(0xFFDABAE5) else Color(0xFF866091)
        else -> if (dark) Color(0xFFE6C77D) else Color(0xFF9A762D)
    }
    val name = stringResource(rarity.labelRes)
    val description = stringResource(R.string.rarity_description, rarity.stars, name)
    val line = buildAnnotatedString {
        withStyle(SpanStyle(color = filledColor)) { append("★".repeat(rarity.stars)) }
        withStyle(SpanStyle(color = scheme.onSurfaceVariant.copy(alpha = .4f))) {
            append("☆".repeat(5 - rarity.stars))
        }
        append("  ")
        withStyle(SpanStyle(color = scheme.onSurfaceVariant)) { append(name) }
    }
    Text(line, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.clearAndSetSemantics { contentDescription = description })
}
