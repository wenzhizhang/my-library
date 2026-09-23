package top.dingfengbo.mylibrary.ui.common

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Every top bar in the app, so its height and its fill are decided once.
 *
 * 56dp, one step below Material's 64: a title and one icon do not need more, and on a phone the bar
 * is a fifth of the screen before the reader has seen any content.
 */
val AppBarHeight = 56.dp

/**
 * The bars are translucent so the blurred picture behind the app reads through them - the app paints
 * that picture itself, already blurred, which is what makes this a frosted bar rather than a coloured
 * one. Too transparent and list rows crawling under the bar fight the title; too opaque and there is
 * no point being translucent at all.
 */
private const val AppBarAlpha = 0.74f

/** The fill for anything that should frost: the top bars and the bottom bar. */
@Composable
fun appBarFill(): Color = MaterialTheme.colorScheme.surface.copy(alpha = AppBarAlpha)

/**
 * [TopAppBar] with the app's height and fill. Same parameters and same defaults as the Material bar,
 * so a screen swaps the name and nothing else.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = title,
        modifier = modifier.height(AppBarHeight),
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = appBarFill()),
    )
}
