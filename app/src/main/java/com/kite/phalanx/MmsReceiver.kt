package com.kite.phalanx

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receiver for incoming MMS messages
 * Required for the app to be a default SMS app
 */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == "android.provider.Telephony.WAP_PUSH_DELIVER") {
            // MMS handling will be implemented in future phases
            // For now, just receive
        }
    }
}
