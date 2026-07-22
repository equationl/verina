package lv.aki.verina.ui.screen.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lv.aki.verina.data.db.ActionEntity
import lv.aki.verina.data.db.AppDatabase
import lv.aki.verina.data.db.RuleEntity
import lv.aki.verina.data.model.ActionType
import lv.aki.verina.data.model.EventType
import lv.aki.verina.data.model.HttpMethod
import lv.aki.verina.data.repository.RuleRepository
import lv.aki.verina.service.action.WebhookExecutor
import lv.aki.verina.service.action.WebhookTestResult
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ActionUiState(
    val actionType: ActionType = ActionType.WEBHOOK,
    val httpMethod: HttpMethod = HttpMethod.GET,
    val url: String = "",
    val headers: String = "{}",
    val body: String = ""
)

/** 电量触发方向：high_to_low=从高到低，low_to_high=从低到高 */
enum class BatteryDirection(val key: String, val displayName: String) {
    HIGH_TO_LOW("high_to_low", "从高到低"),
    LOW_TO_HIGH("low_to_high", "从低到高")
}

data class EditorUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val name: String = "",
    val eventType: EventType = EventType.SMS_RECEIVED,
    val batteryThreshold: Int = 20,
    val batteryDirection: BatteryDirection = BatteryDirection.HIGH_TO_LOW,
    val actions: List<ActionUiState> = listOf(ActionUiState()),
    val isSaved: Boolean = false,
    val isTesting: Boolean = false,
    val testResults: List<WebhookTestResult>? = null
)

class RuleEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RuleRepository(AppDatabase.getInstance(application))

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private var editingRuleId: Long = -1L
    private var originalCreatedAt: Long = 0L
    private var originalEnabled: Boolean = true

    private val timeFormatter = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())

    fun loadRule(ruleId: Long) {
        editingRuleId = ruleId
        if (ruleId == -1L) {
            _state.value = EditorUiState(isLoading = false)
            return
        }
        viewModelScope.launch {
            val ruleWithActions = repository.getRuleWithActions(ruleId)
            if (ruleWithActions != null) {
                originalCreatedAt = ruleWithActions.rule.createdAt
                originalEnabled = ruleWithActions.rule.enabled
                val eventType = try {
                    EventType.valueOf(ruleWithActions.rule.eventType)
                } catch (_: Exception) {
                    EventType.SMS_RECEIVED
                }
                val (threshold, direction) = try {
                    val obj = JSONObject(ruleWithActions.rule.eventConfig)
                    obj.optInt("threshold", 20) to obj.optString("direction", "high_to_low")
                } catch (_: Exception) { 20 to "high_to_low" }
                val batteryDir = BatteryDirection.entries.find { it.key == direction } ?: BatteryDirection.HIGH_TO_LOW

                _state.value = EditorUiState(
                    isLoading = false,
                    isNew = false,
                    name = ruleWithActions.rule.name,
                    eventType = eventType,
                    batteryThreshold = threshold,
                    batteryDirection = batteryDir,
                    actions = ruleWithActions.actions.map { action ->
                        val actionType = try {
                            ActionType.valueOf(action.actionType)
                        } catch (_: Exception) {
                            ActionType.WEBHOOK
                        }
                        ActionUiState(
                            actionType = actionType,
                            httpMethod = try { HttpMethod.valueOf(action.httpMethod) } catch (_: Exception) { HttpMethod.GET },
                            url = action.url,
                            headers = action.headers,
                            body = action.body ?: ""
                        )
                    }.ifEmpty { listOf(ActionUiState()) }
                )
            } else {
                _state.value = EditorUiState(isLoading = false)
            }
        }
    }

    fun updateName(name: String) {
        _state.value = _state.value.copy(name = name)
    }

    fun updateEventType(eventType: EventType) {
        _state.value = _state.value.copy(eventType = eventType)
    }

    fun updateBatteryThreshold(threshold: Int) {
        _state.value = _state.value.copy(batteryThreshold = threshold.coerceIn(1, 100))
    }

    fun updateBatteryDirection(direction: BatteryDirection) {
        _state.value = _state.value.copy(batteryDirection = direction)
    }

    fun updateAction(index: Int, action: ActionUiState) {
        val actions = _state.value.actions.toMutableList()
        if (index in actions.indices) {
            actions[index] = action
            _state.value = _state.value.copy(actions = actions)
        }
    }

    fun addAction() {
        _state.value = _state.value.copy(
            actions = _state.value.actions + ActionUiState()
        )
    }

    fun removeAction(index: Int) {
        val actions = _state.value.actions.toMutableList()
        if (actions.size > 1 && index in actions.indices) {
            actions.removeAt(index)
            _state.value = _state.value.copy(actions = actions)
        }
    }

    fun testActions() {
        val current = _state.value
        if (current.name.isBlank() || current.actions.all { it.url.isBlank() }) return

        _state.value = current.copy(isTesting = true, testResults = null)

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val variables = current.eventType.mockVariables + mapOf(
                "timestamp" to now.toString(),
                "formattedTime" to timeFormatter.format(Date(now))
            )

            val results = current.actions
                .filter { it.url.isNotBlank() && it.actionType == ActionType.WEBHOOK }
                .map { actionUi ->
                    val tempEntity = ActionEntity(
                        ruleId = 0,
                        actionType = actionUi.actionType.name,
                        httpMethod = actionUi.httpMethod.name,
                        url = actionUi.url.trim(),
                        headers = actionUi.headers.trim().ifBlank { "{}" },
                        body = actionUi.body.trim().ifBlank { null }
                    )
                    WebhookExecutor.executeForTest(tempEntity, variables)
                }

            _state.value = _state.value.copy(isTesting = false, testResults = results)
        }
    }

    fun dismissTestResults() {
        _state.value = _state.value.copy(testResults = null)
    }

    fun save() {
        val current = _state.value
        if (current.name.isBlank() || current.actions.all { it.url.isBlank() }) return

        viewModelScope.launch {
            val eventConfig = if (current.eventType == EventType.BATTERY_LEVEL) {
                JSONObject()
                    .put("threshold", current.batteryThreshold)
                    .put("direction", current.batteryDirection.key)
                    .toString()
            } else {
                "{}"
            }
            val rule = RuleEntity(
                id = if (editingRuleId == -1L) 0L else editingRuleId,
                name = current.name.trim(),
                eventType = current.eventType.name,
                enabled = if (editingRuleId == -1L) true else originalEnabled,
                createdAt = if (editingRuleId == -1L) System.currentTimeMillis() else originalCreatedAt,
                eventConfig = eventConfig
            )
            val actions = current.actions
                .filter { it.url.isNotBlank() }
                .mapIndexed { index, actionUi ->
                    ActionEntity(
                        ruleId = rule.id,
                        actionType = actionUi.actionType.name,
                        httpMethod = actionUi.httpMethod.name,
                        url = actionUi.url.trim(),
                        headers = actionUi.headers.trim().ifBlank { "{}" },
                        body = actionUi.body.trim().ifBlank { null },
                        order = index
                    )
                }
            repository.saveRuleWithActions(rule, actions)
            _state.value = current.copy(isSaved = true)
        }
    }
}
