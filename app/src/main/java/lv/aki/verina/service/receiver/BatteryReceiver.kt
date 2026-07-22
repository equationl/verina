package lv.aki.verina.service.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.util.Log
import lv.aki.verina.engine.RuleEngine

class BatteryReceiver(private val ruleEngine: RuleEngine) : BroadcastReceiver() {

    private var lastPercentage: Int = -1

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BATTERY_CHANGED) return

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val percentage = if (scale > 0) (level * 100 / scale) else level

        if (percentage == lastPercentage) return
        val previous = lastPercentage
        lastPercentage = percentage

        Log.d(TAG, "Battery: $previous% -> $percentage%")

        ruleEngine.onBatteryLevelChanged(percentage, previous)
    }

    companion object {
        private const val TAG = "BatteryReceiver"
    }
}
