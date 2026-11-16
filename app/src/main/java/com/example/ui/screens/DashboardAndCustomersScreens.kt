package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.ai.AiAutoFeatureType
import com.example.data.local.CustomerWithBalance
import com.example.data.local.ReminderStatus
import com.example.data.local.TransactionType
import com.example.data.local.TransactionWithCustomer
import com.example.security.AppPermissionsManager
import com.example.ui.SmartDebtViewModel
import com.example.ui.components.AdvancedSearchFilterBar
import com.example.ui.components.AmountOutlinedField
import com.example.ui.components.ArabicTopBar
import androidx.compose.material3.CircularProgressIndicator
import com.example.data.importexport.PreviewRowStatus
import com.example.ui.components.CollectionDonutChart
import com.example.ui.components.CustomersPortfolioSummaryCard
import com.example.ui.components.DateRangePreset
import com.example.ui.components.DebtVsPaymentBarChart
import com.example.ui.components.DebtVsPaymentColumnChart
import com.example.ui.components.EmptyStateView
import com.example.ui.components.FinancialCard
import com.example.ui.components.KpiTile
import com.example.ui.components.PaymentReceiptDialog
import com.example.ui.components.StatusBadge
import com.example.ui.components.matchesDateRange
import com.example.ui.navigation.AppRoutes
import com.example.ui.theme.LocalAppIconShape
import com.example.ui.theme.LocalFinancialColors
import com.example.util.FormatUtils

// 1. الرئيسية والإحصائيات
@Composable
fun DashboardScreen(
    viewModel: SmartDebtViewModel,
    onNavigate: (String) -> Unit,
) {
    val summary by viewModel.dashboardSummary.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val financial = LocalFinancialColors.current
    val cur = settings.currencySymbol

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Hero Header Card with generated artwork
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(financial.heroStart, financial.heroEnd)
                            )
                        )
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_financial_header_1794526245716),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .alpha(0.28f),
                    )
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = settings.storeName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White.copy(alpha = 0.88f),
                                )
                                Text(
                                    text = "مدير الديون الذكي برو",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (settings.biometricLockEnabled) {
                                    IconButton(
                                        onClick = { viewModel.lockSession() },
                                        modifier = Modifier.testTag("dashboard_lock_button"),
                                    ) {
                                        Icon(
                                            Icons.Default.Lock,
                                            contentDescription = "قفل التطبيق بالبصمة",
                                            tint = Color.White,
                                        )
                                    }
                                }
                                Surface(
                                    color = financial.turquoise.copy(alpha = 0.22f),
                                    shape = RoundedCornerShape(20.dp),
                                ) {
                                    Text(
                                        text = "محلي • دون إنترنت",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "صافي الأرصدة المستحقة (الديون - المدفوعات)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                        Text(
                            text = FormatUtils.formatAmount(summary.netOutstandingBalance, cur),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.testTag("dashboard_net_balance"),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(
                                text = "العملاء: ${summary.totalCustomers}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.9f),
                            )
                            Text(
                                text = "المدينون: ${summary.activeDebtorsCount}",
                                style = MaterialTheme.typography.labelMedium,
                                color = financial.warning,
                            )
                            Text(
                                text = "المسددون: ${summary.settledCustomersCount}",
                                style = MaterialTheme.typography.labelMedium,
                                color = financial.turquoise,
                            )
                        }
                    }
                }
            }
        }

        // KPI Grid Row 1
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                KpiTile(
                    title = "إجمالي الديون",
                    value = FormatUtils.formatAmount(summary.totalDebts, cur),
                    icon = Icons.Default.TrendingUp,
                    accentColor = financial.danger,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(AppRoutes.TRANSACTIONS_LOG) },
                )
                KpiTile(
                    title = "إجمالي المحصل",
                    value = FormatUtils.formatAmount(summary.totalPayments, cur),
                    icon = Icons.Default.TrendingDown,
                    accentColor = financial.success,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(AppRoutes.TRANSACTIONS_LOG) },
                )
            }
        }

        // KPI Grid Row 2
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                KpiTile(
                    title = "حوالات معلقة",
                    value = "${summary.pendingTransfersCount}",
                    subtitle = FormatUtils.formatAmount(summary.pendingTransfersAmount, cur),
                    icon = Icons.Default.CurrencyExchange,
                    accentColor = financial.orange,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(AppRoutes.TRANSFERS) },
                )
                KpiTile(
                    title = "تذكيرات متأخرة",
                    value = "${summary.overdueRemindersCount}",
                    subtitle = "اضغط للمتابعة",
                    icon = Icons.Default.NotificationsActive,
                    accentColor = financial.warning,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(AppRoutes.REMINDERS) },
                )
            }
        }

        // Quick Actions Strip
        item {
            Text(
                text = "إجراءات سريعة",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickActionButton(
                    label = "إضافة دين",
                    icon = Icons.Default.AddCard,
                    color = financial.danger,
                    modifier = Modifier.weight(1f).testTag("quick_add_debt"),
                    onClick = { onNavigate(AppRoutes.addDebt()) },
                )
                QuickActionButton(
                    label = "تسجيل دفعة",
                    icon = Icons.Default.Payments,
                    color = financial.success,
                    modifier = Modifier.weight(1f).testTag("quick_add_payment"),
                    onClick = { onNavigate(AppRoutes.addPayment()) },
                )
                QuickActionButton(
                    label = "عميل جديد",
                    icon = Icons.Default.PersonAdd,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f).testTag("quick_add_customer"),
                    onClick = { onNavigate(AppRoutes.addEditCustomer()) },
                )
                QuickActionButton(
                    label = "استيراد DB",
                    icon = Icons.Default.FileUpload,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f).testTag("quick_import"),
                    onClick = { onNavigate(AppRoutes.IMPORT_DATA) },
                )
            }
        }

        // Analytics Overview Card
        item {
            FinancialCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "تحليل التحصيل والمديونيات",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "التقارير",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .clickable { onNavigate(AppRoutes.REPORTS) }
                            .padding(4.dp),
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
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
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "مقارنة إجمالي الديون المستحقة مقابل المدفوعات المستلمة",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                DebtVsPaymentColumnChart(
                    totalDebts = summary.totalDebts,
                    totalPayments = summary.totalPayments,
                    netBalance = summary.netOutstandingBalance,
                    currency = cur,
                )
            }
        }

        // Top Debtors Quick List
        val topDebtors = customers.filter { !it.isArchived && it.balance > 0.001 }.take(3)
        if (topDebtors.isNotEmpty()) {
            item {
                FinancialCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "أعلى العملاء مديونية",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "إرسال SMS",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.clickable { onNavigate(AppRoutes.smsCenter()) },
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    topDebtors.forEachIndexed { idx, cust ->
                        if (idx > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigate(AppRoutes.customerDetail(cust.id)) }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(cust.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    cust.phone.ifBlank { cust.accountNumber },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                FormatUtils.formatAmount(cust.balance, cur),
                                fontWeight = FontWeight.Bold,
                                color = financial.danger,
                            )
                        }
                    }
                }
            }
        }

        // Recent Transactions
        item {
            FinancialCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "أحدث العمليات",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "عرض السجل",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.clickable { onNavigate(AppRoutes.TRANSACTIONS_LOG) },
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (transactions.isEmpty()) {
                    Text(
                        text = "لا توجد عمليات مسجلة بعد. ابدأ بإضافة عميل أو استيراد قاعدة بيانات.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    transactions.take(5).forEachIndexed { idx, tx ->
                        if (idx > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        val color = if (tx.isReversed) MaterialTheme.colorScheme.onSurfaceVariant
                        else if (tx.type.isDebit) financial.danger else financial.success
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tx.customerName, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${tx.type.labelAr} • ${FormatUtils.formatDate(tx.transactionDate)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = (if (tx.type.isDebit) "+" else "-") + FormatUtils.formatAmount(tx.amount, cur),
                                fontWeight = FontWeight.Bold,
                                color = color,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    label: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .height(82.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(LocalAppIconShape.current)
                    .background(color.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// 2. إدارة العملاء
enum class CustomerFilter(val labelAr: String) {
    ALL("الكل"),
    DEBTORS("المدينون"),
    SETTLED("المسددون"),
    OVERDUE("المتأخرون"),
    ARCHIVED("المؤرشفون"),
}

enum class CustomerSortOption(val labelAr: String) {
    HIGHEST_BALANCE("الأعلى ديناً"),
    NAME_ASC("الاسم (أ-ي)"),
    EARLIEST_DUE("أقرب موعد استحقاق"),
}

@Composable
fun CustomersScreen(
    viewModel: SmartDebtViewModel,
    onNavigate: (String) -> Unit,
) {
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val importInspection by viewModel.importInspection.collectAsStateWithLifecycle()
    val importBusy by viewModel.importBusy.collectAsStateWithLifecycle()
    val importError by viewModel.importError.collectAsStateWithLifecycle()
    var search by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(CustomerFilter.ALL) }
    var selectedDatePreset by remember { mutableStateOf(DateRangePreset.ALL) }
    var customStartMillis by remember { mutableStateOf<Long?>(null) }
    var customEndMillis by remember { mutableStateOf<Long?>(null) }
    var selectedSort by remember { mutableStateOf(CustomerSortOption.HIGHEST_BALANCE) }
    var showQuickAddCustomerDialog by remember { mutableStateOf(false) }
    var showCsvImportDialog by remember { mutableStateOf(false) }
    var csvReplaceExisting by remember { mutableStateOf(false) }
    val financial = LocalFinancialColors.current
    val cur = settings.currencySymbol
    val context = LocalContext.current

    val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            viewModel.exportCustomersDatabaseCsvToUri(uri)
        }
    }

    val importCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            csvReplaceExisting = false
            showCsvImportDialog = true
            viewModel.inspectImportUri(uri)
        }
    }

    val activeRemindersByCustomer = remember(reminders) {
        reminders.filter { it.status != ReminderStatus.COMPLETED }.groupBy { it.customerId }
    }

    val activeAdvancedCount = remember(selectedFilter, selectedDatePreset, selectedSort) {
        var c = 0
        if (selectedFilter != CustomerFilter.ALL) c++
        if (selectedDatePreset != DateRangePreset.ALL) c++
        if (selectedSort != CustomerSortOption.HIGHEST_BALANCE) c++
        c
    }

    val activePortfolio = remember(customers) {
        customers.filter { !it.isArchived }
    }
    val summaryTotalDebts = remember(activePortfolio) { activePortfolio.sumOf { it.totalDebts } }
    val summaryTotalCollected = remember(activePortfolio) { activePortfolio.sumOf { it.totalPayments } }

    val filtered = remember(customers, activeRemindersByCustomer, search, selectedFilter, selectedDatePreset, customStartMillis, customEndMillis, selectedSort) {
        val q = FormatUtils.normalizeArabicText(search)
        val qPhone = FormatUtils.normalizePhone(search)
        val base = customers.filter { c ->
            val matchesFilter = when (selectedFilter) {
                CustomerFilter.ALL -> !c.isArchived
                CustomerFilter.DEBTORS -> !c.isArchived && c.balance > 0.001
                CustomerFilter.SETTLED -> !c.isArchived && c.balance <= 0.001
                CustomerFilter.OVERDUE -> !c.isArchived && c.balance > 0.001 && c.overdueRemindersCount > 0
                CustomerFilter.ARCHIVED -> c.isArchived
            }
            val matchesQuery = q.isBlank() ||
                FormatUtils.normalizeArabicText(c.name).contains(q) ||
                c.phone.contains(q) ||
                (qPhone.isNotBlank() && FormatUtils.normalizePhone(c.phone).contains(qPhone)) ||
                c.accountNumber.lowercase().contains(q)
            val custReminders = activeRemindersByCustomer[c.id].orEmpty()
            val matchesDueDate = if (selectedDatePreset == DateRangePreset.ALL) {
                true
            } else {
                custReminders.any { r ->
                    matchesDateRange(r.dueDate, selectedDatePreset, customStartMillis, customEndMillis)
                }
            }
            matchesFilter && matchesQuery && matchesDueDate
        }
        when (selectedSort) {
            CustomerSortOption.HIGHEST_BALANCE -> base.sortedWith(compareByDescending<CustomerWithBalance> { it.balance }.thenBy { it.name })
            CustomerSortOption.NAME_ASC -> base.sortedBy { it.name }
            CustomerSortOption.EARLIEST_DUE -> base.sortedBy { cust ->
                activeRemindersByCustomer[cust.id]?.minOfOrNull { it.dueDate } ?: Long.MAX_VALUE
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SmallFloatingActionButton(
                    onClick = { onNavigate(AppRoutes.addEditCustomer()) },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.testTag("fab_add_customer"),
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = "إضافة عميل جديد")
                }
                ExtendedFloatingActionButton(
                    onClick = { onNavigate(AppRoutes.addDebt()) },
                    icon = { Icon(Icons.Default.AddCard, contentDescription = null) },
                    text = { Text("إضافة دين جديد", fontWeight = FontWeight.Bold) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("fab_add_debt"),
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 840.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "قائمة العملاء (${filtered.size})",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilledTonalButton(
                            onClick = { showQuickAddCustomerDialog = true },
                            modifier = Modifier.testTag("quick_add_customer_button"),
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("عميل جديد")
                        }
                        FilledTonalButton(onClick = { onNavigate(AppRoutes.smsCenter()) }) {
                            Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("SMS")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                AdvancedSearchFilterBar(
                    searchQuery = search,
                    onSearchQueryChange = { search = it },
                    searchPlaceholder = "ابحث باسم العميل أو رقم الهاتف...",
                    searchTestTag = "customers_search_input",
                    datePresets = listOf(
                        DateRangePreset.ALL,
                        DateRangePreset.OVERDUE,
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
                    activeAdvancedFilterCount = activeAdvancedCount,
                    onResetAll = {
                        search = ""
                        selectedFilter = CustomerFilter.ALL
                        selectedDatePreset = DateRangePreset.ALL
                        customStartMillis = null
                        customEndMillis = null
                        selectedSort = CustomerSortOption.HIGHEST_BALANCE
                    },
                    primaryStatusChips = {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(CustomerFilter.entries) { filter ->
                                FilterChip(
                                    selected = selectedFilter == filter,
                                    onClick = { selectedFilter = filter },
                                    label = { Text(filter.labelAr) },
                                    modifier = Modifier.testTag("customer_status_${filter.name}"),
                                )
                            }
                        }
                    },
                    extraAdvancedContent = {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "ترتيب القائمة:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(CustomerSortOption.entries) { opt ->
                                FilterChip(
                                    selected = selectedSort == opt,
                                    onClick = { selectedSort = opt },
                                    label = { Text(opt.labelAr) },
                                )
                            }
                        }
                    },
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 840.dp)
                    .weight(1f)
                    .testTag("customers_list"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 132.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "customers_summary_card") {
                    CustomersPortfolioSummaryCard(
                        totalDebts = summaryTotalDebts,
                        totalCollected = summaryTotalCollected,
                        currency = cur,
                        customersCount = activePortfolio.size,
                        onExportCsv = {
                            exportCsvLauncher.launch("SmartDebt_customers_backup.csv")
                        },
                        onImportCsv = {
                            importCsvLauncher.launch(
                                arrayOf(
                                    "text/csv",
                                    "text/comma-separated-values",
                                    "application/csv",
                                    "text/plain",
                                    "*/*",
                                )
                            )
                        },
                    )
                }

                if (filtered.isEmpty()) {
                    item(key = "empty_customers_state") {
                        EmptyStateView(
                            icon = Icons.Default.People,
                            title = "لا يوجد عملاء مطابقون",
                            subtitle = "اضغط على زر إضافة عميل أو استورد ملف CSV أو عدّل البحث.",
                            actionLabel = "إضافة عميل",
                            onAction = { onNavigate(AppRoutes.addEditCustomer()) },
                        )
                    }
                } else {
                    items(filtered, key = { it.id }) { cust ->
                        val nextDue = activeRemindersByCustomer[cust.id]?.minOfOrNull { it.dueDate }
                        CustomerListItemCard(
                            customer = cust,
                            currency = cur,
                            nextDueDateMillis = nextDue,
                            dangerColor = financial.danger,
                            successColor = financial.success,
                            warningColor = financial.orange,
                            onClick = { onNavigate(AppRoutes.customerDetail(cust.id)) },
                            onAddDebt = { onNavigate(AppRoutes.addDebt(cust.id)) },
                            onRecordPayment = { onNavigate(AppRoutes.addPayment(cust.id)) },
                            onSendSmsAlert = { onNavigate(AppRoutes.smsCenter(cust.id)) },
                            onShareBalance = { viewModel.shareCustomerBalance(context, cust) },
                        )
                    }
                }
            }
        }
    }

    if (showQuickAddCustomerDialog) {
        var quickName by remember { mutableStateOf("") }
        var quickPhone by remember { mutableStateOf("") }
        var quickOpening by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showQuickAddCustomerDialog = false },
            title = { Text("إضافة عميل جديد", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quickName,
                        onValueChange = { quickName = it },
                        label = { Text("اسم العميل *") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quick_customer_name_input"),
                    )
                    OutlinedTextField(
                        value = quickPhone,
                        onValueChange = { quickPhone = it },
                        label = { Text("رقم الجوال") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quick_customer_phone_input"),
                    )
                    AmountOutlinedField(
                        value = quickOpening,
                        onValueChange = { quickOpening = it },
                        label = "دين / رصيد افتتاحي (اختياري)",
                        currency = cur,
                        testTag = "quick_customer_opening_input",
                    )
                    TextButton(
                        onClick = {
                            showQuickAddCustomerDialog = false
                            onNavigate(AppRoutes.addEditCustomer())
                        },
                    ) {
                        Text("فتح النموذج التفصيلي الكامل")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val openingVal = quickOpening.toDoubleOrNull() ?: 0.0
                        viewModel.createCustomer(
                            name = quickName,
                            phone = quickPhone,
                            accountNumber = "",
                            creditLimit = 0.0,
                            notes = "",
                            openingBalance = openingVal,
                            onSuccess = {
                                showQuickAddCustomerDialog = false
                            },
                        )
                    },
                    modifier = Modifier.testTag("quick_customer_save_button"),
                ) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickAddCustomerDialog = false }) {
                    Text("إلغاء")
                }
            },
        )
    }

    if (showCsvImportDialog) {
        val inspection = importInspection
        AlertDialog(
            onDismissRequest = {
                if (!importBusy) {
                    showCsvImportDialog = false
                    viewModel.clearImportState()
                }
            },
            title = { Text("استيراد قاعدة البيانات من CSV", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                        importBusy -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Text("جاري فحص ومعالجة ملف CSV بأمان...")
                            }
                        }
                        importError != null -> {
                            Text(
                                text = importError ?: "تعذر استيراد الملف",
                                color = financial.danger,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        inspection != null -> {
                            val readyCount = inspection.previewRows.count { it.status == PreviewRowStatus.READY_NEW }
                            val dupCount = inspection.previewRows.count {
                                it.status == PreviewRowStatus.DUPLICATE_EXISTING || it.status == PreviewRowStatus.DUPLICATE_IN_FILE
                            }
                            val errCount = inspection.previewRows.count { it.status == PreviewRowStatus.INVALID_ERROR }
                            Text(
                                text = "الملف: ${inspection.fileName}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "السجلات الجاهزة للاستيراد: $readyCount • المكررة: $dupCount • غير الصالحة: $errCount",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "استبدال البيانات الحالية",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                Switch(
                                    checked = csvReplaceExisting,
                                    onCheckedChange = { checked ->
                                        csvReplaceExisting = checked
                                        viewModel.setReplacePreviousOnImport(checked)
                                    },
                                    modifier = Modifier.testTag("customers_csv_replace_switch"),
                                )
                            }
                        }
                        else -> {
                            Text("اختر ملف CSV صالحاً لاستيراد العملاء والأرصدة.")
                        }
                    }
                }
            },
            confirmButton = {
                val readyCount = inspection?.previewRows?.count { it.status == PreviewRowStatus.READY_NEW } ?: 0
                if (inspection != null && !importBusy && readyCount > 0) {
                    Button(
                        onClick = {
                            viewModel.confirmAndExecuteImport(
                                createSafetyBackup = true,
                                replacePreviousData = csvReplaceExisting,
                            )
                            showCsvImportDialog = false
                        },
                        modifier = Modifier.testTag("customers_csv_confirm_import_button"),
                    ) {
                        Text("استيراد ($readyCount)")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCsvImportDialog = false
                        viewModel.clearImportState()
                    },
                ) {
                    Text("إغلاق")
                }
            },
        )
    }
}

@Composable
private fun CustomerListItemCard(
    customer: CustomerWithBalance,
    currency: String,
    nextDueDateMillis: Long?,
    dangerColor: Color,
    successColor: Color,
    warningColor: Color,
    onClick: () -> Unit,
    onAddDebt: () -> Unit,
    onRecordPayment: () -> Unit,
    onSendSmsAlert: () -> Unit,
    onShareBalance: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("customer_card_${customer.id}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(LocalAppIconShape.current)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = customer.name.take(1),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = customer.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${customer.phone.ifBlank { "بدون هاتف" }} • ${customer.accountNumber}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (customer.overdueRemindersCount > 0 || nextDueDateMillis != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (customer.overdueRemindersCount > 0) {
                                    StatusBadge("متأخر (${customer.overdueRemindersCount})", warningColor)
                                }
                                if (nextDueDateMillis != null) {
                                    StatusBadge("استحقاق: ${FormatUtils.formatDate(nextDueDateMillis)}", MaterialTheme.colorScheme.secondary)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        val balColor = if (customer.balance > 0.001) dangerColor else successColor
                        Text(
                            text = FormatUtils.formatAmount(customer.balance, currency),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = balColor,
                        )
                        Text(
                            text = if (customer.balance > 0.001) "عليه" else "مسدد",
                            style = MaterialTheme.typography.labelSmall,
                            color = balColor,
                        )
                    }
                    IconButton(
                        onClick = onClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("customer_details_btn_${customer.id}"),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = "عرض تفاصيل العميل ${customer.name}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    onClick = onAddDebt,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("customer_quick_debt_${customer.id}"),
                ) {
                    Icon(
                        Icons.Default.AddCard,
                        contentDescription = "إضافة دين للعميل ${customer.name}",
                        tint = dangerColor,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "إضافة دين",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = dangerColor,
                        maxLines = 1,
                    )
                }

                FilledTonalButton(
                    onClick = onRecordPayment,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("customer_quick_payment_${customer.id}"),
                ) {
                    Icon(
                        Icons.Default.Payments,
                        contentDescription = "تسجيل دفعة للعميل ${customer.name}",
                        tint = successColor,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "تسجيل دفعة",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = successColor,
                        maxLines = 1,
                    )
                }

                if (customer.phone.isNotBlank()) {
                    IconButton(
                        onClick = onSendSmsAlert,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("customer_sms_alert_${customer.id}"),
                    ) {
                        Icon(
                            Icons.Default.Sms,
                            contentDescription = "إرسال تنبيه دين SMS",
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }

                IconButton(
                    onClick = onShareBalance,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("customer_share_balance_${customer.id}"),
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "مشاركة رصيد العميل ${customer.name}",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

// 3. إضافة وتعديل العملاء
@Composable
fun AddEditCustomerScreen(
    customerId: Long?,
    viewModel: SmartDebtViewModel,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
) {
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val existing = remember(customers, customerId) {
        customerId?.let { id -> customers.find { it.id == id } }
    }
    val isEdit = existing != null
    val context = LocalContext.current

    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var phone by remember(existing) { mutableStateOf(existing?.phone ?: "") }
    var accountNumber by remember(existing) { mutableStateOf(existing?.accountNumber ?: "") }
    var creditLimit by remember(existing) { mutableStateOf(existing?.creditLimit?.takeIf { it > 0 }?.toString() ?: "") }
    var openingBalance by remember { mutableStateOf("") }
    var notes by remember(existing) { mutableStateOf(existing?.notes ?: "") }
    var isArchived by remember(existing) { mutableStateOf(existing?.isArchived ?: false) }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            val picked = AppPermissionsManager.readContactFromUri(context, uri)
            if (picked != null) {
                if (picked.name.isNotBlank()) name = picked.name
                if (picked.phone.isNotBlank()) phone = FormatUtils.normalizePhone(picked.phone)
                viewModel.emitFeedback("تم استيراد جهة الاتصال: ${picked.name}")
            } else {
                viewModel.emitFeedback("تعذر قراءة بيانات جهة الاتصال المحددة.", isError = true)
            }
        }
    }

    val contactsPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            runCatching {
                contactPickerLauncher.launch(
                    Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                )
            }.onFailure {
                viewModel.emitFeedback("تطبيق جهات الاتصال غير متوفر على هذا الجهاز.", isError = true)
            }
        } else {
            viewModel.emitFeedback("يلزم منح صلاحية جهات الاتصال لاستيراد الاسم والرقم تلقائياً.", isError = true)
        }
    }

    Scaffold(
        topBar = {
            ArabicTopBar(
                title = if (isEdit) "تعديل بيانات العميل" else "إضافة عميل جديد",
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .align(Alignment.CenterHorizontally),
            ) {
                FinancialCard {
                    OutlinedButton(
                        onClick = {
                            if (AppPermissionsManager.isPermissionGranted(context, Manifest.permission.READ_CONTACTS)) {
                                runCatching {
                                    contactPickerLauncher.launch(
                                        Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                                    )
                                }.onFailure {
                                    viewModel.emitFeedback("تطبيق جهات الاتصال غير متوفر.", isError = true)
                                }
                            } else {
                                contactsPermLauncher.launch(Manifest.permission.READ_CONTACTS)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("pick_contact_button"),
                    ) {
                        Icon(Icons.Default.Contacts, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("استيراد الاسم والهاتف من جهات الاتصال")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("اسم العميل الكامل *") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("customer_name_input"),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("رقم الهاتف / الجوال") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("customer_phone_input"),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = accountNumber,
                        onValueChange = { accountNumber = it },
                        label = { Text("رقم الحساب (يُنشأ تلقائياً إذا تُرك فارغاً)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    AmountOutlinedField(
                        value = creditLimit,
                        onValueChange = { creditLimit = it },
                        label = "الحد الائتماني (اختياري)",
                        currency = settings.currencySymbol,
                        testTag = "customer_credit_limit_input",
                    )
                    if (!isEdit) {
                        Spacer(modifier = Modifier.height(10.dp))
                        AmountOutlinedField(
                            value = openingBalance,
                            onValueChange = { openingBalance = it },
                            label = "رصيد افتتاحي موثق (اختياري)",
                            currency = settings.currencySymbol,
                            testTag = "customer_opening_balance_input",
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("ملاحظات إضافية") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (isEdit) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Archive, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("أرشفة العميل")
                            }
                            Switch(checked = isArchived, onCheckedChange = { isArchived = it })
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    Button(
                        onClick = {
                            val limitVal = creditLimit.toDoubleOrNull() ?: 0.0
                            val openVal = openingBalance.toDoubleOrNull() ?: 0.0
                            if (isEdit && existing != null) {
                                viewModel.updateCustomer(
                                    customerId = existing.id,
                                    name = name,
                                    phone = phone,
                                    accountNumber = accountNumber,
                                    creditLimit = limitVal,
                                    notes = notes,
                                    isArchived = isArchived,
                                    onSuccess = { onSaved(existing.id) },
                                )
                            } else {
                                viewModel.createCustomer(
                                    name = name,
                                    phone = phone,
                                    accountNumber = accountNumber,
                                    creditLimit = limitVal,
                                    notes = notes,
                                    openingBalance = openVal,
                                    onSuccess = onSaved,
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("save_customer_button"),
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isEdit) "حفظ التعديلات" else "حفظ العميل")
                    }
                }
            }
        }
    }
}

// 4. تفاصيل العميل
@Composable
fun CustomerDetailScreen(
    customerId: Long,
    viewModel: SmartDebtViewModel,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val customerFlow = remember(customerId) { viewModel.observeCustomerDetail(customerId) }
    val statementFlow = remember(customerId) { viewModel.observeCustomerStatement(customerId) }
    val customer by customerFlow.collectAsStateWithLifecycle(initialValue = null)
    val txs by statementFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val aiFeatureResult by viewModel.aiFeatureResult.collectAsStateWithLifecycle()
    val financial = LocalFinancialColors.current
    val cur = settings.currencySymbol
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var receiptTargetTx by remember { mutableStateOf<TransactionWithCustomer?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            ArabicTopBar(
                title = customer?.name ?: "تفاصيل العميل",
                subtitle = customer?.accountNumber,
                onBack = onBack,
                actions = {
                    customer?.let { c ->
                        IconButton(
                            onClick = { viewModel.printCustomerStatement(context, c, txs) },
                            modifier = Modifier.testTag("detail_print_statement_button"),
                        ) {
                            Icon(Icons.Default.Print, contentDescription = "طباعة كشف الحساب")
                        }
                        IconButton(
                            onClick = { viewModel.shareCustomerBalance(context, c) },
                            modifier = Modifier.testTag("topbar_share_balance_button"),
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "مشاركة رصيد العميل")
                        }
                        IconButton(
                            onClick = { onNavigate(AppRoutes.addEditCustomer(customerId)) },
                            modifier = Modifier.testTag("detail_edit_customer_button"),
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "تعديل العميل")
                        }
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.testTag("detail_delete_customer_button"),
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "حذف العميل",
                                tint = financial.danger,
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        val c = customer
        if (c == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text("جاري تحميل بيانات العميل...")
            }
            return@Scaffold
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 840.dp)
                    .testTag("customer_detail_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            item {
                FinancialCard(containerColor = MaterialTheme.colorScheme.primary) {
                    Text(
                        text = "الرصيد المستحق = إجمالي الديون - إجمالي المدفوعات",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = FormatUtils.formatAmount(c.balance, cur),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("customer_detail_balance"),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text("إجمالي الديون", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f))
                            Text(FormatUtils.formatAmount(c.totalDebts, cur), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                        }
                        Column {
                            Text("إجمالي المدفوعات", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f))
                            Text(FormatUtils.formatAmount(c.totalPayments, cur), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                        }
                        Column {
                            Text("العمليات", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f))
                            Text("${c.transactionCount}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { onNavigate(AppRoutes.addDebt(c.id)) },
                        colors = ButtonDefaults.buttonColors(containerColor = financial.danger),
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Icon(Icons.Default.AddCard, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("إضافة دين")
                    }
                    Button(
                        onClick = { onNavigate(AppRoutes.addPayment(c.id)) },
                        colors = ButtonDefaults.buttonColors(containerColor = financial.success),
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("تسجيل دفعة")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { onNavigate(AppRoutes.accountStatement(c.id)) },
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("كشف الحساب")
                    }
                    OutlinedButton(
                        onClick = { onNavigate(AppRoutes.smsCenter(c.id)) },
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("إرسال SMS")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        onClick = { viewModel.shareCustomerBalance(context, c) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("detail_share_balance_button"),
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("مشاركة الرصيد")
                    }
                    FilledTonalButton(
                        onClick = { viewModel.generateAiAutoFeature(AiAutoFeatureType.COLLECTION_MESSAGE, c) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("detail_ai_suggest_button"),
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("اقتراح AI تلقائي")
                    }
                }
            }

            item {
                FinancialCard {
                    Text("معلومات التواصل والحساب", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(8.dp))
                        Text("الهاتف: ${c.phone.ifBlank { "غير مسجل" }}")
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(8.dp))
                        Text("رقم الحساب: ${c.accountNumber}")
                    }
                    if (c.creditLimit > 0.0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("الحد الائتماني: ${FormatUtils.formatAmount(c.creditLimit, cur)}")
                    }
                    if (c.notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            Text(c.notes, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item {
                Text(
                    text = "سجل حركات العميل (${txs.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (txs.isEmpty()) {
                item {
                    FinancialCard {
                        Text("لا توجد حركات مالية مسجلة لهذا العميل بعد.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(txs.reversed(), key = { it.id }) { tx ->
                    val txColor = if (tx.isReversed) MaterialTheme.colorScheme.onSurfaceVariant
                    else if (tx.type.isDebit) financial.danger else financial.success
                    FinancialCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    StatusBadge(tx.type.labelAr, txColor)
                                    if (tx.isReversed) StatusBadge("معكوسة", financial.orange)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(tx.description, fontWeight = FontWeight.Medium)
                                Text(
                                    FormatUtils.formatDateTime(tx.transactionDate),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = FormatUtils.formatAmount(tx.amount, cur),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = txColor,
                                )
                                if (!tx.isReversed && (tx.type == TransactionType.PAYMENT || tx.type == TransactionType.SETTLEMENT)) {
                                    IconButton(
                                        onClick = { receiptTargetTx = tx },
                                        modifier = Modifier.testTag("customer_detail_receipt_${tx.id}"),
                                    ) {
                                        Icon(
                                            Icons.Default.PictureAsPdf,
                                            contentDescription = "إيصال سداد PDF",
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }

    if (showDeleteConfirm && customer != null) {
        val currentCust = customer!!
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("حذف العميل", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "هل تريد حذف (${currentCust.name}) من القائمة؟ إذا كان للعميل حركات مالية مسجلة فسيتم الحفاظ على السجل المحاسبي تلقائياً."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteCustomerSafely(currentCust.id, onSuccess = onBack)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = financial.danger),
                    modifier = Modifier.testTag("confirm_delete_customer_button"),
                ) {
                    Text("تأكيد الحذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("إلغاء")
                }
            },
        )
    }

    receiptTargetTx?.let { tx ->
        PaymentReceiptDialog(
            transaction = tx,
            customer = customer,
            currency = cur,
            onSharePdf = { viewModel.sharePaymentReceipt(context, tx, customer) },
            onSavePdfUri = { uri -> viewModel.savePaymentReceiptToUri(uri, tx, customer) },
            onPrintPdf = { viewModel.printPaymentReceipt(context, tx, customer) },
            onDismiss = { receiptTargetTx = null },
        )
    }

    aiFeatureResult?.let { aiRes ->
        val currentCust = customer
        AlertDialog(
            onDismissRequest = { viewModel.clearAiFeatureResult() },
            title = { Text(aiRes.title, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = aiRes.firebaseRequiredNotice,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(
                            listOf(
                                AiAutoFeatureType.COLLECTION_MESSAGE,
                                AiAutoFeatureType.INSTALLMENT_PLAN,
                                AiAutoFeatureType.AUTO_WORKFLOW_RULE,
                            )
                        ) { ft ->
                            FilterChip(
                                selected = aiRes.featureType == ft,
                                onClick = { viewModel.generateAiAutoFeature(ft, currentCust) },
                                label = { Text(ft.titleAr, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                    Text(aiRes.content, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboard.setText(AnnotatedString(aiRes.content))
                        viewModel.emitFeedback("تم نسخ النص المقترح إلى الحافظة")
                    }
                ) {
                    Text("نسخ النص")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearAiFeatureResult() }) {
                    Text("إغلاق")
                }
            },
        )
    }
}
