package lv.aki.verina.service.receiver

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import lv.aki.verina.data.db.AppDatabase
import lv.aki.verina.data.model.EventType
import lv.aki.verina.data.repository.RuleRepository
import lv.aki.verina.engine.RuleEngine

class NotificationReceiver : NotificationListenerService() {

    private lateinit var ruleEngine: RuleEngine
    private lateinit var db: AppDatabase
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 缓存启用的包名
    private var enabledPackageNames: Set<String> = emptySet()

    // 用于去重的通知缓存：key -> 最后触发时间
    private val recentNotifications = mutableMapOf<String, Long>()

    companion object {
        private const val TAG = "NotificationReceiver"
        private const val DEDUP_WINDOW_MS = 60_000L // 1分钟去重窗口
    }

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.getInstance(applicationContext)
        ruleEngine = RuleEngine(RuleRepository(db), applicationContext)
        loadEnabledPackages()
    }

    private fun loadEnabledPackages() {
        scope.launch {
            try {
                val filterDao = db.notificationFilterDao()
                // 如果没有过滤器配置，默认允许所有
                if (filterDao.getCount() == 0) {
                    enabledPackageNames = emptySet() // 空集表示允许所有
                } else {
                    enabledPackageNames = filterDao.getEnabledPackageNames().toSet()
                }
                Log.i(TAG, "Loaded ${enabledPackageNames.size} enabled packages")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load enabled packages", e)
                enabledPackageNames = emptySet()
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification

        // 过滤正在进行的通知（如通话、导航等）
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) {
            Log.d(TAG, "Skipping ongoing notification from ${sbn.packageName}")
            return
        }

        // 过滤前台服务通知（如"XX正在运行"）
        if (notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0) {
            Log.d(TAG, "Skipping foreground service notification from ${sbn.packageName}")
            return
        }

        // 应用通知过滤
        if (enabledPackageNames.isNotEmpty() && sbn.packageName !in enabledPackageNames) {
            Log.d(TAG, "Skipping notification from ${sbn.packageName} (not in filter)")
            return
        }

        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        // 过滤标题和内容都为空的通知
        if (title.isBlank() && text.isBlank()) {
            Log.d(TAG, "Skipping empty notification from ${sbn.packageName}")
            return
        }

        // 去重：1分钟内相同通知不重复触发
        val dedupKey = "${sbn.packageName}|$title|$text"
        val now = System.currentTimeMillis()
        val lastTriggerTime = recentNotifications[dedupKey]
        if (lastTriggerTime != null && now - lastTriggerTime < DEDUP_WINDOW_MS) {
            Log.d(TAG, "Skipping duplicate notification from ${sbn.packageName} (within ${DEDUP_WINDOW_MS / 1000}s)")
            return
        }
        recentNotifications[dedupKey] = now

        // 清理过期缓存（避免内存泄漏）
        if (recentNotifications.size > 100) {
            val expiredKeys = recentNotifications.filter { now - it.value > DEDUP_WINDOW_MS * 2 }.keys
            expiredKeys.forEach { recentNotifications.remove(it) }
        }

        val appName = resolveAppName(sbn.packageName)
        val variables = mapOf(
            "packageName" to sbn.packageName,
            "appName" to appName,
            "title" to title,
            "text" to text
        )

        Log.i(TAG, "Notification received from $appName (${sbn.packageName})")
        ruleEngine.onEvent(EventType.NOTIFICATION_POSTED, variables)
    }

    private fun resolveAppName(packageName: String): String = try {
        val appInfo = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(appInfo).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        packageName
    }
}
