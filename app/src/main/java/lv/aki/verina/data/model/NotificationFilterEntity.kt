package lv.aki.verina.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notification_filter")
data class NotificationFilterEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val enabled: Boolean = true
)
