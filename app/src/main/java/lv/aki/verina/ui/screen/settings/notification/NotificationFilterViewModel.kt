package lv.aki.verina.ui.screen.settings.notification

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lv.aki.verina.data.db.AppDatabase
import lv.aki.verina.data.model.NotificationFilterEntity

data class AppInfo(
    val packageName: String,
    val appName: String,
    val enabled: Boolean
)

data class NotificationFilterUiState(
    val apps: List<AppInfo> = emptyList(),
    val isLoading: Boolean = true,
    val enabledCount: Int = 0,
    val totalCount: Int = 0
)

class NotificationFilterViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val dao = db.notificationFilterDao()

    private val _state = MutableStateFlow(NotificationFilterUiState())
    val state: StateFlow<NotificationFilterUiState> = _state.asStateFlow()

    init {
        // 不在 init 中加载，等待权限检查
    }

    fun loadApps() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            // 如果数据库为空，初始化所有已安装应用
            if (dao.getCount() == 0) {
                initializeAllApps()
            } else {
                // 检查是否有新安装的应用
                refreshApps()
            }

            // 观察数据库变化
            dao.getAllFilters().collect { filters ->
                val apps = filters.map { filter ->
                    AppInfo(
                        packageName = filter.packageName,
                        appName = filter.appName,
                        enabled = filter.enabled
                    )
                }
                val enabledCount = apps.count { it.enabled }
                _state.value = NotificationFilterUiState(
                    apps = apps,
                    isLoading = false,
                    enabledCount = enabledCount,
                    totalCount = apps.size
                )
            }
        }
    }

    private suspend fun initializeAllApps() {
        val pm = getApplication<Application>().packageManager
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { appInfo ->
                // 只过滤掉没有启动入口的纯系统组件
                hasLaunchableActivity(appInfo, pm)
            }
            .map { appInfo ->
                NotificationFilterEntity(
                    packageName = appInfo.packageName,
                    appName = pm.getApplicationLabel(appInfo).toString(),
                    enabled = true
                )
            }
            .sortedBy { it.appName.lowercase() }

        dao.insertAll(installedApps)
    }

    private fun hasLaunchableActivity(appInfo: ApplicationInfo, pm: PackageManager): Boolean {
        // 检查应用是否有可启动的 Activity
        val intent = pm.getLaunchIntentForPackage(appInfo.packageName)
        if (intent != null) return true

        // 对于系统应用，也检查是否有主 Activity
        if (appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) {
            return try {
                val activities = pm.getPackageInfo(appInfo.packageName, PackageManager.GET_ACTIVITIES).activities
                activities != null && activities.isNotEmpty()
            } catch (_: Exception) {
                false
            }
        }

        return true
    }

    fun toggleApp(packageName: String, enabled: Boolean) {
        viewModelScope.launch {
            dao.setEnabled(packageName, enabled)
        }
    }

    fun selectAll() {
        viewModelScope.launch {
            dao.setAllEnabled(true)
        }
    }

    fun deselectAll() {
        viewModelScope.launch {
            dao.setAllEnabled(false)
        }
    }

    fun refreshApps() {
        viewModelScope.launch {
            val pm = getApplication<Application>().packageManager
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { appInfo ->
                    hasLaunchableActivity(appInfo, pm)
                }

            val existingPackages = dao.getAllPackageNames().toSet()
            val newApps = installedApps
                .filter { it.packageName !in existingPackages }
                .map { appInfo ->
                    NotificationFilterEntity(
                        packageName = appInfo.packageName,
                        appName = pm.getApplicationLabel(appInfo).toString(),
                        enabled = true
                    )
                }

            if (newApps.isNotEmpty()) {
                dao.insertAll(newApps)
            }
        }
    }
}
