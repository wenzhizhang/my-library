package top.dingfengbo.mylibrary.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import top.dingfengbo.mylibrary.R
import top.dingfengbo.mylibrary.theme.Spacing

/**
 * Failed detail load: there is no entity to draw, so offer the message and a retry.
 *
 * The same composed error the lists use — a detail screen failing should not look like a different
 * app from a list failing.
 */
@Composable
fun DetailLoadError(error: Throwable?, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    ErrorState(
        message = errorMessage(error) ?: stringResource(R.string.error_unknown),
        onRetry = onRetry,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Foot of a collection or plan: the last action failure, then the destructive delete.
 *
 * A failed remove leaves no row behind to attach its message to, so it lives down here.
 */
@Composable
fun DetailActionFooter(actionError: Throwable?, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = Spacing.lg)) {
        if (actionError != null) {
            Text(
                text = errorMessage(actionError) ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(Spacing.sm))
        }
        HorizontalDivider()
        Spacer(Modifier.height(Spacing.sm))
        OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Delete, contentDescription = null)
            Spacer(Modifier.width(Spacing.sm))
            Text(stringResource(R.string.catalog_delete))
        }
    }
}

/**
 * Delete confirmation. The object's name belongs in the body, next to the consequence — a title
 * alone is read past. [message] is built by the caller because the wording (and the placeholder
 * that carries the name) is per kind.
 */
@Composable
fun DeleteConfirmDialog(
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.catalog_delete)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.catalog_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
