package com.example.ui

import android.app.Application
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.ProxyProfileEntity
import com.example.data.local.ProxySettings
import com.example.data.local.SettingsDataStore
import com.example.data.model.ConnectionState
import com.example.data.model.DnsMode
import com.example.data.model.InstalledAppInfo
import com.example.data.model.LogLevel
import com.example.data.model.PerAppMode
import com.example.data.model.ProxyLogEntry
import com.example.data.model.ProxyProtocol
import com.example.data.model.ProxyTelemetry
import com.example.data.model.ProxyUriParser
import com.example.data.repository.ProxyRepository
import com.example.vpn.ProxyVpnService
import com.example.vpn.VpnStateHolder
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val profiles: List<ProxyProfileEntity> = emptyList(),
    val selectedProfile: ProxyProfileEntity? = null,
    val settings: ProxySettings = ProxySettings(),
    val telemetry: ProxyTelemetry = ProxyTelemetry(),
    val logs: List<ProxyLogEntry> = emptyList(),
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val filteredApps: List<InstalledAppInfo> = emptyList(),
    val selectedAppCount: Int = 0,
    val appSearchQuery: String = "",
    val isLoadingApps: Boolean = true,
    val testingProfileIds: Set<Long> = emptySet(),
    val perAppChangesPendingRestart: Boolean = false,
)

sealed interface UiEvent {
    data class ShowMessage(val message: String) : UiEvent
    data object RequestVpnStart : UiEvent
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val settingsStore = SettingsDataStore(application)
    private val repository = ProxyRepository(application, db.proxyDao(), settingsStore)

    private val rawApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    private val isLoadingApps = MutableStateFlow(true)
    private val appSearchQuery = MutableStateFlow("")
    private val testingProfileIds = MutableStateFlow<Set<Long>>(emptySet())
    private val perAppDirty = MutableStateFlow(false)

    private val _events = MutableSharedFlow<UiEvent>()
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    val uiState: StateFlow<MainUiState> = combine(
        repository.profilesFlow,
        repository.settingsFlow,
        repository.perAppRulesFlow,
        VpnStateHolder.telemetry,
        VpnStateHolder.logs,
    ) { profiles, settings, rules, telemetry, logs ->
        val selectedPkgSet = rules.map { it.packageName }.toSet()
        val selectedProfile = profiles.firstOrNull { it.id == settings.selectedProfileId }
            ?: profiles.firstOrNull()
        Triple(profiles, settings, selectedPkgSet) to Pair(selectedProfile, Pair(telemetry, logs))
    }.combine(
        combine(rawApps, appSearchQuery, isLoadingApps, testingProfileIds, perAppDirty) { apps, query, loading, testing, dirty ->
            listOf(apps, query, loading, testing, dirty)
        },
    ) { mainTuple, extraList ->
        val (profiles, settings, selectedPkgSet) = mainTuple.first
        val (selectedProfile, telemetryLogs) = mainTuple.second
        val (telemetry, logs) = telemetryLogs

        @Suppress("UNCHECKED_CAST")
        val baseApps = extraList[0] as List<InstalledAppInfo>
        val query = extraList[1] as String
        val loading = extraList[2] as Boolean
        @Suppress("UNCHECKED_CAST")
        val testing = extraList[3] as Set<Long>
        val dirty = extraList[4] as Boolean

        val syncedApps = baseApps.map { app ->
            app.copy(isSelected = app.packageName in selectedPkgSet)
        }
        val normalizedQuery = query.trim().lowercase()
        val filtered = syncedApps.filter { app ->
            val systemOk = settings.showSystemApps || !app.isSystemApp
            val searchOk = normalizedQuery.isEmpty() ||
                app.appName.lowercase().contains(normalizedQuery) ||
                app.packageName.lowercase().contains(normalizedQuery)
            systemOk && searchOk
        }

        val isTunnelActive = telemetry.state == ConnectionState.CONNECTED ||
            telemetry.state == ConnectionState.CONNECTING

        MainUiState(
            profiles = profiles,
            selectedProfile = selectedProfile,
            settings = settings,
            telemetry = telemetry,
            logs = logs,
            installedApps = syncedApps,
            filteredApps = filtered,
            selectedAppCount = selectedPkgSet.size,
            appSearchQuery = query,
            isLoadingApps = loading,
            testingProfileIds = testing,
            perAppChangesPendingRestart = dirty && isTunnelActive,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = MainUiState(),
    )

    init {
        refreshInstalledApps()
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            isLoadingApps.value = true
            val loaded = repository.loadInstalledApps(emptySet())
            rawApps.value = loaded
            isLoadingApps.value = false
        }
    }

    fun getAppIcon(packageName: String): Drawable? = repository.getAppIcon(packageName)

    fun selectProfile(profileId: Long) {
        viewModelScope.launch {
            repository.selectProfile(profileId)
            val state = uiState.value.telemetry.state
            if (state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING) {
                perAppDirty.value = true
            }
        }
    }

    fun saveProfile(
        editingId: Long?,
        name: String,
        protocol: ProxyProtocol,
        host: String,
        portText: String,
        username: String,
        password: String,
    ) {
        val cleanHost = host.trim()
        val port = portText.trim().toIntOrNull()
        if (cleanHost.isEmpty() || port == null || port !in 1..65535) {
            viewModelScope.launch {
                _events.emit(UiEvent.ShowMessage("آدرس سرور یا شماره پورت (۱ تا ۶۵۵۳۵) نامعتبر است"))
            }
            return
        }
        val cleanName = name.trim().ifEmpty { "$cleanHost:$port" }

        viewModelScope.launch {
            val entity = ProxyProfileEntity(
                id = editingId ?: 0L,
                name = cleanName,
                protocol = protocol,
                host = cleanHost,
                port = port,
                username = username.trim(),
                password = password,
            )
            val savedId = repository.saveProfile(entity)
            repository.selectProfile(savedId)
            VpnStateHolder.addLog(
                LogLevel.INFO,
                protocol.label,
                if (editingId == null) "سرور جدید ذخیره شد: $cleanName ($cleanHost:$port)"
                else "پروفایل ویرایش شد: $cleanName",
            )
            _events.emit(UiEvent.ShowMessage("سرور «$cleanName» ذخیره شد"))
        }
    }

    fun importProxyUri(rawUri: String) {
        ProxyUriParser.parse(rawUri).fold(
            onSuccess = { parsed ->
                saveProfile(
                    editingId = null,
                    name = parsed.name,
                    protocol = parsed.protocol,
                    host = parsed.host,
                    portText = parsed.port.toString(),
                    username = parsed.username,
                    password = parsed.password,
                )
            },
            onFailure = { err ->
                viewModelScope.launch {
                    _events.emit(UiEvent.ShowMessage(err.message ?: "فرمت لینک پروکسی نامعتبر است"))
                }
            },
        )
    }

    fun deleteProfile(profile: ProxyProfileEntity) {
        viewModelScope.launch {
            repository.deleteProfile(profile.id)
            VpnStateHolder.addLog(LogLevel.INFO, "Profile", "پروفایل «${profile.name}» حذف شد")
            _events.emit(UiEvent.ShowMessage("سرور «${profile.name}» حذف شد"))
        }
    }

    fun testProfile(profile: ProxyProfileEntity) {
        if (profile.id in testingProfileIds.value) return
        viewModelScope.launch {
            testingProfileIds.update { it + profile.id }
            val result = repository.testProfileLatency(profile)
            testingProfileIds.update { it - profile.id }
            result.fold(
                onSuccess = { ms ->
                    _events.emit(UiEvent.ShowMessage("اتصال موفق به ${profile.name} • پینگ: ${ms}ms"))
                },
                onFailure = { err ->
                    _events.emit(UiEvent.ShowMessage("خطا در تست ${profile.name}: ${err.message ?: "ناموفق"}"))
                },
            )
        }
    }

    fun updateAppSearchQuery(query: String) {
        appSearchQuery.value = query
    }

    fun setPerAppMode(mode: PerAppMode) {
        viewModelScope.launch {
            repository.setPerAppMode(mode)
            markPerAppDirtyIfRunning()
        }
    }

    fun setDnsMode(mode: DnsMode) {
        viewModelScope.launch {
            repository.setDnsMode(mode)
            markPerAppDirtyIfRunning()
        }
    }

    fun setShowSystemApps(show: Boolean) {
        viewModelScope.launch {
            repository.setShowSystemApps(show)
        }
    }

    fun toggleAppSelection(app: InstalledAppInfo) {
        viewModelScope.launch {
            repository.setAppSelected(
                packageName = app.packageName,
                appLabel = app.appName,
                selected = !app.isSelected,
            )
            markPerAppDirtyIfRunning()
        }
    }

    fun selectAllVisibleApps() {
        val visible = uiState.value.filteredApps
        val currentSelected = uiState.value.installedApps.filter { it.isSelected }
        val merged = (currentSelected + visible).distinctBy { it.packageName }
        viewModelScope.launch {
            repository.replaceSelectedApps(merged)
            markPerAppDirtyIfRunning()
        }
    }

    fun clearAllSelectedApps() {
        viewModelScope.launch {
            repository.replaceSelectedApps(emptyList())
            markPerAppDirtyIfRunning()
        }
    }

    private fun markPerAppDirtyIfRunning() {
        val state = uiState.value.telemetry.state
        if (state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING) {
            perAppDirty.value = true
        }
    }

    fun onToggleVpnClicked() {
        val snapshot = uiState.value
        when (snapshot.telemetry.state) {
            ConnectionState.CONNECTED, ConnectionState.CONNECTING -> {
                perAppDirty.value = false
                ProxyVpnService.stopVpn(getApplication())
            }
            ConnectionState.DISCONNECTED, ConnectionState.ERROR -> {
                val profile = snapshot.selectedProfile
                if (profile == null) {
                    viewModelScope.launch {
                        _events.emit(UiEvent.ShowMessage("ابتدا از بخش سرورها یک پروکسی اضافه و انتخاب کنید"))
                    }
                    return
                }
                if (snapshot.settings.perAppMode == PerAppMode.ALLOW_SELECTED && snapshot.selectedAppCount == 0) {
                    viewModelScope.launch {
                        _events.emit(UiEvent.ShowMessage("در حالت «فقط برنامه‌های منتخب» حداقل یک برنامه را انتخاب کنید"))
                    }
                    return
                }
                viewModelScope.launch {
                    repository.selectProfile(profile.id)
                    VpnStateHolder.clearError()
                    _events.emit(UiEvent.RequestVpnStart)
                }
            }
            ConnectionState.DISCONNECTING -> Unit
        }
    }

    fun onVpnPermissionGranted() {
        perAppDirty.value = false
        ProxyVpnService.startVpn(getApplication())
    }

    fun onVpnPermissionDenied() {
        VpnStateHolder.addLog(LogLevel.WARN, "VPN", "کاربر مجوز ساخت VPN را تایید نکرد")
        viewModelScope.launch {
            _events.emit(UiEvent.ShowMessage("برای راه‌اندازی پروکسی تایید مجوز VPN سیستم الزامی است"))
        }
    }

    fun restartVpn() {
        perAppDirty.value = false
        viewModelScope.launch {
            _events.emit(UiEvent.RequestVpnStart)
        }
    }

    fun clearLogs() {
        VpnStateHolder.clearLogs()
    }
}
