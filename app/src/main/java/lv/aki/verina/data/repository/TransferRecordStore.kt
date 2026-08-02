package lv.aki.verina.data.repository

import android.content.Context
import lv.aki.verina.data.db.ActionEntity
import lv.aki.verina.data.db.AppDatabase
import lv.aki.verina.data.db.RuleEntity
import lv.aki.verina.data.db.TransferRecordEntity
import lv.aki.verina.data.db.WebhookRetryEntity
import lv.aki.verina.service.action.WebhookExecutionResult
import org.json.JSONObject

class TransferRecordStore(context: Context) {
    private val dao = AppDatabase.getInstance(context).transferRecordDao()

    suspend fun saveInitial(
        rule: RuleEntity,
        action: ActionEntity,
        eventType: String,
        variables: Map<String, String>,
        result: WebhookExecutionResult,
        keepAll: Boolean,
        maxAttempts: Int
    ): Long? {
        if (result.isSuccessful && !keepAll) return null

        val now = System.currentTimeMillis()
        return dao.insert(
            TransferRecordEntity(
                ruleId = rule.id,
                ruleName = rule.name,
                actionId = action.id,
                eventType = eventType,
                status = if (result.isSuccessful) STATUS_SUCCESS else STATUS_RETRYING,
                httpMethod = action.httpMethod.uppercase(),
                requestUrl = result.requestUrl,
                requestHeaders = JSONObject(result.requestHeaders).toString(),
                requestBody = result.requestBody,
                variablesJson = JSONObject(variables).toString(),
                responseCode = result.statusCode,
                responseHeaders = JSONObject(result.responseHeaders).toString(),
                responseBody = result.responseBody,
                error = result.error,
                attemptCount = 1,
                maxAttempts = maxAttempts,
                keepAll = keepAll,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun finishRetry(
        retry: WebhookRetryEntity,
        result: WebhookExecutionResult,
        retryCount: Int,
        exhausted: Boolean
    ): Long? {
        val existing = retry.transferRecordId?.let { dao.getById(it) }
        val attemptCount = retryCount + 1
        val now = System.currentTimeMillis()

        if (result.isSuccessful && existing?.keepAll != true && !retry.keepRecord) {
            existing?.let { dao.deleteById(it.id) }
            return null
        }

        val updated = (existing ?: TransferRecordEntity(
            ruleId = 0,
            ruleName = "",
            actionId = retry.actionId,
            eventType = "UNKNOWN",
            status = STATUS_RETRYING,
            httpMethod = retry.httpMethod.uppercase(),
            requestUrl = result.requestUrl,
            variablesJson = retry.variablesJson,
            maxAttempts = retry.maxRetries + 1,
            keepAll = retry.keepRecord,
            createdAt = retry.createdAt,
            updatedAt = now
        )).copy(
            status = when {
                result.isSuccessful -> STATUS_SUCCESS
                exhausted -> STATUS_FAILED
                else -> STATUS_RETRYING
            },
            requestUrl = result.requestUrl,
            requestHeaders = JSONObject(result.requestHeaders).toString(),
            requestBody = result.requestBody,
            responseCode = result.statusCode,
            responseHeaders = JSONObject(result.responseHeaders).toString(),
            responseBody = result.responseBody,
            error = result.error,
            attemptCount = attemptCount,
            updatedAt = now
        )

        val id = if (updated.id == 0L) dao.insert(updated) else {
            dao.update(updated)
            updated.id
        }
        if (exhausted && !updated.keepAll) dao.trimFailureOnlyRecords()
        return id
    }

    companion object {
        const val STATUS_SUCCESS = "SUCCESS"
        const val STATUS_RETRYING = "RETRYING"
        const val STATUS_FAILED = "FAILED"
    }
}
