package lv.aki.verina.service.receiver

import android.content.Context
import android.telephony.SubscriptionManager
import android.util.Log

object SimInfoHelper {

    private const val TAG = "SimInfoHelper"

    fun getSimDescription(context: Context, subscriptionId: Int): String {
        val effectiveId = if (subscriptionId >= 0) {
            subscriptionId
        } else {
            val defaultVoice = SubscriptionManager.getDefaultVoiceSubscriptionId()
            if (defaultVoice >= 0) defaultVoice else return "unknown"
        }

        return try {
            val subManager = context.getSystemService(SubscriptionManager::class.java)
            val info = subManager?.getActiveSubscriptionInfo(effectiveId)
            if (info != null) {
                val slot = info.simSlotIndex + 1
                val carrier = info.carrierName?.toString()?.takeIf { it.isNotBlank() }
                if (carrier != null) "SIM $slot ($carrier)" else "SIM $slot"
            } else {
                descriptionFromList(subManager, effectiveId)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to read subscription info", e)
            "unknown"
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get SIM info for subscription $effectiveId", e)
            "unknown"
        }
    }

    @Suppress("DEPRECATION")
    private fun descriptionFromList(
        subManager: SubscriptionManager?,
        subscriptionId: Int
    ): String {
        val list = subManager?.activeSubscriptionInfoList ?: return "unknown"
        val info = list.firstOrNull { it.subscriptionId == subscriptionId } ?: return "unknown"
        val slot = info.simSlotIndex + 1
        val carrier = info.carrierName?.toString()?.takeIf { it.isNotBlank() }
        return if (carrier != null) "SIM $slot ($carrier)" else "SIM $slot"
    }
}
