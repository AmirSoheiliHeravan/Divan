package com.divan.app.core.sms

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.divan.app.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Receives incoming SMS and hands bank messages to the parser.
 *  Works for any sender; rules decide which messages are financial. */
class SmsReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val app = context.applicationContext as? com.divan.app.DivanApp ?: return

        messages.forEach { msg ->
            val sender = msg.originatingAddress ?: return@forEach
            val body = msg.messageBody ?: return@forEach
            val ts = msg.timestampMillis
            scope.launch {
                runCatching { ServiceLocator.smsParser.handleIncoming(sender, body, ts) }
            }
        }
    }
}
