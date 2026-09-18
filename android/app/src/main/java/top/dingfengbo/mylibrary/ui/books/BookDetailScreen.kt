package top.dingfengbo.mylibrary.ui.books

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
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
import top.dingfengbo.mylibrary.api.models.BookResponse
import top.dingfengbo.mylibrary.data.AppContainer
import top.dingfengbo.mylibrary.ui.common.errorMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    container: AppContainer,
    bookId: Int,
    onBack: () -> Unit,
    onEdit: (Int) -> Unit,
    onOpenBook: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookDetailViewModel = viewModel {
        BookDetailViewModel(container.bookRepository, container.libraryEvents, bookId)
    },
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var confirmingDelete by remember { mutableStateOf(false) }

    // The book is gone: leaving is the only sensible thing left to do.
    LaunchedEffect(ui.deleted) {
        if (ui.deleted) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = ui.book?.let { it.titleCn.ifBlank { it.title } }
                            ?: stringResource(R.string.book_detail_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) } },
                actions = {
                    if (ui.book != null) {
                        TextButton(onClick = { onEdit(bookId) }) { Text(stringResource(R.string.book_detail_edit)) }
                    }
                },
            )
        },
        modifier = modifier,
    ) { padding ->
        when {
            ui.loading && ui.book == null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            ui.book == null -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(errorMessage(ui.error) ?: stringResource(R.string.error_unknown))
                Spacer(Modifier.height(12.dp))
                Button(onClick = viewModel::load) { Text(stringResource(R.string.error_retry)) }
            }

            else -> BookDetailContent(
                book = ui.book!!,
                archived = ui.archived,
                working = ui.working,
                actionError = ui.actionError,
                similar = ui.similar,
                onArchive = viewModel::archive,
                onDelete = { confirmingDelete = true },
                onOpenBook = onOpenBook,
                contentPadding = padding,
            )
        }
    }

    if (confirmingDelete) {
        val title = ui.book?.let { it.titleCn.ifBlank { it.title } }.orEmpty()
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.book_detail_delete_confirm_title)) },
            text = { Text(stringResource(R.string.book_detail_delete_confirm_message, title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    viewModel.delete()
                }) { Text(stringResource(R.string.book_detail_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

@Composable
private fun BookDetailContent(
    book: BookResponse,
    archived: Boolean,
    working: Boolean,
    actionError: Throwable?,
    similar: List<top.dingfengbo.mylibrary.data.model.SimilarBookHit>,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onOpenBook: (Int) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth()) {
                BookCover(book.thumbImage, Modifier.width(96.dp).height(128.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = book.titleCn.ifBlank { book.title },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (book.title.isNotBlank() && book.title != book.titleCn) {
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val authors = book.authors.orEmpty()
                        .mapNotNull { it.name?.takeIf { name -> name.isNotBlank() } }
                        .joinToString(", ")
                    if (authors.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(authors, style = MaterialTheme.typography.bodyMedium)
                    }
                    val translator = book.translator
                    if (!translator.isNullOrBlank()) {
                        Text(
                            text = stringResource(R.string.book_detail_field_translator) + " " + translator,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (archived) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.book_detail_archived_done),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        item {
            DetailSection(
                titleRes = R.string.book_detail_section_publishing,
                entries = listOf(
                    R.string.book_detail_field_isbn to book.isbn,
                    R.string.book_detail_field_publisher to book.publisher?.name,
                    R.string.book_detail_field_publish_date to book.publishDate.asDate(),
                    R.string.book_detail_field_brand to book.brand?.name,
                    R.string.book_detail_field_series to book.bookSeries?.name,
                    R.string.book_detail_field_binding to book.bindingType,
                    R.string.book_detail_field_paper to book.paperType,
                    R.string.book_detail_field_pages to book.pages?.toString(),
                    R.string.book_detail_field_language to book.language,
                    R.string.book_detail_field_edition to book.edition,
                    R.string.book_detail_field_printing to book.printingInfo,
                    R.string.book_detail_field_printing_count to book.printedNumber?.toString(),
                    R.string.book_detail_field_price to book.price?.toPlainString(),
                ),
            )
        }

        item {
            DetailSection(
                titleRes = R.string.book_detail_section_purchase,
                entries = listOf(
                    R.string.book_detail_field_purchase_price to book.purchasePrice?.toPlainString(),
                    R.string.book_detail_field_purchase_date to book.purchaseDate.asDate(),
                    R.string.book_detail_field_purchase_store to book.purchaseStore,
                    R.string.book_detail_field_link to book.link,
                ),
            )
        }

        item {
            DetailSection(
                titleRes = R.string.book_detail_section_shelf,
                entries = listOf(
                    R.string.book_detail_field_category to book.category?.path,
                    R.string.book_detail_field_bookshelf to book.bookshelf?.name,
                    R.string.book_detail_field_read_state to book.readState,
                    R.string.book_detail_field_tags to book.tags.orEmpty().joinToString(", "),
                    R.string.book_detail_field_wish to
                        if (book.inWish == true) stringResource(R.string.common_yes) else null,
                    R.string.book_detail_field_archived to
                        if (book.archived == true) stringResource(R.string.common_yes) else null,
                ),
            )
        }

        item {
            DetailSection(
                titleRes = R.string.book_detail_section_content,
                entries = listOf(
                    R.string.book_detail_field_douban to book.doubanScore?.toPlainString(),
                    R.string.book_detail_field_summary to book.summary,
                    R.string.book_detail_field_introduction to book.introduction,
                    R.string.book_detail_field_catalog to book.catalog,
                ),
            )
        }

        if (similar.isNotEmpty()) {
            item { SectionHeader(R.string.book_detail_similar) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(similar, key = { it.id ?: 0 }) { hit ->
                        Column(
                            Modifier.width(84.dp).clickable(enabled = hit.id != null) {
                                hit.id?.let(onOpenBook)
                            }
                        ) {
                            BookCover(hit.thumbImage, Modifier.fillMaxWidth().height(112.dp))
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = hit.titleCn?.takeIf { it.isNotBlank() } ?: hit.title.orEmpty(),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            hit.sharedCount?.let { count ->
                                Text(
                                    text = stringResource(R.string.book_detail_shared_tags, count),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                if (actionError != null) {
                    Text(
                        text = errorMessage(actionError) ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onArchive,
                        enabled = !working && book.archived != true,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.book_detail_archive)) }
                    Button(
                        onClick = onDelete,
                        enabled = !working,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.book_detail_delete)) }
                }
            }
        }
    }
}

/** A section title with its divider, for sections whose body is not a list of detail rows. */
@Composable
private fun SectionHeader(titleRes: Int) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp)) {
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * One titled block of detail rows.
 *
 * Renders nothing at all when every value is blank: a header over an empty block is noise, and most
 * books have no purchase data at all.
 */
@Composable
private fun DetailSection(titleRes: Int, entries: List<Pair<Int, String?>>) {
    val visible = entries.filter { !it.second.isNullOrBlank() }
    if (visible.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp)) {
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        visible.forEach { (labelRes, value) -> DetailRow(labelRes, value) }
    }
}

@Composable
private fun DetailRow(labelRes: Int, value: String?) {
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

/** The backend sends naive timestamps (`2006-01-01T00:00:00`); only the date matters here. */
private fun String?.asDate(): String? = this?.takeIf { it.isNotBlank() }?.take(10)
