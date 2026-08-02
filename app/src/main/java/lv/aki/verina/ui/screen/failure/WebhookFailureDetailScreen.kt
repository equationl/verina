package lv.aki.verina.ui.screen.failure

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import lv.aki.verina.data.db.TransferRecordEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebhookFailureDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: WebhookFailureDetailViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("转发记录详情") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (state.record != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除记录")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            state.isLoading -> Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) { CircularProgressIndicator() }
            state.record == null -> Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) { Text("该转发记录不存在或已被清理") }
            else -> TransferDetail(state.record!!, Modifier.padding(innerPadding))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这条记录？") },
            text = { Text("删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(onNavigateBack) }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun TransferDetail(record: TransferRecordEntity, modifier: Modifier = Modifier) {
    SelectionContainer {
        Column(
            modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DetailField("状态", statusLabel(record.status), isError = record.status == "FAILED")
            DetailField("事件类型", eventTypeLabel(record.eventType))
            DetailField("规则", record.ruleName.ifBlank { "（历史记录未保存）" })
            DetailField("触发时间", formatTime(record.createdAt))
            DetailField("最后更新", formatTime(record.updatedAt))
            DetailField("尝试次数", "${record.attemptCount} / ${record.maxAttempts}")
            record.error?.let { DetailField("错误信息", it, isError = true) }

            SectionLabel("请求")
            DetailField("HTTP 方法", record.httpMethod)
            DetailField("URL", record.requestUrl, monospace = true)
            DetailField("Headers", prettyJson(record.requestHeaders), monospace = true)
            DetailField("Body", record.requestBody?.takeIf { it.isNotBlank() } ?: "（无）", monospace = true)
            DetailField("事件变量", prettyJson(record.variablesJson), monospace = true)

            SectionLabel("响应")
            DetailField("状态码", record.responseCode?.toString() ?: "（未收到响应）")
            DetailField("Headers", prettyJson(record.responseHeaders), monospace = true)
            DetailField("Body", record.responseBody?.takeIf { it.isNotEmpty() } ?: "（无）", monospace = true)

            SectionLabel("标识")
            DetailField("记录 ID", record.id.toString())
            DetailField("Rule ID", record.ruleId.toString())
            DetailField("Action ID", record.actionId.toString())
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun DetailField(
    label: String,
    value: String,
    monospace: Boolean = false,
    isError: Boolean = false
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                modifier = if (monospace) Modifier.horizontalScroll(rememberScrollState()) else Modifier,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun prettyJson(value: String): String = try {
    org.json.JSONObject(value).toString(2)
} catch (_: Exception) {
    value
}
