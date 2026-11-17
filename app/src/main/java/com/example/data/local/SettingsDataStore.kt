package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.DnsMode
import com.example.data.model.PerAppMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "proxygate_settings")

data class ProxySettings(
    val selectedProfileId: Long? = null,
    val perAppMode: PerAppMode = PerAppMode.ALL_APPS,
    val dnsMode: DnsMode = DnsMode.PROXY_TCP,
    val showSystemApps: Boolean = false,
)

class SettingsDataStore(private val context: Context) {
    private object Keys {
        val SELECTED_PROFILE_ID = longPreferencesKey("selected_profile_id")
        val PER_APP_MODE = stringPreferencesKey("per_app_mode")
        val DNS_MODE = stringPreferencesKey("dns_mode")
        val SHOW_SYSTEM_APPS = booleanPreferencesKey("show_system_apps")
    }

    val settingsFlow: Flow<ProxySettings> = context.settingsDataStore.data.map { prefs ->
        ProxySettings(
            selectedProfileId = prefs[Keys.SELECTED_PROFILE_ID],
            perAppMode = prefs[Keys.PER_APP_MODE]
                ?.let { name -> PerAppMode.entries.firstOrNull { it.name == name } }
                ?: PerAppMode.ALL_APPS,
            dnsMode = prefs[Keys.DNS_MODE]
                ?.let { name -> DnsMode.entries.firstOrNull { it.name == name } }
                ?: DnsMode.PROXY_TCP,
            showSystemApps = prefs[Keys.SHOW_SYSTEM_APPS] ?: false,
        )
    }

    suspend fun getSettingsSnapshot(): ProxySettings = settingsFlow.first()

    suspend fun setSelectedProfileId(profileId: Long?) {
        context.settingsDataStore.edit { prefs ->
            if (profileId == null) {
                prefs.remove(Keys.SELECTED_PROFILE_ID)
            } else {
                prefs[Keys.SELECTED_PROFILE_ID] = profileId
            }
        }
    }

    suspend fun setPerAppMode(mode: PerAppMode) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.PER_APP_MODE] = mode.name
        }
    }

    suspend fun setDnsMode(mode: DnsMode) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.DNS_MODE] = mode.name
        }
    }

    suspend fun setShowSystemApps(show: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.SHOW_SYSTEM_APPS] = show
        }
    }
}
