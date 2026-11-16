package com.example.data.local

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromTransactionType(value: TransactionType): String = value.name

    @TypeConverter
    fun toTransactionType(value: String): TransactionType =
        TransactionType.entries.find { it.name == value } ?: TransactionType.DEBT

    @TypeConverter
    fun fromReminderStatus(value: ReminderStatus): String = value.name

    @TypeConverter
    fun toReminderStatus(value: String): ReminderStatus =
        ReminderStatus.entries.find { it.name == value } ?: ReminderStatus.PENDING

    @TypeConverter
    fun fromTransferCompany(value: TransferCompany): String = value.name

    @TypeConverter
    fun toTransferCompany(value: String): TransferCompany =
        TransferCompany.entries.find { it.name == value } ?: TransferCompany.AL_NAJM

    @TypeConverter
    fun fromTransferStatus(value: TransferStatus): String = value.name

    @TypeConverter
    fun toTransferStatus(value: String): TransferStatus =
        TransferStatus.entries.find { it.name == value } ?: TransferStatus.PENDING

    @TypeConverter
    fun fromMessageStatus(value: MessageStatus): String = value.name

    @TypeConverter
    fun toMessageStatus(value: String): MessageStatus =
        MessageStatus.entries.find { it.name == value } ?: MessageStatus.INTENT_OPENED
}
