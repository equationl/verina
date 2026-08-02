package lv.aki.verina.ui.screen.failure

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import lv.aki.verina.data.db.TransferRecordEntity
import lv.aki.verina.data.model.EventType
import lv.aki.verina.data.repository.TransferRecordStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class ClearAction { VISIBLE, ALL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebhookFailureListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    viewModel: WebhookFailureListViewModel = viewModel()
) {
    val records by viewModel.records.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val statuses by viewModel.availableStatuses.collectAsStateWithLifecycle()
    val eventTypes by viewModel.availableEventTypes.collectAsStateWithLifecycle()
    val methods by viewModel.availableMethods.collectAsStateWithLifecycle()
    var clearMenuExpanded by remember { mutableStateOf(false) }
    var clearAction by remember { mutableStateOf<ClearAction?>(null) }
    var confirmSelectedDelete by remember { mutableStateOf(false) }
    var filtersVisible by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val showBackToTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 600
        }
    }
    val selectionMode = selectedIds.isNotEmpty()

    LaunchedEffect(listState) {
        var previousPosition = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { currentPosition ->
                val (currentIndex, currentOffset) = currentPosition
                val (previousIndex, previousOffset) = previousPosition
                when {
                    currentIndex == 0 && currentOffset == 0 -> filtersVisible = true
                    currentIndex > previousIndex ||
                        (currentIndex == previousIndex && currentOffset > previousOffset + 4) -> {
                        filtersVisible = false
                        focusManager.clearFocus()
                    }
                    currentIndex < previousIndex ||
                        (currentIndex == previousIndex && currentOffset < previousOffset - 4) -> {
                        filtersVisible = true
                    }
                }
                previousPosition = currentPosition
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectionMode) "已选择 ${selectedIds.size} 条" else "转发记录") },
                navigationIcon = {
                    IconButton(onClick = if (selectionMode) viewModel::clearSelection else onNavigateBack) {
                        Icon(
                            if (selectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (selectionMode) "退出选择" else "返回"
                        )
                    }
                },
                actions = {
                    if (selectionMode) {
                        IconButton(onClick = viewModel::selectAllVisible) {
                            Icon(Icons.Default.SelectAll, contentDescription = "全选筛选结果")
                        }
                        IconButton(onClick = { confirmSelectedDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除所选")
                        }
                    } else {
                        IconButton(onClick = { clearMenuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "清理记录")
                        }
                        DropdownMenu(
                            expanded = clearMenuExpanded,
                            onDismissRequest = { clearMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("清空当前筛选结果") },
                                leadingIcon = { Icon(Icons.Default.DeleteSweep, null) },
                                enabled = records.isNotEmpty(),
                                onClick = {
                                    clearMenuExpanded = false
                                    clearAction = ClearAction.VISIBLE
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("清空全部记录") },
                                leadingIcon = { Icon(Icons.Default.DeleteSweep, null) },
                                onClick = {
                                    clearMenuExpanded = false
                                    clearAction = ClearAction.ALL
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = showBackToTop && records.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        filtersVisible = true
                        coroutineScope.launch { listState.animateScrollToItem(0) }
                    }
                ) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "返回顶部")
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            AnimatedVisibility(
                visible = filtersVisible || records.isEmpty(),
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
            ) {
                FilterPanel(
                    filters = filters,
                    statuses = statuses,
                    eventTypes = eventTypes,
                    methods = methods,
                    viewModel = viewModel
                )
            }

            if (records.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (filters == RecordFilters()) "暂无转发记录" else "没有符合筛选条件的记录",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (filters != RecordFilters()) {
                            TextButton(onClick = viewModel::resetFilters) { Text("重置筛选") }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            "${records.size} 条结果 · 长按记录可多选",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(records, key = { it.id }) { record ->
                        TransferRecordCard(
                            record = record,
                            selected = record.id in selectedIds,
                            selectionMode = selectionMode,
                            onClick = {
                                if (selectionMode) viewModel.toggleSelection(record.id)
                                else onNavigateToDetail(record.id)
                            },
                            onLongClick = { viewModel.toggleSelection(record.id) }
                        )
                    }
                }
            }
        }
    }

    clearAction?.let { action ->
        AlertDialog(
            onDismissRequest = { clearAction = null },
            title = { Text(if (action == ClearAction.ALL) "清空全部记录？" else "清空筛选结果？") },
            text = {
                Text(
                    if (action == ClearAction.ALL) "所有转发记录都将被永久删除。正在重试的请求不会被取消，后续结果可能重新出现在列表中。"
                    else "当前筛选出的 ${records.size} 条记录将被永久删除。正在重试的请求不会被取消。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (action == ClearAction.ALL) viewModel.clearAll() else viewModel.clearVisible()
                    clearAction = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { clearAction = null }) { Text("取消") } }
        )
    }

    if (confirmSelectedDelete) {
        AlertDialog(
            onDismissRequest = { confirmSelectedDelete = false },
            title = { Text("删除所选记录？") },
            text = { Text("已选择的 ${selectedIds.size} 条记录将被永久删除。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSelected()
                    confirmSelectedDelete = false
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSelectedDelete = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun FilterPanel(
    filters: RecordFilters,
    statuses: List<String>,
    eventTypes: List<String>,
    methods: List<String>,
    viewModel: WebhookFailureListViewModel
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = filters.query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("搜索 URL、规则、错误、变量或响应") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = if (filters.query.isNotEmpty()) {
                { IconButton(onClick = { viewModel.setQuery("") }) { Icon(Icons.Default.Close, "清除搜索") } }
            } else null
        )
        FilterRow("状态", statuses, filters.status, viewModel::setStatus, ::statusLabel)
        FilterRow("事件", eventTypes, filters.eventType, viewModel::setEventType, ::eventTypeLabel)
        FilterRow("方法", methods, filters.httpMethod, viewModel::setHttpMethod) { it }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("排序", style = MaterialTheme.typography.labelLarge)
            RecordSort.entries.forEach { sort ->
                FilterChip(
                    selected = filters.sort == sort,
                    onClick = { viewModel.setSort(sort) },
                    label = { Text(sort.label) }
                )
            }
            AssistChip(
                onClick = viewModel::toggleSortDirection,
                label = { Text(if (filters.descending) "降序" else "升序") },
                leadingIcon = {
                    Icon(
                        if (filters.descending) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                        contentDescription = null
                    )
                }
            )
            if (filters != RecordFilters()) {
                AssistChip(
                    onClick = viewModel::resetFilters,
                    label = { Text("重置") },
                    leadingIcon = { Icon(Icons.Default.FilterAltOff, null) }
                )
            }
        }
    }
}

@Composable
private fun FilterRow(
    label: String,
    options: List<String>,
    selected: String?,
    onSelected: (String?) -> Unit,
    optionLabel: (String) -> String
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        FilterChip(
            selected = selected == null,
            onClick = { onSelected(null) },
            label = { Text("全部") }
        )
        options.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelected(option) },
                label = { Text(optionLabel(option)) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransferRecordCard(
    record: TransferRecordEntity,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onClick() })
                Spacer(Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        statusLabel(record.status),
                        style = MaterialTheme.typography.labelLarge,
                        color = statusColor(record.status)
                    )
                    Text(
                        eventTypeLabel(record.eventType),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    "${record.httpMethod} ${record.requestUrl}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (record.ruleName.isNotBlank()) {
                    Text("规则：${record.ruleName}", style = MaterialTheme.typography.bodyMedium)
                }
                record.error?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    "尝试 ${record.attemptCount}/${record.maxAttempts} · ${formatTime(record.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!selectionMode) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "查看详情")
            }
        }
    }
}

@Composable
private fun statusColor(status: String) = when (status) {
    TransferRecordStore.STATUS_SUCCESS -> MaterialTheme.colorScheme.primary
    TransferRecordStore.STATUS_FAILED -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.tertiary
}

internal fun statusLabel(status: String): String = when (status) {
    TransferRecordStore.STATUS_SUCCESS -> "成功"
    TransferRecordStore.STATUS_FAILED -> "失败"
    TransferRecordStore.STATUS_RETRYING -> "重试中"
    else -> status
}

internal fun eventTypeLabel(eventType: String): String =
    EventType.entries.firstOrNull { it.name == eventType }?.displayName
        ?: if (eventType == "UNKNOWN") "历史记录" else eventType

internal fun formatTime(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
