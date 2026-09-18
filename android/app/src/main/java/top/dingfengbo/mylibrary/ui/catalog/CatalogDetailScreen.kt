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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import top.dingfengbo.mylibrary.R
import top.dingfengbo.mylibrary.data.AppContainer
import top.dingfengbo.mylibrary.data.model.CatalogEntity
import top.dingfengbo.mylibrary.ui.books.BookRowItem
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

    LaunchedEffect(ui.deleted) {
        if (ui.deleted) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = ui.detail?.title ?: stringResource(entity.titleRes),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) } },
                actions = {
                    if (ui.detail != null) {
                        TextButton(onClick = { viewModel.onEditingChange(true) }) {
                            Text(stringResource(R.string.catalog_edit))
                        }
                    }
                },
            )
        },
        modifier = modifier,
    ) { padding ->
        val detail = ui.detail
        when {
            ui.loading && detail == null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            detail == null -> Column(
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
                item {
                    Row(Modifier.fillMaxWidth()) {
                        top.dingfengbo.mylibrary.ui.books.BookCover(
                            path = detail.photo,
                            modifier = Modifier.width(96.dp).height(96.dp),
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = detail.title,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                items(attributeOrder.filter { detail.attributes[it] != null }) { attribute ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(
                            text = stringResource(attribute.labelRes()),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(88.dp),
                        )
                        Text(
                            text = detail.attributes[attribute].orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                item {
                    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.catalog_related_books),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                if (ui.books.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.catalog_no_books),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(ui.books, key = { it.id ?: 0 }) { book ->
                        BookRowItem(book = book, onClick = { book.id?.let(onOpenBook) })
                    }
                }

                item {
                    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        if (ui.actionError != null) {
                            Text(
                                text = errorMessage(ui.actionError) ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        OutlinedButton(
                            onClick = { confirmingDelete = true },
                            enabled = !ui.saving,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.catalog_delete)) }
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
