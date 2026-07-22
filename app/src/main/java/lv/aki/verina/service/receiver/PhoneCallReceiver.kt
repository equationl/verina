package lv.aki.verina.service.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import lv.aki.verina.data.model.EventType
import lv.aki.verina.engine.RuleEngine

class PhoneCallReceiver(private val ruleEngine: RuleEngine) : BroadcastReceiver() {

    private var lastTriggerTime = 0L

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return

        if (state != TelephonyManager.EXTRA_STATE_RINGING) return

        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: return

        val now = System.currentTimeMillis()
        if (now - lastTriggerTime < 3000) {
            Log.d(TAG, "Skipping duplicate RINGING for $number within 3s window")
            return
        }
        lastTriggerTime = now

        val subscriptionId = resolveSubscriptionId(intent)
        val receiver = SimInfoHelper.getSimDescription(context, subscriptionId)

        Log.i(TAG, "Incoming call from $number on $receiver (subId=$subscriptionId)")

        val variables = mapOf(
            "number" to number,
            "state" to "ringing",
            "receiver" to receiver
        )
        ruleEngine.onEvent(EventType.PHONE_CALL, variables)
    }

    private fun resolveSubscriptionId(intent: Intent): Int {
        val keys = listOf(
            "android.telephony.extra.SUBSCRIPTION_INDEX",
            "subscription",
            "phone",
            "slot",
            "simId"
        )
        for (key in keys) {
            val value = intent.getIntExtra(key, -1)
            if (value >= 0) return value
        }
        return -1
    }

    companion object {
        private const val TAG = "PhoneCallReceiver"
    }
}
