package com.flutter_tencent_lbs_plugin.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import com.flutter_tencent_lbs_plugin.models.NotificationIconData
import com.flutter_tencent_lbs_plugin.models.NotificationOptions

object NotificationUtils {

    private var notificationManager: NotificationManager? = null

    fun buildNotification(context: Context, options: NotificationOptions): Notification {
        val pm = context.packageManager
        val iconData: NotificationIconData? = options.iconData
        val iconBackgroundColor = iconData?.backgroundColorRgb?.let { getRgbColor(it) }
        val iconResId = iconData?.let { getIconResIdFromIconData(context, it) } ?: getIconResIdFromAppInfo(context, pm)

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
            iconBackgroundColor?.let { color = it }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                if (!options.playSound) setSound(null)
                if (!options.enableVibration) setVibrate(longArrayOf(0L))
            }
        }.build()
    }

    private fun getIconResIdFromIconData(context: Context, iconData: NotificationIconData): Int {
        val resName = when (iconData.resPrefix) {
            "ic" -> "ic_${iconData.name}"
            "img" -> "img_${iconData.name}"
            else -> return 0
        }
        return context.resources.getIdentifier(
            resName,
            iconData.resType,
            context.packageName
        )
    }

    private fun getIconResIdFromAppInfo(context: Context, pm: PackageManager): Int {
        return try {
            pm.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).icon
        } catch (e: PackageManager.NameNotFoundException) {
            0
        }
    }

    private fun getRgbColor(rgb: String): Int? {
        val rgbSet = rgb.split(",")
        return if (rgbSet.size == 3) {
            Color.rgb(rgbSet[0].toInt(), rgbSet[1].toInt(), rgbSet[2].toInt())
        } else {
            null
        }
    }
}