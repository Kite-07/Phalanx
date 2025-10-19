package com.kite.phalanx

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.telephony.TelephonyManager
import android.util.Log

/**
 * Service to handle "respond via message" requests from the system
 * Required for the app to be a default SMS app
 */
class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == TelephonyManager.ACTION_RESPOND_VIA_MESSAGE) {
            val recipient = intent.data?.schemeSpecificPart
            val message = intent.getStringExtra(Intent.EXTRA_TEXT)

            if (recipient != null && message != null) {
                Log.d("HeadlessSmsSendService", "Sending SMS to $recipient: $message")
                SmsHelper.sendSms(this, recipient, message)
            }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
