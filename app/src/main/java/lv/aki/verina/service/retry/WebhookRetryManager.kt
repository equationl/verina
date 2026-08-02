package lv.aki.verina.service.retry

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import lv.aki.verina.data.db.AppDatabase
import lv.aki.verina.data.db.WebhookRetryEntity
import lv.aki.verina.data.repository.TransferRecordStore
import lv.aki.verina.service.action.WebhookExecutionResult
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

        val MAX_ATTEMPTS: Int = RETRY_DELAYS.size + 1
    }

    private val db = AppDatabase.getInstance(context)
    private val retryDao = db.webhookRetryDao()
    private val recordStore = TransferRecordStore(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var checkJob: Job? = null

    fun start() {
        if (checkJob?.isActive == true) return

        checkJob = scope.launch {
            Log.i(TAG, "Webhook retry manager started")
            retryDao.trimExhausted()
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
        transferRecordId: Long?,
        keepRecord: Boolean,
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
            transferRecordId = transferRecordId,
            keepRecord = keepRecord,
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
                handleRetryFailure(
                    retry,
                    WebhookExecutionResult(
                        requestUrl = retry.url,
                        error = e.message ?: e.javaClass.simpleName
                    )
                )
            }
        }
    }

    private suspend fun processRetry(retry: WebhookRetryEntity) {
        val variables = parseVariables(retry.variablesJson)
        val action = createActionEntity(retry)

        val result = WebhookExecutor.execute(action, variables)
        if (result.isSuccessful) {
            safelyFinishRecord(retry, result, retry.retryCount + 1, exhausted = false)
            // 成功，删除重试记录
            retryDao.updateStatus(retry.id, "COMPLETED")
            Log.i(TAG, "Retry ${retry.id} succeeded, removing from queue")
            // 清理已完成的记录
            scope.launch { cleanupCompletedRetries() }
        } else {
            Log.w(TAG, "Retry ${retry.id} failed: ${result.error}")
            handleRetryFailure(retry, result)
        }
    }

    private suspend fun handleRetryFailure(retry: WebhookRetryEntity, result: WebhookExecutionResult) {
        val newRetryCount = retry.retryCount + 1
        val error = result.error

        if (newRetryCount >= retry.maxRetries) {
            // 达到最大重试次数，标记为耗尽
            retryDao.markExhaustedAndTrim(retry.id, newRetryCount, error)
            val failureRecordId = safelyFinishRecord(
                retry = retry,
                result = result,
                retryCount = newRetryCount,
                exhausted = true
            ) ?: retry.transferRecordId
            Log.w(TAG, "Retry ${retry.id} exhausted after $newRetryCount attempts")

            // 发送通知
            withContext(Dispatchers.Main) {
                if (failureRecordId != null) {
                    WebhookFailureNotifier.showFailureNotification(
                        context = context,
                        failureRecordId = failureRecordId,
                        url = result.requestUrl,
                        httpMethod = retry.httpMethod,
                        headers = JSONObject(result.requestHeaders).toString(),
                        body = result.requestBody,
                        variablesJson = retry.variablesJson,
                        retryCount = newRetryCount,
                        error = error
                    )
                } else {
                    WebhookFailureNotifier.showBatchFailureNotification(context, 1)
                }
            }
        } else {
            val recordId = safelyFinishRecord(retry, result, newRetryCount, exhausted = false)
            // 计算下次重试时间
            val delayMs = RETRY_DELAYS.getOrElse(newRetryCount) { RETRY_DELAYS.last() }
            val nextRetryAt = System.currentTimeMillis() + delayMs

            val updatedRetry = retry.copy(
                transferRecordId = retry.transferRecordId ?: recordId,
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

    private suspend fun safelyFinishRecord(
        retry: WebhookRetryEntity,
        result: WebhookExecutionResult,
        retryCount: Int,
        exhausted: Boolean
    ): Long? = try {
        recordStore.finishRetry(retry, result, retryCount, exhausted)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to update transfer record for retry ${retry.id}", e)
        null
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
        retryDao.deleteCompleted()
    }

    suspend fun getQueueStatus(): Pair<Int, Int> {
        val pending = retryDao.getPendingCount()
        val exhausted = retryDao.getExhaustedCount()
        return Pair(pending, exhausted)
    }
}
