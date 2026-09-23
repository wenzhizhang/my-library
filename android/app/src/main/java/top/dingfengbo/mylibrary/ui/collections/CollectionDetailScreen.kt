package top.dingfengbo.mylibrary.ui.collections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import top.dingfengbo.mylibrary.R
import top.dingfengbo.mylibrary.ui.common.AppTopBar
import top.dingfengbo.mylibrary.data.AppContainer
import top.dingfengbo.mylibrary.data.model.BookQuery
import top.dingfengbo.mylibrary.data.model.BookScope
import top.dingfengbo.mylibrary.theme.NumericTextStyle
import top.dingfengbo.mylibrary.theme.Spacing
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
fun CollectionDetailScreen(
    container: AppContainer,
    collectionId: Int,
    onBack: () -> Unit,
    onOpenBook: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CollectionDetailViewModel = viewModel {
        CollectionDetailViewModel(container.collectionRepository, container.libraryEvents, collectionId)
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
                        text = ui.collection?.name ?: stringResource(R.string.collections_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    if (ui.collection != null) {
                        TextButton(onClick = { editing = true }) { Text(stringResource(R.string.catalog_edit)) }
                    }
                },
            )
        },
        modifier = modifier,
    ) { padding ->
        val collection = ui.collection
        when {
            // The skeleton copies the shape of the header and rows, so nothing moves when the
            // record lands.
            ui.loading && collection == null -> DetailSkeleton(Modifier.fillMaxSize().padding(padding))

            collection == null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { DetailLoadError(error = ui.error, onRetry = viewModel::load) }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                collection.intro?.takeIf { it.isNotBlank() }?.let { intro ->
                    item {
                        Text(intro, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(Spacing.sm))
                    }
                }

                item {
                    CollectionHeader(
                        onCreate = { addingBooks = true },
                        count = collection.totalBooks ?: collection.books?.size ?: 0,
                    )
                }

                items(collection.books.orEmpty(), key = { it.id ?: 0 }) { book ->
                    SimpleBookRow(
                        id = book.id,
                        title = book.title,
                        titleCn = book.titleCn,
                        thumbImage = book.thumbImage,
                        authors = book.authors,
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

    val collection = ui.collection
    if (editing && collection != null) {
        TextFieldsDialog(
            titleRes = R.string.collections_edit,
            fields = listOf(
                DialogField(R.string.attr_name, collection.name),
                DialogField(R.string.attr_intro, collection.intro.orEmpty(), multiline = true),
            ),
            onSave = { values -> viewModel.save(values[0], values.getOrElse(1) { "" }) },
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
            excluded = ui.collection?.books.orEmpty().mapNotNull { it.id }.toSet(),
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
                R.string.collections_delete_confirm_message,
                collection?.name.orEmpty(),
            ),
            onConfirm = {
                confirmingDelete = false
                viewModel.delete()
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

@Composable
private fun CollectionHeader(onCreate: () -> Unit, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = pluralStringResource(R.plurals.books_count, count, count),
            style = MaterialTheme.typography.labelSmall.merge(NumericTextStyle),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onCreate) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.xs))
            Text(stringResource(R.string.collections_add_books))
        }
    }
}
