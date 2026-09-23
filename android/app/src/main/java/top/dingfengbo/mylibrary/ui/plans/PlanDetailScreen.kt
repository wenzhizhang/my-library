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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.math.BigDecimal
import top.dingfengbo.mylibrary.R
import top.dingfengbo.mylibrary.ui.common.AppTopBar
import top.dingfengbo.mylibrary.data.AppContainer
import top.dingfengbo.mylibrary.data.model.BookQuery
import top.dingfengbo.mylibrary.data.model.BookScope
import top.dingfengbo.mylibrary.theme.NumericTextStyle
import top.dingfengbo.mylibrary.theme.Spacing
import top.dingfengbo.mylibrary.theme.statusColors
import top.dingfengbo.mylibrary.ui.common.BookPickerDialog
import top.dingfengbo.mylibrary.ui.common.DeleteConfirmDialog
import top.dingfengbo.mylibrary.ui.common.DetailActionFooter
import top.dingfengbo.mylibrary.ui.common.DetailLoadError
import top.dingfengbo.mylibrary.ui.common.DetailSkeleton
import top.dingfengbo.mylibrary.ui.common.DialogField
import top.dingfengbo.mylibrary.ui.common.SimpleBookRow
import top.dingfengbo.mylibrary.ui.common.TextFieldsDialog

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
            AppTopBar(
                title = {
                    Text(
                        text = ui.plan?.name ?: stringResource(R.string.plans_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
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
            // The skeleton copies the shape of the header and rows, so nothing moves when the
            // record lands.
            ui.loading && plan == null -> DetailSkeleton(Modifier.fillMaxSize().padding(padding))

            plan == null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { DetailLoadError(error = ui.error, onRetry = viewModel::load) }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                item {
                    Column(Modifier.fillMaxWidth()) {
                        plan.progress?.let { progress ->
                            PlanProgressLine(progress)
                            Spacer(Modifier.height(Spacing.xs))
                        }
                        val dates = listOfNotNull(plan.startDate?.take(10), plan.endDate?.take(10))
                            .joinToString(" – ")
                        if (dates.isNotBlank()) {
                            Text(
                                text = dates,
                                style = MaterialTheme.typography.labelSmall.merge(NumericTextStyle),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        plan.intro?.takeIf { it.isNotBlank() }?.let { intro ->
                            Spacer(Modifier.height(Spacing.sm))
                            Text(intro, style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(Spacing.sm))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val bookCount = plan.totalBooks ?: plan.books?.size ?: 0
                            Text(
                                // A plural, not a string: "1 books" in English is a defect the
                                // reader sees on every one-book collection.
                                text = pluralStringResource(
                                    R.plurals.books_count,
                                    bookCount,
                                    bookCount,
                                ),
                                style = MaterialTheme.typography.labelSmall.merge(NumericTextStyle),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = { addingBooks = true }) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(Spacing.xs))
                                Text(stringResource(R.string.collections_add_books))
                            }
                        }
                    }
                }

                items(plan.books.orEmpty(), key = { it.id ?: 0 }) { book ->
                    SimpleBookRow(
                        id = book.id,
                        title = book.title,
                        titleCn = book.titleCn,
                        thumbImage = book.thumbImage,
                        authors = book.authors,
                        readState = book.readState,
                        onClick = { book.id?.let(onOpenBook) },
                        onRemove = { book.id?.let(viewModel::removeBook) },
                        // Only the row that started the remove says so; the others are untouched.
                        removing = book.id != null && ui.removingBookId == book.id,
                        modifier = Modifier.animateItem(),
                    )
                }

                item {
                    DetailActionFooter(
                        actionError = ui.actionError,
                        onDelete = { confirmingDelete = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
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
                DialogField(R.string.plans_start_date, plan.startDate.orEmpty(), date = true),
                DialogField(R.string.plans_end_date, plan.endDate.orEmpty(), date = true),
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
            excluded = ui.plan?.books.orEmpty().mapNotNull { it.id }.toSet(),
            onConfirm = { bookIds ->
                addingBooks = false
                viewModel.addBooks(bookIds)
            },
            onDismiss = { addingBooks = false },
        )
    }

    if (confirmingDelete) {
        DeleteConfirmDialog(
            message = stringResource(
                R.string.plans_delete_confirm_message,
                plan?.name.orEmpty(),
            ),
            onConfirm = {
                confirmingDelete = false
                viewModel.delete()
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

/** The plan's own progress bar, with the percentage beside it as tabular figures. */
@Composable
private fun PlanProgressLine(progress: BigDecimal) {
    val fraction = progress.toFloat().div(100f).coerceIn(0f, 1f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        LinearProgressIndicator(
            progress = { fraction },
            color = statusColors().read,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = "${progress.toPlainString()}%",
            style = MaterialTheme.typography.labelSmall.merge(NumericTextStyle),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
