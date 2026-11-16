package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.CustomerWithBalance
import com.example.data.local.ReminderStatus
import com.example.data.local.TransferCompany
import com.example.data.local.TransferStatus
import com.example.data.local.TransferWithCustomer
import com.example.ui.SmartDebtViewModel
import com.example.ui.components.AdvancedSearchFilterBar
import com.example.ui.components.AmountOutlinedField
import com.example.ui.components.ArabicTopBar
import com.example.ui.components.ConfirmSensitiveActionDialog
import com.example.ui.components.CustomerSelectorField
import com.example.ui.components.DateRangePreset
import com.example.ui.components.EmptyStateView
import com.example.ui.components.FinancialCard
import com.example.ui.components.StatusBadge
import com.example.ui.components.matchesDateRange
import com.example.ui.navigation.AppRoutes
import com.example.ui.theme.LocalFinancialColors
import com.example.util.FormatUtils

// 9. التذكيرات والمتأخرات
@Composable
fun RemindersScreen(
    viewModel: SmartDebtViewModel,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val financial = LocalFinancialColors.current
    val cur = settings.currencySymbol

    var tabIndex by remember { mutableIntStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<ReminderStatus?>(null) }
    var selectedDatePreset by remember { mutableStateOf(DateRangePreset.ALL) }
    var customStartMillis by remember { mutableStateOf<Long?>(null) }
    var customEndMillis by remember { mutableStateOf<Long?>(null) }

    val now = System.currentTimeMillis()
    val leadCutoff = now + com.example.worker.OverdueReminderWorker.DEFAULT_LEAD_HOURS * 3_600_000L

    val activeFilterCount = remember(statusFilter, selectedDatePreset) {
        var c = 0
        if (statusFilter != null) c++
        if (selectedDatePreset != DateRangePreset.ALL) c++
        c
    }

    val filteredReminders = remember(reminders, searchQuery, statusFilter, selectedDatePreset, customStartMillis, customEndMillis, now) {
        val q = FormatUtils.normalizeArabicText(searchQuery)
        reminders.filter { r ->
            val matchesQuery = q.isBlank() ||
                FormatUtils.normalizeArabicText(r.customerName).contains(q) ||
                FormatUtils.normalizeArabicText(r.title).contains(q) ||
                r.customerPhone.contains(q)
            val isLate = r.status == ReminderStatus.OVERDUE || (r.status == ReminderStatus.PENDING && r.dueDate < now)
            val matchesStatus = when (statusFilter) {
                null -> true
                ReminderStatus.OVERDUE -> isLate
                else -> r.status == statusFilter
            }
            val matchesDue = matchesDateRange(
                timestampMillis = r.dueDate,
                preset = selectedDatePreset,
                customStartMillis = customStartMillis,
                customEndMillis = customEndMillis,
                nowMillis = now,
            )
            matchesQuery && matchesStatus && matchesDue
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        viewModel.triggerDueDateCheckNow()
    }

    val overdueCustomers = remember(customers, settings.overdueDaysThreshold) {
        val thresholdMillis = settings.overdueDaysThreshold * 86_400_000L
        customers.filter { c ->
            !c.isArchived && c.balance > 0.001 && (
                c.overdueRemindersCount > 0 ||
                    (c.lastTransactionDate != null && now - c.lastTransactionDate > thresholdMillis)
                )
        }
    }

    Scaffold(
        topBar = {
            ArabicTopBar(title = "التذكيرات والمتأخرات", onBack = onBack)
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.testTag("fab_add_reminder"),
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة تذكير")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            TabRow(selectedTabIndex = tabIndex) {
                Tab(
                    selected = tabIndex == 0,
                    onClick = { tabIndex = 0 },
                    text = { Text("التذكيرات (${reminders.size})") },
                )
                Tab(
                    selected = tabIndex == 1,
                    onClick = { tabIndex = 1 },
                    text = { Text("المتأخرون (${overdueCustomers.size})") },
                )
            }

            // WorkManager background due-date monitor card
            FinancialCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("مراقبة مواعيد الاستحقاق (WorkManager)", fontWeight = FontWeight.Bold)
                        Text(
                            if (settings.workManagerRemindersEnabled)
                                "مفعّل: يفحص المواعيد كل 12 ساعة ويرسل إشعاراً محلياً قبل الاستحقاق بـ 48 ساعة"
                            else
                                "غير مفعّل تلقائياً — يمكنك الفحص الفوري أو التفعيل من الإعدادات",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.triggerDueDateCheckNow()
                            }
                        },
                        modifier = Modifier.testTag("run_workmanager_due_check_button"),
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("فحص الآن")
                    }
                }
            }

            if (tabIndex == 0) {
                AdvancedSearchFilterBar(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    searchPlaceholder = "ابحث باسم العميل أو عنوان الدين...",
                    searchTestTag = "reminders_search_input",
                    datePresets = listOf(
                        DateRangePreset.ALL,
                        DateRangePreset.OVERDUE,
                        DateRangePreset.TODAY,
                        DateRangePreset.NEXT_7_DAYS,
                        DateRangePreset.NEXT_30_DAYS,
                        DateRangePreset.CUSTOM,
                    ),
                    selectedDatePreset = selectedDatePreset,
                    onDatePresetChange = { selectedDatePreset = it },
                    customStartMillis = customStartMillis,
                    customEndMillis = customEndMillis,
                    onCustomRangeChange = { start, end ->
                        customStartMillis = start
                        customEndMillis = end
                    },
                    activeAdvancedFilterCount = activeFilterCount,
                    onResetAll = {
                        searchQuery = ""
                        statusFilter = null
                        selectedDatePreset = DateRangePreset.ALL
                        customStartMillis = null
                        customEndMillis = null
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                    primaryStatusChips = {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = statusFilter == null,
                                    onClick = { statusFilter = null },
                                    label = { Text("الكل") },
                                )
                            }
                            items(ReminderStatus.entries) { st ->
                                FilterChip(
                                    selected = statusFilter == st,
                                    onClick = { statusFilter = st },
                                    label = { Text(st.labelAr) },
                                )
                            }
                        }
                    },
                )
                if (filteredReminders.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Default.NotificationsActive,
                        title = "لا توجد تذكيرات مطابقة",
                        subtitle = "أضف موعد سداد لأي عميل أو غيّر فلاتر الحالة وتاريخ الاستحقاق.",
                        actionLabel = "إضافة تذكير",
                        onAction = { showAddDialog = true },
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(filteredReminders, key = { it.id }) { r ->
                            val isLate = r.status == ReminderStatus.OVERDUE ||
                                (r.status == ReminderStatus.PENDING && r.dueDate < now)
                            val isUpcomingSoon = !isLate &&
                                r.status != ReminderStatus.COMPLETED &&
                                r.dueDate in now..leadCutoff
                            val badgeText = when {
                                isLate -> "متأخر"
                                isUpcomingSoon -> "يستحق خلال 48 ساعة"
                                else -> r.status.labelAr
                            }
                            val badgeColor = when {
                                r.status == ReminderStatus.COMPLETED -> financial.success
                                isLate -> financial.danger
                                isUpcomingSoon -> financial.warning
                                r.status == ReminderStatus.SNOOZED -> financial.orange
                                else -> MaterialTheme.colorScheme.secondary
                            }
                            FinancialCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    StatusBadge(badgeText, badgeColor)
                                    Text(
                                        FormatUtils.formatDate(r.dueDate),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(r.customerName, fontWeight = FontWeight.Bold)
                                Text(r.title, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "الرصيد الحالي: ${FormatUtils.formatAmount(r.customerBalance, cur)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = financial.danger,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (r.status != ReminderStatus.COMPLETED) {
                                        OutlinedButton(onClick = { viewModel.updateReminderStatus(r.id, ReminderStatus.COMPLETED) }) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("تم")
                                        }
                                        OutlinedButton(onClick = { viewModel.snoozeReminder(r.id, 3) }) {
                                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("تأجيل 3 أيام")
                                        }
                                    }
                                    IconButton(onClick = { onNavigate(AppRoutes.smsCenter(r.customerId)) }) {
                                        Icon(Icons.Default.Sms, contentDescription = "إرسال رسالة تذكير")
                                    }
                                    IconButton(onClick = { viewModel.deleteReminder(r.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف التذكير", tint = financial.danger)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                if (overdueCustomers.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Default.CheckCircle,
                        title = "لا يوجد عملاء متأخرون",
                        subtitle = "جميع أرصدة العملاء ضمن المهلة المحددة (${settings.overdueDaysThreshold} يوماً).",
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(overdueCustomers, key = { it.id }) { c ->
                            FinancialCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(c.name, fontWeight = FontWeight.Bold)
                                        Text(
                                            "الرصيد: ${FormatUtils.formatAmount(c.balance, cur)}",
                                            color = financial.danger,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        c.lastTransactionDate?.let {
                                            Text(
                                                "آخر حركة: ${FormatUtils.formatDate(it)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    Button(onClick = { onNavigate(AppRoutes.smsCenter(c.id)) }) {
                                        Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("تذكير")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddReminderDialog(
            customers = customers,
            currency = cur,
            onDismiss = { showAddDialog = false },
            onConfirm = { custId, title, daysFromNow, amount, notes ->
                val dueMillis = System.currentTimeMillis() + daysFromNow * 86_400_000L
                viewModel.addReminder(custId, title, dueMillis, amount, notes) {
                    showAddDialog = false
                }
            },
        )
    }
}

@Composable
private fun AddReminderDialog(
    customers: List<CustomerWithBalance>,
    currency: String,
    onDismiss: () -> Unit,
    onConfirm: (Long, String, Int, Double, String) -> Unit,
) {
    var selectedCustomer by remember { mutableStateOf<CustomerWithBalance?>(null) }
    var title by remember { mutableStateOf("موعد سداد مستحق") }
    var daysText by remember { mutableStateOf("3") }
    var amountText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("جدولة تذكير سداد", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CustomerSelectorField(
                    customers = customers,
                    selectedCustomer = selectedCustomer,
                    currency = currency,
                    onSelect = {
                        selectedCustomer = it
                        if (amountText.isBlank() && it.balance > 0) {
                            amountText = it.balance.toString()
                        }
                    },
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان التذكير *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = daysText,
                    onValueChange = { daysText = it.filter { ch -> ch.isDigit() || ch == '-' } },
                    label = { Text("بعد كم يوم (مثال: 3، أو 0 لليوم)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                AmountOutlinedField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = "المبلغ المتوقع سداده",
                    currency = currency,
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cust = selectedCustomer ?: return@TextButton
                    val days = daysText.toIntOrNull() ?: 3
                    val amt = FormatUtils.parsePositiveAmount(amountText) ?: cust.balance.coerceAtLeast(0.0)
                    onConfirm(cust.id, title, days, amt, notes)
                },
            ) {
                Text("حفظ التذكير", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        },
    )
}

// 10. الحوالات الواردة
@Composable
fun TransfersScreen(
    viewModel: SmartDebtViewModel,
    onBack: () -> Unit,
) {
    val transfers by viewModel.transfers.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val financial = LocalFinancialColors.current
    val cur = settings.currencySymbol

    var selectedCompanyFilter by remember { mutableStateOf<TransferCompany?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var confirmTransferTarget by remember { mutableStateOf<TransferWithCustomer?>(null) }
    var confirmCustomer by remember { mutableStateOf<CustomerWithBalance?>(null) }
    var cancelTarget by remember { mutableStateOf<TransferWithCustomer?>(null) }

    val filtered = transfers.filter { selectedCompanyFilter == null || it.company == selectedCompanyFilter }

    Scaffold(
        topBar = {
            ArabicTopBar(title = "الحوالات الواردة", onBack = onBack)
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.testTag("fab_add_transfer"),
            ) {
                Icon(Icons.Default.Add, contentDescription = "تسجيل حوالة واردة")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = selectedCompanyFilter == null,
                        onClick = { selectedCompanyFilter = null },
                        label = { Text("جميع الشركات") },
                    )
                }
                items(TransferCompany.entries) { comp ->
                    FilterChip(
                        selected = selectedCompanyFilter == comp,
                        onClick = { selectedCompanyFilter = comp },
                        label = { Text(comp.titleAr) },
                    )
                }
            }

            if (filtered.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.CurrencyExchange,
                    title = "لا توجد حوالات واردة",
                    subtitle = "يدعم التطبيق: النجم، الامتياز إكسبرس، داديه، الأكوع موني، وإتش بي مع منع تكرار أرقام المراجع.",
                    actionLabel = "إضافة حوالة",
                    onAction = { showAddDialog = true },
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filtered, key = { it.id }) { tr ->
                        val statusColor = when (tr.status) {
                            TransferStatus.CONFIRMED -> financial.success
                            TransferStatus.PENDING -> financial.orange
                            TransferStatus.CANCELLED -> financial.danger
                        }
                        FinancialCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    StatusBadge(tr.company.titleAr, MaterialTheme.colorScheme.secondary)
                                    StatusBadge(tr.status.labelAr, statusColor)
                                }
                                Text(
                                    FormatUtils.formatDate(tr.transferDate),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("رقم المرجع: ${tr.referenceNumber}", fontWeight = FontWeight.Bold)
                                    Text("المرسل: ${tr.senderName}")
                                    Text(
                                        "العميل: ${tr.customerName ?: "لم يتم الربط بعد"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    FormatUtils.formatAmount(tr.amount, cur),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor,
                                )
                            }
                            if (tr.status == TransferStatus.PENDING) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            confirmTransferTarget = tr
                                            confirmCustomer = customers.find { it.id == tr.customerId }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = financial.success),
                                        modifier = Modifier.weight(1f).testTag("confirm_transfer_${tr.id}"),
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("تأكيد كدفعة")
                                    }
                                    OutlinedButton(
                                        onClick = { cancelTarget = tr },
                                    ) {
                                        Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("إلغاء")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddTransferDialog(
            customers = customers,
            currency = cur,
            onDismiss = { showAddDialog = false },
            onSave = { company, ref, amount, sender, custId, notes ->
                viewModel.addTransfer(
                    company = company,
                    referenceNumber = ref,
                    amount = amount,
                    senderName = sender,
                    transferDate = System.currentTimeMillis(),
                    customerId = custId,
                    notes = notes,
                ) {
                    showAddDialog = false
                }
            },
        )
    }

    confirmTransferTarget?.let { tr ->
        AlertDialog(
            onDismissRequest = { confirmTransferTarget = null },
            title = { Text("تأكيد الحوالة كدفعة", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("شركة الصرافة: ${tr.company.titleAr}")
                    Text("رقم المرجع: ${tr.referenceNumber}")
                    Text("المبلغ الصحيح: ${FormatUtils.formatAmount(tr.amount, cur)}", fontWeight = FontWeight.Bold)
                    CustomerSelectorField(
                        customers = customers,
                        selectedCustomer = confirmCustomer,
                        currency = cur,
                        onSelect = { confirmCustomer = it },
                        label = "اختر العميل لتسجيل الدفعة *",
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val cust = confirmCustomer
                        if (cust == null) {
                            viewModel.emitFeedback("يرجى اختيار العميل قبل تأكيد الحوالة.", isError = true)
                            return@TextButton
                        }
                        viewModel.confirmTransferAsPayment(tr.id, cust.id)
                        confirmTransferTarget = null
                    },
                ) {
                    Text("اعتماد كدفعة", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmTransferTarget = null }) { Text("رجوع") }
            },
        )
    }

    cancelTarget?.let { tr ->
        ConfirmSensitiveActionDialog(
            title = "إلغاء الحوالة",
            message = "هل أنت متأكد من إلغاء الحوالة رقم ${tr.referenceNumber}؟",
            confirmText = "نعم، إلغاء",
            onConfirm = {
                viewModel.cancelTransfer(tr.id)
                cancelTarget = null
            },
            onDismiss = { cancelTarget = null },
        )
    }
}

@Composable
private fun AddTransferDialog(
    customers: List<CustomerWithBalance>,
    currency: String,
    onDismiss: () -> Unit,
    onSave: (TransferCompany, String, Double, String, Long?, String) -> Unit,
) {
    var company by remember { mutableStateOf(TransferCompany.AL_NAJM) }
    var referenceNumber by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var senderName by remember { mutableStateOf("") }
    var selectedCustomer by remember { mutableStateOf<CustomerWithBalance?>(null) }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تسجيل حوالة واردة", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("اختر شركة الصرافة:", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(TransferCompany.entries) { comp ->
                        FilterChip(
                            selected = company == comp,
                            onClick = { company = comp },
                            label = { Text(comp.titleAr) },
                        )
                    }
                }
                OutlinedTextField(
                    value = referenceNumber,
                    onValueChange = { referenceNumber = it },
                    label = { Text("رقم المرجع (مثال: ت/8000011) *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("transfer_ref_input"),
                )
                AmountOutlinedField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = "مبلغ الحوالة *",
                    currency = currency,
                    testTag = "transfer_amount_input",
                )
                OutlinedTextField(
                    value = senderName,
                    onValueChange = { senderName = it },
                    label = { Text("اسم المرسل *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("transfer_sender_input"),
                )
                CustomerSelectorField(
                    customers = customers,
                    selectedCustomer = selectedCustomer,
                    currency = currency,
                    onSelect = { selectedCustomer = it },
                    label = "العميل المستفيد (اختياري الآن)",
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amount = FormatUtils.parsePositiveAmount(amountText) ?: 0.0
                    onSave(company, referenceNumber, amount, senderName, selectedCustomer?.id, notes)
                },
                modifier = Modifier.testTag("save_transfer_button"),
            ) {
                Text("حفظ الحوالة", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        },
    )
}
