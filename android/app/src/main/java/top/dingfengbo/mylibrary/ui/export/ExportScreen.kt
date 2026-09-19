package top.dingfengbo.mylibrary.ui.export

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import top.dingfengbo.mylibrary.R
import top.dingfengbo.mylibrary.data.AppContainer
import top.dingfengbo.mylibrary.data.ExportRepository.Format
import top.dingfengbo.mylibrary.data.ExportRepository.Scope
import top.dingfengbo.mylibrary.ui.common.errorMessage

/**
 * Export via the system "save as" dialog.
 *
 * SAF rather than DownloadManager on purpose: the download has to go through the app's OkHttp stack,
 * where the token is attached and a 401 is recognised. DownloadManager runs in another process and
 * would happily save the error body as a `.csv`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    container: AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExportViewModel = viewModel {
        ExportViewModel(
            repository = container.exportRepository,
            context = container.applicationContext,
            onSessionRejected = container.sessionManager::onTokenRejected,
        )
    },
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<ExportRequest?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(mimeTypeOf(pending)),
    ) { uri: Uri? ->
        val request = pending
        pending = null
        if (uri != null && request != null) viewModel.run(request, uri)
    }

    // Launched from the state change, not from the click: the MIME is part of the CreateDocument
    // contract, and registering it is the composition that follows `pending = request`. Launching
    // inside the click handler hands the system the MIME of the *previous* export.
    LaunchedEffect(pending) {
        pending?.let { saveLauncher.launch(viewModel.suggestedName(it)) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.export_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) } },
            )
        },
        modifier = modifier,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.export_format), style = MaterialTheme.typography.labelMedium)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Format.entries.forEach { format ->
                    FilterChip(
                        selected = ui.format == format,
                        onClick = { viewModel.onFormatChange(format) },
                        label = { Text(format.value.uppercase()) },
                    )
                }
            }

            Text(stringResource(R.string.export_scope), style = MaterialTheme.typography.labelMedium)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Scope.entries.forEach { scope ->
                    FilterChip(
                        selected = ui.scope == scope,
                        onClick = { viewModel.onScopeChange(scope) },
                        label = { Text(stringResource(scope.labelRes)) },
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Button(
                enabled = !ui.running,
                onClick = { pending = ExportRequest.Data(ui.format, ui.scope) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.export_save_as)) }

            OutlinedButton(
                enabled = !ui.running,
                onClick = { pending = ExportRequest.Database },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.export_database)) }

            if (ui.running) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(Modifier.height(22.dp), strokeWidth = 2.dp)
                }
            }

            ui.done?.let {
                Text(
                    text = stringResource(R.string.export_done, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            ui.error?.let {
                Text(
                    text = errorMessage(it) ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** What the "save as" dialog should file the export under; a null request is the "nothing pending" state. */
private fun mimeTypeOf(request: ExportRequest?): String = when (request) {
    is ExportRequest.Data -> when (request.format) {
        Format.Csv -> "text/csv"
        Format.Json -> "application/json"
        Format.Markdown -> "text/markdown"
        Format.Sql -> "application/sql"
        Format.Excel -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    }

    ExportRequest.Database -> "application/vnd.sqlite3"
    null -> "application/octet-stream"
}

private val Scope.labelRes: Int
    get() = when (this) {
        Scope.Books -> R.string.books_title
        Scope.Authors -> R.string.catalog_authors
        Scope.Publishers -> R.string.catalog_publishers
        Scope.Brands -> R.string.catalog_brands
        Scope.Series -> R.string.catalog_series
        Scope.Categories -> R.string.catalog_categories
        Scope.Bookshelves -> R.string.catalog_bookshelves
        Scope.Collections -> R.string.collections_title
    }
