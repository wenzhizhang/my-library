package top.dingfengbo.mylibrary.data.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

/**
 * Persistence for the signed-in session.
 *
 * An interface so the session state machine (expiry, restore, sign-out) can be exercised without an
 * Android Context — the concrete implementation is [TokenStore].
 */
interface SessionStore {
    suspend fun current(): Session?
    suspend fun save(session: Session)
    suspend fun clear()
}

/**
 * Persists the access token in the app's private DataStore.
 *
 * Deliberately not encrypted: the token is a 30-day bearer credential, not a long-lived secret,
 * and app-private storage is already sandboxed on non-rooted devices. Backup is disabled in the
 * manifest so the file cannot leave the device through adb/cloud backup. Passwords are never stored.
 */
class TokenStore(private val context: Context) : SessionStore {
    private object Keys {
        val token = stringPreferencesKey("access_token")
        val username = stringPreferencesKey("username")
        val uuid = stringPreferencesKey("uuid")
        val expiresAt = stringPreferencesKey("expires_at")
    }

    override suspend fun current(): Session? {
        val prefs = context.sessionDataStore.data.first()
        val token = prefs[Keys.token] ?: return null
        return Session(
            token = token,
            username = prefs[Keys.username].orEmpty(),
            uuid = prefs[Keys.uuid].orEmpty(),
            expiresAtEpochSeconds = prefs[Keys.expiresAt]?.toLongOrNull(),
        )
    }

    override suspend fun save(session: Session) {
        context.sessionDataStore.edit { prefs ->
            prefs[Keys.token] = session.token
            prefs[Keys.username] = session.username
            prefs[Keys.uuid] = session.uuid
            session.expiresAtEpochSeconds?.let { prefs[Keys.expiresAt] = it.toString() }
                ?: prefs.remove(Keys.expiresAt)
        }
    }

    override suspend fun clear() {
        context.sessionDataStore.edit { it.clear() }
    }
}
