package com.flutter_tencent_lbs_plugin.recorder

import android.app.Service
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.ResultReceiver
import android.util.Log
import com.flutter_tencent_lbs_plugin.models.NotificationOptions
import com.flutter_tencent_lbs_plugin.utils.NotificationUtils
import com.tencent.map.geolocation.TencentLocation
import com.tencent.map.geolocation.TencentLocationListener
import com.tencent.map.geolocation.TencentLocationManager
import com.tencent.map.geolocation.TencentLocationManagerOptions
import org.json.JSONObject
import java.util.concurrent.Executors

/** Owns collection and its journal. This process never constructs a FlutterEngine. */
class LocationRecordingService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var store: RecordingStore
    private var manager: TencentLocationManager? = null
    private var listener: TencentLocationListener? = null
    private var wakeLock: PowerManager.WakeLock? = null
    @Volatile private var generation = 0
    private var acceptingLocations = false
    @Volatile private var storageFailed = false
    @Volatile private var storageError: String? = null
    private var currentConfig: RecorderConfig? = null

    override fun onCreate() {
        super.onCreate()
        store = RecordingStore(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        @Suppress("DEPRECATION")
        val receiver = intent?.getParcelableExtra<ResultReceiver>(EXTRA_RESULT)
        if (intent?.action == ACTION_STOP) {
            stopRecording(receiver, startId)
            return START_NOT_STICKY
        }
        val requested = try {
            intent?.getStringExtra(EXTRA_CONFIG)?.let(RecorderConfig::fromJson)
        } catch (error: Exception) {
            reply(receiver, error.message ?: error.toString())
            if (!acceptingLocations) finishService(startId)
            return START_NOT_STICKY
        }
        if (requested != null && acceptingLocations && !storageFailed &&
            currentConfig?.json?.toString() == requested.json.toString()) {
            reply(receiver, null)
            return START_STICKY
        }
        val commandGeneration = ++generation
        try {
            stopCapture()
            // A sticky restart has no intent. Show a notification before reading SQLite.
            if (requested != null) showForeground(requested)
            else showRestartNotification()
            io.execute {
                try {
                    val restored = requested ?: store.activeConfig()
                    if (restored == null) {
                        main.post { if (generation == commandGeneration) finishService(startId) }
                        reply(receiver, null)
                        return@execute
                    }
                    check(!storageFailed) { storageError ?: "Recording journal failed" }
                    store.start(restored)
                    main.post {
                        if (generation != commandGeneration) {
                            reply(receiver, "Recording command was superseded")
                            return@post
                        }
                        try {
                            if (requested == null) {
                                @Suppress("DEPRECATION")
                                stopForeground(true)
                                showForeground(restored)
                            }
                            startCapture(restored, commandGeneration)
                            reply(receiver, null)
                        } catch (error: Exception) {
                            failRecording(error, receiver, startId, commandGeneration)
                        }
                    }
                } catch (error: Exception) {
                    main.post { failRecording(error, receiver, startId, commandGeneration) }
                }
            }
        } catch (error: Exception) {
            failRecording(error, receiver, startId, commandGeneration)
        }
        return START_STICKY
    }

    private fun showForeground(value: RecorderConfig) {
        currentConfig = value
        val notification = NotificationUtils.buildNotification(this, value.notificationOptions())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(value.notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else startForeground(value.notificationId, notification)
    }

    private fun showRestartNotification() {
        val label = applicationInfo.loadLabel(packageManager).toString()
        val notification = NotificationUtils.buildNotification(this, NotificationOptions(
            id = RESTART_NOTIFICATION_ID, channelId = "location_recording_restart", channelName = label,
            contentTitle = label, contentText = "", channelDescription = "",
            enableVibration = false, playSound = false, showWhen = false,
        ))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(RESTART_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else startForeground(RESTART_NOTIFICATION_ID, notification)
    }

    private fun startCapture(value: RecorderConfig, commandGeneration: Int) {
        currentConfig = value
        storageFailed = false
        storageError = null
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(FAILURE_NOTIFICATION_ID)
        // SDK globals are process-local: UI-process initialization cannot initialize this process.
        TencentLocationManager.setUserAgreePrivacy(true)
        check(TencentLocationManagerOptions.setKey(value.key)) { "Tencent SDK rejected the API key" }
        val locationManager = TencentLocationManager.getInstance(applicationContext)
        manager = locationManager
        locationManager.coordinateType = value.coordinateType
        locationManager.setMockEnable(value.mockEnable)
        val newListener = object : TencentLocationListener {
            override fun onLocationChanged(location: TencentLocation?, error: Int, reason: String?) {
                if (!acceptingLocations || generation != commandGeneration || storageFailed) return
                if (error != TencentLocation.ERROR_OK || location == null) {
                    val message = "Tencent location error $error: ${reason ?: "Unknown error"}"
                    io.execute { try { store.reportError(message) } catch (failure: Exception) {
                        storageFailure(failure, commandGeneration)
                    } }
                    return
                }
                // Snapshot the SDK object and receipt time on its callback thread, before I/O queues.
                val receivedTimestampMs = System.currentTimeMillis()
                val point = locationJson(location) ?: return
                io.execute {
                    if (!storageFailed) try {
                        store.append(receivedTimestampMs, point)
                    } catch (failure: Exception) { storageFailure(failure, commandGeneration) }
                }
            }

            override fun onStatusUpdate(name: String?, status: Int, desc: String?) {
                Log.i(TAG, "Tencent location status $name=$status: $desc")
            }
        }
        listener = newListener
        acceptingLocations = true
        val code = locationManager.requestLocationUpdates(value.request(), newListener, Looper.getMainLooper())
        check(code == 0) { "Tencent requestLocationUpdates failed with code $code" }
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:LocationRecording").apply { acquire() }
    }

    private fun stopCapture() {
        acceptingLocations = false
        try {
            listener?.let { manager?.removeUpdates(it) }
        } finally {
            listener = null
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = null
        }
    }

    private fun stopRecording(receiver: ResultReceiver?, startId: Int) {
        val stopGeneration = ++generation
        var captureError: String? = null
        try { stopCapture() } catch (error: Exception) {
            captureError = error.message ?: error.toString()
        }
        // SDK callbacks run on main. All accepted callbacks have already enqueued their writes.
        io.execute {
            var failure: String? = captureError ?: storageError
            try { store.stop(failure) } catch (error: Exception) {
                failure = error.message ?: error.toString()
                Log.e(TAG, "Failed to persist recording stop", error)
                try { store.markFailure(failure) } catch (markerError: Exception) {
                    Log.e(TAG, "Failed to persist recording failure marker", markerError)
                }
            }
            main.post {
                reply(receiver, failure)
                if (generation == stopGeneration) finishService(startId)
            }
        }
    }

    private fun failRecording(error: Exception, receiver: ResultReceiver?, startId: Int, expectedGeneration: Int = generation) {
        if (generation != expectedGeneration) {
            reply(receiver, "Recording command was superseded")
            return
        }
        val failedGeneration = ++generation
        try { stopCapture() } catch (cleanupError: Exception) {
            Log.e(TAG, "Failed to unregister location listener", cleanupError)
        }
        val message = error.message ?: error.toString()
        Log.e(TAG, "Location recording stopped", error)
        io.execute {
            try { store.markFailure(message) } catch (markerError: Exception) {
                Log.e(TAG, "Failed to persist recording failure marker", markerError)
            }
            try { store.stop(message) } catch (persistError: Exception) {
                Log.e(TAG, "Failed to persist recording failure", persistError)
            }
            main.post {
                reply(receiver, message)
                if (generation == failedGeneration) {
                    showFailureNotification(message)
                    finishService(startId)
                }
            }
        }
    }

    private fun storageFailure(error: Exception, commandGeneration: Int) {
        if (storageFailed) return
        storageFailed = true
        storageError = error.message ?: error.toString()
        try { store.markFailure(storageError!!) } catch (markerError: Exception) {
            Log.e(TAG, "Failed to persist recording failure marker", markerError)
        }
        // No further locations are accepted after a journal failure.
        main.post { failRecording(error, null, 0, commandGeneration) }
    }

    private fun finishService(startId: Int) {
        if (startId > 0) stopSelf(startId) else stopSelf()
    }

    private fun showFailureNotification(message: String) {
        val label = applicationInfo.loadLabel(packageManager).toString()
        val options = (currentConfig?.notificationOptions() ?: NotificationOptions(
            id = FAILURE_NOTIFICATION_ID, channelId = "location_recording_restart", channelName = label,
            contentTitle = label, contentText = "", channelDescription = "",
            enableVibration = false, playSound = false, showWhen = false,
        )).copy(contentText = message)
        try {
            val notification = NotificationUtils.buildNotification(this, options)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .notify(FAILURE_NOTIFICATION_ID, notification)
        } catch (error: Exception) { Log.e(TAG, "Cannot show recording failure", error) }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // stopWithTask=true skips this callback. Stop explicitly so sticky restart state is cleared.
        stopRecording(null, 0)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        ++generation
        try { stopCapture() } catch (error: Exception) { Log.e(TAG, "Location cleanup failed", error) }
        // A system destruction must retain active config for START_STICKY recovery.
        io.execute { store.close() }
        io.shutdown()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.flutter_tencent_lbs_plugin.START_RECORDING"
        const val ACTION_STOP = "com.flutter_tencent_lbs_plugin.STOP_RECORDING"
        const val EXTRA_CONFIG = "config"
        const val EXTRA_RESULT = "result"
        private const val RESTART_NOTIFICATION_ID = 1918273
        private const val FAILURE_NOTIFICATION_ID = -1918274
        private const val TAG = "TencentLocationRecorder"

        private fun reply(receiver: ResultReceiver?, error: String?) {
            receiver?.send(if (error == null) 0 else 1, Bundle().apply { putString("error", error) })
        }

        internal fun locationJson(location: TencentLocation): JSONObject? {
            val latitude = location.latitude
            val longitude = location.longitude
            val accuracy = location.accuracy.toDouble()
            if (!latitude.isFinite() || !longitude.isFinite() || !accuracy.isFinite() ||
                latitude !in -90.0..90.0 || longitude !in -180.0..180.0 ||
                accuracy <= 0 || location.time <= 0) return null
            val provider = location.sourceProvider
            val source = when (provider) {
                TencentLocation.GPS_PROVIDER, TencentLocation.BEIDOU_PROVIDER -> 0
                TencentLocation.NETWORK_PROVIDER, TencentLocation.WIFI_PROVIDER,
                TencentLocation.CELL_PROVIDER, TencentLocation.COARSE_PROVIDER, TencentLocation.FUSED_PROVIDER -> 1
                TencentLocation.FAKE -> 2
                else -> -1
            }
            return JSONObject().apply {
                put("code", 0)
                put("latitude", latitude)
                put("longitude", longitude)
                put("horizontalAccuracy", accuracy)
                put("time", location.time)
                put("altitude", location.altitude.takeIf { it.isFinite() } ?: JSONObject.NULL)
                put("speed", location.speed.toDouble().takeIf { it.isFinite() } ?: JSONObject.NULL)
                put("bearing", location.bearing.toDouble().takeIf { it.isFinite() && it in 0.0..360.0 } ?: JSONObject.NULL)
                put("sourceProvider", provider ?: JSONObject.NULL)
                put("locationSource", source)
            }
        }
    }
}
