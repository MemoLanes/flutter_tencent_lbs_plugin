package com.flutter_tencent_lbs_plugin.recorder

import android.content.Intent
import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import android.os.Looper
import android.os.ResultReceiver
import com.tencent.map.geolocation.TencentLocationListener
import com.tencent.map.geolocation.TencentLocationManager
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, shadows = [RecorderMarkerLinuxShadow::class])
class LocationRecordingServiceTest {
    private val context get() = RuntimeEnvironment.getApplication()
    @Before fun before() = clean()
    @After fun after() = clean()
    private fun clean() {
        context.deleteDatabase("tencent_location_recording.db")
        File(context.filesDir, "tencent_location_recording_failure.txt").delete()
    }

    @Test fun sameConfigurationStartDoesNotUnregisterExistingSdkListener() {
        val controller = Robolectric.buildService(LocationRecordingService::class.java).create()
        val service = controller.get()
        val manager = Mockito.mock(TencentLocationManager::class.java)
        val listener = Mockito.mock(TencentLocationListener::class.java)
        set(service, "manager", manager)
        set(service, "listener", listener)
        set(service, "acceptingLocations", true)
        set(service, "generation", 7)
        set(service, "currentConfig", config())
        var result: Int? = null
        service.onStartCommand(Intent().setAction(LocationRecordingService.ACTION_START)
            .putExtra(LocationRecordingService.EXTRA_CONFIG, config().json.toString())
            .putExtra(LocationRecordingService.EXTRA_RESULT, object : ResultReceiver(null) {
                override fun onReceiveResult(code: Int, data: Bundle?) { result = code }
            }), 0, 1)
        assertEquals(0, result)
        assertEquals(7, get(service, "generation"))
        Mockito.verify(manager, Mockito.never()).removeUpdates(listener)
        controller.destroy()
        awaitIo(service)
    }

    @Test fun stopAcknowledgesOnlyAfterPreviouslyAcceptedWritesAndStateCommit() {
        val controller = Robolectric.buildService(LocationRecordingService::class.java).create()
        val service = controller.get()
        val store = get(service, "store") as RecordingStore
        store.start(config())
        executor(service).execute { store.append(1234, JSONObject("""{"code":0}""")) }
        var result: Int? = null
        service.onStartCommand(Intent().setAction(LocationRecordingService.ACTION_STOP)
            .putExtra(LocationRecordingService.EXTRA_RESULT, object : ResultReceiver(null) {
                override fun onReceiveResult(code: Int, data: Bundle?) {
                    result = code
                    assertEquals(false, store.getState()["isRecording"])
                    assertEquals(1, (store.readBatch(1)["locations"] as List<*>).size)
                }
            }), 0, 1)
        awaitIo(service)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, result)
        assertTrue(shadowOf(service).isStoppedBySelf)
        controller.destroy()
        awaitIo(service)
    }

    @Test fun removingTaskClearsStickyIntentAndStopsServiceWithoutStartId() {
        val controller = Robolectric.buildService(LocationRecordingService::class.java).create()
        val service = controller.get()
        val store = get(service, "store") as RecordingStore
        store.start(config())
        service.onTaskRemoved(null)
        awaitIo(service)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(false, store.getState()["isRecording"])
        assertTrue(shadowOf(service).isStoppedBySelf)
        controller.destroy()
        awaitIo(service)
    }

    @Test fun cleanupFailureReturnsErrorAndStillStopsService() {
        val controller = Robolectric.buildService(LocationRecordingService::class.java).create()
        val service = controller.get()
        val manager = Mockito.mock(TencentLocationManager::class.java)
        val listener = Mockito.mock(TencentLocationListener::class.java)
        Mockito.doThrow(IllegalStateException("unregister failed")).`when`(manager).removeUpdates(listener)
        set(service, "manager", manager)
        set(service, "listener", listener)
        var result: Int? = null
        service.onStartCommand(Intent().setAction(LocationRecordingService.ACTION_STOP)
            .putExtra(LocationRecordingService.EXTRA_RESULT, object : ResultReceiver(null) {
                override fun onReceiveResult(code: Int, data: Bundle?) { result = code }
            }), 0, 1)
        awaitIo(service)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, result)
        assertTrue(shadowOf(service).isStoppedBySelf)
        controller.destroy()
        awaitIo(service)
    }

    @Test fun journalFailureStopsCaptureAndLeavesErrorNotification() {
        val controller = Robolectric.buildService(LocationRecordingService::class.java).create()
        val service = controller.get()
        val store = get(service, "store") as RecordingStore
        store.start(config())
        set(service, "currentConfig", config())
        set(service, "acceptingLocations", true)
        val failure = LocationRecordingService::class.java.getDeclaredMethod(
            "storageFailure", Exception::class.java, Int::class.javaPrimitiveType,
        ).apply { isAccessible = true }
        executor(service).submit { failure.invoke(service, IOException("disk full"), 0) }.get(5, TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()
        awaitIo(service)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(false, get(service, "acceptingLocations"))
        assertEquals(false, store.getState()["isRecording"])
        assertEquals("disk full", store.getState()["lastError"])
        val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertNotNull(shadowOf(notifications).getNotification(-1918274))
        assertTrue(shadowOf(service).isStoppedBySelf)
        controller.destroy()
        awaitIo(service)
    }

    private fun config() = RecorderConfig.fromJson("""{"key":"test","intervalMs":1000,"coordinateType":1,
        "mockEnable":false,"requestLevel":0,"locMode":10,"allowGps":true,"allowCache":false,
        "gpsFirst":false,"gpsFirstTimeOutMs":5000,"notificationOptions":{"id":1,"channelId":"test",
        "channelName":"Test","notificationTitle":"Recording"}}""")

    private fun get(service: LocationRecordingService, name: String): Any? =
        LocationRecordingService::class.java.getDeclaredField(name).apply { isAccessible = true }.get(service)
    private fun set(service: LocationRecordingService, name: String, value: Any?) {
        LocationRecordingService::class.java.getDeclaredField(name).apply { isAccessible = true }.set(service, value)
    }
    private fun executor(service: LocationRecordingService) = get(service, "io") as ExecutorService
    private fun awaitIo(service: LocationRecordingService) {
        val executor = executor(service)
        if (executor.isShutdown) executor.awaitTermination(5, TimeUnit.SECONDS)
        else executor.submit {}.get(5, TimeUnit.SECONDS)
    }
}
