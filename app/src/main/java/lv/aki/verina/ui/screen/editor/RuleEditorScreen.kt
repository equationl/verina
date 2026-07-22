package lv.aki.verina.ui.screen.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import lv.aki.verina.data.model.ActionType
import lv.aki.verina.data.model.EventType
import lv.aki.verina.data.model.HttpMethod
import lv.aki.verina.service.action.WebhookTestResult

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RuleEditorScreen(
    ruleId: Long,
    onNavigateBack: () -> Unit,
    viewModel: RuleEditorViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(ruleId) {
        viewModel.loadRule(ruleId)
    }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onNavigateBack()
    }

    val formValid = state.name.isNotBlank() && state.actions.any { it.url.isNotBlank() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "新建规则" else "编辑规则") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.testActions() },
                        enabled = formValid && !state.isTesting
                    ) {
                        if (state.isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text("测试")
                    }
                    TextButton(
                        onClick = { viewModel.save() },
                        enabled = formValid
                    ) {
                        Text("保存")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = { viewModel.updateName(it) },
                label = { Text("规则名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("触发事件", style = MaterialTheme.typography.titleMedium)
            EventTypeSelector(
                selected = state.eventType,
                onSelected = { viewModel.updateEventType(it) }
            )

            if (state.eventType == EventType.BATTERY_LEVEL) {
                BatteryThresholdSelector(
                    threshold = state.batteryThreshold,
                    direction = state.batteryDirection,
                    onThresholdChange = { viewModel.updateBatteryThreshold(it) },
                    onDirectionChange = { viewModel.updateBatteryDirection(it) }
                )
            }

            Text("可用插值变量", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.eventType.availableVariables.forEach { variable ->
                    AssistChip(
                        onClick = { },
                        label = { Text("{{$variable}}") }
                    )
                }
            }

            HorizontalDivider()

            Text("动作列表", style = MaterialTheme.typography.titleMedium)

            state.actions.forEachIndexed { index, action ->
                ActionEditor(
                    action = action,
                    index = index,
                    canDelete = state.actions.size > 1,
                    onUpdate = { viewModel.updateAction(index, it) },
                    onDelete = { viewModel.removeAction(index) }
                )
            }

            FilledTonalButton(
                onClick = { viewModel.addAction() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("添加动作")
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // Test results bottom sheet
    state.testResults?.let { results ->
        TestResultsSheet(
            results = results,
            onDismiss = { viewModel.dismissTestResults() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TestResultsSheet(
    results: List<WebhookTestResult>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("测试结果", style = MaterialTheme.typography.titleLarge)

            results.forEachIndexed { index, result ->
                TestResultCard(index = index, result = result)
            }

            if (results.isEmpty()) {
                Text(
                    "没有可测试的动作",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TestResultCard(index: Int, result: WebhookTestResult) {
    var headersExpanded by remember { mutableStateOf(false) }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("动作 ${index + 1}", style = MaterialTheme.typography.titleMedium)

            // Request URL
            Text("请求", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                result.requestUrl,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )

            if (result.error != null) {
                HorizontalDivider()
                Text(
                    "错误: ${result.error}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                HorizontalDivider()

                // Status code
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("状态码: ", style = MaterialTheme.typography.labelMedium)
                    val statusColor = when (result.statusCode) {
                        in 200..299 -> MaterialTheme.colorScheme.primary
                        in 300..399 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.error
                    }
                    Text(
                        "${result.statusCode}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }

                // Response headers (collapsible)
                if (result.responseHeaders.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { headersExpanded = !headersExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("响应头 (${result.responseHeaders.size})", style = MaterialTheme.typography.labelMedium)
                        Icon(
                            if (headersExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    AnimatedVisibility(visible = headersExpanded) {
                        Column {
                            result.responseHeaders.forEach { (k, v) ->
                                Text(
                                    "$k: $v",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                // Response body
                if (result.responseBody.isNotBlank()) {
                    Text("响应体", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            result.responseBody,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventTypeSelector(
    selected: EventType,
    onSelected: (EventType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            supportingText = { Text(selected.description) }
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            EventType.entries.forEach { eventType ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(eventType.displayName)
                            Text(
                                eventType.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        onSelected(eventType)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionEditor(
    action: ActionUiState,
    index: Int,
    canDelete: Boolean,
    onUpdate: (ActionUiState) -> Unit,
    onDelete: () -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("动作 ${index + 1}", style = MaterialTheme.typography.titleMedium)
                if (canDelete) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "删除动作",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Action type selector
            ActionTypeSelector(
                selected = action.actionType,
                onSelected = { onUpdate(action.copy(actionType = it)) }
            )

            // Render form based on action type
            when (action.actionType) {
                ActionType.WEBHOOK -> WebhookForm(action = action, onUpdate = onUpdate)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionTypeSelector(
    selected: ActionType,
    onSelected: (ActionType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("动作类型") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            ActionType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.displayName) },
                    onClick = {
                        onSelected(type)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun WebhookForm(
    action: ActionUiState,
    onUpdate: (ActionUiState) -> Unit
) {
    // HTTP Method selector
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        HttpMethod.entries.forEachIndexed { i, method ->
            SegmentedButton(
                selected = action.httpMethod == method,
                onClick = { onUpdate(action.copy(httpMethod = method)) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = i,
                    count = HttpMethod.entries.size
                )
            ) {
                Text(method.name)
            }
        }
    }

    OutlinedTextField(
        value = action.url,
        onValueChange = { onUpdate(action.copy(url = it)) },
        label = { Text("URL") },
        placeholder = { Text("https://example.com/webhook") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    OutlinedTextField(
        value = action.headers,
        onValueChange = { onUpdate(action.copy(headers = it)) },
        label = { Text("Headers (JSON)") },
        placeholder = { Text("""{"Content-Type": "application/json"}""") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        maxLines = 4
    )

    if (action.httpMethod == HttpMethod.POST) {
        OutlinedTextField(
            value = action.body,
            onValueChange = { onUpdate(action.copy(body = it)) },
            label = { Text("Body") },
            placeholder = { Text("""{"from": "{{sender}}", "text": "{{message}}"}""") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 6
        )
    }
}

@Composable
private fun BatteryThresholdSelector(
    threshold: Int,
    direction: BatteryDirection,
    onThresholdChange: (Int) -> Unit,
    onDirectionChange: (BatteryDirection) -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("触发阈值", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${threshold}%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                when (direction) {
                    BatteryDirection.HIGH_TO_LOW -> "当电量从高于此值降至等于或低于此值时触发"
                    BatteryDirection.LOW_TO_HIGH -> "当电量从低于此值升至等于或高于此值时触发"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                BatteryDirection.entries.forEachIndexed { i, d ->
                    SegmentedButton(
                        selected = direction == d,
                        onClick = { onDirectionChange(d) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = i,
                            count = BatteryDirection.entries.size
                        )
                    ) {
                        Text(d.displayName)
                    }
                }
            }
            Slider(
                value = threshold.toFloat(),
                onValueChange = { onThresholdChange(it.roundToInt()) },
                valueRange = 1f..100f,
                steps = 98,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
