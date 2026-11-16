package com.example.security

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

enum class BiometricAvailability(val canAuthenticate: Boolean, val messageAr: String) {
    AVAILABLE(true, "المصادقة البيومترية (البصمة / الوجه) جاهزة ومفعّلة"),
    NOT_ENROLLED(false, "لم يتم تسجيل بصمة أو قفل شاشة على هذا الجهاز بعد."),
    NO_HARDWARE(false, "الجهاز لا يحتوي على مستشعر بصمة أو وجه."),
    SECURITY_UPDATE_REQUIRED(false, "يتطلب مستشعر الأمان تحديثاً للنظام."),
    UNAVAILABLE(false, "خدمة المصادقة البيومترية غير متاحة حالياً."),
}

object BiometricAuthHelper {

    fun allowedAuthenticators(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }
    }

    fun checkAvailability(context: Context): BiometricAvailability {
        val manager = BiometricManager.from(context)
        val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            manager.canAuthenticate(allowedAuthenticators())
        } else {
            @Suppress("DEPRECATION")
            manager.canAuthenticate()
        }
        return when (status) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NOT_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAvailability.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricAvailability.SECURITY_UPDATE_REQUIRED
            else -> BiometricAvailability.UNAVAILABLE
        }
    }

    fun buildPromptInfo(
        title: String = "التحقق من الهوية",
        subtitle: String = "استخدم البصمة أو التعرف على الوجه أو رمز الجهاز لفتح مدير الديون",
        description: String = "حماية بيانات العملاء وأرصدة الديون والمدفوعات الحساسة",
        negativeButtonText: String = "إلغاء",
    ): BiometricPrompt.PromptInfo {
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setDescription(description)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(allowedAuthenticators())
        } else {
            // Pre-R devices require negativeButtonText unless deprecated setDeviceCredentialAllowed is used;
            // using BIOMETRIC_WEAK + negative button guarantees compatibility across API 24-29.
            builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            builder.setNegativeButtonText(negativeButtonText)
        }
        return builder.build()
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String = "التحقق من الهوية",
        subtitle: String = "استخدم البصمة أو الوجه للوصول إلى بيانات الديون",
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val availability = checkAvailability(activity)
        if (!availability.canAuthenticate) {
            onError(availability.messageAr)
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                val msg = when (errorCode) {
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_CANCELED -> "تم إلغاء التحقق البيومتري."
                    BiometricPrompt.ERROR_LOCKOUT,
                    BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> "تم إيقاف المحاولات مؤقتاً بسبب كثرة المحاولات الخاطئة."
                    else -> errString.toString().ifBlank { "تعذر التحقق البيومتري." }
                }
                onError(msg)
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // System prompt remains open for retry; no fatal error emitted here.
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        prompt.authenticate(buildPromptInfo(title = title, subtitle = subtitle))
    }
}
