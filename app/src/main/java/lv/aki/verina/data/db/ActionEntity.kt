package lv.aki.verina.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "actions",
    foreignKeys = [
        ForeignKey(
            entity = RuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["ruleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("ruleId")]
)
data class ActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleId: Long,
    val actionType: String = "WEBHOOK",
    val httpMethod: String,
    val url: String,
    val headers: String = "{}",
    val body: String? = null,
    val order: Int = 0,
    val actionConfig: String = "{}"
)
