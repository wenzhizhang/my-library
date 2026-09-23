package top.dingfengbo.mylibrary.ui.books

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import top.dingfengbo.mylibrary.R
import top.dingfengbo.mylibrary.data.model.BookQuery
import top.dingfengbo.mylibrary.theme.NumericTextStyle
import top.dingfengbo.mylibrary.theme.Spacing

/**
 * Advanced filters for the "all books" listing, in a sheet so they cost no screen space while the
 * list is being browsed.
 *
 * Eight fields stack up to three screens tall on a phone, so they are grouped under headers and the
 * apply/reset pair is pinned to the bottom: the reader never has to scroll back up to commit a
 * filter they just set.
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
    val active = draft.filterCount

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                // The count is the same wording the list's filter button uses, so the number the
                // reader tapped is the number they see here.
                text = if (active > 0) stringResource(R.string.books_filter_active, active)
                else stringResource(R.string.books_filter_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )

            Column(
                modifier = Modifier
                    // fill = false so a sheet whose groups already fit stays short, while a long set
                    // of filters scrolls inside the space above the pinned actions.
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                FilterGroup(R.string.books_filter_group_identity) {
                    FilterField(
                        value = draft.title,
                        onChange = { draft = draft.copy(title = it) },
                        label = stringResource(R.string.books_filter_title),
                    )
                    FilterField(
                        value = draft.author,
                        onChange = { draft = draft.copy(author = it) },
                        label = stringResource(R.string.books_author),
                    )
                }

                FilterGroup(R.string.books_filter_group_classification) {
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
                }

                FilterGroup(R.string.books_filter_group_price) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
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
                }

                FilterGroup(R.string.books_filter_group_acquisition) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
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
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                OutlinedButton(onClick = { draft = draft.cleared() }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.books_reset))
                }
                Button(onClick = { onApply(draft) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.books_apply))
                }
            }
        }
    }
}

/** One group of related filters: a header that reads as a section, not as another field label. */
@Composable
private fun FilterGroup(titleRes: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
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
        // Filter values are compared against numbers, so they line up as figures rather than prose.
        textStyle = if (number) MaterialTheme.typography.bodyLarge.merge(NumericTextStyle)
        else MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (number) KeyboardType.Number else KeyboardType.Text
        ),
        modifier = modifier.fillMaxWidth(),
    )
}
