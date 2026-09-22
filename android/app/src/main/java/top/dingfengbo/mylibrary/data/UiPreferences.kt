package top.dingfengbo.mylibrary.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.uiPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "ui")

/**
 * Look-and-feel choices that belong to this device, not to the account.
 *
 * Material You is the interesting one: it repaints the app in the wallpaper's colours, which is
 * what a modern Android app should be able to do, but it also erases the app's own palette and
 * makes every phone look different from the web client on the same account. So it is a choice the
 * reader makes here, off by default.
 */
class UiPreferences(private val context: Context) {
  private object Keys {
    val dynamicColor = booleanPreferencesKey("dynamic_color")
  }

  val dynamicColor: Flow<Boolean> = context.uiPreferencesDataStore.data.map { it[Keys.dynamicColor] ?: false }

  suspend fun setDynamicColor(enabled: Boolean) {
    context.uiPreferencesDataStore.edit { it[Keys.dynamicColor] = enabled }
  }
}
