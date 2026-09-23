package top.dingfengbo.mylibrary.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.Role
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
import kotlinx.coroutines.delay
import top.dingfengbo.mylibrary.R
import top.dingfengbo.mylibrary.api.models.BookCard
import top.dingfengbo.mylibrary.data.BookRepository
import top.dingfengbo.mylibrary.theme.Spacing
import top.dingfengbo.mylibrary.ui.books.BookCover

/**
 * Picks several books at once — used when adding books to a collection or a reading plan, which the
 * backend accepts in one batch.
 */
@Composable
fun BookPickerDialog(
    search: suspend (String) -> Result<List<BookCard>>,
    onConfirm: (List<Int>) -> Unit,
    onDismiss: () -> Unit,
    /**
     * Books already in the collection or plan. The web front end hides them as well: offering one
     * again only produces an add that changes nothing.
     */
    excluded: Set<Int> = emptySet(),
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<BookCard>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var picked by remember { mutableStateOf<Set<Int>>(emptySet()) }

    // The caller searches a single page, so a full page means the list on screen is only the
    // beginning of the matches — saying "no results" there would be a lie.
    var truncated by remember { mutableStateOf(false) }

    // Bumping this re-runs the search without touching the query: that is what the retry needs.
    var attempt by remember { mutableStateOf(0) }

    LaunchedEffect(query, excluded, attempt) {
        delay(300)
        loading = true
        search(query)
            .onSuccess { page ->
                truncated = page.size >= BookRepository.PAGE_SIZE
                results = page.filterNot { book -> book.id in excluded }
                error = null
            }
            .onFailure { error = it }
        loading = false
    }

    // One region at a time, and each one appears where the last was, so the dialog does not jump.
    val enter = fadeIn() + expandVertically()
    val exit = fadeOut() + shrinkVertically()
    val showLoading = loading && error == null && results.isEmpty()
    val showError = !loading && error != null
    val showEmpty = !loading && error == null && results.isEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.picker_books_title)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                SearchField(
                    query = query,
                    onQueryChange = { query = it },
                    hintRes = R.string.books_search_hint,
                )
                Spacer(Modifier.height(Spacing.sm))
                AnimatedVisibility(showLoading, enter = enter, exit = exit) {
                    Box(
                        Modifier.fillMaxWidth().padding(Spacing.lg),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
                }
                AnimatedVisibility(showError, enter = enter, exit = exit) {
                    ErrorState(
                        message = errorMessage(error) ?: stringResource(R.string.error_unknown),
                        onRetry = { attempt++ },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                AnimatedVisibility(showEmpty, enter = enter, exit = exit) {
                    // Muted body text: this is a note about the results, not a screen's empty state.
                    Text(
                        text = stringResource(
                            if (truncated) R.string.picker_more_matches else R.string.books_empty
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
                    )
                }
                results.takeIf { it.isNotEmpty() }?.let { books ->
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                        items(books, key = { it.id ?: 0 }) { book ->
                            val id = book.id
                            val isPicked = id != null && id in picked
                            Row(
                                Modifier.fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    // A checkbox role, not a click: the tick carries no text of its
                                    // own, so without this a screen reader hears the book's title and
                                    // nothing about whether it is picked.
                                    .toggleable(
                                        value = isPicked,
                                        enabled = id != null,
                                        role = Role.Checkbox,
                                    ) { wanted ->
                                        if (id != null) {
                                            picked = if (wanted) picked + id else picked - id
                                        }
                                    }
                                    .padding(vertical = Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Square, like every other caller: the covers are square files.
                                BookCover(book.thumbImage, Modifier.size(48.dp))
                                Spacer(Modifier.width(Spacing.md))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = book.titleCn?.takeIf { it.isNotBlank() } ?: book.title.orEmpty(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (isPicked) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = book.authors.orEmpty().joinToString(", "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (isPicked) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                        if (truncated) {
                            item {
                                Text(
                                    text = stringResource(R.string.picker_more_matches),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = Spacing.sm),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = picked.isNotEmpty(),
                onClick = { onConfirm(picked.toList()) },
            ) { Text(stringResource(R.string.picker_add_selected, picked.size)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
