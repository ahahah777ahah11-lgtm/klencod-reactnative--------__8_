package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.importexport.ExportFormat
import com.example.data.local.CustomerWithBalance
import com.example.data.local.TransactionType
import com.example.data.local.TransactionWithCustomer
import com.example.security.AppPermissionsManager
import com.example.ui.SmartDebtViewModel
import com.example.ui.components.AdvancedSearchFilterBar
import com.example.ui.components.AmountOutlinedField
import com.example.ui.components.ArabicTopBar
import com.example.ui.components.CustomerSelectorField
import com.example.ui.components.DateRangePreset
import com.example.ui.components.EmptyStateView
import com.example.ui.components.FinancialCard
import com.example.ui.components.PaymentReceiptDialog
import com.example.ui.components.StatusBadge
import com.example.ui.components.matchesDateRange
import com.example.ui.theme.LocalFinancialColors
import com.example.util.FormatUtils

// 5. كشف الحساب
@Composable
fun AccountStatementScreen(
    customerId: Long,
    viewModel: SmartDebtViewModel,
    onBack: () -> Unit,
) {
    val customer by remember(customerId) {
        viewModel.repository.observeCustomerWithBalance(customerId)
    }.collectAsStateWithLifecycle(initialValue = null)
    val transactions by remember(customerId) {
        viewModel.repository.observeCustomerStatement(customerId)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val financial = LocalFinancialColors.current
    val cur = settings.currencySymbol
    val context = androidx.compose.ui.platform.LocalContext.current

    var includeReversed by remember { mutableStateOf(true) }
    var receiptTargetTx by remember { mutableStateOf<TransactionWithCustomer?>(null) }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val c = customer
        if (uri != null && c != null) {
            viewModel.exportCustomerStatementToUri(c, transactions, ExportFormat.CSV, uri)
        }
    }
    val exportXlsxLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        val c = customer
        if (uri != null && c != null) {
            viewModel.exportCustomerStatementToUri(c, transactions, ExportFormat.XLSX, uri)
        }
    }
    val exportPdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val c = customer
        if (uri != null && c != null) {
            viewModel.saveCustomerStatementPdfToUri(uri, c, transactions)
        }
    }

    val ledgerRows = remember(transactions, includeReversed) {
        var running = 0.0
        transactions.filter { includeReversed || !it.isReversed }.map { tx ->
            if (!tx.isReversed) {
                running += if (tx.type.isDebit) tx.amount else -tx.amount
            }
            tx to running
        }.reversed()
    }

    Scaffold(
        topBar = {
            ArabicTopBar(
                title = "كشف حساب: ${customer?.name ?: ""}",
                subtitle = customer?.accountNumber,
                onBack = onBack,
                actions = {
                    customer?.let { c ->
                        IconButton(
                            onClick = { viewModel.printCustomerStatement(context, c, transactions) },
                            modifier = Modifier.testTag("topbar_print_statement_button"),
                        ) {
                            Icon(Icons.Default.Print, contentDescription = "طباعة كشف الحساب")
                        }
                    }
                    IconButton(onClick = {
                        exportXlsxLauncher.launch("Statement_${customer?.name ?: customerId}.xlsx")
                    }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "تصدير كشف الحساب Excel")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            customer?.let { c ->
                FinancialCard(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text("إجمالي الديون", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(FormatUtils.formatAmount(c.totalDebts, cur), fontWeight = FontWeight.Bold, color = financial.danger)
                        }
                        Column {
                            Text("إجمالي المدفوعات", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(FormatUtils.formatAmount(c.totalPayments, cur), fontWeight = FontWeight.Bold, color = financial.success)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("الرصيد النهائي", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(FormatUtils.formatAmount(c.balance, cur), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { viewModel.printCustomerStatement(context, c, transactions) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("print_statement_button"),
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("طباعة الكشف")
                        }
                        OutlinedButton(
                            onClick = { exportPdfLauncher.launch("Statement_${c.name}.pdf") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_statement_pdf_button"),
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("حفظ PDF")
                        }
                        OutlinedButton(
                            onClick = { viewModel.shareCustomerStatementPdf(context, c, transactions) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("share_statement_pdf_button"),
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("مشاركة")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { exportCsvLauncher.launch("Statement_${c.name}.csv") },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("تصدير CSV")
                        }
                        OutlinedButton(
                            onClick = { exportXlsxLauncher.launch("Statement_${c.name}.xlsx") },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("تصدير Excel")
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = includeReversed,
                    onClick = { includeReversed = !includeReversed },
                    label = { Text("إظهار العمليات المعكوسة") },
                )
            }

            if (ledgerRows.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.ReceiptLong,
                    title = "كشف الحساب فارغ",
                    subtitle = "لا توجد قيود مسجلة في كشف حساب هذا العميل حتى الآن.",
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(ledgerRows, key = { it.first.id }) { (tx, runningBalance) ->
                        val color = if (tx.isReversed) MaterialTheme.colorScheme.onSurfaceVariant
                        else if (tx.type.isDebit) financial.danger else financial.success
                        FinancialCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        StatusBadge(tx.type.labelAr, color)
                                        if (tx.isReversed) StatusBadge("ملغاة/معكوسة", financial.orange)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(tx.description, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        FormatUtils.formatDateTime(tx.transactionDate),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = (if (tx.type.isDebit) "+" else "-") + FormatUtils.formatAmount(tx.amount, cur),
                                            fontWeight = FontWeight.Bold,
                                            color = color,
                                        )
                                        if (!tx.isReversed && (tx.type == TransactionType.PAYMENT || tx.type == TransactionType.SETTLEMENT)) {
                                            IconButton(
                                                onClick = { receiptTargetTx = tx },
                                                modifier = Modifier.testTag("statement_receipt_${tx.id}"),
                                            ) {
                                                Icon(
                                                    Icons.Default.PictureAsPdf,
                                                    contentDescription = "إيصال سداد PDF",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "الرصيد بعد القيد: ${FormatUtils.formatAmount(runningBalance, cur)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
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
}

// 6. إضافة دين
@Composable
fun AddDebtScreen(
    preselectedCustomerId: Long?,
    viewModel: SmartDebtViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val financial = LocalFinancialColors.current
    val cur = settings.currencySymbol
    val context = androidx.compose.ui.platform.LocalContext.current

    var selectedCustomer by remember { mutableStateOf<CustomerWithBalance?>(null) }
    var isOpeningBalance by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var referenceNumber by remember { mutableStateOf("") }
    var attachedPhotoPath by remember { mutableStateOf<String?>(null) }
    var attachedLocationTag by remember { mutableStateOf<String?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            AppPermissionsManager.saveCapturedReceiptPhoto(context, bitmap, "debt")
                .onSuccess { file ->
                    attachedPhotoPath = file.name
                    viewModel.emitFeedback("تم التقاط وحفظ صورة الفاتورة: ${file.name}")
                }
                .onFailure { err ->
                    viewModel.emitFeedback("تعذر حفظ صورة الكاميرا: ${err.message}", isError = true)
                }
        }
    }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            runCatching { cameraLauncher.launch(null) }
                .onFailure { viewModel.emitFeedback("تعذر فتح تطبيق الكاميرا.", isError = true) }
        } else {
            viewModel.emitFeedback("يلزم منح صلاحية الكاميرا لالتقاط صورة الفاتورة.", isError = true)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            AppPermissionsManager.copyPhotoFromPickerUri(context, uri, "debt_gallery")
                .onSuccess { file ->
                    attachedPhotoPath = file.name
                    viewModel.emitFeedback("تم إرفاق الصورة بنجاح: ${file.name}")
                }
                .onFailure { err ->
                    viewModel.emitFeedback("تعذر قراءة الصورة: ${err.message}", isError = true)
                }
        }
    }

    val locationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms.values.any { it }
        if (granted) {
            val loc = AppPermissionsManager.getBestLastKnownLocation(context)
            if (loc != null) {
                attachedLocationTag = loc.toFormattedTag()
                viewModel.emitFeedback("تم إرفاق ${loc.toFormattedTag()}")
            } else {
                viewModel.emitFeedback("تم تفعيل صلاحية الموقع؛ لا تتوفر إشارة GPS حالية.", isError = false)
            }
        } else {
            viewModel.emitFeedback("تم رفض صلاحية الموقع الجغرافي.", isError = true)
        }
    }

    LaunchedEffect(customers, preselectedCustomerId) {
        if (selectedCustomer == null && preselectedCustomerId != null) {
            selectedCustomer = customers.find { it.id == preselectedCustomerId }
        }
    }

    Scaffold(
        topBar = {
            ArabicTopBar(title = "إضافة دين أو رصيد افتتاحي", onBack = onBack)
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
            FinancialCard {
                CustomerSelectorField(
                    customers = customers,
                    selectedCustomer = selectedCustomer,
                    currency = cur,
                    onSelect = { selectedCustomer = it },
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !isOpeningBalance,
                        onClick = { isOpeningBalance = false },
                        label = { Text("دين جديد") },
                    )
                    FilterChip(
                        selected = isOpeningBalance,
                        onClick = { isOpeningBalance = true },
                        label = { Text("رصيد افتتاحي موثق") },
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                AmountOutlinedField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = "المبلغ *",
                    currency = cur,
                    testTag = "debt_amount_input",
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("البيان / سبب الدين") },
                    placeholder = { Text("مثال: فاتورة مبيعات آجل رقم ...") },
                    minLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("debt_description_input"),
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = referenceNumber,
                    onValueChange = { referenceNumber = it },
                    label = { Text("رقم الفاتورة / المرجع المحاسبي (اختياري)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("debt_reference_input"),
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text("مرفقات اختيارية (كاميرا / ملف / موقع):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        OutlinedButton(
                            onClick = {
                                if (AppPermissionsManager.isPermissionGranted(context, Manifest.permission.CAMERA)) {
                                    runCatching { cameraLauncher.launch(null) }
                                        .onFailure { viewModel.emitFeedback("تعذر تشغيل الكاميرا.", isError = true) }
                                } else {
                                    cameraPermLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            modifier = Modifier.testTag("debt_camera_button"),
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("تصوير فاتورة")
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.testTag("debt_photo_picker_button"),
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("صورة من المعرض")
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = {
                                if (AppPermissionsManager.hasLocationPermission(context)) {
                                    val loc = AppPermissionsManager.getBestLastKnownLocation(context)
                                    if (loc != null) {
                                        attachedLocationTag = loc.toFormattedTag()
                                        viewModel.emitFeedback("تم إرفاق ${loc.toFormattedTag()}")
                                    } else {
                                        viewModel.emitFeedback("لم يتم رصد موقع حديث على الجهاز بعد.")
                                    }
                                } else {
                                    locationPermLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION,
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.testTag("debt_location_button"),
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("إرفاق الموقع")
                        }
                    }
                }
                if (attachedPhotoPath != null || attachedLocationTag != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val extras = listOfNotNull(
                        attachedPhotoPath?.let { "مرفق: $it" },
                        attachedLocationTag,
                    ).joinToString(" | ")
                    Text(extras, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }

                val parsed = FormatUtils.parsePositiveAmount(amountText)
                val cust = selectedCustomer
                if (cust != null && parsed != null && parsed > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Text("معاينة الرصيد بعد العملية:", style = MaterialTheme.typography.labelMedium)
                            Text(
                                "${FormatUtils.formatAmount(cust.balance, cur)} ⟵ ${FormatUtils.formatAmount(cust.balance + parsed, cur)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = financial.danger,
                            )
                            if (cust.creditLimit > 0.0 && (cust.balance + parsed) > cust.creditLimit) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "تنبيه محاسبي: الرصيد الجديد سيتجاوز الحد الائتماني المحدد (${FormatUtils.formatAmount(cust.creditLimit, cur)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = financial.orange,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
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
                        val baseDesc = description.ifBlank { if (isOpeningBalance) "رصيد افتتاحي" else "دين جديد" }
                        val extras = listOfNotNull(
                            attachedPhotoPath?.let { "مرفق: $it" },
                            attachedLocationTag,
                        ).joinToString(" • ")
                        val fullDesc = if (extras.isBlank()) baseDesc else "$baseDesc ($extras)"
                        viewModel.recordTransaction(
                            customerId = target.id,
                            type = if (isOpeningBalance) TransactionType.OPENING_BALANCE else TransactionType.DEBT,
                            amount = amount,
                            description = fullDesc,
                            dateMillis = System.currentTimeMillis(),
                            referenceNumber = referenceNumber.takeIf { it.isNotBlank() },
                            onSuccess = onSaved,
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = financial.danger),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("submit_debt_button"),
                ) {
                    Icon(Icons.Default.AddCard, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("اعتماد وتسجيل الدين")
                }
            }
        }
    }
}

// 7. تسجيل دفعة
@Composable
fun AddPaymentScreen(
    preselectedCustomerId: Long?,
    viewModel: SmartDebtViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val financial = LocalFinancialColors.current
    val cur = settings.currencySymbol
    val context = androidx.compose.ui.platform.LocalContext.current

    var selectedCustomer by remember { mutableStateOf<CustomerWithBalance?>(null) }
    var isSettlement by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var referenceNumber by remember { mutableStateOf("") }
    var createdReceiptPair by remember { mutableStateOf<Pair<TransactionWithCustomer, CustomerWithBalance>?>(null) }

    LaunchedEffect(customers, preselectedCustomerId) {
        if (selectedCustomer == null && preselectedCustomerId != null) {
            selectedCustomer = customers.find { it.id == preselectedCustomerId }
        }
    }

    Scaffold(
        topBar = {
            ArabicTopBar(title = "تسجيل دفعة أو تسوية", onBack = onBack)
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
            FinancialCard {
                CustomerSelectorField(
                    customers = customers,
                    selectedCustomer = selectedCustomer,
                    currency = cur,
                    onSelect = { selectedCustomer = it },
                )

                val cust = selectedCustomer
                if (cust != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("الرصيد المستحق: ${FormatUtils.formatAmount(cust.balance, cur)}", fontWeight = FontWeight.Bold)
                        if (cust.balance > 0.0) {
                            TextButton(onClick = {
                                amountText = if (cust.balance % 1.0 == 0.0) cust.balance.toLong().toString() else cust.balance.toString()
                            }) {
                                Text("سداد كامل الرصيد")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !isSettlement,
                        onClick = { isSettlement = false },
                        label = { Text("دفعة نقدية") },
                    )
                    FilterChip(
                        selected = isSettlement,
                        onClick = { isSettlement = true },
                        label = { Text("تسوية / خصم مسموح") },
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                AmountOutlinedField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = "مبلغ الدفعة *",
                    currency = cur,
                    testTag = "payment_amount_input",
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("ملاحظات السداد") },
                    placeholder = { Text("مثال: سند قبض نقدي") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = referenceNumber,
                    onValueChange = { referenceNumber = it },
                    label = { Text("رقم سند القبض / المرجع (اختياري)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("payment_reference_input"),
                )

                if (!settings.allowOverpayment) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = financial.orange, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "حماية الحسابات مفعلة: يُمنع الدفع الزائد عن الرصيد المستحق افتراضياً.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                        val txType = if (isSettlement) TransactionType.SETTLEMENT else TransactionType.PAYMENT
                        val desc = description.ifBlank { if (isSettlement) "تسوية حساب" else "دفعة سداد" }
                        val refNo = referenceNumber.trim().takeIf { it.isNotBlank() }
                        val now = System.currentTimeMillis()
                        viewModel.recordTransactionWithId(
                            customerId = target.id,
                            type = txType,
                            amount = amount,
                            description = desc,
                            dateMillis = now,
                            referenceNumber = refNo,
                            onSuccess = { txId ->
                                val receiptTx = TransactionWithCustomer(
                                    id = txId,
                                    customerId = target.id,
                                    customerName = target.name,
                                    customerPhone = target.phone,
                                    type = txType,
                                    amount = amount,
                                    description = desc,
                                    referenceNumber = refNo,
                                    sourceApp = null,
                                    transactionDate = now,
                                    createdAt = now,
                                    isReversed = false,
                                    reversedAt = null,
                                    reversalReason = null,
                                )
                                val updatedCustomer = target.copy(
                                    totalPayments = target.totalPayments + amount,
                                    balance = target.balance - amount,
                                )
                                createdReceiptPair = receiptTx to updatedCustomer
                            },
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = financial.success),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("submit_payment_button"),
                ) {
                    Icon(Icons.Default.Payments, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("تأكيد تسجيل الدفعة")
                }
            }
        }
    }

    createdReceiptPair?.let { (tx, updatedCust) ->
        PaymentReceiptDialog(
            transaction = tx,
            customer = updatedCust,
            currency = cur,
            onSharePdf = { viewModel.sharePaymentReceipt(context, tx, updatedCust) },
            onSavePdfUri = { uri -> viewModel.savePaymentReceiptToUri(uri, tx, updatedCust) },
            onPrintPdf = { viewModel.printPaymentReceipt(context, tx, updatedCust) },
            onDismiss = {
                createdReceiptPair = null
                onSaved()
            },
        )
    }
}

enum class TransactionStatusFilter(val labelAr: String) {
    ALL("جميع الحالات"),
    ACTIVE("سارية / معتمدة"),
    REVERSED("معكوسة / ملغاة"),
    OVERDUE_CUSTOMER("لعملاء متأخرين"),
}

// 8. سجل العمليات وسجل التعديلات
@Composable
fun TransactionsLogScreen(
    viewModel: SmartDebtViewModel,
    onBack: () -> Unit,
) {
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val financial = LocalFinancialColors.current
    val cur = settings.currencySymbol
    val context = androidx.compose.ui.platform.LocalContext.current

    var tabIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var customerNameFilter by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf(TransactionStatusFilter.ALL) }
    var typeFilter by remember { mutableStateOf<TransactionType?>(null) }
    var selectedDatePreset by remember { mutableStateOf(DateRangePreset.ALL) }
    var customStartMillis by remember { mutableStateOf<Long?>(null) }
    var customEndMillis by remember { mutableStateOf<Long?>(null) }
    var reverseTarget by remember { mutableStateOf<TransactionWithCustomer?>(null) }
    var reverseReason by remember { mutableStateOf("") }
    var receiptTargetTx by remember { mutableStateOf<TransactionWithCustomer?>(null) }

    val customersById = remember(customers) { customers.associateBy { it.id } }
    val activeAdvancedCount = remember(statusFilter, typeFilter, selectedDatePreset, customerNameFilter) {
        var c = 0
        if (statusFilter != TransactionStatusFilter.ALL) c++
        if (typeFilter != null) c++
        if (selectedDatePreset != DateRangePreset.ALL) c++
        if (customerNameFilter.isNotBlank()) c++
        c
    }

    Scaffold(
        topBar = {
            ArabicTopBar(title = "سجل العمليات والمراجعة", onBack = onBack)
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
                    text = { Text("العمليات (${transactions.size})") },
                )
                Tab(
                    selected = tabIndex == 1,
                    onClick = { tabIndex = 1 },
                    text = { Text("سجل المراجعة (${auditLogs.size})") },
                )
            }

            if (tabIndex == 0) {
                val filteredTx = remember(
                    transactions,
                    customersById,
                    searchQuery,
                    customerNameFilter,
                    statusFilter,
                    typeFilter,
                    selectedDatePreset,
                    customStartMillis,
                    customEndMillis,
                ) {
                    val q = FormatUtils.normalizeArabicText(searchQuery)
                    val custQ = FormatUtils.normalizeArabicText(customerNameFilter)
                    transactions.filter { tx ->
                        val matchesType = typeFilter == null || tx.type == typeFilter
                        val matchesStatus = when (statusFilter) {
                            TransactionStatusFilter.ALL -> true
                            TransactionStatusFilter.ACTIVE -> !tx.isReversed
                            TransactionStatusFilter.REVERSED -> tx.isReversed
                            TransactionStatusFilter.OVERDUE_CUSTOMER -> (customersById[tx.customerId]?.overdueRemindersCount ?: 0) > 0
                        }
                        val matchesSearch = q.isBlank() ||
                            FormatUtils.normalizeArabicText(tx.customerName).contains(q) ||
                            FormatUtils.normalizeArabicText(tx.description).contains(q) ||
                            (tx.referenceNumber?.contains(q, ignoreCase = true) == true) ||
                            tx.customerPhone.contains(q)
                        val matchesCustomerName = custQ.isBlank() ||
                            FormatUtils.normalizeArabicText(tx.customerName).contains(custQ)
                        val matchesDate = matchesDateRange(
                            timestampMillis = tx.transactionDate,
                            preset = selectedDatePreset,
                            customStartMillis = customStartMillis,
                            customEndMillis = customEndMillis,
                        )
                        matchesType && matchesStatus && matchesSearch && matchesCustomerName && matchesDate
                    }
                }

                AdvancedSearchFilterBar(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    searchPlaceholder = "ابحث باسم العميل، البيان، أو المرجع...",
                    searchTestTag = "transactions_search_input",
                    datePresets = listOf(
                        DateRangePreset.ALL,
                        DateRangePreset.TODAY,
                        DateRangePreset.LAST_7_DAYS,
                        DateRangePreset.LAST_30_DAYS,
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
                        searchQuery = ""
                        customerNameFilter = ""
                        statusFilter = TransactionStatusFilter.ALL
                        typeFilter = null
                        selectedDatePreset = DateRangePreset.ALL
                        customStartMillis = null
                        customEndMillis = null
                    },
                    customerNameFilter = customerNameFilter,
                    onCustomerNameFilterChange = { customerNameFilter = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    primaryStatusChips = {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = typeFilter == null,
                                    onClick = { typeFilter = null },
                                    label = { Text("الكل") },
                                )
                            }
                            items(TransactionType.entries) { t ->
                                FilterChip(
                                    selected = typeFilter == t,
                                    onClick = { typeFilter = t },
                                    label = { Text(t.labelAr) },
                                    modifier = Modifier.testTag("tx_type_${t.name}"),
                                )
                            }
                        }
                    },
                    extraAdvancedContent = {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "حالة القيد:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(TransactionStatusFilter.entries) { st ->
                                FilterChip(
                                    selected = statusFilter == st,
                                    onClick = { statusFilter = st },
                                    label = { Text(st.labelAr) },
                                    modifier = Modifier.testTag("tx_status_${st.name}"),
                                )
                            }
                        }
                    },
                )

                if (filteredTx.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Default.History,
                        title = "لا توجد عمليات مطابقة",
                        subtitle = "جرب تعديل معايير البحث أو نوع العملية أو نطاق التاريخ.",
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(filteredTx, key = { it.id }) { tx ->
                            val txColor = if (tx.isReversed) MaterialTheme.colorScheme.onSurfaceVariant
                            else if (tx.type.isDebit) financial.danger else financial.success
                            FinancialCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            StatusBadge(tx.type.labelAr, txColor)
                                            if (tx.isReversed) StatusBadge("معكوسة", financial.orange)
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(tx.customerName, fontWeight = FontWeight.Bold)
                                        Text(tx.description, style = MaterialTheme.typography.bodyMedium)
                                        if (!tx.referenceNumber.isNullOrBlank()) {
                                            Text("مرجع: ${tx.referenceNumber}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                                        }
                                        if (tx.isReversed && !tx.reversalReason.isNullOrBlank()) {
                                            Text("سبب العكس: ${tx.reversalReason}", style = MaterialTheme.typography.bodySmall, color = financial.orange)
                                        }
                                        Text(
                                            FormatUtils.formatDateTime(tx.transactionDate),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            FormatUtils.formatAmount(tx.amount, cur),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = txColor,
                                        )
                                        Row {
                                            if (!tx.isReversed && (tx.type == TransactionType.PAYMENT || tx.type == TransactionType.SETTLEMENT)) {
                                                IconButton(
                                                    onClick = { receiptTargetTx = tx },
                                                    modifier = Modifier.testTag("tx_receipt_${tx.id}"),
                                                ) {
                                                    Icon(
                                                        Icons.Default.PictureAsPdf,
                                                        contentDescription = "إيصال سداد PDF",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                            }
                                            if (!tx.isReversed) {
                                                IconButton(
                                                    onClick = {
                                                        reverseTarget = tx
                                                        reverseReason = ""
                                                    },
                                                    modifier = Modifier.testTag("reverse_tx_${tx.id}"),
                                                ) {
                                                    Icon(
                                                        Icons.Default.Replay,
                                                        contentDescription = "عكس العملية مع التوثيق",
                                                        tint = financial.orange,
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
            } else {
                if (auditLogs.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Default.History,
                        title = "سجل المراجعة فارغ",
                        subtitle = "سيتم توثيق كل إضافة أو تعديل أو عكس عملية تلقائياً هنا.",
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(auditLogs, key = { it.id }) { log ->
                            FinancialCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    StatusBadge(log.actionType, MaterialTheme.colorScheme.secondary)
                                    Text(
                                        FormatUtils.formatDateTime(log.createdAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(log.summary, fontWeight = FontWeight.SemiBold)
                                if (log.details.isNotBlank()) {
                                    Text(log.details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    reverseTarget?.let { tx ->
        AlertDialog(
            onDismissRequest = { reverseTarget = null },
            title = { Text("عكس العملية المالية", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("لا يتم حذف العمليات المعتمدة نهائياً؛ سيتم عكس أثر العملية (${tx.type.labelAr} بقيمة ${FormatUtils.formatAmount(tx.amount, cur)}) وتوثيقها في سجل المراجعة.")
                    OutlinedTextField(
                        value = reverseReason,
                        onValueChange = { reverseReason = it },
                        label = { Text("سبب العكس / الإلغاء *") },
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth().testTag("reverse_reason_input"),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.reverseTransaction(tx.id, reverseReason)
                        reverseTarget = null
                    },
                ) {
                    Text("تأكيد العكس", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { reverseTarget = null }) {
                    Text("إلغاء")
                }
            },
        )
    }

    receiptTargetTx?.let { tx ->
        val cust = customersById[tx.customerId]
        PaymentReceiptDialog(
            transaction = tx,
            customer = cust,
            currency = cur,
            onSharePdf = { viewModel.sharePaymentReceipt(context, tx, cust) },
            onSavePdfUri = { uri -> viewModel.savePaymentReceiptToUri(uri, tx, cust) },
            onPrintPdf = { viewModel.printPaymentReceipt(context, tx, cust) },
            onDismiss = { receiptTargetTx = null },
        )
    }
}
