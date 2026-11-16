package com.example.data.assistant

import com.example.data.local.CustomerWithBalance
import com.example.data.local.DashboardSummary
import com.example.data.local.ReminderWithCustomer
import com.example.data.local.TransactionWithCustomer
import com.example.data.local.TransferStatus
import com.example.data.local.TransferWithCustomer
import com.example.util.FormatUtils

data class AssistantMessage(
    val id: Long = System.nanoTime(),
    val isUser: Boolean,
    val text: String,
    val highlightedCustomers: List<CustomerWithBalance> = emptyList(),
)

class LocalSmartAssistant {
    val quickPrompts = listOf(
        "ملخص الوضع المالي",
        "أعلى 5 عملاء مديونية",
        "العملاء المتأخرون عن السداد",
        "حالة الحوالات المعلقة",
        "نسبة التحصيل والتحليل",
        "اقتراح رسائل تذكير",
    )

    fun answerQuery(
        query: String,
        summary: DashboardSummary,
        customers: List<CustomerWithBalance>,
        transactions: List<TransactionWithCustomer>,
        transfers: List<TransferWithCustomer>,
        reminders: List<ReminderWithCustomer>,
        currency: String,
    ): AssistantMessage {
        val q = FormatUtils.normalizeArabicText(query)
        val active = customers.filter { !it.isArchived }
        val debtors = active.filter { it.balance > 0.001 }.sortedByDescending { it.balance }

        // Check if query asks about a specific customer by name or phone
        val matchedCustomer = active.firstOrNull {
            val n = FormatUtils.normalizeArabicText(it.name)
            n.length >= 2 && (q.contains(n) || (it.phone.isNotBlank() && q.contains(it.phone)))
        }
        if (matchedCustomer != null) {
            val custTx = transactions.filter { it.customerId == matchedCustomer.id && !it.isReversed }
            val text = buildString {
                appendLine("تحليل حساب العميل: ${matchedCustomer.name}")
                appendLine("• رقم الحساب: ${matchedCustomer.accountNumber}")
                appendLine("• الهاتف: ${matchedCustomer.phone.ifBlank { "غير مسجل" }}")
                appendLine("• إجمالي الديون: ${FormatUtils.formatAmount(matchedCustomer.totalDebts, currency)}")
                appendLine("• إجمالي المدفوعات: ${FormatUtils.formatAmount(matchedCustomer.totalPayments, currency)}")
                appendLine("• الرصيد المستحق: ${FormatUtils.formatAmount(matchedCustomer.balance, currency)}")
                appendLine("• عدد العمليات المعتمدة: ${custTx.size}")
                matchedCustomer.lastTransactionDate?.let {
                    appendLine("• آخر حركة مالية: ${FormatUtils.formatDate(it)}")
                }
            }
            return AssistantMessage(isUser = false, text = text.trim(), highlightedCustomers = listOf(matchedCustomer))
        }

        return when {
            q.contains("اعلي") || q.contains("اكبر") || q.contains("اكثر") || q.contains("مديونية") -> {
                val top = debtors.take(5)
                if (top.isEmpty()) {
                    AssistantMessage(isUser = false, text = "لا توجد ديون مستحقة حالياً، جميع العملاء مسددون.")
                } else {
                    val sb = StringBuilder("أعلى ${top.size} عملاء مديونية حالياً:\n")
                    top.forEachIndexed { idx, c ->
                        sb.appendLine("${idx + 1}. ${c.name} — الرصيد: ${FormatUtils.formatAmount(c.balance, currency)}")
                    }
                    AssistantMessage(isUser = false, text = sb.toString().trim(), highlightedCustomers = top)
                }
            }
            q.contains("متاخر") || q.contains("تذكير") -> {
                val now = System.currentTimeMillis()
                val overdueIds = reminders.filter {
                    it.status.name == "OVERDUE" || (it.status.name == "PENDING" && it.dueDate < now)
                }.map { it.customerId }.toSet()
                val staleDebtors = debtors.filter {
                    it.id in overdueIds || (it.lastTransactionDate != null && now - it.lastTransactionDate > 14 * 86_400_000L)
                }
                if (staleDebtors.isEmpty()) {
                    AssistantMessage(isUser = false, text = "ممتاز! لا يوجد عملاء متأخرون عن مواعيد السداد حالياً.")
                } else {
                    val sb = StringBuilder("العملاء المتأخرون أو ذوو الديون الراكدة (${staleDebtors.size}):\n")
                    staleDebtors.take(6).forEach { c ->
                        sb.appendLine("• ${c.name}: ${FormatUtils.formatAmount(c.balance, currency)}")
                    }
                    sb.append("\nنصيحة محلية: يمكنك التوجه إلى شاشة رسائل SMS واختيار (تحديد المتأخرين) لإرسال تذكير جماعي.")
                    AssistantMessage(isUser = false, text = sb.toString().trim(), highlightedCustomers = staleDebtors.take(5))
                }
            }
            q.contains("حوال") -> {
                val pending = transfers.filter { it.status == TransferStatus.PENDING }
                val confirmed = transfers.filter { it.status == TransferStatus.CONFIRMED }
                val text = buildString {
                    appendLine("ملخص الحوالات الواردة المحلي:")
                    appendLine("• حوالات بانتظار التأكيد: ${pending.size} بإجمالي ${FormatUtils.formatAmount(pending.sumOf { it.amount }, currency)}")
                    appendLine("• حوالات مؤكدة ومسجلة كمدفوعات: ${confirmed.size} بإجمالي ${FormatUtils.formatAmount(confirmed.sumOf { it.amount }, currency)}")
                    if (pending.isNotEmpty()) {
                        appendLine("\nأحدث الحوالات المعلقة:")
                        pending.take(3).forEach { tr ->
                            appendLine("- ${tr.company.titleAr} | مرجع ${tr.referenceNumber} | ${FormatUtils.formatAmount(tr.amount, currency)} (${tr.senderName})")
                        }
                    }
                }
                AssistantMessage(isUser = false, text = text.trim())
            }
            q.contains("نسب") || q.contains("تحصيل") || q.contains("تحليل") -> {
                val rate = if (summary.totalDebts > 0) (summary.totalPayments / summary.totalDebts) * 100.0 else 100.0
                val reversedCount = transactions.count { it.isReversed }
                val text = buildString {
                    appendLine("التحليل المالي الذكي (محلي 100%):")
                    appendLine("• نسبة التحصيل الإجمالية: ${"%.1f".format(rate)}%")
                    appendLine("• إجمالي الديون المعتمدة: ${FormatUtils.formatAmount(summary.totalDebts, currency)}")
                    appendLine("• إجمالي المدفوعات المحصلة: ${FormatUtils.formatAmount(summary.totalPayments, currency)}")
                    appendLine("• صافي الديون المتبقية: ${FormatUtils.formatAmount(summary.netOutstandingBalance, currency)}")
                    appendLine("• العمليات المعكوسة (للمراجعة): $reversedCount")
                }
                AssistantMessage(isUser = false, text = text.trim())
            }
            q.contains("رسا") || q.contains("sms") -> {
                val top = debtors.take(3)
                val text = buildString {
                    appendLine("اقتراح ذكي للتواصل عبر SMS:")
                    appendLine("لديك ${debtors.size} عميل مدين بإجمالي ${FormatUtils.formatAmount(summary.netOutstandingBalance, currency)}.")
                    if (top.isNotEmpty()) {
                        appendLine("الأولوية المقترحة للتحصيل:")
                        top.forEach { c ->
                            appendLine("• ${c.name}: ${FormatUtils.formatAmount(c.balance, currency)}")
                        }
                    }
                    append("افتح (رسائل SMS) لمعاينة الرسالة وتعديلها قبل فتح تطبيق الرسائل.")
                }
                AssistantMessage(isUser = false, text = text.trim(), highlightedCustomers = top)
            }
            else -> {
                val rate = if (summary.totalDebts > 0) (summary.totalPayments / summary.totalDebts) * 100.0 else 0.0
                val text = buildString {
                    appendLine("ملخص مدير الديون الذكي برو:")
                    appendLine("• عدد العملاء النشطين: ${summary.totalCustomers} (مدينون: ${summary.activeDebtorsCount})")
                    appendLine("• إجمالي الديون: ${FormatUtils.formatAmount(summary.totalDebts, currency)}")
                    appendLine("• إجمالي المحصل: ${FormatUtils.formatAmount(summary.totalPayments, currency)} (${"%.1f".format(rate)}%)")
                    appendLine("• صافي الرصيد المستحق: ${FormatUtils.formatAmount(summary.netOutstandingBalance, currency)}")
                    appendLine("• حوالات معلقة: ${summary.pendingTransfersCount} | تذكيرات متأخرة: ${summary.overdueRemindersCount}")
                    append("\nيمكنك سؤالي عن أي عميل بالاسم، أو عن أعلى الديون، أو المتأخرين، أو الحوالات دون الحاجة للإنترنت.")
                }
                AssistantMessage(isUser = false, text = text.trim(), highlightedCustomers = debtors.take(3))
            }
        }
    }
}
