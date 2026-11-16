package com.example.data.importexport

import com.example.data.local.CustomerWithBalance
import com.example.data.local.TransactionWithCustomer
import com.example.data.local.TransferWithCustomer
import com.example.util.FormatUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class ExportDataset(val titleAr: String) {
    CUSTOMERS("قائمة العملاء والأرصدة"),
    TRANSACTIONS("سجل الديون والمدفوعات"),
    TRANSFERS("سجل الحوالات الواردة"),
    FULL_REPORT("التقرير المالي الشامل"),
}

enum class ExportFormat(val titleAr: String, val extension: String, val mimeType: String) {
    CSV("ملف Excel CSV (متوافق مع العربية)", "csv", "text/csv"),
    XLSX("ملف Excel حقيقي (XLSX)", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    JSON("ملف بيانات JSON", "json", "application/json"),
}

class ExportManager {
    fun buildCustomersTable(customers: List<CustomerWithBalance>): Pair<List<String>, List<List<String>>> {
        val headers = listOf("المعرف", "اسم العميل", "رقم الهاتف", "رقم الحساب", "إجمالي الديون", "إجمالي المدفوعات", "الرصيد المستحق", "الحالة", "ملاحظات")
        val rows = customers.map { c ->
            listOf(
                c.id.toString(),
                c.name,
                c.phone,
                c.accountNumber,
                FormatUtils.formatAmount(c.totalDebts),
                FormatUtils.formatAmount(c.totalPayments),
                FormatUtils.formatAmount(c.balance),
                when {
                    c.balance > 0.001 -> "مدين"
                    else -> "مسدد"
                },
                c.notes,
            )
        }
        return headers to rows
    }

    fun exportCustomersCsvBytes(customers: List<CustomerWithBalance>): ByteArray {
        val (headers, rows) = buildCustomersTable(customers)
        val bos = ByteArrayOutputStream()
        writeCsv(headers, rows, bos)
        return bos.toByteArray()
    }

    fun buildTransactionsTable(transactions: List<TransactionWithCustomer>): Pair<List<String>, List<List<String>>> {
        val headers = listOf("رقم العملية", "التاريخ", "اسم العميل", "النوع", "المبلغ", "الوصف", "المرجع", "الحالة")
        val rows = transactions.map { t ->
            listOf(
                t.id.toString(),
                FormatUtils.formatDateTime(t.transactionDate),
                t.customerName,
                t.type.labelAr,
                FormatUtils.formatAmount(t.amount),
                t.description,
                t.referenceNumber ?: "",
                if (t.isReversed) "معكوسة (${t.reversalReason ?: ""})" else "معتمدة",
            )
        }
        return headers to rows
    }

    fun buildTransfersTable(transfers: List<TransferWithCustomer>): Pair<List<String>, List<List<String>>> {
        val headers = listOf("المعرف", "الشركة", "رقم المرجع", "المبلغ", "المرسل", "العميل المرتبط", "التاريخ", "الحالة")
        val rows = transfers.map { tr ->
            listOf(
                tr.id.toString(),
                tr.company.titleAr,
                tr.referenceNumber,
                FormatUtils.formatAmount(tr.amount),
                tr.senderName,
                tr.customerName ?: "غير مرتبط",
                FormatUtils.formatDate(tr.transferDate),
                tr.status.labelAr,
            )
        }
        return headers to rows
    }

    fun buildStatementTable(
        customer: CustomerWithBalance,
        transactions: List<TransactionWithCustomer>,
    ): Pair<List<String>, List<List<String>>> {
        val headers = listOf("التاريخ", "النوع", "البيان", "المرجع", "مدين (عليه)", "دائن (له)", "الرصيد التراكمي", "الحالة")
        var running = 0.0
        val rows = transactions.map { t ->
            val debit = if (!t.isReversed && t.type.isDebit) t.amount else 0.0
            val credit = if (!t.isReversed && !t.type.isDebit) t.amount else 0.0
            running += (debit - credit)
            listOf(
                FormatUtils.formatDateTime(t.transactionDate),
                t.type.labelAr,
                t.description,
                t.referenceNumber ?: "",
                if (debit > 0) FormatUtils.formatAmount(debit) else "0",
                if (credit > 0) FormatUtils.formatAmount(credit) else "0",
                FormatUtils.formatAmount(running),
                if (t.isReversed) "ملغاة/معكوسة" else "معتمدة",
            )
        }
        return headers to rows
    }

    fun writeCsv(headers: List<String>, rows: List<List<String>>, output: OutputStream) {
        val sb = StringBuilder()
        sb.append('\uFEFF') // UTF-8 BOM for Arabic Excel compatibility
        sb.appendLine(headers.joinToString(",") { escapeCsv(it) })
        rows.forEach { row ->
            sb.appendLine(row.joinToString(",") { escapeCsv(it) })
        }
        output.write(sb.toString().toByteArray(Charsets.UTF_8))
    }

    fun writeJson(
        title: String,
        headers: List<String>,
        rows: List<List<String>>,
        output: OutputStream,
    ) {
        val arr = JSONArray()
        rows.forEach { row ->
            val obj = JSONObject()
            headers.forEachIndexed { idx, h ->
                obj.put(h, row.getOrElse(idx) { "" })
            }
            arr.put(obj)
        }
        val root = JSONObject().apply {
            put("title", title)
            put("exportedAt", System.currentTimeMillis())
            put("count", rows.size)
            put("records", arr)
        }
        output.write(root.toString(2).toByteArray(Charsets.UTF_8))
    }

    fun writeXlsx(
        sheetTitle: String,
        headers: List<String>,
        rows: List<List<String>>,
        output: OutputStream,
    ) {
        val bytes = buildXlsxBytes(sheetTitle, headers, rows)
        output.write(bytes)
    }

    fun buildXlsxBytes(
        sheetTitle: String,
        headers: List<String>,
        rows: List<List<String>>,
    ): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zip ->
            zip.putNextEntry(ZipEntry("[Content_Types].xml"))
            zip.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                </Types>""".trimIndent().toByteArray(Charsets.UTF_8)
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("_rels/.rels"))
            zip.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                </Relationships>""".trimIndent().toByteArray(Charsets.UTF_8)
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
            zip.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                </Relationships>""".trimIndent().toByteArray(Charsets.UTF_8)
            )
            zip.closeEntry()

            val safeTitle = escapeXml(sheetTitle.take(30).ifBlank { "Report" })
            zip.putNextEntry(ZipEntry("xl/workbook.xml"))
            zip.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets>
                    <sheet name="$safeTitle" sheetId="1" r:id="rId1"/>
                  </sheets>
                </workbook>""".trimIndent().toByteArray(Charsets.UTF_8)
            )
            zip.closeEntry()

            val sheetXml = StringBuilder()
            sheetXml.append(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetViews><sheetView rightToLeft="1" workbookViewId="0"/></sheetViews>
                  <sheetData>""".trimIndent()
            )
            val allRows = listOf(headers) + rows
            allRows.forEachIndexed { rIndex, rowValues ->
                val rowNum = rIndex + 1
                sheetXml.append("""<row r="$rowNum">""")
                rowValues.forEachIndexed { cIndex, value ->
                    val colRef = colIndexToLetters(cIndex) + rowNum
                    sheetXml.append("""<c r="$colRef" t="inlineStr"><is><t>${escapeXml(value)}</t></is></c>""")
                }
                sheetXml.append("</row>")
            }
            sheetXml.append("</sheetData></worksheet>")

            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zip.write(sheetXml.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return bos.toByteArray()
    }

    private fun colIndexToLetters(index: Int): String {
        var i = index
        val sb = StringBuilder()
        while (i >= 0) {
            sb.append(('A'.code + (i % 26)).toChar())
            i = (i / 26) - 1
        }
        return sb.reverse().toString()
    }

    private fun escapeCsv(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }

    private fun escapeXml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
