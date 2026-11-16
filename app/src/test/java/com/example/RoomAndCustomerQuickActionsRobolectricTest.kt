package com.example

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.importexport.BackupManager
import com.example.data.importexport.ExportManager
import com.example.data.importexport.ExternalDbInspector
import kotlinx.coroutines.flow.first
import java.io.File
import com.example.data.local.AccountingRepository
import com.example.data.local.AccountingResult
import com.example.data.local.SmartDebtDatabase
import com.example.data.local.TransactionType
import com.example.data.preferences.SettingsDataStore
import com.example.ui.SmartDebtViewModel
import com.example.ui.navigation.AppRoutes
import com.example.ui.screens.CustomerDetailScreen
import com.example.ui.screens.CustomersScreen
import com.example.ui.theme.SmartDebtTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomAndCustomerQuickActionsRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun newInMemoryDb(context: Context): SmartDebtDatabase =
        Room.inMemoryDatabaseBuilder(context, SmartDebtDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    @Test
    fun `customer debt and payment transactions update relational balances accurately`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = newInMemoryDb(context)
        val repo = AccountingRepository(db)
        try {
            val createRes = repo.createCustomer("طارق الحمادي", "773344556", "ACC-Q1", 0.0, "", 1000.0)
            assertTrue(createRes is AccountingResult.Success<*>)
            val customerId = (createRes as AccountingResult.Success<Long>).data

            assertTrue(
                repo.recordTransaction(
                    customerId = customerId,
                    type = TransactionType.DEBT,
                    amount = 2500.0,
                    description = "فاتورة إضافية",
                    referenceNumber = "D-101",
                ) is AccountingResult.Success<*>
            )
            assertTrue(
                repo.recordTransaction(
                    customerId = customerId,
                    type = TransactionType.PAYMENT,
                    amount = 1200.0,
                    description = "سداد جزئي",
                    referenceNumber = "P-101",
                ) is AccountingResult.Success<*>
            )

            val rel = repo.getCustomerWithDebtsAndPayments(customerId)
            assertNotNull(rel)
            assertEquals("طارق الحمادي", rel!!.customer.name)
            assertEquals(2, rel.debts.size)
            assertEquals(1, rel.payments.size)
        } finally {
            db.close()
        }
    }

    @Test
    fun `customers screen quick add dialog creates and displays new customer`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = newInMemoryDb(app)
        val repo = AccountingRepository(db)
        val settingsStore = SettingsDataStore(app)
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
                    CustomersScreen(viewModel = vm, onNavigate = {})
                }
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("quick_add_customer_button").assertIsDisplayed().performClick()
            composeRule.onNodeWithTag("quick_customer_name_input").performTextReplacement("سمير الوصابي")
            composeRule.onNodeWithTag("quick_customer_phone_input").performTextReplacement("779911223")
            composeRule.onNodeWithTag("quick_customer_save_button").performClick()

            composeRule.waitUntil(timeoutMillis = 5000) {
                composeRule.onAllNodesWithTag("quick_customer_save_button").fetchSemanticsNodes().isEmpty() &&
                    composeRule.onAllNodesWithTag("customers_list").fetchSemanticsNodes().isNotEmpty() &&
                    vm.customers.value.any { it.name == "سمير الوصابي" }
            }
            composeRule.onNodeWithTag("customers_list").performScrollToNode(hasText("سمير الوصابي"))
            composeRule.waitForIdle()
            composeRule.onNodeWithText("سمير الوصابي").assertIsDisplayed()
        } finally {
            store.clear()
            db.close()
        }
    }

    @Test
    fun `customers list add customer and navigate to customer detail screen displays room data`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = newInMemoryDb(app)
        val repo = AccountingRepository(db)
        val settingsStore = SettingsDataStore(app)
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
                    var selectedCustomerId by remember { mutableStateOf<Long?>(null) }
                    val detailId = selectedCustomerId
                    if (detailId == null) {
                        CustomersScreen(
                            viewModel = vm,
                            onNavigate = { route ->
                                if (route.startsWith("customer_detail/")) {
                                    selectedCustomerId = route.removePrefix("customer_detail/").toLongOrNull()
                                }
                            },
                        )
                    } else {
                        CustomerDetailScreen(
                            customerId = detailId,
                            viewModel = vm,
                            onBack = { selectedCustomerId = null },
                            onNavigate = {},
                        )
                    }
                }
            }
            composeRule.waitForIdle()

            // Add a customer with an opening balance into Room via Compose UI
            composeRule.onNodeWithTag("quick_add_customer_button").assertIsDisplayed().performClick()
            composeRule.onNodeWithTag("quick_customer_name_input").performTextReplacement("نبيل الشامي")
            composeRule.onNodeWithTag("quick_customer_phone_input").performTextReplacement("774455667")
            composeRule.onNodeWithTag("quick_customer_opening_input").performTextReplacement("3500")
            composeRule.onNodeWithTag("quick_customer_save_button").performClick()

            composeRule.waitUntil(timeoutMillis = 5000) {
                composeRule.onAllNodesWithTag("quick_customer_save_button").fetchSemanticsNodes().isEmpty() &&
                    composeRule.onAllNodesWithTag("customers_list").fetchSemanticsNodes().isNotEmpty() &&
                    vm.customers.value.any { it.name == "نبيل الشامي" }
            }

            // Click the customer card details button to navigate to CustomerDetailScreen
            val insertedId = vm.customers.value.first { it.name == "نبيل الشامي" }.id
            composeRule.onNodeWithTag("customers_list").performScrollToIndex(1)
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("customer_details_btn_$insertedId").assertIsDisplayed().performClick()

            composeRule.waitUntil(timeoutMillis = 5000) {
                composeRule.onAllNodesWithTag("customer_detail_balance").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("customer_detail_balance")
                .assertIsDisplayed()
                .assertTextContains("3,500", substring = true)
            composeRule.onNodeWithText("نبيل الشامي").assertIsDisplayed()
            composeRule.onNodeWithTag("customer_detail_list")
                .performScrollToNode(hasText("الهاتف: 774455667"))
            composeRule.onNodeWithText("الهاتف: 774455667").assertIsDisplayed()
        } finally {
            store.clear()
            db.close()
        }
    }

    @Test
    fun `customers screen search filters by name and phone and summary card displays debt vs collected`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = newInMemoryDb(app)
        val repo = AccountingRepository(db)
        val settingsStore = SettingsDataStore(app)
        val backupMgr = BackupManager(db, settingsStore)
        val inspector = ExternalDbInspector(db, backupMgr)
        val store = ViewModelStore()

        runBlocking {
            val c1 = (repo.createCustomer("خالد المقطري", "771112223", "ACC-S1", 0.0, "") as AccountingResult.Success<Long>).data
            repo.recordTransaction(c1, TransactionType.DEBT, 4000.0, "فاتورة بضاعة")
            repo.recordTransaction(c1, TransactionType.PAYMENT, 1500.0, "دفعة أولى")

            val c2 = (repo.createCustomer("سارة العبسي", "735556667", "ACC-S2", 0.0, "") as AccountingResult.Success<Long>).data
            repo.recordTransaction(c2, TransactionType.DEBT, 2000.0, "مشتريات آجل")
        }

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
                    CustomersScreen(viewModel = vm, onNavigate = {})
                }
            }

            composeRule.waitUntil(timeoutMillis = 5000) {
                composeRule.onAllNodesWithTag("customers_summary_card").fetchSemanticsNodes().isNotEmpty() &&
                    vm.customers.value.size == 2
            }
            composeRule.waitForIdle()

            // Verify summary card displays total debt (6,000) vs total collected payments (1,500)
            composeRule.onNodeWithTag("customers_summary_card").assertIsDisplayed()
            composeRule.onNodeWithTag("summary_total_debts_text").assertTextContains("6,000", substring = true)
            composeRule.onNodeWithTag("summary_total_collected_text").assertTextContains("1,500", substring = true)
            composeRule.onNodeWithTag("customers_export_csv_button").assertIsDisplayed()
            composeRule.onNodeWithTag("customers_import_csv_button").assertIsDisplayed()

            // Filter by customer name
            composeRule.onNodeWithTag("customers_search_input").performTextReplacement("خالد")
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("customers_list").performScrollToNode(hasText("خالد المقطري"))
            composeRule.onNodeWithText("خالد المقطري").assertIsDisplayed()
            assertEquals(0, composeRule.onAllNodesWithText("سارة العبسي").fetchSemanticsNodes().size)

            // Filter by customer phone number
            composeRule.onNodeWithTag("customers_search_input").performTextReplacement("73555")
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("customers_list").performScrollToNode(hasText("سارة العبسي"))
            composeRule.onNodeWithText("سارة العبسي").assertIsDisplayed()
            assertEquals(0, composeRule.onAllNodesWithText("خالد المقطري").fetchSemanticsNodes().size)
        } finally {
            store.clear()
            db.close()
        }
    }

    @Test
    fun `customers database csv export and reimport round trip preserves records and balances`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sourceDb = newInMemoryDb(context)
        val sourceRepo = AccountingRepository(sourceDb)
        val exportMgr = ExportManager()

        val targetDb = newInMemoryDb(context)
        val targetSettings = SettingsDataStore(context)
        val targetBackup = BackupManager(targetDb, targetSettings)
        val targetInspector = ExternalDbInspector(targetDb, targetBackup)
        val targetRepo = AccountingRepository(targetDb)
        val csvFile = File(context.cacheDir, "portability_roundtrip_test.csv")

        try {
            val c1 = (sourceRepo.createCustomer("مؤسسة الفجر", "778899001", "ACC-P1", 0.0, "عميل تصدير") as AccountingResult.Success<Long>).data
            sourceRepo.recordTransaction(c1, TransactionType.DEBT, 5000.0, "بضاعة")
            sourceRepo.recordTransaction(c1, TransactionType.PAYMENT, 2000.0, "سداد")

            val exportedCustomers = sourceRepo.observeCustomersWithBalance().first()
            csvFile.writeBytes(exportMgr.exportCustomersCsvBytes(exportedCustomers))

            val inspectRes = targetInspector.inspectRawFile(csvFile, context)
            assertTrue(inspectRes.isSuccess)
            val inspection = inspectRes.getOrThrow()
            val report = targetInspector.executeImport(
                context = context,
                inspection = inspection,
                createSafetyBackup = false,
                replacePreviousData = false,
            )
            assertEquals(1, report.importedCustomersCount)

            val restored = targetRepo.observeCustomersWithBalance().first()
            assertEquals(1, restored.size)
            assertEquals("مؤسسة الفجر", restored.first().name)
            assertEquals("778899001", restored.first().phone)
            assertEquals(5000.0, restored.first().totalDebts, 0.01)
            assertEquals(2000.0, restored.first().totalPayments, 0.01)
            assertEquals(3000.0, restored.first().balance, 0.01)
        } finally {
            csvFile.delete()
            sourceDb.close()
            targetDb.close()
        }
    }
}
