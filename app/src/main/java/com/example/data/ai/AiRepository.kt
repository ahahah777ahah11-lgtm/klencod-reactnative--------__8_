package com.example.data.ai

import com.example.data.local.CustomerWithBalance
import com.example.data.local.DashboardSummary
import com.example.data.preferences.AppSettings
import com.example.util.FormatUtils

enum class AiAutoFeatureType(
    val titleAr: String,
    val descriptionAr: String,
) {
    COLLECTION_MESSAGE(
        titleAr = "صياغة رسالة تحصيل ذكية",
        descriptionAr = "اقتراح نص رسالة تحصيل احترافية مناسبة لوضع العميل.",
    ),
    INSTALLMENT_PLAN(
        titleAr = "توليد خطة تقسيط تلقائية",
        descriptionAr = "تقسيم رصيد العميل إلى أقساط متوازنة ومواعيد مقترحة.",
    ),
    AUTO_WORKFLOW_RULE(
        titleAr = "اقتراح ميزة متابعة تلقائية",
        descriptionAr = "إنشاء قاعدة متابعة وتذكير ذكية مخصصة للحساب.",
    ),
    PORTFOLIO_BRIEF(
        titleAr = "ملخص تنفيذي ذكي للمحفظة",
        descriptionAr = "تقرير تحليلي سريع لنسبة التحصيل وأولويات المتابعة.",
    ),
}

data class AiGeneratedFeatureResult(
    val featureType: AiAutoFeatureType,
    val title: String,
    val content: String,
    val isPlaceholder: Boolean = true,
    val firebaseRequiredNotice: String = FIREBASE_REQUIRED_NOTICE_AR,
) {
    companion object {
        const val FIREBASE_REQUIRED_NOTICE_AR =
            "ملاحظة: تتطلب ميزات الذكاء الاصطناعي السحابي (Gemini) تفعيل Firebase؛ يتم حالياً عرض مخرجات نموذجية محلية."
    }
}

interface AiRepository {
    suspend fun generateAutoFeature(
        featureType: AiAutoFeatureType,
        summary: DashboardSummary,
        customer: CustomerWithBalance?,
        settings: AppSettings,
    ): AiGeneratedFeatureResult
}

/**
 * Placeholder implementation used when Firebase AI Logic is not enabled.
 * Clearly labels every generated response as a local sample output and displays
 * a Firebase-requirement notice without making any Gemini API calls.
 */
class PlaceholderAiRepository : AiRepository {
    override suspend fun generateAutoFeature(
        featureType: AiAutoFeatureType,
        summary: DashboardSummary,
        customer: CustomerWithBalance?,
        settings: AppSettings,
    ): AiGeneratedFeatureResult {
        val cur = settings.currencySymbol
        val storeName = settings.storeName.ifBlank { "المتجر" }
        val targetName = customer?.name ?: "أبرز العملاء المدينين"
        val targetBalance = customer?.balance ?: summary.netOutstandingBalance

        val content = when (featureType) {
            AiAutoFeatureType.COLLECTION_MESSAGE -> {
                if (customer != null) {
                    buildString {
                        appendLine("[رد نموذجي محلي - Placeholder]")
                        appendLine("السلام عليكم أ/ ${customer.name}، تحية طيبة من $storeName.")
                        if (customer.balance > 0.001) {
                            appendLine("نود تذكيركم بلطف برصيد حسابكم المستحق (${FormatUtils.formatAmount(customer.balance, cur)}) من إجمالي تعاملات (${FormatUtils.formatAmount(customer.totalDebts, cur)}).")
                            append("نرجو التنسيق للسداد أو إرسال رقم مرجع الحوالة عبر هذا الرقم، وشاكرين تعاونكم الدائم.")
                        } else {
                            append("نشكر التزامكم الكريم بسداد كافة المستحقات؛ حسابكم مسدد بالكامل ونتطلع لخدمتكم مجدداً.")
                        }
                    }
                } else {
                    "[رد نموذجي محلي - Placeholder]\nالسلام عليكم، تذكير ودي من $storeName بضرورة متابعة سداد الأرصدة المستحقة لهذا الأسبوع. إجمالي الرصيد القائم حالياً: ${FormatUtils.formatAmount(summary.netOutstandingBalance, cur)}."
                }
            }
            AiAutoFeatureType.INSTALLMENT_PLAN -> {
                val safeBalance = targetBalance.coerceAtLeast(0.0)
                if (safeBalance <= 0.001) {
                    "[رد نموذجي محلي - Placeholder]\nلا يوجد رصيد مستحق على ($targetName) لإنشاء خطة تقسيط."
                } else {
                    val part = safeBalance / 3.0
                    val partText = FormatUtils.formatAmount(part, cur)
                    buildString {
                        appendLine("[خطة تقسيط نموذجية - Placeholder] للعميل: $targetName")
                        appendLine("الرصيد المستحق: ${FormatUtils.formatAmount(safeBalance, cur)}")
                        appendLine("• القسط الأول (خلال 7 أيام): $partText")
                        appendLine("• القسط الثاني (خلال 21 يوماً): $partText")
                        append("• القسط النهائي (خلال 35 يوماً): $partText")
                    }
                }
            }
            AiAutoFeatureType.AUTO_WORKFLOW_RULE -> {
                val triggerDays = settings.overdueDaysThreshold
                buildString {
                    appendLine("[ميزة أتمتة مقترحة - Placeholder] لحساب: $targetName")
                    appendLine("• الشرط: تجاوز مهلة الاستحقاق ($triggerDays يوماً) أو وصول الرصيد إلى ${FormatUtils.formatAmount(targetBalance, cur)}.")
                    appendLine("• الإجراء المقترح 1: جدولة تذكير WorkManager قبل الموعد بـ 48 ساعة.")
                    append("• الإجراء المقترح 2: تجهيز رسالة SMS متابعة مع ملخص الرصيد ومشاركتها عند الطلب.")
                }
            }
            AiAutoFeatureType.PORTFOLIO_BRIEF -> {
                val rate = if (summary.totalDebts > 0.0) {
                    ((summary.totalPayments / summary.totalDebts) * 100.0).toInt().coerceIn(0, 100)
                } else 100
                buildString {
                    appendLine("[ملخص نموذجي محلي - Placeholder] محفظة $storeName")
                    appendLine("• العملاء المدينون النشطون: ${summary.activeDebtorsCount} من إجمالي ${summary.totalCustomers} (التنبيهات المتأخرة: ${summary.overdueRemindersCount})")
                    appendLine("• صافي الديون القائمة: ${FormatUtils.formatAmount(summary.netOutstandingBalance, cur)}")
                    append("• نسبة التحصيل الحالية: $rate% — يُنصح بإرسال تذكير جماعي للعملاء المتأخرين وتأكيد الحوالات المعلقة (${summary.pendingTransfersCount}).")
                }
            }
        }

        return AiGeneratedFeatureResult(
            featureType = featureType,
            title = featureType.titleAr,
            content = content,
            isPlaceholder = true,
            firebaseRequiredNotice = AiGeneratedFeatureResult.FIREBASE_REQUIRED_NOTICE_AR,
        )
    }
}
