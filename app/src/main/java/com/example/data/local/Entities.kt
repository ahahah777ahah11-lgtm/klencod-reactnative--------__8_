package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

enum class TransactionType(val labelAr: String, val isDebit: Boolean) {
    DEBT("دين", true),
    PAYMENT("دفعة", false),
    OPENING_BALANCE("رصيد افتتاحي", true),
    SETTLEMENT("تسوية", false),
}

enum class ReminderStatus(val labelAr: String) {
    PENDING("قيد الانتظار"),
    COMPLETED("مكتمل"),
    SNOOZED("مؤجل"),
    OVERDUE("متأخر"),
}

enum class TransferCompany(val titleAr: String) {
    AL_NAJM("النجم"),
    AL_IMTIYAZ_EXPRESS("الامتياز إكسبرس"),
    DADIEH("داديه"),
    AL_AKWA_MONEY("الأكوع موني"),
    HB("إتش بي"),
}

enum class TransferStatus(val labelAr: String) {
    PENDING("بانتظار التأكيد"),
    CONFIRMED("مؤكدة ومسجلة كدفعة"),
    CANCELLED("ملغاة"),
}

enum class MessageStatus(val labelAr: String) {
    INTENT_OPENED("تم فتح تطبيق الرسائل (بانتظار التأكيد)"),
    USER_CONFIRMED_SENT("تم تأكيد الإرسال يدوياً"),
    SENT_VIA_SMS_MANAGER("تم الإرسال عبر SmsManager"),
    SEND_FAILED("فشل الإرسال"),
    CANCELLED("تم الإلغاء"),
}

@Entity(
    tableName = "customers",
    indices = [
        Index(value = ["name"]),
        Index(value = ["phone"]),
        Index(value = ["account_number"]),
    ],
)
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    @ColumnInfo(name = "account_number") val accountNumber: String = "",
    val notes: String = "",
    @ColumnInfo(name = "credit_limit") val creditLimit: Double = 0.0,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_archived") val isArchived: Boolean = false,
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customer_id"],
            onDelete = ForeignKey.RESTRICT,
        )
    ],
    indices = [
        Index(value = ["customer_id"]),
        Index(value = ["transaction_date"]),
        Index(value = ["reference_number"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "customer_id") val customerId: Long,
    val type: TransactionType,
    val amount: Double,
    val description: String = "",
    @ColumnInfo(name = "reference_number") val referenceNumber: String? = null,
    @ColumnInfo(name = "source_app") val sourceApp: String? = null,
    @ColumnInfo(name = "transaction_date") val transactionDate: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_reversed") val isReversed: Boolean = false,
    @ColumnInfo(name = "reversed_at") val reversedAt: Long? = null,
    @ColumnInfo(name = "reversal_reason") val reversalReason: String? = null,
)

/**
 * Room schema aliases matching the Customers, Debts, Payments & Clients domain nomenclature.
 */
typealias Customers = CustomerEntity
typealias Clients = CustomerEntity
typealias Client = CustomerEntity
typealias Debt = TransactionEntity
typealias DebtEntity = TransactionEntity

@Entity(
    tableName = "debts",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customer_id"],
            onDelete = ForeignKey.RESTRICT,
        )
    ],
    indices = [
        Index(value = ["customer_id"]),
        Index(value = ["due_date"]),
        Index(value = ["linked_transaction_id"]),
    ],
)
data class DebtsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "customer_id") val customerId: Long,
    val amount: Double,
    val description: String = "",
    @ColumnInfo(name = "due_date") val dueDate: Long? = null,
    @ColumnInfo(name = "is_opening_balance") val isOpeningBalance: Boolean = false,
    @ColumnInfo(name = "linked_transaction_id") val linkedTransactionId: Long? = null,
    @ColumnInfo(name = "reference_number") val referenceNumber: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_reversed") val isReversed: Boolean = false,
)

typealias Debts = DebtsEntity

@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customer_id"],
            onDelete = ForeignKey.RESTRICT,
        )
    ],
    indices = [
        Index(value = ["customer_id"]),
        Index(value = ["payment_date"]),
        Index(value = ["linked_transaction_id"]),
    ],
)
data class PaymentsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "customer_id") val customerId: Long,
    val amount: Double,
    val description: String = "",
    @ColumnInfo(name = "is_settlement") val isSettlement: Boolean = false,
    @ColumnInfo(name = "linked_transaction_id") val linkedTransactionId: Long? = null,
    @ColumnInfo(name = "reference_number") val referenceNumber: String? = null,
    @ColumnInfo(name = "payment_date") val paymentDate: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_reversed") val isReversed: Boolean = false,
)

typealias Payments = PaymentsEntity

/**
 * Room one-to-many relational models linking Customers with Debts (and Payments).
 */
data class CustomerWithDebts(
    @Embedded val customer: CustomerEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "customer_id",
    )
    val debts: List<DebtsEntity>,
)

data class CustomerWithDebtsAndPayments(
    @Embedded val customer: CustomerEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "customer_id",
    )
    val debts: List<DebtsEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "customer_id",
    )
    val payments: List<PaymentsEntity>,
)

@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customer_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["customer_id"]),
        Index(value = ["due_date"]),
    ],
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "customer_id") val customerId: Long,
    val title: String,
    @ColumnInfo(name = "due_date") val dueDate: Long,
    @ColumnInfo(name = "amount_due") val amountDue: Double = 0.0,
    val status: ReminderStatus = ReminderStatus.PENDING,
    val notes: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "transfers",
    indices = [
        Index(value = ["reference_number"], unique = true),
        Index(value = ["customer_id"]),
    ],
)
data class TransferEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val company: TransferCompany,
    @ColumnInfo(name = "reference_number") val referenceNumber: String,
    val amount: Double,
    @ColumnInfo(name = "sender_name") val senderName: String,
    @ColumnInfo(name = "transfer_date") val transferDate: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "customer_id") val customerId: Long? = null,
    val status: TransferStatus = TransferStatus.PENDING,
    @ColumnInfo(name = "linked_transaction_id") val linkedTransactionId: Long? = null,
    val notes: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "message_logs",
    indices = [Index(value = ["customer_id"]), Index(value = ["created_at"])],
)
data class MessageLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "customer_id") val customerId: Long,
    @ColumnInfo(name = "customer_name") val customerName: String,
    val phone: String,
    @ColumnInfo(name = "message_body") val messageBody: String,
    @ColumnInfo(name = "template_name") val templateName: String,
    val status: MessageStatus = MessageStatus.INTENT_OPENED,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "sms_templates")
data class SmsTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val body: String,
    @ColumnInfo(name = "is_default") val isDefault: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "audit_logs",
    indices = [Index(value = ["created_at"])],
)
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "action_type") val actionType: String,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: Long? = null,
    val summary: String,
    val details: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

data class CustomerWithBalance(
    val id: Long,
    val name: String,
    val phone: String,
    val accountNumber: String,
    val notes: String,
    val creditLimit: Double,
    val createdAt: Long,
    val updatedAt: Long,
    val isArchived: Boolean,
    val totalDebts: Double,
    val totalPayments: Double,
    val balance: Double,
    val transactionCount: Int,
    val lastTransactionDate: Long?,
    val overdueRemindersCount: Int,
)

data class TransactionWithCustomer(
    val id: Long,
    val customerId: Long,
    val customerName: String,
    val customerPhone: String,
    val type: TransactionType,
    val amount: Double,
    val description: String,
    val referenceNumber: String?,
    val sourceApp: String?,
    val transactionDate: Long,
    val createdAt: Long,
    val isReversed: Boolean,
    val reversedAt: Long?,
    val reversalReason: String?,
)

data class ReminderWithCustomer(
    val id: Long,
    val customerId: Long,
    val customerName: String,
    val customerPhone: String,
    val customerBalance: Double,
    val title: String,
    val dueDate: Long,
    val amountDue: Double,
    val status: ReminderStatus,
    val notes: String,
    val createdAt: Long,
)

data class TransferWithCustomer(
    val id: Long,
    val company: TransferCompany,
    val referenceNumber: String,
    val amount: Double,
    val senderName: String,
    val transferDate: Long,
    val customerId: Long?,
    val customerName: String?,
    val status: TransferStatus,
    val linkedTransactionId: Long?,
    val notes: String,
    val createdAt: Long,
)

data class DashboardSummary(
    val totalCustomers: Int = 0,
    val activeDebtorsCount: Int = 0,
    val settledCustomersCount: Int = 0,
    val totalDebts: Double = 0.0,
    val totalPayments: Double = 0.0,
    val netOutstandingBalance: Double = 0.0,
    val pendingTransfersCount: Int = 0,
    val pendingTransfersAmount: Double = 0.0,
    val overdueRemindersCount: Int = 0,
)
