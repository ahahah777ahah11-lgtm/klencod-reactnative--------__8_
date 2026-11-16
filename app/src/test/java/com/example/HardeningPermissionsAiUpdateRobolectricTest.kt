package com.example

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.AiAutoFeatureType
import com.example.data.ai.AiGeneratedFeatureResult
import com.example.data.ai.PlaceholderAiRepository
import com.example.data.importexport.BackupManager
import com.example.data.importexport.ExportManager
import com.example.data.importexport.ExternalDbInspector
import com.example.data.local.CustomerWithBalance
import com.example.data.local.DashboardSummary
import com.example.data.local.SmartDebtDatabase
import com.example.data.preferences.AppSettings
import com.example.data.preferences.SettingsDataStore
import com.example.data.update.AppUpdateChecker
import com.example.data.update.AppUpdateState
import com.example.security.AppCapabilityKind
import com.example.security.AppPermissionsManager
import com.example.ui.SmartDebtViewModel
import com.example.ui.screens.LocalAssistantScreen
import com.example.ui.theme.SmartDebtTheme
import com.example.util.BalanceShareHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class HardeningPermissionsAiUpdateRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `corrupt empty and blob-containing files fail safely without crashing inspector or backup`() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, SmartDebtDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val settingsStore = SettingsDataStore(context)
        val backupMgr = BackupManager(db, settingsStore)
        val inspector = ExternalDbInspector(db, backupMgr)

        try {
            // 1. Empty .db file
            val emptyDb = File(context.cacheDir, "empty_test.db").apply { writeBytes(ByteArray(0)) }
            assertTrue(inspector.inspectUri(context, Uri.fromFile(emptyDb)).isFailure)
            assertFalse(backupMgr.inspectSqliteBackupUri(context, Uri.fromFile(emptyDb)).isValid)
            emptyDb.delete()

            // 2. Corrupt random bytes disguised as .db
            val badDb = File(context.cacheDir, "corrupt_test.db").apply {
                writeText("NOT_A_REAL_SQLITE_HEADER_1234567890")
            }
            assertTrue(inspector.inspectUri(context, Uri.fromFile(badDb)).isFailure)
            assertFalse(backupMgr.inspectSqliteBackupUri(context, Uri.fromFile(badDb)).isValid)
            badDb.delete()

            // 3. Corrupt XLSX (invalid ZIP bytes with PK header)
            val badXlsx = File(context.cacheDir, "corrupt_test.xlsx").apply {
                writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x7F, 0x7F, 0x00))
            }
            assertTrue(inspector.inspectUri(context, Uri.fromFile(badXlsx)).isFailure)
            badXlsx.delete()

            // 4. Corrupt binary XLS OLE2 header
            val badXls = File(context.cacheDir, "corrupt_test.xls").apply {
                writeBytes(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0, 0, 0, 0))
            }
            assertTrue(inspector.inspectUri(context, Uri.fromFile(badXls)).isFailure)
            badXls.delete()

            // 5. Corrupt JSON
            val badJson = File(context.cacheDir, "corrupt_test.json").apply { writeText("{broken_json") }
            assertTrue(inspector.inspectUri(context, Uri.fromFile(badJson)).isFailure)
            badJson.delete()

            // 6. Real SQLite DB containing a BLOB column must not crash SQLiteCursor
            val blobDb = File(context.cacheDir, "blob_clients.db").apply { delete() }
            val extSql = SQLiteDatabase.openOrCreateDatabase(blobDb, null)
            extSql.execSQL("CREATE TABLE customers (id INTEGER PRIMARY KEY, name TEXT, phone TEXT, balance REAL, avatar BLOB)")
            extSql.execSQL(
                "INSERT INTO customers (name, phone, balance, avatar) VALUES (?, ?, ?, ?)",
                arrayOf("عميل مع مرفق ثنائي", "775566778", 1500.0, byteArrayOf(1, 2, 3, 4)),
            )
            extSql.close()

            val blobInsp = inspector.inspectUri(context, Uri.fromFile(blobDb)).getOrThrow()
            assertEquals(1, blobInsp.previewRows.size)
            assertEquals("عميل مع مرفق ثنائي", blobInsp.previewRows.first().name)
            assertEquals(1500.0, blobInsp.previewRows.first().openingBalance, 0.01)
            blobDb.delete()
        } finally {
            db.close()
        }
    }

    @Test
    fun `manifest declares required permissions without prohibited play-policy permissions and runtime hub checks accurately`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val pkgInfo = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
        val declared = pkgInfo.requestedPermissions?.toSet().orEmpty()

        // Required permissions must exist
        assertTrue(Manifest.permission.SEND_SMS in declared)
        assertTrue(Manifest.permission.POST_NOTIFICATIONS in declared)
        assertTrue(Manifest.permission.READ_CONTACTS in declared)
        assertTrue(Manifest.permission.CAMERA in declared)
        assertTrue(Manifest.permission.ACCESS_COARSE_LOCATION in declared)
        assertTrue(Manifest.permission.ACCESS_FINE_LOCATION in declared)
        assertTrue(Manifest.permission.USE_BIOMETRIC in declared)
        assertTrue(Manifest.permission.INTERNET in declared)

        // Prohibited Play-Policy permissions must NEVER exist
        assertFalse(Manifest.permission.READ_EXTERNAL_STORAGE in declared)
        assertFalse(Manifest.permission.WRITE_EXTERNAL_STORAGE in declared)
        assertFalse("android.permission.READ_MEDIA_IMAGES" in declared)
        assertFalse("android.permission.READ_MEDIA_VIDEO" in declared)
        assertFalse("android.permission.REQUEST_INSTALL_PACKAGES" in declared)
        assertFalse("android.permission.ACCESS_BACKGROUND_LOCATION" in declared)

        val shadowApp = Shadows.shadowOf(app)
        shadowApp.grantPermissions(Manifest.permission.CAMERA, Manifest.permission.READ_CONTACTS)
        shadowApp.denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

        val statuses = AppPermissionsManager.getCapabilityStatuses(app, workManagerRemindersEnabled = true)
        assertTrue(statuses.first { it.kind == AppCapabilityKind.FILES_AND_MEDIA }.isGranted)
        assertTrue(statuses.first { it.kind == AppCapabilityKind.REMINDERS }.isGranted)
        assertTrue(statuses.first { it.kind == AppCapabilityKind.CAMERA }.isGranted)
        assertTrue(statuses.first { it.kind == AppCapabilityKind.CONTACTS }.isGranted)
        assertFalse(statuses.first { it.kind == AppCapabilityKind.LOCATION }.isGranted)
    }

    @Test
    fun `share customer balance formats financial details clearly and starts chooser`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val customer = CustomerWithBalance(
            id = 99L,
            name = "شركة الأفق للتجارة",
            phone = "770998877",
            accountNumber = "ACC-99",
            notes = "",
            creditLimit = 20_000.0,
            createdAt = 1L,
            updatedAt = 1L,
            isArchived = false,
            totalDebts = 10_000.0,
            totalPayments = 3_500.0,
            balance = 6_500.0,
            transactionCount = 2,
            lastTransactionDate = null,
            overdueRemindersCount = 0,
        )
        val settings = AppSettings(storeName = "متجر النخبة", contactPhone = "771122334", currencySymbol = "ر.ي")

        val text = BalanceShareHelper.buildCustomerBalanceText(customer, settings)
        assertTrue(text.contains("متجر النخبة"))
        assertTrue(text.contains("شركة الأفق للتجارة"))
        assertTrue(text.contains("ACC-99"))
        assertTrue(text.contains("6,500 ر.ي"))

        val launched = BalanceShareHelper.shareCustomerBalance(app, customer, settings)
        assertTrue(launched)
        val started = Shadows.shadowOf(app).nextStartedActivity
        assertEquals(android.content.Intent.ACTION_CHOOSER, started.action)
    }

    @Test
    fun `placeholder ai repository returns clearly labeled output with firebase notice and updates assistant UI`() = runBlocking<Unit> {
        val repoAi = PlaceholderAiRepository()
        val summary = DashboardSummary(
            totalCustomers = 5,
            activeDebtorsCount = 3,
            totalDebts = 12_000.0,
            totalPayments = 4_000.0,
            netOutstandingBalance = 8_000.0,
            pendingTransfersCount = 1,
            overdueRemindersCount = 2,
        )
        val res = repoAi.generateAutoFeature(
            featureType = AiAutoFeatureType.INSTALLMENT_PLAN,
            summary = summary,
            customer = null,
            settings = AppSettings(),
        )
        assertTrue(res.isPlaceholder)
        assertEquals(AiGeneratedFeatureResult.FIREBASE_REQUIRED_NOTICE_AR, res.firebaseRequiredNotice)
        assertTrue(res.content.contains("Placeholder"))

        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = SmartDebtViewModel.Factory(app).create(SmartDebtViewModel::class.java)
        composeRule.setContent {
            SmartDebtTheme {
                LocalAssistantScreen(viewModel = vm, onBack = {}, onNavigate = {})
            }
        }
        composeRule.onNodeWithTag("ai_firebase_notice_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_feature_chip_${AiAutoFeatureType.PORTFOLIO_BRIEF.name}")
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ai_generated_content").assertIsDisplayed()
        SmartDebtDatabase.getInstance(app).close()
    }

    @Test
    fun `app update checker evaluates remote manifest version and handles blank or offline url gracefully`() = runBlocking<Unit> {
        val newerJson = """
            {
              "versionCode": 99,
              "versionName": "2.5.0",
              "releaseNotes": "تحسينات استيراد قواعد البيانات",
              "downloadUrl": "https://example.com/release/v2.5.0"
            }
        """.trimIndent()
        val parsedNew = AppUpdateChecker.parseUpdateManifestJson(newerJson).getOrThrow()
        val stateNew = AppUpdateChecker.evaluateManifest(parsedNew, currentVersionCode = 1, currentVersionName = "1.0")
        assertTrue(stateNew is AppUpdateState.UpdateAvailable)
        assertEquals("2.5.0", (stateNew as AppUpdateState.UpdateAvailable).latestVersionName)

        val sameState = AppUpdateChecker.evaluateManifest(
            parsedNew.copy(versionCode = 1),
            currentVersionCode = 1,
            currentVersionName = "1.0",
        )
        assertTrue(sameState is AppUpdateState.UpToDate)

        // Blank URL works offline cleanly
        val blankResult = AppUpdateChecker.checkForUpdates("")
        assertTrue(blankResult is AppUpdateState.UpToDate)
    }
}
