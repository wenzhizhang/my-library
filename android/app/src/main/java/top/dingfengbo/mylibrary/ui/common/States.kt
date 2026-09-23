package top.dingfengbo.mylibrary.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.dingfengbo.mylibrary.R
import top.dingfengbo.mylibrary.theme.Spacing

/**
 * Loading, empty and error, in one place.
 *
 * Before this, every screen drew its own `CircularProgressIndicator`: a spinner tells the reader
 * that something is happening but not what is coming, and it makes the layout jump when the real
 * content arrives. A skeleton that copies the shape of the content is the difference between a
 * screen that feels loaded and one that feels broken. Same for the empty and error cases, which
 * were one line of text with nowhere to go.
 */

/**
 * Tells a screen reader that content is on its way. The blocks say nothing by themselves, so without
 * this a first load is a silent pause followed by content appearing from nowhere.
 */
@Composable
private fun Modifier.skeletonSemantics(): Modifier {
  val label = stringResource(R.string.common_loading)
  return semantics(mergeDescendants = true) {
    contentDescription = label
    liveRegion = LiveRegionMode.Polite
  }
}

/** A single shimmering placeholder block. */
@Composable
fun SkeletonBlock(
  modifier: Modifier = Modifier,
  corner: Int = 8,
) {
  val transition = rememberInfiniteTransition(label = "skeleton")
  val alpha by
    transition.animateFloat(
      initialValue = 0.35f,
      targetValue = 0.7f,
      animationSpec =
        infiniteRepeatable(tween(durationMillis = 900), repeatMode = RepeatMode.Reverse),
      label = "skeleton-alpha",
    )
  Box(
    modifier
      .clip(RoundedCornerShape(corner.dp))
      .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.18f))
  )
}

/**
 * The book list's shape while the first page loads: a cover block, a title line, a meta line. Sized
 * to match the real rows so nothing moves when the data lands.
 */
@Composable
fun BookListSkeleton(modifier: Modifier = Modifier, rows: Int = 6) {
  Column(
    modifier.fillMaxWidth().skeletonSemantics().padding(horizontal = Spacing.lg),
    verticalArrangement = Arrangement.spacedBy(Spacing.md),
  ) {
    repeat(rows) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        SkeletonBlock(Modifier.size(56.dp), corner = 6)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
          SkeletonBlock(Modifier.fillMaxWidth(0.72f).height(16.dp))
          SkeletonBlock(Modifier.fillMaxWidth(0.42f).height(12.dp))
        }
      }
    }
  }
}

/** A detail or form screen's shape while the record loads: a header block plus a few field rows. */
@Composable
fun DetailSkeleton(modifier: Modifier = Modifier, rows: Int = 5) {
  Column(
    modifier.fillMaxWidth().skeletonSemantics().padding(Spacing.lg),
    verticalArrangement = Arrangement.spacedBy(Spacing.md),
  ) {
    SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(24.dp))
    Spacer(Modifier.height(Spacing.sm))
    repeat(rows) {
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SkeletonBlock(Modifier.fillMaxWidth(0.3f).height(11.dp))
        SkeletonBlock(Modifier.fillMaxWidth(0.85f).height(16.dp))
      }
    }
  }
}

/**
 * The backing every state message sits on.
 *
 * A state is the one thing on a screen drawn straight over whatever is behind it, and with a
 * personal background picture set that is a photograph. Hence the surface, for the same reason the
 * web front end keeps its content in frosted cards instead of dimming the page: the picture stays
 * the reader's, the words stay readable.
 *
 * It sizes to its content and never takes a height of its own: callers put states inside lazy list
 * items (a failed page, an empty tail), and a scrollable component measured under a lazy item's
 * unbounded height is a crash, not a layout.
 */
@Composable
private fun StateSurface(
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    Spacer(Modifier.height(Spacing.xl))
    Surface(
      shape = MaterialTheme.shapes.large,
      color = MaterialTheme.colorScheme.surface,
    ) {
      Column(
        Modifier.padding(horizontal = Spacing.xxl, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        content = content,
      )
    }
  }
}

/**
 * "Nothing here" with a way out. [action] is what makes it usable: an empty list the reader cannot
 * populate from where they are standing is a dead end.
 */
@Composable
fun EmptyState(
  icon: ImageVector,
  title: String,
  hint: String? = null,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  StateSurface(modifier) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(40.dp),
    )
    Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    if (!hint.isNullOrBlank()) {
      Text(
        hint,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
    }
    if (actionLabel != null && onAction != null) {
      Spacer(Modifier.height(Spacing.xs))
      Button(onClick = onAction) { Text(actionLabel) }
    }
  }
}

/**
 * A failure the reader can act on. [detail] carries what the API said when there is something
 * specific to show; the retry is always offered because every failure here is a request.
 */
@Composable
fun ErrorState(
  message: String,
  detail: String? = null,
  onRetry: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  StateSurface(modifier) {
    Icon(
      imageVector = Icons.Default.Warning,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.error,
      modifier = Modifier.size(32.dp),
    )
    Text(message, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
    if (!detail.isNullOrBlank()) {
      Text(
        detail,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
    }
    if (onRetry != null) {
      TextButton(onClick = onRetry) { Text(stringResource(R.string.error_retry)) }
    }
  }
}
