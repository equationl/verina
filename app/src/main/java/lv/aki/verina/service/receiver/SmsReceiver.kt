package lv.aki.verina.service.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import lv.aki.verina.data.model.EventType
import lv.aki.verina.engine.RuleEngine

class SmsReceiver(private val ruleEngine: RuleEngine) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages[0].displayOriginatingAddress ?: "unknown"
        val body = messages.joinToString("") { it.displayMessageBody ?: "" }

        val subscriptionId = intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX",
            intent.getIntExtra("subscription", -1))
        val receiver = SimInfoHelper.getSimDescription(context, subscriptionId)

        Log.i(TAG, "SMS received from $sender on $receiver")

        val variables = mapOf(
            "sender" to sender,
            "message" to body,
            "receiver" to receiver
        )
        ruleEngine.onEvent(EventType.SMS_RECEIVED, variables)
    }

    companion object {
        private const val TAG = "SmsReceiver"
    }
}
