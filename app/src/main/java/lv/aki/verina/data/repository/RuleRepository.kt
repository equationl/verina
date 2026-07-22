package lv.aki.verina.data.repository

import androidx.room.withTransaction
import lv.aki.verina.data.db.ActionEntity
import lv.aki.verina.data.db.AppDatabase
import lv.aki.verina.data.db.RuleEntity
import lv.aki.verina.data.db.RuleWithActions
import kotlinx.coroutines.flow.Flow

class RuleRepository(private val db: AppDatabase) {

    private val dao = db.ruleDao()

    fun getAllRulesWithActions(): Flow<List<RuleWithActions>> =
        dao.getAllRulesWithActions()

    suspend fun getRuleWithActions(ruleId: Long): RuleWithActions? =
        dao.getRuleWithActions(ruleId)

    suspend fun getEnabledRulesForEvent(eventType: String): List<RuleWithActions> =
        dao.getEnabledRulesForEvent(eventType)

    suspend fun saveRuleWithActions(rule: RuleEntity, actions: List<ActionEntity>) {
        db.withTransaction {
            val ruleId = if (rule.id == 0L) {
                dao.insertRule(rule)
            } else {
                dao.updateRule(rule)
                rule.id
            }
            dao.deleteActionsForRule(ruleId)
            dao.insertActions(actions.map { it.copy(ruleId = ruleId) })
        }
    }

    suspend fun deleteRule(rule: RuleEntity) =
        dao.deleteRule(rule)

    suspend fun setRuleEnabled(ruleId: Long, enabled: Boolean) =
        dao.setRuleEnabled(ruleId, enabled)
}
