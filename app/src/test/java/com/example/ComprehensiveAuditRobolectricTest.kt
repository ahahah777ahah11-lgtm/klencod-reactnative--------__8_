package com.example

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.importexport.BackupManager
import com.example.data.importexport.ColumnMapping
import com.example.data.importexport.ExportManager
import com.example.data.importexport.ExternalDbInspector
import com.example.data.importexport.ImportFileFormat
import com.example.data.importexport.PreviewRowStatus
import com.example.data.local.AccountingRepository
import com.example.data.local.AccountingResult
import com.example.data.local.SmartDebtDatabase
import com.example.data.local.TransactionType
import com.example.data.local.TransferCompany
import com.example.data.preferences.SettingsDataStore
import com.example.security.AppPermissionsManager
import com.example.ui.SmartDebtViewModel
import com.example.util.AppDiagnosticLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ComprehensiveAuditRobolectricTest {

    private fun newInMemoryDb(context: Context): SmartDebtDatabase =
        Room.inMemoryDatabaseBuilder(context, SmartDebtDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    @Test
    fun `TEST 01 import valid SQLite DB with uncommitted WAL and SHM transactions preserves all records without modifying source`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = newInMemoryDb(context)
        val settingsStore = SettingsDataStore(context)
        val backupMgr = BackupManager(db, settingsStore)
        val inspector = ExternalDbInspector(db, backupMgr)

        val walDbFile = File(context.cacheDir, "audit_wal_live.db").apply { delete() }
        File("${walDbFile.absolutePath}-wal").delete()
        File("${walDbFile.absolutePath}-shm").delete()

        val extDb = SQLiteDatabase.openOrCreateDatabase(walDbFile, null)
        extDb.enableWriteAheadLogging()
        extDb.execSQL("CREATE TABLE accounts (id INTEGER PRIMARY KEY, name TEXT, phone TEXT, balance REAL)")
        extDb.execSQL("INSERT INTO accounts (name, phone, balance) VALUES ('سالم المحمدي', '771001001', 3500.0)")
        // Keep DB open briefly or leave WAL frames uncheckpointed by NOT truncating, then inspect raw file
        extDb.execSQL("INSERT INTO accounts (name, phone, balance) VALUES ('علي الحداد', '771001002', 4200.0)")
        val walSidecar = File("${walDbFile.absolutePath}-wal")
        val walExistsWhileOpen = walSidecar.exists()

        val bytesBefore = walDbFile.readBytes()
        val walBytesBefore = if (walExistsWhileOpen) walSidecar.readBytes() else ByteArray(0)
        val res = inspector.inspectRawFile(walDbFile, context)
        val bytesAfterInspect = walDbFile.readBytes()
        val walBytesAfterInspect = if (walSidecar.exists()) walSidecar.readBytes() else ByteArray(0)
        extDb.close()

        assertTrue("Expected WAL inspection to succeed: ${res.exceptionOrNull()?.message}", res.isSuccess)
        val insp = res.getOrThrow()
        assertEquals(2, insp.previewRows.size)
        if (walExistsWhileOpen) {
            assertTrue(insp.warnings.any { it.contains("WAL") })
        }
        assertTrue(bytesBefore.contentEquals(bytesAfterInspect))
        assertTrue(walBytesBefore.contentEquals(walBytesAfterInspect))

        val report = inspector.executeImport(context, insp, createSafetyBackup = false)
        assertEquals(2, report.importedCustomersCount)
        assertEquals(2, report.importedTransactionsCount)

        db.close()
        walDbFile.delete()
        File("${walDbFile.absolutePath}-wal").delete()
        File("${walDbFile.absolutePath}-shm").delete()
    }

    @Test
    fun `TEST 02 import empty SQLite DB returns safe failure without crash`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = newInMemoryDb(context)
        val inspector = ExternalDbInspector(db, BackupManager(db, SettingsDataStore(context)))
        val emptyFile = File(context.cacheDir, "audit_empty.db").apply { writeBytes(ByteArray(0)) }

        val res = inspector.inspectUri(context, Uri.fromFile(emptyFile))
        assertTrue(res.isFailure)
        assertTrue(res.exceptionOrNull()?.message?.isNotBlank() == true)

        emptyFile.delete()
        db.close()
    }

    @Test
    fun `TEST 03 import corrupt and encrypted SQLite DB fails cleanly with clear Arabic message`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = newInMemoryDb(context)
        val inspector = ExternalDbInspector(db, BackupManager(db, SettingsDataStore(context)))

        // Encrypted/non-sqlite header
        val encryptedFile = File(context.cacheDir, "audit_encrypted.db").apply {
            writeBytes(ByteArray(2048) { (it * 37 + 13).toByte() })
        }
        val encRes = inspector.inspectUri(context, Uri.fromFile(encryptedFile))
        assertTrue(encRes.isFailure)
        assertTrue(encRes.exceptionOrNull()?.message?.contains("مشفر") == true || encRes.exceptionOrNull()?.message?.contains("غير مدعوم") == true)
        encryptedFile.delete()

        // Valid SQLite header followed by zeroed/corrupt page bytes
        val corruptPageFile = File(context.cacheDir, "audit_corrupt_page.db").apply {
            val bytes = ByteArray(4096)
            "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII).copyInto(bytes)
            // Corrupt page size & btree structure
            for (i in 100 until 4096) bytes[i] = 0x7F.toByte()
            writeBytes(bytes)
        }
        val corruptRes = inspector.inspectUri(context, Uri.fromFile(corruptPageFile))
        assertTrue(corruptRes.isFailure)
        corruptPageFile.delete()

        // Check diagnostic log recorded the inspection failures safely
        val logs = AppDiagnosticLogger.readRecentLogs(context)
        assertTrue(logs.isNotEmpty())
        db.close()
    }

    @Test
    fun `TEST 04 import large SQLite DB streams paged rows safely within memory bounds`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = newInMemoryDb(context)
        val inspector = ExternalDbInspector(db, BackupManager(db, SettingsDataStore(context)))

        val largeDb = File(context.cacheDir, "audit_large.db").apply { delete() }
        val ext = SQLiteDatabase.openOrCreateDatabase(largeDb, null)
        ext.execSQL("CREATE TABLE customers (id INTEGER PRIMARY KEY, name TEXT, phone TEXT, balance REAL, notes TEXT)")
        ext.beginTransaction()
        try {
            val stmt = ext.compileStatement("INSERT INTO customers (name, phone, balance, notes) VALUES (?, ?, ?, ?)")
            val longNote = "ملاحظة طويلة ".repeat(200)
            for (i in 1..2500) {
                stmt.bindString(1, "عميل ضخم $i")
                stmt.bindString(2, "77${1000000 + i}")
                stmt.bindDouble(3, (i * 10).toDouble())
                stmt.bindString(4, longNote)
                stmt.executeInsert()
                stmt.clearBindings()
            }
            ext.setTransactionSuccessful()
        } finally {
            ext.endTransaction()
            ext.close()
        }

        val insp = inspector.inspectUri(context, Uri.fromFile(largeDb)).getOrThrow()
        assertEquals(2500, insp.previewRows.size)
        // Oversized cell text is bounded cleanly to 1024 chars
        assertTrue(insp.previewRows.first().notes.length <= 1024)

        val rep = inspector.executeImport(context, insp, createSafetyBackup = false)
        assertEquals(2500, rep.importedCustomersCount)

        largeDb.delete()
        db.close()
    }

    @Test
    fun `TEST 05 import SQLite DB containing BLOB binary column does not crash`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = newInMemoryDb(context)
        val inspector = ExternalDbInspector(db, BackupManager(db, SettingsDataStore(context)))

        val blobDb = File(context.cacheDir, "audit_blob.db").apply { delete() }
        val ext = SQLiteDatabase.openOrCreateDatabase(blobDb, null)
        ext.execSQL("CREATE TABLE clients (id INTEGER PRIMARY KEY, name TEXT, phone TEXT, balance REAL, photo BLOB)")
        ext.execSQL("INSERT INTO clients (name, phone, balance, photo) VALUES (?, ?, ?, ?)", arrayOf("سمير بلوب", "775544332", 1800.0, byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00, 0x10)))
        ext.close()

        val insp = inspector.inspectUri(context, Uri.fromFile(blobDb)).getOrThrow()
        assertEquals(1, insp.previewRows.size)
        assertEquals("سمير بلوب", insp.previewRows.first().name)
        assertEquals("[بيانات ثنائية]", insp.tables.first().rows.first()["photo"])

        blobDb.delete()
        db.close()
    }

    @Test
    fun `TEST 06 import SQLite DB with unknown non-customer tables warns safely without crash`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = newInMemoryDb(context)
        val inspector = ExternalDbInspector(db, BackupManager(db, SettingsDataStore(context)))

        val unknownDb = File(context.cacheDir, "audit_unknown_tables.db").apply { delete() }
        val ext = SQLiteDatabase.openOrCreateDatabase(unknownDb, null)
        ext.execSQL("CREATE TABLE system_preferences (k TEXT PRIMARY KEY, v TEXT)")
        ext.execSQL("INSERT INTO system_preferences VALUES ('theme', 'blue')")
        ext.close()

        val insp = inspector.inspectUri(context, Uri.fromFile(unknownDb)).getOrThrow()
        assertTrue(insp.warnings.any { it.contains("لم يتم اكتشاف أعمدة عملاء") })

        unknownDb.delete()
        db.close()
    }

    @Test
    fun `TEST 07 import SQLite DB with custom column names supports manual remapping`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = newInMemoryDb(context)
        val inspector = ExternalDbInspector(db, BackupManager(db, SettingsDataStore(context)))

        val customDb = File(context.cacheDir, "audit_custom_cols.db").apply { delete() }
        val ext = SQLiteDatabase.openOrCreateDatabase(customDb, null)
        ext.execSQL("CREATE TABLE ledger_x (col_a TEXT, col_b TEXT, col_c REAL)")
        ext.execSQL("INSERT INTO ledger_x VALUES ('محلات السعادة', '779988776', 6400.0)")
        ext.close()

        val insp = inspector.inspectUri(context, Uri.fromFile(customDb)).getOrThrow()
        val remapped = inspector.rebuildPreview(
            insp,
            "ledger_x",
            ColumnMapping(nameCol = "col_a", phoneCol = "col_b", balanceCol = "col_c"),
        )
        assertEquals(1, remapped.previewRows.count { it.status == PreviewRowStatus.READY_NEW })
        assertEquals(6400.0, remapped.previewRows.first().openingBalance, 0.01)

        val rep = inspector.executeImport(context, remapped, createSafetyBackup = false)
        assertEquals(1, rep.importedCustomersCount)

        customDb.delete()
        db.close()
    }

    @Test
    fun `TEST 08 import Arabic CSV with UTF-8 BOM, quoted commas, empty numbers, invalid phones, and duplicate rejection`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = newInMemoryDb(context)
        val repo = AccountingRepository(db)
        repo.createCustomer("عميل قديم", "771122334", "ACC-0", 0.0, "")

        val inspector = ExternalDbInspector(db, BackupManager(db, SettingsDataStore(context)))
        val csvFile = File(context.cacheDir, "audit_arabic.csv")
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val content = """
            اسم العميل,رقم الهاتف,الرصيد,ملاحظات
            "شركة النور, فرع صنعاء",772233445,,تجارة عامة
            "عميل برقم هاتف قصير",12,4500,هاتف غير مكتمل
            "عميل قديم",771122334,900,مكرر يجب تجاهله
            "",770000111,100,اسم فارغ مرفوض
        """.trimIndent().toByteArray(Charsets.UTF_8)
        csvFile.writeBytes(bom + content)

        val insp = inspector.inspectUri(context, Uri.fromFile(csvFile)).getOrThrow()
        assertEquals(ImportFileFormat.CSV, insp.format)
        assertEquals(4, insp.previewRows.size)

        val rep = inspector.executeImport(context, insp, createSafetyBackup = false)
        assertEquals(2, rep.importedCustomersCount)
        assertEquals(1, rep.skippedDuplicatesCount)
        assertEquals(1, rep.errorRowsCount)

        val nour = db.customerDao().findByExactName("شركة النور, فرع صنعاء")
        assertNotNull(nour)
        val shortPhoneCust = db.customerDao().findByExactName("عميل برقم هاتف قصير")
        assertNotNull(shortPhoneCust)
        // Invalid short phone is sanitized safely instead of matching future empty/short phones
        assertEquals("", shortPhoneCust!!.phone)

        csvFile.delete()
        db.close()
    }

    @Test
    fun `TEST 09 import corrupt JSON and empty array JSON returns error safely without crash`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = newInMemoryDb(context)
        val inspector = ExternalDbInspector(db, BackupManager(db, SettingsDataStore(context)))

        val badJson = File(context.cacheDir, "audit_bad.json").apply { writeText("{\"customers\": [{\"name\": ") }
        assertTrue(inspector.inspectUri(context, Uri.fromFile(badJson)).isFailure)
        badJson.delete()

        val emptyArrJson = File(context.cacheDir, "audit_empty_arr.json").apply { writeText("{\"customers\": []}") }
        assertTrue(inspector.inspectUri(context, Uri.fromFile(emptyArrJson)).isFailure)
        emptyArrJson.delete()

        db.close()
    }

    @Test
    fun `TEST 10 import corrupt XLSX and corrupt binary XLS fail cleanly without crash`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = newInMemoryDb(context)
        val inspector = ExternalDbInspector(db, BackupManager(db, SettingsDataStore(context)))

        val corruptXlsx = File(context.cacheDir, "audit_corrupt.xlsx").apply {
            writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x08, 0x00, 0x99.toByte()))
        }
        assertTrue(inspector.inspectUri(context, Uri.fromFile(corruptXlsx)).isFailure)
        corruptXlsx.delete()

        val corruptXls = File(context.cacheDir, "audit_corrupt.xls").apply {
            writeBytes(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte()) + ByteArray(600))
        }
        assertTrue(inspector.inspectUri(context, Uri.fromFile(corruptXls)).isFailure)
        corruptXls.delete()

        db.close()
    }

    @Test
    fun `TEST 11 cancelling import mid-processing resets busy state and preserves database integrity`(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = SmartDebtViewModel.Factory(app).create(SmartDebtViewModel::class.java)

        val largeDb = File(app.cacheDir, "audit_cancel.db").apply { delete() }
        val ext = SQLiteDatabase.openOrCreateDatabase(largeDb, null)
        ext.execSQL("CREATE TABLE customers (id INTEGER PRIMARY KEY, name TEXT, phone TEXT, balance REAL)")
        ext.execSQL("INSERT INTO customers (name, phone, balance) VALUES ('اختبار الإلغاء', '771234567', 500.0)")
        ext.close()

        val beforeCount = SmartDebtDatabase.getInstance(app).customerDao().getAllCustomersOnce().size
        var threwOther = false
        val job = launch(Dispatchers.Default) {
            try {
                vm.inspectImportUri(Uri.fromFile(largeDb))
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                threwOther = true
            }
        }
        job.cancel()
        job.join()

        assertFalse(threwOther)
        assertFalse(vm.importBusy.value)
        assertEquals(beforeCount, SmartDebtDatabase.getInstance(app).customerDao().getAllCustomersOnce().size)

        largeDb.delete()
        SmartDebtDatabase.getInstance(app).close()
    }

    @Test
    fun `TEST 12 app restart after import retains all customers and transactions persistently`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "audit_restart_persist.db"
        context.getDatabasePath(dbName).delete()

        val db1 = Room.databaseBuilder(context, SmartDebtDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        val settingsStore = SettingsDataStore(context)
        val inspector = ExternalDbInspector(db1, BackupManager(db1, settingsStore))

        val csv = File(context.cacheDir, "restart_import.csv").apply {
            writeText("اسم العميل,رقم الهاتف,الرصيد\nسعيد البكري,778800112,7300\n", Charsets.UTF_8)
        }
        val insp = inspector.inspectUri(context, Uri.fromFile(csv)).getOrThrow()
        inspector.executeImport(context, insp, createSafetyBackup = false)
        db1.close()
        csv.delete()

        // Reopen database simulating app cold restart
        val db2 = Room.databaseBuilder(context, SmartDebtDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        val restored = db2.customerDao().findByExactName("سعيد البكري")
        assertNotNull(restored)
        val withBal = db2.customerDao().getCustomerWithBalanceOnce(restored!!.id)!!
        assertEquals(7300.0, withBal.balance, 0.01)

        db2.close()
        context.getDatabasePath(dbName).delete()
        Unit
    }

    @Test
    fun `TEST 13 existing customer data is preserved across imports and empty backup restore protection`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = newInMemoryDb(context)
        val repo = AccountingRepository(db)
        val settingsStore = SettingsDataStore(context)
        val backupMgr = BackupManager(db, settingsStore)
        val inspector = ExternalDbInspector(db, backupMgr)

        val cid = (repo.createCustomer("عميل أصلي محمي", "773311223", "ORIG-1", 0.0, "") as AccountingResult.Success).data
        repo.recordTransaction(cid, TransactionType.DEBT, 5000.0, "فاتورة أصلية")

        // Create an empty backup and verify restoring it over existing customers is blocked
        val emptyBackupJson = """
            {"backupFormatVersion":1,"createdAt":1,"data":{"customers":[],"transactions":[]}}
        """.trimIndent()
        val emptyVal = backupMgr.inspectBackupJson(emptyBackupJson)
        assertTrue(emptyVal.isValid)
        val restoreBlocked = backupMgr.restoreFromValidatedBackup(emptyVal, context)
        assertTrue(restoreBlocked.isFailure)
        assertEquals(1, db.customerDao().getAllCustomersOnce().size)

        // Import additional file and confirm original customer remains intact
        val csv = File(context.cacheDir, "append_safe.csv").apply {
            writeText("name,phone,balance\nعميل جديد إضافي,774455998,1200\n", Charsets.UTF_8)
        }
        val insp = inspector.inspectUri(context, Uri.fromFile(csv)).getOrThrow()
        inspector.executeImport(context, insp, createSafetyBackup = true)
        assertEquals(2, db.customerDao().getAllCustomersOnce().size)
        assertEquals(5000.0, db.customerDao().getCustomerWithBalanceOnce(cid)!!.balance, 0.01)

        csv.delete()
        db.close()
    }

    @Test
    fun `TEST 14 financial totals balance equals total debts minus total payments across imports transfers and reversals`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = newInMemoryDb(context)
        val repo = AccountingRepository(db)

        val cid = (repo.createCustomer("مؤسسة الميزان", "775566112", "ACC-BAL", 0.0, "", openingBalance = 2000.0) as AccountingResult.Success).data
        repo.recordTransaction(cid, TransactionType.DEBT, 8000.0, "توريد")
        val payId = (repo.recordTransaction(cid, TransactionType.PAYMENT, 3000.0, "دفعة نقدية") as AccountingResult.Success).data

        // Transfer with alphanumeric reference "ت/8000011" must keep exact amount 1500.0 and not treat 8000011 as money
        val trId = (repo.addTransfer(TransferCompany.AL_NAJM, "ت/8000011", 1500.0, "أحمد", System.currentTimeMillis(), cid, "") as AccountingResult.Success).data
        repo.confirmTransferAsPayment(trId, cid, allowOverpayment = false)

        val beforeReverse = db.customerDao().getCustomerWithBalanceOnce(cid)!!
        assertEquals(10000.0, beforeReverse.totalDebts, 0.01)
        assertEquals(4500.0, beforeReverse.totalPayments, 0.01)
        assertEquals(5500.0, beforeReverse.totalDebts - beforeReverse.totalPayments, 0.01)
        assertEquals(5500.0, beforeReverse.balance, 0.01)

        // Reversal updates balance cleanly with AuditLog
        repo.reverseTransaction(payId, "تصحيح")
        val afterReverse = db.customerDao().getCustomerWithBalanceOnce(cid)!!
        assertEquals(10000.0, afterReverse.totalDebts, 0.01)
        assertEquals(1500.0, afterReverse.totalPayments, 0.01)
        assertEquals(8500.0, afterReverse.balance, 0.01)

        db.close()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 34, 36])
class MultiSdkAndroid13To16CompatibilityTest {

    @Test
    fun `TEST 15 core accounting database permissions and sqlite import work on Android 13 14 and newer`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, SmartDebtDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val settingsStore = SettingsDataStore(context)
        val backupMgr = BackupManager(db, settingsStore)
        val inspector = ExternalDbInspector(db, backupMgr)

        val statuses = AppPermissionsManager.getCapabilityStatuses(context, workManagerRemindersEnabled = true)
        assertTrue(statuses.isNotEmpty())

        val extFile = File(context.cacheDir, "sdk_compat_test.db").apply { delete() }
        val extDb = SQLiteDatabase.openOrCreateDatabase(extFile, null)
        extDb.execSQL("CREATE TABLE customers (id INTEGER PRIMARY KEY, name TEXT, phone TEXT, balance REAL)")
        extDb.execSQL("INSERT INTO customers (name, phone, balance) VALUES ('عميل التوافق', '776677889', 2900.0)")
        extDb.close()

        val insp = inspector.inspectUri(context, Uri.fromFile(extFile)).getOrThrow()
        val rep = inspector.executeImport(context, insp, createSafetyBackup = false)
        assertEquals(1, rep.importedCustomersCount)

        val c = db.customerDao().findByExactName("عميل التوافق")!!
        assertEquals(2900.0, db.customerDao().getCustomerWithBalanceOnce(c.id)!!.balance, 0.01)

        extFile.delete()
        db.close()
    }
}
