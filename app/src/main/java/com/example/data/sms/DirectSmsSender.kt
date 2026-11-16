package com.example.data.sms

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.example.data.local.CustomerWithBalance
import com.example.data.local.DebtsEntity
import com.example.data.local.MessageLogEntity
import com.example.data.local.MessageStatus
import com.example.data.local.ReminderStatus
import com.example.data.local.ReminderWithCustomer
import com.example.data.preferences.AppSettings
import com.example.util.FormatUtils

data class SmsInboxMessage(
    val id: Long,
    val address: String,
    val body: String,
    val timestamp: Long,
)

enum class AutoReminderItemStatus {
    SENT,
    FAILED,
    SKIPPED_NO_PHONE,
    SKIPPED_COOLDOWN,
    PERMISSION_DENIED,
}

data class AutoReminderDispatchItem(
    val customerId: Long,
    val customerName: String,
    val phone: String,
    val amountDue: Double,
    val messageBody: String,
    val status: AutoReminderItemStatus,
    val detail: String = "",
)

data class AutoReminderDispatchReport(
    val evaluatedDueCustomersCount: Int,
    val sentCount: Int,
    val failedCount: Int,
    val skippedNoPhoneCount: Int,
    val skippedCooldownCount: Int,
    val permissionDenied: Boolean,
    val items: List<AutoReminderDispatchItem>,
)

sealed class SmsDispatchResult {
    data class Dispatched(val partsCount: Int, val normalizedPhone: String) : SmsDispatchResult()
    data object PermissionDenied : SmsDispatchResult()
    data object NoTelephonySupport : SmsDispatchResult()
    data class InvalidInput(val reason: String) : SmsDispatchResult()
    data class Failed(val reason: String) : SmsDispatchResult()
}

interface SmsGateway {
    fun sendSms(context: Context, destination: String, messageBody: String, logId: Long): Int
}

object AndroidSmsManagerGateway : SmsGateway {
    override fun sendSms(context: Context, destination: String, messageBody: String, logId: Long): Int {
        val smsManager = DirectSmsSender.resolveSmsManager(context)
            ?: throw UnsupportedOperationException("SmsManager unavailable")
        val parts = runCatching { smsManager.divideMessage(messageBody) }.getOrNull()
        return if (parts != null && parts.size > 1) {
            val sentIntents = ArrayList<PendingIntent>(parts.size)
            for (index in parts.indices) {
                sentIntents.add(DirectSmsSender.createSentPendingIntent(context, logId, index))
            }
            smsManager.sendMultipartTextMessage(
                destination,
                null,
                parts,
                sentIntents,
                null,
            )
            parts.size
        } else {
            val sentIntent = DirectSmsSender.createSentPendingIntent(context, logId, 0)
            smsManager.sendTextMessage(
                destination,
                null,
                messageBody,
                sentIntent,
                null,
            )
            1
        }
    }
}

object DirectSmsSender {
    const val ACTION_SMS_SENT = "com.smartdebt.app.SMS_SENT"
    const val EXTRA_LOG_ID = "extra_sms_log_id"
    const val DEFAULT_DUE_DEBT_TEMPLATE =
        "عزيزي {اسم_العميل}، نذكركم باستحقاق مبلغ {الرصيد} لصالح {اسم_المتجر} بتاريخ {التاريخ}. نرجو المبادرة بالسداد أو التواصل على {رقم_التواصل}."

    fun hasSendSmsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasReadSmsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun readRecentInboxMessages(context: Context, limit: Int = 30): List<SmsInboxMessage> {
        if (!hasReadSmsPermission(context)) return emptyList()
        val results = mutableListOf<SmsInboxMessage>()
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
        )
        runCatching {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC",
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(Telephony.Sms._ID)
                val addrIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
                while (cursor.moveToNext() && results.size < limit) {
                    val id = if (idIdx >= 0) cursor.getLong(idIdx) else results.size.toLong()
                    val addr = if (addrIdx >= 0) cursor.getString(addrIdx).orEmpty().trim() else ""
                    val body = if (bodyIdx >= 0) cursor.getString(bodyIdx).orEmpty().trim() else ""
                    val date = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L
                    if (body.isNotBlank()) {
                        results += SmsInboxMessage(
                            id = id,
                            address = addr,
                            body = body,
                            timestamp = date,
                        )
                    }
                }
            }
        }
        return results
    }

    fun resolveSmsManager(context: Context): SmsManager? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
        } catch (_: Exception) {
            null
        }
    }

    fun sendSmsToCustomer(
        context: Context,
        customerPhone: String,
        message: String,
        logId: Long = 0L,
        gateway: SmsGateway = AndroidSmsManagerGateway,
    ): SmsDispatchResult = sendDebtAlert(
        context = context,
        phone = customerPhone,
        messageBody = message,
        logId = logId,
        gateway = gateway,
    )

    fun sendDebtAlert(
        context: Context,
        phone: String,
        messageBody: String,
        logId: Long = 0L,
        gateway: SmsGateway = AndroidSmsManagerGateway,
    ): SmsDispatchResult {
        if (!hasSendSmsPermission(context)) {
            return SmsDispatchResult.PermissionDenied
        }

        val destination = FormatUtils.normalizePhone(phone)
        if (destination.isBlank() || destination.count { it.isDigit() } < 5) {
            return SmsDispatchResult.InvalidInput("رقم هاتف العميل غير صالح للإرسال النصي.")
        }

        val cleanBody = messageBody.trim()
        if (cleanBody.isBlank()) {
            return SmsDispatchResult.InvalidInput("نص الرسالة فارغ.")
        }

        return try {
            val partsCount = gateway.sendSms(context, destination, cleanBody, logId)
            SmsDispatchResult.Dispatched(partsCount, destination)
        } catch (_: SecurityException) {
            SmsDispatchResult.PermissionDenied
        } catch (_: UnsupportedOperationException) {
            SmsDispatchResult.NoTelephonySupport
        } catch (ex: Exception) {
            SmsDispatchResult.Failed(ex.message ?: "تعذر إرسال الرسالة عبر الشبكة")
        }
    }

    fun findDueDebtCustomers(
        customers: List<CustomerWithBalance>,
        debts: List<DebtsEntity>,
        reminders: List<ReminderWithCustomer>,
        settings: AppSettings,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<CustomerWithBalance> {
        val overdueThresholdMillis = settings.overdueDaysThreshold.coerceAtLeast(1) * 86_400_000L
        val dueDebtCustomerIds = debts
            .asSequence()
            .filter { !it.isReversed && it.dueDate != null && it.dueDate <= nowMillis }
            .map { it.customerId }
            .toSet()
        val dueReminderCustomerIds = reminders
            .asSequence()
            .filter {
                it.status != ReminderStatus.COMPLETED &&
                    (it.status == ReminderStatus.OVERDUE || it.dueDate <= nowMillis)
            }
            .map { it.customerId }
            .toSet()

        return customers.filter { customer ->
            if (customer.isArchived || customer.balance <= 0.001) return@filter false
            val hasDueDebtRow = customer.id in dueDebtCustomerIds
            val hasDueReminder = customer.id in dueReminderCustomerIds || customer.overdueRemindersCount > 0
            val isAgedOverdue = customer.lastTransactionDate != null &&
                (nowMillis - customer.lastTransactionDate) >= overdueThresholdMillis
            hasDueDebtRow || hasDueReminder || isAgedOverdue
        }
    }

    /**
     * Automatically sends SMS payment reminders to customers whose debts are currently due or overdue.
     * Avoids duplicate spam by skipping customers reminded via SmsManager within [cooldownMillis].
     */
    fun sendAutomaticDueDebtReminders(
        context: Context,
        customers: List<CustomerWithBalance>,
        debts: List<DebtsEntity>,
        reminders: List<ReminderWithCustomer>,
        recentMessageLogs: List<MessageLogEntity>,
        settings: AppSettings,
        nowMillis: Long = System.currentTimeMillis(),
        cooldownMillis: Long = 24L * 3_600_000L,
        templateBody: String? = null,
        gateway: SmsGateway = AndroidSmsManagerGateway,
    ): AutoReminderDispatchReport {
        val dueCustomers = findDueDebtCustomers(
            customers = customers,
            debts = debts,
            reminders = reminders,
            settings = settings,
            nowMillis = nowMillis,
        )
        if (!hasSendSmsPermission(context)) {
            return AutoReminderDispatchReport(
                evaluatedDueCustomersCount = dueCustomers.size,
                sentCount = 0,
                failedCount = 0,
                skippedNoPhoneCount = 0,
                skippedCooldownCount = 0,
                permissionDenied = true,
                items = dueCustomers.map {
                    AutoReminderDispatchItem(
                        customerId = it.id,
                        customerName = it.name,
                        phone = it.phone,
                        amountDue = it.balance,
                        messageBody = "",
                        status = AutoReminderItemStatus.PERMISSION_DENIED,
                        detail = "صلاحية SEND_SMS غير ممنوحة",
                    )
                },
            )
        }

        val activeTemplate = templateBody?.takeIf { it.isNotBlank() } ?: DEFAULT_DUE_DEBT_TEMPLATE
        val recentSentCustomerIds = recentMessageLogs
            .asSequence()
            .filter {
                it.status == MessageStatus.SENT_VIA_SMS_MANAGER &&
                    (nowMillis - it.createdAt) in 0L..cooldownMillis
            }
            .map { it.customerId }
            .toSet()

        val items = mutableListOf<AutoReminderDispatchItem>()
        var sentCount = 0
        var failedCount = 0
        var skippedNoPhoneCount = 0
        var skippedCooldownCount = 0

        for (customer in dueCustomers) {
            val normalizedPhone = FormatUtils.normalizePhone(customer.phone)
            if (normalizedPhone.isBlank() || normalizedPhone.count { it.isDigit() } < 5) {
                skippedNoPhoneCount++
                items += AutoReminderDispatchItem(
                    customerId = customer.id,
                    customerName = customer.name,
                    phone = customer.phone,
                    amountDue = customer.balance,
                    messageBody = "",
                    status = AutoReminderItemStatus.SKIPPED_NO_PHONE,
                    detail = "لا يوجد رقم هاتف صالح",
                )
                continue
            }

            if (customer.id in recentSentCustomerIds) {
                skippedCooldownCount++
                items += AutoReminderDispatchItem(
                    customerId = customer.id,
                    customerName = customer.name,
                    phone = normalizedPhone,
                    amountDue = customer.balance,
                    messageBody = "",
                    status = AutoReminderItemStatus.SKIPPED_COOLDOWN,
                    detail = "تم إرسال تذكير تلقائي للعميل خلال آخر 24 ساعة",
                )
                continue
            }

            val body = SmsTemplateEngine.renderMessage(
                templateBody = activeTemplate,
                customer = customer,
                settings = settings,
                nowMillis = nowMillis,
            )

            when (
                val dispatch = sendDebtAlert(
                    context = context,
                    phone = normalizedPhone,
                    messageBody = body,
                    logId = customer.id,
                    gateway = gateway,
                )
            ) {
                is SmsDispatchResult.Dispatched -> {
                    sentCount++
                    items += AutoReminderDispatchItem(
                        customerId = customer.id,
                        customerName = customer.name,
                        phone = dispatch.normalizedPhone,
                        amountDue = customer.balance,
                        messageBody = body,
                        status = AutoReminderItemStatus.SENT,
                        detail = "تم الإرسال تلقائياً (${dispatch.partsCount} جزء)",
                    )
                }
                SmsDispatchResult.PermissionDenied -> {
                    return AutoReminderDispatchReport(
                        evaluatedDueCustomersCount = dueCustomers.size,
                        sentCount = sentCount,
                        failedCount = failedCount,
                        skippedNoPhoneCount = skippedNoPhoneCount,
                        skippedCooldownCount = skippedCooldownCount,
                        permissionDenied = true,
                        items = items,
                    )
                }
                is SmsDispatchResult.Failed -> {
                    failedCount++
                    items += AutoReminderDispatchItem(
                        customerId = customer.id,
                        customerName = customer.name,
                        phone = normalizedPhone,
                        amountDue = customer.balance,
                        messageBody = body,
                        status = AutoReminderItemStatus.FAILED,
                        detail = dispatch.reason,
                    )
                }
                is SmsDispatchResult.InvalidInput -> {
                    failedCount++
                    items += AutoReminderDispatchItem(
                        customerId = customer.id,
                        customerName = customer.name,
                        phone = normalizedPhone,
                        amountDue = customer.balance,
                        messageBody = body,
                        status = AutoReminderItemStatus.FAILED,
                        detail = dispatch.reason,
                    )
                }
                SmsDispatchResult.NoTelephonySupport -> {
                    failedCount++
                    items += AutoReminderDispatchItem(
                        customerId = customer.id,
                        customerName = customer.name,
                        phone = normalizedPhone,
                        amountDue = customer.balance,
                        messageBody = body,
                        status = AutoReminderItemStatus.FAILED,
                        detail = "الجهاز لا يدعم خدمة الإرسال الخلوي المباشر",
                    )
                }
            }
        }

        return AutoReminderDispatchReport(
            evaluatedDueCustomersCount = dueCustomers.size,
            sentCount = sentCount,
            failedCount = failedCount,
            skippedNoPhoneCount = skippedNoPhoneCount,
            skippedCooldownCount = skippedCooldownCount,
            permissionDenied = false,
            items = items,
        )
    }

    internal fun createSentPendingIntent(
        context: Context,
        logId: Long,
        partIndex: Int,
    ): PendingIntent {
        val intent = Intent(ACTION_SMS_SENT).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_LOG_ID, logId)
            putExtra("part_index", partIndex)
        }
        val requestCode = ((logId * 31L) + partIndex).toInt()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
