package com.example.data.local

import androidx.room.withTransaction
import com.example.util.FormatUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

sealed class AccountingResult<out T> {
    data class Success<T>(val data: T, val message: String) : AccountingResult<T>()
    data class Error(val reason: String) : AccountingResult<Nothing>()
}

class AccountingRepository(private val database: SmartDebtDatabase) {
    private val customerDao = database.customerDao()
    private val transactionDao = database.transactionDao()
    private val debtsDao = database.debtsDao()
    private val paymentsDao = database.paymentsDao()
    private val reminderDao = database.reminderDao()
    private val transferDao = database.transferDao()
    private val messageLogDao = database.messageLogDao()
    private val smsTemplateDao = database.smsTemplateDao()
    private val auditLogDao = database.auditLogDao()

    fun observeCustomersWithBalance(): Flow<List<CustomerWithBalance>> =
        customerDao.observeCustomersWithBalance()

    fun observeCustomerWithBalance(customerId: Long): Flow<CustomerWithBalance?> =
        customerDao.observeCustomerWithBalance(customerId)

    fun observeCustomersWithDebts(): Flow<List<CustomerWithDebts>> =
        customerDao.observeCustomersWithDebts()

    suspend fun getCustomerWithDebts(customerId: Long): CustomerWithDebts? =
        customerDao.getCustomerWithDebts(customerId)

    suspend fun getCustomerWithDebtsAndPayments(customerId: Long): CustomerWithDebtsAndPayments? =
        customerDao.getCustomerWithDebtsAndPayments(customerId)

    fun observeAllTransactions(): Flow<List<TransactionWithCustomer>> =
        transactionDao.observeAllTransactions()

    fun observeCustomerStatement(customerId: Long): Flow<List<TransactionWithCustomer>> =
        transactionDao.observeCustomerStatement(customerId)

    fun observeAllReminders(): Flow<List<ReminderWithCustomer>> =
        reminderDao.observeAllReminders()

    fun observeAllTransfers(): Flow<List<TransferWithCustomer>> =
        transferDao.observeAllTransfers()

    fun observeMessageLogs(): Flow<List<MessageLogEntity>> =
        messageLogDao.observeAllLogs()

    fun observeSmsTemplates(): Flow<List<SmsTemplateEntity>> =
        smsTemplateDao.observeAllTemplates()

    fun observeAuditLogs(): Flow<List<AuditLogEntity>> =
        auditLogDao.observeAuditLogs()

    fun observeAllDebts(): Flow<List<DebtsEntity>> =
        debtsDao.observeAllDebts()

    fun observeAllPayments(): Flow<List<PaymentsEntity>> =
        paymentsDao.observeAllPayments()

    fun observeDashboardSummary(): Flow<DashboardSummary> {
        return combine(
            observeCustomersWithBalance(),
            observeAllTransfers(),
            observeAllReminders(),
        ) { customers, transfers, reminders ->
            val activeCustomers = customers.filter { !it.isArchived }
            val totalDebts = activeCustomers.sumOf { it.totalDebts }
            val totalPayments = activeCustomers.sumOf { it.totalPayments }
            val netBalance = totalDebts - totalPayments
            val activeDebtors = activeCustomers.count { it.balance > 0.001 }
            val settled = activeCustomers.count { it.balance <= 0.001 }
            val pendingTransfers = transfers.filter { it.status == TransferStatus.PENDING }
            val now = System.currentTimeMillis()
            val overdueCount = reminders.count {
                it.status == ReminderStatus.OVERDUE ||
                    (it.status == ReminderStatus.PENDING && it.dueDate < now)
            }
            DashboardSummary(
                totalCustomers = activeCustomers.size,
                activeDebtorsCount = activeDebtors,
                settledCustomersCount = settled,
                totalDebts = totalDebts,
                totalPayments = totalPayments,
                netOutstandingBalance = netBalance,
                pendingTransfersCount = pendingTransfers.size,
                pendingTransfersAmount = pendingTransfers.sumOf { it.amount },
                overdueRemindersCount = overdueCount,
            )
        }
    }

    suspend fun createCustomer(
        name: String,
        phone: String,
        accountNumber: String,
        creditLimit: Double,
        notes: String,
        openingBalance: Double = 0.0,
    ): AccountingResult<Long> {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            return AccountingResult.Error("يرجى إدخال اسم العميل.")
        }
        if (creditLimit < 0.0) {
            return AccountingResult.Error("الحد الائتماني لا يمكن أن يكون سالباً.")
        }
        if (openingBalance < 0.0) {
            return AccountingResult.Error("الرصيد الافتتاحي لا يمكن أن يكون سالباً.")
        }

        val cleanPhone = FormatUtils.normalizePhone(phone)
        val existingByName = customerDao.findByExactName(cleanName)
        if (existingByName != null) {
            return AccountingResult.Error("يوجد عميل مسجل بنفس الاسم مسبقاً: $cleanName")
        }
        if (cleanPhone.isNotBlank()) {
            val existingByPhone = customerDao.findByPhone(cleanPhone)
            if (existingByPhone != null) {
                return AccountingResult.Error("رقم الهاتف مستخدم مسبقاً للعميل: ${existingByPhone.name}")
            }
        }

        val now = System.currentTimeMillis()
        val id = database.withTransaction {
            val generatedAccount = accountNumber.trim().ifBlank {
                "ACC-${(1000..9999).random()}-${(now % 1000)}"
            }
            val newId = customerDao.insert(
                CustomerEntity(
                    name = cleanName,
                    phone = cleanPhone,
                    accountNumber = generatedAccount,
                    notes = notes.trim(),
                    creditLimit = creditLimit,
                    createdAt = now,
                    updatedAt = now,
                )
            )
            auditLogDao.insert(
                AuditLogEntity(
                    actionType = "CREATE_CUSTOMER",
                    entityType = "CUSTOMER",
                    entityId = newId,
                    summary = "إضافة عميل جديد: $cleanName",
                    details = "الهاتف: $cleanPhone | رقم الحساب: $generatedAccount",
                    createdAt = now,
                )
            )
            if (openingBalance > 0.0) {
                val txId = transactionDao.insert(
                    TransactionEntity(
                        customerId = newId,
                        type = TransactionType.OPENING_BALANCE,
                        amount = openingBalance,
                        description = "رصيد افتتاحي موثق عند الإنشاء",
                        transactionDate = now,
                        createdAt = now,
                    )
                )
                debtsDao.insertDebt(
                    DebtsEntity(
                        customerId = newId,
                        amount = openingBalance,
                        description = "رصيد افتتاحي موثق عند الإنشاء",
                        isOpeningBalance = true,
                        linkedTransactionId = txId,
                        createdAt = now,
                    )
                )
                auditLogDao.insert(
                    AuditLogEntity(
                        actionType = "OPENING_BALANCE",
                        entityType = "TRANSACTION",
                        entityId = txId,
                        summary = "تسجيل رصيد افتتاحي للعميل $cleanName بقيمة ${FormatUtils.formatAmount(openingBalance)}",
                        createdAt = now,
                    )
                )
            }
            newId
        }
        return AccountingResult.Success(id, "تمت إضافة العميل بنجاح")
    }

    suspend fun updateCustomer(
        customerId: Long,
        name: String,
        phone: String,
        accountNumber: String,
        creditLimit: Double,
        notes: String,
        isArchived: Boolean,
    ): AccountingResult<Unit> {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            return AccountingResult.Error("اسم العميل مطلوب.")
        }
        if (creditLimit < 0.0) {
            return AccountingResult.Error("الحد الائتماني لا يمكن أن يكون سالباً.")
        }
        val existing = customerDao.getCustomerById(customerId)
            ?: return AccountingResult.Error("العميل غير موجود.")

        val nameConflict = customerDao.findByExactName(cleanName)
        if (nameConflict != null && nameConflict.id != customerId) {
            return AccountingResult.Error("يوجد عميل آخر بنفس الاسم: $cleanName")
        }
        val cleanPhone = FormatUtils.normalizePhone(phone)
        if (cleanPhone.isNotBlank()) {
            val phoneConflict = customerDao.findByPhone(cleanPhone)
            if (phoneConflict != null && phoneConflict.id != customerId) {
                return AccountingResult.Error("رقم الهاتف مسجل لعميل آخر: ${phoneConflict.name}")
            }
        }

        val now = System.currentTimeMillis()
        database.withTransaction {
            customerDao.update(
                existing.copy(
                    name = cleanName,
                    phone = cleanPhone,
                    accountNumber = accountNumber.trim().ifBlank { existing.accountNumber },
                    creditLimit = creditLimit,
                    notes = notes.trim(),
                    isArchived = isArchived,
                    updatedAt = now,
                )
            )
            auditLogDao.insert(
                AuditLogEntity(
                    actionType = "UPDATE_CUSTOMER",
                    entityType = "CUSTOMER",
                    entityId = customerId,
                    summary = "تعديل بيانات العميل: $cleanName",
                    details = "الاسم السابق: ${existing.name} | الهاتف: $cleanPhone",
                    createdAt = now,
                )
            )
        }
        return AccountingResult.Success(Unit, "تم تحديث بيانات العميل")
    }

    suspend fun recordTransaction(
        customerId: Long,
        type: TransactionType,
        amount: Double,
        description: String,
        transactionDate: Long = System.currentTimeMillis(),
        referenceNumber: String? = null,
        sourceApp: String? = null,
        allowOverpayment: Boolean = false,
        duplicateWindowMinutes: Int = 5,
        dueDate: Long? = null,
    ): AccountingResult<Long> {
        if (amount.isNaN() || amount.isInfinite() || amount <= 0.0) {
            return AccountingResult.Error("المبلغ غير صالح: يجب أن يكون أكبر من الصفر.")
        }

        val customerBalance = customerDao.getCustomerWithBalanceOnce(customerId)
            ?: return AccountingResult.Error("العميل المحدد غير موجود.")

        val cleanDesc = description.trim().ifBlank { type.labelAr }
        val cleanRef = referenceNumber?.trim()?.takeIf { it.isNotBlank() }

        if (cleanRef != null && transactionDao.findByReferenceNumber(cleanRef) != null) {
            return AccountingResult.Error("رقم المرجع ($cleanRef) مسجل في عملية سابقة ولا يمكن تكراره.")
        }

        val sinceMillis = System.currentTimeMillis() - (duplicateWindowMinutes.coerceAtLeast(1) * 60_000L)
        val duplicate = transactionDao.findRecentDuplicate(
            customerId = customerId,
            type = type,
            amount = amount,
            description = cleanDesc,
            sinceMillis = sinceMillis,
        )
        if (duplicate != null) {
            return AccountingResult.Error("تم اكتشاف عملية مكررة مطابقة لنفس العميل والمبلغ والوصف خلال الدقائق الأخيرة.")
        }

        if (!type.isDebit && !allowOverpayment) {
            if (customerBalance.balance <= 0.001) {
                return AccountingResult.Error("رصيد العميل الحالي مسدد بالكامل (${FormatUtils.formatAmount(customerBalance.balance)})، ولا يُسمح بالدفع الزائد افتراضياً.")
            }
            if (amount - customerBalance.balance > 0.001) {
                return AccountingResult.Error(
                    "مبلغ الدفعة (${FormatUtils.formatAmount(amount)}) يتجاوز الرصيد المستحق (${FormatUtils.formatAmount(customerBalance.balance)}). يمكنك تعديل المبلغ أو تفعيل السماح بالدفع الزائد من الإعدادات."
                )
            }
        }

        val now = System.currentTimeMillis()
        val txId = database.withTransaction {
            val insertedId = transactionDao.insert(
                TransactionEntity(
                    customerId = customerId,
                    type = type,
                    amount = amount,
                    description = cleanDesc,
                    referenceNumber = cleanRef,
                    sourceApp = sourceApp,
                    transactionDate = transactionDate,
                    createdAt = now,
                )
            )
            if (type.isDebit) {
                debtsDao.insertDebt(
                    DebtsEntity(
                        customerId = customerId,
                        amount = amount,
                        description = cleanDesc,
                        dueDate = dueDate,
                        isOpeningBalance = type == TransactionType.OPENING_BALANCE,
                        linkedTransactionId = insertedId,
                        referenceNumber = cleanRef,
                        createdAt = now,
                    )
                )
            } else {
                paymentsDao.insertPayment(
                    PaymentsEntity(
                        customerId = customerId,
                        amount = amount,
                        description = cleanDesc,
                        isSettlement = type == TransactionType.SETTLEMENT,
                        linkedTransactionId = insertedId,
                        referenceNumber = cleanRef,
                        paymentDate = transactionDate,
                        createdAt = now,
                    )
                )
            }
            auditLogDao.insert(
                AuditLogEntity(
                    actionType = "ADD_${type.name}",
                    entityType = "TRANSACTION",
                    entityId = insertedId,
                    summary = "${type.labelAr} للعميل ${customerBalance.name} بمبلغ ${FormatUtils.formatAmount(amount)}",
                    details = cleanDesc + (cleanRef?.let { " | مرجع: $it" } ?: ""),
                    createdAt = now,
                )
            )
            insertedId
        }
        return AccountingResult.Success(txId, "تم تسجيل ${type.labelAr} بنجاح")
    }

    suspend fun reverseTransaction(transactionId: Long, reason: String): AccountingResult<Unit> {
        val cleanReason = reason.trim()
        if (cleanReason.isBlank()) {
            return AccountingResult.Error("يجب كتابة سبب واضح لعكس أو إلغاء العملية للحفاظ على سجل المراجعة.")
        }
        val tx = transactionDao.getById(transactionId)
            ?: return AccountingResult.Error("العملية غير موجودة.")
        if (tx.isReversed) {
            return AccountingResult.Error("تم عكس هذه العملية مسبقاً.")
        }
        val customer = customerDao.getCustomerById(tx.customerId)
        val now = System.currentTimeMillis()
        database.withTransaction {
            transactionDao.markReversed(transactionId, now, cleanReason)
            if (tx.type.isDebit) {
                debtsDao.markReversedByTransactionId(transactionId)
            } else {
                paymentsDao.markReversedByTransactionId(transactionId)
            }
            auditLogDao.insert(
                AuditLogEntity(
                    actionType = "REVERSE_TRANSACTION",
                    entityType = "TRANSACTION",
                    entityId = transactionId,
                    summary = "عكس عملية ${tx.type.labelAr} للعميل ${customer?.name ?: tx.customerId} بمبلغ ${FormatUtils.formatAmount(tx.amount)}",
                    details = "السبب: $cleanReason | الوصف الأصلي: ${tx.description}",
                    createdAt = now,
                )
            )
        }
        return AccountingResult.Success(Unit, "تم عكس العملية وتحديث الرصيد وتسجيل الأثر في سجل المراجعة")
    }

    suspend fun addTransfer(
        company: TransferCompany,
        referenceNumber: String,
        amount: Double,
        senderName: String,
        transferDate: Long,
        customerId: Long?,
        notes: String,
    ): AccountingResult<Long> {
        val cleanRef = referenceNumber.trim()
        if (cleanRef.isBlank()) {
            return AccountingResult.Error("رقم مرجع الحوالة مطلوب.")
        }
        if (amount.isNaN() || amount.isInfinite() || amount <= 0.0) {
            return AccountingResult.Error("مبلغ الحوالة يجب أن يكون موجباً وأكبر من الصفر.")
        }
        if (senderName.trim().isBlank()) {
            return AccountingResult.Error("اسم مرسل الحوالة مطلوب.")
        }
        val existingRef = transferDao.findByReferenceNumber(cleanRef)
        if (existingRef != null) {
            return AccountingResult.Error("تم اكتشاف تكرار! رقم المرجع ($cleanRef) مسجل مسبقاً لدى ${existingRef.company.titleAr}.")
        }
        val now = System.currentTimeMillis()
        val id = database.withTransaction {
            val transferId = transferDao.insert(
                TransferEntity(
                    company = company,
                    referenceNumber = cleanRef,
                    amount = amount,
                    senderName = senderName.trim(),
                    transferDate = transferDate,
                    customerId = customerId,
                    status = TransferStatus.PENDING,
                    notes = notes.trim(),
                    createdAt = now,
                )
            )
            auditLogDao.insert(
                AuditLogEntity(
                    actionType = "ADD_TRANSFER",
                    entityType = "TRANSFER",
                    entityId = transferId,
                    summary = "تسجيل حوالة واردة (${company.titleAr}) مرجع $cleanRef بمبلغ ${FormatUtils.formatAmount(amount)}",
                    details = "المرسل: ${senderName.trim()}",
                    createdAt = now,
                )
            )
            transferId
        }
        return AccountingResult.Success(id, "تم حفظ الحوالة بانتظار التأكيد")
    }

    suspend fun confirmTransferAsPayment(
        transferId: Long,
        targetCustomerId: Long,
        allowOverpayment: Boolean,
    ): AccountingResult<Long> {
        val transfer = transferDao.getById(transferId)
            ?: return AccountingResult.Error("الحوالة غير موجودة.")
        if (transfer.status == TransferStatus.CONFIRMED) {
            return AccountingResult.Error("تم تأكيد هذه الحوالة وتسجيلها كدفعة مسبقاً.")
        }
        if (transfer.status == TransferStatus.CANCELLED) {
            return AccountingResult.Error("لا يمكن تأكيد حوالة ملغاة.")
        }
        val customer = customerDao.getCustomerWithBalanceOnce(targetCustomerId)
            ?: return AccountingResult.Error("يرجى اختيار عميل صالح لربط الحوالة به.")

        if (!allowOverpayment && transfer.amount - customer.balance > 0.001) {
            return AccountingResult.Error(
                "مبلغ الحوالة (${FormatUtils.formatAmount(transfer.amount)}) أكبر من رصيد العميل المستحق (${FormatUtils.formatAmount(customer.balance)})."
            )
        }

        val existingTxRef = transactionDao.findByReferenceNumber(transfer.referenceNumber)
        if (existingTxRef != null) {
            return AccountingResult.Error("رقم مرجع الحوالة (${transfer.referenceNumber}) مسجل مسبقاً في سجل العمليات.")
        }

        val now = System.currentTimeMillis()
        val txId = database.withTransaction {
            val createdTxId = transactionDao.insert(
                TransactionEntity(
                    customerId = targetCustomerId,
                    type = TransactionType.PAYMENT,
                    amount = transfer.amount,
                    description = "دفعة من حوالة ${transfer.company.titleAr} - المرسل: ${transfer.senderName}",
                    referenceNumber = transfer.referenceNumber,
                    transactionDate = transfer.transferDate,
                    createdAt = now,
                )
            )
            paymentsDao.insertPayment(
                PaymentsEntity(
                    customerId = targetCustomerId,
                    amount = transfer.amount,
                    description = "دفعة من حوالة ${transfer.company.titleAr} - المرسل: ${transfer.senderName}",
                    isSettlement = false,
                    linkedTransactionId = createdTxId,
                    referenceNumber = transfer.referenceNumber,
                    paymentDate = transfer.transferDate,
                    createdAt = now,
                )
            )
            transferDao.update(
                transfer.copy(
                    customerId = targetCustomerId,
                    status = TransferStatus.CONFIRMED,
                    linkedTransactionId = createdTxId,
                )
            )
            auditLogDao.insert(
                AuditLogEntity(
                    actionType = "CONFIRM_TRANSFER",
                    entityType = "TRANSFER",
                    entityId = transferId,
                    summary = "تأكيد حوالة ${transfer.company.titleAr} (${transfer.referenceNumber}) كدفعة للعميل ${customer.name}",
                    details = "المبلغ: ${FormatUtils.formatAmount(transfer.amount)} | رقم العملية: $createdTxId",
                    createdAt = now,
                )
            )
            createdTxId
        }
        return AccountingResult.Success(txId, "تم تأكيد الحوالة وتسجيلها كدفعة رسمية")
    }

    suspend fun cancelTransfer(transferId: Long): AccountingResult<Unit> {
        val transfer = transferDao.getById(transferId)
            ?: return AccountingResult.Error("الحوالة غير موجودة.")
        if (transfer.status == TransferStatus.CONFIRMED) {
            return AccountingResult.Error("الحوالة مؤكدة ومرتبطة بدفعة؛ قم بعكس الدفعة من سجل العمليات بدلاً من إلغاء الحوالة.")
        }
        val now = System.currentTimeMillis()
        database.withTransaction {
            transferDao.update(transfer.copy(status = TransferStatus.CANCELLED))
            auditLogDao.insert(
                AuditLogEntity(
                    actionType = "CANCEL_TRANSFER",
                    entityType = "TRANSFER",
                    entityId = transferId,
                    summary = "إلغاء حوالة مرجع ${transfer.referenceNumber}",
                    createdAt = now,
                )
            )
        }
        return AccountingResult.Success(Unit, "تم إلغاء الحوالة")
    }

    suspend fun addReminder(
        customerId: Long,
        title: String,
        dueDate: Long,
        amountDue: Double,
        notes: String,
    ): AccountingResult<Long> {
        if (title.trim().isBlank()) return AccountingResult.Error("عنوان التذكير مطلوب.")
        if (amountDue < 0.0) return AccountingResult.Error("المبلغ لا يمكن أن يكون سالباً.")
        val customer = customerDao.getCustomerById(customerId)
            ?: return AccountingResult.Error("العميل غير موجود.")
        val now = System.currentTimeMillis()
        val initialStatus = if (dueDate < now) ReminderStatus.OVERDUE else ReminderStatus.PENDING
        val id = reminderDao.insert(
            ReminderEntity(
                customerId = customerId,
                title = title.trim(),
                dueDate = dueDate,
                amountDue = amountDue,
                status = initialStatus,
                notes = notes.trim(),
                createdAt = now,
            )
        )
        auditLogDao.insert(
            AuditLogEntity(
                actionType = "ADD_REMINDER",
                entityType = "REMINDER",
                entityId = id,
                summary = "إضافة تذكير للعميل ${customer.name}: ${title.trim()}",
                createdAt = now,
            )
        )
        return AccountingResult.Success(id, "تمت إضافة التذكير")
    }

    suspend fun updateReminderStatus(reminderId: Long, status: ReminderStatus) {
        val reminder = reminderDao.getById(reminderId) ?: return
        reminderDao.update(reminder.copy(status = status))
    }

    suspend fun snoozeReminder(reminderId: Long, extraDays: Int = 3) {
        val reminder = reminderDao.getById(reminderId) ?: return
        val newDue = System.currentTimeMillis() + extraDays * 86_400_000L
        reminderDao.update(reminder.copy(dueDate = newDue, status = ReminderStatus.SNOOZED))
    }

    suspend fun deleteReminder(reminderId: Long) {
        reminderDao.deleteById(reminderId)
    }

    suspend fun refreshOverdueStatuses(): Int {
        return reminderDao.markDueAsOverdue(System.currentTimeMillis())
    }

    suspend fun logSmsIntentOpened(
        customerId: Long,
        customerName: String,
        phone: String,
        messageBody: String,
        templateName: String,
    ): Long {
        return messageLogDao.insert(
            MessageLogEntity(
                customerId = customerId,
                customerName = customerName,
                phone = phone,
                messageBody = messageBody,
                templateName = templateName,
                status = MessageStatus.INTENT_OPENED,
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun logDirectSmsDispatch(
        customerId: Long,
        customerName: String,
        phone: String,
        messageBody: String,
        templateName: String,
        status: MessageStatus,
        details: String = "",
    ): Long {
        val now = System.currentTimeMillis()
        val id = messageLogDao.insert(
            MessageLogEntity(
                customerId = customerId,
                customerName = customerName,
                phone = phone,
                messageBody = messageBody,
                templateName = templateName,
                status = status,
                createdAt = now,
            )
        )
        auditLogDao.insert(
            AuditLogEntity(
                actionType = if (status == MessageStatus.SENT_VIA_SMS_MANAGER) "SMS_DIRECT_SENT" else "SMS_DIRECT_FAILED",
                entityType = "MESSAGE_LOG",
                entityId = id,
                summary = "${status.labelAr}: $customerName ($phone)",
                details = details,
                createdAt = now,
            )
        )
        return id
    }

    suspend fun updateMessageStatus(logId: Long, status: MessageStatus) {
        messageLogDao.updateStatus(logId, status)
        auditLogDao.insert(
            AuditLogEntity(
                actionType = "SMS_STATUS_UPDATE",
                entityType = "MESSAGE_LOG",
                entityId = logId,
                summary = "تحديث حالة الرسالة إلى: ${status.labelAr}",
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun saveSmsTemplate(id: Long?, name: String, body: String): AccountingResult<Long> {
        if (name.trim().isBlank() || body.trim().isBlank()) {
            return AccountingResult.Error("اسم القالب ونص الرسالة مطلوبان.")
        }
        val savedId = if (id == null || id == 0L) {
            smsTemplateDao.insert(
                SmsTemplateEntity(
                    name = name.trim(),
                    body = body.trim(),
                    isDefault = false,
                    createdAt = System.currentTimeMillis(),
                )
            )
        } else {
            val existing = smsTemplateDao.getAllTemplatesOnce().find { it.id == id }
            if (existing != null) {
                smsTemplateDao.update(existing.copy(name = name.trim(), body = body.trim()))
                id
            } else {
                smsTemplateDao.insert(
                    SmsTemplateEntity(
                        name = name.trim(),
                        body = body.trim(),
                        isDefault = false,
                    )
                )
            }
        }
        return AccountingResult.Success(savedId, "تم حفظ قالب الرسالة")
    }

    suspend fun deleteSmsTemplate(id: Long) {
        smsTemplateDao.deleteById(id)
    }

    fun observeAllCustomers(): Flow<List<CustomerEntity>> =
        customerDao.observeAllCustomers()

    fun observeDebtsForCustomer(customerId: Long): Flow<List<DebtsEntity>> =
        debtsDao.observeDebtsForCustomer(customerId)

    suspend fun updateDebtRecord(
        debtId: Long,
        amount: Double,
        description: String,
        dueDate: Long? = null,
    ): AccountingResult<Unit> {
        if (amount.isNaN() || amount.isInfinite() || amount <= 0.0) {
            return AccountingResult.Error("مبلغ الدين غير صالح.")
        }
        val existing = debtsDao.getDebtById(debtId)
            ?: return AccountingResult.Error("سجل الدين غير موجود.")
        val cleanDesc = description.trim().ifBlank { existing.description }
        val updated = existing.copy(
            amount = amount,
            description = cleanDesc,
            dueDate = dueDate,
        )
        database.withTransaction {
            debtsDao.updateDebt(updated)
            auditLogDao.insert(
                AuditLogEntity(
                    actionType = "UPDATE_DEBT",
                    entityType = "DEBT",
                    entityId = debtId,
                    summary = "تعديل سجل دين رقم $debtId بقيمة ${FormatUtils.formatAmount(amount)}",
                    details = cleanDesc,
                    createdAt = System.currentTimeMillis(),
                )
            )
        }
        return AccountingResult.Success(Unit, "تم تحديث سجل الدين")
    }

    suspend fun deleteDebtRecord(debtId: Long): AccountingResult<Unit> {
        val existing = debtsDao.getDebtById(debtId)
            ?: return AccountingResult.Error("سجل الدين غير موجود.")
        database.withTransaction {
            debtsDao.deleteDebt(existing)
            auditLogDao.insert(
                AuditLogEntity(
                    actionType = "DELETE_DEBT",
                    entityType = "DEBT",
                    entityId = debtId,
                    summary = "حذف سجل دين رقم $debtId",
                    details = existing.description,
                    createdAt = System.currentTimeMillis(),
                )
            )
        }
        return AccountingResult.Success(Unit, "تم حذف سجل الدين")
    }

    suspend fun deleteCustomerSafely(customerId: Long): AccountingResult<Unit> {
        val existing = customerDao.getCustomerById(customerId)
            ?: return AccountingResult.Error("العميل غير موجود.")
        val linkedTx = transactionDao.getAllTransactionsOnce().any { it.customerId == customerId }
        val linkedDebts = debtsDao.getAllDebtsOnce().any { it.customerId == customerId }
        val linkedPayments = paymentsDao.getAllPaymentsOnce().any { it.customerId == customerId }
        if (linkedTx || linkedDebts || linkedPayments) {
            return AccountingResult.Error(
                "لا يمكن حذف عميل مرتبط بعمليات مالية لحماية السجل المحاسبي؛ يمكنك أرشفة العميل بدلاً من الحذف."
            )
        }
        database.withTransaction {
            customerDao.delete(existing)
            auditLogDao.insert(
                AuditLogEntity(
                    actionType = "DELETE_CUSTOMER",
                    entityType = "CUSTOMER",
                    entityId = customerId,
                    summary = "حذف العميل: ${existing.name}",
                    createdAt = System.currentTimeMillis(),
                )
            )
        }
        return AccountingResult.Success(Unit, "تم حذف العميل")
    }
}
