package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.data.ai.AiAutoFeatureType
import com.example.data.ai.AiGeneratedFeatureResult
import com.example.data.update.AppUpdateChecker
import com.example.data.update.AppUpdateState
import com.example.security.AppCapabilityKind
import com.example.security.AppPermissionsManager
import com.example.security.BiometricAuthHelper
import com.example.security.findFragmentActivity
import com.example.ui.SmartDebtViewModel
import com.example.ui.components.ArabicTopBar
import com.example.ui.components.FinancialCard
import com.example.ui.components.StatusBadge
import com.example.ui.navigation.AppRoutes
import com.example.ui.theme.AppBackgroundStyle
import com.example.ui.theme.AppColorPalette
import com.example.ui.theme.AppIconShape
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.LocalAppIconShape
import com.example.ui.theme.LocalFinancialColors
import com.example.util.FormatUtils

// 17. الإعدادات والمظهر
@Composable
fun SettingsScreen(
    viewModel: SmartDebtViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val authorizedFolders by viewModel.authorizedFolders.collectAsStateWithLifecycle()
    val financial = LocalFinancialColors.current
    val context = LocalContext.current
    val biometricAvailability = remember(context) { BiometricAuthHelper.checkAvailability(context) }

    var storeName by remember(settings.storeName) { mutableStateOf(settings.storeName) }
    var contactPhone by remember(settings.contactPhone) { mutableStateOf(settings.contactPhone) }
    var currencySymbol by remember(settings.currencySymbol) { mutableStateOf(settings.currencySymbol) }
    var allowOverpayment by remember(settings.allowOverpayment) { mutableStateOf(settings.allowOverpayment) }
    var duplicateWindow by remember(settings.duplicateWindowMinutes) { mutableStateOf(settings.duplicateWindowMinutes.toString()) }
    var overdueDays by remember(settings.overdueDaysThreshold) { mutableStateOf(settings.overdueDaysThreshold.toString()) }
    var updateUrlInput by remember(settings.updateManifestUrl) { mutableStateOf(settings.updateManifestUrl) }
    var permissionsRefreshTick by remember { mutableIntStateOf(0) }

    val capabilities = remember(context, settings.workManagerRemindersEnabled, authorizedFolders, permissionsRefreshTick) {
        AppPermissionsManager.getCapabilityStatuses(context, settings.workManagerRemindersEnabled)
    }

    val storageFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.onStorageFolderAuthorized(uri)
            permissionsRefreshTick++
        }
    }

    val storageFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val persisted = AppPermissionsManager.takePersistableReadPermission(context, uri)
            permissionsRefreshTick++
            viewModel.emitFeedback(
                if (persisted) "تم منح صلاحية الوصول الدائم للملف المحدد في التخزين"
                else "تم فتح الملف المحدد بنجاح"
            )
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionsRefreshTick++
        if (granted) {
            viewModel.setWorkManagerReminders(true)
        } else {
            viewModel.emitFeedback("تم تفعيل الفحص في الخلفية بدون إشعارات نظام.", isError = false)
            viewModel.setWorkManagerReminders(true)
        }
    }

    val singlePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionsRefreshTick++
        if (granted) {
            viewModel.emitFeedback("تم منح الصلاحية بنجاح")
        } else {
            viewModel.emitFeedback("لم يتم منح الصلاحية؛ يمكنك تفعيلها من إعدادات النظام.", isError = true)
        }
    }

    val multiplePermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsRefreshTick++
        val grantedCount = results.values.count { it }
        viewModel.emitFeedback("تم منح $grantedCount من أصل ${results.size} صلاحيات مطلوبة")
    }

    var showErrorLogPreview by remember { mutableStateOf(false) }
    var errorLogPreviewTick by remember { mutableIntStateOf(0) }
    val exportErrorLogLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            viewModel.exportErrorLogToUri(uri)
        }
    }

    Scaffold(
        topBar = {
            ArabicTopBar(title = "الإعدادات والمظهر", onBack = onBack)
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
                Text("بيانات المتجر (تظهر في رسائل SMS)", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = storeName,
                    onValueChange = { storeName = it },
                    label = { Text("اسم المتجر / النشاط") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = contactPhone,
                    onValueChange = { contactPhone = it },
                    label = { Text("رقم التواصل") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = currencySymbol,
                    onValueChange = { currencySymbol = it },
                    label = { Text("رمز العملة (مثال: ر.ي، ر.س، $)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { viewModel.updateStoreInfo(storeName, contactPhone, currencySymbol) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("حفظ بيانات المتجر")
                }
            }

            FinancialCard {
                val previewDark = when (settings.themeMode) {
                    AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                    AppThemeMode.LIGHT -> false
                    AppThemeMode.DARK -> true
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(LocalAppIconShape.current)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("المظهر، ألوان الخلفية، وأشكال الأيقونات", fontWeight = FontWeight.Bold)
                        Text(
                            "تخصيص فوري ينعكس على كافة الشاشات (RTL)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                // Live preview strip of active background, theme primary, and icon shape
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("appearance_live_preview"),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(LocalAppIconShape.current)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Widgets,
                                contentDescription = "معاينة شكل الأيقونة",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${settings.colorPalette.titleAr} • ${settings.backgroundStyle.titleAr}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "شكل الأيقونات: ${settings.iconShape.titleAr}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("وضع العرض:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AppThemeMode.entries) { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { viewModel.updateTheme(themeMode = mode) },
                            label = { Text(mode.titleAr) },
                            modifier = Modifier.testTag("theme_mode_${mode.name}"),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("لوحة ألوان الثيم:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AppColorPalette.entries) { pal ->
                        val selected = settings.colorPalette == pal
                        Surface(
                            onClick = { viewModel.updateTheme(palette = pal) },
                            shape = MaterialTheme.shapes.small,
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            border = BorderStroke(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) pal.previewPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            ),
                            modifier = Modifier.testTag("palette_${pal.name}"),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(pal.previewPrimary),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (selected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                                Text(
                                    text = pal.titleAr,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("لون الخلفية:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AppBackgroundStyle.entries) { bg ->
                        val selected = settings.backgroundStyle == bg
                        val swatchColor = if (previewDark) bg.darkPreview else bg.lightPreview
                        Surface(
                            onClick = {
                                viewModel.updateTheme(backgroundStyle = bg)
                            },
                            shape = MaterialTheme.shapes.small,
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            border = BorderStroke(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            ),
                            modifier = Modifier.testTag("bg_style_${bg.name}"),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = swatchColor,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier.size(22.dp),
                                ) {}
                                Text(
                                    text = bg.titleAr,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("شكل الأيقونات المميز:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AppIconShape.entries) { shapeOption ->
                        val selected = settings.iconShape == shapeOption
                        Surface(
                            onClick = {
                                viewModel.updateTheme(iconShape = shapeOption)
                            },
                            shape = MaterialTheme.shapes.small,
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            border = BorderStroke(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            ),
                            modifier = Modifier.testTag("icon_shape_${shapeOption.name}"),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(shapeOption.shape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Default.Palette,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                                Text(
                                    text = shapeOption.titleAr,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }

            FinancialCard {
                Text("القواعد المحاسبية وحماية الديون", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("السماح بالدفع الزائد عن الرصيد")
                        Text(
                            "مغلق افتراضياً لمنع أخطاء التحصيل الزائد",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = allowOverpayment,
                        onCheckedChange = { allowOverpayment = it },
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = duplicateWindow,
                    onValueChange = { duplicateWindow = it.filter { ch -> ch.isDigit() } },
                    label = { Text("نافذة منع العمليات المكررة (بالدقائق)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = overdueDays,
                    onValueChange = { overdueDays = it.filter { ch -> ch.isDigit() } },
                    label = { Text("أيام اعتبار العميل متأخراً") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        viewModel.updateAccountingRules(
                            allowOverpayment = allowOverpayment,
                            duplicateWindowMinutes = duplicateWindow.toIntOrNull() ?: 5,
                            overdueDays = overdueDays.toIntOrNull() ?: 14,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("حفظ القواعد المحاسبية")
                }
            }

            FinancialCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("تفعيل فحص التذكيرات الدوري (WorkManager)", fontWeight = FontWeight.SemiBold)
                        Text(
                            "فحص محلي بالخلفية كل 12 ساعة لإرسال إشعار محلي قبل موعد الاستحقاق بـ 48 ساعة وعند التأخر دون إنترنت.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.workManagerRemindersEnabled,
                        onCheckedChange = { enable ->
                            if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.setWorkManagerReminders(enable)
                            }
                        },
                        modifier = Modifier.testTag("workmanager_reminders_switch"),
                    )
                }
            }

            FinancialCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Fingerprint,
                            contentDescription = null,
                            tint = if (biometricAvailability.canAuthenticate) financial.success else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("حماية التطبيق بالبصمة / الوجه (BiometricPrompt)", fontWeight = FontWeight.SemiBold)
                            Text(
                                biometricAvailability.messageAr,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Switch(
                        checked = settings.biometricLockEnabled,
                        onCheckedChange = { enable ->
                            if (!enable) {
                                viewModel.setBiometricLockEnabled(false)
                            } else {
                                val activity = context.findFragmentActivity()
                                if (!biometricAvailability.canAuthenticate) {
                                    viewModel.emitFeedback(biometricAvailability.messageAr, isError = true)
                                } else if (activity != null) {
                                    BiometricAuthHelper.authenticate(
                                        activity = activity,
                                        title = "تفعيل القفل البيومتري",
                                        subtitle = "تحقق بالبصمة أو الوجه لتأمين الوصول للتطبيق وبيانات الديون",
                                        onSuccess = { viewModel.setBiometricLockEnabled(true) },
                                        onError = { msg -> viewModel.emitFeedback(msg, isError = true) },
                                    )
                                } else {
                                    viewModel.setBiometricLockEnabled(true)
                                }
                            }
                        },
                        modifier = Modifier.testTag("biometric_lock_switch"),
                    )
                }
                if (settings.biometricLockEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { viewModel.lockSession() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("lock_app_now_button"),
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("قفل التطبيق الآن (اختبار التحقق بالبصمة)")
                    }
                }
            }

            FinancialCard(modifier = Modifier.testTag("permissions_hub_card")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(LocalAppIconShape.current)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("مركز الصلاحيات والخدمات المتاحة", fontWeight = FontWeight.Bold)
                        Text(
                            "الملفات، الإشعارات، التذكيرات، جهات الاتصال، الكاميرا، الموقع، وSMS",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                capabilities.forEachIndexed { idx, cap ->
                    if (idx > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(cap.kind.titleAr, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                cap.kind.descriptionAr,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (cap.kind == AppCapabilityKind.STORAGE_FOLDERS) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                if (cap.isGranted) {
                                    StatusBadge("${authorizedFolders.size} مجلد", financial.success)
                                }
                                OutlinedButton(
                                    onClick = { storageFolderLauncher.launch(null) },
                                    modifier = Modifier.testTag("grant_perm_STORAGE_FOLDERS"),
                                ) {
                                    Text(if (cap.isGranted) "إضافة" else "منح مجلد")
                                }
                            }
                        } else if (cap.isGranted) {
                            StatusBadge("مفعّل", financial.success)
                        } else if (cap.kind == AppCapabilityKind.REMINDERS) {
                            OutlinedButton(onClick = { viewModel.setWorkManagerReminders(true) }) {
                                Text("تفعيل")
                            }
                        } else if (cap.kind.permission != null) {
                            OutlinedButton(
                                onClick = {
                                    if (cap.kind == AppCapabilityKind.LOCATION) {
                                        multiplePermissionsLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                            )
                                        )
                                    } else {
                                        singlePermissionLauncher.launch(cap.kind.permission)
                                    }
                                },
                                modifier = Modifier.testTag("grant_perm_${cap.kind.name}"),
                            ) {
                                Text("منح")
                            }
                        }
                    }
                }

                if (authorizedFolders.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "مجلدات الذاكرة المصرح بالوصول الدائم إليها (${authorizedFolders.size}):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    authorizedFolders.forEach { folder ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(folder.displayLabel, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            if (folder.canWrite) "قراءة وحفظ مباشر" else "صلاحية قراءة دائمة",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        viewModel.revokeStorageFolder(folder.treeUri)
                                        permissionsRefreshTick++
                                    },
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "إزالة صلاحية المجلد",
                                        tint = financial.danger,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { storageFolderLauncher.launch(null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("grant_storage_folder_button"),
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("منح وصول لمجلدات ذاكرة الهاتف (التنزيلات / المستندات)")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { storageFileLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("grant_storage_file_button"),
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("منح وصول لملفات التخزين عبر مستكشف الملفات (SAF)")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            val missing = AppPermissionsManager.missingRuntimePermissions(context)
                            if (missing.isEmpty()) {
                                viewModel.emitFeedback("جميع صلاحيات التطبيق متاحة ومفعّلة بالفعل")
                            } else {
                                multiplePermissionsLauncher.launch(missing)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("grant_all_permissions_button"),
                    ) {
                        Text("تفعيل جميع الصلاحيات")
                    }
                    OutlinedButton(
                        onClick = { AppPermissionsManager.openAppPermissionSettings(context) },
                        modifier = Modifier.testTag("open_system_perm_settings_button"),
                    ) {
                        Text("إعدادات النظام")
                    }
                }
            }

            FinancialCard(modifier = Modifier.testTag("app_update_card")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(LocalAppIconShape.current)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("التحديث التلقائي للتطبيق", fontWeight = FontWeight.Bold)
                        Text(
                            "الإصدار المثبت حالياً: v${BuildConfig.VERSION_NAME} (رمز ${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("التحقق التلقائي عند تشغيل التطبيق", fontWeight = FontWeight.SemiBold)
                        Text(
                            "يعمل بأمان عند توفر رابط بيان التحديثات ولا يؤثر على العمل دون إنترنت.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.autoUpdateCheckEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.setAutoUpdateSettings(enabled, updateUrlInput)
                        },
                        modifier = Modifier.testTag("auto_update_switch"),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = updateUrlInput,
                    onValueChange = { updateUrlInput = it },
                    label = { Text("رابط بيان التحديثات JSON (اختياري)") },
                    placeholder = { Text("https://example.com/smartdebt-update.json") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("update_manifest_url_input"),
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            viewModel.setAutoUpdateSettings(settings.autoUpdateCheckEnabled, updateUrlInput)
                            viewModel.checkForAppUpdatesNow(updateUrlInput)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("check_updates_now_button"),
                    ) {
                        Text("فحص التحديثات الآن")
                    }
                }
                when (val st = updateState) {
                    AppUpdateState.Checking -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    is AppUpdateState.UpToDate -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(st.message, style = MaterialTheme.typography.bodySmall, color = financial.success)
                    }
                    is AppUpdateState.UpdateAvailable -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                                Text("يتوفر تحديث جديد: v${st.latestVersionName}", fontWeight = FontWeight.Bold)
                                Text(st.releaseNotes, style = MaterialTheme.typography.bodySmall)
                                if (st.downloadUrl.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedButton(
                                        onClick = { AppUpdateChecker.openDownloadUrl(context, st.downloadUrl) },
                                    ) {
                                        Text("فتح رابط التحديث")
                                    }
                                }
                            }
                        }
                    }
                    is AppUpdateState.CheckError -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(st.message, style = MaterialTheme.typography.bodySmall, color = financial.orange)
                    }
                    AppUpdateState.Idle -> Unit
                }
            }

            FinancialCard(modifier = Modifier.testTag("settings_error_log_card")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(LocalAppIconShape.current)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.BugReport,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("سجل أخطاء الاستيراد والتشخيص (ErrorLogger)", fontWeight = FontWeight.Bold)
                        Text(
                            "حفظ استثناءات الاستيراد وLogcat في ملف نصي (.txt) قابل للتصدير",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { exportErrorLogLauncher.launch("SmartDebt-ErrorLog.txt") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_export_error_log_button"),
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("تصدير .txt")
                    }
                    OutlinedButton(
                        onClick = { viewModel.shareErrorLog(context) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_share_error_log_button"),
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("مشاركة")
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = {
                        errorLogPreviewTick++
                        showErrorLogPreview = !showErrorLogPreview
                    }) {
                        Text(if (showErrorLogPreview) "إخفاء المعاينة" else "معاينة السجل المحلي")
                    }
                    TextButton(onClick = {
                        viewModel.clearErrorLogs()
                        errorLogPreviewTick++
                    }) {
                        Text("مسح السجل", color = financial.danger)
                    }
                }
                if (showErrorLogPreview) {
                    val logText = remember(errorLogPreviewTick) { viewModel.getErrorLogPreviewText() }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = logText,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }
            }

            FinancialCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = financial.success)
                    Spacer(Modifier.width(8.dp))
                    Text("الخصوصية والعمل دون إنترنت", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "• لا يوجد تسجيل دخول أو حسابات سحابية.\n• لا تُرفع أي بيانات عملاء إلى الإنترنت.\n• قاعدة البيانات محلية بالكامل (Room SQLite).\n• إرسال الرسائل يتم عبر تطبيق الرسائل الرسمي فقط بموافقتك.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

// 18. المساعد الذكي المحلي
@Composable
fun LocalAssistantScreen(
    viewModel: SmartDebtViewModel,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val messages by viewModel.assistantMessages.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val aiFeatureResult by viewModel.aiFeatureResult.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            ArabicTopBar(
                title = "المساعد الذكي المحلي",
                subtitle = "تحليل فوري دون إنترنت من قاعدة بياناتك",
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            FinancialCard(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("ai_auto_features_card"),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("ميزة الاقتراح والتوليد التلقائي بالذكاء الاصطناعي (AI)", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = AiGeneratedFeatureResult.FIREBASE_REQUIRED_NOTICE_AR,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .testTag("ai_firebase_notice_banner"),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AiAutoFeatureType.entries) { ft ->
                        FilterChip(
                            selected = aiFeatureResult?.featureType == ft,
                            onClick = { viewModel.generateAiAutoFeature(ft) },
                            label = { Text(ft.titleAr) },
                            modifier = Modifier.testTag("ai_feature_chip_${ft.name}"),
                        )
                    }
                }
                aiFeatureResult?.let { res ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                            Text(res.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(res.content, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("ai_generated_content"))
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(res.content))
                                        viewModel.emitFeedback("تم نسخ المخرج المقترح")
                                    }
                                ) {
                                    Text("نسخ")
                                }
                                TextButton(onClick = { viewModel.clearAiFeatureResult() }) {
                                    Text("إخفاء")
                                }
                            }
                        }
                    }
                }
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(viewModel.getQuickAssistantPrompts()) { prompt ->
                    AssistChip(
                        onClick = { viewModel.askLocalAssistant(prompt) },
                        label = { Text(prompt) },
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(messages, key = { it.id }) { msg ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = if (msg.isUser) Alignment.CenterStart else Alignment.CenterEnd,
                    ) {
                        Surface(
                            color = if (msg.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(16.dp),
                            tonalElevation = 2.dp,
                            shadowElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth(0.9f),
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = msg.text,
                                    color = if (msg.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                if (msg.highlightedCustomers.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    msg.highlightedCustomers.forEach { c ->
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 3.dp)
                                                .clickable { onNavigate(AppRoutes.customerDetail(c.id)) },
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                            ) {
                                                Text(c.name, fontWeight = FontWeight.Bold)
                                                Text(
                                                    FormatUtils.formatAmount(c.balance, settings.currencySymbol),
                                                    fontWeight = FontWeight.Bold,
                                                    color = LocalFinancialColors.current.danger,
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

            Surface(tonalElevation = 4.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("اسأل عن عميل، المتأخرين، الحوالات...") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("assistant_query_input"),
                    )
                    IconButton(
                        onClick = {
                            if (input.isNotBlank()) {
                                viewModel.askLocalAssistant(input)
                                input = ""
                            }
                        },
                        modifier = Modifier.size(48.dp).testTag("assistant_send_button"),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "إرسال السؤال", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

// مركز الأدوات للوصول السريع لكل الشاشات الـ 18
@Composable
fun MoreHubScreen(
    onNavigate: (String) -> Unit,
) {
    val itemsList = listOf(
        Triple("سجل العمليات والمراجعة", Icons.Default.History, AppRoutes.TRANSACTIONS_LOG),
        Triple("الحوالات الواردة", Icons.Default.CurrencyExchange, AppRoutes.TRANSFERS),
        Triple("التذكيرات والمتأخرات", Icons.Default.NotificationsActive, AppRoutes.REMINDERS),
        Triple("رسائل SMS", Icons.Default.Sms, AppRoutes.smsCenter()),
        Triple("قوالب الرسائل", Icons.Default.Description, AppRoutes.SMS_TEMPLATES),
        Triple("التقارير والرسوم البيانية", Icons.Default.Analytics, AppRoutes.REPORTS),
        Triple("استيراد قواعد البيانات والملفات", Icons.Default.FileUpload, AppRoutes.IMPORT_DATA),
        Triple("تصدير البيانات (Excel / CSV)", Icons.Default.FileDownload, AppRoutes.EXPORT_DATA),
        Triple("النسخ الاحتياطي والاستعادة", Icons.Default.Backup, AppRoutes.BACKUP_RESTORE),
        Triple("المساعد الذكي المحلي", Icons.Default.AutoAwesome, AppRoutes.LOCAL_ASSISTANT),
        Triple("الإعدادات والمظهر", Icons.Default.Settings, AppRoutes.SETTINGS),
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                text = "الأدوات والإدارة الشاملة",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        items(itemsList) { (title, icon, route) ->
            FinancialCard(
                modifier = Modifier
                    .clickable { onNavigate(route) }
                    .testTag("hub_item_$route"),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(LocalAppIconShape.current)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            icon,
                            contentDescription = title,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
