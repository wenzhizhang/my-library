package top.dingfengbo.mylibrary.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.dingfengbo.mylibrary.R

/** One field of [TextFieldsDialog]. */
data class DialogField(
    val labelRes: Int,
    val initial: String = "",
    val multiline: Boolean = false,
)

/**
 * Create/edit dialog for entities that are just a few text fields (collections, reading plans).
 *
 * Fields are declared by the caller, so the same dialog covers create and edit — and whatever
 * entity gets added next.
 */
@Composable
fun TextFieldsDialog(
    titleRes: Int,
    fields: List<DialogField>,
    onSave: suspend (List<String>) -> Result<Unit>,
    onDismiss: () -> Unit,
) {
    var values by remember(fields) { mutableStateOf(fields.map { it.initial }) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                fields.forEachIndexed { index, field ->
                    OutlinedTextField(
                        value = values.getOrElse(index) { "" },
                        onValueChange = { text ->
                            values = values.toMutableList().also { it[index] = text }
                        },
                        label = { Text(stringResource(field.labelRes)) },
                        singleLine = !field.multiline,
                        minLines = if (field.multiline) 3 else 1,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (working) {
                    Spacer(Modifier.height(4.dp))
                    CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp)
                }
                error?.let {
                    Text(text = errorMessage(it) ?: "", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = values.firstOrNull()?.isNotBlank() == true && !working,
                onClick = {
                    working = true
                    error = null
                    scope.launch {
                        onSave(values.map { it.trim() })
                            .onSuccess { onDismiss() }
                            .onFailure { error = it; working = false }
                    }
                },
            ) { Text(stringResource(R.string.form_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
