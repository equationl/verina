package lv.aki.verina.service.retry

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import lv.aki.verina.data.db.AppDatabase
import lv.aki.verina.data.db.WebhookRetryEntity
import lv.aki.verina.service.action.WebhookExecutor
import lv.aki.verina.service.notification.WebhookFailureNotifier
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WebhookRetryManager(private val context: Context) {

    companion object {
        private const val TAG = "WebhookRetryManager"
        private const val CHECK_INTERVAL_MS = 60_000L // 每分钟检查一次

        // 渐进式延时（毫秒）：1分钟, 5分钟, 10分钟, 20分钟, 30分钟, 45分钟, 1小时, 1.5小时, 2小时
        private val RETRY_DELAYS = longArrayOf(
            TimeUnit.MINUTES.toMillis(1),
            TimeUnit.MINUTES.toMillis(5),
            TimeUnit.MINUTES.toMillis(10),
            TimeUnit.MINUTES.toMillis(20),
            TimeUnit.MINUTES.toMillis(30),
            TimeUnit.MINUTES.toMillis(45),
            TimeUnit.HOURS.toMillis(1),
            TimeUnit.MINUTES.toMillis(90),
            TimeUnit.HOURS.toMillis(2)
        )

        @Volatile
        private var INSTANCE: WebhookRetryManager? = null

        fun getInstance(context: Context): WebhookRetryManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WebhookRetryManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val db = AppDatabase.getInstance(context)
    private val retryDao = db.webhookRetryDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var checkJob: Job? = null

    fun start() {
        if (checkJob?.isActive == true) return

        checkJob = scope.launch {
            Log.i(TAG, "Webhook retry manager started")
            while (isActive) {
                try {
                    processPendingRetries()
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing retries", e)
                }
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        checkJob?.cancel()
        checkJob = null
        Log.i(TAG, "Webhook retry manager stopped")
    }

    suspend fun enqueueRetry(
        actionId: Long,
        url: String,
        httpMethod: String,
        headers: String,
        body: String?,
        variables: Map<String, String>,
        error: String?
    ) {
        val variablesJson = JSONObject(variables as Map<*, *>).toString()
        val now = System.currentTimeMillis()

        val retryEntity = WebhookRetryEntity(
            actionId = actionId,
            url = url,
            httpMethod = httpMethod,
            headers = headers,
            body = body,
            variablesJson = variablesJson,
            status = "PENDING",
            retryCount = 0,
            maxRetries = RETRY_DELAYS.size,
            nextRetryAt = now + RETRY_DELAYS[0],
            scheduledAt = now,
            lastError = error,
            createdAt = now
        )

        val id = retryDao.insert(retryEntity)
        Log.i(TAG, "Enqueued webhook retry #$id for action $actionId, next retry at ${retryEntity.nextRetryAt}")
    }

    private suspend fun processPendingRetries() {
        val currentTime = System.currentTimeMillis()
        val pendingRetries = retryDao.getPendingRetries(currentTime)

        if (pendingRetries.isEmpty()) return

        Log.i(TAG, "Processing ${pendingRetries.size} pending retries")

        for (retry in pendingRetries) {
            try {
                retryDao.updateStatus(retry.id, "PROCESSING")
                processRetry(retry)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing retry ${retry.id}", e)
                handleRetryFailure(retry, e.message)
            }
        }
    }

    private suspend fun processRetry(retry: WebhookRetryEntity) {
        val variables = parseVariables(retry.variablesJson)
        val action = createActionEntity(retry)

        try {
            WebhookExecutor.execute(action, variables)
            // 成功，删除重试记录
            retryDao.updateStatus(retry.id, "COMPLETED")
            Log.i(TAG, "Retry ${retry.id} succeeded, removing from queue")
            // 清理已完成的记录
            scope.launch { cleanupCompletedRetries() }
        } catch (e: Exception) {
            Log.w(TAG, "Retry ${retry.id} failed: ${e.message}")
            handleRetryFailure(retry, e.message)
        }
    }

    private suspend fun handleRetryFailure(retry: WebhookRetryEntity, error: String?) {
        val newRetryCount = retry.retryCount + 1

        if (newRetryCount >= retry.maxRetries) {
            // 达到最大重试次数，标记为耗尽
            retryDao.markExhausted(retry.id, error)
            Log.w(TAG, "Retry ${retry.id} exhausted after $newRetryCount attempts")

            // 发送通知
            withContext(Dispatchers.Main) {
                WebhookFailureNotifier.showFailureNotification(
                    context = context,
                    actionId = retry.actionId,
                    url = retry.url,
                    retryCount = newRetryCount,
                    error = error
                )
            }
        } else {
            // 计算下次重试时间
            val delayMs = RETRY_DELAYS.getOrElse(newRetryCount) { RETRY_DELAYS.last() }
            val nextRetryAt = System.currentTimeMillis() + delayMs

            val updatedRetry = retry.copy(
                retryCount = newRetryCount,
                nextRetryAt = nextRetryAt,
                status = "PENDING",
                lastError = error
            )
            retryDao.update(updatedRetry)
            Log.i(TAG, "Retry ${retry.id} scheduled for attempt $newRetryCount at $nextRetryAt")
        }
    }

    private fun parseVariables(json: String): Map<String, String> {
        return try {
            val obj = JSONObject(json)
            buildMap {
                obj.keys().forEach { key -> put(key, obj.getString(key)) }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun createActionEntity(retry: WebhookRetryEntity): lv.aki.verina.data.db.ActionEntity {
        return lv.aki.verina.data.db.ActionEntity(
            id = retry.actionId,
            ruleId = 0, // 不需要
            httpMethod = retry.httpMethod,
            url = retry.url,
            headers = retry.headers,
            body = retry.body
        )
    }

    private suspend fun cleanupCompletedRetries() {
        val oneDayAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
        retryDao.deleteOldExhausted(oneDayAgo)
    }

    suspend fun getQueueStatus(): Pair<Int, Int> {
        val pending = retryDao.getPendingCount()
        val exhausted = retryDao.getExhaustedCount()
        return Pair(pending, exhausted)
    }
}
