package com.flutter_tencent_lbs_plugin

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.tencent.map.geolocation.TencentLocation
import com.tencent.map.geolocation.TencentLocationListener
import com.tencent.map.geolocation.TencentLocationManager
import com.tencent.map.geolocation.TencentLocationRequest

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

import com.flutter_tencent_lbs_plugin.models.InitOptions
import com.flutter_tencent_lbs_plugin.models.NotificationIconData
import com.flutter_tencent_lbs_plugin.models.NotificationOptions
import com.flutter_tencent_lbs_plugin.utils.JsonUtils
import com.flutter_tencent_lbs_plugin.utils.NotificationUtils
import com.tencent.map.geolocation.TencentLocationManagerOptions

class FlutterTencentLBSPlugin : FlutterPlugin, MethodCallHandler, TencentLocationListener {
    private lateinit var channel: MethodChannel
    private lateinit var applicationContext: Context

    private lateinit var locationManager: TencentLocationManager
    private lateinit var tencentLocationRequest: TencentLocationRequest
    private var resultList: ArrayList<Result> = arrayListOf()
    private var isListenLocationUpdates = false

    private var isCreateChannel = false
    private var notificationManager: NotificationManager? = null

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "flutter_tencent_lbs_plugin")
        channel.setMethodCallHandler(this)
        applicationContext = flutterPluginBinding.applicationContext
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "setUserAgreePrivacy" -> {
                TencentLocationManager.setUserAgreePrivacy(true)
            }

            "init" -> {
                initTencentLBS(call, result)
            }

            "getLocationOnce" -> {
                getLocationOnce(result)
            }

            "getLocation" -> {
                getLocation(call)
                result.success(null)
            }

            "stopLocation" -> {
                stopLocation(result)
            }

            else -> result.notImplemented()
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    override fun onLocationChanged(location: TencentLocation?, error: Int, reason: String?) {
        if (error == TencentLocation.ERROR_OK && location != null) {
            val result = HashMap<String, Any?>()
            result["latitude"] = location.latitude
            result["longitude"] = location.longitude
            result["altitude"] = location.altitude
            result["accuracy"] = location.accuracy
            result["speed"] = location.speed
            result["time"] = location.time
            result["sourceProvider"] = location.sourceProvider
            result["code"] = TencentLocation.ERROR_OK
            sendLocationToFlutter(result)
        } else {
            sendLocationToFlutter(createErrorResult(error), false)
        }
    }

    override fun onStatusUpdate(name: String?, status: Int, desc: String?) {
        val result = HashMap<String, Any?>()
        result["name"] = name
        result["status"] = status
        channel.invokeMethod("receiveStatus", result)
    }

    private fun initTencentLBS(call: MethodCall, result: Result) {
        val args = call.arguments
        val argsMap = args as? Map<*, *>
        if (argsMap == null) {
            result.error("INVALID_ARGUMENTS", "Arguments must be a map", null)
            return
        }

        val apiKey = JsonUtils.getString(argsMap, "key") ?: ""
        TencentLocationManagerOptions.setKey(apiKey)
        locationManager = TencentLocationManager.getInstance(applicationContext)
        tencentLocationRequest = TencentLocationRequest.create()

        val options = InitOptions.getData(locationManager, tencentLocationRequest, args)
        locationManager.coordinateType = options.coordinateType
        locationManager.setMockEnable(options.mockEnable)

        tencentLocationRequest.requestLevel = options.requestLevel
        tencentLocationRequest.locMode = options.locMode
        tencentLocationRequest.isAllowGPS = options.isAllowGPS
        tencentLocationRequest.isIndoorLocationMode = options.isIndoorLocationMode
        tencentLocationRequest.isGpsFirst = options.isGpsFirst
        tencentLocationRequest.gpsFirstTimeOut = options.gpsFirstTimeOut
        result.success(true)
    }

    // 单次定位
    private fun getLocationOnce(result: Result) {
        val error = locationManager.requestSingleFreshLocation(null, this, Looper.getMainLooper())
        if (error == TencentLocation.ERROR_OK) {
            resultList.add(result)
        } else {
            val errResult = createErrorResult(error)
            sendErrorLocationToFlutter(result, errResult)
            notifyLocationRecipients(errResult)
        }
    }


    // 连续定位
    private fun getLocation(call: MethodCall) {
        val args = call.arguments as Map<*, *>?
        val interval: Long = JsonUtils.getInt(args, "interval")?.toLong() ?: 1000
        val backgroundLocation = JsonUtils.getBoolean(args, "backgroundLocation") ?: false
        if (!isListenLocationUpdates) {
            isListenLocationUpdates = true
            tencentLocationRequest.interval = interval
            if (JsonUtils.getBoolean(args, "backgroundLocation") == true) {
                JsonUtils.getMap(args, "androidNotificationOptions")?.let { optionsMap ->
                    val options = NotificationOptions.getData(optionsMap)
                    val notification = NotificationUtils.buildNotification(applicationContext, options)
                    locationManager.enableForegroundLocation(options.id, notification)
                }
            }
            locationManager.requestLocationUpdates(tencentLocationRequest, this)
        }
    }

    private fun stopLocation(result: Result) {
        isListenLocationUpdates = false
        resultList.clear()
        locationManager.disableForegroundLocation(true)
        locationManager.removeUpdates(this)
        result.success(true)
    }

    private fun sendErrorLocationToFlutter(result: Result?, value: Any) {
        result?.error((value as HashMap<*, *>)["code"].toString(), "Err", value)
    }

    private fun sendSuccessLocationToFlutter(result: Result?, value: Any) {
        result?.success(value)
    }

    private fun sendLocationToFlutter(value: Any, isSuccess: Boolean = true) {
        for (result in resultList.iterator()) {
            if (isSuccess) {
                sendSuccessLocationToFlutter(result, value)
            } else {
                sendErrorLocationToFlutter(result, value)
            }
        }
        resultList.clear()
        notifyLocationRecipients(value)
    }

    private fun createErrorResult(code: Int): HashMap<String, Any> {
        val result = HashMap<String, Any>()
        result["code"] = code
        return result
    }

    private fun notifyLocationRecipients(value: Any) {
        channel.invokeMethod("receiveLocation", value)
    }
}
