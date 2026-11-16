package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.importexport.PaymentReceiptPdfGenerator
import com.example.data.local.CustomerWithBalance
import com.example.data.local.TransactionWithCustomer
import com.example.ui.theme.LocalAppIconShape
import com.example.ui.theme.LocalFinancialColors
import com.example.util.FormatUtils
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArabicTopBar(
    title: String,
    subtitle: String? = null,
    canNavigateBack: Boolean = true,
    onBack: () -> Unit = {},
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = {
            if (canNavigateBack) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("top_bar_back_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع",
                    )
                }
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

@Composable
fun FinancialCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content,
        )
    }
}

@Composable
fun KpiTile(
    title: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        ),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(LocalAppIconShape.current)
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun StatusBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun EmptyStateView(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(LocalAppIconShape.current)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!actionLabel.isNullOrBlank() && onAction != null) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun CustomerSelectorField(
    customers: List<CustomerWithBalance>,
    selectedCustomer: CustomerWithBalance?,
    currency: String,
    onSelect: (CustomerWithBalance) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "اختر العميل",
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selectedCustomer?.let { "${it.name} (الرصيد: ${FormatUtils.formatAmount(it.balance, currency)})" } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text("اضغط لاختيار عميل") },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "فتح قائمة العملاء")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .testTag("customer_selector_field"),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.85f),
        ) {
            if (customers.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("لا يوجد عملاء مسجلون") },
                    onClick = { expanded = false },
                )
            } else {
                customers.filter { !it.isArchived }.forEach { cust ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(cust.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "الرصيد: ${FormatUtils.formatAmount(cust.balance, currency)} | ${cust.phone.ifBlank { cust.accountNumber }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = {
                            onSelect(cust)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun DebtVsPaymentBarChart(
    totalDebts: Double,
    totalPayments: Double,
    netBalance: Double,
    currency: String,
    modifier: Modifier = Modifier,
) {
    val financial = LocalFinancialColors.current
    val maxVal = maxOf(totalDebts, totalPayments, netBalance, 1.0)
    val bars = listOf(
        Triple("إجمالي الديون", totalDebts, financial.danger),
        Triple("إجمالي المدفوعات", totalPayments, financial.success),
        Triple("الرصيد المستحق", netBalance.coerceAtLeast(0.0), MaterialTheme.colorScheme.secondary),
    )

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        bars.forEach { (label, amount, color) ->
            val ratio = (amount / maxVal).toFloat().coerceIn(0f, 1f)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        FormatUtils.formatAmount(amount, currency),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = color,
                    )
                }
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp),
                ) {
                    drawRoundRect(
                        color = color.copy(alpha = 0.14f),
                        size = size,
                        cornerRadius = CornerRadius(12f, 12f),
                    )
                    if (ratio > 0f) {
                        drawRoundRect(
                            brush = Brush.horizontalGradient(listOf(color, color.copy(alpha = 0.8f))),
                            size = Size(size.width * ratio, size.height),
                            cornerRadius = CornerRadius(12f, 12f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CollectionDonutChart(
    totalDebts: Double,
    totalPayments: Double,
    modifier: Modifier = Modifier,
    chartSize: androidx.compose.ui.unit.Dp = 100.dp,
    strokeWidth: androidx.compose.ui.unit.Dp = 16.dp,
) {
    val financial = LocalFinancialColors.current
    val collectedRatio = if (totalDebts > 0.0) (totalPayments / totalDebts).toFloat().coerceIn(0f, 1f) else 0f
    val percentText = "${"%.0f".format(collectedRatio * 100)}%"

    Box(modifier = modifier.size(chartSize + 10.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(chartSize)) {
            val stroke = strokeWidth.toPx()
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)
            drawArc(
                color = financial.danger.copy(alpha = 0.24f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (collectedRatio > 0f) {
                drawArc(
                    color = financial.success,
                    startAngle = -90f,
                    sweepAngle = 360f * collectedRatio,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = percentText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "تحصيل",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun CustomersPortfolioSummaryCard(
    totalDebts: Double,
    totalCollected: Double,
    currency: String,
    customersCount: Int,
    onExportCsv: () -> Unit,
    onImportCsv: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val financial = LocalFinancialColors.current
    val maxVal = maxOf(totalDebts, totalCollected, 1.0)
    val collectedRate = if (totalDebts > 0.0) ((totalCollected / totalDebts) * 100.0).coerceIn(0.0, 100.0) else 0.0

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("customers_summary_card"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "ملخص الديون مقابل التحصيل ($customersCount عميل)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(
                    text = "${"%.0f".format(collectedRate)}% محصل",
                    color = if (collectedRate >= 50.0) financial.success else financial.orange,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CollectionDonutChart(
                    totalDebts = totalDebts,
                    totalPayments = totalCollected,
                    chartSize = 58.dp,
                    strokeWidth = 9.dp,
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val debtRatio = (totalDebts / maxVal).toFloat().coerceIn(0f, 1f)
                    val paidRatio = (totalCollected / maxVal).toFloat().coerceIn(0f, 1f)

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("إجمالي الديون", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                FormatUtils.formatAmount(totalDebts, currency),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = financial.danger,
                                modifier = Modifier.testTag("summary_total_debts_text"),
                            )
                        }
                        Canvas(modifier = Modifier.fillMaxWidth().height(8.dp)) {
                            drawRoundRect(
                                color = financial.danger.copy(alpha = 0.14f),
                                size = size,
                                cornerRadius = CornerRadius(8f, 8f),
                            )
                            if (debtRatio > 0f) {
                                drawRoundRect(
                                    brush = Brush.horizontalGradient(listOf(financial.danger, financial.danger.copy(alpha = 0.78f))),
                                    size = Size(size.width * debtRatio, size.height),
                                    cornerRadius = CornerRadius(8f, 8f),
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("المدفوعات المحصلة", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                FormatUtils.formatAmount(totalCollected, currency),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = financial.success,
                                modifier = Modifier.testTag("summary_total_collected_text"),
                            )
                        }
                        Canvas(modifier = Modifier.fillMaxWidth().height(8.dp)) {
                            drawRoundRect(
                                color = financial.success.copy(alpha = 0.14f),
                                size = size,
                                cornerRadius = CornerRadius(8f, 8f),
                            )
                            if (paidRatio > 0f) {
                                drawRoundRect(
                                    brush = Brush.horizontalGradient(listOf(financial.success, financial.success.copy(alpha = 0.78f))),
                                    size = Size(size.width * paidRatio, size.height),
                                    cornerRadius = CornerRadius(8f, 8f),
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onExportCsv,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("customers_export_csv_button"),
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = "تصدير قاعدة البيانات CSV", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("تصدير CSV", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
                FilledTonalButton(
                    onClick = onImportCsv,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("customers_import_csv_button"),
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = "استيراد بيانات CSV", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("استيراد CSV", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
        }
    }
}

private data class ChartBarSeries(
    val title: String,
    val amount: Double,
    val color: Color,
    val subtitle: String,
)

@Composable
fun DebtVsPaymentColumnChart(
    totalDebts: Double,
    totalPayments: Double,
    netBalance: Double,
    currency: String,
    modifier: Modifier = Modifier,
) {
    val financial = LocalFinancialColors.current
    val safeNet = netBalance.coerceAtLeast(0.0)
    val maxVal = maxOf(totalDebts, totalPayments, safeNet, 1.0)
    val series = listOf(
        ChartBarSeries("الديون المستحقة", totalDebts, financial.danger, "إجمالي الديون المسجلة"),
        ChartBarSeries("المدفوعات المستلمة", totalPayments, financial.success, "إجمالي التحصيل"),
        ChartBarSeries("الصافي المتبقي", safeNet, MaterialTheme.colorScheme.secondary, "الديون - المدفوعات"),
    )
    var selectedIndex by remember { mutableIntStateOf(0) }
    val active = series.getOrElse(selectedIndex) { series.first() }
    val shareOfDebts = if (totalDebts > 0.0) ((active.amount / totalDebts) * 100.0).coerceIn(0.0, 100.0) else 0.0
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("debt_vs_payment_column_chart"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Interactive detail pill for the tapped column
        Surface(
            color = active.color.copy(alpha = 0.11f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "${active.title} (${active.subtitle})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = active.color,
                    )
                    Text(
                        text = "النسبة من إجمالي الديون: ${"%.1f".format(shareOfDebts)}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = FormatUtils.formatAmount(active.amount, currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = active.color,
                )
            }
        }

        // Native Compose Canvas grouped vertical column chart
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp)
                .padding(top = 8.dp),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(144.dp)
                    .align(Alignment.TopCenter),
            ) {
                val levels = listOf(0f, 0.5f, 1f)
                val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                levels.forEach { lvl ->
                    val y = size.height * (1f - lvl)
                    drawLine(
                        color = gridColor.copy(alpha = 0.55f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 2f,
                        pathEffect = if (lvl > 0f) dash else null,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom,
            ) {
                series.forEachIndexed { idx, item ->
                    val targetFraction = if (item.amount <= 0.0) 0.04f else (item.amount / maxVal).toFloat().coerceIn(0.08f, 1f)
                    val animatedFraction by animateFloatAsState(
                        targetValue = targetFraction,
                        animationSpec = tween(durationMillis = 500),
                        label = "chart_bar_$idx",
                    )
                    val isSelected = selectedIndex == idx
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedIndex = idx }
                            .padding(horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(136.dp),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .width(42.dp)
                                    .fillMaxHeight(animatedFraction),
                            ) {
                                val topCorner = CornerRadius(14f, 14f)
                                drawRoundRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            item.color,
                                            item.color.copy(alpha = if (isSelected) 0.88f else 0.65f),
                                        )
                                    ),
                                    cornerRadius = topCorner,
                                )
                                if (isSelected) {
                                    drawRoundRect(
                                        color = item.color,
                                        cornerRadius = topCorner,
                                        style = Stroke(width = 3.dp.toPx()),
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) item.color else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // Interactive Legend Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            series.forEachIndexed { idx, item ->
                FilterChip(
                    selected = selectedIndex == idx,
                    onClick = { selectedIndex = idx },
                    label = {
                        Text(
                            item.title,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chart_chip_$idx"),
                )
            }
        }
    }
}

@Composable
fun AmountOutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    currency: String,
    modifier: Modifier = Modifier,
    testTag: String = "amount_input",
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            val filtered = input.filter { it.isDigit() || it == '.' || it == '٫' }
            onValueChange(filtered)
        },
        label = { Text(label) },
        suffix = { Text(currency) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
    )
}

@Composable
fun ConfirmSensitiveActionDialog(
    title: String,
    message: String,
    confirmText: String = "تأكيد",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Info, contentDescription = null) },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(confirmText, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        },
    )
}

enum class DateRangePreset(val labelAr: String) {
    ALL("كل الفترات"),
    OVERDUE("متأخرة حتى الآن"),
    TODAY("اليوم"),
    NEXT_7_DAYS("خلال 7 أيام قادمة"),
    NEXT_30_DAYS("خلال 30 يوماً قادماً"),
    LAST_7_DAYS("آخر 7 أيام"),
    LAST_30_DAYS("آخر 30 يوماً"),
    CUSTOM("نطاق مخصص"),
}

fun matchesDateRange(
    timestampMillis: Long,
    preset: DateRangePreset,
    customStartMillis: Long?,
    customEndMillis: Long?,
    nowMillis: Long = System.currentTimeMillis(),
): Boolean {
    val startOfToday = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val endOfToday = startOfToday + 86_400_000L - 1L
    return when (preset) {
        DateRangePreset.ALL -> true
        DateRangePreset.OVERDUE -> timestampMillis < nowMillis
        DateRangePreset.TODAY -> timestampMillis in startOfToday..endOfToday
        DateRangePreset.NEXT_7_DAYS -> timestampMillis in nowMillis..(nowMillis + 7L * 86_400_000L)
        DateRangePreset.NEXT_30_DAYS -> timestampMillis in nowMillis..(nowMillis + 30L * 86_400_000L)
        DateRangePreset.LAST_7_DAYS -> timestampMillis in (startOfToday - 6L * 86_400_000L)..endOfToday
        DateRangePreset.LAST_30_DAYS -> timestampMillis in (startOfToday - 29L * 86_400_000L)..endOfToday
        DateRangePreset.CUSTOM -> {
            val from = customStartMillis ?: Long.MIN_VALUE
            val to = customEndMillis?.let { it + 86_400_000L - 1L } ?: Long.MAX_VALUE
            timestampMillis in from..to
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSearchFilterBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchPlaceholder: String,
    searchTestTag: String,
    datePresets: List<DateRangePreset>,
    selectedDatePreset: DateRangePreset,
    onDatePresetChange: (DateRangePreset) -> Unit,
    customStartMillis: Long?,
    customEndMillis: Long?,
    onCustomRangeChange: (Long?, Long?) -> Unit,
    activeAdvancedFilterCount: Int,
    onResetAll: () -> Unit,
    modifier: Modifier = Modifier,
    customerNameFilter: String = "",
    onCustomerNameFilterChange: ((String) -> Unit)? = null,
    primaryStatusChips: @Composable () -> Unit = {},
    extraAdvancedContent: @Composable () -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    var pickingStartDate by remember { mutableStateOf(false) }
    var pickingEndDate by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("advanced_search_filter_bar"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text(searchPlaceholder) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "مسح البحث")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag(searchTestTag),
            )

            Surface(
                color = if (expanded || activeAdvancedFilterCount > 0)
                    MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .size(54.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { expanded = !expanded }
                    .testTag("advanced_filter_toggle_button"),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (activeAdvancedFilterCount > 0) {
                        BadgedBox(
                            badge = { Badge { Text("$activeAdvancedFilterCount") } },
                        ) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = "تصفية متقدمة",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = "تصفية متقدمة",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        primaryStatusChips()

        AnimatedVisibility(visible = expanded) {
            FinancialCard(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "خيارات البحث والتصفية المتقدمة",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    if (activeAdvancedFilterCount > 0 || searchQuery.isNotBlank() || customerNameFilter.isNotBlank()) {
                        TextButton(
                            onClick = onResetAll,
                            modifier = Modifier.testTag("reset_advanced_filters_button"),
                        ) {
                            Text("إعادة ضبط")
                        }
                    }
                }

                if (onCustomerNameFilterChange != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = customerNameFilter,
                        onValueChange = onCustomerNameFilterChange,
                        label = { Text("تصفية باسم العميل") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        trailingIcon = {
                            if (customerNameFilter.isNotBlank()) {
                                IconButton(onClick = { onCustomerNameFilterChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "مسح اسم العميل")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("advanced_customer_name_input"),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "نطاق التاريخ / الاستحقاق:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(datePresets) { preset ->
                        FilterChip(
                            selected = selectedDatePreset == preset,
                            onClick = { onDatePresetChange(preset) },
                            label = { Text(preset.labelAr) },
                            modifier = Modifier.testTag("date_filter_${preset.name}"),
                        )
                    }
                }

                if (selectedDatePreset == DateRangePreset.CUSTOM) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { pickingStartDate = true },
                            modifier = Modifier.weight(1f).testTag("pick_start_date_button"),
                        ) {
                            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(customStartMillis?.let { "من: ${FormatUtils.formatDate(it)}" } ?: "من تاريخ")
                        }
                        OutlinedButton(
                            onClick = { pickingEndDate = true },
                            modifier = Modifier.weight(1f).testTag("pick_end_date_button"),
                        ) {
                            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(customEndMillis?.let { "إلى: ${FormatUtils.formatDate(it)}" } ?: "إلى تاريخ")
                        }
                    }
                }

                extraAdvancedContent()
                }
            }
        }
    }

    if (pickingStartDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = customStartMillis ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { pickingStartDate = false },
            confirmButton = {
                TextButton(onClick = {
                    onCustomRangeChange(state.selectedDateMillis, customEndMillis)
                    pickingStartDate = false
                }) { Text("اختيار") }
            },
            dismissButton = {
                TextButton(onClick = { pickingStartDate = false }) { Text("إلغاء") }
            },
        ) {
            DatePicker(state = state)
        }
    }

    if (pickingEndDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = customEndMillis ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { pickingEndDate = false },
            confirmButton = {
                TextButton(onClick = {
                    onCustomRangeChange(customStartMillis, state.selectedDateMillis)
                    pickingEndDate = false
                }) { Text("اختيار") }
            },
            dismissButton = {
                TextButton(onClick = { pickingEndDate = false }) { Text("إلغاء") }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
fun PaymentReceiptDialog(
    transaction: TransactionWithCustomer,
    customer: CustomerWithBalance?,
    currency: String,
    onSharePdf: () -> Unit,
    onSavePdfUri: (Uri) -> Unit,
    onPrintPdf: () -> Unit,
    onDismiss: () -> Unit,
) {
    val financial = LocalFinancialColors.current
    val savePdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            onSavePdfUri(uri)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.PictureAsPdf,
                contentDescription = null,
                tint = financial.success,
                modifier = Modifier.size(32.dp),
            )
        },
        title = {
            Text(
                "إيصال سداد رسمي (PDF)",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("رقم السند: RCP-${transaction.id}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text("العميل: ${transaction.customerName}", fontWeight = FontWeight.Bold)
                        Text(
                            "المبلغ المدفوع: ${FormatUtils.formatAmount(transaction.amount, currency)}",
                            fontWeight = FontWeight.Bold,
                            color = financial.success,
                        )
                        Text(
                            "التاريخ: ${FormatUtils.formatDateTime(transaction.transactionDate)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (customer != null) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text(
                                "الرصيد المتبقي الحالي: ${FormatUtils.formatAmount(customer.balance, currency)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onSharePdf,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("share_receipt_pdf_button"),
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("مشاركة الإيصال PDF")
                }
                OutlinedButton(
                    onClick = { savePdfLauncher.launch(PaymentReceiptPdfGenerator.buildReceiptFileName(transaction)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_receipt_pdf_button"),
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("حفظ الإيصال PDF على الجهاز")
                }
                OutlinedButton(
                    onClick = onPrintPdf,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("print_receipt_pdf_button"),
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("طباعة الإيصال (Print API)")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق")
            }
        },
    )
}

@Composable
fun BiometricLockScreen(
    storeName: String,
    errorMessage: String?,
    onUnlockClick: () -> Unit,
) {
    val financial = LocalFinancialColors.current
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("biometric_lock_screen"),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(financial.heroStart, financial.heroEnd)))
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(78.dp)
                            .clip(LocalAppIconShape.current)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "قفل البصمة أو التعرف على الوجه",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp),
                        )
                    }
                    Text(
                        text = storeName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Text(
                        text = "التطبيق محمي بالبصمة / الوجه",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "للحفاظ على سرية بيانات العملاء والديون، يرجى التحقق بالبصمة أو التعرف على الوجه للمتابعة.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    if (!errorMessage.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = financial.danger, modifier = Modifier.size(18.dp))
                                Text(errorMessage, style = MaterialTheme.typography.bodySmall, color = financial.danger)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = onUnlockClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("biometric_unlock_button"),
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("فتح القفل بالبصمة / الوجه", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

