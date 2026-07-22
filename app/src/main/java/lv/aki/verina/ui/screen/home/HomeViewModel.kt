package lv.aki.verina.ui.screen.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import lv.aki.verina.data.db.AppDatabase
import lv.aki.verina.data.db.RuleEntity
import lv.aki.verina.data.db.RuleWithActions
import lv.aki.verina.data.repository.RuleRepository

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RuleRepository(AppDatabase.getInstance(application))

    val rules = repository.getAllRulesWithActions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleRule(ruleId: Long, enabled: Boolean) {
        viewModelScope.launch {
            repository.setRuleEnabled(ruleId, enabled)
        }
    }

    fun deleteRule(rule: RuleWithActions) {
        viewModelScope.launch {
            repository.deleteRule(rule.rule)
        }
    }
}
