package top.dingfengbo.mylibrary.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.dingfengbo.mylibrary.R
import top.dingfengbo.mylibrary.ui.books.BookCover

/**
 * Row for the slim book shapes (`BookSimple`, `ReadingPlanBookSimple`) that collection and plan
 * endpoints return — the full `BookCard` row does not fit them.
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
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = id != null, onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(thumbImage, Modifier.width(36.dp).height(48.dp))
        Spacer(Modifier.width(10.dp))
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
        }
        if (onRemove != null) {
            TextButton(onClick = onRemove) { Text(stringResource(R.string.picker_remove)) }
        }
    }
}
