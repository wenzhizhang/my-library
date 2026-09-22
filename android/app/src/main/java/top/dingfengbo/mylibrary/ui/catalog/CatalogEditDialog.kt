package top.dingfengbo.mylibrary.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.dingfengbo.mylibrary.R
import top.dingfengbo.mylibrary.data.model.CatalogEntity
import top.dingfengbo.mylibrary.data.model.EntityAttribute
import top.dingfengbo.mylibrary.data.model.EntityEdit
import top.dingfengbo.mylibrary.theme.NumericTextStyle
import top.dingfengbo.mylibrary.theme.Spacing
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
    // Validation is reported once the reader has actually tried to save: a form that complains
    // about an untouched field reads as an accusation rather than help.
    var nameAttempted by remember { mutableStateOf(false) }
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
    // Two named groups, then a catch-all: an attribute these lists have never heard of still gets a
    // field, so a grouping table can never silently drop one from the form.
    val sections = remember(attributes) {
        val basic = listOf(EntityAttribute.Name, EntityAttribute.NameCn)
        val classification =
            listOf(EntityAttribute.Nation, EntityAttribute.Dynasty, EntityAttribute.Parent)
        listOf(
            R.string.catalog_form_section_basic to attributes.filter { it in basic },
            R.string.catalog_form_section_classification to
                attributes.filter { it in classification },
            R.string.catalog_form_section_details to
                attributes.filter { it !in basic && it !in classification },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.catalog_edit_title, stringResource(entity.titleRes))) },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                sections.filter { it.second.isNotEmpty() }
                    .forEachIndexed { index, (titleRes, fields) ->
                        SectionHeading(titleRes = titleRes, first = index == 0)

                        fields.forEach { attribute ->
                            AttributeField(
                                attribute = attribute,
                                draft = draft,
                                choices = choices,
                                nameError = nameAttempted && nameMissing,
                                onDraftChange = { draft = it },
                            )
                        }
                    }

                if (working) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                error?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            text = errorMessage(it) ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !working,
                onClick = {
                    // Save stays live and answers with the reason instead of going grey with no
                    // explanation, which is the oldest dead end a form has.
                    if (nameMissing) {
                        nameAttempted = true
                    } else {
                        nameAttempted = false
                        working = true
                        error = null
                        scope.launch {
                            onSave(draft)
                                .onSuccess { onDismiss() }
                                .onFailure { error = it; working = false }
                        }
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
 * A group heading in the form: divider, then the accent title, matching the book form's sections at
 * dialog scale. The first group skips the divider, which would otherwise sit against the title.
 */
@Composable
private fun SectionHeading(titleRes: Int, first: Boolean) {
    Column(Modifier.fillMaxWidth().padding(top = if (first) Spacing.xs else Spacing.md)) {
        if (!first) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(Spacing.md))
        }
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * One field of the form, in the shape its attribute calls for: a paragraph, a numeric entry, a
 * picker when the backend has a fixed list, free text otherwise.
 */
@Composable
private fun AttributeField(
    attribute: EntityAttribute,
    draft: EntityEdit,
    choices: Map<EntityAttribute, List<String>>,
    nameError: Boolean,
    onDraftChange: (EntityEdit) -> Unit,
) {
    when (attribute) {
        EntityAttribute.Intro -> OutlinedTextField(
            value = draft.intro,
            onValueChange = { onDraftChange(draft.copy(intro = it)) },
            label = { Text(stringResource(attribute.labelRes())) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        // ponytail: numeric parent id; swap for a category picker if it annoys.
        EntityAttribute.Parent -> OutlinedTextField(
            value = draft.parent,
            onValueChange = { value ->
                onDraftChange(draft.copy(parent = value.filter { it.isDigit() }))
            },
            label = { Text(stringResource(attribute.labelRes())) },
            supportingText = { Text(stringResource(R.string.catalog_parent_hint)) },
            singleLine = true,
            // Tabular figures: an id is a number that has to line up with the ids it refers to.
            textStyle = MaterialTheme.typography.bodyLarge.merge(NumericTextStyle),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        else -> {
            val options = choices[attribute]
            if (options == null) {
                // Only the name is required by the backend; its complaint belongs under it, where
                // the reader is looking, rather than in one message under the whole form.
                val requiredSupport: (@Composable () -> Unit)? =
                    if (nameError) {
                        { Text(stringResource(R.string.catalog_name_required), color = MaterialTheme.colorScheme.error) }
                    } else {
                        null
                    }
                OutlinedTextField(
                    value = draft.valueOf(attribute),
                    onValueChange = { onDraftChange(draft.withValue(attribute, it)) },
                    label = { Text(stringResource(attribute.labelRes())) },
                    isError = nameError,
                    supportingText = requiredSupport,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                ChoiceField(
                    value = draft.valueOf(attribute),
                    options = options,
                    labelRes = attribute.labelRes(),
                    onValueChange = { onDraftChange(draft.withValue(attribute, it)) },
                )
            }
        }
    }
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
            // The chevron is what separates a picker from a field you are expected to type in.
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
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
