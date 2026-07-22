package lv.aki.verina.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.provider.Telephony
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import lv.aki.verina.R
import lv.aki.verina.data.db.AppDatabase
import lv.aki.verina.data.repository.RuleRepository
import lv.aki.verina.engine.RuleEngine
import lv.aki.verina.service.receiver.BatteryReceiver
import lv.aki.verina.service.receiver.PhoneCallReceiver
import lv.aki.verina.service.receiver.SmsReceiver

class VerinaForegroundService : Service() {

    private lateinit var ruleEngine: RuleEngine
    private lateinit var smsReceiver: SmsReceiver
    private lateinit var phoneCallReceiver: PhoneCallReceiver
    private lateinit var batteryReceiver: BatteryReceiver

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service created")

        val db = AppDatabase.getInstance(applicationContext)
        val repository = RuleRepository(db)
        ruleEngine = RuleEngine(repository)

        smsReceiver = SmsReceiver(ruleEngine)
        phoneCallReceiver = PhoneCallReceiver(ruleEngine)
        batteryReceiver = BatteryReceiver(ruleEngine)

        ContextCompat.registerReceiver(
            this, smsReceiver,
            IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION),
            ContextCompat.RECEIVER_EXPORTED
        )
        ContextCompat.registerReceiver(
            this, phoneCallReceiver,
            IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED
        )
        ContextCompat.registerReceiver(
            this, batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Service destroyed")
        try {
            unregisterReceiver(smsReceiver)
            unregisterReceiver(phoneCallReceiver)
            unregisterReceiver(batteryReceiver)
        } catch (_: Exception) { }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Verina 事件监听",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Verina 后台服务运行通知"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Verina")
            .setContentText("正在监听事件...")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "VerinaForegroundService"
        const val CHANNEL_ID = "verina_foreground_channel"
        const val NOTIFICATION_ID = 1
    }
}
