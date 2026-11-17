package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.example.data.local.PerAppRuleEntity
import com.example.data.local.ProxyDao
import com.example.data.local.ProxyProfileEntity
import com.example.data.local.ProxySettings
import com.example.data.local.SettingsDataStore
import com.example.data.model.DnsMode
import com.example.data.model.InstalledAppInfo
import com.example.data.model.LogLevel
import com.example.data.model.PerAppMode
import com.example.vpn.ProxyTunnelClient
import com.example.vpn.VpnStateHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class ProxyRepository(
    private val context: Context,
    private val dao: ProxyDao,
    private val settingsDataStore: SettingsDataStore,
) {
    private val iconCache = ConcurrentHashMap<String, Drawable>()

    val profilesFlow: Flow<List<ProxyProfileEntity>> = dao.observeProfiles()
    val perAppRulesFlow: Flow<List<PerAppRuleEntity>> = dao.observePerAppRules()
    val settingsFlow: Flow<ProxySettings> = settingsDataStore.settingsFlow

    suspend fun saveProfile(profile: ProxyProfileEntity): Long {
        return if (profile.id == 0L) {
            val newId = dao.insertProfile(profile)
            val currentSettings = settingsDataStore.getSettingsSnapshot()
            if (currentSettings.selectedProfileId == null) {
                settingsDataStore.setSelectedProfileId(newId)
            }
            newId
        } else {
            dao.updateProfile(profile)
            profile.id
        }
    }

    suspend fun deleteProfile(profileId: Long) {
        dao.deleteProfileById(profileId)
        val current = settingsDataStore.getSettingsSnapshot()
        if (current.selectedProfileId == profileId) {
            settingsDataStore.setSelectedProfileId(null)
        }
    }

    suspend fun selectProfile(profileId: Long) {
        settingsDataStore.setSelectedProfileId(profileId)
    }

    suspend fun setPerAppMode(mode: PerAppMode) {
        settingsDataStore.setPerAppMode(mode)
    }

    suspend fun setDnsMode(mode: DnsMode) {
        settingsDataStore.setDnsMode(mode)
    }

    suspend fun setShowSystemApps(show: Boolean) {
        settingsDataStore.setShowSystemApps(show)
    }

    suspend fun setAppSelected(packageName: String, appLabel: String, selected: Boolean) {
        if (selected) {
            dao.insertPerAppRule(PerAppRuleEntity(packageName = packageName, appLabel = appLabel))
        } else {
            dao.deletePerAppRule(packageName)
        }
    }

    suspend fun replaceSelectedApps(apps: List<InstalledAppInfo>) {
        val entities = apps.map {
            PerAppRuleEntity(packageName = it.packageName, appLabel = it.appName)
        }
        dao.replacePerAppRules(entities)
    }

    suspend fun testProfileLatency(profile: ProxyProfileEntity): Result<Int> = withContext(Dispatchers.IO) {
        VpnStateHolder.addLog(
            LogLevel.INFO,
            "Ping",
            "شروع تست اتصال ${profile.protocol.label} به ${profile.endpoint}…",
        )
        val result = ProxyTunnelClient.testProxyConnection(profile)
        result.fold(
            onSuccess = { latencyMs ->
                dao.updatePingResult(profile.id, latencyMs, null)
                VpnStateHolder.addLog(
                    LogLevel.INFO,
                    "Ping",
                    "پاسخ موفق از ${profile.name}: ${latencyMs}ms",
                )
            },
            onFailure = { error ->
                val msg = error.message ?: "خطا در تست اتصال"
                dao.updatePingResult(profile.id, null, msg)
                VpnStateHolder.addLog(
                    LogLevel.ERROR,
                    "Ping",
                    "تست ${profile.name} ناموفق بود: $msg",
                )
            },
        )
        result
    }

    suspend fun loadInstalledApps(selectedPackages: Set<String>): List<InstalledAppInfo> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val selfPackage = context.packageName
            val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
            val seenPackages = HashSet<String>()
            val apps = ArrayList<InstalledAppInfo>(resolveInfos.size)

            for (ri in resolveInfos) {
                val appInfo = ri.activityInfo?.applicationInfo ?: continue
                val pkg = appInfo.packageName ?: continue
                if (pkg == selfPackage || !seenPackages.add(pkg)) continue

                val label = ri.loadLabel(pm)?.toString()?.trim().takeUnless { it.isNullOrEmpty() }
                    ?: appInfo.loadLabel(pm)?.toString()?.trim()
                    ?: pkg
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0

                apps.add(
                    InstalledAppInfo(
                        packageName = pkg,
                        appName = label,
                        isSystemApp = isSystem,
                        isSelected = pkg in selectedPackages,
                    ),
                )
            }
            apps.sortedWith(compareByDescending<InstalledAppInfo> { it.isSelected }.thenBy { it.appName.lowercase() })
        }

    fun getAppIcon(packageName: String): Drawable? {
        iconCache[packageName]?.let { return it }
        return try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            iconCache[packageName] = drawable
            drawable
        } catch (_: Exception) {
            null
        }
    }
}
