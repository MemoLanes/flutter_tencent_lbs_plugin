package com.flutter_tencent_lbs_plugin

import android.content.Context
import android.os.Looper
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import com.flutter_tencent_lbs_plugin.models.NotificationOptions
import com.flutter_tencent_lbs_plugin.utils.JsonUtils
import com.flutter_tencent_lbs_plugin.utils.NotificationUtils
import com.tencent.map.geolocation.*

class FlutterTencentLBSPlugin : FlutterPlugin, MethodCallHandler, TencentLocationListener {
    private lateinit var channel: MethodChannel
    private lateinit var applicationContext: Context
    private lateinit var locationManager: TencentLocationManager
    private lateinit var tencentLocationRequest: TencentLocationRequest

    private val resultList = arrayListOf<Result>()
    private var isListeningLocationUpdates = false

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(binding.binaryMessenger, "flutter_tencent_lbs_plugin")
        channel.setMethodCallHandler(this)
        applicationContext = binding.applicationContext
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "setUserAgreePrivacy" -> TencentLocationManager.setUserAgreePrivacy(true)
            "init" -> initTencentLBS(call, result)
            "getLocationOnce" -> getLocationOnce(result)
            "getLocation" -> {
                getLocation(call)
                result.success(null)
            }
            "stopLocation" -> stopLocation(result)
            else -> result.notImplemented()
        }
    }

    override fun onLocationChanged(location: TencentLocation?, error: Int, reason: String?) {
        if (error == TencentLocation.ERROR_OK && location != null) {
            sendLocationToFlutter(
                mapOf(
                    "latitude" to location.latitude,
                    "longitude" to location.longitude,
                    "altitude" to location.altitude,
                    "accuracy" to location.accuracy,
                    "speed" to location.speed,
                    "time" to location.time,
                    "sourceProvider" to location.sourceProvider,
                    "code" to TencentLocation.ERROR_OK
                )
            )
        } else {
            sendLocationToFlutter(createErrorResult(error), false)
        }
    }

    override fun onStatusUpdate(name: String?, status: Int, desc: String?) {
        channel.invokeMethod("receiveStatus", mapOf("name" to name, "status" to status))
    }

    private fun initTencentLBS(call: MethodCall, result: Result) {
        val argsMap = call.arguments as? Map<*, *> ?: run {
            result.error("INVALID_ARGUMENTS", "Arguments must be a map", null)
            return
        }
        TencentLocationManagerOptions.setKey(JsonUtils.getString(argsMap, "key") ?: "")

        locationManager = TencentLocationManager.getInstance(applicationContext)

        locationManager.coordinateType = JsonUtils.getInt(argsMap, "coordinateType") ?: locationManager.coordinateType
        locationManager.setMockEnable(JsonUtils.getBoolean(argsMap, "mockEnable") ?: false)

        tencentLocationRequest = TencentLocationRequest.create()
        tencentLocationRequest.requestLevel = JsonUtils.getInt(argsMap, "requestLevel") ?: tencentLocationRequest.requestLevel
        tencentLocationRequest.locMode = JsonUtils.getInt(argsMap, "locMode") ?: TencentLocationRequest.HIGH_ACCURACY_MODE
        tencentLocationRequest.isAllowGPS = JsonUtils.getBoolean(argsMap, "isAllowGPS") ?: tencentLocationRequest.isAllowGPS
        tencentLocationRequest.isIndoorLocationMode = JsonUtils.getBoolean(argsMap, "isIndoorLocationMode") ?: tencentLocationRequest.isIndoorLocationMode
        tencentLocationRequest.isGpsFirst = JsonUtils.getBoolean(argsMap, "isGpsFirst") ?: tencentLocationRequest.isGpsFirst
        tencentLocationRequest.gpsFirstTimeOut = JsonUtils.getInt(argsMap, "gpsFirstTimeOut") ?: tencentLocationRequest.gpsFirstTimeOut

        result.success(true)
    }


    // 单次定位
    private fun getLocationOnce(result: Result) {
        val error = locationManager.requestSingleFreshLocation(null, this, Looper.getMainLooper())
        if (error == TencentLocation.ERROR_OK) {
            resultList.add(result)
        } else {
            val errResult = createErrorResult(error)
            sendResultToFlutter(result, errResult, false)
            notifyLocationRecipients(errResult)
        }
    }


    // 连续定位
    private fun getLocation(call: MethodCall) {
        val args = call.arguments as? Map<*, *>
        val interval = JsonUtils.getInt(args, "interval")?.toLong() ?: 1000

        if (!isListeningLocationUpdates) {
            isListeningLocationUpdates = true
            tencentLocationRequest.interval = interval

            if (JsonUtils.getBoolean(args, "backgroundLocation") == true) {
                JsonUtils.getMap(args, "androidNotificationOptions")?.let { optionsMap ->
                    val options = NotificationOptions.getData(optionsMap)
                    locationManager.enableForegroundLocation(options.id,
                        NotificationUtils.buildNotification(applicationContext, options))
                }
            }
            locationManager.requestLocationUpdates(tencentLocationRequest, this)
        }
    }

    private fun stopLocation(result: Result) {
        isListeningLocationUpdates = false
        resultList.clear()
        locationManager.disableForegroundLocation(true)
        locationManager.removeUpdates(this)
        result.success(true)
    }

    private fun sendResultToFlutter(result: Result?, value: Any, isSuccess: Boolean) {
        if (isSuccess) result?.success(value)
        else result?.error((value as Map<*, *>)["code"].toString(), "Err", value)
    }

    private fun sendLocationToFlutter(value: Any, isSuccess: Boolean = true) {
        resultList.forEach { sendResultToFlutter(it, value, isSuccess) }
        resultList.clear()
        notifyLocationRecipients(value)
    }

    private fun notifyLocationRecipients(value: Any) {
        channel.invokeMethod("receiveLocation", value)
    }

    private fun createErrorResult(code: Int) = hashMapOf("code" to code)
}
