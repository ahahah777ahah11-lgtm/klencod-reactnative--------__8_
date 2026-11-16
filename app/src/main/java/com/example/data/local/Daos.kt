package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Query(
        """
        SELECT 
            c.id AS id,
            c.name AS name,
            c.phone AS phone,
            c.account_number AS accountNumber,
            c.notes AS notes,
            c.credit_limit AS creditLimit,
            c.created_at AS createdAt,
            c.updated_at AS updatedAt,
            c.is_archived AS isArchived,
            COALESCE(SUM(CASE WHEN t.type IN ('DEBT', 'OPENING_BALANCE') AND t.is_reversed = 0 THEN t.amount ELSE 0 END), 0.0) AS totalDebts,
            COALESCE(SUM(CASE WHEN t.type IN ('PAYMENT', 'SETTLEMENT') AND t.is_reversed = 0 THEN t.amount ELSE 0 END), 0.0) AS totalPayments,
            (
                COALESCE(SUM(CASE WHEN t.type IN ('DEBT', 'OPENING_BALANCE') AND t.is_reversed = 0 THEN t.amount ELSE 0 END), 0.0) -
                COALESCE(SUM(CASE WHEN t.type IN ('PAYMENT', 'SETTLEMENT') AND t.is_reversed = 0 THEN t.amount ELSE 0 END), 0.0)
            ) AS balance,
            COUNT(CASE WHEN t.is_reversed = 0 THEN t.id ELSE NULL END) AS transactionCount,
            MAX(CASE WHEN t.is_reversed = 0 THEN t.transaction_date ELSE NULL END) AS lastTransactionDate,
            (SELECT COUNT(*) FROM reminders r WHERE r.customer_id = c.id AND r.status IN ('PENDING', 'OVERDUE') AND r.due_date < :now) AS overdueRemindersCount
        FROM customers c
        LEFT JOIN transactions t ON t.customer_id = c.id
        GROUP BY c.id
        ORDER BY balance DESC, c.name ASC
        """
    )
    fun observeCustomersWithBalance(now: Long = System.currentTimeMillis()): Flow<List<CustomerWithBalance>>

    @Query(
        """
        SELECT 
            c.id AS id,
            c.name AS name,
            c.phone AS phone,
            c.account_number AS accountNumber,
            c.notes AS notes,
            c.credit_limit AS creditLimit,
            c.created_at AS createdAt,
            c.updated_at AS updatedAt,
            c.is_archived AS isArchived,
            COALESCE(SUM(CASE WHEN t.type IN ('DEBT', 'OPENING_BALANCE') AND t.is_reversed = 0 THEN t.amount ELSE 0 END), 0.0) AS totalDebts,
            COALESCE(SUM(CASE WHEN t.type IN ('PAYMENT', 'SETTLEMENT') AND t.is_reversed = 0 THEN t.amount ELSE 0 END), 0.0) AS totalPayments,
            (
                COALESCE(SUM(CASE WHEN t.type IN ('DEBT', 'OPENING_BALANCE') AND t.is_reversed = 0 THEN t.amount ELSE 0 END), 0.0) -
                COALESCE(SUM(CASE WHEN t.type IN ('PAYMENT', 'SETTLEMENT') AND t.is_reversed = 0 THEN t.amount ELSE 0 END), 0.0)
            ) AS balance,
            COUNT(CASE WHEN t.is_reversed = 0 THEN t.id ELSE NULL END) AS transactionCount,
            MAX(CASE WHEN t.is_reversed = 0 THEN t.transaction_date ELSE NULL END) AS lastTransactionDate,
            (SELECT COUNT(*) FROM reminders r WHERE r.customer_id = c.id AND r.status IN ('PENDING', 'OVERDUE') AND r.due_date < :now) AS overdueRemindersCount
        FROM customers c
        LEFT JOIN transactions t ON t.customer_id = c.id
        WHERE c.id = :customerId
        GROUP BY c.id
        """
    )
    fun observeCustomerWithBalance(customerId: Long, now: Long = System.currentTimeMillis()): Flow<CustomerWithBalance?>

    @Query(
        """
        SELECT 
            c.id AS id,
            c.name AS name,
            c.phone AS phone,
            c.account_number AS accountNumber,
            c.notes AS notes,
            c.credit_limit AS creditLimit,
            c.created_at AS createdAt,
            c.updated_at AS updatedAt,
            c.is_archived AS isArchived,
            COALESCE(SUM(CASE WHEN t.type IN ('DEBT', 'OPENING_BALANCE') AND t.is_reversed = 0 THEN t.amount ELSE 0 END), 0.0) AS totalDebts,
            COALESCE(SUM(CASE WHEN t.type IN ('PAYMENT', 'SETTLEMENT') AND t.is_reversed = 0 THEN t.amount ELSE 0 END), 0.0) AS totalPayments,
            (
                COALESCE(SUM(CASE WHEN t.type IN ('DEBT', 'OPENING_BALANCE') AND t.is_reversed = 0 THEN t.amount ELSE 0 END), 0.0) -
                COALESCE(SUM(CASE WHEN t.type IN ('PAYMENT', 'SETTLEMENT') AND t.is_reversed = 0 THEN t.amount ELSE 0 END), 0.0)
            ) AS balance,
            COUNT(CASE WHEN t.is_reversed = 0 THEN t.id ELSE NULL END) AS transactionCount,
            MAX(CASE WHEN t.is_reversed = 0 THEN t.transaction_date ELSE NULL END) AS lastTransactionDate,
            0 AS overdueRemindersCount
        FROM customers c
        LEFT JOIN transactions t ON t.customer_id = c.id
        WHERE c.id = :customerId
        GROUP BY c.id
        """
    )
    suspend fun getCustomerWithBalanceOnce(customerId: Long): CustomerWithBalance?

    @Query("SELECT * FROM customers ORDER BY name ASC")
    fun observeAllCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers ORDER BY name ASC")
    suspend fun getAllCustomersOnce(): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getCustomerById(id: Long): CustomerEntity?

    @Query("SELECT * FROM customers WHERE name = :name LIMIT 1")
    suspend fun findByExactName(name: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE phone = :phone AND phone != '' LIMIT 1")
    suspend fun findByPhone(phone: String): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(customer: CustomerEntity): Long

    @Update
    suspend fun update(customer: CustomerEntity): Int

    @Delete
    suspend fun delete(customer: CustomerEntity): Int

    @Query("DELETE FROM customers WHERE id = :customerId")
    suspend fun deleteById(customerId: Long): Int

    @Transaction
    @Query("SELECT * FROM customers ORDER BY name ASC")
    fun observeCustomersWithDebts(): Flow<List<CustomerWithDebts>>

    @Transaction
    @Query("SELECT * FROM customers WHERE id = :customerId")
    suspend fun getCustomerWithDebts(customerId: Long): CustomerWithDebts?

    @Transaction
    @Query("SELECT * FROM customers WHERE id = :customerId")
    suspend fun getCustomerWithDebtsAndPayments(customerId: Long): CustomerWithDebtsAndPayments?

    @Query("DELETE FROM customers")
    suspend fun deleteAll()
}

@Dao
interface TransactionDao {
    @Query(
        """
        SELECT 
            t.id AS id,
            t.customer_id AS customerId,
            c.name AS customerName,
            c.phone AS customerPhone,
            t.type AS type,
            t.amount AS amount,
            t.description AS description,
            t.reference_number AS referenceNumber,
            t.source_app AS sourceApp,
            t.transaction_date AS transactionDate,
            t.created_at AS createdAt,
            t.is_reversed AS isReversed,
            t.reversed_at AS reversedAt,
            t.reversal_reason AS reversalReason
        FROM transactions t
        INNER JOIN customers c ON c.id = t.customer_id
        ORDER BY t.transaction_date DESC, t.id DESC
        """
    )
    fun observeAllTransactions(): Flow<List<TransactionWithCustomer>>

    @Query(
        """
        SELECT 
            t.id AS id,
            t.customer_id AS customerId,
            c.name AS customerName,
            c.phone AS customerPhone,
            t.type AS type,
            t.amount AS amount,
            t.description AS description,
            t.reference_number AS referenceNumber,
            t.source_app AS sourceApp,
            t.transaction_date AS transactionDate,
            t.created_at AS createdAt,
            t.is_reversed AS isReversed,
            t.reversed_at AS reversedAt,
            t.reversal_reason AS reversalReason
        FROM transactions t
        INNER JOIN customers c ON c.id = t.customer_id
        WHERE t.customer_id = :customerId
        ORDER BY t.transaction_date ASC, t.id ASC
        """
    )
    fun observeCustomerStatement(customerId: Long): Flow<List<TransactionWithCustomer>>

    @Query("SELECT * FROM transactions ORDER BY transaction_date ASC, id ASC")
    suspend fun getAllTransactionsOnce(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query(
        """
        SELECT * FROM transactions 
        WHERE customer_id = :customerId 
          AND type = :type 
          AND ABS(amount - :amount) < 0.001 
          AND is_reversed = 0 
          AND created_at >= :sinceMillis
          AND description = :description
        LIMIT 1
        """
    )
    suspend fun findRecentDuplicate(
        customerId: Long,
        type: TransactionType,
        amount: Double,
        description: String,
        sinceMillis: Long,
    ): TransactionEntity?

    @Query(
        """
        SELECT * FROM transactions 
        WHERE reference_number = :referenceNumber 
          AND reference_number IS NOT NULL 
          AND reference_number != '' 
          AND is_reversed = 0
        LIMIT 1
        """
    )
    suspend fun findByReferenceNumber(referenceNumber: String): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transaction: TransactionEntity): Long

    @Query(
        """
        UPDATE transactions 
        SET is_reversed = 1, reversed_at = :reversedAt, reversal_reason = :reason 
        WHERE id = :id AND is_reversed = 0
        """
    )
    suspend fun markReversed(id: Long, reversedAt: Long, reason: String): Int

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}

@Dao
interface ReminderDao {
    @Query(
        """
        SELECT 
            r.id AS id,
            r.customer_id AS customerId,
            c.name AS customerName,
            c.phone AS customerPhone,
            (
                COALESCE((SELECT SUM(amount) FROM transactions t WHERE t.customer_id = c.id AND t.type IN ('DEBT','OPENING_BALANCE') AND t.is_reversed = 0), 0.0) -
                COALESCE((SELECT SUM(amount) FROM transactions t WHERE t.customer_id = c.id AND t.type IN ('PAYMENT','SETTLEMENT') AND t.is_reversed = 0), 0.0)
            ) AS customerBalance,
            r.title AS title,
            r.due_date AS dueDate,
            r.amount_due AS amountDue,
            r.status AS status,
            r.notes AS notes,
            r.created_at AS createdAt
        FROM reminders r
        INNER JOIN customers c ON c.id = r.customer_id
        ORDER BY r.due_date ASC
        """
    )
    fun observeAllReminders(): Flow<List<ReminderWithCustomer>>

    @Query("SELECT * FROM reminders ORDER BY due_date ASC")
    suspend fun getAllRemindersOnce(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): ReminderEntity?

    @Query("UPDATE reminders SET status = 'OVERDUE' WHERE status = 'PENDING' AND due_date < :now")
    suspend fun markDueAsOverdue(now: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM reminders")
    suspend fun deleteAll()
}

@Dao
interface TransferDao {
    @Query(
        """
        SELECT 
            tr.id AS id,
            tr.company AS company,
            tr.reference_number AS referenceNumber,
            tr.amount AS amount,
            tr.sender_name AS senderName,
            tr.transfer_date AS transferDate,
            tr.customer_id AS customerId,
            c.name AS customerName,
            tr.status AS status,
            tr.linked_transaction_id AS linkedTransactionId,
            tr.notes AS notes,
            tr.created_at AS createdAt
        FROM transfers tr
        LEFT JOIN customers c ON c.id = tr.customer_id
        ORDER BY tr.transfer_date DESC, tr.id DESC
        """
    )
    fun observeAllTransfers(): Flow<List<TransferWithCustomer>>

    @Query("SELECT * FROM transfers ORDER BY transfer_date DESC")
    suspend fun getAllTransfersOnce(): List<TransferEntity>

    @Query("SELECT * FROM transfers WHERE id = :id")
    suspend fun getById(id: Long): TransferEntity?

    @Query("SELECT * FROM transfers WHERE LOWER(TRIM(reference_number)) = LOWER(TRIM(:referenceNumber)) LIMIT 1")
    suspend fun findByReferenceNumber(referenceNumber: String): TransferEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transfer: TransferEntity): Long

    @Update
    suspend fun update(transfer: TransferEntity)

    @Query("DELETE FROM transfers")
    suspend fun deleteAll()
}

@Dao
interface MessageLogDao {
    @Query("SELECT * FROM message_logs ORDER BY created_at DESC")
    fun observeAllLogs(): Flow<List<MessageLogEntity>>

    @Query("SELECT * FROM message_logs ORDER BY created_at DESC")
    suspend fun getAllLogsOnce(): List<MessageLogEntity>

    @Query("SELECT * FROM message_logs WHERE id = :id")
    suspend fun getById(id: Long): MessageLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: MessageLogEntity): Long

    @Query("UPDATE message_logs SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: MessageStatus)

    @Query("DELETE FROM message_logs")
    suspend fun deleteAll()
}

@Dao
interface SmsTemplateDao {
    @Query("SELECT * FROM sms_templates ORDER BY is_default DESC, id ASC")
    fun observeAllTemplates(): Flow<List<SmsTemplateEntity>>

    @Query("SELECT * FROM sms_templates ORDER BY is_default DESC, id ASC")
    suspend fun getAllTemplatesOnce(): List<SmsTemplateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: SmsTemplateEntity): Long

    @Update
    suspend fun update(template: SmsTemplateEntity)

    @Query("DELETE FROM sms_templates WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM sms_templates")
    suspend fun deleteAll()
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY created_at DESC, id DESC LIMIT 500")
    fun observeAuditLogs(): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs ORDER BY created_at DESC, id DESC")
    suspend fun getAllAuditLogsOnce(): List<AuditLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(auditLog: AuditLogEntity): Long

    @Query("DELETE FROM audit_logs")
    suspend fun deleteAll()
}

@Dao
interface ClientDao {
    @Query("SELECT * FROM customers WHERE is_archived = 0 ORDER BY name ASC")
    fun observeAllClients(): Flow<List<Clients>>

    @Query("SELECT * FROM customers WHERE id = :clientId LIMIT 1")
    suspend fun getClientById(clientId: Long): Clients?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertClient(client: Clients): Long

    @Update
    suspend fun updateClient(client: Clients): Int

    @Delete
    suspend fun deleteClient(client: Clients): Int

    @Query("DELETE FROM customers WHERE id = :clientId")
    suspend fun deleteClientById(clientId: Long): Int
}

@Dao
interface DebtDao {
    @Query("SELECT * FROM transactions WHERE customer_id = :clientId AND is_reversed = 0 ORDER BY transaction_date DESC, id DESC")
    fun observeClientDebtsAndPayments(clientId: Long): Flow<List<Debt>>

    @Query("SELECT * FROM transactions WHERE is_reversed = 0 ORDER BY transaction_date DESC, id DESC")
    fun observeAllDebtsAndPayments(): Flow<List<Debt>>

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM transactions WHERE customer_id = :clientId AND type IN ('DEBT', 'OPENING_BALANCE') AND is_reversed = 0")
    suspend fun getTotalDebtsForClient(clientId: Long): Double

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM transactions WHERE customer_id = :clientId AND type IN ('PAYMENT', 'SETTLEMENT') AND is_reversed = 0")
    suspend fun getTotalPaymentsForClient(clientId: Long): Double

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDebtOrPayment(entry: Debt): Long

    @Update
    suspend fun updateDebtOrPayment(entry: Debt): Int

    @Delete
    suspend fun deleteDebtOrPayment(entry: Debt): Int

    @Query("DELETE FROM transactions WHERE id = :entryId")
    suspend fun deleteDebtOrPaymentById(entryId: Long): Int
}

@Dao
interface DebtsDao {
    @Query("SELECT * FROM debts WHERE is_reversed = 0 ORDER BY created_at DESC, id DESC")
    fun observeAllDebts(): Flow<List<DebtsEntity>>

    @Query("SELECT * FROM debts WHERE customer_id = :customerId AND is_reversed = 0 ORDER BY created_at DESC, id DESC")
    fun observeDebtsForCustomer(customerId: Long): Flow<List<DebtsEntity>>

    @Query("SELECT * FROM debts WHERE id = :debtId LIMIT 1")
    suspend fun getDebtById(debtId: Long): DebtsEntity?

    @Query("SELECT * FROM debts ORDER BY created_at ASC, id ASC")
    suspend fun getAllDebtsOnce(): List<DebtsEntity>

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM debts WHERE customer_id = :customerId AND is_reversed = 0")
    suspend fun getTotalDebtsForCustomer(customerId: Long): Double

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebt(debt: DebtsEntity): Long

    @Update
    suspend fun updateDebt(debt: DebtsEntity): Int

    @Delete
    suspend fun deleteDebt(debt: DebtsEntity): Int

    @Query("DELETE FROM debts WHERE id = :debtId")
    suspend fun deleteDebtById(debtId: Long): Int

    @Query("UPDATE debts SET is_reversed = 1 WHERE linked_transaction_id = :transactionId AND is_reversed = 0")
    suspend fun markReversedByTransactionId(transactionId: Long): Int

    @Query("DELETE FROM debts")
    suspend fun deleteAll()
}

@Dao
interface PaymentsDao {
    @Query("SELECT * FROM payments WHERE is_reversed = 0 ORDER BY payment_date DESC, id DESC")
    fun observeAllPayments(): Flow<List<PaymentsEntity>>

    @Query("SELECT * FROM payments WHERE customer_id = :customerId AND is_reversed = 0 ORDER BY payment_date DESC, id DESC")
    fun observePaymentsForCustomer(customerId: Long): Flow<List<PaymentsEntity>>

    @Query("SELECT * FROM payments ORDER BY payment_date ASC, id ASC")
    suspend fun getAllPaymentsOnce(): List<PaymentsEntity>

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM payments WHERE customer_id = :customerId AND is_reversed = 0")
    suspend fun getTotalPaymentsForCustomer(customerId: Long): Double

    @Query("SELECT * FROM payments WHERE id = :paymentId LIMIT 1")
    suspend fun getPaymentById(paymentId: Long): PaymentsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: PaymentsEntity): Long

    @Update
    suspend fun updatePayment(payment: PaymentsEntity): Int

    @Delete
    suspend fun deletePayment(payment: PaymentsEntity): Int

    @Query("UPDATE payments SET is_reversed = 1 WHERE linked_transaction_id = :transactionId AND is_reversed = 0")
    suspend fun markReversedByTransactionId(transactionId: Long): Int

    @Query("DELETE FROM payments")
    suspend fun deleteAll()
}
