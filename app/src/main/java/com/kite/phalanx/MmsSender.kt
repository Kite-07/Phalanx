package com.kite.phalanx

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import timber.log.Timber

/**
 * Helper class for sending MMS messages
 *
 * Note: MMS sending on Android is complex and requires the app to be the default SMS app.
 * This implementation uses SmsManager to send MMS and also writes to the database for record keeping.
 */
object MmsSender {
    /**
     * Send an MMS message with text and/or attachments
     * @param context Application context
     * @param recipient Phone number of recipient
     * @param text Optional message text
     * @param attachments List of attachments to send
     * @param subscriptionId SIM subscription ID (-1 for default)
     */
    suspend fun sendMms(
        context: Context,
        recipient: String,
        text: String?,
        attachments: List<SelectedAttachment>,
        subscriptionId: Int = -1
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Create MMS message in the system database
            val mmsUri = insertMms(context, recipient, text, subscriptionId)
            if (mmsUri == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to create MMS", Toast.LENGTH_SHORT).show()
                }
                return@withContext false
            }

            val mmsId = mmsUri.lastPathSegment?.toLongOrNull()
            if (mmsId == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Invalid MMS ID", Toast.LENGTH_SHORT).show()
                }
                return@withContext false
            }

            // Add text part if present
            if (!text.isNullOrBlank()) {
                insertTextPart(context, mmsId, text)
            }

            // Add attachment parts
            for (attachment in attachments) {
                insertAttachmentPart(context, mmsId, attachment)
            }

            // Add recipient address
            insertAddress(context, mmsId, recipient)

            // Now actually send the MMS using SmsManager
            val sent = sendViaSmsManager(context, mmsUri, recipient, text, attachments, subscriptionId)

            if (sent) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Sending MMS...", Toast.LENGTH_SHORT).show()
                }
                Timber.d("MMS sent to $recipient")
                return@withContext true
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to send MMS", Toast.LENGTH_SHORT).show()
                }
                return@withContext false
            }

        } catch (e: Exception) {
            Timber.e(e, "Error sending MMS")
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to send MMS: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            return@withContext false
        }
    }

    /**
     * Send MMS using SmsManager API
     */
    private fun sendViaSmsManager(
        context: Context,
        mmsUri: Uri,
        recipient: String,
        text: String?,
        attachments: List<SelectedAttachment>,
        subscriptionId: Int
    ): Boolean {
        try {
            val smsManager = if (subscriptionId != -1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)?.createForSubscriptionId(subscriptionId)
            } else if (subscriptionId != -1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
            } else {
                SmsManager.getDefault()
            }

            if (smsManager == null) {
                Timber.e("Failed to get SmsManager")
                return false
            }

            // Create PendingIntent for send status
            val sentIntent = Intent(MmsSentReceiver.ACTION_MMS_SENT).apply {
                putExtra("mms_uri", mmsUri.toString())
            }
            val sentPI = PendingIntent.getBroadcast(
                context,
                0,
                sentIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            // Send using sendMultimediaMessage
            // Note: contentUri should point to the MMS we created in the database
            Timber.d("Calling sendMultimediaMessage with URI: $mmsUri")
            smsManager.sendMultimediaMessage(
                context,
                mmsUri,
                null, // locationUrl - not used for sending
                null, // configOverrides - optional
                sentPI  // sentIntent - to receive send status
            )

            Timber.d("sendMultimediaMessage called successfully")
            return true
        } catch (e: Exception) {
            Timber.e(e, "Error sending MMS via SmsManager")
            return false
        }
    }

    /**
     * Insert MMS message into the system database
     */
    private fun insertMms(
        context: Context,
        recipient: String,
        text: String?,
        subscriptionId: Int
    ): Uri? {
        val values = ContentValues().apply {
            put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_OUTBOX)
            put(Telephony.Mms.MESSAGE_TYPE, 128) // SEND_REQ
            put(Telephony.Mms.DATE, System.currentTimeMillis() / 1000L)
            put(Telephony.Mms.READ, 1)
            put(Telephony.Mms.SEEN, 1)
            if (subscriptionId != -1) {
                put(Telephony.Mms.SUBSCRIPTION_ID, subscriptionId)
            }
            if (!text.isNullOrBlank()) {
                put(Telephony.Mms.SUBJECT, "") // Subject is optional
            }
        }

        return try {
            context.contentResolver.insert(Telephony.Mms.CONTENT_URI, values)
        } catch (e: Exception) {
            Timber.e(e, "Error inserting MMS")
            null
        }
    }

    /**
     * Insert text part into MMS
     */
    private fun insertTextPart(context: Context, mmsId: Long, text: String) {
        val partUri = Uri.parse("content://mms/$mmsId/part")
        val values = ContentValues().apply {
            put(Telephony.Mms.Part.CONTENT_TYPE, "text/plain")
            put(Telephony.Mms.Part.CHARSET, 106) // UTF-8
            put(Telephony.Mms.Part.TEXT, text)
        }

        try {
            context.contentResolver.insert(partUri, values)
        } catch (e: Exception) {
            Timber.e(e, "Error inserting text part")
        }
    }

    /**
     * Insert attachment part into MMS
     */
    private fun insertAttachmentPart(
        context: Context,
        mmsId: Long,
        attachment: SelectedAttachment
    ) {
        val partUri = Uri.parse("content://mms/$mmsId/part")

        try {
            // Read attachment data
            val inputStream = context.contentResolver.openInputStream(attachment.uri)
            if (inputStream == null) {
                Timber.e("Failed to open input stream for attachment")
                return
            }

            val values = ContentValues().apply {
                put(Telephony.Mms.Part.CONTENT_TYPE, attachment.mimeType)
                put(Telephony.Mms.Part.NAME, attachment.fileName ?: "attachment")
            }

            val partInsertUri = context.contentResolver.insert(partUri, values)
            if (partInsertUri != null) {
                // Write attachment data
                val outputStream = context.contentResolver.openOutputStream(partInsertUri)
                if (outputStream != null) {
                    inputStream.copyTo(outputStream)
                    outputStream.close()
                }
            }

            inputStream.close()
        } catch (e: Exception) {
            Timber.e(e, "Error inserting attachment part")
        }
    }

    /**
     * Insert recipient address into MMS
     */
    private fun insertAddress(context: Context, mmsId: Long, address: String) {
        val addrUri = Uri.parse("content://mms/$mmsId/addr")
        val values = ContentValues().apply {
            put(Telephony.Mms.Addr.ADDRESS, address)
            put(Telephony.Mms.Addr.CHARSET, 106) // UTF-8
            put(Telephony.Mms.Addr.TYPE, 151) // TO address
        }

        try {
            context.contentResolver.insert(addrUri, values)
        } catch (e: Exception) {
            Timber.e(e, "Error inserting address")
        }
    }
}
