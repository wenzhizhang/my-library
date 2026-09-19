package top.dingfengbo.mylibrary.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.dingfengbo.mylibrary.R

/**
 * Tail of a paged list: the first-page spinner, the error, the empty notice and the next-page
 * spinner.
 *
 * The error offers a retry even when rows are already on screen. A failed reset load keeps the
 * previous query's rows, so without it the list would look like it had been refreshed.
 *
 * @param loading first page in flight and nothing to show yet
 * @param empty list loaded, nothing in it and no error
 */
@Composable
fun ListStatus(
    loading: Boolean,
    error: Throwable?,
    loadingMore: Boolean,
    empty: Boolean,
    emptyText: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        loading -> Row(modifier, horizontalArrangement = Arrangement.Center) {
            CircularProgressIndicator()
        }

        error != null -> Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(errorMessage(error) ?: "", color = MaterialTheme.colorScheme.error)
            Button(onClick = onRetry) { Text(stringResource(R.string.error_retry)) }
        }

        empty -> Text(
            text = emptyText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )

        loadingMore -> Row(modifier, horizontalArrangement = Arrangement.Center) {
            CircularProgressIndicator(Modifier.padding(2.dp), strokeWidth = 2.dp)
        }
    }
}
