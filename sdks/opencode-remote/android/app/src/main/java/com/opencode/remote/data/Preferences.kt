package com.opencode.remote.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.store: DataStore<Preferences> by preferencesDataStore("settings")

private val KEY_URL = stringPreferencesKey("relay_url")
private val KEY_TOKEN = stringPreferencesKey("relay_token")

data class ServerConfig(val url: String, val token: String)

suspend fun Context.loadConfig(): ServerConfig {
    val prefs = store.data.first()
    return ServerConfig(
        url = prefs[KEY_URL] ?: "",
        token = prefs[KEY_TOKEN] ?: ""
    )
}

suspend fun Context.saveConfig(url: String, token: String) {
    store.edit {
        it[KEY_URL] = url
        it[KEY_TOKEN] = token
    }
}
