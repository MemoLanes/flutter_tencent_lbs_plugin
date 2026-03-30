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
        val request = tencentLocationRequest ?: return
        val errorCode = manager.requestSingleFreshLocation(request, this, Looper.getMainLooper())
        if (errorCode != TencentLocation.ERROR_OK) {
            flutterApi.onError(errorCode.toLong(), "requestSingleFreshLocation failed with error code $errorCode") {}
        }
    }

    override fun startLocationUpdates(
        request: ContinuousLocationRequest,
        androidNotificationOptions: AndroidNotificationOptions?,
    ) {
        val manager = locationManager ?: return
        val req = tencentLocationRequest ?: return
        if (isListeningLocationUpdates) return
        // 后台定位必须提供通知配置且 id > 0，否则无法启用前台服务
        if (request.backgroundLocation == true) {
            if (androidNotificationOptions == null || androidNotificationOptions.id.toInt() <= 0) {
                flutterApi.onError(-1L, "后台定位需要 androidNotificationOptions 且 id > 0") {}
                return
            }
        }
        isListeningLocationUpdates = true

        val intervalError = validateIntervalMs(request.intervalMs)
        if (intervalError != null) {
            isListeningLocationUpdates = false
            flutterApi.onError(-1L, intervalError) {}
            return
        }
        req.interval = request.intervalMs
        applyCommonRequestOptions(
            req = req,
            requestLevel = request.requestLevel,
            locMode = request.locMode,
            allowGps = request.allowGps,
            allowCache = request.allowCache,
            gpsFirst = request.gpsFirst,
            gpsFirstTimeOutMs = request.gpsFirstTimeOutMs,
        )

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
            val interval = update.intervalMs!!
            val intervalError = validateIntervalMs(interval)
            if (intervalError != null) {
                flutterApi.onError(-1L, intervalError) {}
                return
            }
            manager.changeCallbackInterval(interval)
            req.interval = interval
            return
        }

        update.intervalMs?.let {
            val intervalError = validateIntervalMs(it)
            if (intervalError != null) {
                flutterApi.onError(-1L, intervalError) {}
                return
            }
            req.interval = it
        }
        applyCommonRequestOptions(
            req = req,
            requestLevel = update.requestLevel,
            locMode = update.locMode,
            allowGps = update.allowGps,
            allowCache = update.allowCache,
            gpsFirst = update.gpsFirst,
            gpsFirstTimeOutMs = update.gpsFirstTimeOutMs,
        )

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
            val rawProvider = location.sourceProvider
            val unifiedSource = toUnifiedLocationSource(rawProvider)
            val acc = location.accuracy.toDouble()
            val data = LocationData(
                code = error.toLong(),
                latitude = location.latitude.toDouble(),
                longitude = location.longitude.toDouble(),
                altitude = location.altitude.toDouble(),
                accuracy = acc,
                horizontalAccuracy = acc,
                verticalAccuracy = null,
                speed = location.speed.toDouble(),
                bearing = location.bearing.toDouble().takeIf { it in 0.0..360.0 },
                address = location.address,
                name = location.name,
                timeIso = null,
                timeMs = location.time,
                sourceProvider = rawProvider,
                locationSource = unifiedSource,
            )
            flutterApi.onLocation(data) {}
        } else {
            flutterApi.onError(error.toLong(), reason ?: "定位失败") {}
        }
    }

    /**
     * 将 Android TencentLocation.getSourceProvider() 的字符串映射为与 iOS 统一的定位来源码。
     * 统一约定：-1=未知，0=GPS，1=网络，2=模拟，3=外设GPS，4=外设网络。
     */
    private fun toUnifiedLocationSource(sourceProvider: String?): Long? {
        if (sourceProvider.isNullOrEmpty()) return -1L
        return when (sourceProvider) {
            TencentLocation.GPS_PROVIDER,
            TencentLocation.BEIDOU_PROVIDER -> 0L  // 卫星/GPS
            TencentLocation.NETWORK_PROVIDER,
            TencentLocation.WIFI_PROVIDER,
            TencentLocation.CELL_PROVIDER,
            TencentLocation.COARSE_PROVIDER,
            TencentLocation.FUSED_PROVIDER -> 1L  // 网络
            TencentLocation.FAKE -> 2L            // 模拟/作弊
            else -> -1L
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
            contentTitle = p.notificationTitle,
            contentText = p.notificationText ?: "",
            channelDescription = p.channelDescription,
            enableVibration = p.enableVibration ?: false,
            playSound = p.playSound ?: false,
            showWhen = p.showWhen ?: false,
        )
    }

    private fun applyCommonRequestOptions(
        req: TencentLocationRequest,
        requestLevel: Long?,
        locMode: Long?,
        allowGps: Boolean?,
        allowCache: Boolean?,
        gpsFirst: Boolean?,
        gpsFirstTimeOutMs: Long?,
    ) {
        requestLevel?.toInt()?.let { req.requestLevel = it }
        locMode?.toInt()?.let { req.locMode = it }
        allowGps?.let { req.isAllowGPS = it }
        allowCache?.let { req.isAllowCache = it }
        gpsFirst?.let { req.isGpsFirst = it }
        gpsFirstTimeOutMs?.toInt()?.coerceIn(0, 60000)?.let { req.gpsFirstTimeOut = it }
    }

    /** 校验 interval（严格按指南口径）：必须 >= 1000ms。 */
    private fun validateIntervalMs(intervalMs: Long): String? {
        if (intervalMs < 1000L) {
            return "intervalMs 必须大于等于 1000，当前值: $intervalMs"
        }
        return null
    }
}
