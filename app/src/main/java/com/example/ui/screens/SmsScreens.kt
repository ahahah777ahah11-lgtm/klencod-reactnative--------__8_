package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import com.example.data.local.MessageStatus
import com.example.data.local.SmsTemplateEntity
import com.example.data.sms.DirectSmsSender
import com.example.data.sms.SmsTemplateEngine
import com.example.ui.SmartDebtViewModel
import com.example.ui.components.ArabicTopBar
import com.example.ui.components.EmptyStateView
import com.example.ui.components.FinancialCard
import com.example.ui.components.StatusBadge
import com.example.ui.navigation.AppRoutes
import com.example.ui.theme.LocalFinancialColors
import com.example.util.FormatUtils

// 11. رسائل SMS
@Composable
fun SmsCenterScreen(
    preselectedCustomerId: Long?,
    viewModel: SmartDebtViewModel,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val context = LocalContext.current
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val templates by viewModel.smsTemplates.collectAsStateWithLifecycle()
    val messageLogs by viewModel.messageLogs.collectAsStateWithLifecycle()
    val smsInboxMessages by viewModel.smsInboxMessages.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val financial = LocalFinancialColors.current
    val cur = settings.currencySymbol

    var tabIndex by remember { mutableIntStateOf(0) }
    val selectedIds = remember { mutableStateMapOf<Long, Boolean>() }
    var activeTemplate by remember { mutableStateOf<SmsTemplateEntity?>(null) }
    var customTemplateBody by remember { mutableStateOf("") }
    var previewCustomerIndex by remember { mutableIntStateOf(0) }
    var editablePreviewBody by remember { mutableStateOf("") }

    var hasSmsPermission by remember { mutableStateOf(DirectSmsSender.hasSendSmsPermission(context)) }
    var hasReadSmsPermission by remember { mutableStateOf(DirectSmsSender.hasReadSmsPermission(context)) }
    var permissionDeniedExplicitly by remember { mutableStateOf(false) }
    var showBulkConfirmDialog by remember { mutableStateOf(false) }
    var pendingDirectAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasSmsPermission = granted
        if (granted) {
            permissionDeniedExplicitly = false
            viewModel.emitFeedback("تم منح صلاحية إرسال الرسائل النصية (SEND_SMS)")
            pendingDirectAction?.invoke()
        } else {
            permissionDeniedExplicitly = true
            viewModel.emitFeedback(
                "لم يتم منح صلاحية SEND_SMS. يمكنك استخدام زر فتح تطبيق الرسائل الرسمي.",
                isError = true,
            )
        }
        pendingDirectAction = null
    }

    val readSmsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasReadSmsPermission = granted
        if (granted) {
            viewModel.loadSmsInboxMessages(context)
        } else {
            viewModel.emitFeedback("لم يتم منح صلاحية قراءة الرسائل (READ_SMS).", isError = true)
        }
    }

    LaunchedEffect(templates) {
        if (activeTemplate == null && templates.isNotEmpty()) {
            val first = templates.first()
            activeTemplate = first
            customTemplateBody = first.body
        }
    }

    LaunchedEffect(preselectedCustomerId, customers) {
        if (preselectedCustomerId != null && preselectedCustomerId > 0 && customers.isNotEmpty()) {
            selectedIds[preselectedCustomerId] = true
        }
    }

    val activeCustomers = remember(customers) { customers.filter { !it.isArchived } }
    val selectedCustomers = activeCustomers.filter { selectedIds[it.id] == true }

    val previewCustomer = selectedCustomers.getOrNull(previewCustomerIndex.coerceIn(0, (selectedCustomers.size - 1).coerceAtLeast(0)))

    LaunchedEffect(previewCustomer?.id, customTemplateBody, settings) {
        editablePreviewBody = if (previewCustomer != null) {
            SmsTemplateEngine.renderMessage(customTemplateBody, previewCustomer, settings)
        } else {
            ""
        }
    }

    Scaffold(
        topBar = {
            ArabicTopBar(
                title = "مركز رسائل SMS",
                subtitle = "إرسال برمجي عبر SmsManager أو عبر تطبيق الرسائل الرسمي",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { onNavigate(AppRoutes.SMS_TEMPLATES) }) {
                        Icon(Icons.Default.Description, contentDescription = "إدارة القوالب")
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
            TabRow(selectedTabIndex = tabIndex) {
                Tab(
                    selected = tabIndex == 0,
                    onClick = { tabIndex = 0 },
                    text = { Text("إرسال (${selectedCustomers.size})") },
                )
                Tab(
                    selected = tabIndex == 1,
                    onClick = { tabIndex = 1 },
                    text = { Text("السجل (${messageLogs.size})") },
                )
                Tab(
                    selected = tabIndex == 2,
                    onClick = { tabIndex = 2 },
                    text = { Text("الواردة (${smsInboxMessages.size})") },
                )
            }

            if (tabIndex == 0) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Direct SmsManager Permission Card
                    item {
                        FinancialCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(
                                        Icons.Default.Security,
                                        contentDescription = null,
                                        tint = if (hasSmsPermission) financial.success else financial.orange,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            "صلاحية الإرسال البرمجي (SmsManager)",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            if (hasSmsPermission) {
                                                "مفعّلة: يمكن إرسال تنبيهات الديون مباشرة عبر SmsManager."
                                            } else if (permissionDeniedExplicitly) {
                                                "مرفوضة حالياً: امنح صلاحية SEND_SMS للإرسال المباشر أو استخدم تطبيق الرسائل."
                                            } else {
                                                "تُطلب صلاحية SEND_SMS عند اختيار الإرسال المباشر فقط."
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                if (!hasSmsPermission) {
                                    OutlinedButton(
                                        onClick = { smsPermissionLauncher.launch(Manifest.permission.SEND_SMS) },
                                        modifier = Modifier.testTag("grant_sms_permission_button"),
                                    ) {
                                        Text("منح الصلاحية")
                                    }
                                } else {
                                    StatusBadge("مفعّلة", financial.success)
                                }
                            }
                        }
                    }

                    // Quick group selection buttons
                    item {
                        FinancialCard {
                            Text("تحديد سريع للمستلمين", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        selectedIds.clear()
                                        activeCustomers.filter { it.balance > 0.001 }.forEach { selectedIds[it.id] = true }
                                        previewCustomerIndex = 0
                                    },
                                    modifier = Modifier.weight(1f).testTag("select_all_debtors_btn"),
                                ) {
                                    Text("تحديد جميع المدينين")
                                }
                                OutlinedButton(
                                    onClick = {
                                        selectedIds.clear()
                                        val now = System.currentTimeMillis()
                                        val threshold = settings.overdueDaysThreshold * 86_400_000L
                                        activeCustomers.filter { c ->
                                            c.balance > 0.001 && (c.overdueRemindersCount > 0 || (c.lastTransactionDate != null && now - c.lastTransactionDate > threshold))
                                        }.forEach { selectedIds[it.id] = true }
                                        previewCustomerIndex = 0
                                    },
                                    modifier = Modifier.weight(1f).testTag("select_overdue_btn"),
                                ) {
                                    Text("تحديد المتأخرين")
                                }
                            }
                            if (selectedCustomers.isNotEmpty()) {
                                TextButton(onClick = { selectedIds.clear() }) {
                                    Text("إلغاء تحديد الكل")
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = {
                                    val autoAction = {
                                        viewModel.sendAutomaticDueDebtReminders(
                                            context = context,
                                            templateBody = customTemplateBody.takeIf { it.isNotBlank() },
                                            onNeedPermission = {
                                                smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                                            },
                                        )
                                    }
                                    if (hasSmsPermission) {
                                        autoAction()
                                    } else {
                                        pendingDirectAction = autoAction
                                        smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("sms_auto_due_reminders_button"),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("إرسال تذكير تلقائي للديون المستحقة الآن")
                            }
                        }
                    }

                    // Template Picker
                    item {
                        FinancialCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("قالب الرسالة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                TextButton(onClick = { onNavigate(AppRoutes.SMS_TEMPLATES) }) {
                                    Text("تعديل القوالب")
                                }
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(templates, key = { it.id }) { tpl ->
                                    FilterChip(
                                        selected = activeTemplate?.id == tpl.id,
                                        onClick = {
                                            activeTemplate = tpl
                                            customTemplateBody = tpl.body
                                        },
                                        label = { Text(tpl.name) },
                                    )
                                }
                            }
                        }
                    }

                    // Preview & Edit Message Card
                    if (previewCustomer != null) {
                        item {
                            FinancialCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "معاينة وتعديل الرسالة (${previewCustomerIndex + 1}/${selectedCustomers.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    if (selectedCustomers.size > 1) {
                                        Row {
                                            TextButton(
                                                onClick = {
                                                    previewCustomerIndex = (previewCustomerIndex - 1 + selectedCustomers.size) % selectedCustomers.size
                                                },
                                            ) { Text("السابق") }
                                            TextButton(
                                                onClick = {
                                                    previewCustomerIndex = (previewCustomerIndex + 1) % selectedCustomers.size
                                                },
                                            ) { Text("التالي") }
                                        }
                                    }
                                }
                                Text(
                                    "المستلم: ${previewCustomer.name} (${previewCustomer.phone.ifBlank { "لا يوجد رقم هاتف!" }})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (previewCustomer.phone.isBlank()) financial.danger else MaterialTheme.colorScheme.secondary,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = editablePreviewBody,
                                    onValueChange = { editablePreviewBody = it },
                                    label = { Text("نص الرسالة القابل للتعديل قبل الإرسال") },
                                    minLines = 4,
                                    modifier = Modifier.fillMaxWidth().testTag("sms_preview_editor"),
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        viewModel.openSmsIntentForCustomer(
                                            context = context,
                                            customer = previewCustomer,
                                            messageBody = editablePreviewBody,
                                            templateName = activeTemplate?.name ?: "رسالة مخصصة",
                                        )
                                        if (selectedCustomers.size > 1 && previewCustomerIndex < selectedCustomers.size - 1) {
                                            previewCustomerIndex++
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("open_sms_intent_button"),
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("فتح تطبيق الرسائل (الافتراضي) لـ ${previewCustomer.name}")
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        val sendAction = {
                                            viewModel.sendDirectDebtAlertViaSmsManager(
                                                context = context,
                                                customer = previewCustomer,
                                                messageBody = editablePreviewBody,
                                                templateName = activeTemplate?.name ?: "رسالة مخصصة",
                                                onNeedPermission = {
                                                    smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                                                },
                                            )
                                        }
                                        if (hasSmsPermission) {
                                            sendAction()
                                        } else {
                                            pendingDirectAction = sendAction
                                            smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("send_direct_sms_manager_button"),
                                ) {
                                    Icon(Icons.Default.Sms, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("إرسال تنبيه مباشر عبر SmsManager")
                                }
                                if (selectedCustomers.size > 1) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = {
                                            if (hasSmsPermission) {
                                                showBulkConfirmDialog = true
                                            } else {
                                                pendingDirectAction = { showBulkConfirmDialog = true }
                                                smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("send_bulk_sms_manager_button"),
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("إرسال تنبيهات للمحددين (${selectedCustomers.size}) عبر SmsManager")
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "الافتراضي: فتح تطبيق الرسائل (Intent.ACTION_SENDTO) دون صلاحيات ولا يعتبر الرسالة مرسلة تلقائياً. الإرسال المباشر عبر SmsManager اختياري ويطلب SEND_SMS فقط عند اختياره.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // Customer Checklist
                    item {
                        Text(
                            text = "اختر العملاء (${activeCustomers.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    items(activeCustomers, key = { it.id }) { cust ->
                        val checked = selectedIds[cust.id] == true
                        FinancialCard(
                            modifier = Modifier.clickable {
                                selectedIds[cust.id] = !checked
                                previewCustomerIndex = 0
                            },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        selectedIds[cust.id] = it
                                        previewCustomerIndex = 0
                                    },
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(cust.name, fontWeight = FontWeight.Bold)
                                    Text(
                                        cust.phone.ifBlank { "بدون رقم هاتف" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    FormatUtils.formatAmount(cust.balance, cur),
                                    fontWeight = FontWeight.Bold,
                                    color = if (cust.balance > 0) financial.danger else financial.success,
                                )
                            }
                        }
                    }
                }
            } else if (tabIndex == 1) {
                if (messageLogs.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Default.Sms,
                        title = "سجل الرسائل فارغ",
                        subtitle = "عند فتح تطبيق الرسائل لأي عميل، ستُسجل الرسالة هنا بانتظار تأكيدك.",
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(messageLogs, key = { it.id }) { log ->
                            val badgeColor = when (log.status) {
                                MessageStatus.INTENT_OPENED -> financial.orange
                                MessageStatus.USER_CONFIRMED_SENT,
                                MessageStatus.SENT_VIA_SMS_MANAGER -> financial.success
                                MessageStatus.SEND_FAILED,
                                MessageStatus.CANCELLED -> financial.danger
                            }
                            FinancialCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    StatusBadge(log.status.labelAr, badgeColor)
                                    Text(
                                        FormatUtils.formatDateTime(log.createdAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("${log.customerName} (${log.phone})", fontWeight = FontWeight.Bold)
                                Text(log.messageBody, style = MaterialTheme.typography.bodyMedium)
                                if (log.status == MessageStatus.INTENT_OPENED) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { viewModel.updateMessageStatus(log.id, MessageStatus.USER_CONFIRMED_SENT) },
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("تأكيد الإرسال")
                                        }
                                        OutlinedButton(
                                            onClick = { viewModel.updateMessageStatus(log.id, MessageStatus.CANCELLED) },
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("لم تُرسل")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        FinancialCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.MarkEmailRead, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text("قراءة رسائل SMS الواردة (READ_SMS)", fontWeight = FontWeight.Bold)
                                        Text(
                                            if (hasReadSmsPermission) "الصلاحية ممنوحة • اضغط تحديث لقراءة صندوق الوارد"
                                            else "امنح صلاحية قراءة الرسائل لقراءة إشعارات الحوالات الواردة",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                StatusBadge(
                                    text = if (hasReadSmsPermission) "مفعّلة" else "مطلوبة",
                                    color = if (hasReadSmsPermission) financial.success else financial.orange,
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    if (DirectSmsSender.hasReadSmsPermission(context)) {
                                        hasReadSmsPermission = true
                                        viewModel.loadSmsInboxMessages(context)
                                    } else {
                                        readSmsPermissionLauncher.launch(Manifest.permission.READ_SMS)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("read_sms_inbox_button"),
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(if (hasReadSmsPermission) "قراءة وتحديث الرسائل الواردة" else "منح صلاحية قراءة SMS وقراءة الوارد")
                            }
                        }
                    }
                    if (smsInboxMessages.isEmpty()) {
                        item {
                            EmptyStateView(
                                icon = Icons.Default.Sms,
                                title = "لا توجد رسائل مقروءة بعد",
                                subtitle = "اضغط على زر قراءة وتحديث الرسائل لعرض آخر الرسائل النصية الواردة.",
                            )
                        }
                    } else {
                        items(smsInboxMessages, key = { "${it.id}_${it.timestamp}" }) { sms ->
                            FinancialCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(sms.address.ifBlank { "مرسل غير معروف" }, fontWeight = FontWeight.Bold)
                                    Text(
                                        FormatUtils.formatDateTime(sms.timestamp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(sms.body, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBulkConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBulkConfirmDialog = false },
            title = { Text("تأكيد إرسال تنبيهات الديون", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "سيتم إرسال ${selectedCustomers.size} رسالة SMS برمجياً عبر SmsManager إلى العملاء المحددين باستخدام القالب المحدد. هل تريد المتابعة؟"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBulkConfirmDialog = false
                        viewModel.sendBulkDebtAlertsViaSmsManager(
                            context = context,
                            recipients = selectedCustomers,
                            templateBody = customTemplateBody,
                            templateName = activeTemplate?.name ?: "تنبيه جماعي",
                            onNeedPermission = {
                                smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                            },
                        )
                    },
                ) {
                    Text("إرسال الآن", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkConfirmDialog = false }) {
                    Text("إلغاء")
                }
            },
        )
    }
}

// 12. قوالب الرسائل
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmsTemplatesScreen(
    viewModel: SmartDebtViewModel,
    onBack: () -> Unit,
) {
    val templates by viewModel.smsTemplates.collectAsStateWithLifecycle()
    var editingTemplate by remember { mutableStateOf<SmsTemplateEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            ArabicTopBar(title = "قوالب رسائل SMS", onBack = onBack)
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingTemplate = null
                    showEditor = true
                },
                modifier = Modifier.testTag("fab_add_template"),
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة قالب رسالة")
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                FinancialCard {
                    Text("المتغيرات الديناميكية المدعومة:", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SmsTemplateEngine.availableVariables.forEach { (token, desc) ->
                            StatusBadge("$token ($desc)", MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }

            items(templates, key = { it.id }) { tpl ->
                FinancialCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(tpl.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row {
                            IconButton(onClick = {
                                editingTemplate = tpl
                                showEditor = true
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "تعديل القالب")
                            }
                            if (!tpl.isDefault) {
                                IconButton(onClick = { viewModel.deleteSmsTemplate(tpl.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "حذف القالب", tint = LocalFinancialColors.current.danger)
                                }
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    Text(tpl.body, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    if (showEditor) {
        var name by remember(editingTemplate) { mutableStateOf(editingTemplate?.name ?: "") }
        var body by remember(editingTemplate) { mutableStateOf(editingTemplate?.body ?: "") }

        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text(if (editingTemplate == null) "إضافة قالب رسالة" else "تعديل القالب", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("اسم القالب *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text("نص القالب *") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("إدراج متغير:", style = MaterialTheme.typography.labelSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        SmsTemplateEngine.availableVariables.forEach { (token, _) ->
                            AssistChip(
                                onClick = { body = "$body $token".trimStart() },
                                label = { Text(token) },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.saveSmsTemplate(editingTemplate?.id, name, body) {
                            showEditor = false
                        }
                    },
                ) {
                    Text("حفظ", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditor = false }) { Text("إلغاء") }
            },
        )
    }
}
