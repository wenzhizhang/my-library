package top.dingfengbo.mylibrary.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import top.dingfengbo.mylibrary.R

/**
 * The app's one search box: a magnifier that says what the field is for, and a clear button,
 * because retyping a query to widen the results is the common move.
 *
 * Shared by the lists and the pickers so a search looks the same wherever the reader meets one.
 */
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    hintRes: Int,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(hintRes)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = stringResource(R.string.common_clear),
                    )
                }
            }
        },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}
