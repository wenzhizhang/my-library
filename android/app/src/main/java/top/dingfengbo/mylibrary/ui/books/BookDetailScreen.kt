package top.dingfengbo.mylibrary.ui.books

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import top.dingfengbo.mylibrary.R
import top.dingfengbo.mylibrary.api.models.BookResponse
import top.dingfengbo.mylibrary.data.AppContainer
import top.dingfengbo.mylibrary.theme.IdentifierTextStyle
import top.dingfengbo.mylibrary.theme.Spacing
import top.dingfengbo.mylibrary.theme.StatusColors
import top.dingfengbo.mylibrary.ui.common.DetailLoadError
import top.dingfengbo.mylibrary.ui.common.DetailSkeleton
import top.dingfengbo.mylibrary.ui.common.errorMessage
import top.dingfengbo.mylibrary.ui.common.rememberHaptics

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
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val haptics = rememberHaptics()
    val isbnCopied = stringResource(R.string.book_detail_isbn_copied)

    // The book is gone: leaving is the only sensible thing left to do.
    LaunchedEffect(ui.deleted) {
        if (ui.deleted) onBack()
    }

    // One copy path for the ISBN: the clipboard, a tick you can feel, and a line that says the copy
    // happened — a silent clipboard write on a long press is indistinguishable from nothing.
    val copyIsbn: (String) -> Unit = { isbn ->
        clipboard.setText(AnnotatedString(isbn))
        haptics.confirm()
        scope.launch { snackbarHostState.showSnackbar(isbnCopied) }
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
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    if (ui.book != null) {
                        IconButton(onClick = { onEdit(bookId) }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.book_detail_edit),
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { padding ->
        when {
            ui.loading && ui.book == null -> DetailSkeleton(Modifier.fillMaxSize().padding(padding))

            ui.book == null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                // The shared detail-load failure, so a book that will not load looks like a
                // collection that will not load.
                DetailLoadError(error = ui.error, onRetry = viewModel::load)
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
                onCopyIsbn = copyIsbn,
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
    onCopyIsbn: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        item {
            Row(Modifier.fillMaxWidth()) {
                BookCover(book.thumbImage, Modifier.size(120.dp))
                Spacer(Modifier.width(Spacing.lg))
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
                        Spacer(Modifier.height(Spacing.sm))
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
                }
            }
        }

        item { StatusChips(book = book, archived = archived) }

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
                onCopyIsbn = onCopyIsbn,
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
                    R.string.book_detail_field_tags to book.tags.orEmpty().joinToString(", "),
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
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    items(similar, key = { it.id ?: 0 }) { hit ->
                        Column(
                            Modifier.width(84.dp).clickable(enabled = hit.id != null) {
                                hit.id?.let(onOpenBook)
                            }
                        ) {
                            BookCover(hit.thumbImage, Modifier.fillMaxWidth().aspectRatio(1f))
                            Spacer(Modifier.height(Spacing.xs))
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
            Column(Modifier.fillMaxWidth().padding(top = Spacing.lg)) {
                if (actionError != null) {
                    Text(
                        text = errorMessage(actionError) ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    OutlinedButton(
                        onClick = onArchive,
                        // `archived` is this screen's own flag: the loaded book only stops saying
                        // `archived = false` after a reload, which used to leave the button armed
                        // for a second archive call. A success leaves the button disabled.
                        enabled = !working && !archived && book.archived != true,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.book_detail_archive)) }
                    Button(
                        onClick = onDelete,
                        enabled = !working,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text(stringResource(R.string.book_detail_delete))
                    }
                }
            }
        }
    }
}

/**
 * The book's state as tinted chips: reading progress, wishlist and archive are what the reader
 * scans for, and as plain field rows they read like data instead of status.
 */
@Composable
private fun StatusChips(book: BookResponse, archived: Boolean) {
    val readState = book.readState?.takeIf { it.isNotBlank() }
    val isArchived = archived || book.archived == true
    if (readState == null && book.inWish != true && !isArchived) return

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (readState != null) {
            StatusChip(
                // An unrecognised state shows as the backend sent it, not as a label saying nothing.
                label = ReadState.labelResFor(readState)?.let { stringResource(it) } ?: readState,
                tint = readStateTint(readState),
            )
        }
        if (book.inWish == true) {
            StatusChip(stringResource(R.string.book_detail_field_wish), StatusColors.wishlist)
        }
        if (isArchived) {
            StatusChip(stringResource(R.string.book_detail_field_archived), StatusColors.archived)
        }
    }
}

@Composable
private fun StatusChip(label: String, tint: Color) {
    Surface(
        color = tint.copy(alpha = 0.16f),
        contentColor = tint,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        )
    }
}

/** The reading-state tints from the palette; anything the backend adds reads as "no state yet". */
private fun readStateTint(state: String): Color =
    when (state) {
        ReadState.Read.value -> StatusColors.read
        ReadState.Reading.value -> StatusColors.reading
        ReadState.Unread.value -> StatusColors.unread
        ReadState.Abandoned.value -> StatusColors.archived
        else -> StatusColors.unread
    }

/** A section title with its divider, for sections whose body is not a list of detail rows. */
@Composable
private fun SectionHeader(titleRes: Int) {
    Column(Modifier.fillMaxWidth().padding(top = Spacing.lg, bottom = Spacing.xs)) {
        HorizontalDivider()
        Spacer(Modifier.height(Spacing.sm))
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
 *
 * @param onCopyIsbn the long-press action for the ISBN row — the one row here that does something.
 */
@Composable
private fun DetailSection(
    titleRes: Int,
    entries: List<Pair<Int, String?>>,
    onCopyIsbn: ((String) -> Unit)? = null,
) {
    val visible = entries.filter { !it.second.isNullOrBlank() }
    if (visible.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = Spacing.lg, bottom = Spacing.xs)) {
        HorizontalDivider()
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(Spacing.xs))
        visible.forEach { (labelRes, value) ->
            DetailRow(
                labelRes = labelRes,
                value = value,
                onCopy = if (labelRes == R.string.book_detail_field_isbn) onCopyIsbn else null,
            )
        }
    }
}

/** [onCopy] is non-null only for the ISBN: every other row is data you can only read. */
@Composable
private fun DetailRow(labelRes: Int, value: String?, onCopy: ((String) -> Unit)? = null) {
    if (value.isNullOrBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onCopy == null) Modifier
                else Modifier.combinedClickable(onClick = { onCopy(value) }, onLongClick = { onCopy(value) })
            )
            .padding(vertical = Spacing.xs),
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp),
        )
        Text(
            text = value,
            // The ISBN is an identifier: monospace, like a UUID or the server URL.
            style = if (labelRes == R.string.book_detail_field_isbn) {
                MaterialTheme.typography.bodyMedium + IdentifierTextStyle
            } else {
                MaterialTheme.typography.bodyMedium
            },
            modifier = Modifier.weight(1f),
        )
    }
}

/** The backend sends naive timestamps (`2006-01-01T00:00:00`); only the date matters here. */
private fun String?.asDate(): String? = this?.takeIf { it.isNotBlank() }?.take(10)
