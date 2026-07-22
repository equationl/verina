package lv.aki.verina.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val eventType: String,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val eventConfig: String = "{}"
)
