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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
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
import top.dingfengbo.mylibrary.theme.NumericTextStyle
import top.dingfengbo.mylibrary.theme.Spacing
import top.dingfengbo.mylibrary.ui.common.BookListSkeleton
import top.dingfengbo.mylibrary.ui.common.EmptyState
import top.dingfengbo.mylibrary.ui.common.ErrorState
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
                    IconButton(onClick = { catalogMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = stringResource(R.string.catalog_menu),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            // Hidden while the empty state is on screen: that state carries the same "add a book"
            // action, and on a short screen the two of them stack over the hint text.
            if (!ui.showEmpty) {
                ExtendedFloatingActionButton(
                    onClick = onCreateBook,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.books_add_new)) },
                )
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
                )
            }

            Spacer(Modifier.height(Spacing.sm))

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)) {
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

            Spacer(Modifier.height(Spacing.sm))

            // The sort control takes the whole row. Sharing it with the two buttons below wrapped
            // "Added" and "Series" onto two lines on a 320dp screen.
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
            ) {
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

            Row(
                modifier = Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = pluralStringResource(R.plurals.books_count, ui.totalBooks, ui.totalBooks),
                    // Tabular figures, so the count does not jitter as it changes.
                    style = MaterialTheme.typography.labelSmall + NumericTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(vertical = Spacing.xs),
                )
                // The list/grid switch belongs with the other "how am I looking at this" choices,
                // not in the bar of destinations.
                TextButton(onClick = viewModel::onToggleView) {
                    Text(stringResource(if (ui.grid) R.string.books_view_list else R.string.books_view_grid))
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

            // A failure that arrives while rows are already on screen used to be one line of text at
            // the very end of the list, where nobody scrolls. It stands above them now.
            if (ui.error != null && ui.books.isNotEmpty()) {
                ErrorState(
                    message = errorMessage(ui.error) ?: stringResource(R.string.error_unknown),
                    onRetry = viewModel::reload,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            PullToRefreshBox(
                // The view model's own load drives the indicator: a refresh of rows already on
                // screen. The first page shows the skeleton instead — see ListStatus.
                isRefreshing = ui.loading && ui.books.isNotEmpty(),
                onRefresh = viewModel::reload,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                if (ui.books.isEmpty()) {
                    // Nothing to scroll yet: the skeleton, empty and error states sit outside the
                    // grid so they keep the screen's own gutter instead of the grid's.
                    // Scrollable here, where the state is the whole content area: on a short screen
                    // the state is taller than the space under the controls, and a state the reader
                    // cannot reach is not a state.
                    ListStatus(
                        ui = ui,
                        onRetry = viewModel::reload,
                        onCreateBook = onCreateBook,
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    )
                } else {
                    LazyVerticalGrid(
                        columns = if (ui.grid) GridCells.Adaptive(minSize = 108.dp) else GridCells.Fixed(1),
                        state = gridState,
                        // Bottom padding clears the floating add action, so the last row is not
                        // half-covered by it once the list is long enough to scroll.
                        contentPadding = PaddingValues(
                            start = Spacing.md,
                            end = Spacing.md,
                            top = Spacing.md,
                            bottom = 88.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(ui.books, key = { it.id ?: 0 }) { book ->
                            if (ui.grid) {
                                BookGridItem(
                                    book = book,
                                    onClick = { book.id?.let(onOpenBook) },
                                    modifier = Modifier.animateItem(),
                                )
                            } else {
                                BookRowItem(
                                    book = book,
                                    onClick = { book.id?.let(onOpenBook) },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }

                        if (ui.loadingMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                // A load-more spinner stays inline and small: the rows above it are
                                // still the point of the screen.
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                    CircularProgressIndicator(Modifier.size(Spacing.xl), strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
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
            Column(Modifier.fillMaxWidth().padding(bottom = Spacing.xxl)) {
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
                            .padding(horizontal = Spacing.xl, vertical = Spacing.md),
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
                            .padding(horizontal = Spacing.xl, vertical = Spacing.md),
                    )
                }
            }
        }
    }
}

@Composable
private fun ListStatus(
    ui: BookListUiState,
    onRetry: () -> Unit,
    onCreateBook: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        // The first page: a skeleton in the shape of the rows about to arrive, so nothing jumps.
        ui.loading && ui.books.isEmpty() -> BookListSkeleton(modifier)

        ui.error != null && ui.books.isEmpty() -> ErrorState(
            message = errorMessage(ui.error) ?: stringResource(R.string.error_unknown),
            onRetry = onRetry,
            modifier = modifier,
        )

        ui.showEmpty -> {
            // Nothing is narrowing the list: the reader is looking at an empty shelf, so the hint
            // has to be about adding to it rather than about searching.
            val idle = ui.searchText.isBlank() && !ui.query.hasFilters
            EmptyState(
                icon = when {
                    !idle -> Icons.Default.Search
                    ui.scope == BookScope.Wishlist -> Icons.Default.Favorite
                    else -> Icons.AutoMirrored.Filled.List
                },
                title = stringResource(R.string.books_empty),
                hint = stringResource(if (idle) R.string.books_empty_hint_plain else R.string.books_empty_hint),
                actionLabel = if (idle && ui.scope == BookScope.All) stringResource(R.string.books_add_new) else null,
                onAction = if (idle && ui.scope == BookScope.All) onCreateBook else null,
                modifier = modifier,
            )
        }
    }
}

@Composable
internal fun BookRowItem(book: BookCard, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Clickable before the padding, so the whole row is the touch target and the ripple
            // reaches the edges; Spacing.xs keeps the cover on the same 16dp gutter as the
            // skeleton, which gets the rest from the grid's own content padding.
            .clickable(onClick = onClick)
            .padding(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(book.thumbImage, Modifier.size(56.dp))
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            BookTitles(book)
        }
    }
}

@Composable
internal fun BookGridItem(book: BookCard, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clickable(onClick = onClick).padding(Spacing.xs)) {
        BookCover(book.thumbImage, Modifier.fillMaxWidth().aspectRatio(1f))
        Spacer(Modifier.height(Spacing.sm))
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
                // Tabular figures for the ISBN: a column of digits reads as a column only when the
                // figures line up.
                style = MaterialTheme.typography.labelSmall + NumericTextStyle,
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

/**
 * A cover, whole.
 *
 * These files are square and already contain the cover at its own framing, so the container is
 * square too and the image is fitted inside it: cropping a square into a portrait box is exactly
 * how the top and bottom of a cover disappear. Fitting also means a file that is not square shows
 * complete, with the container's own fill around it rather than a slice of it.
 *
 * [modifier] sizes the container; callers pass a square.
 */
@Composable
internal fun BookCover(path: String?, modifier: Modifier = Modifier) {
    val url = MediaUrls.image(path)
    // The same corner and fill as the skeleton block that stands in for it, so the swap is
    // invisible and a letterboxed cover sits on the same surface the placeholder uses.
    val shape = MaterialTheme.shapes.extraSmall
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.matchParentSize(),
            )
        }
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
