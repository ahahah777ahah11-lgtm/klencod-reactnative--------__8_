package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.update.AppUpdateChecker
import com.example.data.update.AppUpdateState
import com.example.security.BiometricAuthHelper
import com.example.ui.SmartDebtViewModel
import com.example.ui.components.BiometricLockScreen
import com.example.ui.navigation.AppRoutes
import com.example.ui.navigation.SmartDebtNavGraph
import com.example.ui.theme.SmartDebtTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: SmartDebtViewModel = viewModel(
                factory = SmartDebtViewModel.Factory(application)
            )
            val settings by vm.settings.collectAsStateWithLifecycle()
            val isUnlocked by vm.isSessionUnlocked.collectAsStateWithLifecycle()
            val updateState by vm.updateState.collectAsStateWithLifecycle()
            var biometricError by remember { mutableStateOf<String?>(null) }

            val triggerBiometricPrompt = {
                runCatching {
                    BiometricAuthHelper.authenticate(
                        activity = this,
                        onSuccess = {
                            biometricError = null
                            vm.unlockSession()
                        },
                        onError = { err ->
                            biometricError = err
                        },
                    )
                }.onFailure { err ->
                    biometricError = err.message ?: "تعذر تشغيل التحقق بالبصمة"
                }
            }

            SmartDebtTheme(
                themeMode = settings.themeMode,
                colorPalette = settings.colorPalette,
                backgroundStyle = settings.backgroundStyle,
                iconShape = settings.iconShape,
            ) {
                if (settings.biometricLockEnabled && !isUnlocked) {
                    LaunchedEffect(Unit) {
                        triggerBiometricPrompt()
                    }
                    BiometricLockScreen(
                        storeName = settings.storeName,
                        errorMessage = biometricError,
                        onUnlockClick = { triggerBiometricPrompt() },
                    )
                } else {
                    SmartDebtAppRoot(viewModel = vm)
                    val availableUpdate = updateState as? AppUpdateState.UpdateAvailable
                    if (availableUpdate != null) {
                        AlertDialog(
                            onDismissRequest = { vm.dismissUpdatePrompt() },
                            title = { Text("يتوفر تحديث جديد للتطبيق (v${availableUpdate.latestVersionName})") },
                            text = { Text(availableUpdate.releaseNotes) },
                            confirmButton = {
                                if (availableUpdate.downloadUrl.isNotBlank()) {
                                    Button(
                                        onClick = {
                                            AppUpdateChecker.openDownloadUrl(this@MainActivity, availableUpdate.downloadUrl)
                                            vm.dismissUpdatePrompt()
                                        }
                                    ) {
                                        Text("تحديث الآن")
                                    }
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { vm.dismissUpdatePrompt() }) {
                                    Text("لاحقاً")
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private data class TopNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val testTag: String,
)

@Composable
fun SmartDebtAppRoot(
    viewModel: SmartDebtViewModel,
    startDestination: String = AppRoutes.DASHBOARD,
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val topDestinations = remember {
        listOf(
            TopNavItem(AppRoutes.DASHBOARD, "الرئيسية", Icons.Default.Home, "nav_dashboard"),
            TopNavItem(AppRoutes.CUSTOMERS, "العملاء", Icons.Default.People, "nav_customers"),
            TopNavItem(AppRoutes.ADD_TRANSACTION, "معاملة", Icons.Default.AddCard, "nav_add_transaction"),
            TopNavItem(AppRoutes.DEBTS_OVERVIEW, "الديون", Icons.Default.AccountBalanceWallet, "nav_debts_overview"),
            TopNavItem(AppRoutes.MORE_HUB, "المزيد", Icons.Default.Apps, "nav_more"),
        )
    }

    val topRoutesSet = remember {
        setOf(
            AppRoutes.DASHBOARD,
            AppRoutes.CUSTOMERS,
            AppRoutes.ADD_TRANSACTION,
            AppRoutes.DEBTS_OVERVIEW,
            AppRoutes.MORE_HUB,
        )
    }
    val showTopLevelNav = currentRoute in topRoutesSet

    LaunchedEffect(Unit) {
        viewModel.feedback.collect { fb ->
            snackbarHostState.showSnackbar(fb.message)
        }
    }

    fun navigateTop(route: String) {
        val target = if (route == AppRoutes.ADD_TRANSACTION) AppRoutes.addTransaction() else route
        navController.navigate(target) {
            popUpTo(startDestination) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            },
            bottomBar = {
                if (showTopLevelNav && !useRail) {
                    NavigationBar(windowInsets = WindowInsets.navigationBars) {
                        topDestinations.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTop(item.route) },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag(item.testTag),
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
                if (showTopLevelNav && useRail) {
                    NavigationRail {
                        topDestinations.forEach { item ->
                            NavigationRailItem(
                                selected = currentRoute == item.route,
                                onClick = { navigateTop(item.route) },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag(item.testTag),
                            )
                        }
                    }
                }

                SmartDebtNavGraph(
                    navController = navController,
                    viewModel = viewModel,
                    startDestination = startDestination,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
