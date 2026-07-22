package lv.aki.verina.data.db

import androidx.room.Embedded
import androidx.room.Relation

data class RuleWithActions(
    @Embedded val rule: RuleEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "ruleId"
    )
    val actions: List<ActionEntity>
)
