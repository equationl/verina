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

object WebhookExecutor {

    private const val TAG = "WebhookExecutor"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun execute(action: ActionEntity, variables: Map<String, String>) {
        withContext(Dispatchers.IO) {
            try {
                val url = TemplateEngine.render(action.url, variables)
                val headersJson = TemplateEngine.renderHeaders(action.headers, variables)
                val headers = parseHeaders(headersJson)

                val request = when (action.httpMethod.uppercase()) {
                    "POST" -> buildPostRequest(url, headers, action.body, variables)
                    else -> buildGetRequest(url, headers)
                }

                client.newCall(request).execute().use { response ->
                    Log.i(TAG, "Webhook ${action.httpMethod} $url -> ${response.code}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Webhook execution failed for action ${action.id}", e)
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
                    "POST" -> buildPostRequest(url, headers, action.body, variables)
                    else -> buildGetRequest(url, headers)
                }

                client.newCall(request).execute().use { response ->
                    val respHeaders = buildMap {
                        response.headers.forEach { (k, v) -> put(k, v) }
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
        body: String?,
        variables: Map<String, String>
    ): Request {
        val renderedBody = body?.let { TemplateEngine.render(it, variables) } ?: ""
        val contentType = headers["Content-Type"] ?: "application/json"
        val requestBody = renderedBody.toRequestBody(contentType.toMediaTypeOrNull())

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
