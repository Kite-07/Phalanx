package com.kite.phalanx

import android.net.Uri
import android.provider.Telephony

/**
 * Represents an attachment in an MMS message
 */
data class MessageAttachment(
    val id: String,
    val contentType: String, // MIME type (e.g., "image/jpeg", "video/mp4", "audio/mpeg")
    val uri: Uri, // Content URI for the attachment
    val fileName: String? = null,
    val fileSize: Long = 0 // Size in bytes
) {
    val isImage: Boolean
        get() = contentType.startsWith("image/")

    val isVideo: Boolean
        get() = contentType.startsWith("video/")

    val isAudio: Boolean
        get() = contentType.startsWith("audio/")

    val isVCard: Boolean
        get() = contentType == "text/x-vcard" || contentType == "text/vcard"
}

/**
 * Message delivery status for sent messages
 */
enum class DeliveryStatus {
    PENDING,    // Message is queued or being sent
    SENT,       // Message was sent successfully (not necessarily delivered)
    DELIVERED,  // Message was delivered to recipient
    FAILED      // Message failed to send or deliver
}

/**
 * Represents an SMS or MMS message
 */
data class SmsMessage(
    val id: Long = -1, // Message ID from the system database
    val threadId: Long = -1, // Thread ID this message belongs to
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
    val isRead: Boolean = true, // Whether message has been read by user
    val isMms: Boolean = false, // true for MMS, false for SMS
    val attachments: List<MessageAttachment> = emptyList(), // MMS attachments
    val messageType: Int = Telephony.Sms.MESSAGE_TYPE_INBOX, // Message type from Telephony API
    val status: Int = -1, // Delivery status from Telephony.Sms.STATUS column

    // Reply metadata (for messages that are replies to other messages)
    val replyToMessageId: Long? = null, // ID of the message being replied to
    val replyToSnippet: String? = null, // Cached snippet of the original message
    val replyToSender: String? = null // Sender name/number of the original message
) {
    /**
     * Get the delivery status for this message
     */
    val deliveryStatus: DeliveryStatus
        get() = when {
            // For received messages, status doesn't apply
            !isSentByUser -> DeliveryStatus.DELIVERED

            // Check message type first
            messageType == Telephony.Sms.MESSAGE_TYPE_FAILED -> DeliveryStatus.FAILED
            messageType == Telephony.Sms.MESSAGE_TYPE_OUTBOX ||
            messageType == Telephony.Sms.MESSAGE_TYPE_QUEUED -> DeliveryStatus.PENDING

            // For sent messages, check delivery status
            status == 0 -> DeliveryStatus.DELIVERED  // STATUS_COMPLETE
            status == 64 -> DeliveryStatus.FAILED     // STATUS_FAILED
            status == 32 -> DeliveryStatus.SENT       // STATUS_PENDING (sent but not delivered)
            messageType == Telephony.Sms.MESSAGE_TYPE_SENT -> DeliveryStatus.SENT

            else -> DeliveryStatus.PENDING
        }

    /**
     * Check if this message can be retried (i.e., it failed to send)
     */
    val canRetry: Boolean
        get() = isSentByUser && deliveryStatus == DeliveryStatus.FAILED
}

internal fun isUserMessage(type: Int): Boolean = when (type) {
    Telephony.Sms.MESSAGE_TYPE_SENT,
    Telephony.Sms.MESSAGE_TYPE_OUTBOX,
    Telephony.Sms.MESSAGE_TYPE_FAILED,
    Telephony.Sms.MESSAGE_TYPE_QUEUED -> true
    else -> false
}
