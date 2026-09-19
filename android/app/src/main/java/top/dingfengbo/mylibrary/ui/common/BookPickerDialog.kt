package top.dingfengbo.mylibrary.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

    LaunchedEffect(query, excluded) {
        delay(300)
        loading = true
        search(query)
            .onSuccess { results = it.filterNot { book -> book.id in excluded }; error = null }
            .onFailure { error = it }
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.picker_books_title)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.books_search_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                when {
                    loading -> Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) { CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp) }

                    error != null -> Text(
                        text = errorMessage(error) ?: "",
                        color = MaterialTheme.colorScheme.error,
                    )

                    results.isEmpty() -> Text(
                        text = stringResource(R.string.books_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                        items(results, key = { it.id ?: 0 }) { book ->
                            val id = book.id
                            val isPicked = id != null && id in picked
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable(enabled = id != null) {
                                        if (id != null) {
                                            picked = if (isPicked) picked - id else picked + id
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                BookCover(book.thumbImage, Modifier.width(32.dp).height(42.dp))
                                Spacer(Modifier.width(10.dp))
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
                                    Text("✓", color = MaterialTheme.colorScheme.primary)
                                }
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
