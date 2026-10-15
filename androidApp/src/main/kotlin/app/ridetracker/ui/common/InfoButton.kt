package app.ridetracker.ui.common

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.ridetracker.R
import kotlinx.coroutines.launch

/**
 * A small ⓘ beside a card's title: tapping it shows [text] in an outlined tooltip. Home's cards keep explanations
 * here instead of in sentences on the card (owner, 16 Oct 2026: too much text).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoButton(text: String, modifier: Modifier = Modifier) {
    val state = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    val shape = TooltipDefaults.richTooltipContainerShape
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            RichTooltip(
                // Plain surface with the hairline outline: the blur doesn't reach into a tooltip's popup.
                modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
                shape = shape,
                colors = TooltipDefaults.richTooltipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                shadowElevation = 0.dp,
                tonalElevation = 0.dp,
            ) { Text(text) }
        },
        state = state,
        modifier = modifier,
    ) {
        IconButton(onClick = { scope.launch { state.show() } }, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = stringResource(R.string.more_info),
                modifier = Modifier.size(18.dp),
                tint = LocalContentColor.current.copy(alpha = 0.7f),
            )
        }
    }
}
