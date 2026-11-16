package com.example.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object FormatUtils {
    private val enSymbols = DecimalFormatSymbols(Locale.US).apply {
        groupingSeparator = ','
        decimalSeparator = '.'
    }

    fun formatAmount(amount: Double, currencySymbol: String = ""): String {
        val df = DecimalFormat("#,##0.##", enSymbols)
        val formatted = df.format(amount)
        return if (currencySymbol.isBlank()) formatted else "$formatted $currencySymbol"
    }

    fun formatAbsAmount(amount: Double, currencySymbol: String = ""): String {
        return formatAmount(abs(amount), currencySymbol)
    }

    fun formatDate(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy/MM/dd", Locale.US)
        return sdf.format(Date(millis))
    }

    fun formatDateTime(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US)
        return sdf.format(Date(millis))
    }

    fun normalizePhone(raw: String): String {
        val trimmed = raw.trim()
        val builder = StringBuilder()
        for (ch in trimmed) {
            when (ch) {
                in '0'..'9', '+' -> builder.append(ch)
                in '٠'..'٩' -> builder.append(('0'.code + (ch.code - '٠'.code)).toChar())
                in '۰'..'۹' -> builder.append(('0'.code + (ch.code - '۰'.code)).toChar())
            }
        }
        return builder.toString()
    }

    fun parsePositiveAmount(raw: String): Double? {
        val normalized = raw.trim()
            .replace(",", "")
            .replace("،", "")
            .map { ch ->
                when (ch) {
                    in '٠'..'٩' -> ('0'.code + (ch.code - '٠'.code)).toChar()
                    in '۰'..'۹' -> ('0'.code + (ch.code - '۰'.code)).toChar()
                    '٫' -> '.'
                    else -> ch
                }
            }
            .joinToString("")
        val parsed = normalized.toDoubleOrNull() ?: return null
        return if (parsed.isNaN() || parsed.isInfinite()) null else parsed
    }

    fun normalizeArabicText(text: String): String {
        return text.trim()
            .replace("أ", "ا")
            .replace("إ", "ا")
            .replace("آ", "ا")
            .replace("ة", "ه")
            .replace("ى", "ي")
            .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
            .replace(Regex("\\s+"), " ")
            .lowercase(Locale.ROOT)
    }
}
