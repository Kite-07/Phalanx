package com.kite.phalanx

import android.net.Uri
import android.provider.Telephony

data class SmsMessage(
    val sender: String,
    val body: String,
    val timestamp: Long,
    val isSentByUser: Boolean,
    val contactPhotoUri: Uri? = null,
    val unreadCount: Int = 0,
    val contactName: String? = null,
    val draftText: String? = null,
    val subscriptionId: Int = -1, // SIM subscription ID, -1 if unknown
    val isSeen: Boolean = true, // Whether message has been seen by user
    val isRead: Boolean = true // Whether message has been read by user
)

internal fun isUserMessage(type: Int): Boolean = when (type) {
    Telephony.Sms.MESSAGE_TYPE_SENT,
    Telephony.Sms.MESSAGE_TYPE_OUTBOX,
    Telephony.Sms.MESSAGE_TYPE_FAILED,
    Telephony.Sms.MESSAGE_TYPE_QUEUED -> true
    else -> false
}
