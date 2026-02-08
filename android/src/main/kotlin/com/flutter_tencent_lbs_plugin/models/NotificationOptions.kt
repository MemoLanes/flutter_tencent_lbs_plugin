package com.flutter_tencent_lbs_plugin.models

/** 前台定位通知配置，由 Pigeon AndroidNotificationOptions 转换后用于系统 Notification。 */
data class NotificationOptions(
    val id: Int,
    val channelId: String,
    val channelName: String,
    val channelDescription: String?,
    val contentTitle: String,
    val contentText: String,
    val enableVibration: Boolean,
    val playSound: Boolean,
    val showWhen: Boolean,
    val iconData: NotificationIconData?,
)
