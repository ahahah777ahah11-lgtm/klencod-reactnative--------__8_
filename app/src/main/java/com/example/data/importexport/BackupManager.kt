package com.example.data.importexport

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.withTransaction
import com.example.data.local.AuditLogEntity
import com.example.data.local.CustomerEntity
import com.example.data.local.DebtsEntity
import com.example.data.local.MessageLogEntity
import com.example.data.local.MessageStatus
import com.example.data.local.PaymentsEntity
import com.example.data.local.ReminderEntity
import com.example.data.local.ReminderStatus
import com.example.data.local.SmartDebtDatabase
import com.example.data.local.SmsTemplateEntity
import com.example.data.local.TransactionEntity
import com.example.data.local.TransactionType
import com.example.data.local.TransferCompany
import com.example.data.local.TransferEntity
import com.example.data.local.TransferStatus
import com.example.data.preferences.AppSettings
import com.example.data.preferences.SettingsDataStore
import com.example.ui.theme.AppBackgroundStyle
import com.example.ui.theme.AppColorPalette
import com.example.ui.theme.AppIconShape
import com.example.ui.theme.AppThemeMode
import com.example.util.AppDiagnosticLogger
import com.example.util.ErrorLogger
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

data class ParsedBackupData(
    val settings: AppSettings?,
    val customers: List<CustomerEntity>,
    val transactions: List<TransactionEntity>,
    val reminders: List<ReminderEntity>,
    val transfers: List<TransferEntity>,
    val messageLogs: List<MessageLogEntity>,
    val smsTemplates: List<SmsTemplateEntity>,
    val auditLogs: List<AuditLogEntity>,
)

data class BackupValidationResult(
    val isValid: Boolean,
    val checksumVerified: Boolean,
    val createdAt: Long,
    val formatVersion: Int,
    val errorMessage: String? = null,
    val warnings: List<String> = emptyList(),
    val data: ParsedBackupData? = null,
)

data class SqliteBackupValidationResult(
    val isValid: Boolean,
    val integrityOk: Boolean,
    val customersCount: Int = 0,
    val transactionsCount: Int = 0,
    val remindersCount: Int = 0,
    val transfersCount: Int = 0,
    val messageLogsCount: Int = 0,
    val fileSizeBytes: Long = 0L,
    val errorMessage: String? = null,
    val tempDbFilePath: String? = null,
)

data class DatabaseIntegrityReport(
    val isValid: Boolean,
    val sqliteHeaderValid: Boolean,
    val quickCheckPassed: Boolean,
    val walDetected: Boolean,
    val shmDetected: Boolean,
    val walCheckpointed: Boolean,
    val tablesCount: Int = 0,
    val errorMessage: String? = null,
    val warnings: List<String> = emptyList(),
)

class BackupManager(
    private val database: SmartDebtDatabase,
    private val settingsDataStore: SettingsDataStore,
) {
    companion object {
        const val BACKUP_FILENAME = "SmartDebtManager-backup.json"
        const val SQLITE_BACKUP_FILENAME = "SmartDebtManager-backup.db"
        const val BACKUP_FORMAT_VERSION = 1
    }

    suspend fun createBackupJsonString(): String {
        val settings = settingsDataStore.getSettingsOnce()
        val customers = database.customerDao().getAllCustomersOnce()
        val transactions = database.transactionDao().getAllTransactionsOnce()
        val reminders = database.reminderDao().getAllRemindersOnce()
        val transfers = database.transferDao().getAllTransfersOnce()
        val messageLogs = database.messageLogDao().getAllLogsOnce()
        val smsTemplates = database.smsTemplateDao().getAllTemplatesOnce()
        val auditLogs = database.auditLogDao().getAllAuditLogsOnce()

        val dataObj = JSONObject().apply {
            put("settings", JSONObject().apply {
                put("storeName", settings.storeName)
                put("contactPhone", settings.contactPhone)
                put("currencySymbol", settings.currencySymbol)
                put("themeMode", settings.themeMode.name)
                put("colorPalette", settings.colorPalette.name)
                put("backgroundStyle", settings.backgroundStyle.name)
                put("iconShape", settings.iconShape.name)
                put("allowOverpayment", settings.allowOverpayment)
                put("duplicateWindowMinutes", settings.duplicateWindowMinutes)
                put("workManagerRemindersEnabled", settings.workManagerRemindersEnabled)
                put("overdueDaysThreshold", settings.overdueDaysThreshold)
                put("biometricLockEnabled", settings.biometricLockEnabled)
                put("autoUpdateCheckEnabled", settings.autoUpdateCheckEnabled)
                put("updateManifestUrl", settings.updateManifestUrl)
            })
            put("customers", JSONArray().apply {
                customers.forEach { c ->
                    put(JSONObject().apply {
                        put("id", c.id)
                        put("name", c.name)
                        put("phone", c.phone)
                        put("accountNumber", c.accountNumber)
                        put("notes", c.notes)
                        put("creditLimit", c.creditLimit)
                        put("createdAt", c.createdAt)
                        put("updatedAt", c.updatedAt)
                        put("isArchived", c.isArchived)
                    })
                }
            })
            put("transactions", JSONArray().apply {
                transactions.forEach { t ->
                    put(JSONObject().apply {
                        put("id", t.id)
                        put("customerId", t.customerId)
                        put("type", t.type.name)
                        put("amount", t.amount)
                        put("description", t.description)
                        put("referenceNumber", t.referenceNumber ?: JSONObject.NULL)
                        put("sourceApp", t.sourceApp ?: JSONObject.NULL)
                        put("transactionDate", t.transactionDate)
                        put("createdAt", t.createdAt)
                        put("isReversed", t.isReversed)
                        put("reversedAt", t.reversedAt ?: JSONObject.NULL)
                        put("reversalReason", t.reversalReason ?: JSONObject.NULL)
                    })
                }
            })
            put("reminders", JSONArray().apply {
                reminders.forEach { r ->
                    put(JSONObject().apply {
                        put("id", r.id)
                        put("customerId", r.customerId)
                        put("title", r.title)
                        put("dueDate", r.dueDate)
                        put("amountDue", r.amountDue)
                        put("status", r.status.name)
                        put("notes", r.notes)
                        put("createdAt", r.createdAt)
                    })
                }
            })
            put("transfers", JSONArray().apply {
                transfers.forEach { tr ->
                    put(JSONObject().apply {
                        put("id", tr.id)
                        put("company", tr.company.name)
                        put("referenceNumber", tr.referenceNumber)
                        put("amount", tr.amount)
                        put("senderName", tr.senderName)
                        put("transferDate", tr.transferDate)
                        put("customerId", tr.customerId ?: JSONObject.NULL)
                        put("status", tr.status.name)
                        put("linkedTransactionId", tr.linkedTransactionId ?: JSONObject.NULL)
                        put("notes", tr.notes)
                        put("createdAt", tr.createdAt)
                    })
                }
            })
            put("messageLogs", JSONArray().apply {
                messageLogs.forEach { m ->
                    put(JSONObject().apply {
                        put("id", m.id)
                        put("customerId", m.customerId)
                        put("customerName", m.customerName)
                        put("phone", m.phone)
                        put("messageBody", m.messageBody)
                        put("templateName", m.templateName)
                        put("status", m.status.name)
                        put("createdAt", m.createdAt)
                    })
                }
            })
            put("smsTemplates", JSONArray().apply {
                smsTemplates.forEach { s ->
                    put(JSONObject().apply {
                        put("id", s.id)
                        put("name", s.name)
                        put("body", s.body)
                        put("isDefault", s.isDefault)
                        put("createdAt", s.createdAt)
                    })
                }
            })
            put("auditLogs", JSONArray().apply {
                auditLogs.forEach { a ->
                    put(JSONObject().apply {
                        put("id", a.id)
                        put("actionType", a.actionType)
                        put("entityType", a.entityType)
                        put("entityId", a.entityId ?: JSONObject.NULL)
                        put("summary", a.summary)
                        put("details", a.details)
                        put("createdAt", a.createdAt)
                    })
                }
            })
        }

        val canonicalPayload = dataObj.toString()
        val checksum = sha256(canonicalPayload)

        val root = JSONObject().apply {
            put("appName", "Smart Debt Manager Pro SMS")
            put("appPackage", "com.smartdebt.app")
            put("backupFormatVersion", BACKUP_FORMAT_VERSION)
            put("createdAt", System.currentTimeMillis())
            put("checksumSha256", checksum)
            put("data", dataObj)
        }
        return root.toString(2)
    }

    suspend fun createLocalSafetyBackup(context: Context, prefix: String = "pre_import"): File {
        val dir = File(context.filesDir, "backups").apply { mkdirs() }
        val file = File(dir, "SmartDebtManager-${prefix}-${System.currentTimeMillis()}.json")
        val tmp = File(dir, "${file.name}.tmp")
        try {
            tmp.writeText(createBackupJsonString(), Charsets.UTF_8)
            if (file.exists()) file.delete()
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
            }
        } catch (t: Throwable) {
            AppDiagnosticLogger.logError(context, "Backup", "Failed writing local safety backup", t)
            throw t
        } finally {
            runCatching { tmp.delete() }
        }
        return file
    }

    fun listLocalBackups(context: Context): List<File> {
        val dir = File(context.filesDir, "backups")
        if (!dir.exists()) return emptyList()
        return dir.listFiles { f ->
            f.isFile && (f.extension.equals("json", ignoreCase = true) || f.extension.equals("db", ignoreCase = true))
        }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    suspend fun exportSqliteDatabaseToFile(context: Context, destinationFile: File): Result<File> {
        val tempWrite = File(destinationFile.parentFile ?: context.cacheDir, "${destinationFile.name}.tmp")
        return runCatching {
            destinationFile.parentFile?.mkdirs()
            // Flush WAL pages to the main .db file before copying
            SmartDebtDatabase.checkpointWal(database)
            val liveDbFile = context.getDatabasePath(SmartDebtDatabase.DB_NAME)
            if (liveDbFile != null && liveDbFile.exists() && liveDbFile.length() > 0L) {
                liveDbFile.inputStream().use { input ->
                    tempWrite.outputStream().use { out ->
                        input.copyTo(out)
                    }
                }
                normalizeWalHeaderFlags(tempWrite)
            } else {
                // Fallback for in-memory database instances (e.g. unit tests)
                writeStandaloneSqliteSnapshot(tempWrite)
            }
            if (destinationFile.exists()) destinationFile.delete()
            if (!tempWrite.renameTo(destinationFile)) {
                tempWrite.copyTo(destinationFile, overwrite = true)
            }
            database.auditLogDao().insert(
                AuditLogEntity(
                    actionType = "EXPORT_SQLITE_DB",
                    entityType = "SYSTEM",
                    summary = "تصدير قاعدة بيانات SQLite إلى ملف محلي (${destinationFile.name})",
                    details = "الحجم: ${destinationFile.length() / 1024} كيلوبايت",
                    createdAt = System.currentTimeMillis(),
                )
            )
            destinationFile
        }.onFailure { err ->
            AppDiagnosticLogger.logError(context, "Backup", "exportSqliteDatabaseToFile failed", err)
        }.also {
            runCatching { tempWrite.delete() }
        }
    }

    suspend fun exportSqliteDatabaseToUri(context: Context, uri: Uri): Result<Unit> {
        return runCatching {
            val tempExport = File(context.cacheDir, "export_sqlite_${System.currentTimeMillis()}.db")
            try {
                exportSqliteDatabaseToFile(context, tempExport).getOrThrow()
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    tempExport.inputStream().use { input -> input.copyTo(out) }
                } ?: error("تعذر فتح مسار الحفظ المحدد لملف SQLite")
            } finally {
                tempExport.delete()
            }
        }
    }

    suspend fun createLocalSqliteBackup(context: Context, prefix: String = "sqlite_manual"): File {
        val dir = File(context.filesDir, "backups").apply { mkdirs() }
        val file = File(dir, "SmartDebtManager-${prefix}-${System.currentTimeMillis()}.db")
        return exportSqliteDatabaseToFile(context, file).getOrThrow()
    }

    fun inspectSqliteBackupUri(context: Context, uri: Uri): SqliteBackupValidationResult {
        val tempFile = File(context.cacheDir, "inspect_restore_${System.currentTimeMillis()}.db")
        return try {
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { out -> input.copyTo(out) }
                true
            } ?: false
            if (!copied) {
                runCatching { tempFile.delete() }
                return SqliteBackupValidationResult(
                    isValid = false,
                    integrityOk = false,
                    errorMessage = "تعذر فتح ملف قاعدة البيانات المحدد.",
                )
            }
            inspectSqliteBackupFileInternal(tempFile, deleteOnInvalid = true)
        } catch (t: Throwable) {
            runCatching { tempFile.delete() }
            SqliteBackupValidationResult(
                isValid = false,
                integrityOk = false,
                errorMessage = "فشل قراءة ملف SQLite: ${t.message ?: "ملف غير صالح"}",
            )
        }
    }

    fun inspectSqliteBackupFile(context: Context, sourceFile: File): SqliteBackupValidationResult {
        val tempFile = File(context.cacheDir, "inspect_local_restore_${System.currentTimeMillis()}.db")
        return try {
            sourceFile.inputStream().use { input ->
                tempFile.outputStream().use { out -> input.copyTo(out) }
            }
            inspectSqliteBackupFileInternal(tempFile, deleteOnInvalid = true)
        } catch (t: Throwable) {
            runCatching { tempFile.delete() }
            SqliteBackupValidationResult(
                isValid = false,
                integrityOk = false,
                errorMessage = "تعذر فحص ملف SQLite: ${t.message ?: "ملف تالف"}",
            )
        }
    }

    fun validateDatabaseIntegrity(
        context: Context?,
        sourceFile: File,
    ): DatabaseIntegrityReport {
        if (!sourceFile.exists() || sourceFile.length() < 100L) {
            val msg = "حجم ملف قاعدة البيانات صغير جداً أو غير موجود."
            ErrorLogger.logImportException(context, "ValidateDbIntegrity", "$msg (${sourceFile.name})")
            return DatabaseIntegrityReport(
                isValid = false,
                sqliteHeaderValid = false,
                quickCheckPassed = false,
                walDetected = false,
                shmDetected = false,
                walCheckpointed = false,
                errorMessage = msg,
            )
        }

        val header = ByteArray(20)
        val headerReadCount = runCatching {
            sourceFile.inputStream().use { it.read(header) }
        }.getOrElse { err ->
            val msg = "تعذر قراءة ترويسة ملف SQLite: ${err.message}"
            ErrorLogger.logImportException(context, "ValidateDbIntegrity", msg, err)
            return DatabaseIntegrityReport(
                isValid = false,
                sqliteHeaderValid = false,
                quickCheckPassed = false,
                walDetected = false,
                shmDetected = false,
                walCheckpointed = false,
                errorMessage = msg,
            )
        }

        val magic = if (headerReadCount >= 15) String(header, 0, 15, Charsets.US_ASCII) else ""
        if (!magic.startsWith("SQLite format 3")) {
            val msg = "قاعدة البيانات مشفرة أو بصيغة غير صالحة (ليست SQLite3)."
            ErrorLogger.logImportException(context, "ValidateDbIntegrity", "$msg (${sourceFile.name})")
            return DatabaseIntegrityReport(
                isValid = false,
                sqliteHeaderValid = false,
                quickCheckPassed = false,
                walDetected = false,
                shmDetected = false,
                walCheckpointed = false,
                errorMessage = msg,
            )
        }

        val walSidecar = File("${sourceFile.absolutePath}-wal")
        val shmSidecar = File("${sourceFile.absolutePath}-shm")
        val isWalHeader = headerReadCount >= 20 && (header[18].toInt() == 2 || header[19].toInt() == 2)
        val walDetected = isWalHeader || walSidecar.exists()
        val shmDetected = shmSidecar.exists()
        val warnings = mutableListOf<String>()

        val tempDir = context?.cacheDir ?: sourceFile.parentFile?.takeIf { it.canWrite() }
            ?: File(System.getProperty("java.io.tmpdir") ?: ".")
        val tempDb = File.createTempFile("integrity_chk_", ".db", tempDir)
        val tempWal = File("${tempDb.absolutePath}-wal")
        val tempShm = File("${tempDb.absolutePath}-shm")
        var walCheckpointed = false

        try {
            sourceFile.inputStream().use { input ->
                tempDb.outputStream().use { out -> input.copyTo(out) }
            }

            if (walSidecar.exists() && walSidecar.length() > 0L) {
                walSidecar.inputStream().use { input ->
                    tempWal.outputStream().use { out -> input.copyTo(out) }
                }
                if (shmSidecar.exists() && shmSidecar.length() > 0L) {
                    shmSidecar.inputStream().use { input ->
                        tempShm.outputStream().use { out -> input.copyTo(out) }
                    }
                }
                runCatching {
                    SQLiteDatabase.openDatabase(
                        tempDb.absolutePath,
                        null,
                        SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
                    ).use { rwDb ->
                        rwDb.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { c ->
                            walCheckpointed = c.moveToFirst()
                        }
                    }
                }.onFailure { err ->
                    ErrorLogger.logImportWarning(
                        context,
                        "ValidateDbIntegrity",
                        "WAL checkpoint fallback warning for ${sourceFile.name}: ${err.message}",
                    )
                    warnings.add("تعذر دمج بعض إطارات WAL الجانبية: ${err.message}")
                }
                runCatching { tempWal.delete() }
                runCatching { tempShm.delete() }
                if (walCheckpointed) {
                    warnings.add("تم دمج ومعالجة معاملات WAL/SHM على نسخة فحص مؤقتة بأمان دون تعديل الأصل.")
                }
            } else if (isWalHeader) {
                warnings.add("القاعدة كانت بوضع WAL دون ملف (-wal) مرفق؛ تم فحص النسخة المؤقتة بعد تطبيع الترويسة.")
            }

            normalizeWalHeaderFlags(tempDb)

            val db = SQLiteDatabase.openDatabase(
                tempDb.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
            )
            return db.use { sqlite ->
                var quickCheckMsg = ""
                var quickOk = false
                sqlite.rawQuery("PRAGMA quick_check", null).use { c ->
                    if (c.moveToFirst()) {
                        quickCheckMsg = c.getString(0).orEmpty()
                        quickOk = quickCheckMsg.equals("ok", ignoreCase = true)
                    }
                }
                var tablesCount = 0
                if (quickOk) {
                    sqlite.rawQuery(
                        "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'",
                        null,
                    ).use { tc ->
                        if (tc.moveToFirst()) tablesCount = tc.getInt(0)
                    }
                }
                if (!quickOk) {
                    val errMsg = "فشل فحص سلامة SQLite (PRAGMA quick_check): $quickCheckMsg"
                    ErrorLogger.logImportException(context, "ValidateDbIntegrity", "$errMsg (${sourceFile.name})")
                    DatabaseIntegrityReport(
                        isValid = false,
                        sqliteHeaderValid = true,
                        quickCheckPassed = false,
                        walDetected = walDetected,
                        shmDetected = shmDetected,
                        walCheckpointed = walCheckpointed,
                        tablesCount = tablesCount,
                        errorMessage = errMsg,
                        warnings = warnings,
                    )
                } else {
                    ErrorLogger.logImportInfo(
                        context,
                        "ValidateDbIntegrity",
                        "Integrity check passed for ${sourceFile.name} (tables=$tablesCount, walDetected=$walDetected, walCheckpointed=$walCheckpointed)",
                    )
                    DatabaseIntegrityReport(
                        isValid = true,
                        sqliteHeaderValid = true,
                        quickCheckPassed = true,
                        walDetected = walDetected,
                        shmDetected = shmDetected,
                        walCheckpointed = walCheckpointed,
                        tablesCount = tablesCount,
                        warnings = warnings,
                    )
                }
            }
        } catch (t: Throwable) {
            val errMsg = "فشل فحص سلامة قاعدة البيانات (${sourceFile.name}): ${t.message ?: "ملف تالف أو مشفر"}"
            ErrorLogger.logImportException(context, "ValidateDbIntegrity", errMsg, t)
            return DatabaseIntegrityReport(
                isValid = false,
                sqliteHeaderValid = true,
                quickCheckPassed = false,
                walDetected = walDetected,
                shmDetected = shmDetected,
                walCheckpointed = walCheckpointed,
                errorMessage = errMsg,
                warnings = warnings,
            )
        } finally {
            runCatching { tempWal.delete() }
            runCatching { tempShm.delete() }
            runCatching { tempDb.delete() }
        }
    }

    private fun inspectSqliteBackupFileInternal(tempFile: File, deleteOnInvalid: Boolean): SqliteBackupValidationResult {
        val integrityReport = validateDatabaseIntegrity(context = null, sourceFile = tempFile)
        if (!integrityReport.isValid) {
            if (deleteOnInvalid) tempFile.delete()
            return SqliteBackupValidationResult(
                isValid = false,
                integrityOk = integrityReport.quickCheckPassed,
                errorMessage = integrityReport.errorMessage ?: "فشل فحص سلامة قاعدة البيانات.",
            )
        }
        normalizeWalHeaderFlags(tempFile)

        return try {
            val db = SQLiteDatabase.openDatabase(
                tempFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
            )
            db.use { sqlite ->
                val tables = mutableSetOf<String>()
                sqlite.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { c ->
                    while (c.moveToNext()) tables.add(c.getString(0).lowercase())
                }
                if ("customers" !in tables || "transactions" !in tables) {
                    if (deleteOnInvalid) tempFile.delete()
                    return SqliteBackupValidationResult(
                        isValid = false,
                        integrityOk = true,
                        errorMessage = "الملف لا يحتوي على جداول تطبيق مدير الديون الذكي (customers / transactions). يمكنك استخدام شاشة استيراد البيانات لاستيراد قواعد التطبيقات الأخرى.",
                    )
                }
                fun countTable(name: String): Int {
                    if (name !in tables) return 0
                    return sqlite.rawQuery("SELECT COUNT(*) FROM \"$name\"", null).use { c ->
                        if (c.moveToFirst()) c.getInt(0) else 0
                    }
                }
                SqliteBackupValidationResult(
                    isValid = true,
                    integrityOk = true,
                    customersCount = countTable("customers"),
                    transactionsCount = countTable("transactions"),
                    remindersCount = countTable("reminders"),
                    transfersCount = countTable("transfers"),
                    messageLogsCount = countTable("message_logs"),
                    fileSizeBytes = tempFile.length(),
                    errorMessage = null,
                    tempDbFilePath = tempFile.absolutePath,
                )
            }
        } catch (t: Throwable) {
            ErrorLogger.logImportException(null, "InspectSqliteBackup", "Failed opening backup copy", t)
            if (deleteOnInvalid) runCatching { tempFile.delete() }
            SqliteBackupValidationResult(
                isValid = false,
                integrityOk = false,
                errorMessage = "تعذر فتح قاعدة بيانات SQLite: ${t.message ?: "قاعدة بيانات تالفة"}",
            )
        }
    }

    suspend fun restoreFromValidatedSqliteBackup(
        context: Context,
        validation: SqliteBackupValidationResult,
    ): Result<String> {
        val path = validation.tempDbFilePath
        if (!validation.isValid || path.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException(validation.errorMessage ?: "ملف SQLite غير صالح"))
        }
        val sourceFile = File(path)
        if (!sourceFile.exists()) {
            return Result.failure(IllegalStateException("الملف المؤقت للنسخة غير موجود؛ يرجى إعادة اختيار الملف."))
        }

        return try {
            createLocalSafetyBackup(context, "pre_sqlite_restore")
            val parsed = readAllTablesFromSqliteCopy(sourceFile)
            database.withTransaction {
                database.reminderDao().deleteAll()
                database.debtsDao().deleteAll()
                database.paymentsDao().deleteAll()
                database.transactionDao().deleteAll()
                database.transferDao().deleteAll()
                database.messageLogDao().deleteAll()
                database.smsTemplateDao().deleteAll()
                database.auditLogDao().deleteAll()
                database.customerDao().deleteAll()

                parsed.customers.forEach { database.customerDao().insert(it) }
                parsed.transactions.forEach { tx ->
                    database.transactionDao().insert(tx)
                    if (tx.type.isDebit) {
                        database.debtsDao().insertDebt(
                            DebtsEntity(
                                customerId = tx.customerId,
                                amount = tx.amount,
                                description = tx.description,
                                isOpeningBalance = tx.type == TransactionType.OPENING_BALANCE,
                                linkedTransactionId = tx.id,
                                referenceNumber = tx.referenceNumber,
                                createdAt = tx.createdAt,
                                isReversed = tx.isReversed,
                            )
                        )
                    } else {
                        database.paymentsDao().insertPayment(
                            PaymentsEntity(
                                customerId = tx.customerId,
                                amount = tx.amount,
                                description = tx.description,
                                isSettlement = tx.type == TransactionType.SETTLEMENT,
                                linkedTransactionId = tx.id,
                                referenceNumber = tx.referenceNumber,
                                paymentDate = tx.transactionDate,
                                createdAt = tx.createdAt,
                                isReversed = tx.isReversed,
                            )
                        )
                    }
                }
                parsed.reminders.forEach { database.reminderDao().insert(it) }
                parsed.transfers.forEach { database.transferDao().insert(it) }
                parsed.messageLogs.forEach { database.messageLogDao().insert(it) }
                if (parsed.smsTemplates.isEmpty()) {
                    SmartDebtDatabase.seedDefaultTemplates(database.smsTemplateDao())
                } else {
                    parsed.smsTemplates.forEach { database.smsTemplateDao().insert(it) }
                }
                parsed.auditLogs.forEach { database.auditLogDao().insert(it) }
                database.auditLogDao().insert(
                    AuditLogEntity(
                        actionType = "RESTORE_SQLITE_DB",
                        entityType = "SYSTEM",
                        summary = "استعادة قاعدة بيانات SQLite كاملة (${parsed.customers.size} عميل، ${parsed.transactions.size} عملية)",
                        createdAt = System.currentTimeMillis(),
                    )
                )
            }
            Result.success(
                "تمت استعادة قاعدة بيانات SQLite بنجاح (${parsed.customers.size} عميل، ${parsed.transactions.size} عملية)."
            )
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            sourceFile.delete()
        }
    }

    private suspend fun writeStandaloneSqliteSnapshot(targetFile: File) {
        targetFile.delete()
        val customers = database.customerDao().getAllCustomersOnce()
        val transactions = database.transactionDao().getAllTransactionsOnce()
        val reminders = database.reminderDao().getAllRemindersOnce()
        val transfers = database.transferDao().getAllTransfersOnce()
        val messageLogs = database.messageLogDao().getAllLogsOnce()
        val smsTemplates = database.smsTemplateDao().getAllTemplatesOnce()
        val auditLogs = database.auditLogDao().getAllAuditLogsOnce()

        val debts = database.debtsDao().getAllDebtsOnce()
        val payments = database.paymentsDao().getAllPaymentsOnce()

        val sqlite = SQLiteDatabase.openOrCreateDatabase(targetFile, null)
        sqlite.use { db ->
            db.execSQL("CREATE TABLE customers (id INTEGER PRIMARY KEY, name TEXT NOT NULL, phone TEXT NOT NULL, account_number TEXT NOT NULL, notes TEXT NOT NULL, credit_limit REAL NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, is_archived INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE transactions (id INTEGER PRIMARY KEY, customer_id INTEGER NOT NULL, type TEXT NOT NULL, amount REAL NOT NULL, description TEXT NOT NULL, reference_number TEXT, source_app TEXT, transaction_date INTEGER NOT NULL, created_at INTEGER NOT NULL, is_reversed INTEGER NOT NULL, reversed_at INTEGER, reversal_reason TEXT)")
            db.execSQL("CREATE TABLE debts (id INTEGER PRIMARY KEY, customer_id INTEGER NOT NULL, amount REAL NOT NULL, description TEXT NOT NULL, due_date INTEGER, is_opening_balance INTEGER NOT NULL, linked_transaction_id INTEGER, reference_number TEXT, created_at INTEGER NOT NULL, is_reversed INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE payments (id INTEGER PRIMARY KEY, customer_id INTEGER NOT NULL, amount REAL NOT NULL, description TEXT NOT NULL, is_settlement INTEGER NOT NULL, linked_transaction_id INTEGER, reference_number TEXT, payment_date INTEGER NOT NULL, created_at INTEGER NOT NULL, is_reversed INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE reminders (id INTEGER PRIMARY KEY, customer_id INTEGER NOT NULL, title TEXT NOT NULL, due_date INTEGER NOT NULL, amount_due REAL NOT NULL, status TEXT NOT NULL, notes TEXT NOT NULL, created_at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE transfers (id INTEGER PRIMARY KEY, company TEXT NOT NULL, reference_number TEXT NOT NULL, amount REAL NOT NULL, sender_name TEXT NOT NULL, transfer_date INTEGER NOT NULL, customer_id INTEGER, status TEXT NOT NULL, linked_transaction_id INTEGER, notes TEXT NOT NULL, created_at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE message_logs (id INTEGER PRIMARY KEY, customer_id INTEGER NOT NULL, customer_name TEXT NOT NULL, phone TEXT NOT NULL, message_body TEXT NOT NULL, template_name TEXT NOT NULL, status TEXT NOT NULL, created_at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE sms_templates (id INTEGER PRIMARY KEY, name TEXT NOT NULL, body TEXT NOT NULL, is_default INTEGER NOT NULL, created_at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE audit_logs (id INTEGER PRIMARY KEY, action_type TEXT NOT NULL, entity_type TEXT NOT NULL, entity_id INTEGER, summary TEXT NOT NULL, details TEXT NOT NULL, created_at INTEGER NOT NULL)")

            db.beginTransaction()
            try {
                customers.forEach { c ->
                    db.execSQL(
                        "INSERT INTO customers VALUES (?,?,?,?,?,?,?,?,?)",
                        arrayOf(c.id, c.name, c.phone, c.accountNumber, c.notes, c.creditLimit, c.createdAt, c.updatedAt, if (c.isArchived) 1 else 0),
                    )
                }
                transactions.forEach { t ->
                    db.execSQL(
                        "INSERT INTO transactions VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                        arrayOf(t.id, t.customerId, t.type.name, t.amount, t.description, t.referenceNumber, t.sourceApp, t.transactionDate, t.createdAt, if (t.isReversed) 1 else 0, t.reversedAt, t.reversalReason),
                    )
                }
                debts.forEach { d ->
                    db.execSQL(
                        "INSERT INTO debts VALUES (?,?,?,?,?,?,?,?,?,?)",
                        arrayOf(d.id, d.customerId, d.amount, d.description, d.dueDate, if (d.isOpeningBalance) 1 else 0, d.linkedTransactionId, d.referenceNumber, d.createdAt, if (d.isReversed) 1 else 0),
                    )
                }
                payments.forEach { p ->
                    db.execSQL(
                        "INSERT INTO payments VALUES (?,?,?,?,?,?,?,?,?,?)",
                        arrayOf(p.id, p.customerId, p.amount, p.description, if (p.isSettlement) 1 else 0, p.linkedTransactionId, p.referenceNumber, p.paymentDate, p.createdAt, if (p.isReversed) 1 else 0),
                    )
                }
                reminders.forEach { r ->
                    db.execSQL(
                        "INSERT INTO reminders VALUES (?,?,?,?,?,?,?,?)",
                        arrayOf(r.id, r.customerId, r.title, r.dueDate, r.amountDue, r.status.name, r.notes, r.createdAt),
                    )
                }
                transfers.forEach { tr ->
                    db.execSQL(
                        "INSERT INTO transfers VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                        arrayOf(tr.id, tr.company.name, tr.referenceNumber, tr.amount, tr.senderName, tr.transferDate, tr.customerId, tr.status.name, tr.linkedTransactionId, tr.notes, tr.createdAt),
                    )
                }
                messageLogs.forEach { m ->
                    db.execSQL(
                        "INSERT INTO message_logs VALUES (?,?,?,?,?,?,?,?)",
                        arrayOf(m.id, m.customerId, m.customerName, m.phone, m.messageBody, m.templateName, m.status.name, m.createdAt),
                    )
                }
                smsTemplates.forEach { s ->
                    db.execSQL(
                        "INSERT INTO sms_templates VALUES (?,?,?,?,?)",
                        arrayOf(s.id, s.name, s.body, if (s.isDefault) 1 else 0, s.createdAt),
                    )
                }
                auditLogs.forEach { a ->
                    db.execSQL(
                        "INSERT INTO audit_logs VALUES (?,?,?,?,?,?,?)",
                        arrayOf(a.id, a.actionType, a.entityType, a.entityId, a.summary, a.details, a.createdAt),
                    )
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    private fun readAllTablesFromSqliteCopy(file: File): ParsedBackupData {
        val db = SQLiteDatabase.openDatabase(
            file.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
        )
        return db.use { sqlite ->
            val tables = mutableSetOf<String>()
            sqlite.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { c ->
                while (c.moveToNext()) tables.add(c.getString(0).lowercase())
            }

            val customers = mutableListOf<CustomerEntity>()
            val customerIds = mutableSetOf<Long>()
            sqlite.rawQuery("SELECT id, name, phone, account_number, notes, credit_limit, created_at, updated_at, is_archived FROM customers", null).use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    customerIds.add(id)
                    customers.add(
                        CustomerEntity(
                            id = id,
                            name = c.getString(1) ?: "",
                            phone = c.getString(2) ?: "",
                            accountNumber = c.getString(3) ?: "",
                            notes = c.getString(4) ?: "",
                            creditLimit = c.getDouble(5),
                            createdAt = c.getLong(6),
                            updatedAt = c.getLong(7),
                            isArchived = c.getInt(8) != 0,
                        )
                    )
                }
            }

            val transactions = mutableListOf<TransactionEntity>()
            sqlite.rawQuery("SELECT id, customer_id, type, amount, description, reference_number, source_app, transaction_date, created_at, is_reversed, reversed_at, reversal_reason FROM transactions", null).use { c ->
                while (c.moveToNext()) {
                    val cid = c.getLong(1)
                    if (cid !in customerIds) continue
                    transactions.add(
                        TransactionEntity(
                            id = c.getLong(0),
                            customerId = cid,
                            type = TransactionType.entries.find { it.name == c.getString(2) } ?: TransactionType.DEBT,
                            amount = c.getDouble(3),
                            description = c.getString(4) ?: "",
                            referenceNumber = if (c.isNull(5)) null else c.getString(5),
                            sourceApp = if (c.isNull(6)) null else c.getString(6),
                            transactionDate = c.getLong(7),
                            createdAt = c.getLong(8),
                            isReversed = c.getInt(9) != 0,
                            reversedAt = if (c.isNull(10)) null else c.getLong(10),
                            reversalReason = if (c.isNull(11)) null else c.getString(11),
                        )
                    )
                }
            }

            val reminders = mutableListOf<ReminderEntity>()
            if ("reminders" in tables) {
                sqlite.rawQuery("SELECT id, customer_id, title, due_date, amount_due, status, notes, created_at FROM reminders", null).use { c ->
                    while (c.moveToNext()) {
                        val cid = c.getLong(1)
                        if (cid !in customerIds) continue
                        reminders.add(
                            ReminderEntity(
                                id = c.getLong(0),
                                customerId = cid,
                                title = c.getString(2) ?: "تذكير",
                                dueDate = c.getLong(3),
                                amountDue = c.getDouble(4),
                                status = ReminderStatus.entries.find { it.name == c.getString(5) } ?: ReminderStatus.PENDING,
                                notes = c.getString(6) ?: "",
                                createdAt = c.getLong(7),
                            )
                        )
                    }
                }
            }

            val transfers = mutableListOf<TransferEntity>()
            if ("transfers" in tables) {
                sqlite.rawQuery("SELECT id, company, reference_number, amount, sender_name, transfer_date, customer_id, status, linked_transaction_id, notes, created_at FROM transfers", null).use { c ->
                    while (c.moveToNext()) {
                        val cid = if (c.isNull(6)) null else c.getLong(6)
                        transfers.add(
                            TransferEntity(
                                id = c.getLong(0),
                                company = TransferCompany.entries.find { it.name == c.getString(1) } ?: TransferCompany.AL_NAJM,
                                referenceNumber = c.getString(2) ?: "",
                                amount = c.getDouble(3),
                                senderName = c.getString(4) ?: "",
                                transferDate = c.getLong(5),
                                customerId = cid?.takeIf { it in customerIds },
                                status = TransferStatus.entries.find { it.name == c.getString(7) } ?: TransferStatus.PENDING,
                                linkedTransactionId = if (c.isNull(8)) null else c.getLong(8),
                                notes = c.getString(9) ?: "",
                                createdAt = c.getLong(10),
                            )
                        )
                    }
                }
            }

            val messageLogs = mutableListOf<MessageLogEntity>()
            if ("message_logs" in tables) {
                sqlite.rawQuery("SELECT id, customer_id, customer_name, phone, message_body, template_name, status, created_at FROM message_logs", null).use { c ->
                    while (c.moveToNext()) {
                        messageLogs.add(
                            MessageLogEntity(
                                id = c.getLong(0),
                                customerId = c.getLong(1),
                                customerName = c.getString(2) ?: "",
                                phone = c.getString(3) ?: "",
                                messageBody = c.getString(4) ?: "",
                                templateName = c.getString(5) ?: "",
                                status = MessageStatus.entries.find { it.name == c.getString(6) } ?: MessageStatus.INTENT_OPENED,
                                createdAt = c.getLong(7),
                            )
                        )
                    }
                }
            }

            val smsTemplates = mutableListOf<SmsTemplateEntity>()
            if ("sms_templates" in tables) {
                sqlite.rawQuery("SELECT id, name, body, is_default, created_at FROM sms_templates", null).use { c ->
                    while (c.moveToNext()) {
                        smsTemplates.add(
                            SmsTemplateEntity(
                                id = c.getLong(0),
                                name = c.getString(1) ?: "",
                                body = c.getString(2) ?: "",
                                isDefault = c.getInt(3) != 0,
                                createdAt = c.getLong(4),
                            )
                        )
                    }
                }
            }

            val auditLogs = mutableListOf<AuditLogEntity>()
            if ("audit_logs" in tables) {
                sqlite.rawQuery("SELECT id, action_type, entity_type, entity_id, summary, details, created_at FROM audit_logs", null).use { c ->
                    while (c.moveToNext()) {
                        auditLogs.add(
                            AuditLogEntity(
                                id = c.getLong(0),
                                actionType = c.getString(1) ?: "INFO",
                                entityType = c.getString(2) ?: "SYSTEM",
                                entityId = if (c.isNull(3)) null else c.getLong(3),
                                summary = c.getString(4) ?: "",
                                details = c.getString(5) ?: "",
                                createdAt = c.getLong(6),
                            )
                        )
                    }
                }
            }

            ParsedBackupData(
                settings = null,
                customers = customers,
                transactions = transactions,
                reminders = reminders,
                transfers = transfers,
                messageLogs = messageLogs,
                smsTemplates = smsTemplates,
                auditLogs = auditLogs,
            )
        }
    }

    private fun normalizeWalHeaderFlags(dbFile: File) {
        runCatching {
            if (dbFile.length() < 24L) return
            RandomAccessFile(dbFile, "rw").use { raf ->
                raf.seek(18)
                val writeVer = raf.read()
                val readVer = raf.read()
                if (writeVer == 2 || readVer == 2) {
                    raf.seek(18)
                    raf.write(1)
                    raf.write(1)
                }
            }
        }
    }

    fun inspectBackupJson(rawJson: String): BackupValidationResult {
        return try {
            val trimmed = rawJson.trim().removePrefix("\uFEFF")
            val root = JSONObject(trimmed)
            val version = root.optInt("backupFormatVersion", 0)
            val createdAt = root.optLong("createdAt", 0L)
            val expectedChecksum = root.optString("checksumSha256", "")
            val dataObj = root.optJSONObject("data")
                ?: return BackupValidationResult(
                    isValid = false,
                    checksumVerified = false,
                    createdAt = createdAt,
                    formatVersion = version,
                    errorMessage = "هيكل ملف النسخة الاحتياطية غير صالح: قسم البيانات (data) مفقود.",
                )

            val warnings = mutableListOf<String>()
            val checksumVerified = if (expectedChecksum.isNotBlank()) {
                val actual = sha256(dataObj.toString())
                if (actual != expectedChecksum) {
                    return BackupValidationResult(
                        isValid = false,
                        checksumVerified = false,
                        createdAt = createdAt,
                        formatVersion = version,
                        errorMessage = "فشل التحقق من سلامة الملف (Checksum Mismatch): قد يكون الملف تالفاً أو تم تعديله يدوياً.",
                    )
                }
                true
            } else {
                warnings.add("الملف لا يحتوي على بصمة تحقق SHA-256، تم فحص الهيكل والقيم يدوياً.")
                false
            }

            if (!dataObj.has("customers") || !dataObj.has("transactions")) {
                return BackupValidationResult(
                    isValid = false,
                    checksumVerified = checksumVerified,
                    createdAt = createdAt,
                    formatVersion = version,
                    errorMessage = "الملف لا يحتوي على جداول العملاء والعمليات الأساسية.",
                )
            }

            val settingsObj = dataObj.optJSONObject("settings")
            val settings = settingsObj?.let { s ->
                AppSettings(
                    storeName = s.optString("storeName", "متجري"),
                    contactPhone = s.optString("contactPhone", ""),
                    currencySymbol = s.optString("currencySymbol", "ر.ي"),
                    themeMode = AppThemeMode.entries.find { it.name == s.optString("themeMode") }
                        ?: AppThemeMode.SYSTEM,
                    colorPalette = AppColorPalette.entries.find { it.name == s.optString("colorPalette") }
                        ?: AppColorPalette.OFFICIAL_NAVY_TEAL,
                    backgroundStyle = AppBackgroundStyle.entries.find { it.name == s.optString("backgroundStyle") }
                        ?: AppBackgroundStyle.THEME_DEFAULT,
                    iconShape = AppIconShape.entries.find { it.name == s.optString("iconShape") }
                        ?: AppIconShape.SQUIRCLE,
                    allowOverpayment = s.optBoolean("allowOverpayment", false),
                    duplicateWindowMinutes = s.optInt("duplicateWindowMinutes", 5),
                    workManagerRemindersEnabled = s.optBoolean("workManagerRemindersEnabled", false),
                    overdueDaysThreshold = s.optInt("overdueDaysThreshold", 14),
                    biometricLockEnabled = s.optBoolean("biometricLockEnabled", false),
                    autoUpdateCheckEnabled = s.optBoolean("autoUpdateCheckEnabled", true),
                    updateManifestUrl = s.optString("updateManifestUrl", ""),
                )
            }

            val customersArr = dataObj.optJSONArray("customers") ?: JSONArray()
            val customers = mutableListOf<CustomerEntity>()
            val customerIds = mutableSetOf<Long>()
            for (i in 0 until customersArr.length()) {
                val obj = customersArr.getJSONObject(i)
                val id = obj.getLong("id")
                val name = obj.optString("name", "").trim()
                if (name.isBlank()) {
                    return BackupValidationResult(
                        isValid = false,
                        checksumVerified = checksumVerified,
                        createdAt = createdAt,
                        formatVersion = version,
                        errorMessage = "بيانات عميل تالفة في السجل رقم ${i + 1}: الاسم فارغ.",
                    )
                }
                customerIds.add(id)
                customers.add(
                    CustomerEntity(
                        id = id,
                        name = name,
                        phone = obj.optString("phone", ""),
                        accountNumber = obj.optString("accountNumber", ""),
                        notes = obj.optString("notes", ""),
                        creditLimit = obj.optDouble("creditLimit", 0.0).coerceAtLeast(0.0),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                        isArchived = obj.optBoolean("isArchived", false),
                    )
                )
            }

            val txArr = dataObj.optJSONArray("transactions") ?: JSONArray()
            val transactions = mutableListOf<TransactionEntity>()
            for (i in 0 until txArr.length()) {
                val obj = txArr.getJSONObject(i)
                val custId = obj.getLong("customerId")
                val amount = obj.getDouble("amount")
                if (amount <= 0.0 || amount.isNaN() || amount.isInfinite()) {
                    return BackupValidationResult(
                        isValid = false,
                        checksumVerified = checksumVerified,
                        createdAt = createdAt,
                        formatVersion = version,
                        errorMessage = "عملية مالية غير صالحة في السجل رقم ${i + 1}: المبلغ ($amount) يجب أن يكون موجباً.",
                    )
                }
                if (custId !in customerIds) {
                    warnings.add("تم تجاهل عملية مرتبطة بعميل غير موجود ($custId).")
                    continue
                }
                transactions.add(
                    TransactionEntity(
                        id = obj.getLong("id"),
                        customerId = custId,
                        type = TransactionType.entries.find { it.name == obj.optString("type") }
                            ?: TransactionType.DEBT,
                        amount = amount,
                        description = obj.optString("description", ""),
                        referenceNumber = obj.optString("referenceNumber", "").takeIf { it.isNotBlank() && it != "null" },
                        sourceApp = obj.optString("sourceApp", "").takeIf { it.isNotBlank() && it != "null" },
                        transactionDate = obj.optLong("transactionDate", System.currentTimeMillis()),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        isReversed = obj.optBoolean("isReversed", false),
                        reversedAt = if (obj.isNull("reversedAt")) null else obj.optLong("reversedAt"),
                        reversalReason = obj.optString("reversalReason", "").takeIf { it.isNotBlank() && it != "null" },
                    )
                )
            }

            val remindersArr = dataObj.optJSONArray("reminders") ?: JSONArray()
            val reminders = mutableListOf<ReminderEntity>()
            for (i in 0 until remindersArr.length()) {
                val obj = remindersArr.getJSONObject(i)
                val custId = obj.getLong("customerId")
                if (custId !in customerIds) continue
                reminders.add(
                    ReminderEntity(
                        id = obj.getLong("id"),
                        customerId = custId,
                        title = obj.optString("title", "تذكير"),
                        dueDate = obj.optLong("dueDate", System.currentTimeMillis()),
                        amountDue = obj.optDouble("amountDue", 0.0).coerceAtLeast(0.0),
                        status = ReminderStatus.entries.find { it.name == obj.optString("status") }
                            ?: ReminderStatus.PENDING,
                        notes = obj.optString("notes", ""),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    )
                )
            }

            val transfersArr = dataObj.optJSONArray("transfers") ?: JSONArray()
            val transfers = mutableListOf<TransferEntity>()
            for (i in 0 until transfersArr.length()) {
                val obj = transfersArr.getJSONObject(i)
                val amount = obj.optDouble("amount", 0.0)
                if (amount <= 0.0) continue
                val cid = if (obj.isNull("customerId")) null else obj.optLong("customerId")
                transfers.add(
                    TransferEntity(
                        id = obj.getLong("id"),
                        company = TransferCompany.entries.find { it.name == obj.optString("company") }
                            ?: TransferCompany.AL_NAJM,
                        referenceNumber = obj.optString("referenceNumber", ""),
                        amount = amount,
                        senderName = obj.optString("senderName", ""),
                        transferDate = obj.optLong("transferDate", System.currentTimeMillis()),
                        customerId = cid?.takeIf { it in customerIds },
                        status = TransferStatus.entries.find { it.name == obj.optString("status") }
                            ?: TransferStatus.PENDING,
                        linkedTransactionId = if (obj.isNull("linkedTransactionId")) null else obj.optLong("linkedTransactionId"),
                        notes = obj.optString("notes", ""),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    )
                )
            }

            val msgArr = dataObj.optJSONArray("messageLogs") ?: JSONArray()
            val messageLogs = mutableListOf<MessageLogEntity>()
            for (i in 0 until msgArr.length()) {
                val obj = msgArr.getJSONObject(i)
                messageLogs.add(
                    MessageLogEntity(
                        id = obj.getLong("id"),
                        customerId = obj.optLong("customerId", 0L),
                        customerName = obj.optString("customerName", ""),
                        phone = obj.optString("phone", ""),
                        messageBody = obj.optString("messageBody", ""),
                        templateName = obj.optString("templateName", ""),
                        status = MessageStatus.entries.find { it.name == obj.optString("status") }
                            ?: MessageStatus.INTENT_OPENED,
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    )
                )
            }

            val tplArr = dataObj.optJSONArray("smsTemplates") ?: JSONArray()
            val smsTemplates = mutableListOf<SmsTemplateEntity>()
            for (i in 0 until tplArr.length()) {
                val obj = tplArr.getJSONObject(i)
                smsTemplates.add(
                    SmsTemplateEntity(
                        id = obj.getLong("id"),
                        name = obj.optString("name", "قالب"),
                        body = obj.optString("body", ""),
                        isDefault = obj.optBoolean("isDefault", false),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    )
                )
            }

            val auditArr = dataObj.optJSONArray("auditLogs") ?: JSONArray()
            val auditLogs = mutableListOf<AuditLogEntity>()
            for (i in 0 until auditArr.length()) {
                val obj = auditArr.getJSONObject(i)
                auditLogs.add(
                    AuditLogEntity(
                        id = obj.getLong("id"),
                        actionType = obj.optString("actionType", "INFO"),
                        entityType = obj.optString("entityType", "SYSTEM"),
                        entityId = if (obj.isNull("entityId")) null else obj.optLong("entityId"),
                        summary = obj.optString("summary", ""),
                        details = obj.optString("details", ""),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    )
                )
            }

            BackupValidationResult(
                isValid = true,
                checksumVerified = checksumVerified,
                createdAt = createdAt,
                formatVersion = version,
                warnings = warnings,
                data = ParsedBackupData(
                    settings = settings,
                    customers = customers,
                    transactions = transactions,
                    reminders = reminders,
                    transfers = transfers,
                    messageLogs = messageLogs,
                    smsTemplates = smsTemplates,
                    auditLogs = auditLogs,
                ),
            )
        } catch (e: Exception) {
            BackupValidationResult(
                isValid = false,
                checksumVerified = false,
                createdAt = 0L,
                formatVersion = 0,
                errorMessage = "ملف JSON غير صالح أو تالف: ${e.message}",
            )
        }
    }

    suspend fun restoreFromValidatedBackup(
        validation: BackupValidationResult,
        context: Context? = null,
    ): Result<String> {
        val data = validation.data
        if (!validation.isValid || data == null) {
            return Result.failure(IllegalArgumentException(validation.errorMessage ?: "النسخة غير صالحة"))
        }

        val existingCustomersCount = database.customerDao().getAllCustomersOnce().size
        if (data.customers.isEmpty() && existingCustomersCount > 0) {
            AppDiagnosticLogger.logWarn(context, "Backup", "Blocked empty JSON backup restore over $existingCustomersCount existing customers")
            return Result.failure(
                IllegalStateException("ملف النسخة الاحتياطية لا يحتوي على عملاء؛ تم إيقاف الاستعادة لحماية بيانات العملاء الحالية.")
            )
        }

        return try {
            if (context != null) {
                createLocalSafetyBackup(context, "pre_json_restore")
            }
            database.withTransaction {
                database.reminderDao().deleteAll()
                database.debtsDao().deleteAll()
                database.paymentsDao().deleteAll()
                database.transactionDao().deleteAll()
                database.transferDao().deleteAll()
                database.messageLogDao().deleteAll()
                database.smsTemplateDao().deleteAll()
                database.auditLogDao().deleteAll()
                database.customerDao().deleteAll()

                data.customers.forEach { database.customerDao().insert(it) }
            data.transactions.forEach { tx ->
                database.transactionDao().insert(tx)
                if (tx.type.isDebit) {
                    database.debtsDao().insertDebt(
                        DebtsEntity(
                            customerId = tx.customerId,
                            amount = tx.amount,
                            description = tx.description,
                            isOpeningBalance = tx.type == TransactionType.OPENING_BALANCE,
                            linkedTransactionId = tx.id,
                            referenceNumber = tx.referenceNumber,
                            createdAt = tx.createdAt,
                            isReversed = tx.isReversed,
                        )
                    )
                } else {
                    database.paymentsDao().insertPayment(
                        PaymentsEntity(
                            customerId = tx.customerId,
                            amount = tx.amount,
                            description = tx.description,
                            isSettlement = tx.type == TransactionType.SETTLEMENT,
                            linkedTransactionId = tx.id,
                            referenceNumber = tx.referenceNumber,
                            paymentDate = tx.transactionDate,
                            createdAt = tx.createdAt,
                            isReversed = tx.isReversed,
                        )
                    )
                }
            }
            data.reminders.forEach { database.reminderDao().insert(it) }
            data.transfers.forEach { database.transferDao().insert(it) }
            data.messageLogs.forEach { database.messageLogDao().insert(it) }
            if (data.smsTemplates.isEmpty()) {
                SmartDebtDatabase.seedDefaultTemplates(database.smsTemplateDao())
            } else {
                data.smsTemplates.forEach { database.smsTemplateDao().insert(it) }
            }
            data.auditLogs.forEach { database.auditLogDao().insert(it) }
            database.auditLogDao().insert(
                AuditLogEntity(
                    actionType = "RESTORE_BACKUP",
                    entityType = "SYSTEM",
                    summary = "استعادة نسخة احتياطية كاملة (${data.customers.size} عميل، ${data.transactions.size} عملية)",
                    createdAt = System.currentTimeMillis(),
                )
            )
        }

            data.settings?.let { settingsDataStore.applyRestoredSettings(it) }
            AppDiagnosticLogger.logInfo(context, "Backup", "JSON backup restored: ${data.customers.size} customers, ${data.transactions.size} txs")
            Result.success("تمت استعادة النسخة الاحتياطية بنجاح (${data.customers.size} عميل، ${data.transactions.size} عملية).")
        } catch (t: Throwable) {
            AppDiagnosticLogger.logError(context, "Backup", "JSON backup restore failed", t)
            Result.failure(t)
        }
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
