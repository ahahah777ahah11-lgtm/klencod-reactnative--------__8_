package com.example.data.importexport

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.data.local.CustomerWithBalance
import com.example.data.local.TransactionWithCustomer
import com.example.data.preferences.AppSettings
import com.example.util.FormatUtils
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object PaymentReceiptPdfGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 36f

    fun buildReceiptFileName(transaction: TransactionWithCustomer): String {
        val safeCustomer = transaction.customerName
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .take(24)
            .ifBlank { "customer" }
        return "Receipt_${transaction.id}_${safeCustomer}.pdf"
    }

    fun writeReceiptPdf(
        transaction: TransactionWithCustomer,
        customer: CustomerWithBalance?,
        settings: AppSettings,
        out: OutputStream,
    ): Result<Unit> = runCatching {
        val nativeAttempt = runCatching {
            val doc = createReceiptPdfDocument(transaction, customer, settings)
            try {
                doc.writeTo(out)
            } finally {
                runCatching { doc.close() }
            }
        }
        if (nativeAttempt.isFailure) {
            writeMinimalValidPdfFallback(transaction, customer, settings, out)
        }
    }

    fun createTempReceiptFile(
        context: Context,
        transaction: TransactionWithCustomer,
        customer: CustomerWithBalance?,
        settings: AppSettings,
    ): Result<File> = runCatching {
        val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
        val file = File(dir, buildReceiptFileName(transaction))
        file.outputStream().use { out ->
            writeReceiptPdf(transaction, customer, settings, out).getOrThrow()
        }
        file
    }

    fun shareReceiptPdf(
        context: Context,
        receiptFile: File,
        customerName: String,
    ): Boolean {
        return runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                receiptFile,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "إيصال سداد - $customerName")
                putExtra(Intent.EXTRA_TEXT, "مرفق إيصال استلام الدفعة للعميل: $customerName")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "مشاركة إيصال الدفع PDF").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        }.getOrElse { false }
    }

    fun printReceiptPdf(
        context: Context,
        transaction: TransactionWithCustomer,
        customer: CustomerWithBalance?,
        settings: AppSettings,
    ): Boolean {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            ?: return false
        val jobName = "سند_قبض_${transaction.id}_${transaction.customerName}"
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?,
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder(buildReceiptFileName(transaction))
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(1)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback,
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onWriteCancelled()
                    return
                }
                val doc = createReceiptPdfDocument(transaction, customer, settings)
                try {
                    FileOutputStream(destination.fileDescriptor).use { out ->
                        doc.writeTo(out)
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message)
                } finally {
                    doc.close()
                }
            }
        }
        printManager.print(jobName, adapter, PrintAttributes.Builder().build())
        return true
    }

    private fun createReceiptPdfDocument(
        transaction: TransactionWithCustomer,
        customer: CustomerWithBalance?,
        settings: AppSettings,
    ): PdfDocument {
        val doc = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas
        val cur = settings.currencySymbol

        val navy = Color.parseColor("#0F2942")
        val teal = Color.parseColor("#0E9F8B")
        val emerald = Color.parseColor("#1B8A5A")
        val slate = Color.parseColor("#4A5568")
        val lightBg = Color.parseColor("#F4F7FA")
        val mintBg = Color.parseColor("#E8F7F2")
        val borderGray = Color.parseColor("#CBD5E1")

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            color = borderGray
        }

        val rightX = PAGE_WIDTH - MARGIN
        val contentWidth = (PAGE_WIDTH - MARGIN * 2).toInt()

        // 1. Header Banner
        fillPaint.color = navy
        canvas.drawRoundRect(RectF(MARGIN, MARGIN, rightX, MARGIN + 108f), 14f, 14f, fillPaint)
        fillPaint.color = teal
        canvas.drawRect(MARGIN, MARGIN + 100f, rightX, MARGIN + 108f, fillPaint)

        val headerTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.RIGHT
        }
        val headerSubPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D9E8F5")
            textSize = 12f
            textAlign = Paint.Align.RIGHT
        }
        val receiptNumberPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7FE5D4")
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.LEFT
        }

        canvas.drawText(settings.storeName, rightX - 18f, MARGIN + 38f, headerTitlePaint)
        canvas.drawText(
            "سند قبض مالي / إيصال استلام ${transaction.type.labelAr}",
            rightX - 18f,
            MARGIN + 64f,
            headerSubPaint,
        )
        if (settings.contactPhone.isNotBlank()) {
            canvas.drawText("هاتف المتجر: ${settings.contactPhone}", rightX - 18f, MARGIN + 84f, headerSubPaint)
        }
        canvas.drawText("رقم الإيصال: RCP-${transaction.id}", MARGIN + 18f, MARGIN + 42f, receiptNumberPaint)
        canvas.drawText(
            FormatUtils.formatDate(transaction.transactionDate),
            MARGIN + 18f,
            MARGIN + 64f,
            TextPaint(headerSubPaint).apply { textAlign = Paint.Align.LEFT },
        )

        var y = MARGIN + 132f

        // 2. Highlighted Amount Box
        fillPaint.color = mintBg
        val amountRect = RectF(MARGIN, y, rightX, y + 84f)
        canvas.drawRoundRect(amountRect, 12f, 12f, fillPaint)
        strokePaint.color = teal
        canvas.drawRoundRect(amountRect, 12f, 12f, strokePaint)
        strokePaint.color = borderGray

        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = slate
            textSize = 12.5f
            textAlign = Paint.Align.RIGHT
        }
        val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = navy
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.RIGHT
        }
        val bigAmountPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = emerald
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("المبلغ المستلم من العميل", PAGE_WIDTH / 2f, y + 28f, TextPaint(labelPaint).apply { textAlign = Paint.Align.CENTER })
        canvas.drawText(FormatUtils.formatAmount(transaction.amount, cur), PAGE_WIDTH / 2f, y + 62f, bigAmountPaint)

        y += 106f

        // 3. Customer & Transaction Details Box
        fillPaint.color = lightBg
        val detailsRect = RectF(MARGIN, y, rightX, y + 218f)
        canvas.drawRoundRect(detailsRect, 12f, 12f, fillPaint)
        canvas.drawRoundRect(detailsRect, 12f, 12f, strokePaint)

        var rowY = y + 32f
        fun drawDetailRow(label: String, value: String) {
            canvas.drawText(label, rightX - 18f, rowY, labelPaint)
            canvas.drawText(value, MARGIN + 18f, rowY, TextPaint(valuePaint).apply { textAlign = Paint.Align.LEFT })
            rowY += 28f
        }

        drawDetailRow("اسم العميل:", transaction.customerName)
        drawDetailRow("رقم الهاتف:", transaction.customerPhone.ifBlank { "غير مسجل" })
        drawDetailRow("رقم الحساب:", customer?.accountNumber?.ifBlank { "-" } ?: "-")
        drawDetailRow("نوع السند:", transaction.type.labelAr)
        drawDetailRow("تاريخ ووقت السداد:", FormatUtils.formatDateTime(transaction.transactionDate))
        drawDetailRow("الرقم المرجعي:", transaction.referenceNumber?.ifBlank { "لا يوجد" } ?: "لا يوجد")

        // Multiline description via StaticLayout
        canvas.drawText("البيان / الملاحظات:", rightX - 18f, rowY, labelPaint)
        val descText = transaction.description.ifBlank { "دفعة سداد حساب" }
        val descLayout = StaticLayout.Builder
            .obtain(descText, 0, descText.length, valuePaint, contentWidth - 150)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(2)
            .build()
        canvas.save()
        canvas.translate(MARGIN + 18f, rowY - 14f)
        descLayout.draw(canvas)
        canvas.restore()

        y += 242f

        // 4. Account Standing After Payment (if customer details available)
        if (customer != null) {
            fillPaint.color = Color.WHITE
            val summaryRect = RectF(MARGIN, y, rightX, y + 114f)
            canvas.drawRoundRect(summaryRect, 12f, 12f, fillPaint)
            canvas.drawRoundRect(summaryRect, 12f, 12f, strokePaint)

            canvas.drawText("ملخص موقف الحساب", rightX - 18f, y + 28f, valuePaint)
            val colWidth = (rightX - MARGIN) / 3f
            val colHeaderPaint = TextPaint(labelPaint).apply { textAlign = Paint.Align.CENTER; textSize = 11.5f }
            val colValPaint = TextPaint(valuePaint).apply { textAlign = Paint.Align.CENTER; textSize = 14f }

            val col3Center = MARGIN + colWidth * 0.5f
            val col2Center = MARGIN + colWidth * 1.5f
            val col1Center = MARGIN + colWidth * 2.5f

            canvas.drawText("إجمالي الديون", col1Center, y + 58f, colHeaderPaint)
            canvas.drawText(FormatUtils.formatAmount(customer.totalDebts, cur), col1Center, y + 84f, colValPaint)

            canvas.drawText("إجمالي المدفوعات", col2Center, y + 58f, colHeaderPaint)
            canvas.drawText(
                FormatUtils.formatAmount(customer.totalPayments, cur),
                col2Center,
                y + 84f,
                TextPaint(colValPaint).apply { color = emerald },
            )

            canvas.drawText("الرصيد المتبقي الحالي", col3Center, y + 58f, colHeaderPaint)
            canvas.drawText(
                FormatUtils.formatAmount(customer.balance, cur),
                col3Center,
                y + 84f,
                TextPaint(colValPaint).apply { color = if (customer.balance > 0.001) Color.parseColor("#C53030") else emerald },
            )

            y += 140f
        }

        // 5. Signatures & Verification Footer
        val sigY = maxOf(y + 24f, PAGE_HEIGHT - 145f)
        canvas.drawText("توقيع المحاسب / التاجر", rightX - 40f, sigY, labelPaint)
        canvas.drawLine(rightX - 180f, sigY + 34f, rightX - 20f, sigY + 34f, strokePaint)

        canvas.drawText("توقيع المستلم / العميل", MARGIN + 40f, sigY, TextPaint(labelPaint).apply { textAlign = Paint.Align.LEFT })
        canvas.drawLine(MARGIN + 20f, sigY + 34f, MARGIN + 180f, sigY + 34f, strokePaint)

        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = slate
            textSize = 10.5f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "صادر إلكترونياً من تطبيق مدير الديون الذكي برو SMS — المعادلة المعتمدة: الرصيد = إجمالي الديون - إجمالي المدفوعات",
            PAGE_WIDTH / 2f,
            PAGE_HEIGHT - 34f,
            footerPaint,
        )

        doc.finishPage(page)
        return doc
        } catch (t: Throwable) {
            runCatching { doc.close() }
            throw t
        }
    }

    private fun writeMinimalValidPdfFallback(
        transaction: TransactionWithCustomer,
        customer: CustomerWithBalance?,
        settings: AppSettings,
        out: OutputStream,
    ) {
        fun escapePdfText(raw: String): String = raw
            .replace("\\", "\\\\")
            .replace("(", "\\(")
            .replace(")", "\\)")
            .map { if (it.code in 32..126) it else '?' }
            .joinToString("")

        val contentStream = buildString {
            append("BT\n/F1 18 Tf\n50 780 Td\n")
            append("(${escapePdfText("Smart Debt Manager Pro SMS - Payment Receipt")}) Tj\n")
            append("/F1 12 Tf\n0 -28 Td\n")
            append("(${escapePdfText("Receipt ID: RCP-${transaction.id}")}) Tj\n")
            append("0 -20 Td\n")
            append("(${escapePdfText("Amount Paid: ${transaction.amount} ${settings.currencySymbol}")}) Tj\n")
            append("0 -20 Td\n")
            append("(${escapePdfText("Date: ${FormatUtils.formatDateTime(transaction.transactionDate)}")}) Tj\n")
            if (customer != null) {
                append("0 -20 Td\n")
                append("(${escapePdfText("Remaining Balance: ${customer.balance}")}) Tj\n")
            }
            append("ET\n")
        }
        val objects = listOf(
            "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n",
            "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n",
            "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>\nendobj\n",
            "4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n",
            "5 0 obj\n<< /Length ${contentStream.toByteArray(Charsets.US_ASCII).size} >>\nstream\n${contentStream}endstream\nendobj\n",
        )
        val sb = StringBuilder("%PDF-1.4\n")
        val offsets = mutableListOf<Int>()
        objects.forEach { obj ->
            offsets.add(sb.toString().toByteArray(Charsets.US_ASCII).size)
            sb.append(obj)
        }
        val xrefStart = sb.toString().toByteArray(Charsets.US_ASCII).size
        sb.append("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { offset ->
            sb.append("%010d 00000 n \n".format(offset))
        }
        sb.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xrefStart\n%%EOF\n")
        out.write(sb.toString().toByteArray(Charsets.US_ASCII))
    }

    // ==================== كشوفات الحسابات والطباعة (Account Statements Print & PDF) ====================

    fun buildStatementFileName(customer: CustomerWithBalance): String {
        val safeName = customer.name
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .take(24)
            .ifBlank { "customer" }
        return "Statement_${customer.id}_${safeName}.pdf"
    }

    fun writeCustomerStatementPdf(
        customer: CustomerWithBalance,
        transactions: List<TransactionWithCustomer>,
        settings: AppSettings,
        out: OutputStream,
    ): Result<Unit> = runCatching {
        val nativeAttempt = runCatching {
            val doc = createCustomerStatementPdfDocument(customer, transactions, settings)
            try {
                doc.writeTo(out)
            } finally {
                runCatching { doc.close() }
            }
        }
        if (nativeAttempt.isFailure) {
            writeMinimalStatementPdfFallback(customer, transactions, settings, out)
        }
    }

    fun createTempStatementFile(
        context: Context,
        customer: CustomerWithBalance,
        transactions: List<TransactionWithCustomer>,
        settings: AppSettings,
    ): Result<File> = runCatching {
        val dir = File(context.cacheDir, "statements").apply { mkdirs() }
        val file = File(dir, buildStatementFileName(customer))
        file.outputStream().use { out ->
            writeCustomerStatementPdf(customer, transactions, settings, out).getOrThrow()
        }
        file
    }

    fun shareCustomerStatementPdf(
        context: Context,
        statementFile: File,
        customerName: String,
    ): Boolean {
        return runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                statementFile,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "كشف حساب العميل - $customerName")
                putExtra(Intent.EXTRA_TEXT, "مرفق كشف الحساب المالي للعميل: $customerName")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "مشاركة كشف الحساب PDF").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        }.getOrElse { false }
    }

    fun printCustomerStatement(
        context: Context,
        customer: CustomerWithBalance,
        transactions: List<TransactionWithCustomer>,
        settings: AppSettings,
    ): Boolean {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            ?: return false
        val jobName = "كشف_حساب_${customer.name}"
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?,
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                val pageCount = ((transactions.size.coerceAtLeast(1) - 1) / 14) + 1
                val info = PrintDocumentInfo.Builder(buildStatementFileName(customer))
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(pageCount)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback,
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onWriteCancelled()
                    return
                }
                val doc = createCustomerStatementPdfDocument(customer, transactions, settings)
                try {
                    FileOutputStream(destination.fileDescriptor).use { out ->
                        doc.writeTo(out)
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message)
                } finally {
                    doc.close()
                }
            }
        }
        printManager.print(jobName, adapter, PrintAttributes.Builder().build())
        return true
    }

    fun printPortfolioStatement(
        context: Context,
        customers: List<CustomerWithBalance>,
        settings: AppSettings,
    ): Boolean {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            ?: return false
        val activeCustomers = customers.filter { !it.isArchived }.sortedByDescending { it.balance }
        val jobName = "كشف_الديون_الشامل_${settings.storeName}"
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?,
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                val pageCount = ((activeCustomers.size.coerceAtLeast(1) - 1) / 16) + 1
                val info = PrintDocumentInfo.Builder("Portfolio_Statement.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(pageCount)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback,
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onWriteCancelled()
                    return
                }
                val doc = createPortfolioStatementPdfDocument(activeCustomers, settings)
                try {
                    FileOutputStream(destination.fileDescriptor).use { out ->
                        doc.writeTo(out)
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message)
                } finally {
                    doc.close()
                }
            }
        }
        printManager.print(jobName, adapter, PrintAttributes.Builder().build())
        return true
    }

    private fun createCustomerStatementPdfDocument(
        customer: CustomerWithBalance,
        transactions: List<TransactionWithCustomer>,
        settings: AppSettings,
    ): PdfDocument {
        val doc = PdfDocument()
        try {
            val cur = settings.currencySymbol
            val navy = Color.parseColor("#0F2942")
            val teal = Color.parseColor("#0E9F8B")
            val emerald = Color.parseColor("#1B8A5A")
            val crimson = Color.parseColor("#C53030")
            val slate = Color.parseColor("#4A5568")
            val lightBg = Color.parseColor("#F4F7FA")
            val borderGray = Color.parseColor("#CBD5E1")

            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f
                color = borderGray
            }
            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.RIGHT
            }
            val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#D9E8F5")
                textSize = 11.5f
                textAlign = Paint.Align.RIGHT
            }
            val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = slate
                textSize = 11f
                textAlign = Paint.Align.RIGHT
            }
            val valPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = navy
                textSize = 11.5f
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.RIGHT
            }
            val leftValPaint = TextPaint(valPaint).apply { textAlign = Paint.Align.LEFT }
            val rightX = PAGE_WIDTH - MARGIN

            // Build chronological running balance
            var running = 0.0
            val rows = transactions.map { tx ->
                if (!tx.isReversed) {
                    running += if (tx.type.isDebit) tx.amount else -tx.amount
                }
                tx to running
            }.reversed()

            val rowsPerPage = 14
            val chunks = if (rows.isEmpty()) listOf(emptyList()) else rows.chunked(rowsPerPage)

            chunks.forEachIndexed { pageIndex, chunk ->
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex + 1).create()
                val page = doc.startPage(pageInfo)
                val canvas = page.canvas

                fillPaint.color = navy
                canvas.drawRoundRect(RectF(MARGIN, MARGIN, rightX, MARGIN + 92f), 12f, 12f, fillPaint)
                fillPaint.color = teal
                canvas.drawRect(MARGIN, MARGIN + 86f, rightX, MARGIN + 92f, fillPaint)

                canvas.drawText(settings.storeName, rightX - 16f, MARGIN + 34f, titlePaint)
                canvas.drawText("كشف حساب تفصيلي للعميل: ${customer.name}", rightX - 16f, MARGIN + 58f, subPaint)
                canvas.drawText("الحساب: ${customer.accountNumber} | الهاتف: ${customer.phone.ifBlank { "-" }}", rightX - 16f, MARGIN + 78f, subPaint)
                canvas.drawText(
                    "صفحة ${pageIndex + 1} / ${chunks.size}",
                    MARGIN + 16f,
                    MARGIN + 36f,
                    TextPaint(subPaint).apply { textAlign = Paint.Align.LEFT },
                )
                canvas.drawText(
                    FormatUtils.formatDate(System.currentTimeMillis()),
                    MARGIN + 16f,
                    MARGIN + 58f,
                    TextPaint(subPaint).apply { textAlign = Paint.Align.LEFT },
                )

                var y = MARGIN + 110f
                if (pageIndex == 0) {
                    fillPaint.color = lightBg
                    val box = RectF(MARGIN, y, rightX, y + 66f)
                    canvas.drawRoundRect(box, 10f, 10f, fillPaint)
                    canvas.drawRoundRect(box, 10f, 10f, strokePaint)

                    val colW = (rightX - MARGIN) / 3f
                    val centers = listOf(MARGIN + colW * 2.5f, MARGIN + colW * 1.5f, MARGIN + colW * 0.5f)
                    val cHeader = TextPaint(labelPaint).apply { textAlign = Paint.Align.CENTER }
                    val cVal = TextPaint(valPaint).apply { textAlign = Paint.Align.CENTER; textSize = 13f }

                    canvas.drawText("إجمالي الديون", centers[0], y + 24f, cHeader)
                    canvas.drawText(FormatUtils.formatAmount(customer.totalDebts, cur), centers[0], y + 48f, TextPaint(cVal).apply { color = crimson })

                    canvas.drawText("إجمالي المدفوعات", centers[1], y + 24f, cHeader)
                    canvas.drawText(FormatUtils.formatAmount(customer.totalPayments, cur), centers[1], y + 48f, TextPaint(cVal).apply { color = emerald })

                    canvas.drawText("الرصيد النهائي المستحق", centers[2], y + 24f, cHeader)
                    canvas.drawText(
                        FormatUtils.formatAmount(customer.balance, cur),
                        centers[2],
                        y + 48f,
                        TextPaint(cVal).apply { color = if (customer.balance > 0.001) crimson else emerald },
                    )
                    y += 82f
                }

                // Table header
                fillPaint.color = Color.parseColor("#E2E8F0")
                canvas.drawRect(MARGIN, y, rightX, y + 26f, fillPaint)
                canvas.drawText("التاريخ والنوع", rightX - 10f, y + 17f, valPaint)
                canvas.drawText("البيان", rightX - 180f, y + 17f, valPaint)
                canvas.drawText("المبلغ / الرصيد", MARGIN + 10f, y + 17f, leftValPaint)
                y += 32f

                if (chunk.isEmpty()) {
                    canvas.drawText("لا توجد قيود مالية مسجلة لهذا العميل.", rightX - 10f, y + 20f, labelPaint)
                } else {
                    chunk.forEach { (tx, balAfter) ->
                        val rowRect = RectF(MARGIN, y, rightX, y + 36f)
                        fillPaint.color = Color.WHITE
                        canvas.drawRect(rowRect, fillPaint)
                        canvas.drawLine(MARGIN, y + 36f, rightX, y + 36f, strokePaint)

                        val typePrefix = if (tx.isReversed) "${tx.type.labelAr} (ملغاة)" else tx.type.labelAr
                        canvas.drawText(
                            "$typePrefix • ${FormatUtils.formatDate(tx.transactionDate)}",
                            rightX - 8f,
                            y + 15f,
                            valPaint,
                        )
                        canvas.drawText(tx.description.take(30), rightX - 8f, y + 30f, labelPaint)

                        val amtColor = when {
                            tx.isReversed -> slate
                            tx.type.isDebit -> crimson
                            else -> emerald
                        }
                        val sign = if (tx.type.isDebit) "+" else "-"
                        canvas.drawText(
                            "$sign${FormatUtils.formatAmount(tx.amount, cur)}",
                            MARGIN + 8f,
                            y + 15f,
                            TextPaint(leftValPaint).apply { color = amtColor },
                        )
                        canvas.drawText(
                            "الرصيد: ${FormatUtils.formatAmount(balAfter, cur)}",
                            MARGIN + 8f,
                            y + 30f,
                            TextPaint(labelPaint).apply { textAlign = Paint.Align.LEFT; textSize = 10f },
                        )
                        y += 38f
                    }
                }

                val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = slate
                    textSize = 10f
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(
                    "كشف حساب معتمد — تطبيق مدير الديون الذكي برو SMS",
                    PAGE_WIDTH / 2f,
                    PAGE_HEIGHT - 28f,
                    footerPaint,
                )
                doc.finishPage(page)
            }
            return doc
        } catch (t: Throwable) {
            runCatching { doc.close() }
            throw t
        }
    }

    private fun createPortfolioStatementPdfDocument(
        customers: List<CustomerWithBalance>,
        settings: AppSettings,
    ): PdfDocument {
        val doc = PdfDocument()
        try {
            val cur = settings.currencySymbol
            val navy = Color.parseColor("#0F2942")
            val teal = Color.parseColor("#0E9F8B")
            val emerald = Color.parseColor("#1B8A5A")
            val crimson = Color.parseColor("#C53030")
            val slate = Color.parseColor("#4A5568")
            val borderGray = Color.parseColor("#CBD5E1")

            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f
                color = borderGray
            }
            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.RIGHT
            }
            val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#D9E8F5")
                textSize = 11.5f
                textAlign = Paint.Align.RIGHT
            }
            val valPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = navy
                textSize = 11.5f
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.RIGHT
            }
            val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = slate
                textSize = 10.5f
                textAlign = Paint.Align.RIGHT
            }
            val leftValPaint = TextPaint(valPaint).apply { textAlign = Paint.Align.LEFT }
            val rightX = PAGE_WIDTH - MARGIN

            val chunks = if (customers.isEmpty()) listOf(emptyList()) else customers.chunked(16)
            val totalDebtSum = customers.sumOf { it.totalDebts }
            val totalPaySum = customers.sumOf { it.totalPayments }
            val netSum = totalDebtSum - totalPaySum

            chunks.forEachIndexed { idx, chunk ->
                val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, idx + 1).create())
                val canvas = page.canvas

                fillPaint.color = navy
                canvas.drawRoundRect(RectF(MARGIN, MARGIN, rightX, MARGIN + 88f), 12f, 12f, fillPaint)
                fillPaint.color = teal
                canvas.drawRect(MARGIN, MARGIN + 82f, rightX, MARGIN + 88f, fillPaint)

                canvas.drawText(settings.storeName, rightX - 16f, MARGIN + 34f, titlePaint)
                canvas.drawText("كشف الأرصدة والمديونيات الشامل (${customers.size} عميل)", rightX - 16f, MARGIN + 58f, subPaint)
                canvas.drawText(
                    "إجمالي الديون: ${FormatUtils.formatAmount(totalDebtSum, cur)} | المحصل: ${FormatUtils.formatAmount(totalPaySum, cur)} | الصافي: ${FormatUtils.formatAmount(netSum, cur)}",
                    rightX - 16f,
                    MARGIN + 76f,
                    subPaint,
                )

                var y = MARGIN + 108f
                fillPaint.color = Color.parseColor("#E2E8F0")
                canvas.drawRect(MARGIN, y, rightX, y + 26f, fillPaint)
                canvas.drawText("العميل والحساب", rightX - 10f, y + 17f, valPaint)
                canvas.drawText("الرصيد المتبقي", MARGIN + 10f, y + 17f, leftValPaint)
                y += 32f

                chunk.forEach { c ->
                    canvas.drawLine(MARGIN, y + 34f, rightX, y + 34f, strokePaint)
                    canvas.drawText(c.name, rightX - 8f, y + 14f, valPaint)
                    canvas.drawText("هاتف: ${c.phone.ifBlank { "-" }} • عمليات: ${c.transactionCount}", rightX - 8f, y + 29f, labelPaint)
                    canvas.drawText(
                        FormatUtils.formatAmount(c.balance, cur),
                        MARGIN + 8f,
                        y + 20f,
                        TextPaint(leftValPaint).apply { color = if (c.balance > 0.001) crimson else emerald },
                    )
                    y += 36f
                }

                doc.finishPage(page)
            }
            return doc
        } catch (t: Throwable) {
            runCatching { doc.close() }
            throw t
        }
    }

    private fun writeMinimalStatementPdfFallback(
        customer: CustomerWithBalance,
        transactions: List<TransactionWithCustomer>,
        settings: AppSettings,
        out: OutputStream,
    ) {
        fun esc(raw: String): String = raw
            .replace("\\", "\\\\")
            .replace("(", "\\(")
            .replace(")", "\\)")
            .map { if (it.code in 32..126) it else '?' }
            .joinToString("")

        val contentStream = buildString {
            append("BT\n/F1 16 Tf\n50 780 Td\n")
            append("(${esc("Account Statement - Customer ID: ${customer.id}")}) Tj\n")
            append("/F1 11 Tf\n0 -24 Td\n")
            append("(${esc("Total Debts: ${customer.totalDebts} ${settings.currencySymbol}")}) Tj\n")
            append("0 -18 Td\n")
            append("(${esc("Total Payments: ${customer.totalPayments} ${settings.currencySymbol}")}) Tj\n")
            append("0 -18 Td\n")
            append("(${esc("Net Balance: ${customer.balance} ${settings.currencySymbol}")}) Tj\n")
            append("0 -18 Td\n")
            append("(${esc("Transactions Count: ${transactions.size}")}) Tj\n")
            append("ET\n")
        }
        val objects = listOf(
            "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n",
            "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n",
            "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>\nendobj\n",
            "4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n",
            "5 0 obj\n<< /Length ${contentStream.toByteArray(Charsets.US_ASCII).size} >>\nstream\n${contentStream}endstream\nendobj\n",
        )
        val sb = StringBuilder("%PDF-1.4\n")
        val offsets = mutableListOf<Int>()
        objects.forEach { obj ->
            offsets.add(sb.toString().toByteArray(Charsets.US_ASCII).size)
            sb.append(obj)
        }
        val xrefStart = sb.toString().toByteArray(Charsets.US_ASCII).size
        sb.append("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { offset -> sb.append("%010d 00000 n \n".format(offset)) }
        sb.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xrefStart\n%%EOF\n")
        out.write(sb.toString().toByteArray(Charsets.US_ASCII))
    }
}
