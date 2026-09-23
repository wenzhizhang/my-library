package top.dingfengbo.mylibrary.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.dingfengbo.mylibrary.R
import top.dingfengbo.mylibrary.theme.Spacing
import top.dingfengbo.mylibrary.theme.statusColors
import top.dingfengbo.mylibrary.ui.books.BookCover
import top.dingfengbo.mylibrary.ui.books.ReadState

/**
 * Row for the slim book shapes (`BookSimple`, `ReadingPlanBookSimple`) that collection and plan
 * endpoints return — the full `BookCard` row does not fit them.
 *
 * @param readState the API's own value ("unread", "reading"…); only reading plans carry one, so it
 *   is optional and the row is otherwise identical in both lists.
 * @param removing the remove is already in flight. The action helper in the view model drops a
 *   second remove, so the row has to say so rather than look like it ignored the tap.
 */
@Composable
fun SimpleBookRow(
    id: Int?,
    title: String?,
    titleCn: String?,
    thumbImage: String?,
    authors: List<String>?,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null,
    readState: String? = null,
    removing: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = id != null, onClick = onClick)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(thumbImage, Modifier.size(44.dp))
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = titleCn?.takeIf { it.isNotBlank() } ?: title.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val authorText = authors.orEmpty().joinToString(", ")
            if (authorText.isNotBlank()) {
                Text(
                    text = authorText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            readState?.takeIf { it.isNotBlank() }?.let { state ->
                Text(
                    text = readStateLabel(state),
                    style = MaterialTheme.typography.labelSmall,
                    color = readStateTint(state),
                    maxLines = 1,
                )
            }
        }
        if (onRemove != null) {
            if (removing) {
                // Same footprint as the button it replaces: the row does not resize mid-request.
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            } else {
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.picker_remove),
                    )
                }
            }
        }
    }
}

/** Known states get their own label; anything else the backend stores is shown as it came. */
@Composable
private fun readStateLabel(state: String): String =
    ReadState.entries.firstOrNull { it.value == state }?.let { stringResource(it.labelRes) } ?: state

/** The reading-state tints from the palette; an unknown state reads as "no state yet". */
@Composable
private fun readStateTint(state: String): Color = when (state) {
    ReadState.Read.value -> statusColors().read
    ReadState.Reading.value -> statusColors().reading
    ReadState.Unread.value -> statusColors().unread
    ReadState.Abandoned.value -> statusColors().archived
    else -> statusColors().unread
}
