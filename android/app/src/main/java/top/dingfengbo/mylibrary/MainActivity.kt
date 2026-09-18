package top.dingfengbo.mylibrary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import top.dingfengbo.mylibrary.theme.MyLibraryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = (application as MyLibraryApp).container

        enableEdgeToEdge()
        setContent { MyLibraryTheme { AppNavigation(container) } }
    }
}
