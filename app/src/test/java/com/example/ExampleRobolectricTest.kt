package com.example

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.importexport.BackupManager
import com.example.data.importexport.ExportManager
import com.example.data.importexport.ExternalDbInspector
import com.example.data.local.AccountingRepository
import com.example.data.local.SmartDebtDatabase
import com.example.data.preferences.SettingsDataStore
import com.example.ui.SmartDebtViewModel
import com.example.ui.screens.DashboardScreen
import com.example.ui.theme.SmartDebtTheme
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-xxhdpi")
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `app string resource is configured and dashboard renders cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertFalse(appName.isBlank())

        val app = context as Application
        val db = Room.inMemoryDatabaseBuilder(context, SmartDebtDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = AccountingRepository(db)
        val settingsStore = SettingsDataStore(context)
        val backupMgr = BackupManager(db, settingsStore)
        val inspector = ExternalDbInspector(db, backupMgr)
        val store = ViewModelStore()
        val vm = ViewModelProvider(
            store,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SmartDebtViewModel(app, repo, settingsStore, backupMgr, ExportManager(), inspector) as T
            },
        )[SmartDebtViewModel::class.java]

        try {
            composeRule.setContent {
                SmartDebtTheme {
                    DashboardScreen(viewModel = vm, onNavigate = {})
                }
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("dashboard_net_balance").assertIsDisplayed()
            composeRule.onNodeWithTag("quick_add_customer").performScrollTo().assertIsDisplayed()
        } finally {
            store.clear()
            db.close()
        }
    }
}
