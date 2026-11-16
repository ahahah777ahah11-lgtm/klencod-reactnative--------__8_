package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        CustomerEntity::class,
        TransactionEntity::class,
        DebtsEntity::class,
        PaymentsEntity::class,
        ReminderEntity::class,
        TransferEntity::class,
        MessageLogEntity::class,
        SmsTemplateEntity::class,
        AuditLogEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class SmartDebtDatabase : RoomDatabase() {
    abstract fun customerDao(): CustomerDao
    abstract fun transactionDao(): TransactionDao
    abstract fun debtsDao(): DebtsDao
    abstract fun paymentsDao(): PaymentsDao
    abstract fun reminderDao(): ReminderDao
    abstract fun transferDao(): TransferDao
    abstract fun messageLogDao(): MessageLogDao
    abstract fun smsTemplateDao(): SmsTemplateDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun clientDao(): ClientDao
    abstract fun debtDao(): DebtDao

    companion object {
        const val DB_NAME = "smart_debt_manager_pro.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `debts` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `customer_id` INTEGER NOT NULL,
                        `amount` REAL NOT NULL,
                        `description` TEXT NOT NULL,
                        `due_date` INTEGER,
                        `is_opening_balance` INTEGER NOT NULL,
                        `linked_transaction_id` INTEGER,
                        `reference_number` TEXT,
                        `created_at` INTEGER NOT NULL,
                        `is_reversed` INTEGER NOT NULL,
                        FOREIGN KEY(`customer_id`) REFERENCES `customers`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_debts_customer_id` ON `debts` (`customer_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_debts_due_date` ON `debts` (`due_date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_debts_linked_transaction_id` ON `debts` (`linked_transaction_id`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `payments` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `customer_id` INTEGER NOT NULL,
                        `amount` REAL NOT NULL,
                        `description` TEXT NOT NULL,
                        `is_settlement` INTEGER NOT NULL,
                        `linked_transaction_id` INTEGER,
                        `reference_number` TEXT,
                        `payment_date` INTEGER NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `is_reversed` INTEGER NOT NULL,
                        FOREIGN KEY(`customer_id`) REFERENCES `customers`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_payments_customer_id` ON `payments` (`customer_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_payments_payment_date` ON `payments` (`payment_date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_payments_linked_transaction_id` ON `payments` (`linked_transaction_id`)")

                db.execSQL(
                    """
                    INSERT INTO `debts` (`customer_id`, `amount`, `description`, `due_date`, `is_opening_balance`, `linked_transaction_id`, `reference_number`, `created_at`, `is_reversed`)
                    SELECT `customer_id`, `amount`, `description`, NULL, CASE WHEN `type` = 'OPENING_BALANCE' THEN 1 ELSE 0 END, `id`, `reference_number`, `created_at`, `is_reversed`
                    FROM `transactions` WHERE `type` IN ('DEBT', 'OPENING_BALANCE')
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `payments` (`customer_id`, `amount`, `description`, `is_settlement`, `linked_transaction_id`, `reference_number`, `payment_date`, `created_at`, `is_reversed`)
                    SELECT `customer_id`, `amount`, `description`, CASE WHEN `type` = 'SETTLEMENT' THEN 1 ELSE 0 END, `id`, `reference_number`, `transaction_date`, `created_at`, `is_reversed`
                    FROM `transactions` WHERE `type` IN ('PAYMENT', 'SETTLEMENT')
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var INSTANCE: SmartDebtDatabase? = null

        fun getInstance(context: Context): SmartDebtDatabase {
            val current = INSTANCE
            if (current != null && current.isOpen) return current
            return synchronized(this) {
                val existing = INSTANCE
                if (existing != null && existing.isOpen) {
                    existing
                } else {
                    buildDatabase(context.applicationContext).also { INSTANCE = it }
                }
            }
        }

        fun buildDatabase(context: Context): SmartDebtDatabase {
            return Room.databaseBuilder(
                context,
                SmartDebtDatabase::class.java,
                DB_NAME,
            )
                .addMigrations(MIGRATION_1_2)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            seedDefaultTemplates(getInstance(context).smsTemplateDao())
                        }
                    }
                })
                .build()
        }

        fun checkpointWal(database: SmartDebtDatabase): Boolean {
            return runCatching {
                database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { c ->
                    c.moveToFirst()
                }
                true
            }.getOrDefault(false)
        }

        suspend fun seedDefaultTemplates(dao: SmsTemplateDao) {
            if (dao.getAllTemplatesOnce().isNotEmpty()) return
            val defaults = listOf(
                SmsTemplateEntity(
                    name = "تذكير ودي بالرصيد",
                    body = "مرحباً {اسم_العميل}، نود تذكيركم بأن رصيدكم المستحق لدى {اسم_المتجر} حتى تاريخ {التاريخ} هو {الرصيد}. رقم الحساب: {رقم_الحساب}. للاستفسار: {رقم_التواصل}.",
                    isDefault = true,
                ),
                SmsTemplateEntity(
                    name = "كشف حساب مختصر",
                    body = "الأخ/ت {اسم_العميل}، تفاصيل حسابكم لدى {اسم_المتجر}: إجمالي الديون {إجمالي_الديون}، إجمالي المدفوعات {إجمالي_المدفوعات}، الرصيد المتبقي {الرصيد}. شاكرين تعاونكم ({رقم_التواصل}).",
                    isDefault = false,
                ),
                SmsTemplateEntity(
                    name = "إشعار تأخر سداد",
                    body = "عزيزي {اسم_العميل}، نأمل المبادرة بسداد المبلغ المستحق البالغ {الرصيد} لدى {اسم_المتجر} في أقرب وقت ممكن. تاريخ الإشعار: {التاريخ}. للتواصل: {رقم_التواصل}.",
                    isDefault = false,
                ),
                SmsTemplateEntity(
                    name = "شكر على السداد",
                    body = "شكراً لكم {اسم_العميل} على سدادكم لدى {اسم_المتجر}. إجمالي المدفوعات {إجمالي_المدفوعات}، والرصيد الحالي {الرصيد}. رقم الحساب: {رقم_الحساب}.",
                    isDefault = false,
                ),
            )
            defaults.forEach { dao.insert(it) }
        }
    }
}
