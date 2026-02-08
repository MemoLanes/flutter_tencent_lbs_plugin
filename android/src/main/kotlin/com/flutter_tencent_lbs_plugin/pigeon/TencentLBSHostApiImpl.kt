package com.flutter_tencent_lbs_plugin.pigeon

import android.content.Context
import android.os.Looper
import com.flutter_tencent_lbs_plugin.models.NotificationOptions
import com.flutter_tencent_lbs_plugin.utils.NotificationUtils
import com.tencent.map.geolocation.TencentLocation
import com.tencent.map.geolocation.TencentLocationListener
import com.tencent.map.geolocation.TencentLocationManager
import com.tencent.map.geolocation.TencentLocationManagerOptions
import com.tencent.map.geolocation.TencentLocationRequest

/**
 * Pigeon Host API 实现，与腾讯定位 SDK 文档对齐。
 * 坐标系：Flutter 统一 0=GCJ02, 1=WGS84 → Android COORDINATE_TYPE_GCJ02=1, COORDINATE_TYPE_WGS84=0
 */
class TencentLBSHostApiImpl(
    private val context: Context,
    private val flutterApi: TencentLBSFlutterApi,
) : TencentLBSHostApi, TencentLocationListener {

    private var locationManager: TencentLocationManager? = null
    private var tencentLocationRequest: TencentLocationRequest? = null
    private var isListeningLocationUpdates = false

    override fun configure(options: InitOptions): Boolean {
        TencentLocationManagerOptions.setKey(options.apiKey)
        locationManager = TencentLocationManager.getInstance(context)

        // 坐标系：Flutter 0=GCJ02 -> Android 1, Flutter 1=WGS84 -> Android 0
        val coordType = when (options.coordinateType?.toInt()) {
            0 -> TencentLocationManager.COORDINATE_TYPE_GCJ02
            1 -> TencentLocationManager.COORDINATE_TYPE_WGS84
            else -> TencentLocationManager.COORDINATE_TYPE_WGS84
        }
        locationManager?.coordinateType = coordType
        locationManager?.setMockEnable(options.mockEnable ?: false)

        val request = TencentLocationRequest.create()
        request.requestLevel = (options.requestLevel ?: TencentLocationRequest.REQUEST_LEVEL_ADMIN_AREA).toInt()
        request.locMode = (options.locMode ?: TencentLocationRequest.HIGH_ACCURACY_MODE).toInt()
        request.isAllowGPS = options.allowGps ?: true
        request.isIndoorLocationMode = options.indoorLocationMode ?: false
        request.isGpsFirst = options.gpsFirst ?: false
        request.gpsFirstTimeOut = (options.gpsFirstTimeOutMs ?: 5000).toInt().coerceIn(0, 60000)
        tencentLocationRequest = request
        return true
    }

    override fun setUserAgreePrivacy(agree: Boolean) {
        TencentLocationManager.setUserAgreePrivacy(agree)
    }

    override fun requestLocationOnce() {
        val manager = locationManager ?: return
        manager.requestSingleFreshLocation(tencentLocationRequest, this, Looper.getMainLooper())
    }

    override fun startLocationUpdates(
        request: ContinuousLocationRequest,
        androidNotificationOptions: AndroidNotificationOptions?,
    ) {
        val manager = locationManager ?: return
        val req = tencentLocationRequest ?: return
        if (isListeningLocationUpdates) return
        isListeningLocationUpdates = true

        req.interval = request.intervalMs
        request.requestLevel?.toInt()?.let { req.requestLevel = it }
        request.locMode?.toInt()?.let { req.locMode = it }
        request.allowGps?.let { req.isAllowGPS = it }
        request.allowCache?.let { req.isAllowCache = it }
        request.gpsFirst?.let { req.isGpsFirst = it }
        request.gpsFirstTimeOutMs?.toInt()?.coerceIn(0, 60000)?.let { req.gpsFirstTimeOut = it }

        if (request.backgroundLocation == true && androidNotificationOptions != null) {
            val notifId = androidNotificationOptions.id.toInt()
            if (notifId > 0) {
                val opts = toNotificationOptions(androidNotificationOptions)
                manager.enableForegroundLocation(notifId, NotificationUtils.buildNotification(context, opts))
            }
        }
        manager.requestLocationUpdates(req, this)
    }

    override fun updateLocationRequest(update: LocationRequestUpdate?) {
        if (update == null) return
        val manager = locationManager ?: return
        val req = tencentLocationRequest ?: return
        if (!isListeningLocationUpdates) return

        val onlyInterval = update.intervalMs != null &&
            update.requestLevel == null &&
            update.locMode == null &&
            update.gpsFirstTimeOutMs == null &&
            update.allowGps == null &&
            update.allowCache == null &&
            update.gpsFirst == null

        if (onlyInterval && update.intervalMs != null && update.intervalMs!! > 0) {
            manager.changeCallbackInterval(update.intervalMs!!)
            req.interval = update.intervalMs!!
            return
        }

        update.intervalMs?.let { req.interval = it }
        update.requestLevel?.toInt()?.let { req.requestLevel = it }
        update.locMode?.toInt()?.let { req.locMode = it }
        update.gpsFirstTimeOutMs?.toInt()?.coerceIn(0, 60000)?.let { req.gpsFirstTimeOut = it }
        update.allowGps?.let { req.isAllowGPS = it }
        update.allowCache?.let { req.isAllowCache = it }
        update.gpsFirst?.let { req.isGpsFirst = it }

        manager.removeUpdates(this)
        manager.requestLocationUpdates(req, this)
    }

    override fun stopLocationUpdates() {
        isListeningLocationUpdates = false
        locationManager?.disableForegroundLocation(true)
        locationManager?.removeUpdates(this)
    }

    override fun onLocationChanged(location: TencentLocation?, error: Int, reason: String?) {
        if (error == TencentLocation.ERROR_OK && location != null) {
            val data = LocationData(
                code = error.toLong(),
                latitude = location.latitude.toDouble(),
                longitude = location.longitude.toDouble(),
                altitude = location.altitude.toDouble(),
                accuracy = location.accuracy.toDouble(),
                speed = location.speed.toDouble(),
                bearing = null,
                address = location.address,
                name = location.name,
                timeIso = null,
                timeMs = location.time,
                sourceProvider = location.sourceProvider,
            )
            flutterApi.onLocation(data) {}
        } else {
            flutterApi.onError(error.toLong(), reason ?: "定位失败") {}
        }
    }

    override fun onStatusUpdate(name: String?, status: Int, desc: String?) {
        flutterApi.onStatus(LocationStatusData(name = name, status = status.toLong())) {}
    }

    private fun toNotificationOptions(p: AndroidNotificationOptions): NotificationOptions {
        return NotificationOptions(
            id = p.id.toInt(),
            channelId = p.channelId,
            channelName = p.channelName,
            channelDescription = p.channelDescription,
            contentTitle = p.notificationTitle,
            contentText = p.notificationText ?: "",
            enableVibration = p.enableVibration ?: false,
            playSound = p.playSound ?: false,
            showWhen = p.showWhen ?: false,
            iconData = null,
        )
    }
}
