package lv.aki.verina.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

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

    @Query("UPDATE webhook_retry_queue SET status = 'EXHAUSTED', lastError = :error WHERE id = :id")
    suspend fun markExhausted(id: Long, error: String?)

    @Query("DELETE FROM webhook_retry_queue WHERE status = 'EXHAUSTED' AND createdAt < :beforeTime")
    suspend fun deleteOldExhausted(beforeTime: Long)

    @Query("SELECT COUNT(*) FROM webhook_retry_queue WHERE status = 'PENDING'")
    suspend fun getPendingCount(): Int

    @Query("SELECT COUNT(*) FROM webhook_retry_queue WHERE status = 'EXHAUSTED'")
    suspend fun getExhaustedCount(): Int
}
