package top.dingfengbo.mylibrary.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * The app's theme.
 *
 * [dynamicColor] is off by default on purpose. Android 12+ Material You paints the app in the
 * device's wallpaper colours, which looks modern but means the app has no identity of its own and
 * looks different from the web client sitting on the same account. It is offered as a setting
 * instead, so the choice belongs to the reader.
 */
@Composable
fun MyLibraryTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  /**
   * When a background picture is active the page must let it through: Scaffold paints
   * `colorScheme.background`, and an opaque page colour would hide the picture entirely. A
   * transparent background has no derivable `on*` colour, so the caller that introduces the
   * transparency (the shell's Surface) states the content colour.
   */
  transparentBackground: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkColors
      else -> LightColors
    }

  MaterialTheme(
    colorScheme = if (transparentBackground) colorScheme.copy(background = Color.Transparent) else colorScheme,
    typography = Typography,
    shapes = AppShapes,
    content = content,
  )
}
