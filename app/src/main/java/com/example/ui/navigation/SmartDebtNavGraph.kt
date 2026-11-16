package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.ui.SmartDebtViewModel
import com.example.ui.screens.AccountStatementScreen
import com.example.ui.screens.AddDebtScreen
import com.example.ui.screens.AddEditCustomerScreen
import com.example.ui.screens.AddPaymentScreen
import com.example.ui.screens.AddTransactionScreen
import com.example.ui.screens.BackupRestoreScreen
import com.example.ui.screens.CustomerDetailScreen
import com.example.ui.screens.CustomersScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DebtsOverviewScreen
import com.example.ui.screens.ExportDataScreen
import com.example.ui.screens.ImportDataScreen
import com.example.ui.screens.LocalAssistantScreen
import com.example.ui.screens.MoreHubScreen
import com.example.ui.screens.RemindersScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SmsCenterScreen
import com.example.ui.screens.SmsTemplatesScreen
import com.example.ui.screens.TransactionsLogScreen
import com.example.ui.screens.TransfersScreen

@Composable
fun SmartDebtNavGraph(
    navController: NavHostController,
    viewModel: SmartDebtViewModel,
    startDestination: String = AppRoutes.DASHBOARD,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(AppRoutes.DASHBOARD) {
            DashboardScreen(
                viewModel = viewModel,
                onNavigate = { navController.navigate(it) },
            )
        }
        composable(AppRoutes.CUSTOMERS) {
            CustomersScreen(
                viewModel = viewModel,
                onNavigate = { navController.navigate(it) },
            )
        }
        composable(
            route = AppRoutes.ADD_TRANSACTION,
            arguments = listOf(
                navArgument("customerId") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
                navArgument("initialType") {
                    type = NavType.StringType
                    defaultValue = "DEBT"
                },
            ),
        ) { entry ->
            val cid = entry.arguments?.getLong("customerId")?.takeIf { it > 0L }
            val initialType = entry.arguments?.getString("initialType")
            AddTransactionScreen(
                preselectedCustomerId = cid,
                initialTypeName = initialType,
                viewModel = viewModel,
                canNavigateBack = navController.previousBackStackEntry != null,
                onBack = { navController.popBackStack() },
                onNavigate = { route ->
                    navController.navigate(route) {
                        launchSingleTop = true
                    }
                },
                onSaved = {
                    if (cid != null && navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    } else {
                        navController.navigate(AppRoutes.DEBTS_OVERVIEW) {
                            launchSingleTop = true
                        }
                    }
                },
            )
        }
        composable(AppRoutes.DEBTS_OVERVIEW) {
            DebtsOverviewScreen(
                viewModel = viewModel,
                canNavigateBack = false,
                onBack = { navController.popBackStack() },
                onNavigate = { navController.navigate(it) },
            )
        }
        composable(
            route = AppRoutes.ADD_EDIT_CUSTOMER,
            arguments = listOf(
                navArgument("customerId") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) { entry ->
            val cid = entry.arguments?.getLong("customerId")?.takeIf { it > 0L }
            AddEditCustomerScreen(
                customerId = cid,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onSaved = { savedId ->
                    navController.popBackStack()
                    if (cid == null) {
                        navController.navigate(AppRoutes.customerDetail(savedId))
                    }
                },
            )
        }
        composable(
            route = AppRoutes.CUSTOMER_DETAIL,
            arguments = listOf(navArgument("customerId") { type = NavType.LongType }),
        ) { entry ->
            val cid = entry.arguments?.getLong("customerId") ?: 0L
            CustomerDetailScreen(
                customerId = cid,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNavigate = { navController.navigate(it) },
            )
        }
        composable(
            route = AppRoutes.ACCOUNT_STATEMENT,
            arguments = listOf(navArgument("customerId") { type = NavType.LongType }),
        ) { entry ->
            val cid = entry.arguments?.getLong("customerId") ?: 0L
            AccountStatementScreen(
                customerId = cid,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = AppRoutes.ADD_DEBT,
            arguments = listOf(
                navArgument("customerId") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) { entry ->
            val cid = entry.arguments?.getLong("customerId")?.takeIf { it > 0L }
            AddDebtScreen(
                preselectedCustomerId = cid,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
        composable(
            route = AppRoutes.ADD_PAYMENT,
            arguments = listOf(
                navArgument("customerId") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) { entry ->
            val cid = entry.arguments?.getLong("customerId")?.takeIf { it > 0L }
            AddPaymentScreen(
                preselectedCustomerId = cid,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
        composable(AppRoutes.TRANSACTIONS_LOG) {
            TransactionsLogScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(AppRoutes.REMINDERS) {
            RemindersScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNavigate = { navController.navigate(it) },
            )
        }
        composable(AppRoutes.TRANSFERS) {
            TransfersScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = AppRoutes.SMS_CENTER,
            arguments = listOf(
                navArgument("customerId") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) { entry ->
            val cid = entry.arguments?.getLong("customerId")?.takeIf { it > 0L }
            SmsCenterScreen(
                preselectedCustomerId = cid,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNavigate = { navController.navigate(it) },
            )
        }
        composable(AppRoutes.SMS_TEMPLATES) {
            SmsTemplatesScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(AppRoutes.REPORTS) {
            ReportsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNavigate = { navController.navigate(it) },
            )
        }
        composable(AppRoutes.IMPORT_DATA) {
            ImportDataScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(AppRoutes.EXPORT_DATA) {
            ExportDataScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(AppRoutes.BACKUP_RESTORE) {
            BackupRestoreScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(AppRoutes.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(AppRoutes.LOCAL_ASSISTANT) {
            LocalAssistantScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNavigate = { navController.navigate(it) },
            )
        }
        composable(AppRoutes.MORE_HUB) {
            MoreHubScreen(onNavigate = { navController.navigate(it) })
        }
    }
}
