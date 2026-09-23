package top.dingfengbo.mylibrary.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import top.dingfengbo.mylibrary.R
import top.dingfengbo.mylibrary.ui.common.AppTopBar
import top.dingfengbo.mylibrary.api.models.ApiStatsBooksGet200Response
import top.dingfengbo.mylibrary.api.models.NameCount
import top.dingfengbo.mylibrary.data.AppContainer
import top.dingfengbo.mylibrary.theme.NumericTextStyle
import top.dingfengbo.mylibrary.theme.Spacing
import top.dingfengbo.mylibrary.ui.common.EmptyState
import top.dingfengbo.mylibrary.ui.common.ErrorState
import top.dingfengbo.mylibrary.ui.common.SkeletonBlock
import top.dingfengbo.mylibrary.ui.common.errorMessage

/**
 * The library as numbers.
 *
 * Every figure here is one of a column of figures, so all of them use the tabular style: proportional
 * digits make a column of prices wobble, and a chart whose bar lengths are the point cannot have its
 * scale moving when a "1" is replaced by an "8". Each chart also says what it is showing, because a
 * bar chart with no unit is a picture, not information.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = viewModel { StatsViewModel(container.statsRepository) },
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text(stringResource(R.string.stats_title)) },
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
        val stats = ui.stats
        when {
            ui.loading && stats == null -> StatsSkeleton(Modifier.fillMaxSize().padding(padding))

            stats == null ->
                ErrorState(
                    message = errorMessage(ui.error) ?: stringResource(R.string.error_unknown),
                    onRetry = viewModel::load,
                    modifier = Modifier.fillMaxSize().padding(padding),
                )

            else ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    if (stats.overview?.totalBooks == 0) {
                        // No books means every chart below would be an empty frame. Say so once, and
                        // say where books come from.
                        item {
                            EmptyState(
                                icon = Icons.Default.Add,
                                title = stringResource(R.string.stats_empty_title),
                                hint = stringResource(R.string.stats_empty_hint),
                            )
                        }
                    } else {
                        item { OverviewSection(stats) }
                        item { BarSection(R.string.stats_by_read_state, stats.byReadState) }
                        item { BarSection(R.string.stats_by_category, stats.byCategory) }
                        item { BarSection(R.string.stats_by_binding, stats.byBinding) }
                        item { BarSection(R.string.stats_by_language, stats.byLanguage) }
                        item { BarSection(R.string.stats_by_compose, stats.byCompose) }
                        item { BarSection(R.string.stats_by_score, stats.byScore) }
                        item { BarSection(R.string.stats_top_authors, stats.topAuthors) }
                        item { BarSection(R.string.stats_top_publishers, stats.topPublishers) }
                        item {
                            ChartSection(
                                titleRes = R.string.stats_timeline_years,
                                rows = stats.timelineYears.orEmpty().map {
                                    ChartRow(it.label.orEmpty(), it.count ?: 0, null)
                                },
                            )
                        }
                        item {
                            ChartSection(
                                titleRes = R.string.stats_timeline_months,
                                rows = stats.timelineMonths.orEmpty().map {
                                    ChartRow(it.label.orEmpty(), it.count ?: 0, null)
                                },
                            )
                        }
                        item {
                            ChartSection(
                                titleRes = R.string.stats_purchase_years,
                                rows = stats.purchaseYears.orEmpty().map {
                                    ChartRow(it.label.orEmpty(), it.count ?: 0, it.price?.toPlainString())
                                },
                                amount = true,
                            )
                        }
                        item {
                            ChartSection(
                                titleRes = R.string.stats_purchase_months,
                                rows = stats.purchaseMonths.orEmpty().map {
                                    ChartRow(it.label.orEmpty(), it.count ?: 0, it.price?.toPlainString())
                                },
                                amount = true,
                            )
                        }
                    }
                }
        }
    }
}

/** The totals, on one label/value column so the figures line up down the screen. */
@Composable
private fun OverviewSection(stats: ApiStatsBooksGet200Response) {
    val overview = stats.overview
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.stats_overview),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(Spacing.sm))
        StatRow(R.string.stats_total_books, overview?.totalBooks?.toString())
        StatRow(R.string.catalog_authors, overview?.totalAuthors?.toString())
        StatRow(R.string.catalog_publishers, overview?.totalPublishers?.toString())
        StatRow(R.string.catalog_categories, overview?.totalCategories?.toString())
        StatRow(R.string.stats_avg_price, overview?.avgPrice?.toPlainString())
        StatRow(R.string.stats_avg_purchase_price, overview?.avgPurchasePrice?.toPlainString())
        StatRow(R.string.stats_total_spent, overview?.totalSpent?.toPlainString())
    }
}

@Composable
private fun StatRow(labelRes: Int, value: String?) {
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.merge(NumericTextStyle),
            textAlign = TextAlign.End,
        )
    }
}

/** One bar: a label, the length, and an optional second figure (a sum, on the purchase charts). */
private data class ChartRow(val label: String, val count: Int, val extra: String?)

/**
 * A horizontal bar chart drawn with layout rather than a charting library: the payload is already
 * "label + count", and bar length is just a fraction of the row.
 *
 * [amount] switches the second column from a count to a count plus a sum of money, and with it the
 * caption: the reader has to be told which of the two numbers they are looking at.
 */
@Composable
private fun ChartSection(titleRes: Int, rows: List<ChartRow>, amount: Boolean = false) {
    if (rows.isEmpty()) return
    val max = rows.maxOf { it.count }.coerceAtLeast(1)

    ChartFrame(
        titleRes = titleRes,
        captionRes =
            if (amount) R.string.stats_chart_caption_amount else R.string.stats_chart_caption,
    ) {
        rows.forEach { row ->
            val count = row.count
            Row(
                Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.label,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(96.dp),
                )
                Box(Modifier.weight(1f)) {
                    Box(
                        Modifier.fillMaxWidth(fraction = count.toFloat() / max)
                            .height(14.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    )
                }
                Spacer(Modifier.width(Spacing.md))
                // Fixed width and end-aligned: a column of figures that grows a digit must not shove
                // the bars sideways.
                Text(
                    text = listOfNotNull(count.toString(), row.extra).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium.merge(NumericTextStyle),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.width(88.dp),
                )
            }
        }
    }
}

@Composable
private fun BarSection(titleRes: Int, rows: List<NameCount>?) {
    ChartSection(
        titleRes = titleRes,
        rows = rows.orEmpty().map { ChartRow(it.name.orEmpty(), it.count ?: 0, null) },
    )
}

/** A chart's heading: what it counts, in what unit, and the line that says how to read it. */
@Composable
private fun ChartFrame(titleRes: Int, captionRes: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider()
        Spacer(Modifier.height(Spacing.lg))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.stats_unit, stringResource(R.string.stats_unit_books)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = stringResource(captionRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.md))
        content()
    }
}

/** Three blocks the shape of the real ones: an overview, then two charts. */
@Composable
private fun StatsSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier.padding(horizontal = Spacing.lg, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxl),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SkeletonBlock(Modifier.width(96.dp).height(14.dp))
            repeat(5) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SkeletonBlock(Modifier.weight(1f).height(12.dp))
                    Spacer(Modifier.width(Spacing.md))
                    SkeletonBlock(Modifier.width(64.dp).height(12.dp))
                }
            }
        }
        repeat(2) { chart ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SkeletonBlock(Modifier.width(120.dp).height(14.dp))
                SkeletonBlock(Modifier.width(180.dp).height(11.dp))
                repeat(4) { bar ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SkeletonBlock(Modifier.width(72.dp).height(12.dp))
                        Spacer(Modifier.width(Spacing.md))
                        SkeletonBlock(
                            Modifier.fillMaxWidth(0.25f + bar * 0.18f + chart * 0.05f).height(14.dp)
                        )
                    }
                }
            }
        }
    }
}
