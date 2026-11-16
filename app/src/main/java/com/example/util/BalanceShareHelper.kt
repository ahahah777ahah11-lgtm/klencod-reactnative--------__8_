package com.example.util

import android.content.Context
import android.content.Intent
import com.example.data.local.CustomerWithBalance
import com.example.data.preferences.AppSettings

object BalanceShareHelper {

    fun buildCustomerBalanceText(
        customer: CustomerWithBalance,
        settings: AppSettings,
        nowMillis: Long = System.currentTimeMillis(),
    ): String {
        val cur = settings.currencySymbol
        val statusText = when {
            customer.balance > 0.001 -> "رصيد مستحق عليه"
            else -> "الحساب مسدد بالكامل"
        }
        return buildString {
            appendLine("ملخص رصيد العميل - ${settings.storeName}")
            appendLine("━━━━━━━━━━━━━━")
            appendLine("العميل: ${customer.name}")
            if (customer.accountNumber.isNotBlank()) {
                appendLine("رقم الحساب: ${customer.accountNumber}")
            }
            if (customer.phone.isNotBlank()) {
                appendLine("الهاتف: ${customer.phone}")
            }
            appendLine("إجمالي الديون: ${FormatUtils.formatAmount(customer.totalDebts, cur)}")
            appendLine("إجمالي المدفوعات: ${FormatUtils.formatAmount(customer.totalPayments, cur)}")
            appendLine("الرصيد الصافي: ${FormatUtils.formatAmount(customer.balance, cur)} ($statusText)")
            appendLine("التاريخ: ${FormatUtils.formatDate(nowMillis)}")
            if (settings.contactPhone.isNotBlank()) {
                appendLine("للتواصل: ${settings.contactPhone}")
            }
        }.trim()
    }

    fun shareCustomerBalance(
        context: Context,
        customer: CustomerWithBalance,
        settings: AppSettings,
    ): Boolean {
        return runCatching {
            val shareText = buildCustomerBalanceText(customer, settings)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "ملخص رصيد العميل: ${customer.name}")
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            val chooser = Intent.createChooser(sendIntent, "مشاركة رصيد ${customer.name}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }
}
