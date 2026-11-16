package com.example.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.data.local.AuditLogEntity
import com.example.data.local.ReminderEntity
import com.example.data.local.ReminderStatus
import com.example.data.local.SmartDebtDatabase
import com.example.util.FormatUtils
import java.util.concurrent.TimeUnit

data class DueDateScanSummary(
    val upcomingBeforeDueCount: Int,
    val newlyOverdueCount: Int,
    val upcomingCustomerNames: List<String>,
    val upcomingNotificationPosted: Boolean,
    val overdueNotificationPosted: Boolean,
)

class OverdueReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val db = SmartDebtDatabase.getInstance(applicationContext)
        runDueDateScan(applicationContext, db)
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "smart_debt_overdue_check"
        const val CHANNEL_ID = "debt_reminders_local"
        const val NOTIFICATION_ID_UPCOMING = 1041
        const val NOTIFICATION_ID_OVERDUE = 1042
        const val DEFAULT_LEAD_HOURS = 48L

        suspend fun runDueDateScan(
            context: Context,
            db: SmartDebtDatabase,
            nowMillis: Long = System.currentTimeMillis(),
            leadWindowHours: Long = DEFAULT_LEAD_HOURS,
        ): DueDateScanSummary {
            val leadCutoff = nowMillis + (leadWindowHours * 3_600_000L)
            val allReminders = db.reminderDao().getAllRemindersOnce()
            val customersById = db.customerDao().getAllCustomersOnce().associateBy { it.id }

            // 1. Identify upcoming reminders BEFORE their due date (within lead window)
            val upcoming = allReminders
                .filter { r ->
                    (r.status == ReminderStatus.PENDING || r.status == ReminderStatus.SNOOZED) &&
                        r.dueDate in nowMillis..leadCutoff
                }
                .sortedBy { it.dueDate }

            // 2. Mark newly overdue reminders
            val newlyOverdue = db.reminderDao().markDueAsOverdue(nowMillis)

            val upcomingNames = upcoming.mapNotNull { customersById[it.customerId]?.name }.distinct()

            var upcomingPosted = false
            if (upcoming.isNotEmpty()) {
                val first = upcoming.first()
                val firstCustomer = customersById[first.customerId]?.name ?: "أحد العملاء"
                val title = "تنبيه قبل موعد استحقاق الدين"
                val message = if (upcoming.size == 1) {
                    "تذكير لـ $firstCustomer (${first.title}) يستحق بتاريخ ${FormatUtils.formatDate(first.dueDate)} بمبلغ ${FormatUtils.formatAmount(first.amountDue)}"
                } else {
                    "يوجد ${upcoming.size} مواعيد سداد تستحق خلال $leadWindowHours ساعة، أولها لـ $firstCustomer بتاريخ ${FormatUtils.formatDate(first.dueDate)}."
                }
                upcomingPosted = postLocalNotification(
                    context = context,
                    notificationId = NOTIFICATION_ID_UPCOMING,
                    title = title,
                    message = message,
                )
            }

            var overduePosted = false
            if (newlyOverdue > 0) {
                overduePosted = postLocalNotification(
                    context = context,
                    notificationId = NOTIFICATION_ID_OVERDUE,
                    title = "تذكيرات السداد المتأخرة",
                    message = "يوجد $newlyOverdue تذكير سداد تجاوز موعد الاستحقاق ويتطلب المتابعة.",
                )
            }

            if (upcoming.isNotEmpty() || newlyOverdue > 0) {
                db.auditLogDao().insert(
                    AuditLogEntity(
                        actionType = "WORKMANAGER_DUE_CHECK",
                        entityType = "REMINDER",
                        summary = "فحص WorkManager للمواعيد: ${upcoming.size} قريبة الاستحقاق، $newlyOverdue متأخرة",
                        details = upcomingNames.joinToString("، "),
                        createdAt = nowMillis,
                    )
                )
            }

            return DueDateScanSummary(
                upcomingBeforeDueCount = upcoming.size,
                newlyOverdueCount = newlyOverdue,
                upcomingCustomerNames = upcomingNames,
                upcomingNotificationPosted = upcomingPosted,
                overdueNotificationPosted = overduePosted,
            )
        }

        private fun postLocalNotification(
            context: Context,
            notificationId: Int,
            title: String,
            message: String,
        ): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) return false
            }

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "تذكيرات مواعيد الديون المحلية",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "إشعارات محلية قبل موعد استحقاق الديون وعند التأخر دون إنترنت"
                }
                nm.createNotificationChannel(channel)
            }

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
            nm.notify(notificationId, notification)
            return true
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<OverdueReminderWorker>(12, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun runImmediateCheck(context: Context) {
            val oneTime = OneTimeWorkRequestBuilder<OverdueReminderWorker>().build()
            WorkManager.getInstance(context).enqueue(oneTime)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
