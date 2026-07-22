package lv.aki.verina.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {

    @Transaction
    @Query("SELECT * FROM rules ORDER BY createdAt DESC")
    fun getAllRulesWithActions(): Flow<List<RuleWithActions>>

    @Transaction
    @Query("SELECT * FROM rules WHERE id = :ruleId")
    suspend fun getRuleWithActions(ruleId: Long): RuleWithActions?

    @Transaction
    @Query("SELECT * FROM rules WHERE enabled = 1 AND eventType = :eventType")
    suspend fun getEnabledRulesForEvent(eventType: String): List<RuleWithActions>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: RuleEntity): Long

    @Update
    suspend fun updateRule(rule: RuleEntity)

    @Delete
    suspend fun deleteRule(rule: RuleEntity)

    @Query("UPDATE rules SET enabled = :enabled WHERE id = :ruleId")
    suspend fun setRuleEnabled(ruleId: Long, enabled: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActions(actions: List<ActionEntity>)

    @Query("DELETE FROM actions WHERE ruleId = :ruleId")
    suspend fun deleteActionsForRule(ruleId: Long)
}
