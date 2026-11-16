package com.example.data.ai

import com.example.data.importexport.ColumnMapping
import com.example.data.importexport.DiscoveredTable
import com.example.util.FormatUtils

data class AiColumnSuggestion(
    val fieldLabelAr: String,
    val matchedColumn: String?,
    val confidencePercent: Int,
    val reasonAr: String,
)

data class AiImportAnalysis(
    val detectedPatternAr: String,
    val confidenceScore: Int,
    val refinedMapping: ColumnMapping,
    val columnSuggestions: List<AiColumnSuggestion>,
    val anomalyWarnings: List<String>,
    val recommendedSummaryAr: String,
)

/**
 * Intelligent offline column-and-content analyzer for external accounting imports.
 * Combines semantic Arabic/English header scoring with sample cell pattern inspection
 * (phones, currency numbers, dates, and transfer reference strings like ت/8000011).
 */
object AiImportColumnProcessor {

    private val TRANSFER_REF_REGEX = Regex(
        pattern = "(?:^|\\s)(?:ت|ح|حوالة|مرجع|سند|ref|tr|tx)[\\s/:\\-]*\\d+",
        option = RegexOption.IGNORE_CASE,
    )
    private val PHONE_VALUE_REGEX = Regex("^(?:\\+967|00967|967|0)?(?:7[01378]\\d{7}|\\d{7,12})$")

    fun analyzeAndRefine(
        table: DiscoveredTable,
        baseMapping: ColumnMapping,
        detectedSourceApp: String,
    ): AiImportAnalysis {
        val sampleRows = table.rows.take(40)
        val warnings = mutableListOf<String>()

        fun columnValues(col: String): List<String> =
            sampleRows.mapNotNull { r -> r[col]?.trim()?.takeIf { it.isNotBlank() } }

        fun looksLikeTransferRefColumn(col: String): Boolean {
            val colNorm = FormatUtils.normalizeArabicText(col)
            if (colNorm.contains("مرجع") || colNorm.contains("حوالة") || colNorm.contains("reference") || colNorm.contains("ref")) {
                return true
            }
            val vals = columnValues(col)
            if (vals.isEmpty()) return false
            val refHits = vals.count { TRANSFER_REF_REGEX.containsMatchIn(it) }
            return refHits > 0 && refHits * 2 >= vals.size
        }

        fun phoneScore(col: String): Int {
            val colNorm = FormatUtils.normalizeArabicText(col)
            var score = 0
            if (listOf("هاتف", "جوال", "موبايل", "تلفون", "تواصل", "phone", "mobile", "tel").any { colNorm.contains(it) }) {
                score += 60
            }
            val vals = columnValues(col)
            if (vals.isNotEmpty()) {
                val hits = vals.count { v ->
                    val cleaned = FormatUtils.normalizePhone(v).replace("+", "")
                    cleaned.length in 7..13 && PHONE_VALUE_REGEX.matches(v.replace(" ", "").replace("-", ""))
                }
                score += (hits * 40) / vals.size.coerceAtLeast(1)
            }
            return score
        }

        fun moneyScore(col: String): Int {
            if (looksLikeTransferRefColumn(col)) return -100
            val vals = columnValues(col)
            if (vals.isEmpty()) return 0
            val numericHits = vals.count { FormatUtils.parsePositiveAmount(it) != null }
            return (numericHits * 50) / vals.size.coerceAtLeast(1)
        }

        fun textNameScore(col: String): Int {
            val colNorm = FormatUtils.normalizeArabicText(col)
            var score = 0
            if (listOf("اسم العميل", "العميل", "الاسم", "الحساب", "customer", "client", "name").any { colNorm.contains(it) }) {
                score += 65
            }
            val vals = columnValues(col)
            if (vals.isNotEmpty()) {
                val textHits = vals.count { v -> v.any { ch -> ch.isLetter() } && v.length in 2..60 }
                score += (textHits * 35) / vals.size.coerceAtLeast(1)
            }
            return score
        }

        var refined = baseMapping

        // Guard: ensure transfer reference columns are never treated as balance/debt/payment
        if (refined.balanceCol != null && looksLikeTransferRefColumn(refined.balanceCol)) {
            warnings.add("رصد الذكاء الاصطناعي أن العمود (${refined.balanceCol}) يحتوي أرقام مراجع حوالات وتم فصله عن الرصيد لحماية الأرقام المالية.")
            refined = refined.copy(balanceCol = null, notesCol = refined.notesCol ?: refined.balanceCol)
        }

        val usedCols = mutableSetOf<String>()
        val bestName = refined.nameCol ?: table.columns.maxByOrNull { textNameScore(it) }?.takeIf { textNameScore(it) >= 30 }
        bestName?.let { usedCols.add(it) }
        refined = refined.copy(nameCol = bestName)

        val bestPhone = refined.phoneCol ?: table.columns
            .filter { it !in usedCols }
            .maxByOrNull { phoneScore(it) }
            ?.takeIf { phoneScore(it) >= 35 }
        bestPhone?.let { usedCols.add(it) }
        refined = refined.copy(phoneCol = bestPhone)

        refined.accountCol?.let { usedCols.add(it) }
        refined.dateCol?.let { usedCols.add(it) }
        refined.notesCol?.let { usedCols.add(it) }

        val bestBalance = refined.balanceCol ?: table.columns
            .filter { it !in usedCols }
            .firstOrNull { col ->
                val norm = FormatUtils.normalizeArabicText(col)
                (norm.contains("رصيد") || norm.contains("bal") || norm.contains("مبلغ") || norm.contains("amount")) &&
                    moneyScore(col) >= 25
            }
        bestBalance?.let { usedCols.add(it) }
        refined = refined.copy(balanceCol = bestBalance)

        // Inspect transfer-reference values embedded inside notes/description/unmapped columns
        val possibleRefCols = table.columns.filter { looksLikeTransferRefColumn(it) }
        if (possibleRefCols.isNotEmpty()) {
            warnings.add("تم رصد حقول مراجع حوالات (${possibleRefCols.joinToString(", ")})؛ لن تُحتسب كأرصدة مالية تلقائياً.")
        }

        // Check invalid or unusual phones
        refined.phoneCol?.let { pCol ->
            val badPhones = columnValues(pCol).count { v ->
                FormatUtils.normalizePhone(v).count { it.isDigit() } < 5
            }
            if (badPhones > 0) {
                warnings.add("تنبيه ذكي: يوجد $badPhones صف بأرقام هواتف غير مكتملة في العمود ($pCol)؛ سيتم تنظيفها بأمان.")
            }
        }

        val suggestions = buildList {
            add(
                AiColumnSuggestion(
                    fieldLabelAr = "اسم العميل",
                    matchedColumn = refined.nameCol,
                    confidencePercent = if (refined.nameCol != null) 95 else 20,
                    reasonAr = if (refined.nameCol != null) "مطابق دلالياً لأسماء العملاء" else "لم يُعثر على عمود اسم واضح",
                )
            )
            add(
                AiColumnSuggestion(
                    fieldLabelAr = "رقم الهاتف",
                    matchedColumn = refined.phoneCol,
                    confidencePercent = if (refined.phoneCol != null) phoneScore(refined.phoneCol!!).coerceIn(55, 98) else 0,
                    reasonAr = if (refined.phoneCol != null) "تطابق نمط الأرقام المحلية" else "اختياري — لم يظهر عمود أرقام هواتف",
                )
            )
            add(
                AiColumnSuggestion(
                    fieldLabelAr = "الرصيد / الديون",
                    matchedColumn = refined.balanceCol ?: refined.debtCol,
                    confidencePercent = if (refined.balanceCol != null || refined.debtCol != null) 90 else 30,
                    reasonAr = when {
                        refined.debtCol != null && refined.paymentCol != null -> "تفصيل تلقائي بين الديون والمدفوعات"
                        refined.balanceCol != null -> "سيُسجل كرصيد افتتاحي موثق"
                        else -> "اختر عمود الرصيد أو الديون يدوياً إذا لزم"
                    },
                )
            )
            add(
                AiColumnSuggestion(
                    fieldLabelAr = "المدفوعات",
                    matchedColumn = refined.paymentCol,
                    confidencePercent = if (refined.paymentCol != null) 88 else 0,
                    reasonAr = if (refined.paymentCol != null) "تمييز عمود المدفوعات/السداد" else "غير منفصل (سيُعتمد الرصيد الصافي)",
                )
            )
        }

        val confidence = listOfNotNull(
            if (refined.nameCol != null) 95 else 30,
            if (refined.balanceCol != null || refined.debtCol != null) 90 else 45,
            if (refined.phoneCol != null) 88 else 60,
        ).average().toInt().coerceIn(25, 98)

        val recommendedSummary = buildString {
            append("تم تحليل (${table.name}) عبر المعالج الذكي للملفات (${confidence}% ثقة). ")
            if (refined.debtCol != null && refined.paymentCol != null) {
                append("تم فصل أعمدة الديون والمدفوعات آلياً.")
            } else if (refined.balanceCol != null) {
                append("سيتم اعتماد الرصيد الافتتاحي الموثق دون اختلاق عمليات.")
            } else {
                append("يُرجى مراجعة مطابقة عمود المبالغ قبل الحفظ.")
            }
        }

        return AiImportAnalysis(
            detectedPatternAr = detectedSourceApp,
            confidenceScore = confidence,
            refinedMapping = refined,
            columnSuggestions = suggestions,
            anomalyWarnings = warnings,
            recommendedSummaryAr = recommendedSummary,
        )
    }
}
