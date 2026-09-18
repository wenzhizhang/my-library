package top.dingfengbo.mylibrary.ui.books

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import top.dingfengbo.mylibrary.R
import top.dingfengbo.mylibrary.data.model.BookQuery

/**
 * Advanced filters for the "all books" listing, in a sheet so they cost no screen space while the
 * list is being browsed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookFilterSheet(
    initial: BookQuery,
    onApply: (BookQuery) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.books_filter_sheet_title), style = MaterialTheme.typography.titleMedium)

            FilterField(
                value = draft.author,
                onChange = { draft = draft.copy(author = it) },
                label = stringResource(R.string.books_author),
            )
            FilterField(
                value = draft.publisher,
                onChange = { draft = draft.copy(publisher = it) },
                label = stringResource(R.string.books_publisher),
            )
            FilterField(
                value = draft.tag,
                onChange = { draft = draft.copy(tag = it) },
                label = stringResource(R.string.books_tag),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterField(
                    value = draft.minPrice,
                    onChange = { draft = draft.copy(minPrice = it) },
                    label = stringResource(R.string.books_price_min),
                    number = true,
                    modifier = Modifier.weight(1f),
                )
                FilterField(
                    value = draft.maxPrice,
                    onChange = { draft = draft.copy(maxPrice = it) },
                    label = stringResource(R.string.books_price_max),
                    number = true,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterField(
                    value = draft.purchaseYear,
                    onChange = { draft = draft.copy(purchaseYear = it) },
                    label = stringResource(R.string.books_purchase_year),
                    number = true,
                    modifier = Modifier.weight(1f),
                )
                FilterField(
                    value = draft.purchaseMonth,
                    onChange = { draft = draft.copy(purchaseMonth = it) },
                    label = stringResource(R.string.books_purchase_month),
                    number = true,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = { draft = draft.cleared() }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.books_reset))
                }
                Button(onClick = { onApply(draft) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.books_apply))
                }
            }
        }
    }
}

@Composable
private fun FilterField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    number: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (number) KeyboardType.Number else KeyboardType.Text
        ),
        modifier = modifier.fillMaxWidth(),
    )
}
