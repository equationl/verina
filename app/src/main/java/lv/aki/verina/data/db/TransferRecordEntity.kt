package lv.aki.verina.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transfer_records",
    indices = [Index("status"), Index("eventType"), Index("createdAt")]
)
data class TransferRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleId: Long,
    val ruleName: String,
    val actionId: Long,
    val eventType: String,
    val status: String,
    val httpMethod: String,
    val requestUrl: String,
    val requestHeaders: String = "{}",
    val requestBody: String? = null,
    val variablesJson: String = "{}",
    val responseCode: Int? = null,
    val responseHeaders: String = "{}",
    val responseBody: String? = null,
    val error: String? = null,
    val attemptCount: Int = 1,
    val maxAttempts: Int = 1,
    val keepAll: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
