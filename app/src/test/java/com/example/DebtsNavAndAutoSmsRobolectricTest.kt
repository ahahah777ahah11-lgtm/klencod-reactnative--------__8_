package com.example

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import com.example.data.local.AccountingRepository
import com.example.data.local.AccountingResult
import com.example.data.local.CustomerWithBalance
import com.example.data.local.DebtsEntity
import com.example.data.local.MessageLogEntity
import com.example.data.local.MessageStatus
import com.example.data.local.PaymentsEntity
import com.example.data.local.SmartDebtDatabase
import com.example.data.local.TransactionType
import com.example.data.preferences.AppSettings
import com.example.data.preferences.SettingsDataStore
import com.example.data.sms.AutoReminderItemStatus
import com.example.data.sms.DirectSmsSender
import com.example.data.sms.SmsGateway
import com.example.ui.SmartDebtViewModel
import com.example.ui.navigation.AppRoutes
import com.example.ui.theme.SmartDebtTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DebtsNavAndAutoSmsRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun newInMemoryDb(context: Context): SmartDebtDatabase =
        Room.inMemoryDatabaseBuilder(context, SmartDebtDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    @Test
    fun `room customers debts and payments entities persist update and relate cleanly`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = newInMemoryDb(context)
        val repo = AccountingRepository(db)
        try {
            val res = repo.createCustomer("شركة النور", "771234567", "ACC-D1", 0.0, "")
            val customerId = (res as AccountingResult.Success<Long>).data
            val now = System.currentTimeMillis()

            val debtTx = repo.recordTransaction(
                customerId = customerId,
                type = TransactionType.DEBT,
                amount = 4500.0,
                description = "بضاعة آجل مستحقة",
                transactionDate = now,
                dueDate = now - 3_600_000L,
            )
            assertTrue(debtTx is AccountingResult.Success<*>)

            val payTx = repo.recordTransaction(
                customerId = customerId,
                type = TransactionType.PAYMENT,
                amount = 1500.0,
                description = "دفعة أولى",
                transactionDate = now,
            )
            assertTrue(payTx is AccountingResult.Success<*>)

            val debts = db.debtsDao().observeAllDebts().first()
            val payments = db.paymentsDao().observeAllPayments().first()
            assertEquals(1, debts.size)
            assertEquals(4500.0, debts.first().amount, 0.01)
            assertNotNull(debts.first().dueDate)

            assertEquals(1, payments.size)
            assertEquals(1500.0, payments.first().amount, 0.01)

            // Verify PaymentsDao CRUD update & getById
            val updatedRows = db.paymentsDao().updatePayment(
                payments.first().copy(description = "دفعة معدلة")
            )
            assertEquals(1, updatedRows)
            assertEquals("دفعة معدلة", db.paymentsDao().getPaymentById(payments.first().id)?.description)

            // Verify relational model joins Customer + Debts + Payments
            val combined = repo.getCustomerWithDebtsAndPayments(customerId)
            assertNotNull(combined)
            assertEquals("شركة النور", combined!!.customer.name)
            assertEquals(1, combined.debts.size)
            assertEquals(1, combined.payments.size)

            // Verify isolated PaymentsDao insert & delete
            val extraPayId = db.paymentsDao().insertPayment(
                PaymentsEntity(customerId = customerId, amount = 100.0, description = "اختبار حذف")
            )
            val insertedExtra = db.paymentsDao().getPaymentById(extraPayId)
            assertNotNull(insertedExtra)
            db.paymentsDao().deletePayment(insertedExtra!!)
            assertNull(db.paymentsDao().getPaymentById(extraPayId))
        } finally {
            db.close()
        }
    }

    @Test
    fun `automatic due debt sms reminder sends to due debtors and skips settled no phone and cooldown`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val now = 1_750_000_000_000L
        val settings = AppSettings(storeName = "متجر الأمل", contactPhone = "770001111", currencySymbol = "ر.ي", overdueDaysThreshold = 30)

        val customers = listOf(
            CustomerWithBalance(
                id = 1L, name = "عميل مستحق", phone = "771112223", accountNumber = "A1",
                notes = "", creditLimit = 0.0, createdAt = now - 500000L, updatedAt = now,
                isArchived = false, totalDebts = 3000.0, totalPayments = 1000.0, balance = 2000.0,
                transactionCount = 2, lastTransactionDate = now - 86_400_000L, overdueRemindersCount = 0,
            ),
            CustomerWithBalance(
                id = 2L, name = "عميل مسدد", phone = "772223334", accountNumber = "A2",
                notes = "", creditLimit = 0.0, createdAt = now, updatedAt = now,
                isArchived = false, totalDebts = 1000.0, totalPayments = 1000.0, balance = 0.0,
                transactionCount = 2, lastTransactionDate = now - 86_400_000L, overdueRemindersCount = 1,
            ),
            CustomerWithBalance(
                id = 3L, name = "عميل بدون هاتف", phone = "", accountNumber = "A3",
                notes = "", creditLimit = 0.0, createdAt = now, updatedAt = now,
                isArchived = false, totalDebts = 500.0, totalPayments = 0.0, balance = 500.0,
                transactionCount = 1, lastTransactionDate = now - 86_400_000L, overdueRemindersCount = 1,
            ),
            CustomerWithBalance(
                id = 4L, name = "عميل تم تذكيره", phone = "773334445", accountNumber = "A4",
                notes = "", creditLimit = 0.0, createdAt = now, updatedAt = now,
                isArchived = false, totalDebts = 800.0, totalPayments = 0.0, balance = 800.0,
                transactionCount = 1, lastTransactionDate = now - 40L * 86_400_000L, overdueRemindersCount = 0,
            ),
        )
        val debts = listOf(
            DebtsEntity(id = 10L, customerId = 1L, amount = 3000.0, description = "فاتورة مستحقة", dueDate = now - 60_000L),
        )
        val recentLogs = listOf(
            MessageLogEntity(
                id = 99L,
                customerId = 4L,
                customerName = "عميل تم تذكيره",
                phone = "773334445",
                messageBody = "تذكير سابق",
                templateName = "تذكير تلقائي",
                status = MessageStatus.SENT_VIA_SMS_MANAGER,
                createdAt = now - 3_600_000L,
            )
        )

        val sentPayloads = mutableListOf<Pair<String, String>>()
        val fakeGateway = object : SmsGateway {
            override fun sendSms(
                context: Context,
                destination: String,
                messageBody: String,
                logId: Long,
            ): Int {
                sentPayloads += destination to messageBody
                return 1
            }
        }

        // First verify PermissionDenied when SEND_SMS is not granted
        shadowOf(app).denyPermissions(Manifest.permission.SEND_SMS)
        val deniedReport = DirectSmsSender.sendAutomaticDueDebtReminders(
            context = app,
            customers = customers,
            debts = debts,
            reminders = emptyList(),
            recentMessageLogs = recentLogs,
            settings = settings,
            nowMillis = now,
            gateway = fakeGateway,
        )
        assertTrue(deniedReport.permissionDenied)
        assertEquals(0, sentPayloads.size)

        // Grant SEND_SMS and verify selective dispatch
        shadowOf(app).grantPermissions(Manifest.permission.SEND_SMS)
        val report = DirectSmsSender.sendAutomaticDueDebtReminders(
            context = app,
            customers = customers,
            debts = debts,
            reminders = emptyList(),
            recentMessageLogs = recentLogs,
            settings = settings,
            nowMillis = now,
            gateway = fakeGateway,
        )
        assertFalse(report.permissionDenied)
        assertEquals(3, report.evaluatedDueCustomersCount)
        assertEquals(1, report.sentCount)
        assertEquals(1, report.skippedNoPhoneCount)
        assertEquals(1, report.skippedCooldownCount)
        assertEquals(0, report.failedCount)

        assertEquals(1, sentPayloads.size)
        assertEquals("771112223", sentPayloads.first().first)
        assertTrue(sentPayloads.first().second.contains("عميل مستحق"))
        assertTrue(sentPayloads.first().second.contains("2,000"))
        assertEquals(AutoReminderItemStatus.SENT, report.items.first { it.customerId == 1L }.status)
    }

    @Test
    fun `navigation compose switches cleanly between customer list add transaction and debts overview`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = newInMemoryDb(app)
        val repo = AccountingRepository(db)
        val settingsStore = SettingsDataStore(app)
        val backupMgr = BackupManager(db, settingsStore)
        val inspector = ExternalDbInspector(db, backupMgr)
        val store = ViewModelStore()

        runBlocking {
            repo.createCustomer("فهد العواضي", "775551112", "ACC-N1", 0.0, "")
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
                    SmartDebtAppRoot(
                        viewModel = vm,
                        startDestination = AppRoutes.CUSTOMERS,
                    )
                }
            }

            // 1. Starts on Customer List
            composeRule.waitUntil(timeoutMillis = 5000) {
                composeRule.onAllNodesWithTag("customers_list").fetchSemanticsNodes().isNotEmpty() &&
                    vm.customers.value.any { it.name == "فهد العواضي" }
            }
            composeRule.onNodeWithTag("customers_list").performScrollToNode(hasText("فهد العواضي"))
            composeRule.onNodeWithText("فهد العواضي").assertIsDisplayed()

            // 2. Switch to Add Transaction via Navigation Compose bottom bar
            composeRule.onNodeWithTag("nav_add_transaction").assertIsDisplayed().performClick()
            composeRule.waitUntil(timeoutMillis = 5000) {
                composeRule.onAllNodesWithTag("customer_selector_field").fetchSemanticsNodes().isNotEmpty()
            }

            // Select customer and add a debt transaction due today
            composeRule.onNodeWithTag("customer_selector_field").performScrollTo().performClick()
            composeRule.waitUntil(timeoutMillis = 3000) {
                composeRule.onAllNodesWithText("فهد العواضي", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("فهد العواضي", substring = true).performClick()
            composeRule.onNodeWithTag("add_transaction_amount_input").performScrollTo().performTextReplacement("2400")
            composeRule.onNodeWithTag("add_transaction_description_input").performScrollTo().performTextReplacement("فاتورة جملة")
            composeRule.onNodeWithTag("due_preset_DUE_TODAY").performScrollTo().performClick()
            composeRule.onNodeWithTag("submit_transaction_button").performScrollTo().performClick()

            // 3. After saving, verifies Navigation switches to Debts Overview with live totals & due reminder card
            composeRule.waitUntil(timeoutMillis = 5000) {
                composeRule.onAllNodesWithTag("debts_overview_list").fetchSemanticsNodes().isNotEmpty() &&
                    vm.debts.value.isNotEmpty() &&
                    vm.dashboardSummary.value.totalDebts >= 2400.0
            }
            composeRule.onNodeWithTag("overview_total_debts").assertIsDisplayed()
            assertTrue(composeRule.onAllNodesWithText("2,400", substring = true).fetchSemanticsNodes().isNotEmpty())
            composeRule.onNodeWithTag("debts_overview_list").performScrollToNode(hasTestTag("auto_due_sms_card"))
            composeRule.onNodeWithTag("auto_due_sms_card").assertIsDisplayed()
            composeRule.onNodeWithTag("debts_overview_list").performScrollToNode(hasTestTag("send_auto_due_sms_button"))
            composeRule.onNodeWithTag("send_auto_due_sms_button").assertIsDisplayed()

            // 4. Switch back to Customer List via Navigation Bar and confirm balance updated
            composeRule.onNodeWithTag("nav_customers").performClick()
            composeRule.waitUntil(timeoutMillis = 5000) {
                composeRule.onAllNodesWithTag("customers_list").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("summary_total_debts_text").assertTextContains("2,400", substring = true)
        } finally {
            store.clear()
            db.close()
        }
    }
}
