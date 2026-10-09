package app.ridetracker.ui.common

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Space the floating navigation bar takes at the bottom of top-level screens.
 * Lists add it to their bottom padding and FABs sit above it.
 */
val LocalBottomBarSpace = compositionLocalOf<Dp> { 0.dp }

/** How many times the current tab was tapped again in the navigation bar (changes = "go back to the top"). */
val LocalTabReselects = compositionLocalOf { 0 }

/** Scrolls [state] to the top when the driver taps this screen's tab while already on it. */
@Composable
fun ScrollToTopOnReselect(state: LazyListState) {
    val reselects = LocalTabReselects.current
    val atStart = remember { reselects }
    LaunchedEffect(reselects) {
        if (reselects != atStart) state.animateScrollToItem(0)
    }
}
