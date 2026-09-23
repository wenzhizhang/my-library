package top.dingfengbo.mylibrary.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import top.dingfengbo.mylibrary.R
import top.dingfengbo.mylibrary.data.model.BookScope
import top.dingfengbo.mylibrary.ui.common.AppTopBar
import top.dingfengbo.mylibrary.data.AppContainer
import top.dingfengbo.mylibrary.data.MediaUrls
import top.dingfengbo.mylibrary.data.auth.Session
import top.dingfengbo.mylibrary.theme.IdentifierTextStyle
import top.dingfengbo.mylibrary.theme.Spacing
import top.dingfengbo.mylibrary.ui.common.ErrorState
import top.dingfengbo.mylibrary.ui.common.SkeletonBlock
import top.dingfengbo.mylibrary.ui.common.errorMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    session: Session,
    serverUrl: String,
    onSignOut: suspend () -> Unit,
    onOpenStats: () -> Unit,
    onShowScope: (BookScope) -> Unit,
    onOpenExport: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel {
        SettingsViewModel(container.preferencesRepository, container.backgroundState, container.uiPreferences)
    },
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var confirmingSignOut by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        // Mine is one of the bar's roots: the bar owns the bottom inset for it.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = { Text(stringResource(R.string.mine_title)) },
            )
        },
        modifier = modifier,
    ) { padding ->
        Column(
            Modifier.fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxl),
        ) {
            Section(R.string.settings_account) {
                ValueRow(Icons.Default.Person, R.string.settings_username, session.username)
                ValueRow(
                    Icons.Default.AccountBox,
                    R.string.settings_user_uuid,
                    session.uuid,
                    identifier = true,
                )
            }

            Section(R.string.settings_server) {
                // The label already says "server", so the row carries only the address, in the
                // identifier face: it is something the reader copies out, not something to read.
                ValueRow(Icons.Default.Place, null, serverUrl, identifier = true)
            }

            Section(R.string.settings_appearance) {
                ToggleRow(
                    icon = Icons.Default.Face,
                    title = stringResource(R.string.settings_dynamic_color),
                    subtitle = stringResource(R.string.settings_dynamic_color_hint),
                    checked = ui.dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor,
                )
            }

            // The section's leading visual is the picture in effect, so "which background am I on?"
            // is answered before the reader starts comparing thumbnails.
            Section(
                titleRes = R.string.settings_background,
                leading = { InEffectPreview(ui) },
            ) {
                BackgroundChoices(ui = ui, onSelect = viewModel::select, onRetry = viewModel::load)
                Text(
                    text = stringResource(R.string.settings_background_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.xs),
                )
            }

            // The listings live here rather than as a row of tabs over the library: they are places
            // you go on purpose, not a switch you flip while browsing.
            Section(R.string.books_title) {
                NavigationRow(Icons.Default.Favorite, R.string.books_scope_wishlist) {
                    onShowScope(BookScope.Wishlist)
                }
                // The curated icon set has no archive glyph; a tick is the nearest honest one.
                NavigationRow(Icons.Default.Done, R.string.books_scope_archived) {
                    onShowScope(BookScope.Archived)
                }
            }

            Section(R.string.settings_more) {
                // The same glyph the bar used for statistics before it moved in here.
                NavigationRow(Icons.Default.Info, R.string.stats_title, onOpenStats)
                NavigationRow(Icons.Default.Share, R.string.export_title, onOpenExport)
            }

            Button(
                onClick = { confirmingSignOut = true },
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(stringResource(R.string.settings_logout))
            }
        }
    }

    if (confirmingSignOut) {
        AlertDialog(
            onDismissRequest = { confirmingSignOut = false },
            title = { Text(stringResource(R.string.settings_logout_confirm_title)) },
            text = { Text(stringResource(R.string.settings_logout_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingSignOut = false
                        scope.launch { onSignOut() }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.settings_logout_confirm_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingSignOut = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

/** A labelled group of rows: the label is what marks where one group ends and the next begins. */
@Composable
private fun Section(
    titleRes: Int,
    leading: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(Spacing.md))
            }
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            content = content,
        )
    }
}

/**
 * One row of a section: a leading visual, a label, an optional second line, and whatever the row
 * ends in. [onSelect] makes the whole row the target of a single choice, which TalkBack then
 * announces as selected; [onClick] makes it a plain navigable row. Neither: the row is information.
 */
@Composable
private fun RowShell(
    icon: ImageVector? = null,
    title: String? = null,
    support: String? = null,
    identifier: Boolean = false,
    onClick: (() -> Unit)? = null,
    onSelect: (() -> Unit)? = null,
    selected: Boolean = false,
    checked: Boolean = false,
    onToggle: ((Boolean) -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            // 48dp is the smallest thing a thumb hits reliably, and the padding sits inside the tap
            // target so the whole row is live rather than just the text.
            .heightIn(min = 48.dp)
            .then(
                when {
                    // A switch row is one control: the whole row toggles and carries the label, so the
                    // Switch itself is given no handler and cannot become a nameless second target.
                    onToggle != null ->
                        Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onToggle)

                    onSelect != null ->
                        Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)

                    // A row that navigates is a button, not an option: selectable would publish a
                    // Selected state that never changes and read as "not selected" to a screen reader.
                    onClick != null ->
                        Modifier.clickable(role = Role.Button, onClick = onClick)

                    else -> Modifier
                }
            )
            .padding(vertical = Spacing.sm, horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            leading != null -> leading()
            icon != null ->
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint =
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
        }
        if (leading != null || icon != null) Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color =
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (support != null) {
                Text(
                    text = support,
                    style =
                        if (identifier) MaterialTheme.typography.bodySmall.merge(IdentifierTextStyle)
                        else MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) trailing()
    }
}

/** Something the app knows and the reader only reads: no tap target, the value is the whole point. */
@Composable
private fun ValueRow(icon: ImageVector, labelRes: Int?, value: String, identifier: Boolean = false) {
    RowShell(
        icon = icon,
        title = if (labelRes != null) stringResource(labelRes) else null,
        support = value,
        identifier = identifier,
    )
}

/** A row the app can only turn on or off. */
@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    RowShell(
        icon = icon,
        title = title,
        support = subtitle,
        checked = checked,
        onToggle = onCheckedChange,
        trailing = {
            // No handler: the row owns the toggle, and the label is part of that one target.
            Switch(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.padding(start = Spacing.md),
            )
        },
    )
}

/** A row that leaves this screen: the chevron is the promise that something happens. */
@Composable
private fun NavigationRow(icon: ImageVector, titleRes: Int, onClick: () -> Unit) {
    RowShell(
        icon = icon,
        title = stringResource(titleRes),
        onClick = onClick,
        trailing = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.md).size(20.dp),
            )
        },
    )
}

/** The chosen picture, or the configured default when the account has chosen nothing. */
@Composable
private fun InEffectPreview(ui: SettingsUiState) {
    val activeId = ui.selectedId ?: ui.defaultId
    val active = ui.backgrounds.firstOrNull { it.id != null && it.id == activeId } ?: return
    AsyncImage(
        model = MediaUrls.image(active.url),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(width = 72.dp, height = 40.dp).clip(MaterialTheme.shapes.extraSmall),
    )
}

@Composable
private fun BackgroundChoices(ui: SettingsUiState, onSelect: (String) -> Unit, onRetry: () -> Unit) {
    if (ui.loading && ui.backgrounds.isEmpty()) {
        // Three rows the shape of the real ones, so nothing jumps when the list lands.
        repeat(3) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonBlock(Modifier.size(width = 72.dp, height = 40.dp), corner = 6)
                Spacer(Modifier.width(Spacing.md))
                SkeletonBlock(Modifier.height(14.dp).weight(1f))
            }
        }
        return
    }

    // Above the choices, not below, and kept even when they are already on screen: a failure the
    // reader has to scroll past the picker to find reads as "the picker did nothing".
    if (ui.error != null) {
        ErrorState(
            message = errorMessage(ui.error) ?: stringResource(R.string.error_unknown),
            onRetry = onRetry,
        )
    }

    val activeId = ui.selectedId ?: ui.defaultId
    ui.backgrounds.forEach { background ->
        val id = background.id
        val isActive = id != null && id == activeId
        // An entry with no id cannot be chosen; that row stays inert rather than offering a tap that
        // does nothing.
        val select: (() -> Unit)? = id?.let { selectId -> { onSelect(selectId) } }
        RowShell(
            leading = {
                AsyncImage(
                    model = MediaUrls.image(background.url),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier.size(width = 72.dp, height = 40.dp)
                            .clip(MaterialTheme.shapes.extraSmall),
                )
            },
            title = background.name.orEmpty(),
            selected = isActive,
            onSelect = select,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Marked separately from the check: even with a choice of one's own, the default is
                    // still worth naming, because a signed-out app and every other device show it.
                    if (id != null && id == ui.defaultId) {
                        Text(
                            text = stringResource(R.string.settings_background_default),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(Spacing.sm))
                    }
                    if (isActive) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            },
        )
    }
}
