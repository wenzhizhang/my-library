package top.dingfengbo.mylibrary.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.dingfengbo.mylibrary.R

/**
 * Failed detail load: there is no entity to draw, so offer the message and a retry.
 */
@Composable
fun DetailLoadError(error: Throwable?, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(errorMessage(error) ?: stringResource(R.string.error_unknown))
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.error_retry)) }
    }
}

/**
 * Foot of a collection or plan: the last action failure, then the destructive delete.
 *
 * A failed remove leaves no row behind to attach its message to, so it lives down here.
 */
@Composable
fun DetailActionFooter(actionError: Throwable?, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 16.dp)) {
        if (actionError != null) {
            Text(
                text = errorMessage(actionError) ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
        }
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.catalog_delete))
        }
    }
}

/**
 * Delete confirmation. Collections and plans differ in wording and in what they quote as the
 * title, so both come from the caller.
 */
@Composable
fun DeleteConfirmDialog(
    name: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.catalog_delete_confirm_title, name)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.catalog_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
