package app.ridetracker.ui.common

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Space the floating navigation bar takes at the bottom of top-level screens.
 * Lists add it to their bottom padding and FABs sit above it.
 */
val LocalBottomBarSpace = compositionLocalOf<Dp> { 0.dp }

/** How many times the current tab was tapped again in the navigation bar (changes = "go back to the top"). */
val LocalTabReselects = compositionLocalOf { 0 }

/** How many times the driver switched to this tab from another one (changes = "open at the top"). */
val LocalTabEntries = compositionLocalOf { 0 }

/**
 * Tab screens open at the top when the driver switches to them, and scroll up when their tab is tapped again.
 * Coming back from a screen opened on top (a trip, an entry) keeps the place.
 */
@Composable
fun ScrollToTopOnReselect(state: LazyListState) {
    val reselects = LocalTabReselects.current
    val atStart = remember { reselects }
    LaunchedEffect(reselects) {
        if (reselects != atStart) state.animateScrollToItem(0)
    }
    val entries = LocalTabEntries.current
    var seen by rememberSaveable { mutableIntStateOf(entries) }
    LaunchedEffect(entries) {
        if (entries != seen) {
            state.scrollToItem(0)
            seen = entries
        }
    }
}
