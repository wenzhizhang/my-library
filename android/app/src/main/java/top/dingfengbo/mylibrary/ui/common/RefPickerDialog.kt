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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.dingfengbo.mylibrary.R
import top.dingfengbo.mylibrary.data.model.NamedRef
import top.dingfengbo.mylibrary.ui.books.RefChoice

/**
 * Search-and-pick dialog for a reference list (author, publisher, brand, series, category, shelf).
 *
 * A dialog rather than an anchored dropdown: the soft keyboard covers most of a phone screen, and a
 * full-height list is the only layout where "type two characters, tap the match" actually works.
 */
@Composable
fun RefPickerDialog(
    titleRes: Int,
    selected: List<RefChoice>,
    multi: Boolean,
    search: suspend (String) -> Result<List<NamedRef>>,
    createFields: List<Int> = emptyList(),
    onCreate: (suspend (List<String>) -> Result<NamedRef>)? = null,
    onConfirm: (List<RefChoice>) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<NamedRef>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var picked by remember { mutableStateOf(selected) }
    var creating by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        delay(250)
        loading = true
        search(query)
            .onSuccess { results = it; error = null }
            .onFailure { error = it }
        loading = false
    }

    if (creating && onCreate != null) {
        NewEntityDialog(
            titleRes = titleRes,
            fieldLabelRes = createFields,
            onSubmit = onCreate,
            onCreated = { ref ->
                val choice = RefChoice(ref.id, ref.label)
                creating = false
                if (multi) {
                    // The web front end appends a freshly created reference to the selection; the
                    // dialog stays open, so "新建" must never drop what was already picked.
                    if (picked.none { it.id == choice.id }) picked = picked + choice
                } else {
                    onConfirm(listOf(choice))
                }
            },
            onDismiss = { creating = false },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.ref_search_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (picked.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = picked.joinToString("、") { it.label },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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

                    else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                        if (onCreate != null && createFields.isNotEmpty() && query.isNotBlank()) {
                            item {
                                Text(
                                    text = stringResource(R.string.ref_create_new, query),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { creating = true }
                                        .padding(vertical = 10.dp),
                                )
                                HorizontalDivider()
                            }
                        }
                        if (picked.isNotEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.ref_clear),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            // Single-select has no confirm button, so clearing has to
                                            // be applied at once — exactly like tapping a row is.
                                            if (multi) picked = emptyList() else onConfirm(emptyList())
                                        }
                                        .padding(vertical = 10.dp),
                                )
                            }
                        }
                        items(results, key = { it.id }) { ref ->
                            val isPicked = picked.any { it.id == ref.id }
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (multi) {
                                            picked = if (isPicked) {
                                                picked.filterNot { it.id == ref.id }
                                            } else {
                                                picked + RefChoice(ref.id, ref.label)
                                            }
                                        } else {
                                            onConfirm(listOf(RefChoice(ref.id, ref.label)))
                                        }
                                    }
                                    .padding(vertical = 10.dp),
                            ) {
                                Text(
                                    text = ref.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isPicked) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                                ref.detail?.let { detail ->
                                    Text(
                                        text = detail,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (multi) {
                TextButton(onClick = { onConfirm(picked) }) { Text(stringResource(R.string.ref_done)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@Composable
private fun NewEntityDialog(
    titleRes: Int,
    fieldLabelRes: List<Int>,
    onSubmit: suspend (List<String>) -> Result<NamedRef>,
    onCreated: (NamedRef) -> Unit,
    onDismiss: () -> Unit,
) {
    var values by remember { mutableStateOf(List(fieldLabelRes.size) { "" }) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ref_create_title, stringResource(titleRes))) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                fieldLabelRes.forEachIndexed { index, labelRes ->
                    OutlinedTextField(
                        value = values[index],
                        onValueChange = { text ->
                            values = values.toMutableList().also { it[index] = text }
                        },
                        label = { Text(stringResource(labelRes)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
                }
                if (working) {
                    Spacer(Modifier.height(8.dp))
                    CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp)
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(text = errorMessage(it) ?: "", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = values.all { it.isNotBlank() } && !working,
                onClick = {
                    working = true
                    error = null
                    scope.launch {
                        onSubmit(values.map { it.trim() })
                            .onSuccess { onCreated(it) }
                            .onFailure { error = it; working = false }
                    }
                },
            ) { Text(stringResource(R.string.ref_create_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
