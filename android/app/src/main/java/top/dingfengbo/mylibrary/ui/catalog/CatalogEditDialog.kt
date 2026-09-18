package top.dingfengbo.mylibrary.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
 * The fields come from the caller: `entity.createAttributes` when creating, `entity.editable` when
 * editing. That is what keeps six entities down to one dialog.
 */
@Composable
fun CatalogEditDialog(
    entity: CatalogEntity,
    attributes: List<EntityAttribute>,
    initial: EntityEdit,
    onSave: suspend (EntityEdit) -> Result<Unit>,
    onDismiss: () -> Unit,
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    val scope = rememberCoroutineScope()

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
                                placeholder = { Text(stringResource(R.string.catalog_parent_hint)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        else -> OutlinedTextField(
                            value = draft.valueOf(attribute),
                            onValueChange = { draft = draft.withValue(attribute, it) },
                            label = { Text(stringResource(attribute.labelRes())) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
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
