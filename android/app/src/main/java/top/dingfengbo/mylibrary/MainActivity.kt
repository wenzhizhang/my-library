package top.dingfengbo.mylibrary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
            // The picture sits behind every screen, so the pages have to let it through: see the
            // theme's transparentBackground.
            MyLibraryTheme(transparentBackground = background != null) {
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
 * with a light wash on top so text over it keeps its contrast.
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
        Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.12f)))
    }
}
