package app.ridetracker.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity

/**
 * The phone's text is set large (Settings → Display → Font size, 130% and up). Rows that put a label and an
 * amount side by side stack them instead, so nothing is cut off or broken mid-number (checked at 200%).
 */
@Composable
@ReadOnlyComposable
fun isLargeText(): Boolean = LocalDensity.current.fontScale >= LARGE_TEXT_SCALE

private const val LARGE_TEXT_SCALE = 1.3f
