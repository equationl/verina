package lv.aki.verina.data.model

enum class EventType(
    val displayName: String,
    val description: String,
    val availableVariables: List<String>,
    val mockVariables: Map<String, String>
) {
    SMS_RECEIVED(
        displayName = "短信接收",
        description = "当收到新短信时触发",
        availableVariables = listOf("sender", "message", "receiver", "timestamp", "formattedTime"),
        mockVariables = mapOf("sender" to "13800138000", "message" to "这是一条测试短信", "receiver" to "SIM 1 (中国移动)")
    ),
    PHONE_CALL(
        displayName = "电话呼入",
        description = "当收到来电时触发",
        availableVariables = listOf("number", "state", "receiver", "timestamp", "formattedTime"),
        mockVariables = mapOf("number" to "13900139000", "state" to "ringing", "receiver" to "SIM 1 (中国移动)")
    ),
    BATTERY_LEVEL(
        displayName = "电量阈值",
        description = "当电池电量跨越指定阈值时触发（可选从高到低或从低到高）",
        availableVariables = listOf("level", "threshold", "timestamp", "formattedTime"),
        mockVariables = mapOf("level" to "15", "threshold" to "20")
    );
}
