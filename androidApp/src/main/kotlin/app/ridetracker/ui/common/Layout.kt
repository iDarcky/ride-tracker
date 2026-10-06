package app.ridetracker.ui.common

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Space the floating navigation bar takes at the bottom of top-level screens.
 * Lists add it to their bottom padding and FABs sit above it.
 */
val LocalBottomBarSpace = compositionLocalOf<Dp> { 0.dp }
