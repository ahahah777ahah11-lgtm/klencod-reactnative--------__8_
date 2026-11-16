package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.CustomerWithBalance
import com.example.data.local.TransactionType
import com.example.data.sms.DirectSmsSender
import com.example.ui.SmartDebtViewModel
import com.example.ui.components.AmountOutlinedField
import com.example.ui.components.ArabicTopBar
import com.example.ui.components.CollectionDonutChart
import com.example.ui.components.CustomerSelectorField
import com.example.ui.components.DebtVsPaymentBarChart
import com.example.ui.components.EmptyStateView
import com.example.ui.components.FinancialCard
import com.example.ui.components.KpiTile
import com.example.ui.components.StatusBadge
import com.example.ui.navigation.AppRoutes
import com.example.ui.theme.LocalFinancialColors
import com.example.util.FormatUtils

private enum class DueDatePreset(val labelAr: String, val offsetDays: Int?) {
    NONE("بدون استحقاق", null),
    DUE_TODAY("مستحق اليوم", 0),
    IN_7_DAYS("+7 أيام", 7),
    IN_30_DAYS("+30 يوماً", 30),
}

// شاشة موحدة لإضافة معاملة (دين / دفعة / رصيد افتتاحي / تسوية)
@Composable
fun AddTransactionScreen(
    preselectedCustomerId: Long?,
    initialTypeName: String?,
    viewModel: SmartDebtViewModel,
    canNavigateBack: Boolean = false,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onSaved: () -> Unit,
) {
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val financial = LocalFinancialColors.current
    val cur = settings.currencySymbol

    val initialType = remember(initialTypeName) {
        runCatching { TransactionType.valueOf(initialTypeName.orEmpty()) }
            .getOrDefault(TransactionType.DEBT)
    }

    var selectedCustomer by remember { mutableStateOf<CustomerWithBalance?>(null) }
    var selectedType by remember(initialType) { mutableStateOf(initialType) }
    var amountText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var referenceNumber by remember { mutableStateOf("") }
    var selectedDuePreset by remember { mutableStateOf(DueDatePreset.NONE) }

    LaunchedEffect(customers, preselectedCustomerId) {
        if (selectedCustomer == null && preselectedCustomerId != null) {
            selectedCustomer = customers.find { it.id == preselectedCustomerId }
        }
    }

    Scaffold(
        topBar = {
            ArabicTopBar(
                title = "إضافة معاملة مالية",
                subtitle = "تسجيل دين أو دفعة سداد مباشرة في Room",
                canNavigateBack = canNavigateBack,
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FinancialCard {
                Text(
                    text = "نوع المعاملة",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(TransactionType.entries) { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type.labelAr) },
                            modifier = Modifier.testTag("tx_type_chip_${type.name}"),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                CustomerSelectorField(
                    customers = customers,
                    selectedCustomer = selectedCustomer,
                    currency = cur,
                    onSelect = { selectedCustomer = it },
                )

                val cust = selectedCustomer
                if (cust != null && !selectedType.isDebit && cust.balance > 0.0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "الرصيد المستحق: ${FormatUtils.formatAmount(cust.balance, cur)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        TextButton(
                            onClick = {
                                amountText = if (cust.balance % 1.0 == 0.0) {
                                    cust.balance.toLong().toString()
                                } else {
                                    cust.balance.toString()
                                }
                            },
                        ) {
                            Text("سداد كامل")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                AmountOutlinedField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = "المبلغ *",
                    currency = cur,
                    testTag = "add_transaction_amount_input",
                )

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("البيان / الوصف") },
                    placeholder = { Text("مثال: فاتورة آجل أو سند قبض") },
                    minLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_transaction_description_input"),
                )

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = referenceNumber,
                    onValueChange = { referenceNumber = it },
                    label = { Text("رقم المرجع / الفاتورة (اختياري)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_transaction_reference_input"),
                )

                if (selectedType.isDebit) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "موعد استحقاق الدين (للتذكير التلقائي عبر SMS)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(DueDatePreset.entries) { preset ->
                            FilterChip(
                                selected = selectedDuePreset == preset,
                                onClick = { selectedDuePreset = preset },
                                label = { Text(preset.labelAr) },
                                modifier = Modifier.testTag("due_preset_${preset.name}"),
                            )
                        }
                    }
                }

                val parsedAmount = FormatUtils.parsePositiveAmount(amountText)
                if (cust != null && parsedAmount != null && parsedAmount > 0.0) {
                    val nextBalance = if (selectedType.isDebit) {
                        cust.balance + parsedAmount
                    } else {
                        cust.balance - parsedAmount
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Text("معاينة الرصيد بعد المعاملة:", style = MaterialTheme.typography.labelMedium)
                            Text(
                                text = "${FormatUtils.formatAmount(cust.balance, cur)} ⟵ ${FormatUtils.formatAmount(nextBalance, cur)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedType.isDebit) financial.danger else financial.success,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        val target = selectedCustomer
                        if (target == null) {
                            viewModel.emitFeedback("يرجى اختيار العميل أولاً.", isError = true)
                            return@Button
                        }
                        val amount = FormatUtils.parsePositiveAmount(amountText) ?: 0.0
                        val now = System.currentTimeMillis()
                        val dueDate = if (selectedType.isDebit) {
                            selectedDuePreset.offsetDays?.let { days ->
                                now + (days * 86_400_000L)
                            }
                        } else {
                            null
                        }
                        val desc = description.ifBlank { selectedType.labelAr }
                        viewModel.recordTransaction(
                            customerId = target.id,
                            type = selectedType,
                            amount = amount,
                            description = desc,
                            dateMillis = now,
                            referenceNumber = referenceNumber.takeIf { it.isNotBlank() },
                            dueDate = dueDate,
                            onSuccess = {
                                amountText = ""
                                description = ""
                                referenceNumber = ""
                                onSaved()
                            },
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedType.isDebit) financial.danger else financial.success,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("submit_transaction_button"),
                ) {
                    Icon(
                        if (selectedType.isDebit) Icons.Default.AddCard else Icons.Default.Payments,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("حفظ ${selectedType.labelAr}")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onNavigate(AppRoutes.CUSTOMERS) },
                    modifier = Modifier.weight(1f).testTag("tx_to_customers_btn"),
                ) {
                    Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("قائمة العملاء")
                }
                OutlinedButton(
                    onClick = { onNavigate(AppRoutes.DEBTS_OVERVIEW) },
                    modifier = Modifier.weight(1f).testTag("tx_to_debts_overview_btn"),
                ) {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("نظرة الديون")
                }
            }
        }
    }
}

private enum class DebtsOverviewFilter(val labelAr: String) {
    ALL("الكل"),
    DUE_NOW("مستحقة / متأخرة"),
    DEBTORS_ONLY("المدينون"),
    RAW_DEBTS("سجل جدول الديون"),
}

// شاشة نظرة عامة على الديون والمدفوعات والتذكير التلقائي
@Composable
fun DebtsOverviewScreen(
    viewModel: SmartDebtViewModel,
    canNavigateBack: Boolean = false,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val context = LocalContext.current
    val summary by viewModel.dashboardSummary.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val debts by viewModel.debts.collectAsStateWithLifecycle()
    val payments by viewModel.payments.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val financial = LocalFinancialColors.current
    val cur = settings.currencySymbol

    var filter by remember { mutableStateOf(DebtsOverviewFilter.ALL) }
    var hasSmsPermission by remember { mutableStateOf(DirectSmsSender.hasSendSmsPermission(context)) }
    var lastAutoDispatchCount by remember { mutableIntStateOf(-1) }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasSmsPermission = granted
        if (granted) {
            viewModel.sendAutomaticDueDebtReminders(context) { report ->
                lastAutoDispatchCount = report.sentCount
            }
        } else {
            viewModel.emitFeedback("يرجى منح صلاحية SEND_SMS لإرسال التذكيرات التلقائية.", isError = true)
        }
    }

    val now = System.currentTimeMillis()
    val activeCustomers = remember(customers) { customers.filter { !it.isArchived } }
    val customersById = remember(customers) { customers.associateBy { it.id } }

    val dueCustomers = remember(activeCustomers, debts, reminders, settings) {
        DirectSmsSender.findDueDebtCustomers(
            customers = activeCustomers,
            debts = debts,
            reminders = reminders,
            settings = settings,
            nowMillis = now,
        )
    }
    val dueIds = remember(dueCustomers) { dueCustomers.map { it.id }.toSet() }

    val visibleCustomers = remember(activeCustomers, dueIds, filter) {
        when (filter) {
            DebtsOverviewFilter.ALL -> activeCustomers.filter { it.totalDebts > 0.0 || it.balance > 0.0 }
            DebtsOverviewFilter.DUE_NOW -> activeCustomers.filter { it.id in dueIds }
            DebtsOverviewFilter.DEBTORS_ONLY -> activeCustomers.filter { it.balance > 0.001 }
            DebtsOverviewFilter.RAW_DEBTS -> emptyList()
        }
    }

    Scaffold(
        topBar = {
            ArabicTopBar(
                title = "نظرة عامة على الديون",
                subtitle = "متابعة المديونيات والتحصيل والديون المستحقة",
                canNavigateBack = canNavigateBack,
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .testTag("debts_overview_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // KPI Row 1
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    KpiTile(
                        title = "إجمالي الديون (${debts.size})",
                        value = FormatUtils.formatAmount(summary.totalDebts, cur),
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        accentColor = financial.danger,
                        modifier = Modifier.weight(1f).testTag("overview_total_debts"),
                    )
                    KpiTile(
                        title = "إجمالي المدفوعات (${payments.size})",
                        value = FormatUtils.formatAmount(summary.totalPayments, cur),
                        icon = Icons.AutoMirrored.Filled.TrendingDown,
                        accentColor = financial.success,
                        modifier = Modifier.weight(1f).testTag("overview_total_payments"),
                    )
                }
            }

            // Donut & Bar comparison card
            item {
                FinancialCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "نسبة التحصيل مقابل الرصيد المتبقي",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        StatusBadge(
                            text = FormatUtils.formatAmount(summary.netOutstandingBalance, cur),
                            color = financial.danger,
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        CollectionDonutChart(
                            totalDebts = summary.totalDebts,
                            totalPayments = summary.totalPayments,
                        )
                        DebtVsPaymentBarChart(
                            totalDebts = summary.totalDebts,
                            totalPayments = summary.totalPayments,
                            netBalance = summary.netOutstandingBalance,
                            currency = cur,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // Automatic Due-Debt SMS Reminder Card
            item {
                FinancialCard(modifier = Modifier.testTag("auto_due_sms_card")) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = if (dueCustomers.isNotEmpty()) financial.danger else financial.success,
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "تذكير تلقائي للديون المستحقة (${dueCustomers.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = if (dueCustomers.isEmpty()) {
                                        "لا توجد ديون متأخرة أو مستحقة حالياً"
                                    } else {
                                        "عملاء لديهم ديون مستحقة الآن جاهزون للتنبيه عبر SMS"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        StatusBadge(
                            text = if (hasSmsPermission) "SMS جاهز" else "يتطلب صلاحية",
                            color = if (hasSmsPermission) financial.success else financial.orange,
                        )
                    }
                    if (lastAutoDispatchCount >= 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "آخر تشغيل: تم إرسال $lastAutoDispatchCount رسالة تذكير",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (DirectSmsSender.hasSendSmsPermission(context)) {
                                hasSmsPermission = true
                                viewModel.sendAutomaticDueDebtReminders(context) { report ->
                                    lastAutoDispatchCount = report.sentCount
                                }
                            } else {
                                smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("send_auto_due_sms_button"),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("إرسال رسائل التذكير التلقائية بالديون المستحقة")
                    }
                }
            }

            // Quick switch action strip
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { onNavigate(AppRoutes.CUSTOMERS) },
                        modifier = Modifier.weight(1f).testTag("overview_to_customers_btn"),
                    ) {
                        Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("العملاء")
                    }
                    Button(
                        onClick = { onNavigate(AppRoutes.addTransaction()) },
                        modifier = Modifier.weight(1f).testTag("overview_to_add_tx_btn"),
                    ) {
                        Icon(Icons.Default.AddCard, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("إضافة معاملة")
                    }
                }
            }

            // Filter Chips
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(DebtsOverviewFilter.entries) { item ->
                        FilterChip(
                            selected = filter == item,
                            onClick = { filter = item },
                            label = { Text(item.labelAr) },
                            modifier = Modifier.testTag("overview_filter_${item.name}"),
                        )
                    }
                }
            }

            if (filter == DebtsOverviewFilter.RAW_DEBTS) {
                if (debts.isEmpty()) {
                    item {
                        EmptyStateView(
                            icon = Icons.AutoMirrored.Filled.ReceiptLong,
                            title = "لا توجد قيود ديون في الجدول",
                            subtitle = "ابدأ بإضافة معاملة دين لتظهر تفاصيلها هنا.",
                        )
                    }
                } else {
                    items(debts, key = { it.id }) { debt ->
                        val owner = customersById[debt.customerId]
                        val isOverdue = debt.dueDate != null && debt.dueDate <= now
                        FinancialCard(
                            modifier = Modifier.clickable {
                                if (owner != null) {
                                    onNavigate(AppRoutes.customerDetail(owner.id))
                                }
                            },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(owner?.name ?: "عميل #${debt.customerId}", fontWeight = FontWeight.Bold)
                                    Text(
                                        debt.description.ifBlank { "قيد دين" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    debt.dueDate?.let { due ->
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Schedule,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = if (isOverdue) financial.danger else financial.orange,
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = "الاستحقاق: ${FormatUtils.formatDate(due)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isOverdue) financial.danger else financial.orange,
                                            )
                                        }
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = FormatUtils.formatAmount(debt.amount, cur),
                                        fontWeight = FontWeight.Bold,
                                        color = financial.danger,
                                    )
                                    if (isOverdue) {
                                        StatusBadge("مستحق", financial.danger)
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (visibleCustomers.isEmpty()) {
                item {
                    EmptyStateView(
                        icon = Icons.Default.CheckCircle,
                        title = "لا توجد مديونيات مطابقة",
                        subtitle = "جميع الحسابات مسددة أو لا تطابق الفلتر الحالي.",
                    )
                }
            } else {
                items(visibleCustomers, key = { it.id }) { cust ->
                    val isDue = cust.id in dueIds
                    FinancialCard(
                        modifier = Modifier
                            .clickable { onNavigate(AppRoutes.customerDetail(cust.id)) }
                            .testTag("overview_customer_card_${cust.id}"),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(cust.name, fontWeight = FontWeight.Bold)
                                    if (isDue) {
                                        StatusBadge("مستحق التذكير", financial.danger)
                                    }
                                }
                                Text(
                                    text = "إجمالي الدين: ${FormatUtils.formatAmount(cust.totalDebts, cur)} • المدفوع: ${FormatUtils.formatAmount(cust.totalPayments, cur)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = FormatUtils.formatAmount(cust.balance, cur),
                                fontWeight = FontWeight.Bold,
                                color = if (cust.balance > 0.001) financial.danger else financial.success,
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onNavigate(
                                        AppRoutes.addTransaction(
                                            customerId = cust.id,
                                            initialType = TransactionType.PAYMENT.name,
                                        )
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("تسجيل دفعة")
                            }
                            OutlinedButton(
                                onClick = { onNavigate(AppRoutes.smsCenter(cust.id)) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("رسالة")
                            }
                        }
                    }
                }
            }
        }
    }
}
