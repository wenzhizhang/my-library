package top.dingfengbo.mylibrary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import top.dingfengbo.mylibrary.theme.MyLibraryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = (application as MyLibraryApp).container

        enableEdgeToEdge()
        setContent {
            val background by container.backgroundState.url.collectAsState()
            val dynamicColor by container.uiPreferences.dynamicColor.collectAsState(initial = false)
            // The picture sits behind every screen, so the pages have to let it through: see the
            // theme's transparentBackground.
            MyLibraryTheme(dynamicColor = dynamicColor, transparentBackground = background != null) {
                Box(Modifier.fillMaxSize()) {
                    AppBackground(background)
                    AppNavigation(container)
                }
            }
        }
    }
}

/**
 * The full-screen background, matching the web front end: the image is blurred and scaled to cover,
 * with the same 12% white wash the web puts over it (frontend/src/MyLibrary.css, .MyLibrary::after).
 *
 * The wash follows the theme because the web has no dark mode to match here: light text over a
 * photograph washed white is the one combination that does not work, so the dark theme washes
 * darker. It stays light in both directions for the same reason the web keeps it light - the app,
 * like the web, keeps text readable over a picture by giving the content its own surface (see
 * EmptyState and ErrorState) rather than by dimming the picture the reader chose.
 *
 * Blur is a no-op below API 31; the image and the wash still apply there.
 */
@Composable
private fun AppBackground(url: String?) {
    if (url == null) return
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(10.dp),
        )
        // 12% in light: the value the web uses. 45% in dark: nothing to match there, and light
        // text over a picture washed white is the one combination that does not work.
        val darkWash = isSystemInDarkTheme()
        val wash = if (darkWash) Color.Black.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.12f)
        Box(Modifier.fillMaxSize().background(wash))
    }
}
