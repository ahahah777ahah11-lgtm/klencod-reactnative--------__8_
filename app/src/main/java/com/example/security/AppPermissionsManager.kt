package com.example.security

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.ContactsContract
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.io.File
import java.io.OutputStream

enum class AppCapabilityKind(
    val titleAr: String,
    val descriptionAr: String,
    val permission: String?,
) {
    FILES_AND_MEDIA(
        titleAr = "الوصول للملفات والصور (SAF + Photo Picker)",
        descriptionAr = "استيراد وتصدير قواعد البيانات والصور بأمان دون الحاجة لصلاحيات تخزين عريضة.",
        permission = null,
    ),
    STORAGE_FOLDERS(
        titleAr = "الوصول الدائم لمجلدات ذاكرة الهاتف (SAF Tree)",
        descriptionAr = "منح صلاحية مستمرة لقراءة ملفات القواعد والنسخ الاحتياطية وحفظها داخل مجلدات الجهاز.",
        permission = null,
    ),
    NOTIFICATIONS(
        titleAr = "الإشعارات المحلية",
        descriptionAr = "تنبيهات مواعيد الاستحقاق والتأخيرات على شريط الإشعارات.",
        permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else null,
    ),
    REMINDERS(
        titleAr = "تذكيرات المهام المجدولة (WorkManager)",
        descriptionAr = "فحص دوري لمواعيد سداد الديون وتنبيهات المتأخرين.",
        permission = null,
    ),
    CONTACTS(
        titleAr = "جهات الاتصال",
        descriptionAr = "استيراد اسم العميل ورقم هاتفه مباشرة من سجل الهاتف.",
        permission = Manifest.permission.READ_CONTACTS,
    ),
    CAMERA(
        titleAr = "الكاميرا",
        descriptionAr = "التقاط صورة فاتورة أو سند إرفاق للعمليات والعملاء.",
        permission = Manifest.permission.CAMERA,
    ),
    LOCATION(
        titleAr = "خدمات الموقع",
        descriptionAr = "إرفاق إحداثيات الموقع الحالية اختيارياً عند تسجيل تحصيل ميداني.",
        permission = Manifest.permission.ACCESS_FINE_LOCATION,
    ),
    SMS(
        titleAr = "الرسائل النصية المباشرة (SMS)",
        descriptionAr = "إرسال تنبيهات الديون مباشرة أو فتح تطبيق الرسائل الرسمي.",
        permission = Manifest.permission.SEND_SMS,
    ),
    READ_SMS(
        titleAr = "قراءة الرسائل الواردة (READ_SMS)",
        descriptionAr = "قراءة إشعارات الحوالات الواردة في صندوق SMS لاستيرادها تلقائياً.",
        permission = Manifest.permission.READ_SMS,
    ),
    BIOMETRIC(
        titleAr = "قفل التطبيق بالبصمة / الوجه",
        descriptionAr = "حماية الديون وكشوفات الحساب عبر BiometricPrompt.",
        permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Manifest.permission.USE_BIOMETRIC
        } else null,
    ),
    PRINTING(
        titleAr = "طباعة الكشوفات والإيصالات PDF",
        descriptionAr = "الربط المباشر مع خدمة الطباعة النظامية PrintManager لطباعة كشوفات الحسابات.",
        permission = null,
    ),
}

data class CapabilityStatus(
    val kind: AppCapabilityKind,
    val isGranted: Boolean,
    val requiresRuntimeRequest: Boolean,
)

data class AuthorizedStorageFolder(
    val treeUri: Uri,
    val displayLabel: String,
    val canWrite: Boolean,
    val persistedTime: Long,
)

data class StorageTreeFileEntry(
    val documentUri: Uri,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val relativePath: String,
)

data class PickedContactInfo(
    val name: String,
    val phone: String,
)

data class CapturedLocationInfo(
    val latitude: Double,
    val longitude: Double,
    val provider: String,
) {
    fun toFormattedTag(): String =
        "الموقع: %.5f, %.5f".format(java.util.Locale.US, latitude, longitude)
}

object AppPermissionsManager {

    private val importableExtensions = setOf(
        "db", "sqlite", "sqlite3", "db3", "csv", "xlsx", "xls", "json", "txt",
    )

    fun isSupportedImportFileName(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in importableExtensions
    }

    fun formatTreeFolderLabel(treeUri: Uri): String {
        val rawId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: treeUri.lastPathSegment
            ?: treeUri.toString()
        val cleaned = rawId
            .replace("primary:", "الذاكرة الداخلية / ")
            .replace("home:", "المستندات / ")
            .trim()
        return cleaned.ifBlank { "مجلد ذاكرة الهاتف" }
    }

    fun allRuntimePermissions(): Array<String> {
        val perms = mutableListOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return perms.toTypedArray()
    }

    fun takePersistableReadPermission(context: Context, uri: Uri): Boolean {
        return runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            true
        }.getOrDefault(false)
    }

    fun takePersistableTreePermission(context: Context, treeUri: Uri): Boolean {
        val readWriteFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val rwOk = runCatching {
            context.contentResolver.takePersistableUriPermission(treeUri, readWriteFlags)
            true
        }.getOrDefault(false)
        if (rwOk) return true
        return takePersistableReadPermission(context, treeUri)
    }

    fun releasePersistableTreePermission(context: Context, treeUri: Uri): Boolean {
        return runCatching {
            context.contentResolver.releasePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            true
        }.getOrDefault(false)
    }

    fun getPersistedStorageFolders(context: Context): List<AuthorizedStorageFolder> {
        return runCatching {
            context.contentResolver.persistedUriPermissions
                .filter { perm ->
                    perm.isReadPermission && runCatching {
                        DocumentsContract.isTreeUri(perm.uri)
                    }.getOrDefault(false)
                }
                .map { perm ->
                    AuthorizedStorageFolder(
                        treeUri = perm.uri,
                        displayLabel = formatTreeFolderLabel(perm.uri),
                        canWrite = perm.isWritePermission,
                        persistedTime = perm.persistedTime,
                    )
                }
                .sortedByDescending { it.persistedTime }
        }.getOrDefault(emptyList())
    }

    fun hasStorageFoldersPermission(context: Context): Boolean {
        return getPersistedStorageFolders(context).isNotEmpty()
    }

    fun scanStorageTreeFiles(
        context: Context,
        treeUri: Uri,
        maxDepth: Int = 2,
        maxFiles: Int = 80,
    ): List<StorageTreeFileEntry> {
        val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return emptyList()
        val results = mutableListOf<StorageTreeFileEntry>()
        val folderPrefix = formatTreeFolderLabel(treeUri)

        fun scanNode(docId: String, pathPrefix: String, depth: Int) {
            if (results.size >= maxFiles || depth > maxDepth) return
            val childrenUri = runCatching {
                DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            }.getOrNull() ?: return

            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            )

            runCatching {
                context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                    val subDirs = mutableListOf<Pair<String, String>>()
                    while (cursor.moveToNext() && results.size < maxFiles) {
                        val childId = if (idIdx >= 0) cursor.getString(idIdx).orEmpty() else continue
                        val displayName = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty() else continue
                        val mimeType = if (mimeIdx >= 0) cursor.getString(mimeIdx).orEmpty() else ""
                        val sizeBytes = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L
                        val lastMod = if (modIdx >= 0 && !cursor.isNull(modIdx)) cursor.getLong(modIdx) else 0L

                        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (depth < maxDepth) {
                                subDirs += childId to "$pathPrefix/$displayName"
                            }
                        } else if (isSupportedImportFileName(displayName)) {
                            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                            results += StorageTreeFileEntry(
                                documentUri = docUri,
                                displayName = displayName,
                                mimeType = mimeType,
                                sizeBytes = sizeBytes,
                                lastModified = lastMod,
                                relativePath = pathPrefix,
                            )
                        }
                    }

                    for ((subId, subPath) in subDirs) {
                        if (results.size >= maxFiles) break
                        scanNode(subId, subPath, depth + 1)
                    }
                }
            }
        }

        scanNode(rootDocId, folderPrefix, depth = 0)
        return results.sortedByDescending { it.lastModified }
    }

    fun writeDocumentInAuthorizedFolder(
        context: Context,
        treeUri: Uri,
        fileName: String,
        mimeType: String,
        writer: (OutputStream) -> Unit,
    ): Result<Uri> {
        return runCatching {
            val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
            val createdUri = DocumentsContract.createDocument(
                context.contentResolver,
                parentDocUri,
                mimeType,
                fileName,
            ) ?: error("تعذر إنشاء الملف داخل المجلد المصرح به")
            context.contentResolver.openOutputStream(createdUri)?.use { out ->
                writer(out)
            } ?: error("تعذر فتح مجرى الكتابة للملف")
            createdUri
        }
    }

    fun isPrintServiceAvailable(context: Context): Boolean {
        return runCatching {
            context.getSystemService(Context.PRINT_SERVICE) != null
        }.getOrDefault(false)
    }

    fun isPermissionGranted(context: Context, permission: String): Boolean {
        return runCatching {
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    fun hasLocationPermission(context: Context): Boolean {
        return isPermissionGranted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            isPermissionGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isPermissionGranted(context, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }
    }

    fun getCapabilityStatuses(context: Context, workManagerRemindersEnabled: Boolean): List<CapabilityStatus> {
        return AppCapabilityKind.entries.map { kind ->
            val granted = when (kind) {
                AppCapabilityKind.FILES_AND_MEDIA -> true
                AppCapabilityKind.STORAGE_FOLDERS -> hasStorageFoldersPermission(context)
                AppCapabilityKind.REMINDERS -> workManagerRemindersEnabled
                AppCapabilityKind.NOTIFICATIONS -> hasNotificationPermission(context)
                AppCapabilityKind.LOCATION -> hasLocationPermission(context)
                AppCapabilityKind.PRINTING -> isPrintServiceAvailable(context)
                else -> kind.permission?.let { isPermissionGranted(context, it) } ?: true
            }
            CapabilityStatus(
                kind = kind,
                isGranted = granted,
                requiresRuntimeRequest = kind.permission != null && kind != AppCapabilityKind.BIOMETRIC,
            )
        }
    }

    fun missingRuntimePermissions(context: Context): Array<String> {
        return allRuntimePermissions().filter { !isPermissionGranted(context, it) }.toTypedArray()
    }

    fun openAppPermissionSettings(context: Context): Boolean {
        return runCatching {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun openNotificationSettings(context: Context): Boolean {
        return runCatching {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
            }.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    fun triggerHapticAlert(context: Context, durationMs: Long = 45L): Boolean {
        return runCatching {
            if (!isPermissionGranted(context, Manifest.permission.VIBRATE)) return@runCatching false
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(durationMs)
                }
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }

    fun readContactFromUri(context: Context, contactUri: Uri): PickedContactInfo? {
        return runCatching {
            val resolver = context.contentResolver
            // Primary path: direct Phone.CONTENT_URI lookup
            val direct = runCatching {
                resolver.query(
                    contactUri,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val name = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty().trim() else ""
                        val rawNum = if (numIdx >= 0) cursor.getString(numIdx).orEmpty().trim() else ""
                        if (rawNum.isNotBlank() || name.isNotBlank()) {
                            PickedContactInfo(name = name, phone = rawNum)
                        } else null
                    } else null
                }
            }.getOrNull()
            if (direct != null && direct.phone.isNotBlank()) return@runCatching direct

            // Fallback path for OEM contact apps that return Contacts.CONTENT_URI
            resolver.query(
                contactUri,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ),
                null,
                null,
                null,
            )?.use { c ->
                if (!c.moveToFirst()) return@use direct
                val idIdx = c.getColumnIndex(ContactsContract.Contacts._ID)
                val nameIdx = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                val contactId = if (idIdx >= 0) c.getString(idIdx) else null
                val fallbackName = if (nameIdx >= 0) c.getString(nameIdx).orEmpty().trim() else direct?.name.orEmpty()
                var fallbackPhone = direct?.phone.orEmpty()
                if (!contactId.isNullOrBlank() && fallbackPhone.isBlank()) {
                    resolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                        arrayOf(contactId),
                        null,
                    )?.use { pc ->
                        if (pc.moveToFirst()) {
                            val pIdx = pc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                            if (pIdx >= 0) fallbackPhone = pc.getString(pIdx).orEmpty().trim()
                        }
                    }
                }
                if (fallbackName.isNotBlank() || fallbackPhone.isNotBlank()) {
                    PickedContactInfo(name = fallbackName, phone = fallbackPhone)
                } else null
            } ?: direct
        }.getOrNull()
    }

    fun saveCapturedReceiptPhoto(context: Context, bitmap: Bitmap, prefix: String = "receipt"): Result<File> {
        return runCatching {
            val dir = File(context.filesDir, "attachments").apply { mkdirs() }
            val file = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            file
        }
    }

    fun copyPhotoFromPickerUri(context: Context, uri: Uri, prefix: String = "gallery"): Result<File> {
        return runCatching {
            val dir = File(context.filesDir, "attachments").apply { mkdirs() }
            val file = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: error("تعذر قراءة الصورة المحددة")
            file
        }
    }

    @SuppressLint("MissingPermission")
    fun getBestLastKnownLocation(context: Context): CapturedLocationInfo? {
        if (!hasLocationPermission(context)) return null
        return runCatching {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER,
            )
            var best: Location? = null
            for (p in providers) {
                val loc = runCatching { lm.getLastKnownLocation(p) }.getOrNull() ?: continue
                if (best == null || loc.time > best.time) {
                    best = loc
                }
            }
            best?.let {
                CapturedLocationInfo(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    provider = it.provider ?: "location",
                )
            }
        }.getOrNull()
    }
}
