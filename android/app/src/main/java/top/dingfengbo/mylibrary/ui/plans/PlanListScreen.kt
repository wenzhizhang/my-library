package top.dingfengbo.mylibrary.ui.plans

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import top.dingfengbo.mylibrary.R
import top.dingfengbo.mylibrary.data.AppContainer
import top.dingfengbo.mylibrary.ui.common.DialogField
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
            TopAppBar(
                title = { Text(stringResource(R.string.plans_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { creating = true }) {
                Text(stringResource(R.string.catalog_add))
            }
        },
        modifier = modifier,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = ui.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text(stringResource(R.string.catalog_search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            )

            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(ui.items, key = { it.id ?: 0 }) { plan ->
                    Column(
                        Modifier.fillMaxWidth()
                            .clickable(enabled = plan.id != null) { plan.id?.let(onOpenPlan) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(plan.name.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                        val progress = plan.progress?.toFloat()?.div(100f)
                        if (progress != null) {
                            LinearProgressIndicator(
                                progress = { progress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            )
                        }
                        val subtitle = listOfNotNull(
                            plan.totalBooks?.let { stringResource(R.string.books_count, it) },
                            plan.startDate?.take(10),
                            plan.endDate?.take(10),
                        ).joinToString(" · ")
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                item {
                    when {
                        ui.loading && ui.items.isEmpty() -> Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) { CircularProgressIndicator() }

                        ui.error != null && ui.items.isEmpty() -> Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(errorMessage(ui.error) ?: "", color = MaterialTheme.colorScheme.error)
                            Button(onClick = viewModel::reload) { Text(stringResource(R.string.error_retry)) }
                        }

                        ui.showEmpty -> Text(
                            text = stringResource(R.string.catalog_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )

                        ui.loadingMore -> Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) { CircularProgressIndicator(Modifier.padding(2.dp), strokeWidth = 2.dp) }
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
                DialogField(R.string.plans_start_date),
                DialogField(R.string.plans_end_date),
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
