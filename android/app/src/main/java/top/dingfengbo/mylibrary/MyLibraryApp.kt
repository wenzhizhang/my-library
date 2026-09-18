package top.dingfengbo.mylibrary

import android.app.Application
import top.dingfengbo.mylibrary.data.AppContainer

class MyLibraryApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
