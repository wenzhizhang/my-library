package top.dingfengbo.mylibrary.ui.books

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import top.dingfengbo.mylibrary.R
import top.dingfengbo.mylibrary.api.models.BookCard
import top.dingfengbo.mylibrary.data.AppContainer
import top.dingfengbo.mylibrary.data.MediaUrls
import top.dingfengbo.mylibrary.data.model.CatalogEntity
import top.dingfengbo.mylibrary.data.model.BookScope
import top.dingfengbo.mylibrary.data.model.BookSort
import top.dingfengbo.mylibrary.ui.common.errorMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookListScreen(
    container: AppContainer,
    onOpenBook: (Int) -> Unit,
    onCreateBook: () -> Unit,
    onOpenCatalog: (CatalogEntity) -> Unit,
    onOpenCollections: () -> Unit,
    onOpenPlans: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookListViewModel = viewModel {
        BookListViewModel(container.bookRepository, container.libraryEvents)
    },
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    var catalogMenu by remember { mutableStateOf(false) }

    // Prefetch the next page a screenful before the end. The view model decides whether more exist,
    // so this closure never needs to read a stale list size.
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last -> viewModel.onListScrolledTo(last) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(ui.scope.titleRes)) },
                actions = {
                    TextButton(onClick = { catalogMenu = true }) { Text(stringResource(R.string.catalog_menu)) }
                    TextButton(onClick = viewModel::onToggleView) {
                        Text(
                            stringResource(
                                if (ui.grid) R.string.books_view_list else R.string.books_view_grid
                            )
                        )
                    }
                    TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.settings_title)) }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onCreateBook) {
                Text(stringResource(R.string.books_add_new))
            }
        },
        modifier = modifier,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (ui.supportsFilters) {
                OutlinedTextField(
                    value = ui.searchText,
                    onValueChange = viewModel::onSearchTextChange,
                    placeholder = { Text(stringResource(R.string.books_search_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                )
            }

            Spacer(Modifier.height(8.dp))

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                val scopes = BookScope.entries
                scopes.forEachIndexed { index, scope ->
                    SegmentedButton(
                        selected = ui.scope == scope,
                        onClick = { viewModel.onScopeChange(scope) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = scopes.size),
                    ) {
                        Text(stringResource(scope.titleRes))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                    val sorts = BookSort.entries
                    sorts.forEachIndexed { index, sort ->
                        SegmentedButton(
                            selected = ui.query.sort == sort,
                            onClick = { viewModel.onSortChange(sort) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = sorts.size),
                        ) {
                            Text(stringResource(sort.labelRes), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                if (ui.supportsFilters) {
                    TextButton(onClick = { viewModel.onFilterSheetOpenChange(true) }) {
                        Text(
                            if (ui.query.hasFilters) {
                                stringResource(R.string.books_filter_active, ui.query.filterCount)
                            } else {
                                stringResource(R.string.books_filter)
                            }
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.books_count, ui.totalBooks),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            LazyVerticalGrid(
                columns = if (ui.grid) GridCells.Adaptive(minSize = 108.dp) else GridCells.Fixed(1),
                state = gridState,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(ui.books, key = { it.id ?: 0 }) { book ->
                    if (ui.grid) {
                        BookGridItem(book = book, onClick = { book.id?.let(onOpenBook) })
                    } else {
                        BookRowItem(book = book, onClick = { book.id?.let(onOpenBook) })
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    ListStatus(
                        ui = ui,
                        onRetry = viewModel::reload,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            }
        }
    }

    if (ui.filterSheetOpen) {
        BookFilterSheet(
            initial = ui.query,
            onApply = viewModel::onApplyFilters,
            onDismiss = { viewModel.onFilterSheetOpenChange(false) },
        )
    }

    if (catalogMenu) {
        ModalBottomSheet(onDismissRequest = { catalogMenu = false }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                listOf(
                    stringResource(R.string.collections_title) to onOpenCollections,
                    stringResource(R.string.plans_title) to onOpenPlans,
                ).forEach { (label, open) ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                catalogMenu = false
                                open()
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                    )
                }
                CatalogEntity.entries.forEach { entity ->
                    Text(
                        text = stringResource(entity.titleRes),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                catalogMenu = false
                                onOpenCatalog(entity)
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ListStatus(ui: BookListUiState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    when {
        ui.loading && ui.books.isEmpty() -> Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        ui.error != null && ui.books.isEmpty() -> Column(
            modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(errorMessage(ui.error) ?: "", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onRetry) { Text(stringResource(R.string.error_retry)) }
        }

        ui.showEmpty -> Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.books_empty), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.books_empty_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        ui.loadingMore -> Row(modifier, horizontalArrangement = Arrangement.Center) {
            CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
        }

        ui.error != null -> Text(
            text = errorMessage(ui.error) ?: "",
            color = MaterialTheme.colorScheme.error,
            modifier = modifier,
        )
    }
}

@Composable
internal fun BookRowItem(book: BookCard, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(book.thumbImage, Modifier.width(44.dp).height(58.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            BookTitles(book)
        }
    }
}

@Composable
internal fun BookGridItem(book: BookCard, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick).padding(4.dp)) {
        BookCover(book.thumbImage, Modifier.fillMaxWidth().aspectRatio(3f / 4f))
        Spacer(Modifier.height(6.dp))
        BookTitles(book, compact = true)
    }
}

@Composable
private fun BookTitles(book: BookCard, compact: Boolean = false) {
    Text(
        text = book.displayTitle(),
        style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    val authors = book.authors.orEmpty().joinToString(", ")
    if (authors.isNotEmpty()) {
        Text(
            text = authors,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (!compact) {
        val meta = listOfNotNull(book.isbn, book.publisher?.name).joinToString(" · ")
        if (meta.isNotEmpty()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Chinese title when there is one, matching the web list. */
internal fun BookCard.displayTitle(): String =
    titleCn?.takeIf { it.isNotBlank() } ?: title.orEmpty()

@Composable
internal fun BookCover(path: String?, modifier: Modifier = Modifier) {
    val url = MediaUrls.image(path)
    if (url == null) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    } else {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}

internal val BookScope.titleRes: Int
    get() = when (this) {
        BookScope.All -> R.string.books_scope_all
        BookScope.Wishlist -> R.string.books_scope_wishlist
        BookScope.Archived -> R.string.books_scope_archived
    }

internal val BookSort.labelRes: Int
    get() = when (this) {
        BookSort.Title -> R.string.books_sort_title
        BookSort.CreatedAt -> R.string.books_sort_created
        BookSort.Series -> R.string.books_sort_series
    }
