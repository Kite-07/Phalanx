package com.kite.phalanx

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Helper class for MMS message operations
 */
object MmsHelper {
    private const val TAG = "MmsHelper"

    /**
     * Parse MMS message from the system database
     * @param context Application context
     * @param mmsUri URI of the MMS message
     * @return Parsed message details or null if parsing fails
     */
    fun parseMmsMessage(context: Context, mmsUri: Uri): MmsMessageDetails? {
        try {
            val cursor = context.contentResolver.query(
                mmsUri,
                arrayOf(
                    Telephony.Mms._ID,
                    Telephony.Mms.THREAD_ID,
                    Telephony.Mms.DATE,
                    Telephony.Mms.MESSAGE_BOX,
                    Telephony.Mms.SUBJECT,
                    Telephony.Mms.SUBSCRIPTION_ID,
                    Telephony.Mms.READ,
                    Telephony.Mms.SEEN
                ),
                null,
                null,
                null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val id = it.getLong(it.getColumnIndexOrThrow(Telephony.Mms._ID))
                    val threadId = it.getLong(it.getColumnIndexOrThrow(Telephony.Mms.THREAD_ID))
                    val date = it.getLong(it.getColumnIndexOrThrow(Telephony.Mms.DATE)) * 1000L // Convert to milliseconds
                    val messageBox = it.getInt(it.getColumnIndexOrThrow(Telephony.Mms.MESSAGE_BOX))
                    val subject = it.getString(it.getColumnIndexOrThrow(Telephony.Mms.SUBJECT)) ?: ""
                    val subscriptionId = it.getInt(it.getColumnIndexOrThrow(Telephony.Mms.SUBSCRIPTION_ID))
                    val read = it.getInt(it.getColumnIndexOrThrow(Telephony.Mms.READ)) == 1
                    val seen = it.getInt(it.getColumnIndexOrThrow(Telephony.Mms.SEEN)) == 1

                    // Get sender address
                    val sender = getMmsSender(context, id)

                    // Get message body
                    val body = getMmsText(context, id)

                    // Get attachments
                    val attachments = getMmsAttachments(context, id)

                    return MmsMessageDetails(
                        id = id,
                        threadId = threadId,
                        sender = sender,
                        body = body,
                        subject = subject,
                        timestamp = date,
                        messageBox = messageBox,
                        subscriptionId = subscriptionId,
                        attachments = attachments,
                        isRead = read,
                        isSeen = seen
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing MMS message", e)
        }
        return null
    }

    /**
     * Get the sender's address from an MMS message
     */
    private fun getMmsSender(context: Context, mmsId: Long): String {
        val uri = Uri.parse("content://mms/$mmsId/addr")
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(Telephony.Mms.Addr.ADDRESS, Telephony.Mms.Addr.TYPE),
            null,
            null,
            null
        )

        cursor?.use {
            while (it.moveToNext()) {
                val type = it.getInt(it.getColumnIndexOrThrow(Telephony.Mms.Addr.TYPE))
                // TYPE = 137 is FROM (sender)
                if (type == 137) {
                    return it.getString(it.getColumnIndexOrThrow(Telephony.Mms.Addr.ADDRESS)) ?: ""
                }
            }
        }
        return ""
    }

    /**
     * Extract text content from an MMS message
     */
    private fun getMmsText(context: Context, mmsId: Long): String {
        val partUri = Uri.parse("content://mms/part")
        val cursor = context.contentResolver.query(
            partUri,
            arrayOf(Telephony.Mms.Part._ID, Telephony.Mms.Part.CONTENT_TYPE, Telephony.Mms.Part.TEXT),
            "${Telephony.Mms.Part.MSG_ID}=?",
            arrayOf(mmsId.toString()),
            null
        )

        val textParts = mutableListOf<String>()
        cursor?.use {
            while (it.moveToNext()) {
                val contentType = it.getString(it.getColumnIndexOrThrow(Telephony.Mms.Part.CONTENT_TYPE))
                if (contentType == "text/plain") {
                    val text = it.getString(it.getColumnIndexOrThrow(Telephony.Mms.Part.TEXT))
                    if (text != null) {
                        textParts.add(text)
                    }
                }
            }
        }

        return textParts.joinToString("\n")
    }

    /**
     * Extract attachments from an MMS message
     */
    private fun getMmsAttachments(context: Context, mmsId: Long): List<MessageAttachment> {
        val attachments = mutableListOf<MessageAttachment>()
        val partUri = Uri.parse("content://mms/part")

        val cursor = context.contentResolver.query(
            partUri,
            arrayOf(
                Telephony.Mms.Part._ID,
                Telephony.Mms.Part.CONTENT_TYPE,
                Telephony.Mms.Part.NAME,
                Telephony.Mms.Part._DATA
            ),
            "${Telephony.Mms.Part.MSG_ID}=?",
            arrayOf(mmsId.toString()),
            null
        )

        cursor?.use {
            while (it.moveToNext()) {
                val partId = it.getString(it.getColumnIndexOrThrow(Telephony.Mms.Part._ID))
                val contentType = it.getString(it.getColumnIndexOrThrow(Telephony.Mms.Part.CONTENT_TYPE)) ?: continue

                // Skip text/plain parts (we handle those separately)
                if (contentType == "text/plain" || contentType == "application/smil") {
                    continue
                }

                val name = it.getString(it.getColumnIndexOrThrow(Telephony.Mms.Part.NAME))
                val data = it.getString(it.getColumnIndexOrThrow(Telephony.Mms.Part._DATA))

                // Build URI for the part
                val partUri = Uri.parse("content://mms/part/$partId")

                // Get file size
                var fileSize = 0L
                try {
                    context.contentResolver.openInputStream(partUri)?.use { inputStream ->
                        fileSize = inputStream.available().toLong()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting file size for part $partId", e)
                }

                attachments.add(
                    MessageAttachment(
                        id = partId,
                        contentType = contentType,
                        uri = partUri,
                        fileName = name ?: "attachment_$partId",
                        fileSize = fileSize
                    )
                )
            }
        }

        return attachments
    }

    /**
     * Save attachment to external cache for sharing/viewing
     */
    fun saveAttachmentToCache(context: Context, attachment: MessageAttachment): File? {
        try {
            val cacheDir = File(context.externalCacheDir, "attachments")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            val extension = when {
                attachment.isImage -> when (attachment.contentType) {
                    "image/jpeg" -> "jpg"
                    "image/png" -> "png"
                    "image/gif" -> "gif"
                    "image/webp" -> "webp"
                    else -> "img"
                }
                attachment.isVideo -> when (attachment.contentType) {
                    "video/mp4" -> "mp4"
                    "video/3gpp" -> "3gp"
                    else -> "video"
                }
                attachment.isAudio -> when (attachment.contentType) {
                    "audio/mpeg" -> "mp3"
                    "audio/aac" -> "aac"
                    else -> "audio"
                }
                else -> "dat"
            }

            val file = File(cacheDir, "${attachment.id}.$extension")

            context.contentResolver.openInputStream(attachment.uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }

            return file
        } catch (e: Exception) {
            Log.e(TAG, "Error saving attachment to cache", e)
            return null
        }
    }
}

/**
 * Data class representing parsed MMS message details
 */
data class MmsMessageDetails(
    val id: Long,
    val threadId: Long,
    val sender: String,
    val body: String,
    val subject: String,
    val timestamp: Long,
    val messageBox: Int,
    val subscriptionId: Int,
    val attachments: List<MessageAttachment>,
    val isRead: Boolean,
    val isSeen: Boolean
)
