package top.dingfengbo.mylibrary.ui.catalog

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import top.dingfengbo.mylibrary.R
import top.dingfengbo.mylibrary.ui.common.AppTopBar
import top.dingfengbo.mylibrary.data.AppContainer
import top.dingfengbo.mylibrary.data.model.CatalogEntity
import top.dingfengbo.mylibrary.data.model.EntityAttribute
import top.dingfengbo.mylibrary.theme.IdentifierTextStyle
import top.dingfengbo.mylibrary.theme.NumericTextStyle
import top.dingfengbo.mylibrary.theme.Spacing
import top.dingfengbo.mylibrary.ui.books.BookCover
import top.dingfengbo.mylibrary.ui.books.BookRowItem
import top.dingfengbo.mylibrary.ui.common.BookListSkeleton
import top.dingfengbo.mylibrary.ui.common.DetailSkeleton
import top.dingfengbo.mylibrary.ui.common.EmptyState
import top.dingfengbo.mylibrary.ui.common.ErrorState
import top.dingfengbo.mylibrary.ui.common.errorMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogDetailScreen(
    container: AppContainer,
    entity: CatalogEntity,
    entityId: Int,
    onBack: () -> Unit,
    onOpenBook: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CatalogDetailViewModel = viewModel {
        CatalogDetailViewModel(container.catalogRepository, container.libraryEvents, entity, entityId)
    },
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var confirmingDelete by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(ui.deleted) {
        if (ui.deleted) onBack()
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { viewModel.onBooksScrolledTo(it) }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = {
                    Text(
                        text = ui.detail?.title ?: stringResource(entity.titleRes),
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
                    if (ui.detail != null) {
                        IconButton(onClick = { viewModel.onEditingChange(true) }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.catalog_edit),
                            )
                        }
                    }
                },
            )
        },
        modifier = modifier,
    ) { padding ->
        val detail = ui.detail
        when {
            ui.loading && detail == null ->
                DetailSkeleton(Modifier.fillMaxSize().padding(padding))

            detail == null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                ErrorState(
                    message = errorMessage(ui.error) ?: stringResource(R.string.error_unknown),
                    onRetry = viewModel::load,
                )
            }

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                item {
                    Row(Modifier.fillMaxWidth()) {
                        BookCover(
                            path = detail.photo,
                            modifier = Modifier.width(96.dp).height(96.dp),
                        )
                        Spacer(Modifier.width(Spacing.lg))
                        Column(Modifier.weight(1f)) {
                            // The kind above the name: the six screens share one layout, and the
                            // fields alone do not say which catalog this record belongs to.
                            Text(
                                text = stringResource(entity.titleRes),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(Spacing.xs))
                            Text(
                                text = detail.title,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                items(attributeOrder.filter { detail.attributes[it] != null }) { attribute ->
                    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
                        Text(
                            text = stringResource(attribute.labelRes()),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(88.dp),
                        )
                        Text(
                            text = detail.attributes[attribute].orEmpty(),
                            style = attributeValueStyle(attribute),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                item {
                    Column(Modifier.fillMaxWidth().padding(top = Spacing.lg)) {
                        HorizontalDivider()
                        Spacer(Modifier.height(Spacing.md))
                        Text(
                            text = if (ui.booksTotal > 0) {
                                stringResource(R.string.catalog_related_books_count, ui.booksTotal)
                            } else {
                                stringResource(R.string.catalog_related_books)
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                // The book list's own row, so a book looks the same wherever it is listed.
                items(ui.books, key = { it.id ?: 0 }) { book ->
                    BookRowItem(book = book, onClick = { book.id?.let(onOpenBook) })
                }

                item {
                    when {
                        // A failed page is not "no books": the count above came from the entity itself.
                        ui.booksError != null -> ErrorState(
                            message = errorMessage(ui.booksError) ?: stringResource(R.string.error_unknown),
                            onRetry = viewModel::retryBooks,
                        )

                        ui.booksLoading && ui.books.isEmpty() -> BookListSkeleton(rows = 3)

                        ui.books.isEmpty() -> EmptyState(
                            icon = Icons.Default.Menu,
                            title = stringResource(R.string.catalog_no_books),
                        )

                        ui.booksLoadingMore -> Row(
                            Modifier.fillMaxWidth().padding(Spacing.sm),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                }

                item {
                    Column(Modifier.fillMaxWidth().padding(top = Spacing.lg)) {
                        if (ui.actionError != null) {
                            ErrorState(
                                message = errorMessage(ui.actionError) ?: stringResource(R.string.error_unknown),
                            )
                            Spacer(Modifier.height(Spacing.sm))
                        }
                        HorizontalDivider()
                        Spacer(Modifier.height(Spacing.sm))
                        OutlinedButton(
                            onClick = { confirmingDelete = true },
                            enabled = !ui.saving,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                            Spacer(Modifier.width(Spacing.sm))
                            Text(stringResource(R.string.catalog_delete))
                        }
                    }
                }
            }
        }
    }

    val detail = ui.detail
    if (ui.editing && detail != null) {
        CatalogEditDialog(
            entity = entity,
            attributes = entity.editable,
            initial = detail.edit,
            onSave = { edit -> viewModel.save(edit) },
            onDismiss = { viewModel.onEditingChange(false) },
            loadChoices = container.catalogRepository::attributeChoices,
        )
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.catalog_delete_confirm_title, detail?.title.orEmpty())) },
            text = { Text(stringResource(R.string.catalog_delete_confirm_message)) },
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

/**
 * Attribute values that are data rather than prose get the matching face: tabular figures for ids
 * and counts, monospace for stored paths. Prose stays in the body face.
 */
@Composable
private fun attributeValueStyle(attribute: EntityAttribute): TextStyle = when {
    attribute.numericValue -> MaterialTheme.typography.bodyMedium.merge(NumericTextStyle)
    attribute.identifierValue -> MaterialTheme.typography.bodySmall.merge(IdentifierTextStyle)
    else -> MaterialTheme.typography.bodyMedium
}
