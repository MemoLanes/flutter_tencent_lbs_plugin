package com.flutter_tencent_lbs_plugin.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.flutter_tencent_lbs_plugin.models.NotificationOptions

object NotificationUtils {

    private var notificationManager: NotificationManager? = null

    fun buildNotification(context: Context, options: NotificationOptions): Notification {
        val pm = context.packageManager
        val iconResId = getIconResIdFromAppInfo(context, pm)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager == null) {
                notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            }
            if (notificationManager?.getNotificationChannel(options.channelId) == null) {
                val channel = NotificationChannel(options.channelId, options.channelName, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    if (!options.playSound) setSound(null, null)
                    enableVibration(options.enableVibration)
                    description = options.channelDescription
                }
                notificationManager?.createNotificationChannel(channel)
            }
        }

        return NotificationCompat.Builder(context, options.channelId).apply {
            setSmallIcon(iconResId)
            setContentTitle(options.contentTitle)
            setContentText(options.contentText)
            setShowWhen(options.showWhen)
            priority = NotificationCompat.PRIORITY_LOW

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                if (!options.playSound) setSound(null)
                if (!options.enableVibration) setVibrate(longArrayOf(0L))
            }
        }.build()
    }

    private fun getIconResIdFromAppInfo(context: Context, pm: PackageManager): Int {
        return try {
            pm.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).icon
        } catch (e: PackageManager.NameNotFoundException) {
            0
        }
    }
}