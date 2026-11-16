package com.example.data.importexport

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import androidx.room.withTransaction
import com.example.data.ai.AiImportAnalysis
import com.example.data.ai.AiImportColumnProcessor
import com.example.data.local.AuditLogEntity
import com.example.data.local.CustomerEntity
import com.example.data.local.DebtsEntity
import com.example.data.local.PaymentsEntity
import com.example.data.local.SmartDebtDatabase
import com.example.data.local.TransactionEntity
import com.example.data.local.TransactionType
import com.example.util.AppDiagnosticLogger
import com.example.util.ErrorLogger
import com.example.util.FormatUtils
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.ZipInputStream

enum class ImportFileFormat(val labelAr: String) {
    SQLITE_DB("قاعدة بيانات SQLite (.db / .sqlite3)"),
    CSV("ملف قيم مفصولة (CSV)"),
    XLSX("ملف Excel حديث (XLSX)"),
    XLS("ملف Excel (XLS)"),
    JSON("ملف JSON / نسخة احتياطية"),
}

data class DiscoveredTable(
    val name: String,
    val columns: List<String>,
    val rows: List<Map<String, String>>,
)

data class ColumnMapping(
    val nameCol: String? = null,
    val phoneCol: String? = null,
    val accountCol: String? = null,
    val balanceCol: String? = null,
    val debtCol: String? = null,
    val paymentCol: String? = null,
    val dateCol: String? = null,
    val notesCol: String? = null,
)

enum class PreviewRowStatus(val labelAr: String) {
    READY_NEW("جديد وجاهز للاستيراد"),
    DUPLICATE_EXISTING("مكرر (موجود مسبقاً)"),
    DUPLICATE_IN_FILE("مكرر داخل الملف"),
    INVALID_ERROR("خطأ في البيانات"),
}

data class ImportedLinkedTx(
    val type: TransactionType,
    val amount: Double,
    val description: String,
    val dateMillis: Long,
)

data class ImportPreviewRow(
    val rowNumber: Int,
    val name: String,
    val phone: String,
    val accountNumber: String,
    val openingBalance: Double,
    val totalDebts: Double,
    val totalPayments: Double,
    val dateMillis: Long?,
    val notes: String,
    val status: PreviewRowStatus,
    val reason: String = "",
    val hasExplicitBreakdown: Boolean = false,
    val linkedTransactions: List<ImportedLinkedTx> = emptyList(),
)

data class ImportInspectionResult(
    val fileName: String,
    val format: ImportFileFormat,
    val detectedSourceApp: String,
    val tables: List<DiscoveredTable>,
    val selectedTableName: String,
    val mapping: ColumnMapping,
    val previewRows: List<ImportPreviewRow>,
    val linkedTransactionsCount: Int = 0,
    val warnings: List<String> = emptyList(),
    val aiAnalysis: AiImportAnalysis? = null,
    val replaceExistingMode: Boolean = false,
)

data class ImportExecutionReport(
    val sourceApp: String,
    val importedCustomersCount: Int,
    val importedTransactionsCount: Int,
    val skippedDuplicatesCount: Int,
    val errorRowsCount: Int,
    val errors: List<String>,
    val backupFilePath: String?,
    val replacedPreviousData: Boolean = false,
)

class ExternalDbInspector(
    private val database: SmartDebtDatabase,
    private val backupManager: BackupManager,
) {
    companion object {
        private const val MAX_IN_MEMORY_FILE_BYTES = 32L * 1024L * 1024L
        private const val MAX_SQLITE_ROWS_PER_TABLE = 10_000
        private const val SQLITE_PAGE_BATCH_SIZE = 1_000
        private const val MAX_CELL_TEXT_CHARS = 1_024
    }

    suspend fun inspectUri(context: Context, uri: Uri): Result<ImportInspectionResult> {
        val tempFile = File(context.cacheDir, "import_preview_${System.currentTimeMillis()}.tmp")
        return try {
            val fileName = runCatching { resolveFileName(context, uri) }.getOrDefault("import_file")
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
                true
            } ?: false
            if (!copied || !tempFile.exists()) {
                AppDiagnosticLogger.logWarn(context, "Import", "Failed opening URI stream: $uri")
                return Result.failure(IllegalStateException("تعذر فتح الملف المحدد أو انتهت صلاحية الوصول إليه."))
            }
            inspectTempOrRawFile(tempFile, fileName, context)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            AppDiagnosticLogger.logError(context, "Import", "inspectUri failure", t)
            Result.failure(IllegalStateException("تعذر قراءة الملف: ${t.message ?: "ملف غير صالح أو تالف"}"))
        } finally {
            runCatching { tempFile.delete() }
        }
    }

    suspend fun inspectRawFile(file: File, context: Context? = null): Result<ImportInspectionResult> {
        return try {
            inspectTempOrRawFile(file, file.name, context)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            AppDiagnosticLogger.logError(context, "Import", "inspectRawFile failure for ${file.name}", t)
            Result.failure(IllegalStateException("تعذر قراءة الملف: ${t.message ?: "ملف غير صالح"}"))
        }
    }

    private suspend fun inspectTempOrRawFile(
        file: File,
        fileName: String,
        context: Context? = null,
    ): Result<ImportInspectionResult> {
        if (!file.exists() || file.length() <= 0L) {
            AppDiagnosticLogger.logWarn(context, "Import", "Empty or missing file: $fileName")
            return Result.failure(IllegalStateException("الملف المحدد فارغ ولا يحتوي على أي بيانات."))
        }
        val header = readHeaderPrefix(file, 64)
        val format = detectFormat(fileName, header)
        if (format == ImportFileFormat.SQLITE_DB) {
            return inspectSqliteFile(file, fileName, context)
        }
        if (file.length() > MAX_IN_MEMORY_FILE_BYTES) {
            AppDiagnosticLogger.logWarn(context, "Import", "Oversized non-SQLite file (${file.length()} bytes): $fileName")
            return Result.failure(IllegalStateException("حجم الملف يتجاوز الحد الآمن للمعالجة (32 ميجابايت)."))
        }
        val bytes = file.readBytes()
        return when (format) {
            ImportFileFormat.SQLITE_DB -> inspectSqliteFile(file, fileName, context)
            ImportFileFormat.CSV -> inspectCsvBytes(bytes, fileName, context)
            ImportFileFormat.XLSX -> inspectXlsxBytes(bytes, fileName, context)
            ImportFileFormat.XLS -> inspectXlsBytes(bytes, fileName, context)
            ImportFileFormat.JSON -> inspectJsonBytes(bytes, fileName, context)
        }
    }

    private fun readHeaderPrefix(file: File, maxLen: Int): ByteArray {
        val buf = ByteArray(maxLen)
        val count = file.inputStream().use { it.read(buf) }
        return if (count <= 0) ByteArray(0) else buf.copyOf(count)
    }

    suspend fun runAiReanalysis(inspection: ImportInspectionResult): ImportInspectionResult {
        val table = inspection.tables.find { it.name == inspection.selectedTableName }
            ?: inspection.tables.firstOrNull()
            ?: return inspection
        val baseMapping = suggestColumnMapping(table.columns)
        val analysis = AiImportColumnProcessor.analyzeAndRefine(table, baseMapping, inspection.detectedSourceApp)
        val updated = inspection.copy(aiAnalysis = analysis)
        return rebuildPreview(updated, table.name, analysis.refinedMapping, inspection.replaceExistingMode)
    }

    suspend fun rebuildPreview(
        inspection: ImportInspectionResult,
        tableName: String,
        mapping: ColumnMapping,
        replaceExisting: Boolean = inspection.replaceExistingMode,
    ): ImportInspectionResult {
        val table = inspection.tables.find { it.name == tableName }
            ?: inspection.tables.firstOrNull()
            ?: return inspection
        val existingCustomers = if (replaceExisting) emptyList() else database.customerDao().getAllCustomersOnce()
        val existingNames = existingCustomers.map { FormatUtils.normalizeArabicText(it.name) }.toSet()
        val existingPhones = existingCustomers.mapNotNull {
            it.phone.takeIf { p -> p.isNotBlank() }?.let { p -> FormatUtils.normalizePhone(p) }?.takeIf { n -> n.count { c -> c.isDigit() } >= 5 }
        }.toSet()

        val seenInFile = mutableSetOf<String>()
        val linkedByCustomerKey = extractLinkedTransactionsByCustomerKey(inspection.tables, table)
        val idCol = findCustomerIdColumn(table.columns)
        var totalLinkedCount = 0

        val previewRows = table.rows.mapIndexed { idx, row ->
            val rawName = mapping.nameCol?.let { row[it] }.orEmpty().trim()
            val rawPhone = mapping.phoneCol?.let { row[it] }.orEmpty().trim()
            val cleanNormalized = FormatUtils.normalizePhone(rawPhone)
            val normPhone = if (cleanNormalized.count { it.isDigit() } >= 5) cleanNormalized else ""
            val phoneNoteWarning = if (rawPhone.isNotBlank() && normPhone.isEmpty()) " (تم تجاهل صيغة هاتف غير صالحة)" else ""
            val accountNo = mapping.accountCol?.let { row[it] }.orEmpty().trim()
            val notes = (mapping.notesCol?.let { row[it] }.orEmpty().trim() + phoneNoteWarning).trim()
            val dateMillis = mapping.dateCol?.let { parseFlexibleDate(row[it].orEmpty()) }

            val rowKey = idCol?.let { row[it]?.trim() }.takeUnless { it.isNullOrBlank() }
            val linkedTxs = if (rowKey != null) linkedByCustomerKey[rowKey].orEmpty() else emptyList()

            val debtVal = mapping.debtCol?.let { FormatUtils.parsePositiveAmount(row[it].orEmpty()) }
            val payVal = mapping.paymentCol?.let { FormatUtils.parsePositiveAmount(row[it].orEmpty()) }
            val balVal = mapping.balanceCol?.let { FormatUtils.parsePositiveAmount(row[it].orEmpty()) }

            val hasLinkedTxs = linkedTxs.isNotEmpty()
            val hasBreakdown = hasLinkedTxs || debtVal != null || payVal != null
            val effectiveDebt = if (hasLinkedTxs) {
                linkedTxs.filter { it.type.isDebit }.sumOf { it.amount }
            } else {
                debtVal ?: 0.0
            }
            val effectivePay = if (hasLinkedTxs) {
                linkedTxs.filter { !it.type.isDebit }.sumOf { it.amount }
            } else {
                payVal ?: 0.0
            }
            val effectiveOpening = when {
                hasBreakdown -> 0.0
                balVal != null -> balVal
                else -> 0.0
            }

            when {
                rawName.isBlank() -> ImportPreviewRow(
                    rowNumber = idx + 1,
                    name = "(بدون اسم)",
                    phone = normPhone,
                    accountNumber = accountNo,
                    openingBalance = 0.0,
                    totalDebts = 0.0,
                    totalPayments = 0.0,
                    dateMillis = dateMillis,
                    notes = notes,
                    status = PreviewRowStatus.INVALID_ERROR,
                    reason = "اسم العميل فارغ",
                )
                effectiveDebt < 0.0 || effectivePay < 0.0 || effectiveOpening < 0.0 -> ImportPreviewRow(
                    rowNumber = idx + 1,
                    name = rawName,
                    phone = normPhone,
                    accountNumber = accountNo,
                    openingBalance = effectiveOpening,
                    totalDebts = effectiveDebt,
                    totalPayments = effectivePay,
                    dateMillis = dateMillis,
                    notes = notes,
                    status = PreviewRowStatus.INVALID_ERROR,
                    reason = "قيمة مالية سالبة مرفوضة",
                )
                FormatUtils.normalizeArabicText(rawName) in existingNames ||
                    (normPhone.length >= 5 && normPhone in existingPhones) -> ImportPreviewRow(
                    rowNumber = idx + 1,
                    name = rawName,
                    phone = normPhone,
                    accountNumber = accountNo,
                    openingBalance = effectiveOpening,
                    totalDebts = effectiveDebt,
                    totalPayments = effectivePay,
                    dateMillis = dateMillis,
                    notes = notes,
                    status = PreviewRowStatus.DUPLICATE_EXISTING,
                    reason = "العميل أو رقم الهاتف موجود مسبقاً في قاعدة البيانات",
                    hasExplicitBreakdown = hasBreakdown,
                    linkedTransactions = linkedTxs,
                )
                !seenInFile.add(FormatUtils.normalizeArabicText(rawName)) -> ImportPreviewRow(
                    rowNumber = idx + 1,
                    name = rawName,
                    phone = normPhone,
                    accountNumber = accountNo,
                    openingBalance = effectiveOpening,
                    totalDebts = effectiveDebt,
                    totalPayments = effectivePay,
                    dateMillis = dateMillis,
                    notes = notes,
                    status = PreviewRowStatus.DUPLICATE_IN_FILE,
                    reason = "عميل مكرر داخل نفس الملف",
                    hasExplicitBreakdown = hasBreakdown,
                    linkedTransactions = linkedTxs,
                )
                else -> {
                    totalLinkedCount += linkedTxs.size
                    ImportPreviewRow(
                        rowNumber = idx + 1,
                        name = rawName,
                        phone = normPhone,
                        accountNumber = accountNo,
                        openingBalance = effectiveOpening,
                        totalDebts = effectiveDebt,
                        totalPayments = effectivePay,
                        dateMillis = dateMillis,
                        notes = notes,
                        status = PreviewRowStatus.READY_NEW,
                        reason = when {
                            hasLinkedTxs -> "مرتبط بـ ${linkedTxs.size} عملية حقيقية من جدول العمليات"
                            !hasBreakdown && effectiveOpening > 0.0 ->
                                "سيُسجل الرصيد (${FormatUtils.formatAmount(effectiveOpening)}) كرصيد افتتاحي موثق دون اختلاق عمليات"
                            else -> "جاهز للاستيراد"
                        },
                        hasExplicitBreakdown = hasBreakdown,
                        linkedTransactions = linkedTxs,
                    )
                }
            }
        }

        val analysis = inspection.aiAnalysis ?: AiImportColumnProcessor.analyzeAndRefine(
            table = table,
            baseMapping = mapping,
            detectedSourceApp = inspection.detectedSourceApp,
        )
        return inspection.copy(
            selectedTableName = table.name,
            mapping = mapping,
            previewRows = previewRows,
            linkedTransactionsCount = totalLinkedCount,
            aiAnalysis = analysis,
            replaceExistingMode = replaceExisting,
        )
    }

    suspend fun executeImport(
        context: Context?,
        inspection: ImportInspectionResult,
        createSafetyBackup: Boolean = true,
        replacePreviousData: Boolean = inspection.replaceExistingMode,
    ): ImportExecutionReport {
        val shouldBackup = createSafetyBackup || replacePreviousData
        val backupPath = if (shouldBackup && context != null) {
            runCatching {
                backupManager.createLocalSafetyBackup(
                    context,
                    if (replacePreviousData) "pre_replace_import" else "pre_import",
                ).absolutePath
            }.onFailure { err ->
                ErrorLogger.logImportException(context, "ImportBackup", "Failed pre-import safety backup", err)
            }.getOrNull()
        } else {
            null
        }

        var importedCustomers = 0
        var importedTransactions = 0
        var skippedDuplicates = 0
        val errors = mutableListOf<String>()
        val now = System.currentTimeMillis()

        try {
            database.withTransaction {
            val customerDao = database.customerDao()
            val txDao = database.transactionDao()
            val debtsDao = database.debtsDao()
            val paymentsDao = database.paymentsDao()
            val auditDao = database.auditLogDao()

            if (replacePreviousData) {
                database.reminderDao().deleteAll()
                debtsDao.deleteAll()
                paymentsDao.deleteAll()
                txDao.deleteAll()
                database.transferDao().deleteAll()
                customerDao.deleteAll()
                auditDao.insert(
                    AuditLogEntity(
                        actionType = "CLEAR_BEFORE_IMPORT",
                        entityType = "IMPORT",
                        summary = "إزالة البيانات السابقة قبل استيراد ${inspection.fileName}",
                        details = "نسخة الأمان المسبقة: ${backupPath ?: "غير متوفرة"}",
                        createdAt = now,
                    )
                )
            }

            suspend fun insertSyncedTransaction(tx: TransactionEntity) {
                val txId = txDao.insert(tx)
                if (tx.type.isDebit) {
                    debtsDao.insertDebt(
                        DebtsEntity(
                            customerId = tx.customerId,
                            amount = tx.amount,
                            description = tx.description,
                            isOpeningBalance = tx.type == TransactionType.OPENING_BALANCE,
                            linkedTransactionId = txId,
                            referenceNumber = tx.referenceNumber,
                            createdAt = tx.createdAt,
                        )
                    )
                } else {
                    paymentsDao.insertPayment(
                        PaymentsEntity(
                            customerId = tx.customerId,
                            amount = tx.amount,
                            description = tx.description,
                            isSettlement = tx.type == TransactionType.SETTLEMENT,
                            linkedTransactionId = txId,
                            referenceNumber = tx.referenceNumber,
                            paymentDate = tx.transactionDate,
                            createdAt = tx.createdAt,
                        )
                    )
                }
            }

            for (row in inspection.previewRows) {
                when (row.status) {
                    PreviewRowStatus.DUPLICATE_EXISTING, PreviewRowStatus.DUPLICATE_IN_FILE -> {
                        skippedDuplicates++
                    }
                    PreviewRowStatus.INVALID_ERROR -> {
                        errors.add("سطر ${row.rowNumber} (${row.name}): ${row.reason}")
                    }
                    PreviewRowStatus.READY_NEW -> {
                        val existingByName = customerDao.findByExactName(row.name)
                        val existingByPhone = if (row.phone.length >= 5) customerDao.findByPhone(row.phone) else null
                        if (existingByName != null || existingByPhone != null) {
                            skippedDuplicates++
                            continue
                        }
                        val accNo = row.accountNumber.ifBlank { "IMP-${row.rowNumber}-${now % 1000}" }
                        val customerId = customerDao.insert(
                            CustomerEntity(
                                name = row.name,
                                phone = row.phone,
                                accountNumber = accNo,
                                notes = row.notes,
                                createdAt = now,
                                updatedAt = now,
                            )
                        )
                        importedCustomers++
                        val txDate = row.dateMillis ?: now

                        if (row.linkedTransactions.isNotEmpty()) {
                            row.linkedTransactions.forEach { ltx ->
                                if (ltx.amount > 0.0) {
                                    insertSyncedTransaction(
                                        TransactionEntity(
                                            customerId = customerId,
                                            type = ltx.type,
                                            amount = ltx.amount,
                                            description = ltx.description.ifBlank { "${ltx.type.labelAr} مستوردة" },
                                            sourceApp = inspection.detectedSourceApp,
                                            transactionDate = ltx.dateMillis,
                                            createdAt = now,
                                        )
                                    )
                                    importedTransactions++
                                }
                            }
                        } else if (row.hasExplicitBreakdown) {
                            if (row.totalDebts > 0.0) {
                                insertSyncedTransaction(
                                    TransactionEntity(
                                        customerId = customerId,
                                        type = TransactionType.DEBT,
                                        amount = row.totalDebts,
                                        description = "دين مستورد من ${inspection.detectedSourceApp}",
                                        sourceApp = inspection.detectedSourceApp,
                                        transactionDate = txDate,
                                        createdAt = now,
                                    )
                                )
                                importedTransactions++
                            }
                            if (row.totalPayments > 0.0) {
                                insertSyncedTransaction(
                                    TransactionEntity(
                                        customerId = customerId,
                                        type = TransactionType.PAYMENT,
                                        amount = row.totalPayments,
                                        description = "دفعة مستوردة من ${inspection.detectedSourceApp}",
                                        sourceApp = inspection.detectedSourceApp,
                                        transactionDate = txDate,
                                        createdAt = now,
                                    )
                                )
                                importedTransactions++
                            }
                        } else if (row.openingBalance > 0.0) {
                            // Rule: Record available balance as a documented opening balance without fabricating history
                            insertSyncedTransaction(
                                TransactionEntity(
                                    customerId = customerId,
                                    type = TransactionType.OPENING_BALANCE,
                                    amount = row.openingBalance,
                                    description = "رصيد افتتاحي موثق مستورد من ${inspection.detectedSourceApp}",
                                    sourceApp = inspection.detectedSourceApp,
                                    transactionDate = txDate,
                                    createdAt = now,
                                )
                            )
                            importedTransactions++
                        }
                    }
                }
            }

                auditDao.insert(
                    AuditLogEntity(
                        actionType = "IMPORT_DATA",
                        entityType = "IMPORT",
                        summary = "استيراد بيانات من ${inspection.detectedSourceApp}: $importedCustomers عميل، $importedTransactions عملية",
                        details = "الملف: ${inspection.fileName} | متجاهل مكرر: $skippedDuplicates | أخطاء: ${errors.size}",
                        createdAt = now,
                    )
                )
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            ErrorLogger.logImportException(context, "ExecuteImport", "Transaction failed during import of ${inspection.fileName}", t)
            throw t
        }

        AppDiagnosticLogger.logInfo(
            context,
            "Import",
            "Executed import from ${inspection.detectedSourceApp}: importedCustomers=$importedCustomers, tx=$importedTransactions, skipped=$skippedDuplicates, errors=${errors.size}",
        )

        return ImportExecutionReport(
            sourceApp = inspection.detectedSourceApp,
            importedCustomersCount = importedCustomers,
            importedTransactionsCount = importedTransactions,
            skippedDuplicatesCount = skippedDuplicates,
            errorRowsCount = errors.size,
            errors = errors,
            backupFilePath = backupPath,
            replacedPreviousData = replacePreviousData,
        )
    }

    private data class PreparedSqliteCopy(
        val file: File,
        val isTemp: Boolean,
        val warning: String? = null,
    ) {
        fun cleanup() {
            if (isTemp) {
                runCatching { file.delete() }
                runCatching { File("${file.absolutePath}-wal").delete() }
                runCatching { File("${file.absolutePath}-shm").delete() }
            }
        }
    }

    private suspend fun inspectSqliteFile(
        file: File,
        fileName: String,
        context: Context? = null,
    ): Result<ImportInspectionResult> {
        val integrity = backupManager.validateDatabaseIntegrity(context, file)
        if (!integrity.isValid) {
            val msg = integrity.errorMessage ?: "فشل التحقق من سلامة قاعدة البيانات SQLite."
            AppDiagnosticLogger.logError(context, "Import", "validateDatabaseIntegrity failed for $fileName: $msg")
            return Result.failure(IllegalStateException(msg))
        }

        val preparedCopy = prepareSafeReadOnlySqliteCopy(file)
        val tables = mutableListOf<DiscoveredTable>()
        val warnings = mutableListOf<String>()
        preparedCopy.warning?.let { warnings.add(it) }
        integrity.warnings.forEach { if (it !in warnings) warnings.add(it) }
        var detectedApp = "قاعدة بيانات SQLite"
        try {
            val db = SQLiteDatabase.openDatabase(
                preparedCopy.file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
            )
            db.use { sqlite ->
                // Secondary PRAGMA quick_check confirmation on read-only copy
                var integrityOk = false
                var quickCheckMsg = ""
                sqlite.rawQuery("PRAGMA quick_check", null).use { qc ->
                    if (qc.moveToFirst()) {
                        quickCheckMsg = qc.getString(0).orEmpty()
                        integrityOk = quickCheckMsg.equals("ok", ignoreCase = true)
                    }
                }
                if (!integrityOk) {
                    AppDiagnosticLogger.logError(context, "Import", "PRAGMA quick_check failed for $fileName: $quickCheckMsg")
                    return Result.failure(
                        IllegalStateException("فشل فحص سلامة قاعدة البيانات (PRAGMA quick_check): الملف تالف أو غير مكتمل ($quickCheckMsg).")
                    )
                }

                val tableNames = mutableListOf<String>()
                sqlite.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata' AND name != 'room_master_table'",
                    null,
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        cursor.getString(0)?.takeIf { it.isNotBlank() }?.let { tableNames.add(it) }
                    }
                }

                detectedApp = detectAccountingAppFromTables(tableNames, fileName)

                for (tbl in tableNames) {
                    val escapedTbl = tbl.replace("\"", "\"\"")
                    runCatching {
                        val columns = mutableListOf<String>()
                        sqlite.rawQuery("PRAGMA table_info(\"$escapedTbl\")", null).use { c ->
                            val nameIdx = c.getColumnIndex("name")
                            while (c.moveToNext()) {
                                if (nameIdx >= 0) {
                                    c.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { columns.add(it) }
                                }
                            }
                        }
                        if (columns.isEmpty()) return@runCatching

                        val totalRows = runCatching {
                            sqlite.rawQuery("SELECT COUNT(*) FROM \"$escapedTbl\"", null).use { cc ->
                                if (cc.moveToFirst()) cc.getInt(0) else 0
                            }
                        }.getOrDefault(0)

                        val rows = ArrayList<Map<String, String>>(totalRows.coerceIn(0, MAX_SQLITE_ROWS_PER_TABLE))
                        var offset = 0
                        while (rows.size < MAX_SQLITE_ROWS_PER_TABLE) {
                            val batchLimit = (MAX_SQLITE_ROWS_PER_TABLE - rows.size).coerceAtMost(SQLITE_PAGE_BATCH_SIZE)
                            var fetchedInBatch = 0
                            sqlite.rawQuery(
                                "SELECT * FROM \"$escapedTbl\" LIMIT ? OFFSET ?",
                                arrayOf(batchLimit.toString(), offset.toString()),
                            ).use { c ->
                                val colCount = c.columnCount
                                val canonicalKeys = Array(colCount) { idx ->
                                    columns.getOrElse(idx) { c.getColumnName(idx) ?: "col_$idx" }
                                }
                                while (c.moveToNext()) {
                                    fetchedInBatch++
                                    val map = LinkedHashMap<String, String>(colCount)
                                    for (i in 0 until colCount) {
                                        val colName = canonicalKeys[i]
                                        val value = runCatching {
                                            when (c.getType(i)) {
                                                android.database.Cursor.FIELD_TYPE_NULL -> ""
                                                android.database.Cursor.FIELD_TYPE_INTEGER -> c.getLong(i).toString()
                                                android.database.Cursor.FIELD_TYPE_FLOAT -> {
                                                    val d = c.getDouble(i)
                                                    if (!d.isFinite()) "" else if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
                                                }
                                                android.database.Cursor.FIELD_TYPE_BLOB -> "[بيانات ثنائية]"
                                                else -> {
                                                    val s = c.getString(i).orEmpty()
                                                    if (s.length > MAX_CELL_TEXT_CHARS) s.take(MAX_CELL_TEXT_CHARS) else s
                                                }
                                            }
                                        }.getOrDefault("")
                                        map[colName] = value
                                    }
                                    rows.add(map)
                                }
                            }
                            if (fetchedInBatch < batchLimit) break
                            offset += fetchedInBatch
                        }
                        if (totalRows > MAX_SQLITE_ROWS_PER_TABLE) {
                            warnings.add("جدول ($tbl) يحتوي على $totalRows صفاً؛ تمت قراءة أول $MAX_SQLITE_ROWS_PER_TABLE صفوف لحماية الذاكرة.")
                        }
                        tables.add(DiscoveredTable(name = tbl, columns = columns, rows = rows))
                    }.onFailure { err ->
                        AppDiagnosticLogger.logWarn(context, "Import", "Skipped unreadable table $tbl: ${err.message}")
                    }
                }
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            AppDiagnosticLogger.logError(context, "Import", "Failed opening SQLite database $fileName", t)
            val msgLower = t.message.orEmpty().lowercase(Locale.ROOT)
            val friendlyMsg = if (msgLower.contains("not a database") || msgLower.contains("encrypt") || msgLower.contains("cipher")) {
                "قاعدة البيانات مشفرة أو غير مدعومة ($fileName)."
            } else {
                "فشل فتح قاعدة البيانات ($fileName): ${t.message ?: "قاعدة تالفة أو مشفرة"}"
            }
            return Result.failure(IllegalStateException(friendlyMsg))
        } finally {
            preparedCopy.cleanup()
        }

        if (tables.isEmpty()) {
            return Result.failure(IllegalStateException("لم يتم العثور على جداول بيانات قابلة للقراءة داخل ملف SQLite."))
        }

        val bestTable = pickBestCustomerTable(tables)
        val baseMapping = suggestColumnMapping(bestTable.columns)
        val aiAnalysis = AiImportColumnProcessor.analyzeAndRefine(bestTable, baseMapping, detectedApp)
        if (!hasCustomerSchemaSignals(tables)) {
            warnings.add("تنبيه: لم يتم اكتشاف أعمدة عملاء قياسية تلقائياً؛ يرجى مطابقة الأعمدة يدوياً قبل الاستيراد.")
        }
        aiAnalysis.anomalyWarnings.forEach { if (it !in warnings) warnings.add(it) }
        val base = ImportInspectionResult(
            fileName = fileName,
            format = ImportFileFormat.SQLITE_DB,
            detectedSourceApp = detectedApp,
            tables = tables,
            selectedTableName = bestTable.name,
            mapping = aiAnalysis.refinedMapping,
            previewRows = emptyList(),
            warnings = warnings,
            aiAnalysis = aiAnalysis,
        )
        return Result.success(rebuildPreview(base, bestTable.name, aiAnalysis.refinedMapping))
    }

    private fun prepareSafeReadOnlySqliteCopy(original: File): PreparedSqliteCopy {
        val walFile = File("${original.absolutePath}-wal")
        val shmFile = File("${original.absolutePath}-shm")
        val header = readHeaderPrefix(original, 20)
        val isWalHeader = header.size >= 20 && (header[18].toInt() == 2 || header[19].toInt() == 2)
        val hasWalSidecar = walFile.exists() && walFile.length() > 0L

        if (!isWalHeader && !hasWalSidecar) {
            return PreparedSqliteCopy(file = original, isTemp = false)
        }

        val tempDir = original.parentFile?.takeIf { it.canWrite() }
            ?: File(System.getProperty("java.io.tmpdir") ?: ".")
        val temp = File.createTempFile("safe_ro_", ".db", tempDir)
        original.inputStream().use { input ->
            temp.outputStream().use { output -> input.copyTo(output) }
        }

        if (hasWalSidecar) {
            // Preserve uncommitted WAL/SHM transactions safely on the temporary copy without touching originals
            val tempWal = File("${temp.absolutePath}-wal")
            val tempShm = File("${temp.absolutePath}-shm")
            runCatching {
                walFile.inputStream().use { input -> tempWal.outputStream().use { out -> input.copyTo(out) } }
                if (shmFile.exists()) {
                    shmFile.inputStream().use { input -> tempShm.outputStream().use { out -> input.copyTo(out) } }
                }
                val rwDb = SQLiteDatabase.openDatabase(
                    temp.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
                )
                rwDb.use { sqlite ->
                    sqlite.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
                }
            }
            runCatching { tempWal.delete() }
            runCatching { tempShm.delete() }
            normalizeHeaderFlagsForReadOnly(temp)
            return PreparedSqliteCopy(
                file = temp,
                isTemp = true,
                warning = "تم دمج معاملات ملف WAL المرفق على نسخة مؤقتة بأمان دون تعديل الأصل.",
            )
        }

        // Single-file WAL database without sidecar: clear WAL flags on temporary copy so OPEN_READONLY succeeds
        normalizeHeaderFlagsForReadOnly(temp)
        return PreparedSqliteCopy(
            file = temp,
            isTemp = true,
            warning = "الملف الأصلي كان في وضع WAL دون ملف (-wal) مرفق؛ تم فتح البيانات المدمجة في النسخة المؤقتة.",
        )
    }

    private fun normalizeHeaderFlagsForReadOnly(temp: File) {
        runCatching {
            if (temp.length() >= 20L) {
                RandomAccessFile(temp, "rw").use { raf ->
                    raf.seek(18)
                    val w = raf.read()
                    val r = raf.read()
                    if (w == 2 || r == 2) {
                        raf.seek(18)
                        raf.write(if (w == 2) 1 else w)
                        raf.write(if (r == 2) 1 else r)
                    }
                }
            }
        }
    }

    private fun hasCustomerSchemaSignals(tables: List<DiscoveredTable>): Boolean {
        val signals = listOf(
            "اسم العميل", "العميل", "الاسم", "اسم الحساب", "customer_name", "client_name", "acc_name", "name",
            "phone", "mobile", "الهاتف", "الجوال", "balance", "الرصيد", "debit", "debt", "الدين", "عليه",
        )
        return tables.any { tbl ->
            val tblNorm = FormatUtils.normalizeArabicText(tbl.name)
            if (tblNorm.contains("customer") || tblNorm.contains("client") || tblNorm.contains("account") ||
                tblNorm.contains("عملاء") || tblNorm.contains("حساب")
            ) {
                return@any true
            }
            tbl.columns.any { col ->
                val cNorm = FormatUtils.normalizeArabicText(col)
                signals.any { sig -> cNorm == sig || cNorm.contains(sig) }
            }
        }
    }

    private fun findCustomerIdColumn(columns: List<String>): String? {
        return columns.firstOrNull { col ->
            val n = FormatUtils.normalizeArabicText(col)
            n == "id" || n == "_id" || n == "cust_id" || n == "customer_id" || n == "client_id" || n == "acc_id"
        }
    }

    private fun extractLinkedTransactionsByCustomerKey(
        tables: List<DiscoveredTable>,
        customerTable: DiscoveredTable,
    ): Map<String, List<ImportedLinkedTx>> {
        val candidateTxTables = tables.filter { tbl ->
            if (tbl.name == customerTable.name || tbl.rows.isEmpty()) return@filter false
            val n = tbl.name.lowercase(Locale.ROOT)
            n.contains("trans") || n.contains("oper") || n.contains("debt") || n.contains("pay") ||
                n.contains("voucher") || n.contains("journal") || n.contains("ledger") ||
                n.contains("عمليات") || n.contains("قيود") || n.contains("ديون") || n.contains("حركات")
        }
        if (candidateTxTables.isEmpty()) return emptyMap()

        val result = mutableMapOf<String, MutableList<ImportedLinkedTx>>()
        for (txTable in candidateTxTables) {
            val fkCol = txTable.columns.firstOrNull { col ->
                val n = FormatUtils.normalizeArabicText(col)
                n in listOf("customer_id", "client_id", "cust_id", "acc_id", "account_id", "معرف العميل", "رقم العميل")
            } ?: continue

            val debitCol = txTable.columns.firstOrNull { col ->
                val n = FormatUtils.normalizeArabicText(col)
                n in listOf("debit", "debt", "عليه", "مدين", "المبلغ المدين")
            }
            val creditCol = txTable.columns.firstOrNull { col ->
                val n = FormatUtils.normalizeArabicText(col)
                n in listOf("credit", "paid", "payment", "له", "دائن", "المبلغ الدائن")
            }
            val amountCol = txTable.columns.firstOrNull { col ->
                val n = FormatUtils.normalizeArabicText(col)
                n in listOf("amount", "value", "total", "المبلغ", "القيمة")
            }
            val typeCol = txTable.columns.firstOrNull { col ->
                val n = FormatUtils.normalizeArabicText(col)
                n in listOf("type", "tx_type", "op_type", "kind", "النوع", "نوع العملية")
            }
            val descCol = txTable.columns.firstOrNull { col ->
                val n = FormatUtils.normalizeArabicText(col)
                n in listOf("description", "notes", "note", "details", "بيان", "البيان", "الوصف", "ملاحظات")
            }
            val dateCol = txTable.columns.firstOrNull { col ->
                val n = FormatUtils.normalizeArabicText(col)
                n in listOf("date", "transaction_date", "trans_date", "created_at", "التاريخ")
            }

            for (row in txTable.rows) {
                val custKey = row[fkCol]?.trim().orEmpty()
                if (custKey.isBlank()) continue
                val dateMs = dateCol?.let { parseFlexibleDate(row[it].orEmpty()) } ?: System.currentTimeMillis()
                val desc = descCol?.let { row[it] }.orEmpty().trim()

                if (debitCol != null || creditCol != null) {
                    val d = debitCol?.let { FormatUtils.parsePositiveAmount(row[it].orEmpty()) } ?: 0.0
                    val c = creditCol?.let { FormatUtils.parsePositiveAmount(row[it].orEmpty()) } ?: 0.0
                    if (d > 0.0) {
                        result.getOrPut(custKey) { mutableListOf() }
                            .add(ImportedLinkedTx(TransactionType.DEBT, d, desc.ifBlank { "دين مستورد" }, dateMs))
                    }
                    if (c > 0.0) {
                        result.getOrPut(custKey) { mutableListOf() }
                            .add(ImportedLinkedTx(TransactionType.PAYMENT, c, desc.ifBlank { "دفعة مستوردة" }, dateMs))
                    }
                } else if (amountCol != null) {
                    val amt = FormatUtils.parsePositiveAmount(row[amountCol].orEmpty()) ?: 0.0
                    if (amt <= 0.0) continue
                    val rawType = FormatUtils.normalizeArabicText(typeCol?.let { row[it] }.orEmpty())
                    val isPayment = rawType in listOf("payment", "pay", "credit", "paid", "0", "2", "دفعه", "سداد", "له", "دائن", "قبض")
                    val txType = if (isPayment) TransactionType.PAYMENT else TransactionType.DEBT
                    result.getOrPut(custKey) { mutableListOf() }
                        .add(ImportedLinkedTx(txType, amt, desc.ifBlank { txType.labelAr }, dateMs))
                }
            }
        }
        return result
    }

    private suspend fun inspectCsvBytes(
        bytes: ByteArray,
        fileName: String,
        context: Context? = null,
    ): Result<ImportInspectionResult> {
        return try {
            val text = decodeBestCharset(bytes).removePrefix("\uFEFF")
            val lines = text.lines().filter { it.isNotBlank() }
            if (lines.isEmpty()) {
                ErrorLogger.logImportWarning(context, "ImportCSV", "Empty CSV file: $fileName")
                return Result.failure(IllegalStateException("ملف CSV فارغ."))
            }
            val delimiter = detectCsvDelimiter(lines.first())
            val parsedRows = parseCsvLines(text, delimiter)
            if (parsedRows.size < 2) {
                ErrorLogger.logImportWarning(context, "ImportCSV", "Insufficient rows in CSV: $fileName")
                return Result.failure(IllegalStateException("ملف CSV لا يحتوي على صفوف بيانات كافية."))
            }
            val headers = parsedRows.first().mapIndexed { idx, h -> h.trim().ifBlank { "عمود_${idx + 1}" } }
            val rowMaps = parsedRows.drop(1).map { values ->
                val map = linkedMapOf<String, String>()
                headers.forEachIndexed { idx, h ->
                    map[h] = values.getOrElse(idx) { "" }.trim()
                }
                map
            }
            val table = DiscoveredTable(name = "بيانات CSV", columns = headers, rows = rowMaps)
            val baseMapping = suggestColumnMapping(headers)
            val detectedApp = detectAccountingAppFromTables(listOf(fileName), fileName)
            val aiAnalysis = AiImportColumnProcessor.analyzeAndRefine(table, baseMapping, detectedApp)
            val base = ImportInspectionResult(
                fileName = fileName,
                format = ImportFileFormat.CSV,
                detectedSourceApp = detectedApp,
                tables = listOf(table),
                selectedTableName = table.name,
                mapping = aiAnalysis.refinedMapping,
                previewRows = emptyList(),
                warnings = aiAnalysis.anomalyWarnings,
                aiAnalysis = aiAnalysis,
            )
            Result.success(rebuildPreview(base, table.name, aiAnalysis.refinedMapping))
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            ErrorLogger.logImportException(context, "ImportCSV", "CSV parsing error for $fileName", t)
            Result.failure(IllegalStateException("تعذر قراءة ملف CSV: ${t.message ?: "بنية غير صالحة"}"))
        }
    }

    private suspend fun inspectXlsxBytes(
        bytes: ByteArray,
        fileName: String,
        context: Context? = null,
    ): Result<ImportInspectionResult> {
        return try {
            val entries = mutableMapOf<String, ByteArray>()
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var entry = zip.nextEntry
                var totalUncompressed = 0L
                while (entry != null && totalUncompressed < MAX_IN_MEMORY_FILE_BYTES) {
                    if (!entry.isDirectory && (entry.name.startsWith("xl/sharedStrings") || entry.name.startsWith("xl/worksheets/"))) {
                        val entryBytes = zip.readBytes()
                        totalUncompressed += entryBytes.size
                        if (totalUncompressed <= MAX_IN_MEMORY_FILE_BYTES) {
                            entries[entry.name] = entryBytes
                        }
                    }
                    entry = zip.nextEntry
                }
            }
            val sharedStrings = entries["xl/sharedStrings.xml"]?.let { runCatching { parseSharedStringsXml(it) }.getOrNull() } ?: emptyList()
            val sheetPaths = entries.keys.filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }.sorted()
            if (sheetPaths.isEmpty()) {
                ErrorLogger.logImportWarning(context, "ImportXLSX", "Missing worksheet XML in $fileName")
                return Result.failure(IllegalStateException("ملف XLSX تالف أو لا يحتوي على أوراق عمل صالحة."))
            }

            val tables = mutableListOf<DiscoveredTable>()
            sheetPaths.forEachIndexed { sheetIdx, path ->
                val sheetBytes = entries[path] ?: return@forEachIndexed
                val matrix = runCatching { parseSheetXml(sheetBytes, sharedStrings) }.getOrDefault(emptyList())
                if (matrix.size >= 2) {
                    val headers = matrix.first().mapIndexed { idx, h -> h.trim().ifBlank { "عمود_${idx + 1}" } }
                    val rows = matrix.drop(1).filter { row -> row.any { it.isNotBlank() } }.map { row ->
                        val map = linkedMapOf<String, String>()
                        headers.forEachIndexed { idx, h ->
                            map[h] = row.getOrElse(idx) { "" }.trim()
                        }
                        map
                    }
                    tables.add(DiscoveredTable("ورقة_${sheetIdx + 1}", headers, rows))
                }
            }

            if (tables.isEmpty()) {
                ErrorLogger.logImportWarning(context, "ImportXLSX", "No valid data rows in XLSX: $fileName")
                return Result.failure(IllegalStateException("لا توجد صفوف بيانات صالحة داخل ملف XLSX."))
            }
            val best = pickBestCustomerTable(tables)
            val baseMapping = suggestColumnMapping(best.columns)
            val detectedApp = detectAccountingAppFromTables(tables.map { it.name }, fileName)
            val aiAnalysis = AiImportColumnProcessor.analyzeAndRefine(best, baseMapping, detectedApp)
            val base = ImportInspectionResult(
                fileName = fileName,
                format = ImportFileFormat.XLSX,
                detectedSourceApp = detectedApp,
                tables = tables,
                selectedTableName = best.name,
                mapping = aiAnalysis.refinedMapping,
                previewRows = emptyList(),
                warnings = aiAnalysis.anomalyWarnings,
                aiAnalysis = aiAnalysis,
            )
            Result.success(rebuildPreview(base, best.name, aiAnalysis.refinedMapping))
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            ErrorLogger.logImportException(context, "ImportXLSX", "XLSX parsing error for $fileName", t)
            Result.failure(IllegalStateException("ملف XLSX غير صالح أو تالف: ${t.message ?: "خطأ في قراءة الأرشيف"}"))
        }
    }

    private suspend fun inspectXlsBytes(
        bytes: ByteArray,
        fileName: String,
        context: Context? = null,
    ): Result<ImportInspectionResult> {
        return try {
            // Check if .xls is actually SpreadsheetML XML / HTML export (common in Arabic accounting apps)
            val headText = bytes.take(512).toByteArray().toString(Charsets.UTF_8).trimStart()
            if (headText.startsWith("<") && (headText.contains("Workbook", true) || headText.contains("html", true) || headText.contains("table", true))) {
                val tables = parseHtmlOrSpreadsheetMlXls(bytes.toString(Charsets.UTF_8))
                if (tables.isNotEmpty()) {
                    val best = pickBestCustomerTable(tables)
                    val baseMapping = suggestColumnMapping(best.columns)
                    val detectedApp = detectAccountingAppFromTables(tables.map { it.name }, fileName)
                    val aiAnalysis = AiImportColumnProcessor.analyzeAndRefine(best, baseMapping, detectedApp)
                    val base = ImportInspectionResult(
                        fileName = fileName,
                        format = ImportFileFormat.XLS,
                        detectedSourceApp = detectedApp,
                        tables = tables,
                        selectedTableName = best.name,
                        mapping = aiAnalysis.refinedMapping,
                        previewRows = emptyList(),
                        warnings = aiAnalysis.anomalyWarnings,
                        aiAnalysis = aiAnalysis,
                    )
                    return Result.success(rebuildPreview(base, best.name, aiAnalysis.refinedMapping))
                }
            }

            // Binary BIFF8 OLE2 Compound Document (.xls)
            val tables = parseBiff8Xls(bytes)
            if (tables.isEmpty()) {
                ErrorLogger.logImportWarning(context, "ImportXLS", "Could not extract tables from XLS: $fileName")
                return Result.failure(IllegalStateException("تعذر استخراج جداول من ملف XLS. يرجى التأكد من سلامة الملف أو حفظه بصيغة XLSX/CSV."))
            }
            val best = pickBestCustomerTable(tables)
            val baseMapping = suggestColumnMapping(best.columns)
            val detectedApp = detectAccountingAppFromTables(tables.map { it.name }, fileName)
            val aiAnalysis = AiImportColumnProcessor.analyzeAndRefine(best, baseMapping, detectedApp)
            val base = ImportInspectionResult(
                fileName = fileName,
                format = ImportFileFormat.XLS,
                detectedSourceApp = detectedApp,
                tables = tables,
                selectedTableName = best.name,
                mapping = aiAnalysis.refinedMapping,
                previewRows = emptyList(),
                warnings = aiAnalysis.anomalyWarnings,
                aiAnalysis = aiAnalysis,
            )
            Result.success(rebuildPreview(base, best.name, aiAnalysis.refinedMapping))
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            ErrorLogger.logImportException(context, "ImportXLS", "XLS parsing error for $fileName", t)
            Result.failure(IllegalStateException("فشل قراءة ملف XLS: ${t.message ?: "تنسيق غير مدعوم"}"))
        }
    }

    private suspend fun inspectJsonBytes(
        bytes: ByteArray,
        fileName: String,
        context: Context? = null,
    ): Result<ImportInspectionResult> {
        return try {
            val text = decodeBestCharset(bytes).removePrefix("\uFEFF").trim()
            val tables = mutableListOf<DiscoveredTable>()
            when {
                text.startsWith("[") -> {
                    val arr = JSONArray(text)
                    val tbl = jsonArrayToTable("بيانات JSON", arr)
                    if (tbl.rows.isNotEmpty()) tables.add(tbl)
                }
                text.startsWith("{") -> {
                    val obj = JSONObject(text)
                    val dataNode = obj.optJSONObject("data") ?: obj
                    val keys = dataNode.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val arr = dataNode.optJSONArray(key)
                        if (arr != null && arr.length() > 0 && arr.optJSONObject(0) != null) {
                            val tbl = jsonArrayToTable(key, arr)
                            if (tbl.rows.isNotEmpty()) tables.add(tbl)
                        }
                    }
                }
            }
            if (tables.isEmpty()) {
                ErrorLogger.logImportWarning(context, "ImportJSON", "No importable arrays found in JSON: $fileName")
                return Result.failure(IllegalStateException("ملف JSON لا يحتوي على مصفوفة سجلات قابلة للاستيراد."))
            }
            val best = pickBestCustomerTable(tables)
            val baseMapping = suggestColumnMapping(best.columns)
            val detectedApp = detectAccountingAppFromTables(tables.map { it.name }, fileName)
            val aiAnalysis = AiImportColumnProcessor.analyzeAndRefine(best, baseMapping, detectedApp)
            val base = ImportInspectionResult(
                fileName = fileName,
                format = ImportFileFormat.JSON,
                detectedSourceApp = detectedApp,
                tables = tables,
                selectedTableName = best.name,
                mapping = aiAnalysis.refinedMapping,
                previewRows = emptyList(),
                warnings = aiAnalysis.anomalyWarnings,
                aiAnalysis = aiAnalysis,
            )
            Result.success(rebuildPreview(base, best.name, aiAnalysis.refinedMapping))
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            ErrorLogger.logImportException(context, "ImportJSON", "JSON parsing error for $fileName", t)
            Result.failure(IllegalStateException("ملف JSON غير صالح أو تالف: ${t.message ?: "خطأ في البنية"}"))
        }
    }

    private fun jsonArrayToTable(name: String, arr: JSONArray): DiscoveredTable {
        val colSet = linkedSetOf<String>()
        val rows = mutableListOf<Map<String, String>>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val map = linkedMapOf<String, String>()
            val k = obj.keys()
            while (k.hasNext()) {
                val key = k.next()
                colSet.add(key)
                val v = obj.opt(key)
                map[key] = if (v == null || v == JSONObject.NULL) "" else v.toString()
            }
            rows.add(map)
        }
        return DiscoveredTable(name, colSet.toList(), rows)
    }

    private fun detectFormat(fileName: String, bytes: ByteArray): ImportFileFormat {
        val headerStr = bytes.take(16).toByteArray().toString(Charsets.US_ASCII)
        if (headerStr.startsWith("SQLite format 3")) return ImportFileFormat.SQLITE_DB
        if (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) return ImportFileFormat.XLSX
        if (bytes.size >= 8 && bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() && bytes[2] == 0x11.toByte() && bytes[3] == 0xE0.toByte()) {
            return ImportFileFormat.XLS
        }
        return when {
            fileName.endsWith(".db", true) || fileName.endsWith(".sqlite", true) || fileName.endsWith(".sqlite3", true) -> ImportFileFormat.SQLITE_DB
            fileName.endsWith(".xlsx", true) -> ImportFileFormat.XLSX
            fileName.endsWith(".xls", true) -> ImportFileFormat.XLS
            fileName.endsWith(".json", true) -> ImportFileFormat.JSON
            else -> ImportFileFormat.CSV
        }
    }

    private fun detectAccountingAppFromTables(tableNames: List<String>, fileName: String): String {
        val joined = (tableNames + fileName).joinToString(" ").lowercase(Locale.ROOT)
        return when {
            joined.contains("daftr") || joined.contains("daftar") || joined.contains("دفتر") ||
                (tableNames.any { it.equals("accounts", true) } && tableNames.any { it.equals("operations", true) || it.equals("debts", true) }) ->
                "دفتر الحسابات"
            joined.contains("muhasib") || joined.contains("smart") || joined.contains("المحاسب") ||
                tableNames.any { it.contains("acc_", true) || it.equals("vouchers", true) || it.equals("journal", true) } ->
                "المحاسب الذكي برو"
            else -> "ملف خارجي ($fileName)"
        }
    }

    private fun pickBestCustomerTable(tables: List<DiscoveredTable>): DiscoveredTable {
        return tables.maxByOrNull { tbl ->
            var score = 0
            val n = tbl.name.lowercase(Locale.ROOT)
            if (n.contains("customer") || n.contains("client") || n.contains("account") || n.contains("عملاء") || n.contains("حساب")) score += 30
            if (tbl.rows.isNotEmpty()) score += 10
            val mapping = suggestColumnMapping(tbl.columns)
            if (mapping.nameCol != null) score += 25
            if (mapping.phoneCol != null) score += 10
            if (mapping.balanceCol != null || mapping.debtCol != null) score += 15
            score
        } ?: tables.first()
    }

    fun suggestColumnMapping(columns: List<String>): ColumnMapping {
        fun match(excluded: Set<String> = emptySet(), vararg keywords: String): String? {
            return columns.firstOrNull { col ->
                if (col in excluded) return@firstOrNull false
                val norm = FormatUtils.normalizeArabicText(col)
                val tokens = norm.split(' ', '_', '-', '/').filter { it.isNotBlank() }
                keywords.any { rawKw ->
                    val kw = FormatUtils.normalizeArabicText(rawKw)
                    if (kw.length <= 3) {
                        norm == kw || tokens.any { it == kw }
                    } else {
                        norm.contains(kw)
                    }
                }
            }
        }

        val nameCol = match(
            emptySet(),
            "اسم العميل", "العميل", "الاسم", "اسم الحساب", "customer_name", "client_name", "acc_name", "name", "title"
        ) ?: columns.firstOrNull()
        val phoneCol = match(
            setOfNotNull(nameCol),
            "الهاتف", "الجوال", "الموبايل", "تلفون", "رقم التواصل", "phone", "mobile", "tel"
        )
        val accountCol = match(
            setOfNotNull(nameCol, phoneCol),
            "رقم الحساب", "الكود", "account_number", "acc_no", "code"
        )
        val dateCol = match(
            setOfNotNull(nameCol, phoneCol, accountCol),
            "التاريخ", "تاريخ", "date", "created_at", "trans_date"
        )
        val notesCol = match(
            setOfNotNull(nameCol, phoneCol, accountCol, dateCol),
            "ملاحظات", "البيان", "الوصف", "تفاصيل", "notes", "description", "details", "remark"
        )
        val nonMonetary = setOfNotNull(nameCol, phoneCol, accountCol, dateCol, notesCol)
        val balanceCol = match(
            nonMonetary,
            "الرصيد", "المتبقي", "الصافي", "balance", "net", "remaining", "due"
        )
        val debtCol = match(
            nonMonetary + setOfNotNull(balanceCol),
            "اجمالي الديون", "الدين", "الديون", "عليه", "مدين", "debit", "debt", "total_debt"
        )
        val paymentCol = match(
            nonMonetary + setOfNotNull(balanceCol, debtCol),
            "اجمالي المدفوعات", "المدفوع", "المدفوعات", "الدفعة", "له", "دائن", "سداد", "credit", "paid", "payment"
        )
        return ColumnMapping(
            nameCol = nameCol,
            phoneCol = phoneCol,
            accountCol = accountCol,
            balanceCol = balanceCol,
            debtCol = debtCol,
            paymentCol = paymentCol,
            dateCol = dateCol,
            notesCol = notesCol,
        )
    }

    private fun resolveFileName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                return cursor.getString(idx) ?: "import_file"
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "import_file"
    }

    private fun decodeBestCharset(bytes: ByteArray): String {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2) {
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
                return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            }
            if (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
                return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            }
        }
        return bytes.toString(Charsets.UTF_8)
    }

    private fun detectCsvDelimiter(headerLine: String): Char {
        val candidates = listOf(',', ';', '\t', '|')
        return candidates.maxByOrNull { ch -> headerLine.count { it == ch } } ?: ','
    }

    private fun parseCsvLines(text: String, delimiter: Char): List<List<String>> {
        val result = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < text.length && text[i + 1] == '"') {
                        cell.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == delimiter && !inQuotes -> {
                    currentRow.add(cell.toString())
                    cell.setLength(0)
                }
                (ch == '\n' || ch == '\r') && !inQuotes -> {
                    if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    currentRow.add(cell.toString())
                    cell.setLength(0)
                    if (currentRow.any { it.isNotBlank() }) {
                        result.add(currentRow.toList())
                    }
                    currentRow.clear()
                }
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(cell.toString())
            if (currentRow.any { it.isNotBlank() }) result.add(currentRow.toList())
        }
        return result
    }

    private fun parseSharedStringsXml(bytes: ByteArray): List<String> {
        val list = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
        var inSi = false
        var inT = false
        val current = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "si") {
                        inSi = true
                        current.setLength(0)
                    } else if (inSi && parser.name == "t") {
                        inT = true
                    }
                }
                XmlPullParser.TEXT -> if (inSi && inT) current.append(parser.text)
                XmlPullParser.END_TAG -> {
                    if (parser.name == "t") inT = false
                    else if (parser.name == "si") {
                        list.add(current.toString())
                        inSi = false
                    }
                }
            }
            event = parser.next()
        }
        return list
    }

    private fun parseSheetXml(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
        var currentRow = mutableListOf<String>()
        var cellType = ""
        var cellColIndex = -1
        var inValue = false
        var inInlineT = false
        val textBuf = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "row" -> currentRow = mutableListOf()
                        "c" -> {
                            cellType = parser.getAttributeValue(null, "t") ?: ""
                            val ref = parser.getAttributeValue(null, "r") ?: ""
                            cellColIndex = cellRefToColIndex(ref)
                            textBuf.setLength(0)
                        }
                        "v" -> {
                            inValue = true
                            textBuf.setLength(0)
                        }
                        "t" -> {
                            inInlineT = true
                            textBuf.setLength(0)
                        }
                    }
                }
                XmlPullParser.TEXT -> if (inValue || inInlineT) textBuf.append(parser.text)
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "v" -> inValue = false
                        "t" -> inInlineT = false
                        "c" -> {
                            val raw = textBuf.toString()
                            val valStr = if (cellType == "s") {
                                raw.toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: ""
                            } else {
                                raw
                            }
                            if (cellColIndex in 0..512) {
                                while (currentRow.size <= cellColIndex) currentRow.add("")
                                currentRow[cellColIndex] = valStr
                            } else if (currentRow.size <= 512) {
                                currentRow.add(valStr)
                            }
                        }
                        "row" -> if (currentRow.any { it.isNotBlank() }) rows.add(currentRow.toList())
                    }
                }
            }
            event = parser.next()
        }
        return rows
    }

    private fun cellRefToColIndex(ref: String): Int {
        val letters = ref.takeWhile { it.isLetter() }.uppercase(Locale.US)
        if (letters.isEmpty() || letters.length > 3) return -1
        var idx = 0
        for (ch in letters) {
            if (ch !in 'A'..'Z') return -1
            idx = idx * 26 + (ch.code - 'A'.code + 1)
        }
        val colIdx = idx - 1
        return if (colIdx in 0..512) colIdx else -1
    }

    private fun parseHtmlOrSpreadsheetMlXls(content: String): List<DiscoveredTable> {
        val rows = mutableListOf<List<String>>()
        val rowRegex = Regex("<tr[^>]*>(.*?)</tr>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val cellRegex = Regex("<t[dh][^>]*>(.*?)</t[dh]>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val stripTags = Regex("<[^>]+>")
        for (rm in rowRegex.findAll(content)) {
            val cells = cellRegex.findAll(rm.groupValues[1]).map { cm ->
                stripTags.replace(cm.groupValues[1], "").trim()
            }.toList()
            if (cells.any { it.isNotBlank() }) rows.add(cells)
        }
        if (rows.size >= 2) {
            val headers = rows.first().mapIndexed { i, h -> h.ifBlank { "عمود_${i + 1}" } }
            val mapped = rows.drop(1).map { r ->
                val map = linkedMapOf<String, String>()
                headers.forEachIndexed { idx, h -> map[h] = r.getOrElse(idx) { "" } }
                map
            }
            return listOf(DiscoveredTable("جدول XLS", headers, mapped))
        }
        return emptyList()
    }

    private fun parseBiff8Xls(bytes: ByteArray): List<DiscoveredTable> {
        val workbookStream = extractOle2WorkbookStream(bytes) ?: return emptyList()
        if (workbookStream.size < 4) return emptyList()
        val buf = ByteBuffer.wrap(workbookStream).order(ByteOrder.LITTLE_ENDIAN)
        val sheetNames = mutableListOf<String>()
        val sheetOffsets = mutableListOf<Int>()
        val sst = mutableListOf<String>()
        val sheetCells = mutableMapOf<Int, MutableMap<Int, MutableMap<Int, String>>>()
        var currentSheetIdx = -1

        var pos = 0
        while (pos + 4 <= buf.capacity()) {
            val recType = buf.getShort(pos).toInt() and 0xFFFF
            val recLen = buf.getShort(pos + 2).toInt() and 0xFFFF
            pos += 4
            if (pos + recLen > buf.capacity()) break

            when (recType) {
                0x0085 -> { // BOUNDSHEET
                    if (recLen >= 8) {
                        val bofOffset = buf.getInt(pos)
                        val nameLen = buf.get(pos + 6).toInt() and 0xFF
                        val flags = buf.get(pos + 7).toInt() and 0xFF
                        val isUtf16 = (flags and 0x01) != 0
                        val name = readBiffStringData(workbookStream, pos + 8, nameLen, isUtf16)
                        sheetNames.add(name.ifBlank { "ورقة_${sheetNames.size + 1}" })
                        sheetOffsets.add(bofOffset)
                    }
                }
                0x00FC -> { // SST
                    if (recLen >= 8) {
                        val totalUnique = buf.getInt(pos + 4).coerceIn(0, 200_000)
                        parseSstRecord(workbookStream, pos + 8, pos + recLen, totalUnique, sst)
                    }
                }
                0x0809 -> { // BOF
                    val bofPos = pos - 4
                    val idx = sheetOffsets.indexOf(bofPos)
                    if (idx >= 0) {
                        currentSheetIdx = idx
                        sheetCells.getOrPut(currentSheetIdx) { sortedMapOf() }
                    }
                }
                0x00FD -> { // LABELSST
                    if (currentSheetIdx >= 0 && recLen >= 10) {
                        val r = buf.getShort(pos).toInt() and 0xFFFF
                        val c = buf.getShort(pos + 2).toInt() and 0xFFFF
                        val sstIdx = buf.getInt(pos + 6)
                        if (r <= 100_000 && c <= 512) {
                            val str = sst.getOrElse(sstIdx) { "" }
                            sheetCells.getOrPut(currentSheetIdx) { sortedMapOf() }
                                .getOrPut(r) { sortedMapOf() }[c] = str
                        }
                    }
                }
                0x0204 -> { // LABEL
                    if (currentSheetIdx >= 0 && recLen >= 8) {
                        val r = buf.getShort(pos).toInt() and 0xFFFF
                        val c = buf.getShort(pos + 2).toInt() and 0xFFFF
                        val len = buf.getShort(pos + 6).toInt() and 0xFFFF
                        if (r <= 100_000 && c <= 512) {
                            val str = readBiffStringData(workbookStream, pos + 8, len.coerceAtMost(recLen - 8), false)
                            sheetCells.getOrPut(currentSheetIdx) { sortedMapOf() }
                                .getOrPut(r) { sortedMapOf() }[c] = str
                        }
                    }
                }
                0x0203 -> { // NUMBER
                    if (currentSheetIdx >= 0 && recLen >= 14) {
                        val r = buf.getShort(pos).toInt() and 0xFFFF
                        val c = buf.getShort(pos + 2).toInt() and 0xFFFF
                        val d = buf.getDouble(pos + 6)
                        if (r <= 100_000 && c <= 512 && d.isFinite()) {
                            val str = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
                            sheetCells.getOrPut(currentSheetIdx) { sortedMapOf() }
                                .getOrPut(r) { sortedMapOf() }[c] = str
                        }
                    }
                }
                0x027E -> { // RK
                    if (currentSheetIdx >= 0 && recLen >= 10) {
                        val r = buf.getShort(pos).toInt() and 0xFFFF
                        val c = buf.getShort(pos + 2).toInt() and 0xFFFF
                        val rk = buf.getInt(pos + 6)
                        val d = decodeRk(rk)
                        if (r <= 100_000 && c <= 512 && d.isFinite()) {
                            val str = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
                            sheetCells.getOrPut(currentSheetIdx) { sortedMapOf() }
                                .getOrPut(r) { sortedMapOf() }[c] = str
                        }
                    }
                }
                0x00BD -> { // MULRK
                    if (currentSheetIdx >= 0 && recLen >= 6) {
                        val r = buf.getShort(pos).toInt() and 0xFFFF
                        val firstCol = buf.getShort(pos + 2).toInt() and 0xFFFF
                        val count = ((recLen - 6) / 6).coerceAtMost(512)
                        if (r <= 100_000) {
                            for (k in 0 until count) {
                                val targetCol = firstCol + k
                                if (targetCol > 512 || pos + 4 + k * 6 + 6 > pos + recLen) break
                                val rk = buf.getInt(pos + 4 + k * 6 + 2)
                                val d = decodeRk(rk)
                                if (d.isFinite()) {
                                    val str = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
                                    sheetCells.getOrPut(currentSheetIdx) { sortedMapOf() }
                                        .getOrPut(r) { sortedMapOf() }[targetCol] = str
                                }
                            }
                        }
                    }
                }
            }
            pos += recLen
        }

        val tables = mutableListOf<DiscoveredTable>()
        sheetCells.forEach { (idx, rowsMap) ->
            if (rowsMap.size >= 2) {
                val maxCol = (rowsMap.values.maxOfOrNull { it.keys.maxOrNull() ?: 0 } ?: 0).coerceIn(0, 512)
                val matrix = rowsMap.entries.sortedBy { it.key }.map { (_, colsMap) ->
                    (0..maxCol).map { c -> colsMap[c].orEmpty() }
                }
                val headers = matrix.first().mapIndexed { i, h -> h.trim().ifBlank { "عمود_${i + 1}" } }
                val rowMaps = matrix.drop(1).filter { r -> r.any { it.isNotBlank() } }.map { r ->
                    val map = linkedMapOf<String, String>()
                    headers.forEachIndexed { cIdx, h -> map[h] = r.getOrElse(cIdx) { "" }.trim() }
                    map
                }
                val title = sheetNames.getOrElse(idx) { "ورقة_${idx + 1}" }
                tables.add(DiscoveredTable(title, headers, rowMaps))
            }
        }
        return tables
    }

    private fun extractOle2WorkbookStream(bytes: ByteArray): ByteArray? {
        if (bytes.size < 512) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val sectorShift = buf.getShort(30).toInt() and 0xFFFF
        if (sectorShift !in 6..16) return null
        val sectorSize = 1 shl sectorShift
        val firstDirSector = buf.getInt(48)
        if (firstDirSector < 0) return null
        val fatSectors = mutableListOf<Int>()
        for (i in 0 until 109) {
            val sec = buf.getInt(76 + i * 4)
            if (sec >= 0) fatSectors.add(sec)
        }
        val fat = mutableListOf<Int>()
        for (sec in fatSectors) {
            val offLong = (sec.toLong() + 1L) * sectorSize.toLong()
            if (offLong >= 0L && offLong + sectorSize <= bytes.size) {
                val off = offLong.toInt()
                for (j in 0 until sectorSize / 4) {
                    fat.add(buf.getInt(off + j * 4))
                }
            }
        }
        fun readChain(startSector: Int): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            var cur = startSector
            var guard = 0
            val maxStreamBytes = 16 * 1024 * 1024
            while (cur >= 0 && cur < fat.size && guard++ < 50_000 && out.size() < maxStreamBytes) {
                val offLong = (cur.toLong() + 1L) * sectorSize.toLong()
                if (offLong >= 0L && offLong + sectorSize <= bytes.size) {
                    out.write(bytes, offLong.toInt(), sectorSize)
                }
                val next = fat[cur]
                if (next == cur) break
                cur = next
            }
            return out.toByteArray()
        }

        val dirBytes = readChain(firstDirSector)
        val dirBuf = ByteBuffer.wrap(dirBytes).order(ByteOrder.LITTLE_ENDIAN)
        var entryOff = 0
        while (entryOff + 128 <= dirBytes.size) {
            val nameLen = (dirBuf.getShort(entryOff + 64).toInt() and 0xFFFF).coerceAtMost(64)
            val sb = StringBuilder()
            for (k in 0 until (nameLen - 2).coerceAtLeast(0) step 2) {
                sb.append(dirBuf.getShort(entryOff + k).toInt().toChar())
            }
            val name = sb.toString()
            if (name.equals("Workbook", true) || name.equals("Book", true)) {
                val startSec = dirBuf.getInt(entryOff + 116)
                val size = dirBuf.getInt(entryOff + 120)
                if (startSec < 0 || size <= 0) return null
                val full = readChain(startSec)
                return full.copyOf(size.coerceIn(0, full.size))
            }
            entryOff += 128
        }
        return null
    }

    private fun parseSstRecord(data: ByteArray, start: Int, end: Int, totalUnique: Int, out: MutableList<String>) {
        var pos = start
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        while (out.size < totalUnique && pos + 3 <= end) {
            val charCount = buf.getShort(pos).toInt() and 0xFFFF
            val flags = data[pos + 2].toInt() and 0xFF
            pos += 3
            val isUtf16 = (flags and 0x01) != 0
            val hasExt = (flags and 0x04) != 0
            val hasRich = (flags and 0x08) != 0
            var richRuns = 0
            var extSize = 0
            if (hasRich && pos + 2 <= end) {
                richRuns = buf.getShort(pos).toInt() and 0xFFFF
                pos += 2
            }
            if (hasExt && pos + 4 <= end) {
                extSize = buf.getInt(pos)
                if (extSize < 0) break
                pos += 4
            }
            val byteLen = if (isUtf16) charCount * 2 else charCount
            if (byteLen < 0 || pos + byteLen > end) break
            out.add(readBiffStringData(data, pos, charCount, isUtf16))
            val advance = byteLen.toLong() + richRuns.toLong() * 4L + extSize.toLong()
            if (advance <= 0L && byteLen == 0) continue
            if (pos + advance > Int.MAX_VALUE) break
            pos += advance.toInt()
        }
    }

    private fun readBiffStringData(data: ByteArray, offset: Int, charCount: Int, isUtf16: Boolean): String {
        if (charCount <= 0 || offset < 0 || offset >= data.size) return ""
        return if (isUtf16) {
            val end = (offset + charCount * 2).coerceAtMost(data.size)
            String(data, offset, end - offset, Charsets.UTF_16LE)
        } else {
            val end = (offset + charCount).coerceAtMost(data.size)
            String(data, offset, end - offset, Charsets.ISO_8859_1)
        }
    }

    private fun decodeRk(rk: Int): Double {
        val div100 = (rk and 0x01) != 0
        val isInt = (rk and 0x02) != 0
        var value = if (isInt) {
            (rk shr 2).toDouble()
        } else {
            val bits = (rk and 0xFFFFFFFC.toInt()).toLong() shl 32
            java.lang.Double.longBitsToDouble(bits)
        }
        if (div100) value /= 100.0
        return value
    }

    private fun parseFlexibleDate(raw: String): Long? {
        val clean = raw.trim()
        if (clean.isBlank()) return null
        clean.toLongOrNull()?.let { num ->
            if (num > 1_000_000_000_000L) return num
            if (num in 1_000_000_000L..9_999_999_999L) return num * 1000L
        }
        val patterns = listOf("yyyy-MM-dd", "yyyy/MM/dd", "dd/MM/yyyy", "dd-MM-yyyy", "yyyy-MM-dd HH:mm:ss")
        for (p in patterns) {
            runCatching {
                SimpleDateFormat(p, Locale.US).parse(clean)?.time
            }.getOrNull()?.let { return it }
        }
        return null
    }
}
