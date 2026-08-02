package lv.aki.verina.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferRecordDao {
    @Insert
    suspend fun insert(record: TransferRecordEntity): Long

    @Update
    suspend fun update(record: TransferRecordEntity)

    @Query("SELECT * FROM transfer_records ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<TransferRecordEntity>>

    @Query("SELECT * FROM transfer_records WHERE id = :id")
    suspend fun getById(id: Long): TransferRecordEntity?

    @Query("DELETE FROM transfer_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transfer_records WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM transfer_records")
    suspend fun clearAll()

    @Query("DELETE FROM transfer_records WHERE keepAll = 0 AND status = 'FAILED' AND id NOT IN (SELECT id FROM transfer_records WHERE keepAll = 0 AND status = 'FAILED' ORDER BY createdAt DESC, id DESC LIMIT :limit)")
    suspend fun trimFailureOnlyRecords(limit: Int = 1000)
}
