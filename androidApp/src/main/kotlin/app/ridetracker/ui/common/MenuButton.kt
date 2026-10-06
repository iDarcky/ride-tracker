package app.ridetracker.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.res.stringResource
import app.ridetracker.R

/** Opens the top-right menu panel; provided by the navigation host. */
val LocalOpenMenu = compositionLocalOf<() -> Unit> { {} }

/** Top-right button on every main tab (where Google apps put the account picture). */
@Composable
fun MenuButton() {
    val open = LocalOpenMenu.current
    IconButton(onClick = open) {
        Icon(Icons.Outlined.AccountCircle, contentDescription = stringResource(R.string.open_menu))
    }
}
