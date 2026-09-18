package top.dingfengbo.mylibrary.ui.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import top.dingfengbo.mylibrary.R
import top.dingfengbo.mylibrary.data.AppContainer
import top.dingfengbo.mylibrary.data.model.BookQuery
import top.dingfengbo.mylibrary.data.model.BookScope
import top.dingfengbo.mylibrary.ui.common.BookPickerDialog
import top.dingfengbo.mylibrary.ui.common.DialogField
import top.dingfengbo.mylibrary.ui.common.SimpleBookRow
import top.dingfengbo.mylibrary.ui.common.TextFieldsDialog
import top.dingfengbo.mylibrary.ui.common.errorMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanDetailScreen(
    container: AppContainer,
    planId: Int,
    onBack: () -> Unit,
    onOpenBook: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlanDetailViewModel = viewModel {
        PlanDetailViewModel(container.readingPlanRepository, container.libraryEvents, planId)
    },
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    var addingBooks by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    LaunchedEffect(ui.deleted) {
        if (ui.deleted) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = ui.plan?.name ?: stringResource(R.string.plans_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) } },
                actions = {
                    if (ui.plan != null) {
                        TextButton(onClick = { editing = true }) { Text(stringResource(R.string.catalog_edit)) }
                    }
                },
            )
        },
        modifier = modifier,
    ) { padding ->
        val plan = ui.plan
        when {
            ui.loading && plan == null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            plan == null -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(errorMessage(ui.error) ?: stringResource(R.string.error_unknown))
                Spacer(Modifier.height(12.dp))
                Button(onClick = viewModel::load) { Text(stringResource(R.string.error_retry)) }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item {
                    Column(Modifier.fillMaxWidth()) {
                        val progress = plan.progress?.toFloat()?.div(100f)
                        if (progress != null) {
                            LinearProgressIndicator(
                                progress = { progress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${plan.progress?.toPlainString()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val dates = listOfNotNull(plan.startDate?.take(10), plan.endDate?.take(10))
                            .joinToString(" – ")
                        if (dates.isNotBlank()) {
                            Text(
                                text = dates,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        plan.intro?.takeIf { it.isNotBlank() }?.let { intro ->
                            Spacer(Modifier.height(6.dp))
                            Text(intro, style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.books_count, plan.totalBooks ?: plan.books?.size ?: 0),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = { addingBooks = true }) {
                                Text(stringResource(R.string.collections_add_books))
                            }
                        }
                    }
                }

                items(plan.books.orEmpty(), key = { it.id ?: 0 }) { book ->
                    Column {
                        SimpleBookRow(
                            id = book.id,
                            title = book.title,
                            titleCn = book.titleCn,
                            thumbImage = book.thumbImage,
                            authors = book.authors,
                            onClick = { book.id?.let(onOpenBook) },
                            onRemove = { book.id?.let(viewModel::removeBook) },
                        )
                        book.readState?.takeIf { it.isNotBlank() }?.let { state ->
                            Text(
                                text = state,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 46.dp, bottom = 4.dp),
                            )
                        }
                    }
                }

                item {
                    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        ui.actionError?.let {
                            Text(
                                text = errorMessage(it) ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { confirmingDelete = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.catalog_delete)) }
                    }
                }
            }
        }
    }

    val plan = ui.plan
    if (editing && plan != null) {
        TextFieldsDialog(
            titleRes = R.string.plans_edit,
            fields = listOf(
                DialogField(R.string.attr_name, plan.name),
                DialogField(R.string.attr_intro, plan.intro.orEmpty(), multiline = true),
                DialogField(R.string.plans_start_date, plan.startDate.orEmpty()),
                DialogField(R.string.plans_end_date, plan.endDate.orEmpty()),
            ),
            onSave = { values ->
                viewModel.save(
                    name = values[0],
                    intro = values.getOrElse(1) { "" },
                    startDate = values.getOrElse(2) { "" },
                    endDate = values.getOrElse(3) { "" },
                )
            },
            onDismiss = { editing = false },
        )
    }

    if (addingBooks) {
        BookPickerDialog(
            search = { query ->
                container.bookRepository
                    .page(BookScope.All, BookQuery(text = query), page = 1)
                    .map { it.books }
            },
            onConfirm = { bookIds ->
                addingBooks = false
                viewModel.addBooks(bookIds)
            },
            onDismiss = { addingBooks = false },
        )
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.catalog_delete_confirm_title, plan?.name.orEmpty())) },
            text = { Text(stringResource(R.string.plans_delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    viewModel.delete()
                }) { Text(stringResource(R.string.catalog_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}
