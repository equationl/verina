package lv.aki.verina.service.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import lv.aki.verina.R

object WebhookFailureNotifier {

    private const val CHANNEL_ID = "webhook_failure_channel"
    private const val CHANNEL_NAME = "Webhook 失败通知"
    private const val NOTIFICATION_ID_BASE = 10000

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Webhook 请求失败时的通知"
                enableVibration(true)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun showFailureNotification(
        context: Context,
        actionId: Long,
        url: String,
        httpMethod: String,
        headers: String,
        body: String?,
        variablesJson: String,
        retryCount: Int,
        error: String?
    ) {
        createChannel(context)

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formattedBody = if (body.isNullOrBlank()) "（无）" else body
        val formattedError = error ?: "未知错误"

        val detailText = buildString {
            appendLine("请求 $url 已失败 $retryCount 次，已放弃重试。")
            appendLine("错误: $formattedError")
            appendLine()
            appendLine("请求详情:")
            appendLine("方法: $httpMethod")
            appendLine("Headers: $headers")
            appendLine("Body: $formattedBody")
            appendLine("变量: $variablesJson")
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Webhook 发送失败")
            .setContentText("$httpMethod $url 已失败 $retryCount 次")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(detailText)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val notificationId = (NOTIFICATION_ID_BASE + actionId).toInt()
        notificationManager.notify(notificationId, notification)
    }

    fun showBatchFailureNotification(context: Context, count: Int) {
        createChannel(context)

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Webhook 批量失败")
            .setContentText("有 $count 个 Webhook 请求已达到最大重试次数")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID_BASE, notification)
    }
}
