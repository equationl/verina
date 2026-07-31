package lv.aki.verina.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import lv.aki.verina.data.model.NotificationFilterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationFilterDao {
    @Query("SELECT * FROM notification_filter ORDER BY appName ASC")
    fun getAllFilters(): Flow<List<NotificationFilterEntity>>

    @Query("SELECT * FROM notification_filter WHERE enabled = 1")
    suspend fun getEnabledFilters(): List<NotificationFilterEntity>

    @Query("SELECT packageName FROM notification_filter")
    suspend fun getAllPackageNames(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(filters: List<NotificationFilterEntity>)

    @Query("UPDATE notification_filter SET enabled = :enabled")
    suspend fun setAllEnabled(enabled: Boolean)

    @Query("UPDATE notification_filter SET enabled = :enabled WHERE packageName = :packageName")
    suspend fun setEnabled(packageName: String, enabled: Boolean)

    @Query("SELECT COUNT(*) FROM notification_filter")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM notification_filter WHERE enabled = 1")
    suspend fun getEnabledCount(): Int
}
