package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.importexport.BackupManager
import com.example.data.importexport.ExternalDbInspector
import com.example.data.local.AccountingRepository
import com.example.data.local.SmartDebtDatabase
import com.example.data.preferences.SettingsDataStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ImportAndBackupRobolectricTest {

    private fun newInMemoryDb(context: Context): SmartDebtDatabase =
        Room.inMemoryDatabaseBuilder(context, SmartDebtDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    @Test
    fun `json backup SHA-256 verification and safe CSV import preserve financial integrity`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = newInMemoryDb(context)
        val repo = AccountingRepository(db)
        val settingsStore = SettingsDataStore(context)
        val backupMgr = BackupManager(db, settingsStore)
        val inspector = ExternalDbInspector(db, backupMgr)

        val csvFile = File(context.cacheDir, "arabic_utf8_customers.csv").apply { delete() }

        try {
            repo.createCustomer("عبدالله الصالح", "771234000", "ACC-B1", 0.0, "", 1500.0)
            val json = backupMgr.createBackupJsonString()
            val validInspection = backupMgr.inspectBackupJson(json)
            assertTrue(validInspection.isValid)
            assertTrue(validInspection.checksumVerified)

            val tampered = json.replace("عبدالله الصالح", "اسم معدل")
            val tamperedInspection = backupMgr.inspectBackupJson(tampered)
            assertFalse("Tampered backup should fail SHA-256 verification", tamperedInspection.isValid)

            csvFile.writeText(
                "الاسم,الهاتف,الرصيد\n\"شركة النور, فرع صنعاء\",772233445,4200\n",
                Charsets.UTF_8,
            )
            val csvRes = inspector.inspectRawFile(csvFile, context)
            assertTrue(csvRes.isSuccess)
            val report = inspector.executeImport(
                context = context,
                inspection = csvRes.getOrThrow(),
                createSafetyBackup = false,
                replacePreviousData = false,
            )
            assertEquals(1, report.importedCustomersCount)
            assertEquals(2, db.customerDao().getAllCustomersOnce().size)
        } finally {
            csvFile.delete()
            db.close()
        }
    }
}
