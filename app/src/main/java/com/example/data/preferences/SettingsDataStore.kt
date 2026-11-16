package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.AppBackgroundStyle
import com.example.ui.theme.AppColorPalette
import com.example.ui.theme.AppIconShape
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "smart_debt_settings")

data class AppSettings(
    val storeName: String = "متجر النخبة التجاري",
    val contactPhone: String = "",
    val currencySymbol: String = "ر.ي",
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val colorPalette: AppColorPalette = AppColorPalette.OFFICIAL_NAVY_TEAL,
    val backgroundStyle: AppBackgroundStyle = AppBackgroundStyle.THEME_DEFAULT,
    val iconShape: AppIconShape = AppIconShape.SQUIRCLE,
    val allowOverpayment: Boolean = false,
    val duplicateWindowMinutes: Int = 5,
    val workManagerRemindersEnabled: Boolean = false,
    val overdueDaysThreshold: Int = 14,
    val biometricLockEnabled: Boolean = false,
    val autoUpdateCheckEnabled: Boolean = true,
    val updateManifestUrl: String = "",
)

class SettingsDataStore(private val context: Context) {
    private object Keys {
        val STORE_NAME = stringPreferencesKey("store_name")
        val CONTACT_PHONE = stringPreferencesKey("contact_phone")
        val CURRENCY_SYMBOL = stringPreferencesKey("currency_symbol")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val COLOR_PALETTE = stringPreferencesKey("color_palette")
        val BACKGROUND_STYLE = stringPreferencesKey("background_style")
        val ICON_SHAPE = stringPreferencesKey("icon_shape")
        val ALLOW_OVERPAYMENT = booleanPreferencesKey("allow_overpayment")
        val DUPLICATE_WINDOW_MINUTES = intPreferencesKey("duplicate_window_minutes")
        val WORKMANAGER_REMINDERS = booleanPreferencesKey("workmanager_reminders")
        val OVERDUE_DAYS_THRESHOLD = intPreferencesKey("overdue_days_threshold")
        val BIOMETRIC_LOCK_ENABLED = booleanPreferencesKey("biometric_lock_enabled")
        val AUTO_UPDATE_CHECK_ENABLED = booleanPreferencesKey("auto_update_check_enabled")
        val UPDATE_MANIFEST_URL = stringPreferencesKey("update_manifest_url")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            storeName = prefs[Keys.STORE_NAME] ?: "متجر النخبة التجاري",
            contactPhone = prefs[Keys.CONTACT_PHONE] ?: "",
            currencySymbol = prefs[Keys.CURRENCY_SYMBOL] ?: "ر.ي",
            themeMode = AppThemeMode.entries.find { it.name == prefs[Keys.THEME_MODE] }
                ?: AppThemeMode.SYSTEM,
            colorPalette = AppColorPalette.entries.find { it.name == prefs[Keys.COLOR_PALETTE] }
                ?: AppColorPalette.OFFICIAL_NAVY_TEAL,
            backgroundStyle = AppBackgroundStyle.entries.find { it.name == prefs[Keys.BACKGROUND_STYLE] }
                ?: AppBackgroundStyle.THEME_DEFAULT,
            iconShape = AppIconShape.entries.find { it.name == prefs[Keys.ICON_SHAPE] }
                ?: AppIconShape.SQUIRCLE,
            allowOverpayment = prefs[Keys.ALLOW_OVERPAYMENT] ?: false,
            duplicateWindowMinutes = prefs[Keys.DUPLICATE_WINDOW_MINUTES] ?: 5,
            workManagerRemindersEnabled = prefs[Keys.WORKMANAGER_REMINDERS] ?: false,
            overdueDaysThreshold = prefs[Keys.OVERDUE_DAYS_THRESHOLD] ?: 14,
            biometricLockEnabled = prefs[Keys.BIOMETRIC_LOCK_ENABLED] ?: false,
            autoUpdateCheckEnabled = prefs[Keys.AUTO_UPDATE_CHECK_ENABLED] ?: true,
            updateManifestUrl = prefs[Keys.UPDATE_MANIFEST_URL] ?: "",
        )
    }

    suspend fun getSettingsOnce(): AppSettings = settingsFlow.first()

    suspend fun updateStoreInfo(storeName: String, contactPhone: String, currencySymbol: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.STORE_NAME] = storeName.trim().ifBlank { "متجري" }
            prefs[Keys.CONTACT_PHONE] = contactPhone.trim()
            prefs[Keys.CURRENCY_SYMBOL] = currencySymbol.trim().ifBlank { "ر.ي" }
        }
    }

    suspend fun updateTheme(
        themeMode: AppThemeMode? = null,
        colorPalette: AppColorPalette? = null,
        backgroundStyle: AppBackgroundStyle? = null,
        iconShape: AppIconShape? = null,
    ) {
        context.dataStore.edit { prefs ->
            if (themeMode != null) {
                prefs[Keys.THEME_MODE] = themeMode.name
            }
            if (colorPalette != null) {
                prefs[Keys.COLOR_PALETTE] = colorPalette.name
            }
            if (backgroundStyle != null) {
                prefs[Keys.BACKGROUND_STYLE] = backgroundStyle.name
            }
            if (iconShape != null) {
                prefs[Keys.ICON_SHAPE] = iconShape.name
            }
        }
    }

    suspend fun updateAccountingRules(
        allowOverpayment: Boolean,
        duplicateWindowMinutes: Int,
        overdueDaysThreshold: Int,
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ALLOW_OVERPAYMENT] = allowOverpayment
            prefs[Keys.DUPLICATE_WINDOW_MINUTES] = duplicateWindowMinutes.coerceIn(1, 1440)
            prefs[Keys.OVERDUE_DAYS_THRESHOLD] = overdueDaysThreshold.coerceIn(1, 365)
        }
    }

    suspend fun updateWorkManagerReminders(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WORKMANAGER_REMINDERS] = enabled
        }
    }

    suspend fun updateBiometricLock(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BIOMETRIC_LOCK_ENABLED] = enabled
        }
    }

    suspend fun updateAutoUpdateSettings(enabled: Boolean, manifestUrl: String? = null) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUTO_UPDATE_CHECK_ENABLED] = enabled
            if (manifestUrl != null) {
                prefs[Keys.UPDATE_MANIFEST_URL] = manifestUrl.trim()
            }
        }
    }

    suspend fun applyRestoredSettings(settings: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.STORE_NAME] = settings.storeName
            prefs[Keys.CONTACT_PHONE] = settings.contactPhone
            prefs[Keys.CURRENCY_SYMBOL] = settings.currencySymbol
            prefs[Keys.THEME_MODE] = settings.themeMode.name
            prefs[Keys.COLOR_PALETTE] = settings.colorPalette.name
            prefs[Keys.BACKGROUND_STYLE] = settings.backgroundStyle.name
            prefs[Keys.ICON_SHAPE] = settings.iconShape.name
            prefs[Keys.ALLOW_OVERPAYMENT] = settings.allowOverpayment
            prefs[Keys.DUPLICATE_WINDOW_MINUTES] = settings.duplicateWindowMinutes
            prefs[Keys.WORKMANAGER_REMINDERS] = settings.workManagerRemindersEnabled
            prefs[Keys.OVERDUE_DAYS_THRESHOLD] = settings.overdueDaysThreshold
            prefs[Keys.BIOMETRIC_LOCK_ENABLED] = settings.biometricLockEnabled
            prefs[Keys.AUTO_UPDATE_CHECK_ENABLED] = settings.autoUpdateCheckEnabled
            prefs[Keys.UPDATE_MANIFEST_URL] = settings.updateManifestUrl
        }
    }
}
