package com.flutter_tencent_lbs_plugin.recorder

import com.flutter_tencent_lbs_plugin.models.NotificationOptions
import com.tencent.map.geolocation.TencentLocationManager
import com.tencent.map.geolocation.TencentLocationRequest
import org.json.JSONObject

internal class RecorderConfig private constructor(val json: JSONObject) {
    val key: String get() = json.getString("key")
    val notificationId: Int get() = json.getJSONObject("notificationOptions").getInt("id")
    val coordinateType: Int get() = if (json.getInt("coordinateType") == 1)
        TencentLocationManager.COORDINATE_TYPE_WGS84 else TencentLocationManager.COORDINATE_TYPE_GCJ02
    val mockEnable: Boolean get() = json.getBoolean("mockEnable")

    fun request(): TencentLocationRequest = TencentLocationRequest.create().apply {
        interval = json.getLong("intervalMs")
        requestLevel = json.getInt("requestLevel")
        locMode = json.getInt("locMode")
        isAllowGPS = json.getBoolean("allowGps")
        isAllowCache = json.getBoolean("allowCache")
        isGpsFirst = json.getBoolean("gpsFirst")
        gpsFirstTimeOut = json.getInt("gpsFirstTimeOutMs")
    }

    fun notificationOptions(): NotificationOptions {
        val n = json.getJSONObject("notificationOptions")
        return NotificationOptions(id = n.getInt("id"), channelId = n.getString("channelId"),
            channelName = n.getString("channelName"), contentTitle = n.getString("notificationTitle"),
            contentText = n.optString("notificationText", ""),
            channelDescription = n.optString("channelDescription", ""),
            enableVibration = n.optBoolean("enableVibration"), playSound = n.optBoolean("playSound"),
            showWhen = n.optBoolean("showWhen"))
    }

    companion object {
        fun fromArguments(arguments: Any?): RecorderConfig {
            require(arguments is Map<*, *>) { "Recording options are required" }
            return fromJson(JSONObject(arguments).toString())
        }

        fun fromJson(value: String): RecorderConfig {
            val json = JSONObject(value)
            require(json.getString("key").isNotBlank()) { "key must not be empty" }
            require(json.getLong("intervalMs") >= 1000) { "intervalMs must be at least 1000" }
            require(json.getInt("coordinateType") in 0..1) { "Invalid coordinateType" }
            require(json.getInt("requestLevel") in listOf(0, 1, 3, 4)) { "Invalid requestLevel" }
            require(json.getInt("locMode") in 10..12) { "Invalid locMode" }
            require(json.getInt("gpsFirstTimeOutMs") in 0..60000) { "Invalid gpsFirstTimeOutMs" }
            val notification = json.getJSONObject("notificationOptions")
            require(notification.getLong("id") in 1..Int.MAX_VALUE.toLong()) { "Notification id must be a positive 32-bit integer" }
            for (key in listOf("channelId", "channelName", "notificationTitle")) {
                require(notification.getString(key).isNotBlank()) { "$key must not be empty" }
            }
            // Read all booleans now, so malformed commands fail before starting a service.
            for (key in listOf("mockEnable", "allowGps", "allowCache", "gpsFirst")) json.getBoolean(key)
            return RecorderConfig(json)
        }
    }
}
