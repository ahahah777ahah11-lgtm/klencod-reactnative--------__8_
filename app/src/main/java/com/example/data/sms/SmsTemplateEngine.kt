package com.example.data.sms

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.data.local.CustomerWithBalance
import com.example.data.preferences.AppSettings
import com.example.util.FormatUtils

object SmsTemplateEngine {
    val availableVariables = listOf(
        "{اسم_العميل}" to "اسم العميل",
        "{الرصيد}" to "الرصيد المستحق الحالي",
        "{إجمالي_الديون}" to "إجمالي الديون",
        "{إجمالي_المدفوعات}" to "إجمالي المدفوعات",
        "{التاريخ}" to "تاريخ اليوم",
        "{اسم_المتجر}" to "اسم المتجر من الإعدادات",
        "{رقم_التواصل}" to "رقم التواصل من الإعدادات",
        "{رقم_الحساب}" to "رقم حساب العميل",
    )

    fun renderMessage(
        templateBody: String,
        customer: CustomerWithBalance,
        settings: AppSettings,
        nowMillis: Long = System.currentTimeMillis(),
    ): String {
        val cur = settings.currencySymbol
        return templateBody
            .replace("{اسم_العميل}", customer.name)
            .replace("{الرصيد}", FormatUtils.formatAmount(customer.balance, cur))
            .replace("{إجمالي_الديون}", FormatUtils.formatAmount(customer.totalDebts, cur))
            .replace("{إجمالي_المدفوعات}", FormatUtils.formatAmount(customer.totalPayments, cur))
            .replace("{التاريخ}", FormatUtils.formatDate(nowMillis))
            .replace("{اسم_المتجر}", settings.storeName)
            .replace("{رقم_التواصل}", settings.contactPhone.ifBlank { "-" })
            .replace("{رقم_الحساب}", customer.accountNumber.ifBlank { "-" })
    }

    fun buildSmsIntent(phone: String, messageBody: String): Intent {
        val normalized = FormatUtils.normalizePhone(phone)
        return Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:${Uri.encode(normalized)}")
            putExtra("sms_body", messageBody)
        }
    }

    fun launchSmsApp(context: Context, phone: String, messageBody: String): Boolean {
        val intent = buildSmsIntent(phone, messageBody).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
