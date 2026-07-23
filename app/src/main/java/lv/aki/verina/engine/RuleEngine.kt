package lv.aki.verina.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import lv.aki.verina.data.db.ActionEntity
import lv.aki.verina.data.model.ActionType
import lv.aki.verina.data.model.EventType
import lv.aki.verina.data.repository.RuleRepository
import lv.aki.verina.service.action.WebhookExecutor
import lv.aki.verina.service.retry.WebhookRetryManager
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RuleEngine(
    private val repository: RuleRepository,
    private val context: Context? = null
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val timeFormatter = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
    private val retryManager: WebhookRetryManager? by lazy {
        context?.let { WebhookRetryManager.getInstance(it) }
    }

    private fun timeVariables(): Map<String, String> {
        val now = System.currentTimeMillis()
        return mapOf(
            "timestamp" to now.toString(),
            "formattedTime" to timeFormatter.format(Date(now))
        )
    }

    private suspend fun executeAction(action: ActionEntity, variables: Map<String, String>) {
        when (action.actionType) {
            ActionType.WEBHOOK.name -> {
                try {
                    WebhookExecutor.execute(action, variables)
                } catch (e: Exception) {
                    Log.e(TAG, "Webhook execution failed for action ${action.id}, enqueueing retry", e)
                    retryManager?.enqueueRetry(
                        actionId = action.id,
                        url = action.url,
                        httpMethod = action.httpMethod,
                        headers = action.headers,
                        body = action.body,
                        variables = variables,
                        error = e.message
                    )
                }
            }
            else -> Log.w(TAG, "Unknown action type: ${action.actionType}, skipping")
        }
    }

    fun onEvent(eventType: EventType, variables: Map<String, String>) {
        scope.launch {
            try {
                val rules = repository.getEnabledRulesForEvent(eventType.name)
                Log.i(TAG, "Event $eventType triggered, matched ${rules.size} rule(s)")
                for (ruleWithActions in rules) {
                    val enrichedVars = variables + timeVariables()
                    for (action in ruleWithActions.actions.sortedBy { it.order }) {
                        executeAction(action, enrichedVars)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing event $eventType", e)
            }
        }
    }

    fun onBatteryLevelChanged(currentLevel: Int, previousLevel: Int) {
        scope.launch {
            try {
                val rules = repository.getEnabledRulesForEvent(EventType.BATTERY_LEVEL.name)
                for (ruleWithActions in rules) {
                    val (threshold, direction) = parseBatteryConfig(ruleWithActions.rule.eventConfig)

                    val shouldTrigger = when (direction) {
                        "low_to_high" -> when {
                            previousLevel == -1 -> currentLevel >= threshold
                            previousLevel < threshold && currentLevel >= threshold -> true
                            else -> false
                        }
                        else -> when { // high_to_low (default)
                            previousLevel == -1 -> currentLevel <= threshold
                            previousLevel > threshold && currentLevel <= threshold -> true
                            else -> false
                        }
                    }

                    if (shouldTrigger) {
                        val dirDesc = if (direction == "low_to_high") "low->high" else "high->low"
                        Log.i(TAG, "Battery ${currentLevel}% crossed threshold ${threshold}% ($dirDesc), firing rule '${ruleWithActions.rule.name}'")
                        val variables = mapOf(
                            "level" to currentLevel.toString(),
                            "threshold" to threshold.toString(),
                        ) + timeVariables()
                        for (action in ruleWithActions.actions.sortedBy { it.order }) {
                            executeAction(action, variables)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing battery event", e)
            }
        }
    }

    private fun parseBatteryConfig(eventConfig: String): Pair<Int, String> {
        return try {
            val obj = JSONObject(eventConfig)
            val threshold = obj.optInt("threshold", 20)
            val direction = obj.optString("direction", "high_to_low")
            Pair(threshold, direction)
        } catch (_: Exception) {
            Pair(20, "high_to_low")
        }
    }

    companion object {
        private const val TAG = "RuleEngine"
    }
}
