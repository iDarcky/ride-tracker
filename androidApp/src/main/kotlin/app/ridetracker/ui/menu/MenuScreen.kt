package app.ridetracker.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ridetracker.BuildConfig
import app.ridetracker.R

private data class MenuEntry(val icon: ImageVector, val title: String, val summary: String?, val onClick: () -> Unit)

/**
 * Full-screen panel opened from the top-right button, like Google Photos and Google Health.
 * Holds everything that is not a daily task: apps, data, settings (later: premium, help, feedback).
 */
@Composable
fun MenuScreen(
    onClose: () -> Unit,
    onManagePlatforms: () -> Unit,
    onImport: () -> Unit,
    onYourData: () -> Unit,
    onSettings: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close)) }
            }
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 8.dp, bottom = 24.dp),
            )
            Group(
                listOf(
                    MenuEntry(Icons.Outlined.UploadFile, stringResource(R.string.import_title), stringResource(R.string.import_menu_summary), onImport),
                    MenuEntry(Icons.Outlined.Apps, stringResource(R.string.apps), stringResource(R.string.apps_summary), onManagePlatforms),
                ),
            )
            Spacer(Modifier.height(16.dp))
            Group(
                listOf(
                    MenuEntry(Icons.Outlined.Storage, stringResource(R.string.section_data), stringResource(R.string.your_data_summary), onYourData),
                    MenuEntry(Icons.Outlined.Settings, stringResource(R.string.nav_settings), null, onSettings),
                ),
            )
            Spacer(Modifier.height(32.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.local_first),
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                stringResource(R.string.version_footer, BuildConfig.VERSION_NAME),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Material 3 Expressive segmented list: one rounded group, small gaps between rows. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Group(entries: List<MenuEntry>) {
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        entries.forEachIndexed { index, entry ->
            SegmentedListItem(
                onClick = entry.onClick,
                shapes = ListItemDefaults.segmentedShapes(index = index, count = entries.size),
                // Faint blue cards on the normal background (no grey panel).
                colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)),
                leadingContent = { Icon(entry.icon, contentDescription = null) },
                supportingContent = entry.summary?.let { { Text(it) } },
                content = { Text(entry.title, style = MaterialTheme.typography.titleMedium) },
            )
        }
    }
}
