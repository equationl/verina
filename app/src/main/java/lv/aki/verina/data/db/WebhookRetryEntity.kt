package lv.aki.verina.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "webhook_retry_queue",
    indices = [Index("scheduledAt"), Index("status")]
)
data class WebhookRetryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actionId: Long,
    val transferRecordId: Long? = null,
    @ColumnInfo(defaultValue = "0") val keepRecord: Boolean = false,
    val url: String,
    val httpMethod: String,
    val headers: String = "{}",
    val body: String? = null,
    val variablesJson: String = "{}",
    val status: String = "PENDING", // PENDING, PROCESSING, FAILED, EXHAUSTED
    val retryCount: Int = 0,
    val maxRetries: Int = 10,
    val nextRetryAt: Long = 0,
    val scheduledAt: Long = System.currentTimeMillis(),
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
