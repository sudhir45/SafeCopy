package com.sudhir.safecopy.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "settings")

/**
 * App settings in DataStore (spec 11). M3 only needs recent recipients; the rest of the
 * spec 11 keys arrive with the Settings screen in M5. DataStore lives in app-private files,
 * which backup rules exclude (spec 2.9).
 */
class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore

    val recentRecipients: Flow<List<String>> = store.data.map { RecentRecipients.decode(it[KEY_RECENTS]) }

    suspend fun addRecentRecipient(recipient: String) {
        store.edit { prefs ->
            prefs[KEY_RECENTS] = RecentRecipients.encode(
                RecentRecipients.add(RecentRecipients.decode(prefs[KEY_RECENTS]), recipient),
            )
        }
    }

    private companion object {
        // Ordered list, so a joined string rather than the string set spec 11 sketches.
        val KEY_RECENTS = stringPreferencesKey("recentRecipients")
    }
}
