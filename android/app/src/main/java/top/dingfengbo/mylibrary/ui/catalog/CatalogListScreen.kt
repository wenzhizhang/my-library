package top.dingfengbo.mylibrary.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import top.dingfengbo.mylibrary.R
import top.dingfengbo.mylibrary.data.AppContainer
import top.dingfengbo.mylibrary.data.model.CatalogEntity
import top.dingfengbo.mylibrary.data.model.EntityEdit
import top.dingfengbo.mylibrary.data.model.NamedRef
import top.dingfengbo.mylibrary.theme.Spacing
import top.dingfengbo.mylibrary.ui.common.BookListSkeleton
import top.dingfengbo.mylibrary.ui.common.EmptyState
import top.dingfengbo.mylibrary.ui.common.ErrorState
import top.dingfengbo.mylibrary.ui.common.errorMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogListScreen(
    container: AppContainer,
    entity: CatalogEntity,
    onOpenEntity: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CatalogListViewModel = viewModel {
        CatalogListViewModel(container.catalogRepository, container.libraryEvents, entity)
    },
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var creating by remember { mutableStateOf(false) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { viewModel.onScrolledTo(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(entity.titleRes)) },
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
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { creating = true }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Text(stringResource(R.string.catalog_add))
            }
        },
        modifier = modifier,
    ) { padding ->
        // The pull indicator is the refresh state of a list that already has rows: a first page in
        // flight has nothing to pull on and shows the skeleton instead, so the two never overlap.
        PullToRefreshBox(
            isRefreshing = ui.loading && ui.rows.isNotEmpty(),
            onRefresh = viewModel::reload,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Column(Modifier.fillMaxSize()) {
                OutlinedTextField(
                    value = ui.query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = { Text(stringResource(R.string.catalog_search_hint)) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (ui.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = stringResource(R.string.common_clear),
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = Spacing.xxxl),
                ) {
                    items(ui.rows, key = { it.id }) { row ->
                        CatalogRow(
                            entity = entity,
                            row = row,
                            onClick = { onOpenEntity(row.id) },
                            modifier = Modifier.animateItem(),
                        )
                    }

                    item {
                        when {
                            ui.loading && ui.rows.isEmpty() ->
                                BookListSkeleton(modifier = Modifier.padding(vertical = Spacing.lg))

                            ui.error != null -> ErrorState(
                                message = errorMessage(ui.error) ?: stringResource(R.string.error_unknown),
                                onRetry = viewModel::reload,
                            )

                            ui.showEmpty -> if (ui.query.isNotBlank()) {
                                // A filtered list that is empty is a different dead end from a catalog
                                // with nothing in it: the way out is the query, not the create button.
                                EmptyState(
                                    icon = Icons.Default.Search,
                                    title = stringResource(R.string.catalog_no_match),
                                    actionLabel = stringResource(R.string.common_clear),
                                    onAction = { viewModel.onQueryChange("") },
                                )
                            } else {
                                EmptyState(
                                    icon = entity.markerIcon,
                                    title = stringResource(R.string.catalog_empty),
                                    actionLabel = stringResource(R.string.catalog_add),
                                    onAction = { creating = true },
                                )
                            }

                            ui.loadingMore -> Row(
                                Modifier.fillMaxWidth().padding(Spacing.md),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        CatalogEditDialog(
            entity = entity,
            // Same field set as editing: `*Creation` and `*Update` accept the same attributes.
            attributes = entity.editable,
            initial = EntityEdit(),
            loadChoices = container.catalogRepository::attributeChoices,
            onSave = { edit ->
                container.catalogRepository.create(entity, edit)
                    .map { }
                    .onSuccess { container.libraryEvents.bump() }
            },
            onDismiss = { creating = false },
        )
    }
}

/**
 * One entry of a catalog.
 *
 * The marker carries the entity kind: the same row shape serves all six catalogs, so the glyph is
 * what tells an author from a bookshelf. The chevron is the affordance the row was missing — it
 * opens the entity's detail screen.
 */
@Composable
private fun CatalogRow(
    entity: CatalogEntity,
    row: NamedRef,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = entity.markerIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = row.label,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            row.detail?.let { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
