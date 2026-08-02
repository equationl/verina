package lv.aki.verina.ui.screen.failure

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import lv.aki.verina.data.db.AppDatabase
import lv.aki.verina.data.db.TransferRecordEntity

enum class RecordSort(val label: String) {
    CREATED_AT("触发时间"),
    UPDATED_AT("更新时间"),
    STATUS("状态"),
    EVENT_TYPE("事件类型"),
    HTTP_METHOD("请求方法"),
    RULE_NAME("规则名称")
}

data class RecordFilters(
    val query: String = "",
    val status: String? = null,
    val eventType: String? = null,
    val httpMethod: String? = null,
    val sort: RecordSort = RecordSort.CREATED_AT,
    val descending: Boolean = true
)

class WebhookFailureListViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getInstance(application).transferRecordDao()
    private val allRecords = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _filters = MutableStateFlow(RecordFilters())
    val filters: StateFlow<RecordFilters> = _filters.asStateFlow()
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    val records = combine(allRecords, _filters) { records, filters ->
        val filtered = records.filter { record ->
            val query = filters.query.trim()
            (filters.status == null || record.status == filters.status) &&
                (filters.eventType == null || record.eventType == filters.eventType) &&
                (filters.httpMethod == null || record.httpMethod == filters.httpMethod) &&
                (query.isEmpty() || listOf(
                    record.ruleName,
                    record.requestUrl,
                    record.error.orEmpty(),
                    record.variablesJson,
                    record.responseBody.orEmpty()
                ).any { it.contains(query, ignoreCase = true) })
        }
        val comparator = when (filters.sort) {
            RecordSort.CREATED_AT -> compareBy<TransferRecordEntity> { it.createdAt }
            RecordSort.UPDATED_AT -> compareBy { it.updatedAt }
            RecordSort.STATUS -> compareBy { it.status }
            RecordSort.EVENT_TYPE -> compareBy { it.eventType }
            RecordSort.HTTP_METHOD -> compareBy { it.httpMethod }
            RecordSort.RULE_NAME -> compareBy { it.ruleName.lowercase() }
        }.thenBy { it.id }
        filtered.sortedWith(if (filters.descending) comparator.reversed() else comparator)
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val availableStatuses = allRecords
        .map { records -> records.map { it.status }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val availableEventTypes = allRecords
        .map { records -> records.map { it.eventType }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val availableMethods = allRecords
        .map { records -> records.map { it.httpMethod }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) { updateFilters(_filters.value.copy(query = value)) }
    fun setStatus(value: String?) { updateFilters(_filters.value.copy(status = value)) }
    fun setEventType(value: String?) { updateFilters(_filters.value.copy(eventType = value)) }
    fun setHttpMethod(value: String?) { updateFilters(_filters.value.copy(httpMethod = value)) }
    fun setSort(value: RecordSort) { _filters.value = _filters.value.copy(sort = value) }
    fun toggleSortDirection() { _filters.value = _filters.value.copy(descending = !_filters.value.descending) }
    fun resetFilters() { updateFilters(RecordFilters()) }

    private fun updateFilters(value: RecordFilters) {
        _filters.value = value
        _selectedIds.value = emptySet()
    }

    fun toggleSelection(id: Long) {
        _selectedIds.value = _selectedIds.value.toMutableSet().apply {
            if (!add(id)) remove(id)
        }
    }

    fun selectAllVisible() { _selectedIds.value = records.value.mapTo(mutableSetOf()) { it.id } }
    fun clearSelection() { _selectedIds.value = emptySet() }

    fun deleteSelected() {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            dao.deleteByIds(ids)
            _selectedIds.value = emptySet()
        }
    }

    fun clearVisible() {
        val ids = records.value.map { it.id }
        if (ids.isEmpty()) return
        viewModelScope.launch {
            dao.deleteByIds(ids)
            _selectedIds.value = emptySet()
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            dao.clearAll()
            _selectedIds.value = emptySet()
        }
    }
}

data class WebhookFailureDetailState(
    val isLoading: Boolean = true,
    val record: TransferRecordEntity? = null
)

class WebhookFailureDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    private val dao = AppDatabase.getInstance(application).transferRecordDao()
    private val _state = MutableStateFlow(WebhookFailureDetailState())
    val state: StateFlow<WebhookFailureDetailState> = _state.asStateFlow()

    init {
        val recordId = savedStateHandle.get<Long>("recordId") ?: -1L
        viewModelScope.launch {
            _state.value = WebhookFailureDetailState(false, dao.getById(recordId))
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = _state.value.record?.id ?: return
        viewModelScope.launch {
            dao.deleteById(id)
            onDeleted()
        }
    }
}
