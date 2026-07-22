package lv.aki.verina.service.receiver

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import lv.aki.verina.data.db.AppDatabase
import lv.aki.verina.data.model.EventType
import lv.aki.verina.data.repository.RuleRepository
import lv.aki.verina.engine.RuleEngine

class NotificationReceiver : NotificationListenerService() {

    private lateinit var ruleEngine: RuleEngine

    override fun onCreate() {
        super.onCreate()
        ruleEngine = RuleEngine(RuleRepository(AppDatabase.getInstance(applicationContext)))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) {
            Log.d(TAG, "Skipping ongoing notification from ${sbn.packageName}")
            return
        }

        val extras = notification.extras
        val appName = resolveAppName(sbn.packageName)
        val variables = mapOf(
            "packageName" to sbn.packageName,
            "appName" to appName,
            "title" to extras.getCharSequence(Notification.EXTRA_TITLE).orEmpty().toString(),
            "text" to extras.getCharSequence(Notification.EXTRA_TEXT).orEmpty().toString()
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

    companion object {
        private const val TAG = "NotificationReceiver"
    }
}
