package top.dingfengbo.mylibrary.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Every top bar in the app, so its height, its fill and its alignment are decided once.
 *
 * It lays itself out rather than shrinking Material's bar with a height modifier: that modifier only
 * clips, while the bar inside keeps placing its content for its own 64dp, which leaves the title a few
 * dp from the bottom edge and the actions below centre.
 *
 * [AppBarHeight] is 56dp - one step below Material's 64 - because a title and an icon do not need more,
 * and on a phone the bar is a fifth of the screen before the reader has seen any content.
 */
val AppBarHeight = 56.dp

/**
 * The bars are translucent so the picture behind the app reads through them. The app paints that
 * picture itself and already blurs it, which is what makes this frosted rather than merely tinted:
 * nothing here tries to blur what is behind a bar, because Android has no cheap way to do that.
 *
 * The alpha is low on purpose. The picture is washed light before it is ever drawn, so a high alpha
 * over it is indistinguishable from an opaque white bar - which is exactly what a "translucent" bar
 * must not look like. Nothing scrolls under these bars (the screens keep their content inside the
 * scaffold's padding), so a low alpha costs no legibility.
 */
private const val AppBarAlpha = 0.40f

/** The fill for anything that should frost: the top bars and the bottom bar. */
@Composable
fun appBarFill(): Color = MaterialTheme.colorScheme.surface.copy(alpha = AppBarAlpha)

/**
 * The app's top bar. Same parameters as the Material one, so a screen swaps the name and nothing else:
 * [title] keeps the type style Material gave it, [navigationIcon] is optional, [actions] take the end.
 */
@Composable
fun AppTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Surface(color = appBarFill(), modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(AppBarHeight)
                .padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navigationIcon?.invoke()
            ProvideTextStyle(MaterialTheme.typography.titleLarge) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        // 16dp from the screen edge, the inset Material itself uses for a title. With a
                        // navigation icon in front, the icon's own 48dp touch target provides it.
                        .padding(start = if (navigationIcon == null) 16.dp else 0.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    title()
                }
            }
            actions()
        }
    }
}
