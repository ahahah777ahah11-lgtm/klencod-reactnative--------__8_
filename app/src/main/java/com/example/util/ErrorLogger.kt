package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dedicated error & diagnostics logger for data import, database integrity,
 * and backup/restore exceptions. Outputs to Android Logcat and persists entries
 * into a local text file (`smartdebt_error_log.txt`) so the user can export or share
 * diagnostic reports whenever an import error happens.
 */
object ErrorLogger {
    private const val TAG = "SmartDebtImportError"
    private const val LOG_DIR = "logs"
    const val ERROR_LOG_FILENAME = "smartdebt_error_log.txt"
    private const val MAX_LOG_BYTES = 512L * 1024L

    private val lock = Any()

    fun logImportException(
        context: Context?,
        operation: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        runCatching { Log.e(TAG, "[$operation] $message", throwable) }
        appendToLocalFile(context, "ERROR", operation, message, throwable)
    }

    fun logImportWarning(
        context: Context?,
        operation: String,
        message: String,
    ) {
        runCatching { Log.w(TAG, "[$operation] $message") }
        appendToLocalFile(context, "WARN", operation, message, null)
    }

    fun logImportInfo(
        context: Context?,
        operation: String,
        message: String,
    ) {
        runCatching { Log.i(TAG, "[$operation] $message") }
        appendToLocalFile(context, "INFO", operation, message, null)
    }

    fun getLogFile(context: Context): File {
        val dir = File(context.filesDir, LOG_DIR).apply { mkdirs() }
        return File(dir, ERROR_LOG_FILENAME)
    }

    fun readRecentEntries(context: Context, maxLines: Int = 150): List<String> {
        return runCatching {
            val file = getLogFile(context)
            if (!file.exists()) return emptyList()
            synchronized(lock) {
                file.readLines(Charsets.UTF_8).takeLast(maxLines)
            }
        }.getOrDefault(emptyList())
    }

    fun readLogText(context: Context, maxLines: Int = 300): String {
        val lines = readRecentEntries(context, maxLines)
        return if (lines.isEmpty()) {
            "لا توجد أخطاء مسجلة في سجل استيراد البيانات حتى الآن."
        } else {
            lines.joinToString("\n")
        }
    }

    fun exportErrorLogToUri(context: Context, uri: Uri): Result<Int> {
        return runCatching {
            val content = buildExportReportText(context)
            val lineCount = content.lines().size
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
            } ?: error("تعذر فتح مسار حفظ ملف سجل الأخطاء")
            lineCount
        }.onFailure { err ->
            runCatching { Log.e(TAG, "[ExportLog] Failed to export error log", err) }
        }
    }

    fun createExportableTxtFile(context: Context): Result<File> {
        return runCatching {
            val exportDir = File(context.cacheDir, "error_reports").apply { mkdirs() }
            val outFile = File(exportDir, "SmartDebt-ErrorLog-${System.currentTimeMillis()}.txt")
            outFile.writeText(buildExportReportText(context), Charsets.UTF_8)
            outFile
        }
    }

    fun shareErrorLog(context: Context): Boolean {
        return runCatching {
            val reportFile = createExportableTxtFile(context).getOrThrow()
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                reportFile,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "سجل أخطاء الاستيراد - مدير الديون الذكي برو SMS")
                putExtra(Intent.EXTRA_TEXT, "مرفق ملف سجل الأخطاء التشخيصي لتتبع استثناءات الاستيراد وقاعدة البيانات.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "تصدير / مشاركة سجل الأخطاء").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        }.getOrElse { false }
    }

    fun clearErrorLogs(context: Context): Boolean {
        return runCatching {
            synchronized(lock) {
                val file = getLogFile(context)
                val prev = File(file.parentFile, "$ERROR_LOG_FILENAME.prev")
                prev.delete()
                if (file.exists()) file.delete() else true
            }
        }.getOrDefault(false)
    }

    private fun buildExportReportText(context: Context): String {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val entries = readRecentEntries(context, 400)
        return buildString {
            appendLine("=== Smart Debt Manager Pro SMS - Import & Database Error Log ===")
            appendLine("Generated At: $ts")
            appendLine("Entries Count: ${entries.size}")
            appendLine("===============================================================")
            if (entries.isEmpty()) {
                appendLine("(No recorded import or database exceptions)")
            } else {
                entries.forEach { appendLine(it) }
            }
        }
    }

    private fun appendToLocalFile(
        context: Context?,
        level: String,
        operation: String,
        message: String,
        throwable: Throwable?,
    ) {
        val ctx = context ?: return
        runCatching {
            val file = getLogFile(ctx)
            synchronized(lock) {
                if (file.exists() && file.length() > MAX_LOG_BYTES) {
                    val rotated = File(file.parentFile, "$ERROR_LOG_FILENAME.prev")
                    runCatching { rotated.delete() }
                    runCatching { file.renameTo(rotated) }
                }
                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
                val stackSummary = throwable?.let { t ->
                    val cause = t.cause?.let { " -> Caused by ${it::class.java.simpleName}: ${it.message}" }.orEmpty()
                    " | ${t::class.java.simpleName}: ${t.message}$cause"
                }.orEmpty()
                file.appendText("$ts [$level][$operation] $message$stackSummary\n", Charsets.UTF_8)
            }
        }
    }
}
