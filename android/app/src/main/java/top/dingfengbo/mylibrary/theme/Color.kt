package top.dingfengbo.mylibrary.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The app's palette: a single desaturated green accent over cool neutral surfaces.
 *
 * Chosen for a library: ink on paper reads calm, green is under-used in this category, and the
 * hue sits far from the purple/violet the Compose template ships with. Values are Material 3
 * tonal steps of hue ~163 (primary 40 light / 80 dark, container 90 / 30, surfaces 99 / 10), so
 * every role keeps a predictable contrast relationship instead of being picked by eye.
 *
 * One accent only: secondary and tertiary are tonal neighbours of the same hue, never a second
 * colour. Semantic roles (error, and the status tints below) are the only exceptions.
 */
internal val LightColors =
  lightColorScheme(
    primary = Color(0xFF2E6A54),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFAEF2D8),
    onPrimaryContainer = Color(0xFF002115),
    secondary = Color(0xFF4C6359),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCEE9DB),
    onSecondaryContainer = Color(0xFF092017),
    tertiary = Color(0xFF3F6375),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC2E8FE),
    onTertiaryContainer = Color(0xFF001E2C),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF5FBF7),
    onBackground = Color(0xFF171D1A),
    surface = Color(0xFFF5FBF7),
    onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFDBE5DF),
    onSurfaceVariant = Color(0xFF3F4945),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF5F1),
    surfaceContainer = Color(0xFFE9EFEB),
    surfaceContainerHigh = Color(0xFFE3EAE6),
    surfaceContainerHighest = Color(0xFFDEE4E0),
    outline = Color(0xFF6F7975),
    outlineVariant = Color(0xFFBFC9C4),
    inverseSurface = Color(0xFF2C322F),
    inverseOnSurface = Color(0xFFEDF2EE),
    inversePrimary = Color(0xFF92D5BA),
    scrim = Color(0xFF000000),
  )

internal val DarkColors =
  darkColorScheme(
    primary = Color(0xFF92D5BA),
    onPrimary = Color(0xFF003828),
    primaryContainer = Color(0xFF13503D),
    onPrimaryContainer = Color(0xFFAEF2D8),
    secondary = Color(0xFFB2CCBE),
    onSecondary = Color(0xFF1E352B),
    secondaryContainer = Color(0xFF344C41),
    onSecondaryContainer = Color(0xFFCEE9DB),
    tertiary = Color(0xFFA6CCDF),
    onTertiary = Color(0xFF073544),
    tertiaryContainer = Color(0xFF254C5C),
    onTertiaryContainer = Color(0xFFC2E8FE),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1512),
    onBackground = Color(0xFFDEE4E0),
    surface = Color(0xFF0F1512),
    onSurface = Color(0xFFDEE4E0),
    surfaceVariant = Color(0xFF3F4945),
    onSurfaceVariant = Color(0xFFBFC9C4),
    surfaceContainerLowest = Color(0xFF0A0F0D),
    surfaceContainerLow = Color(0xFF171D1A),
    surfaceContainer = Color(0xFF1B211E),
    surfaceContainerHigh = Color(0xFF252B28),
    surfaceContainerHighest = Color(0xFF303633),
    outline = Color(0xFF89938E),
    outlineVariant = Color(0xFF3F4945),
    inverseSurface = Color(0xFFDEE4E0),
    inverseOnSurface = Color(0xFF2C322F),
    inversePrimary = Color(0xFF2E6A54),
    scrim = Color(0xFF000000),
  )

/**
 * Status tints the app shows outside Material's own roles (reading progress, wishlist, archived).
 *
 * One set per theme, because these are read as text and as thin indicators rather than as fills. The
 * light steps measure 4.6-6.3:1 against a light surface but only 2.6-2.9:1 against this app's dark
 * one, which leaves a dark-theme reader unable to tell which shelf or state a book is in. The dark
 * steps are the same hues at tonal 80, matching the steps DarkColors itself is built from.
 */
internal data class StatusPalette(
  val read: Color,
  val reading: Color,
  val unread: Color,
  val wishlist: Color,
  val archived: Color,
)

private val LightStatus =
  StatusPalette(
    read = Color(0xFF2E6A54),
    // 5.03:1 as an 11sp chip label over its own 16% fill. The tonal-40 gold it replaces measured
    // 3.93:1 there, which is below AA for text that small.
    reading = Color(0xFF755812),
    unread = Color(0xFF5A6560),
    wishlist = Color(0xFF8A4A6A),
    archived = Color(0xFF5C6470),
  )

private val DarkStatus =
  StatusPalette(
    read = Color(0xFF92D5BA),
    reading = Color(0xFFE8C36A),
    unread = Color(0xFFBEC9C4),
    wishlist = Color(0xFFF4B0CE),
    archived = Color(0xFFC2CBD6),
  )

/** The tints for the theme in use, so one call site reads correctly in light and in dark. */
@Composable
internal fun statusColors(): StatusPalette = if (isSystemInDarkTheme()) DarkStatus else LightStatus
