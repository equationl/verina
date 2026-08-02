package lv.aki.verina.service.action

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lv.aki.verina.data.db.ActionEntity
import lv.aki.verina.engine.TemplateEngine
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class WebhookTestResult(
    val requestUrl: String,
    val statusCode: Int = -1,
    val responseHeaders: Map<String, String> = emptyMap(),
    val responseBody: String = "",
    val error: String? = null
)

data class WebhookExecutionResult(
    val requestUrl: String,
    val requestHeaders: Map<String, String> = emptyMap(),
    val requestBody: String? = null,
    val statusCode: Int? = null,
    val responseHeaders: Map<String, String> = emptyMap(),
    val responseBody: String? = null,
    val error: String? = null
) {
    val isSuccessful: Boolean
        get() = error == null && statusCode != null && statusCode in 200..299
}

object WebhookExecutor {

    private const val TAG = "WebhookExecutor"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun execute(action: ActionEntity, variables: Map<String, String>): WebhookExecutionResult {
        return withContext(Dispatchers.IO) {
            var url = action.url
            var headers = emptyMap<String, String>()
            var renderedBody: String? = null
            try {
                url = TemplateEngine.render(action.url, variables)
                val headersJson = TemplateEngine.renderHeaders(action.headers, variables)
                headers = parseHeaders(headersJson)
                renderedBody = action.body?.let { TemplateEngine.render(it, variables) }

                val request = when (action.httpMethod.uppercase()) {
                    "POST" -> buildPostRequest(url, headers, renderedBody)
                    else -> buildGetRequest(url, headers)
                }

                client.newCall(request).execute().use { response ->
                    val responseHeaders = response.headers.names().associateWith { name ->
                        response.headers.values(name).joinToString("\n")
                    }
                    val responseBody = response.body?.string()
                    Log.i(TAG, "Webhook ${action.httpMethod} $url -> ${response.code}")
                    WebhookExecutionResult(
                        requestUrl = url,
                        requestHeaders = headers,
                        requestBody = renderedBody,
                        statusCode = response.code,
                        responseHeaders = responseHeaders,
                        responseBody = responseBody,
                        error = if (response.isSuccessful) null else "Webhook request failed with status ${response.code}"
                    )
                }
            } catch (e: Exception) {
                WebhookExecutionResult(
                    requestUrl = url,
                    requestHeaders = headers,
                    requestBody = renderedBody,
                    error = e.message ?: e.javaClass.simpleName
                )
            }
        }
    }

    suspend fun executeForTest(action: ActionEntity, variables: Map<String, String>): WebhookTestResult {
        return withContext(Dispatchers.IO) {
            val url = try {
                TemplateEngine.render(action.url, variables)
            } catch (e: Exception) {
                return@withContext WebhookTestResult(requestUrl = action.url, error = "URL 渲染失败: ${e.message}")
            }

            try {
                val headersJson = TemplateEngine.renderHeaders(action.headers, variables)
                val headers = parseHeaders(headersJson)

                val request = when (action.httpMethod.uppercase()) {
                    "POST" -> buildPostRequest(
                        url,
                        headers,
                        action.body?.let { TemplateEngine.render(it, variables) }
                    )
                    else -> buildGetRequest(url, headers)
                }

                client.newCall(request).execute().use { response ->
                    val respHeaders = response.headers.names().associateWith { name ->
                        response.headers.values(name).joinToString("\n")
                    }
                    val respBody = response.body?.string()?.take(8192) ?: ""

                    WebhookTestResult(
                        requestUrl = url,
                        statusCode = response.code,
                        responseHeaders = respHeaders,
                        responseBody = respBody
                    )
                }
            } catch (e: Exception) {
                WebhookTestResult(requestUrl = url, error = e.message ?: "未知错误")
            }
        }
    }

    private fun buildGetRequest(url: String, headers: Map<String, String>): Request {
        return Request.Builder()
            .url(url)
            .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
            .get()
            .build()
    }

    private fun buildPostRequest(
        url: String,
        headers: Map<String, String>,
        renderedBody: String?
    ): Request {
        val contentType = headers["Content-Type"] ?: "application/json"
        val requestBody = (renderedBody ?: "").toRequestBody(contentType.toMediaTypeOrNull())

        return Request.Builder()
            .url(url)
            .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
            .post(requestBody)
            .build()
    }

    private fun parseHeaders(json: String): Map<String, String> {
        return try {
            val obj = JSONObject(json)
            buildMap {
                obj.keys().forEach { key -> put(key, obj.getString(key)) }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
