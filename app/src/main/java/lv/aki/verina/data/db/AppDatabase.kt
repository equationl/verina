package lv.aki.verina.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import lv.aki.verina.data.model.NotificationFilterEntity

@Database(
    entities = [RuleEntity::class, ActionEntity::class, WebhookRetryEntity::class, NotificationFilterEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ruleDao(): RuleDao
    abstract fun webhookRetryDao(): WebhookRetryDao
    abstract fun notificationFilterDao(): NotificationFilterDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE actions ADD COLUMN actionType TEXT NOT NULL DEFAULT 'WEBHOOK'")
                db.execSQL("ALTER TABLE actions ADD COLUMN actionConfig TEXT NOT NULL DEFAULT '{}'")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS webhook_retry_queue (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        actionId INTEGER NOT NULL,
                        url TEXT NOT NULL,
                        httpMethod TEXT NOT NULL,
                        headers TEXT NOT NULL DEFAULT '{}',
                        body TEXT,
                        variablesJson TEXT NOT NULL DEFAULT '{}',
                        status TEXT NOT NULL DEFAULT 'PENDING',
                        retryCount INTEGER NOT NULL DEFAULT 0,
                        maxRetries INTEGER NOT NULL DEFAULT 10,
                        nextRetryAt INTEGER NOT NULL DEFAULT 0,
                        scheduledAt INTEGER NOT NULL DEFAULT 0,
                        lastError TEXT,
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_webhook_retry_queue_scheduledAt ON webhook_retry_queue (scheduledAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_webhook_retry_queue_status ON webhook_retry_queue (status)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS notification_filter (
                        packageName TEXT NOT NULL PRIMARY KEY,
                        appName TEXT NOT NULL,
                        enabled INTEGER NOT NULL DEFAULT 1
                    )
                """.trimIndent())
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "verina_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
