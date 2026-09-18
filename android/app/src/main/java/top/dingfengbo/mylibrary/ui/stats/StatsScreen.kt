package top.dingfengbo.mylibrary.ui.stats

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import top.dingfengbo.mylibrary.R
import top.dingfengbo.mylibrary.api.models.ApiStatsBooksGet200Response
import top.dingfengbo.mylibrary.api.models.NameCount
import top.dingfengbo.mylibrary.data.AppContainer
import top.dingfengbo.mylibrary.ui.common.errorMessage

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
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) } },
            )
        },
        modifier = modifier,
    ) { padding ->
        val stats = ui.stats
        when {
            ui.loading && stats == null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            stats == null -> Column(
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
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
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
                    )
                }
                item {
                    ChartSection(
                        titleRes = R.string.stats_purchase_months,
                        rows = stats.purchaseMonths.orEmpty().map {
                            ChartRow(it.label.orEmpty(), it.count ?: 0, it.price?.toPlainString())
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewSection(stats: ApiStatsBooksGet200Response) {
    val overview = stats.overview
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.stats_overview),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(6.dp))
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
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** One bar: a label, the bar length, and an optional second figure (a sum, for purchase charts). */
private data class ChartRow(val label: String, val count: Int, val extra: String?)

/**
 * A horizontal bar chart drawn with layout rather than a charting library: the payload is already
 * "label + count", and bar width is just a fraction of the row.
 */
@Composable
private fun BarSection(titleRes: Int, rows: List<NameCount>?) {
    ChartSection(
        titleRes = titleRes,
        rows = rows.orEmpty().map { ChartRow(it.name.orEmpty(), it.count ?: 0, null) },
    )
}

@Composable
private fun ChartSection(titleRes: Int, rows: List<ChartRow>) {
    if (rows.isEmpty()) return
    val max = rows.maxOf { it.count }.coerceAtLeast(1)

    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(6.dp))
        rows.forEach { row ->
            val count = row.count
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp),
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
                        Modifier
                            .fillMaxWidth(fraction = count.toFloat() / max)
                            .height(14.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = listOfNotNull(count.toString(), row.extra).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
