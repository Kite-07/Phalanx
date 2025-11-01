package com.kite.phalanx

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Receiver for incoming MMS messages
 * Required for the app to be a default SMS app
 */
class MmsReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "MmsReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        if (intent.action == Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) {
            val pendingResult = goAsync()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // MMS messages take time to be written to the database after WAP_PUSH_DELIVER
                    // We need to poll the database to find the new message
                    var mmsDetails: MmsMessageDetails? = null
                    var attempts = 0

                    while (mmsDetails == null && attempts < 10) {
                        delay(500) // Wait 500ms before checking

                        // Query for the most recent MMS in the inbox
                        val latestMms = getLatestMms(context)
                        if (latestMms != null) {
                            // Check if this MMS is new (within last 10 seconds)
                            val age = System.currentTimeMillis() - latestMms.timestamp
                            if (age < 10_000) {
                                mmsDetails = latestMms
                            }
                        }

                        attempts++
                    }

                    if (mmsDetails != null) {
                        Log.d(TAG, "Received MMS from ${mmsDetails.sender} with ${mmsDetails.attachments.size} attachments")

                        // Show notification for the new MMS
                        val messageText = if (mmsDetails.body.isNotEmpty()) {
                            mmsDetails.body
                        } else if (mmsDetails.attachments.isNotEmpty()) {
                            val firstAttachment = mmsDetails.attachments.first()
                            when {
                                firstAttachment.isImage -> "📷 Photo"
                                firstAttachment.isVideo -> "🎥 Video"
                                firstAttachment.isAudio -> "🎵 Audio"
                                else -> "📎 Attachment"
                            }
                        } else {
                            "MMS message"
                        }

                        NotificationHelper.showMessageNotification(
                            context = context,
                            sender = mmsDetails.sender,
                            message = messageText,
                            timestamp = mmsDetails.timestamp
                        )
                    } else {
                        Log.w(TAG, "Failed to retrieve MMS message after $attempts attempts")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing incoming MMS", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    /**
     * Get the most recent MMS message from the inbox
     */
    private fun getLatestMms(context: Context): MmsMessageDetails? {
        try {
            val cursor = context.contentResolver.query(
                Telephony.Mms.CONTENT_URI,
                arrayOf(Telephony.Mms._ID),
                "${Telephony.Mms.MESSAGE_BOX}=?",
                arrayOf(Telephony.Mms.MESSAGE_BOX_INBOX.toString()),
                "${Telephony.Mms.DATE} DESC LIMIT 1"
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val mmsId = it.getLong(it.getColumnIndexOrThrow(Telephony.Mms._ID))
                    val mmsUri = Uri.parse("content://mms/$mmsId")
                    return MmsHelper.parseMmsMessage(context, mmsUri)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying for latest MMS", e)
        }
        return null
    }
}
