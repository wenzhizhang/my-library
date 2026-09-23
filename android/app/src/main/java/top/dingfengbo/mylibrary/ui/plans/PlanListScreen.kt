package top.dingfengbo.mylibrary.ui.plans

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import top.dingfengbo.mylibrary.R
import top.dingfengbo.mylibrary.ui.common.AppTopBar
import top.dingfengbo.mylibrary.api.models.ReadingPlanSummary
import top.dingfengbo.mylibrary.data.AppContainer
import top.dingfengbo.mylibrary.theme.NumericTextStyle
import top.dingfengbo.mylibrary.theme.Spacing
import top.dingfengbo.mylibrary.theme.StatusColors
import top.dingfengbo.mylibrary.ui.common.BookListSkeleton
import top.dingfengbo.mylibrary.ui.common.DialogField
import top.dingfengbo.mylibrary.ui.common.EmptyState
import top.dingfengbo.mylibrary.ui.common.ErrorState
import top.dingfengbo.mylibrary.ui.common.SearchField
import top.dingfengbo.mylibrary.ui.common.TextFieldsDialog
import top.dingfengbo.mylibrary.ui.common.errorMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanListScreen(
    container: AppContainer,
    onOpenPlan: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlanListViewModel = viewModel {
        PlanListViewModel(container.readingPlanRepository, container.libraryEvents)
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
            AppTopBar(
                title = { Text(stringResource(R.string.plans_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { creating = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Text(stringResource(R.string.plans_create))
            }
        },
        modifier = modifier,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchField(
                query = ui.query,
                onQueryChange = viewModel::onQueryChange,
                hintRes = R.string.catalog_search_hint,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
            )

            // The list is the only thing the gesture has to reach, so the refresh box wraps it and
            // nothing else — the empty state is a list item, so it stays pullable too.
            PullToRefreshBox(
                isRefreshing = ui.refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(ui.items, key = { it.id ?: 0 }) { plan ->
                        PlanRow(
                            plan = plan,
                            onClick = { plan.id?.let(onOpenPlan) },
                            modifier = Modifier.animateItem(),
                        )
                    }

                    item {
                        when {
                            // The failure comes first. A reset load that failed keeps the previous
                            // query's rows, and without a retry here the list would look refreshed.
                            ui.error != null -> ErrorState(
                                message = errorMessage(ui.error) ?: stringResource(R.string.error_unknown),
                                onRetry = viewModel::reload,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            ui.loading && ui.items.isEmpty() -> BookListSkeleton()

                            ui.showEmpty -> EmptyState(
                                icon = Icons.Default.DateRange,
                                title = stringResource(R.string.catalog_empty),
                                hint = stringResource(R.string.plans_empty_hint),
                                actionLabel = stringResource(R.string.plans_create),
                                onAction = { creating = true },
                            )

                            // A next page may still be inline and small: it is not the first load.
                            ui.loadingMore -> Box(
                                Modifier.fillMaxWidth().padding(Spacing.md),
                                contentAlignment = Alignment.Center,
                            ) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        TextFieldsDialog(
            titleRes = R.string.plans_create,
            fields = listOf(
                DialogField(R.string.attr_name),
                DialogField(R.string.attr_intro, multiline = true),
                DialogField(R.string.plans_start_date, date = true),
                DialogField(R.string.plans_end_date, date = true),
            ),
            onSave = { values ->
                container.readingPlanRepository.create(
                    name = values[0],
                    intro = values.getOrElse(1) { "" },
                    startDate = values.getOrElse(2) { "" },
                    endDate = values.getOrElse(3) { "" },
                ).map { }.onSuccess { container.libraryEvents.bump() }
            },
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun PlanRow(
    plan: ReadingPlanSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val percentText = plan.progress?.let { "${it.toPlainString()}%" }
    val fraction = plan.progress?.toFloat()?.div(100f)?.coerceIn(0f, 1f)
    val dates = listOfNotNull(plan.startDate?.take(10), plan.endDate?.take(10)).joinToString(" – ")

    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = plan.id != null, onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Collections and plans are one tap apart, so a row says which kind it is before the name.
        Icon(
            imageVector = Icons.Default.DateRange,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = plan.name.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (fraction != null && percentText != null) {
                Spacer(Modifier.height(Spacing.xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        color = StatusColors.read,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = percentText,
                        style = MaterialTheme.typography.labelSmall.merge(NumericTextStyle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = listOfNotNull(
                    plan.totalBooks?.let { pluralStringResource(R.plurals.books_count, it, it) },
                    dates.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                // A count and dates share this line, so the whole line takes tabular figures and
                // the digits still line up from row to row.
                style = MaterialTheme.typography.labelSmall.merge(NumericTextStyle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
