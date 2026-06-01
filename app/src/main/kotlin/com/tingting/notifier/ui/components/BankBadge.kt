package com.tingting.notifier.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue

/**
 * Circular initials badge for a bank/wallet (used on Home, History and the
 * supported-banks list). The background hue is derived deterministically from the
 * name so the same bank always gets the same color.
 */
@Composable
fun BankBadge(
    name: String,
    modifier: Modifier = Modifier,
    size: Int = 40,
) {
    val initials = initialsOf(name)
    val palette = badgePalette
    val (bg, fg) = palette[(name.hashCode().absoluteValue) % palette.size]
    Surface(
        modifier = modifier.size(size.dp),
        shape = CircleShape,
        color = bg,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = initials,
                color = fg,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** Up to two uppercase initials from the bank name (e.g. "MB Bank" → "MB"). */
fun initialsOf(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(2).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}

private val badgePalette: List<Pair<Color, Color>> = listOf(
    Color(0xFFE8F5E9) to Color(0xFF2E7D32),
    Color(0xFFFFEBEE) to Color(0xFFC62828),
    Color(0xFFE3F2FD) to Color(0xFF1565C0),
    Color(0xFFE8EAF6) to Color(0xFF283593),
    Color(0xFFE1F5FE) to Color(0xFF0277BD),
    Color(0xFFFCE4EC) to Color(0xFFC2185B),
    Color(0xFFFFF3E0) to Color(0xFFE65100),
    Color(0xFFF3E5F5) to Color(0xFF6A1B9A),
)
