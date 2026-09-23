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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import top.dingfengbo.mylibrary.R
import top.dingfengbo.mylibrary.ui.common.AppTopBar
import top.dingfengbo.mylibrary.data.AppContainer
import top.dingfengbo.mylibrary.data.ExportRepository.Format
import top.dingfengbo.mylibrary.data.ExportRepository.Scope
import top.dingfengbo.mylibrary.theme.Spacing
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
            AppTopBar(
                title = { Text(stringResource(R.string.export_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
        modifier = modifier,
    ) { padding ->
        // Both buttons are dead while a picker is open or an export is running: a second export into
        // the same file is not something the reader can want.
        val busy = ui.running != null || pending != null

        Column(
            Modifier.fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxl),
        ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SectionLabel(R.string.export_format)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Format.entries.forEachIndexed { index, format ->
                        SegmentedButton(
                            selected = ui.format == format,
                            enabled = !busy,
                            onClick = { viewModel.onFormatChange(format) },
                            shape =
                                SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = Format.entries.size,
                                ),
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            // The file extension rather than a format name: the question the reader is
                            // answering is which file they end up with, and ".md" needs no translation.
                            Text(".${format.extension}")
                        }
                    }
                }
                Help(stringResource(formatHelp(ui.format)))
            }

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SectionLabel(R.string.export_scope)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Scope.entries.forEach { scope ->
                        FilterChip(
                            selected = ui.scope == scope,
                            enabled = !busy,
                            onClick = { viewModel.onScopeChange(scope) },
                            label = { Text(stringResource(scope.labelRes)) },
                        )
                    }
                }
                Help(stringResource(R.string.export_scope_help, stringResource(ui.scope.labelRes)))
            }

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Button(
                    enabled = !busy,
                    onClick = { pending = ExportRequest.Data(ui.format, ui.scope) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ButtonContent(
                        running = ui.running is ExportRequest.Data,
                        labelRes = R.string.export_save_as,
                        progressColor = MaterialTheme.colorScheme.onPrimary,
                    )
                }

                OutlinedButton(
                    enabled = !busy,
                    onClick = { pending = ExportRequest.Database },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ButtonContent(
                        running = ui.running is ExportRequest.Database,
                        labelRes = R.string.export_database,
                        progressColor = MaterialTheme.colorScheme.primary,
                    )
                }
                Help(stringResource(R.string.export_database_help))
            }

            ui.done?.let { fileName ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = stringResource(R.string.export_done, fileName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            ui.error?.let { throwable ->
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        text = errorMessage(throwable) ?: stringResource(R.string.error_unknown),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    // Retrying has to reopen the picker: the failed attempt left no file to write to.
                    ui.lastRequest?.let { failed ->
                        TextButton(onClick = { pending = failed }) {
                            Text(stringResource(R.string.error_retry))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** What the currently chosen option actually produces. */
@Composable
private fun Help(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A button's own progress: the export runs for seconds on a large library, so it says so in place. */
@Composable
private fun ButtonContent(running: Boolean, labelRes: Int, progressColor: Color) {
    if (running) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = progressColor,
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(stringResource(R.string.export_saving))
    } else {
        Text(stringResource(labelRes))
    }
}

private fun formatHelp(format: Format): Int = when (format) {
    Format.Sql -> R.string.export_format_sql_help
    Format.Csv -> R.string.export_format_csv_help
    Format.Excel -> R.string.export_format_excel_help
    Format.Markdown -> R.string.export_format_markdown_help
    Format.Json -> R.string.export_format_json_help
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
