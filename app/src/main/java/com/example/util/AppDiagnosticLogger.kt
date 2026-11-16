package com.example.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lightweight, crash-safe local diagnostic logger for database imports,
 * backup/restore operations, and exception tracing (offline-only).
 */
object AppDiagnosticLogger {
    private const val TAG = "SmartDebtDiag"
    private const val MAX_LOG_BYTES = 256L * 1024L
    private const val LOG_DIR = "logs"
    private const val LOG_FILE = "smartdebt_diagnostics.log"

    private val lock = Any()

    fun logInfo(context: Context?, area: String, message: String) {
        writeLine(context, "INFO", area, message, null)
        ErrorLogger.logImportInfo(context, area, message)
    }

    fun logWarn(context: Context?, area: String, message: String) {
        writeLine(context, "WARN", area, message, null)
        ErrorLogger.logImportWarning(context, area, message)
    }

    fun logError(context: Context?, area: String, message: String, throwable: Throwable? = null) {
        writeLine(context, "ERROR", area, message, throwable)
        ErrorLogger.logImportException(context, area, message, throwable)
    }

    fun readRecentLogs(context: Context, maxLines: Int = 120): List<String> {
        return runCatching {
            val file = resolveLogFile(context) ?: return emptyList()
            if (!file.exists()) return emptyList()
            file.readLines(Charsets.UTF_8).takeLast(maxLines)
        }.getOrDefault(emptyList())
    }

    private fun writeLine(
        context: Context?,
        level: String,
        area: String,
        message: String,
        throwable: Throwable?,
    ) {
        runCatching {
            when (level) {
                "ERROR" -> Log.e(TAG, "[$area] $message", throwable)
                "WARN" -> Log.w(TAG, "[$area] $message")
                else -> Log.i(TAG, "[$area] $message")
            }
        }
        val ctx = context ?: return
        runCatching {
            val file = resolveLogFile(ctx) ?: return
            synchronized(lock) {
                if (file.exists() && file.length() > MAX_LOG_BYTES) {
                    val rotated = File(file.parentFile, "$LOG_FILE.prev")
                    runCatching { rotated.delete() }
                    runCatching { file.renameTo(rotated) }
                }
                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
                val trace = throwable?.let { " | ${it::class.java.simpleName}: ${it.message}" }.orEmpty()
                file.appendText("$ts [$level][$area] $message$trace\n", Charsets.UTF_8)
            }
        }
    }

    private fun resolveLogFile(context: Context): File? {
        return runCatching {
            val dir = File(context.filesDir, LOG_DIR).apply { mkdirs() }
            File(dir, LOG_FILE)
        }.getOrNull()
    }
}
