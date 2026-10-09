package app.ridetracker.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

/**
 * The app's frosted glass (like Android's volume panel and power menu): what's behind shows through, blurred and
 * tinted with the surface colour, with a hairline outline. Used by the navigation bar and every menu.
 */
val LocalHaze = staticCompositionLocalOf<HazeState?> { null }

/** How much of the surface colour covers the blur: light for bars and menus, more for dialogs full of text. */
const val GLASS_LIGHT = 0.6f
const val GLASS_DIALOG = 0.8f

@Composable
fun rememberGlassStyle(tintAlpha: Float = GLASS_LIGHT): HazeBlurStyle {
    val tint = MaterialTheme.colorScheme.surface.copy(alpha = tintAlpha)
    return remember(tint) {
        HazeBlurStyle {
            blurRadius(24.dp)
            noiseFactor(0.04f)
            colorEffects(listOf(HazeColorEffect.tint(tint)))
        }
    }
}

/** Frosted glass behind this element (clip it to its shape first). Plain surface where blur isn't available. */
@Composable
fun Modifier.glass(tintAlpha: Float = GLASS_LIGHT): Modifier {
    val haze = LocalHaze.current ?: return this
    return hazeBlur(HazeInput.Sources(haze), rememberGlassStyle(tintAlpha))
}

/** Container colour for something drawn in glass: transparent when the blur is there, plain surface when not. */
@Composable
fun glassContainer(): Color = if (LocalHaze.current != null) Color.Transparent else MaterialTheme.colorScheme.surface

/** A Material dropdown menu in the app's frosted glass. */
@Composable
fun GlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = LocalHaze.current != null
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.glass(),
        offset = offset,
        containerColor = if (glass) Color.Transparent else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content,
    )
}
