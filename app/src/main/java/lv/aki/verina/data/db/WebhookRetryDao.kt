package lv.aki.verina.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WebhookRetryDao {
    @Insert
    suspend fun insert(retry: WebhookRetryEntity): Long

    @Update
    suspend fun update(retry: WebhookRetryEntity)

    @Query("SELECT * FROM webhook_retry_queue WHERE status = 'PENDING' AND nextRetryAt <= :currentTime ORDER BY nextRetryAt ASC LIMIT :limit")
    suspend fun getPendingRetries(currentTime: Long, limit: Int = 10): List<WebhookRetryEntity>

    @Query("SELECT * FROM webhook_retry_queue WHERE id = :id")
    suspend fun getById(id: Long): WebhookRetryEntity?

    @Query("UPDATE webhook_retry_queue SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("UPDATE webhook_retry_queue SET status = 'EXHAUSTED', retryCount = :retryCount, lastError = :error WHERE id = :id")
    suspend fun markExhausted(id: Long, retryCount: Int, error: String?)

    @Query("DELETE FROM webhook_retry_queue WHERE status = 'EXHAUSTED' AND id NOT IN (SELECT id FROM webhook_retry_queue WHERE status = 'EXHAUSTED' ORDER BY createdAt DESC, id DESC LIMIT :limit)")
    suspend fun trimExhausted(limit: Int = 1000)

    @Transaction
    suspend fun markExhaustedAndTrim(id: Long, retryCount: Int, error: String?) {
        markExhausted(id, retryCount, error)
        trimExhausted()
    }

    @Query("SELECT * FROM webhook_retry_queue WHERE status = 'EXHAUSTED' ORDER BY createdAt DESC, id DESC")
    fun observeExhausted(): Flow<List<WebhookRetryEntity>>

    @Query("DELETE FROM webhook_retry_queue WHERE status = 'COMPLETED'")
    suspend fun deleteCompleted()

    @Query("SELECT COUNT(*) FROM webhook_retry_queue WHERE status = 'PENDING'")
    suspend fun getPendingCount(): Int

    @Query("SELECT COUNT(*) FROM webhook_retry_queue WHERE status = 'EXHAUSTED'")
    suspend fun getExhaustedCount(): Int
}
