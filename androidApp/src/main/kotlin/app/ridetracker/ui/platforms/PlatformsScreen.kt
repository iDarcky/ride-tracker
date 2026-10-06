package app.ridetracker.ui.platforms

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ridetracker.R
import app.ridetracker.shared.data.PlatformEntity
import app.ridetracker.ui.common.PlatformBadge
import app.ridetracker.ui.common.container
import app.ridetracker.ui.common.platformColorPresets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformsScreen(
    onBack: () -> Unit,
    viewModel: PlatformsViewModel = viewModel { PlatformsViewModel(container.incomeRepository) },
) {
    val platforms by viewModel.platforms.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<PlatformEntity?>(null) }
    var adding by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.apps)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_app)) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 96.dp),
        ) {
            items(platforms, key = { it.id }) { platform ->
                ListItem(
                    modifier = Modifier.clickable { editing = platform },
                    leadingContent = {
                        PlatformBadge(
                            platform.name,
                            platform.colorArgb,
                            modifier = if (platform.archived) Modifier.alpha(0.4f) else Modifier,
                        )
                    },
                    headlineContent = { Text(platform.name) },
                    supportingContent = { Text(stringResource(if (platform.archived) R.string.app_archived else R.string.app_active)) },
                    trailingContent = {
                        Switch(
                            checked = !platform.archived,
                            onCheckedChange = { active -> viewModel.update(platform.copy(archived = !active)) },
                        )
                    },
                )
            }
        }
    }

    if (adding) {
        PlatformDialog(
            title = stringResource(R.string.add_app),
            initialName = "",
            initialColor = platformColorPresets[platforms.size % platformColorPresets.size],
            onDismiss = { adding = false },
            onConfirm = { name, color ->
                viewModel.add(name, color)
                adding = false
            },
        )
    }
    editing?.let { platform ->
        PlatformDialog(
            title = stringResource(R.string.edit_app),
            initialName = platform.name,
            initialColor = platform.colorArgb,
            onDismiss = { editing = null },
            onConfirm = { name, color ->
                viewModel.update(platform.copy(name = name, colorArgb = color))
                editing = null
            },
        )
    }
}

@Composable
private fun PlatformDialog(
    title: String,
    initialName: String,
    initialColor: Long,
    onDismiss: () -> Unit,
    onConfirm: (String, Long) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableLongStateOf(initialColor) }
    val selectedLabel = stringResource(R.string.colour_selected)
    val optionLabel = stringResource(R.string.colour_option)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.colour), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    platformColorPresets.forEach { preset ->
                        val selected = preset == color
                        PlatformBadge(
                            name = if (name.isBlank()) "?" else name,
                            colorArgb = preset,
                            size = 40.dp,
                            modifier = Modifier
                                .semantics { contentDescription = if (selected) selectedLabel else optionLabel }
                                .then(
                                    if (selected) {
                                        Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)).padding(4.dp)
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable { color = preset },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name, color) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
