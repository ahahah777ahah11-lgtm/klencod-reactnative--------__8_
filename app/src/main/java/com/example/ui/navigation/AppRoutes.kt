package com.example.ui.navigation

object AppRoutes {
    const val DASHBOARD = "dashboard"
    const val CUSTOMERS = "customers"
    const val ADD_EDIT_CUSTOMER = "add_edit_customer?customerId={customerId}"
    const val CUSTOMER_DETAIL = "customer_detail/{customerId}"
    const val ACCOUNT_STATEMENT = "account_statement/{customerId}"
    const val ADD_DEBT = "add_debt?customerId={customerId}"
    const val ADD_PAYMENT = "add_payment?customerId={customerId}"
    const val ADD_TRANSACTION = "add_transaction?customerId={customerId}&initialType={initialType}"
    const val DEBTS_OVERVIEW = "debts_overview"
    const val TRANSACTIONS_LOG = "transactions_log"
    const val REMINDERS = "reminders"
    const val TRANSFERS = "transfers"
    const val SMS_CENTER = "sms_center?customerId={customerId}"
    const val SMS_TEMPLATES = "sms_templates"
    const val REPORTS = "reports"
    const val IMPORT_DATA = "import_data"
    const val EXPORT_DATA = "export_data"
    const val BACKUP_RESTORE = "backup_restore"
    const val SETTINGS = "settings"
    const val LOCAL_ASSISTANT = "local_assistant"
    const val MORE_HUB = "more_hub"

    fun addEditCustomer(customerId: Long? = null): String =
        if (customerId != null && customerId > 0L) "add_edit_customer?customerId=$customerId"
        else "add_edit_customer"

    fun customerDetail(customerId: Long): String = "customer_detail/$customerId"
    fun accountStatement(customerId: Long): String = "account_statement/$customerId"

    fun addDebt(customerId: Long? = null): String =
        if (customerId != null && customerId > 0L) "add_debt?customerId=$customerId"
        else "add_debt"

    fun addPayment(customerId: Long? = null): String =
        if (customerId != null && customerId > 0L) "add_payment?customerId=$customerId"
        else "add_payment"

    fun addTransaction(customerId: Long? = null, initialType: String = "DEBT"): String =
        if (customerId != null && customerId > 0L) "add_transaction?customerId=$customerId&initialType=$initialType"
        else "add_transaction?initialType=$initialType"

    fun smsCenter(customerId: Long? = null): String =
        if (customerId != null && customerId > 0L) "sms_center?customerId=$customerId"
        else "sms_center"
}
