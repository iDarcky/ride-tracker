package app.ridetracker.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/** Preset colours for platform badges. */
val platformColorPresets: List<Long> = listOf(
    0xFF000000, 0xFF34D186, 0xFF1E5EFF, 0xFFE53935, 0xFFFB8C00,
    0xFFFDD835, 0xFF8E24AA, 0xFF00897B, 0xFF6D4C41, 0xFF546E7A,
)

private fun contrast(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return max(la, lb) / min(la, lb)
}

/** Rounded square with the platform's first letter: works for any app without brand logos. */
@Composable
fun PlatformBadge(name: String, colorArgb: Long, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val background = Color(colorArgb)
    val shape = RoundedCornerShape(size / 4) // 10dp at the standard 40dp size
    // Outline badges that blend into the surface, e.g. Uber black in dark mode.
    val needsOutline = contrast(background, MaterialTheme.colorScheme.surface) < 1.6f
    Box(
        modifier = modifier
            .size(size)
            .then(if (needsOutline) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape) else Modifier)
            .background(background, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().take(1).uppercase(),
            color = if (background.luminance() > 0.5f) Color.Black else Color.White,
            style = if (size >= 40.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
        )
    }
}
