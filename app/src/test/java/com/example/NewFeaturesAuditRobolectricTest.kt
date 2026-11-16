package com.example

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.Customers
import com.example.data.local.Debts
import com.example.data.sms.DirectSmsSender
import com.example.data.sms.SmsDispatchResult
import com.example.data.sms.SmsGateway
import com.example.ui.screens.CustomersScreen
import com.example.ui.screens.ImportDataScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.SmartDebtTheme
import com.example.data.ai.AiImportColumnProcessor
import com.example.data.importexport.BackupManager
import com.example.data.importexport.ColumnMapping
import com.example.data.importexport.DiscoveredTable
import com.example.data.importexport.ExportManager
import com.example.data.importexport.ExternalDbInspector
import com.example.data.importexport.PaymentReceiptPdfGenerator
import com.example.data.local.AccountingRepository
import com.example.data.local.CustomerWithBalance
import com.example.data.local.SmartDebtDatabase
import com.example.data.local.TransactionType
import com.example.data.local.TransactionWithCustomer
import com.example.data.preferences.AppSettings
import com.example.data.preferences.SettingsDataStore
import com.example.security.AppCapabilityKind
import com.example.security.AppPermissionsManager
import com.example.ui.SmartDebtViewModel
import com.example.util.ErrorLogger
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
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NewFeaturesAuditRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun newInMemoryDb(context: Context): SmartDebtDatabase =
        Room.inMemoryDatabaseBuilder(context, SmartDebtDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    @Test
    fun `validateDatabaseIntegrity verifies valid WAL and SHM database without altering original files`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = newInMemoryDb(context)
        val backupMgr = BackupManager(db, SettingsDataStore(context))

        val walFile = File(context.cacheDir, "integrity_wal_test.db").apply { delete() }
        val walSidecar = File("${walFile.absolutePath}-wal").apply { delete() }
        val shmSidecar = File("${walFile.absolutePath}-shm").apply { delete() }

        val extDb = SQLiteDatabase.openOrCreateDatabase(walFile, null)
        extDb.enableWriteAheadLogging()
        extDb.execSQL("CREATE TABLE clients (id INTEGER PRIMARY KEY, name TEXT, balance REAL)")
        extDb.execSQL("INSERT INTO clients (name, balance) VALUES ('حسين العامري', 2750.0)")

        val rawBefore = walFile.readBytes()
        val walBefore = if (walSidecar.exists()) walSidecar.readBytes() else ByteArray(0)

        val report = backupMgr.validateDatabaseIntegrity(context, walFile)

        val rawAfter = walFile.readBytes()
        val walAfter = if (walSidecar.exists()) walSidecar.readBytes() else ByteArray(0)
        extDb.close()

        assertTrue("Expected valid WAL database integrity: ${report.errorMessage}", report.isValid)
        assertTrue(report.quickCheckPassed)
        assertTrue(report.tablesCount >= 1)
        assertTrue(rawBefore.contentEquals(rawAfter))
        assertTrue(walBefore.contentEquals(walAfter))

        db.close()
        walFile.delete()
        walSidecar.delete()
        shmSidecar.delete()
    }

    @Test
    fun `validateDatabaseIntegrity rejects corrupt or encrypted SQLite files and logs to ErrorLogger`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ErrorLogger.clearErrorLogs(context)
        val db = newInMemoryDb(context)
        val backupMgr = BackupManager(db, SettingsDataStore(context))

        val corruptFile = File(context.cacheDir, "integrity_corrupt.db").apply {
            writeBytes("NOT A SQLITE DATABASE HEADER CORRUPTED".toByteArray())
        }
        val report = backupMgr.validateDatabaseIntegrity(context, corruptFile)

        assertFalse(report.isValid)
        assertNotNull(report.errorMessage)

        val logText = ErrorLogger.readLogText(context)
        assertTrue("Expected ErrorLogger entry for integrity failure", logText.contains("integrity_corrupt.db") || logText.contains("SQLiteIntegrity"))

        corruptFile.delete()
        db.close()
    }

    @Test
    fun `ErrorLogger logs import exceptions and exports non-empty txt diagnostic file`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ErrorLogger.clearErrorLogs(context)

        ErrorLogger.logImportException(
            context = context,
            operation = "ImportTest",
            message = "Simulated import crash prevented",
            throwable = IllegalStateException("Corrupt stream"),
        )

        val entries = ErrorLogger.readRecentEntries(context)
        assertTrue(entries.any { it.contains("ImportTest") && it.contains("Corrupt stream") })

        val outFile = File(context.cacheDir, "exported_error_log_test.txt").apply { delete() }
        val exportRes = ErrorLogger.exportErrorLogToUri(context, Uri.fromFile(outFile))
        assertTrue(exportRes.isSuccess)
        assertTrue(outFile.exists() && outFile.readText(Charsets.UTF_8).contains("Simulated import crash prevented"))

        outFile.delete()
        ErrorLogger.clearErrorLogs(context)
        assertTrue(ErrorLogger.readRecentEntries(context).isEmpty())
    }

    @Test
    fun `AiImportColumnProcessor infers ambiguous headers and prevents transfer references from becoming balances`() {
        val table = DiscoveredTable(
            name = "external_export",
            columns = listOf("title_ar", "mob_no", "ref_no", "due_amt"),
            rows = listOf(
                mapOf("title_ar" to "خالد الشرعبي", "mob_no" to "771234567", "ref_no" to "ت/8000011", "due_amt" to "15000"),
                mapOf("title_ar" to "ماجد الحميري", "mob_no" to "733112233", "ref_no" to "ت/8000012", "due_amt" to "8500"),
            ),
        )
        val pollutedBase = ColumnMapping(
            nameCol = null,
            phoneCol = null,
            balanceCol = "ref_no",
        )
        val analysis = AiImportColumnProcessor.analyzeAndRefine(table, pollutedBase, "ملف اختباري")

        assertEquals("title_ar", analysis.refinedMapping.nameCol)
        assertEquals("mob_no", analysis.refinedMapping.phoneCol)
        assertFalse(
            "Transfer reference column must never remain mapped as balance",
            analysis.refinedMapping.balanceCol == "ref_no",
        )
        assertTrue(analysis.confidenceScore in 25..98)
        assertTrue(analysis.anomalyWarnings.any { it.contains("مراجع حوالات") })
        assertTrue(analysis.columnSuggestions.isNotEmpty())
    }

    @Test
    fun `executeImport with replacePreviousData clears prior customers after safety backup and ViewModel clears prior state on new file`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = context as Application
        val db = newInMemoryDb(context)
        val repo = AccountingRepository(db)
        val settingsStore = SettingsDataStore(context)
        val backupMgr = BackupManager(db, settingsStore)
        val inspector = ExternalDbInspector(db, backupMgr)
        val vm = SmartDebtViewModel(app, repo, settingsStore, backupMgr, ExportManager(), inspector)

        // Seed existing customer before import
        repo.createCustomer("عميل قديم", "770000001", "OLD-1", 0.0, "", 1200.0)
        assertEquals(1, db.customerDao().getAllCustomersOnce().size)

        val csvFile = File(context.cacheDir, "replace_import_test.csv").apply {
            writeText("الاسم,الجوال,الرصيد\nعميل جديد فقط,771111222,4500\n", Charsets.UTF_8)
        }
        val inspRes = inspector.inspectRawFile(csvFile, context)
        assertTrue(inspRes.isSuccess)
        val updatedPreview = inspector.rebuildPreview(
            inspection = inspRes.getOrThrow(),
            tableName = inspRes.getOrThrow().selectedTableName,
            mapping = inspRes.getOrThrow().mapping,
            replaceExisting = true,
        )
        val report = inspector.executeImport(
            context = context,
            inspection = updatedPreview,
            createSafetyBackup = true,
            replacePreviousData = true,
        )

        assertTrue(report.replacedPreviousData)
        assertNotNull(report.backupFilePath)
        assertTrue(File(report.backupFilePath!!).exists())
        assertEquals(1, report.importedCustomersCount)

        val finalCustomers = db.customerDao().getAllCustomersOnce()
        assertEquals(1, finalCustomers.size)
        assertEquals("عميل جديد فقط", finalCustomers.first().name)
        assertTrue(db.auditLogDao().getAllAuditLogsOnce().any { it.actionType == "CLEAR_BEFORE_IMPORT" })

        // Verify ViewModel clears prior state cleanly when clearImportState is called
        vm.clearImportState()
        assertNull(vm.importInspection.value)
        assertNull(vm.importReport.value)
        assertNull(vm.importError.value)

        csvFile.delete()
        report.backupFilePath?.let { File(it).delete() }
        db.close()
    }

    @Test
    fun `account statement PDF generation and PrintManager helpers work without crashes and capabilities are Google Play compliant`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val customer = CustomerWithBalance(
            id = 10L,
            name = "سامي الوصابي",
            phone = "771234567",
            accountNumber = "ACC-10",
            notes = "",
            creditLimit = 10000.0,
            isArchived = false,
            createdAt = 1000L,
            updatedAt = 2000L,
            totalDebts = 5000.0,
            totalPayments = 2000.0,
            balance = 3000.0,
            transactionCount = 2,
            lastTransactionDate = 2000L,
            overdueRemindersCount = 0,
        )
        val txs = listOf(
            TransactionWithCustomer(
                id = 1L,
                customerId = 10L,
                customerName = "سامي الوصابي",
                customerPhone = "771234567",
                type = TransactionType.DEBT,
                amount = 5000.0,
                description = "فاتورة بضاعة",
                referenceNumber = "INV-1",
                sourceApp = null,
                transactionDate = 1000L,
                createdAt = 1000L,
                isReversed = false,
                reversedAt = null,
                reversalReason = null,
            ),
            TransactionWithCustomer(
                id = 2L,
                customerId = 10L,
                customerName = "سامي الوصابي",
                customerPhone = "771234567",
                type = TransactionType.PAYMENT,
                amount = 2000.0,
                description = "دفعة نقدية",
                referenceNumber = "RCP-2",
                sourceApp = null,
                transactionDate = 2000L,
                createdAt = 2000L,
                isReversed = false,
                reversedAt = null,
                reversalReason = null,
            ),
        )

        val out = ByteArrayOutputStream()
        val pdfResult = PaymentReceiptPdfGenerator.writeCustomerStatementPdf(customer, txs, AppSettings(), out)
        assertTrue(pdfResult.isSuccess)
        val pdfHeader = String(out.toByteArray().take(5).toByteArray(), Charsets.US_ASCII)
        assertEquals("%PDF-", pdfHeader)

        // Verify PrintManager triggers safely
        val printedSingle = PaymentReceiptPdfGenerator.printCustomerStatement(context, customer, txs, AppSettings())
        val printedPortfolio = PaymentReceiptPdfGenerator.printPortfolioStatement(context, listOf(customer), AppSettings())
        assertTrue(printedSingle)
        assertTrue(printedPortfolio)

        // Verify permissions panel includes BIOMETRIC & PRINTING without prohibited broad storage permissions
        val statuses = AppPermissionsManager.getCapabilityStatuses(context, workManagerRemindersEnabled = true)
        assertTrue(statuses.any { it.kind == AppCapabilityKind.BIOMETRIC })
        assertTrue(statuses.any { it.kind == AppCapabilityKind.PRINTING && it.isGranted })
        val runtimePerms = AppPermissionsManager.allRuntimePermissions().toList()
        assertFalse(runtimePerms.contains("android.permission.READ_EXTERNAL_STORAGE"))
        assertFalse(runtimePerms.contains("android.permission.WRITE_EXTERNAL_STORAGE"))
        assertFalse(runtimePerms.contains("android.permission.READ_MEDIA_IMAGES"))
    }

    @Test
    fun `storage folder SAF permissions hub and manifest declarations remain Play-policy compliant`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pkgInfo = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val declared = pkgInfo.requestedPermissions?.toSet().orEmpty()

        assertTrue(Manifest.permission.VIBRATE in declared)
        assertTrue(Manifest.permission.USE_BIOMETRIC in declared)
        assertFalse(Manifest.permission.MANAGE_EXTERNAL_STORAGE in declared)
        assertFalse(Manifest.permission.READ_EXTERNAL_STORAGE in declared)
        assertFalse(Manifest.permission.WRITE_EXTERNAL_STORAGE in declared)

        assertTrue(AppPermissionsManager.isSupportedImportFileName("backup_clients.db"))
        assertTrue(AppPermissionsManager.isSupportedImportFileName("export.XLSX"))
        assertTrue(AppPermissionsManager.isSupportedImportFileName("ledger.sqlite3"))
        assertFalse(AppPermissionsManager.isSupportedImportFileName("photo.png"))

        val treeUri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADownload")
        val label = AppPermissionsManager.formatTreeFolderLabel(treeUri)
        assertTrue(label.contains("Download"))

        val statuses = AppPermissionsManager.getCapabilityStatuses(context, workManagerRemindersEnabled = true)
        assertTrue(statuses.any { it.kind == AppCapabilityKind.STORAGE_FOLDERS })
        assertTrue(statuses.any { it.kind == AppCapabilityKind.FILES_AND_MEDIA && it.isGranted })
    }

    @Test
    fun `import screen displays SAF storage folder authorization trigger cleanly`() {
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
                    ImportDataScreen(viewModel = vm, onBack = {})
                }
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("authorize_storage_folder_import_button")
                .performScrollTo()
                .assertIsDisplayed()
        } finally {
            store.clear()
            db.close()
        }
    }

    @Test
    fun `settings screen displays SAF storage folder grant trigger cleanly`() {
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
                    SettingsScreen(viewModel = vm, onBack = {})
                }
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("grant_storage_folder_button")
                .performScrollTo()
                .assertIsDisplayed()
            composeRule.onNodeWithTag("grant_storage_file_button")
                .performScrollTo()
                .assertIsDisplayed()
        } finally {
            store.clear()
            db.close()
        }
    }

    @Test
    fun `room Customers and Debts entities with CustomerWithDebts relation and SMS permissions work cleanly`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = newInMemoryDb(context)
        try {
            val custId = db.customerDao().insert(
                Customers(name = "مؤسسة الأمل", phone = "771002003", accountNumber = "ACC-REL-1")
            )
            val debt1Id = db.debtsDao().insertDebt(Debts(customerId = custId, amount = 3200.0, description = "بضاعة آجل"))
            val debt2Id = db.debtsDao().insertDebt(Debts(customerId = custId, amount = 1800.0, description = "فاتورة ثانية"))

            // Update and delete CRUD verification on DebtsDao and CustomerDao
            val debt1 = db.debtsDao().getDebtById(debt1Id)!!
            assertEquals(1, db.debtsDao().updateDebt(debt1.copy(amount = 3500.0, description = "بضاعة آجل معدلة")))
            assertEquals(1, db.debtsDao().deleteDebtById(debt2Id))

            val customer = db.customerDao().getCustomerById(custId)!!
            assertEquals(1, db.customerDao().update(customer.copy(notes = "عميل موثق")))

            val rel = db.customerDao().getCustomerWithDebts(custId)
            assertNotNull(rel)
            assertEquals("مؤسسة الأمل", rel!!.customer.name)
            assertEquals("عميل موثق", rel.customer.notes)
            assertEquals(1, rel.debts.size)
            assertEquals(3500.0, rel.debts.sumOf { it.amount }, 0.01)

            // Verify CustomerDao delete on unlinked customer
            val tempCustId = db.customerDao().insert(Customers(name = "عميل مؤقت", phone = "770000999"))
            assertEquals(1, db.customerDao().deleteById(tempCustId))
            assertNull(db.customerDao().getCustomerById(tempCustId))

            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            val declared = pkgInfo.requestedPermissions?.toSet().orEmpty()
            assertTrue(Manifest.permission.SEND_SMS in declared)
            assertTrue(Manifest.permission.READ_SMS in declared)
            assertTrue(AppPermissionsManager.allRuntimePermissions().contains(Manifest.permission.READ_SMS))
            assertTrue(AppPermissionsManager.getCapabilityStatuses(context, true).any { it.kind == AppCapabilityKind.READ_SMS })
            assertTrue(DirectSmsSender.readRecentInboxMessages(context).isEmpty())

            // Verify sendSmsToCustomer checks SEND_SMS permission and dispatches when granted
            val deniedRes = DirectSmsSender.sendSmsToCustomer(context, "771002003", "تذكير برصيد مستحق")
            assertEquals(SmsDispatchResult.PermissionDenied, deniedRes)

            Shadows.shadowOf(context as Application).grantPermissions(Manifest.permission.SEND_SMS)
            val sentRes = DirectSmsSender.sendSmsToCustomer(
                context = context,
                customerPhone = "771-002-003",
                message = "مرحباً بكم، الرصيد المستحق 3500 ر.ي",
                gateway = object : SmsGateway {
                    override fun sendSms(context: Context, destination: String, messageBody: String, logId: Long): Int {
                        assertEquals("771002003", destination)
                        return 1
                    }
                },
            )
            assertTrue(sentRes is SmsDispatchResult.Dispatched)
        } finally {
            db.close()
        }
    }

    @Test
    fun `compose CustomersScreen displays LazyColumn list and adds new customer directly via ViewModel`() {
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
            composeRule.onNodeWithTag("quick_customer_name_input").performTextReplacement("فهد اليماني")
            composeRule.onNodeWithTag("quick_customer_phone_input").performTextReplacement("778899001")
            composeRule.onNodeWithTag("quick_customer_opening_input").performTextReplacement("2400")
            composeRule.onNodeWithTag("quick_customer_save_button").performClick()

            composeRule.waitUntil(timeoutMillis = 5000) {
                composeRule.onAllNodesWithTag("quick_customer_save_button").fetchSemanticsNodes().isEmpty() &&
                    composeRule.onAllNodesWithTag("customers_list").fetchSemanticsNodes().isNotEmpty() &&
                    composeRule.onAllNodesWithText("فهد اليماني").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithText("فهد اليماني").assertIsDisplayed()
        } finally {
            store.clear()
            db.close()
        }
    }
}
