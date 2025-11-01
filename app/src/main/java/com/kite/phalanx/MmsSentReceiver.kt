package com.kite.phalanx

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import android.widget.Toast

/**
 * Receiver for MMS send status
 */
class MmsSentReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "MmsSentReceiver"
        const val ACTION_MMS_SENT = "com.kite.phalanx.MMS_SENT"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d(TAG, "onReceive called - context: $context, intent: $intent, action: ${intent?.action}")

        if (context == null || intent == null) {
            Log.e(TAG, "Context or intent is null")
            return
        }

        if (intent.action != ACTION_MMS_SENT) {
            Log.w(TAG, "Received intent with unexpected action: ${intent.action}")
            return
        }

        val mmsUriString = intent.getStringExtra("mms_uri")
        Log.d(TAG, "MMS URI from intent: $mmsUriString")

        if (mmsUriString == null) {
            Log.e(TAG, "MMS URI not found in intent")
            return
        }

        val mmsUri = Uri.parse(mmsUriString)
        val resultCode = resultCode
        Log.d(TAG, "Result code: $resultCode")

        when (resultCode) {
            Activity.RESULT_OK -> {
                Log.d(TAG, "MMS sent successfully")
                // Update the message status to SENT
                updateMmsStatus(context, mmsUri, Telephony.Mms.MESSAGE_BOX_SENT)
                Toast.makeText(context, "MMS sent", Toast.LENGTH_SHORT).show()
            }
            else -> {
                Log.e(TAG, "MMS send failed with result code: $resultCode")
                // Update the message status to FAILED
                updateMmsStatus(context, mmsUri, Telephony.Mms.MESSAGE_BOX_FAILED)
                Toast.makeText(context, "Failed to send MMS", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateMmsStatus(context: Context, mmsUri: Uri, status: Int) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Mms.MESSAGE_BOX, status)
            }
            context.contentResolver.update(mmsUri, values, null, null)
            Log.d(TAG, "Updated MMS status to $status")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating MMS status", e)
        }
    }
}
