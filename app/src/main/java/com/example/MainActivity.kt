package com.example

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.model.ConnectionState
import com.example.ui.MainViewModel
import com.example.ui.UiEvent
import com.example.ui.navigation.ProxyDestination
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LogsScreen
import com.example.ui.screens.PerAppScreen
import com.example.ui.screens.ProfilesScreen
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.theme.ProxyGateTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                ProxyGateTheme {
                    ProxyGateApp()
                }
            }
        }
    }
}

private data class NavTabItem(
    val destination: ProxyDestination,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxyGateApp(
    viewModel: MainViewModel = viewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onVpnPermissionGranted()
        } else {
            viewModel.onVpnPermissionDenied()
        }
    }

    val launchVpnFlow = remember(context) {
        {
            val prepareIntent = VpnService.prepare(context)
            if (prepareIntent == null) {
                viewModel.onVpnPermissionGranted()
            } else {
                vpnPermissionLauncher.launch(prepareIntent)
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ ->
        launchVpnFlow()
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                UiEvent.RequestVpnStart -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        launchVpnFlow()
                    }
                }
            }
        }
    }

    val tabs = remember {
        listOf(
            NavTabItem(
                destination = ProxyDestination.Dashboard,
                labelRes = R.string.nav_dashboard,
                selectedIcon = Icons.Rounded.Shield,
                unselectedIcon = Icons.Outlined.Shield,
                testTag = "nav_tab_dashboard",
            ),
            NavTabItem(
                destination = ProxyDestination.Profiles,
                labelRes = R.string.nav_profiles,
                selectedIcon = Icons.Rounded.Dns,
                unselectedIcon = Icons.Outlined.Dns,
                testTag = "nav_tab_profiles",
            ),
            NavTabItem(
                destination = ProxyDestination.PerApp,
                labelRes = R.string.nav_per_app,
                selectedIcon = Icons.Rounded.Apps,
                unselectedIcon = Icons.Outlined.Apps,
                testTag = "nav_tab_per_app",
            ),
            NavTabItem(
                destination = ProxyDestination.Logs,
                labelRes = R.string.nav_logs,
                selectedIcon = Icons.Rounded.Terminal,
                unselectedIcon = Icons.Outlined.Terminal,
                testTag = "nav_tab_logs",
            ),
        )
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val navigateToTab: (ProxyDestination) -> Unit = { dest ->
        navController.navigate(dest) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            if (uiState.telemetry.state == ConnectionState.CONNECTED) {
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = EmeraldPrimary.copy(alpha = 0.16f),
                                ) {
                                    Text(
                                        text = "VPN",
                                        style = MonoCodeStyle.copy(color = EmeraldPrimary),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp,
                    ) {
                        tabs.forEach { tab ->
                            val selected = currentDestination?.hasRoute(tab.destination::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.destination) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes),
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        Spacer(Modifier.size(12.dp))
                        tabs.forEach { tab ->
                            val selected = currentDestination?.hasRoute(tab.destination::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.destination) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes),
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag),
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    NavHost(
                        navController = navController,
                        startDestination = ProxyDestination.Dashboard,
                    ) {
                        composable<ProxyDestination.Dashboard> {
                            DashboardScreen(
                                uiState = uiState,
                                onToggleVpn = viewModel::onToggleVpnClicked,
                                onTestSelectedProfile = {
                                    uiState.selectedProfile?.let(viewModel::testProfile)
                                },
                                onOpenProfiles = { navigateToTab(ProxyDestination.Profiles) },
                                onOpenPerApp = { navigateToTab(ProxyDestination.PerApp) },
                                onChangeDnsMode = viewModel::setDnsMode,
                                onRestartVpn = viewModel::restartVpn,
                            )
                        }
                        composable<ProxyDestination.Profiles> {
                            ProfilesScreen(
                                uiState = uiState,
                                onSelectProfile = viewModel::selectProfile,
                                onSaveProfile = viewModel::saveProfile,
                                onImportUri = viewModel::importProxyUri,
                                onDeleteProfile = viewModel::deleteProfile,
                                onTestProfile = viewModel::testProfile,
                            )
                        }
                        composable<ProxyDestination.PerApp> {
                            PerAppScreen(
                                uiState = uiState,
                                appIconProvider = viewModel::getAppIcon,
                                onChangeMode = viewModel::setPerAppMode,
                                onSearchQueryChange = viewModel::updateAppSearchQuery,
                                onToggleShowSystemApps = viewModel::setShowSystemApps,
                                onToggleApp = viewModel::toggleAppSelection,
                                onSelectAllVisible = viewModel::selectAllVisibleApps,
                                onClearSelection = viewModel::clearAllSelectedApps,
                                onRestartVpn = viewModel::restartVpn,
                            )
                        }
                        composable<ProxyDestination.Logs> {
                            LogsScreen(
                                logs = uiState.logs,
                                onClearLogs = viewModel::clearLogs,
                            )
                        }
                    }
                }
            }
        }
    }
}
