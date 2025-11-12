package com.kite.phalanx

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import java.util.LinkedHashMap
import timber.log.Timber

/**
 * Utility for loading unified SMS and MMS messages
 */
object MessageLoader {
    /**
     * Load all messages (SMS + MMS) for a specific sender/thread
     * Returns messages sorted by timestamp (oldest first)
     */
    fun loadThreadMessages(context: Context, sender: String): List<SmsMessage> {
        val messages = mutableListOf<SmsMessage>()

        // Load SMS messages
        val smsCursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.SUBSCRIPTION_ID,
                Telephony.Sms.READ,
                Telephony.Sms.SEEN,
                Telephony.Sms.STATUS
            ),
            "${Telephony.Sms.ADDRESS}=?",
            arrayOf(sender),
            "${Telephony.Sms.DATE} ASC"
        )

        smsCursor?.use {
            val idxId = it.getColumnIndex(Telephony.Sms._ID)
            val idxThreadId = it.getColumnIndex(Telephony.Sms.THREAD_ID)
            val idxAddress = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val idxBody = it.getColumnIndex(Telephony.Sms.BODY)
            val idxDate = it.getColumnIndex(Telephony.Sms.DATE)
            val idxType = it.getColumnIndex(Telephony.Sms.TYPE)
            val idxSubId = it.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID)
            val idxRead = it.getColumnIndex(Telephony.Sms.READ)
            val idxSeen = it.getColumnIndex(Telephony.Sms.SEEN)
            val idxStatus = it.getColumnIndex(Telephony.Sms.STATUS)

            while (it.moveToNext()) {
                try {
                    val id = if (idxId >= 0) it.getLong(idxId) else -1
                    val threadId = if (idxThreadId >= 0) it.getLong(idxThreadId) else -1
                    val address = it.getString(idxAddress) ?: ""
                    val body = it.getString(idxBody) ?: ""
                    val timestamp = it.getLong(idxDate)
                    val type = it.getInt(idxType)
                    val subId = if (idxSubId >= 0) it.getInt(idxSubId) else -1
                    val read = if (idxRead >= 0) it.getInt(idxRead) == 1 else true
                    val seen = if (idxSeen >= 0) it.getInt(idxSeen) == 1 else true
                    val status = if (idxStatus >= 0) it.getInt(idxStatus) else -1

                    messages.add(
                        SmsMessage(
                            id = id,
                            threadId = threadId,
                            sender = address,
                            body = body,
                            timestamp = timestamp,
                            isSentByUser = isUserMessage(type),
                            subscriptionId = subId,
                            isRead = read,
                            isSeen = seen,
                            isMms = false,
                            messageType = type,
                            status = status
                        )
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Error parsing SMS")
                }
            }
        }

        // Load MMS messages - we need to find MMS messages from this sender
        // First, get the thread ID from SMS (if any)
        val threadId = messages.firstOrNull()?.threadId ?: -1

        if (threadId > 0) {
            loadMmsMessagesForThread(context, threadId, sender, messages)
        }

        // Sort all messages by timestamp
        return messages.sortedBy { it.timestamp }
    }

    /**
     * Load MMS messages for a specific thread
     */
    private fun loadMmsMessagesForThread(
        context: Context,
        threadId: Long,
        sender: String,
        messages: MutableList<SmsMessage>
    ) {
        val mmsCursor = context.contentResolver.query(
            Telephony.Mms.CONTENT_URI,
            arrayOf(
                Telephony.Mms._ID,
                Telephony.Mms.THREAD_ID,
                Telephony.Mms.DATE,
                Telephony.Mms.MESSAGE_BOX,
                Telephony.Mms.SUBSCRIPTION_ID,
                Telephony.Mms.READ,
                Telephony.Mms.SEEN
            ),
            "${Telephony.Mms.THREAD_ID}=?",
            arrayOf(threadId.toString()),
            "${Telephony.Mms.DATE} ASC"
        )

        mmsCursor?.use {
            val idxId = it.getColumnIndex(Telephony.Mms._ID)
            val idxThreadId = it.getColumnIndex(Telephony.Mms.THREAD_ID)
            val idxDate = it.getColumnIndex(Telephony.Mms.DATE)
            val idxBox = it.getColumnIndex(Telephony.Mms.MESSAGE_BOX)
            val idxSubId = it.getColumnIndex(Telephony.Mms.SUBSCRIPTION_ID)
            val idxRead = it.getColumnIndex(Telephony.Mms.READ)
            val idxSeen = it.getColumnIndex(Telephony.Mms.SEEN)

            while (it.moveToNext()) {
                try {
                    val id = if (idxId >= 0) it.getLong(idxId) else -1
                    val mmsThreadId = if (idxThreadId >= 0) it.getLong(idxThreadId) else -1
                    val date = if (idxDate >= 0) it.getLong(idxDate) * 1000L else 0L // Convert to milliseconds
                    val messageBox = if (idxBox >= 0) it.getInt(idxBox) else Telephony.Mms.MESSAGE_BOX_INBOX
                    val subId = if (idxSubId >= 0) it.getInt(idxSubId) else -1
                    val read = if (idxRead >= 0) it.getInt(idxRead) == 1 else true
                    val seen = if (idxSeen >= 0) it.getInt(idxSeen) == 1 else true

                    // Parse full MMS details
                    val mmsUri = Uri.parse("content://mms/$id")
                    val mmsDetails = MmsHelper.parseMmsMessage(context, mmsUri)

                    if (mmsDetails != null) {
                        // Only include if sender matches (for received) or if it's sent by user
                        val isSent = messageBox == Telephony.Mms.MESSAGE_BOX_SENT ||
                                messageBox == Telephony.Mms.MESSAGE_BOX_OUTBOX

                        if (isSent || mmsDetails.sender == sender ||
                            mmsDetails.sender.replace(Regex("[^0-9]"), "") == sender.replace(Regex("[^0-9]"), "")) {

                            messages.add(
                                SmsMessage(
                                    id = id,
                                    threadId = mmsThreadId,
                                    sender = if (isSent) sender else mmsDetails.sender,
                                    body = mmsDetails.body,
                                    timestamp = date,
                                    isSentByUser = isSent,
                                    subscriptionId = subId,
                                    isRead = read,
                                    isSeen = seen,
                                    isMms = true,
                                    attachments = mmsDetails.attachments,
                                    messageType = messageBox
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error parsing MMS")
                }
            }
        }
    }

    /**
     * Load latest message from each conversation (for conversation list)
     * Returns a map of sender -> latest message
     */
    fun loadConversationList(context: Context): LinkedHashMap<String, SmsMessage> {
        val latestByAddress = LinkedHashMap<String, SmsMessage>()

        // Load all SMS messages (newest first)
        val smsCursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.SUBSCRIPTION_ID,
                Telephony.Sms.READ,
                Telephony.Sms.SEEN,
                Telephony.Sms.STATUS
            ),
            null,
            null,
            "${Telephony.Sms.DATE} DESC"
        )

        smsCursor?.use {
            val idxId = it.getColumnIndex(Telephony.Sms._ID)
            val idxThreadId = it.getColumnIndex(Telephony.Sms.THREAD_ID)
            val idxAddress = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val idxBody = it.getColumnIndex(Telephony.Sms.BODY)
            val idxDate = it.getColumnIndex(Telephony.Sms.DATE)
            val idxType = it.getColumnIndex(Telephony.Sms.TYPE)
            val idxSubId = it.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID)
            val idxRead = it.getColumnIndex(Telephony.Sms.READ)
            val idxSeen = it.getColumnIndex(Telephony.Sms.SEEN)
            val idxStatus = it.getColumnIndex(Telephony.Sms.STATUS)

            while (it.moveToNext()) {
                try {
                    val address = it.getString(idxAddress)?.takeIf { addr -> addr.isNotBlank() }
                        ?: continue

                    // Skip if we already have a newer message from this address
                    if (latestByAddress.containsKey(address)) continue

                    // Skip blocked numbers
                    if (SmsOperations.isNumberBlocked(context, address)) continue

                    val id = if (idxId >= 0) it.getLong(idxId) else -1
                    val threadId = if (idxThreadId >= 0) it.getLong(idxThreadId) else -1
                    val body = it.getString(idxBody) ?: ""
                    val timestamp = it.getLong(idxDate)
                    val type = it.getInt(idxType)
                    val subId = if (idxSubId >= 0) it.getInt(idxSubId) else -1
                    val read = if (idxRead >= 0) it.getInt(idxRead) == 1 else true
                    val seen = if (idxSeen >= 0) it.getInt(idxSeen) == 1 else true
                    val status = if (idxStatus >= 0) it.getInt(idxStatus) else -1

                    latestByAddress[address] = SmsMessage(
                        id = id,
                        threadId = threadId,
                        sender = address,
                        body = body,
                        timestamp = timestamp,
                        isSentByUser = isUserMessage(type),
                        subscriptionId = subId,
                        isRead = read,
                        isSeen = seen,
                        isMms = false,
                        messageType = type,
                        status = status
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Error parsing SMS in conversation list")
                }
            }
        }

        // Now load MMS messages and update if they're newer
        val mmsCursor = context.contentResolver.query(
            Telephony.Mms.CONTENT_URI,
            arrayOf(
                Telephony.Mms._ID,
                Telephony.Mms.THREAD_ID,
                Telephony.Mms.DATE,
                Telephony.Mms.MESSAGE_BOX,
                Telephony.Mms.SUBSCRIPTION_ID,
                Telephony.Mms.READ,
                Telephony.Mms.SEEN
            ),
            null,
            null,
            "${Telephony.Mms.DATE} DESC"
        )

        mmsCursor?.use {
            val idxId = it.getColumnIndex(Telephony.Mms._ID)
            val idxThreadId = it.getColumnIndex(Telephony.Mms.THREAD_ID)
            val idxDate = it.getColumnIndex(Telephony.Mms.DATE)
            val idxBox = it.getColumnIndex(Telephony.Mms.MESSAGE_BOX)
            val idxSubId = it.getColumnIndex(Telephony.Mms.SUBSCRIPTION_ID)
            val idxRead = it.getColumnIndex(Telephony.Mms.READ)
            val idxSeen = it.getColumnIndex(Telephony.Mms.SEEN)

            while (it.moveToNext()) {
                try {
                    val id = if (idxId >= 0) it.getLong(idxId) else -1
                    val threadId = if (idxThreadId >= 0) it.getLong(idxThreadId) else -1
                    val date = if (idxDate >= 0) it.getLong(idxDate) * 1000L else 0L
                    val messageBox = if (idxBox >= 0) it.getInt(idxBox) else Telephony.Mms.MESSAGE_BOX_INBOX
                    val subId = if (idxSubId >= 0) it.getInt(idxSubId) else -1
                    val read = if (idxRead >= 0) it.getInt(idxRead) == 1 else true
                    val seen = if (idxSeen >= 0) it.getInt(idxSeen) == 1 else true

                    // Parse MMS details
                    val mmsUri = Uri.parse("content://mms/$id")
                    val mmsDetails = MmsHelper.parseMmsMessage(context, mmsUri) ?: continue

                    val sender = mmsDetails.sender
                    if (sender.isBlank()) continue
                    if (SmsOperations.isNumberBlocked(context, sender)) continue

                    // Only update if this MMS is newer than existing SMS
                    val existing = latestByAddress[sender]
                    if (existing == null || date > existing.timestamp) {
                        val isSent = messageBox == Telephony.Mms.MESSAGE_BOX_SENT ||
                                messageBox == Telephony.Mms.MESSAGE_BOX_OUTBOX

                        // Create preview text for MMS
                        val previewText = if (mmsDetails.body.isNotEmpty()) {
                            mmsDetails.body
                        } else if (mmsDetails.attachments.isNotEmpty()) {
                            val attachment = mmsDetails.attachments.first()
                            when {
                                attachment.isImage -> "📷 Photo"
                                attachment.isVideo -> "🎥 Video"
                                attachment.isAudio -> "🎵 Audio"
                                else -> "📎 Attachment"
                            }
                        } else {
                            "MMS message"
                        }

                        latestByAddress[sender] = SmsMessage(
                            id = id,
                            threadId = threadId,
                            sender = sender,
                            body = previewText,
                            timestamp = date,
                            isSentByUser = isSent,
                            subscriptionId = subId,
                            isRead = read,
                            isSeen = seen,
                            isMms = true,
                            attachments = mmsDetails.attachments,
                            messageType = messageBox
                        )
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error parsing MMS in conversation list")
                }
            }
        }

        return latestByAddress
    }
}
