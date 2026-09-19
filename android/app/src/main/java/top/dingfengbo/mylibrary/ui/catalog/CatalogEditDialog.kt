package top.dingfengbo.mylibrary.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.dingfengbo.mylibrary.R
import top.dingfengbo.mylibrary.data.model.CatalogEntity
import top.dingfengbo.mylibrary.data.model.EntityAttribute
import top.dingfengbo.mylibrary.data.model.EntityEdit
import top.dingfengbo.mylibrary.ui.common.errorMessage

/**
 * Create/edit form for a catalog entity.
 *
 * The fields come from the caller: `entity.editable` drives both creating and editing, since that
 * list is exactly what the `*Creation` and `*Update` schemas accept. That is what keeps six
 * entities down to one dialog.
 *
 * [loadChoices] supplies the option lists for the attributes the backend validates against a fixed
 * set (nation, dynasty). An attribute with no options stays a free-text field, so a failed fetch —
 * or a value the list no longer contains — never blocks the save.
 */
@Composable
fun CatalogEditDialog(
    entity: CatalogEntity,
    attributes: List<EntityAttribute>,
    initial: EntityEdit,
    onSave: suspend (EntityEdit) -> Result<Unit>,
    onDismiss: () -> Unit,
    loadChoices: suspend (EntityAttribute) -> Result<List<String>> = { Result.success(emptyList()) },
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var choices by remember { mutableStateOf<Map<EntityAttribute, List<String>>>(emptyMap()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(attributes) {
        choices = attributes
            .mapNotNull { attribute ->
                loadChoices(attribute).getOrNull()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { attribute to it }
            }
            .toMap()
    }

    val nameMissing = EntityAttribute.Name in attributes && draft.name.isBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.catalog_edit_title, stringResource(entity.titleRes))) },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                attributes.forEach { attribute ->
                    when (attribute) {
                        EntityAttribute.Intro -> OutlinedTextField(
                            value = draft.intro,
                            onValueChange = { draft = draft.copy(intro = it) },
                            label = { Text(stringResource(attribute.labelRes())) },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        EntityAttribute.Parent -> Column {
                            // ponytail: numeric parent id; swap for a category picker if it annoys.
                            OutlinedTextField(
                                value = draft.parent,
                                onValueChange = { value ->
                                    draft = draft.copy(parent = value.filter { it.isDigit() })
                                },
                                label = { Text(stringResource(attribute.labelRes())) },
                                supportingText = { Text(stringResource(R.string.catalog_parent_hint)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        else -> {
                            val options = choices[attribute]
                            if (options == null) {
                                OutlinedTextField(
                                    value = draft.valueOf(attribute),
                                    onValueChange = { draft = draft.withValue(attribute, it) },
                                    label = { Text(stringResource(attribute.labelRes())) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                ChoiceField(
                                    value = draft.valueOf(attribute),
                                    options = options,
                                    labelRes = attribute.labelRes(),
                                    onValueChange = { draft = draft.withValue(attribute, it) },
                                )
                            }
                        }
                    }
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
                enabled = !nameMissing && !working,
                onClick = {
                    working = true
                    error = null
                    scope.launch {
                        onSave(draft)
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

/**
 * Read-only picker for an attribute whose value the backend checks against a fixed list.
 *
 * The menu holds [options], an empty entry for clearing, and [value] itself when the list no longer
 * contains it — an entry saved before a config change must stay visible and re-savable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceField(
    value: String,
    options: List<String>,
    labelRes: Int,
    onValueChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(labelRes)) },
            singleLine = true,
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.exposedDropdownSize(),
        ) {
            val entries = listOf("") + options + listOfNotNull(value.takeIf { it.isNotEmpty() && it !in options })
            entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(if (entry.isEmpty()) stringResource(R.string.catalog_choice_empty) else entry) },
                    onClick = {
                        onValueChange(entry)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun EntityEdit.valueOf(attribute: EntityAttribute): String = when (attribute) {
    EntityAttribute.Name -> name
    EntityAttribute.NameCn -> nameCn
    EntityAttribute.Nation -> nation
    EntityAttribute.Dynasty -> dynasty
    EntityAttribute.Intro -> intro
    EntityAttribute.Photo -> photo
    EntityAttribute.Logo -> logo
    EntityAttribute.Parent -> parent
    else -> ""
}

private fun EntityEdit.withValue(attribute: EntityAttribute, value: String): EntityEdit = when (attribute) {
    EntityAttribute.Name -> copy(name = value)
    EntityAttribute.NameCn -> copy(nameCn = value)
    EntityAttribute.Nation -> copy(nation = value)
    EntityAttribute.Dynasty -> copy(dynasty = value)
    EntityAttribute.Intro -> copy(intro = value)
    EntityAttribute.Photo -> copy(photo = value)
    EntityAttribute.Logo -> copy(logo = value)
    EntityAttribute.Parent -> copy(parent = value)
    else -> this
}
