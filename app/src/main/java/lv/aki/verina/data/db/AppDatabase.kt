package lv.aki.verina.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import lv.aki.verina.data.model.NotificationFilterEntity

@Database(
    entities = [RuleEntity::class, ActionEntity::class, WebhookRetryEntity::class, NotificationFilterEntity::class, TransferRecordEntity::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ruleDao(): RuleDao
    abstract fun webhookRetryDao(): WebhookRetryDao
    abstract fun notificationFilterDao(): NotificationFilterDao
    abstract fun transferRecordDao(): TransferRecordDao

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

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE webhook_retry_queue ADD COLUMN transferRecordId INTEGER")
                db.execSQL("ALTER TABLE webhook_retry_queue ADD COLUMN keepRecord INTEGER NOT NULL DEFAULT 0")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS transfer_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        ruleId INTEGER NOT NULL,
                        ruleName TEXT NOT NULL,
                        actionId INTEGER NOT NULL,
                        eventType TEXT NOT NULL,
                        status TEXT NOT NULL,
                        httpMethod TEXT NOT NULL,
                        requestUrl TEXT NOT NULL,
                        requestHeaders TEXT NOT NULL,
                        requestBody TEXT,
                        variablesJson TEXT NOT NULL,
                        responseCode INTEGER,
                        responseHeaders TEXT NOT NULL,
                        responseBody TEXT,
                        error TEXT,
                        attemptCount INTEGER NOT NULL,
                        maxAttempts INTEGER NOT NULL,
                        keepAll INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transfer_records_status ON transfer_records (status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transfer_records_eventType ON transfer_records (eventType)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transfer_records_createdAt ON transfer_records (createdAt)")
                db.execSQL("""
                    INSERT INTO transfer_records (
                        ruleId, ruleName, actionId, eventType, status, httpMethod,
                        requestUrl, requestHeaders, requestBody, variablesJson,
                        responseCode, responseHeaders, responseBody, error,
                        attemptCount, maxAttempts, keepAll, createdAt, updatedAt
                    )
                    SELECT 0, '', actionId, 'UNKNOWN', 'FAILED', httpMethod,
                        url, headers, body, variablesJson,
                        NULL, '{}', NULL, lastError,
                        retryCount + 1, maxRetries + 1, 0, createdAt, createdAt
                    FROM webhook_retry_queue WHERE status = 'EXHAUSTED'
                """.trimIndent())
                db.execSQL("""
                    DELETE FROM transfer_records
                    WHERE keepAll = 0 AND status = 'FAILED' AND id NOT IN (
                        SELECT id FROM transfer_records
                        WHERE keepAll = 0 AND status = 'FAILED'
                        ORDER BY createdAt DESC, id DESC LIMIT 1000
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
