package app.ridetracker.ui.settings

import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ridetracker.R
import app.ridetracker.backup.AutoBackup
import app.ridetracker.RideTrackerApplication
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** The default name of the weekly backup file. */
private const val FILE_NAME = "Ridely backup.json"

/**
 * Weekly backup (its own page, like every setting): pick one file once (Google Drive or the phone), and the backup is
 * written over it every week. Restoring is the usual Your data → Restore with that file.
 */
@Composable
fun AutoBackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = (context.applicationContext as RideTrackerApplication).container.settingsRepository
    val backup by settings.autoBackup.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val savedText = stringResource(R.string.backup_saved)
    val failedText = stringResource(R.string.backup_failed)
    val runNow: () -> Unit = {
        scope.launch {
            val ok = AutoBackup.run(context)
            snackbar.showSnackbar(if (ok) savedText else failedText)
        }
    }
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Keep the right to write it after a restart; the old file's right goes.
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
        scope.launch {
            backup?.fileUri?.let { old -> if (old != uri.toString()) runCatching { context.contentResolver.releasePersistableUriPermission(old.toUri(), flags) } }
            settings.setAutoBackupFile(uri.toString())
            runNow()
        }
    }
    val turnOff: () -> Unit = {
        scope.launch {
            backup?.fileUri?.let { runCatching { context.contentResolver.releasePersistableUriPermission(it.toUri(), flags) } }
            settings.setAutoBackupFile(null)
        }
    }
    val current = backup ?: return

    SettingsFrame(stringResource(R.string.auto_backup), onBack, snackbar) {
        item {
            Text(
                stringResource(R.string.auto_backup_intro),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            ListItem(
                modifier = Modifier.toggleable(value = current.enabled, role = Role.Switch) { on ->
                    if (on) pick.launch(FILE_NAME) else turnOff()
                },
                headlineContent = { Text(stringResource(R.string.auto_backup_switch), style = MaterialTheme.typography.titleLarge) },
                trailingContent = { Switch(checked = current.enabled, onCheckedChange = null) },
            )
        }
        if (current.enabled) {
            item {
                ListItem(
                    modifier = Modifier.clickable { pick.launch(FILE_NAME) },
                    leadingContent = { Icon(Icons.Outlined.CloudUpload, contentDescription = null) },
                    overlineContent = { Text(stringResource(R.string.auto_backup_file)) },
                    headlineContent = { Text(fileName(context, current.fileUri) ?: FILE_NAME) },
                    trailingContent = { TextButton(onClick = { pick.launch(FILE_NAME) }) { Text(stringResource(R.string.auto_backup_change)) } },
                )
            }
            item {
                val last = current.lastBackupEpochMillis?.let { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)) }
                ListItem(
                    leadingContent = if (current.lastFailed) {
                        { Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                    } else {
                        null
                    },
                    headlineContent = {
                        Text(
                            when {
                                current.lastFailed -> stringResource(R.string.auto_backup_failed)
                                last != null -> stringResource(R.string.auto_backup_last, last)
                                else -> stringResource(R.string.auto_backup_never)
                            },
                            color = if (current.lastFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                )
            }
            item {
                FilledTonalButton(onClick = runNow, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.auto_backup_now))
                }
            }
        }
        item {
            Text(
                stringResource(R.string.auto_backup_android),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The file's name as its app shows it ("Ridely backup.json"); null when it can't be read. */
private fun fileName(context: android.content.Context, uri: String?): String? = uri?.let {
    runCatching {
        context.contentResolver.query(it.toUri(), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()
}
