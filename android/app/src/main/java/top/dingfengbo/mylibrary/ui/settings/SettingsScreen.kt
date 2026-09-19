package top.dingfengbo.mylibrary.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import top.dingfengbo.mylibrary.R
import top.dingfengbo.mylibrary.data.AppContainer
import top.dingfengbo.mylibrary.data.MediaUrls
import top.dingfengbo.mylibrary.data.auth.Session
import top.dingfengbo.mylibrary.ui.common.errorMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    session: Session,
    serverUrl: String,
    onBack: () -> Unit,
    onSignOut: suspend () -> Unit,
    onOpenStats: () -> Unit,
    onOpenExport: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel {
        SettingsViewModel(container.preferencesRepository, container.backgroundState)
    },
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var confirmingSignOut by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
                },
            )
        },
        modifier = modifier,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            Text(stringResource(R.string.settings_account), style = MaterialTheme.typography.titleSmall)
            InfoRow(stringResource(R.string.settings_username), session.username)
            InfoRow(stringResource(R.string.settings_user_uuid), session.uuid)

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Text(stringResource(R.string.settings_server), style = MaterialTheme.typography.titleSmall)
            InfoRow(null, serverUrl)

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Text(stringResource(R.string.settings_more), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onOpenStats) { Text(stringResource(R.string.stats_title)) }
            TextButton(onClick = onOpenExport) { Text(stringResource(R.string.export_title)) }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Text(stringResource(R.string.settings_background), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))

            if (ui.loading && ui.backgrounds.isEmpty()) {
                CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
            }
            // What is actually painted: the account's own choice, or the configured default when it
            // has not chosen one — the web front end's `selectedId || defaultId`.
            val activeId = ui.selectedId ?: ui.defaultId
            ui.backgrounds.forEach { background ->
                val id = background.id
                val active = id != null && id == activeId
                Row(
                    Modifier.fillMaxWidth()
                        .clickable(enabled = id != null) { id?.let(viewModel::select) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = MediaUrls.image(background.url),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(width = 72.dp, height = 40.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = background.name.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    // Marked separately from the checkmark: with a choice of your own, the default is
                    // still worth identifying — it is what a signed-out app and other devices show.
                    if (id != null && id == ui.defaultId) {
                        Text(
                            text = stringResource(R.string.settings_background_default),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    if (active) {
                        Text("✓", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            ui.error?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = errorMessage(it) ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Button(onClick = { confirmingSignOut = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_logout))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.settings_background_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (confirmingSignOut) {
        AlertDialog(
            onDismissRequest = { confirmingSignOut = false },
            title = { Text(stringResource(R.string.settings_logout_confirm_title)) },
            text = { Text(stringResource(R.string.settings_logout_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingSignOut = false
                    scope.launch { onSignOut() }
                }) {
                    Text(stringResource(R.string.settings_logout_confirm_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingSignOut = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun InfoRow(label: String?, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}
