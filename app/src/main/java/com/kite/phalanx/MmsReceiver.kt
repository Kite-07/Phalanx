package com.kite.phalanx

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receiver for incoming MMS messages
 * Required for the app to be a default SMS app
 */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == "android.provider.Telephony.WAP_PUSH_DELIVER") {
            Log.d("MmsReceiver", "MMS received")
            // MMS handling will be implemented in future phases
            // For now, just receive and log
        }
    }
}
