package com.flutter_tencent_lbs_plugin.recorder

import com.tencent.map.geolocation.TencentLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class RecordingStoreTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun before() { clean() }
    @After fun after() { clean() }

    private fun clean() {
        context.deleteDatabase("tencent_location_recording.db")
        java.io.File(context.filesDir, "tencent_location_recording_failure.txt").delete()
    }

    @Test fun journalSurvivesProcessReopenAndAcknowledgementIsExplicit() {
        val store = RecordingStore(context)
        assertEquals(false, store.getState()["initialized"])
        store.start(config())
        store.reportError("temporary SDK error")
        store.append(1234, point())
        assertNull(store.getState()["lastError"])
        val first = store.readBatch(1)
        val firstSequence = sequence(first)
        store.close()

        val reopened = RecordingStore(context)
        assertEquals(first, reopened.readBatch(1)) // Reading never consumes a point.
        assertTrue(reopened.activeConfig() != null)
        assertEquals(true, reopened.getState()["isRecording"])
        assertFailsWith<IllegalArgumentException> { reopened.acknowledge("old-journal", firstSequence) }
        assertEquals(first, reopened.readBatch(1))
        reopened.acknowledge(first["journalId"] as String, firstSequence)
        assertTrue((reopened.readBatch(1)["locations"] as List<*>).isEmpty())
        reopened.append(5678, point())
        assertTrue(sequence(reopened.readBatch(1)) > firstSequence) // AUTOINCREMENT after deletion.
        reopened.stop("storage failure")
        assertNull(reopened.activeConfig())
        assertEquals("storage failure", reopened.getState()["lastError"])
        reopened.close()
    }

    @Test fun newDatabaseHasNewIdentityAndStopCompletesMigration() {
        val store = RecordingStore(context)
        val firstId = store.readBatch(1)["journalId"]
        store.stop()
        assertEquals(mapOf("initialized" to true, "isRecording" to false, "lastError" to null), store.getState())
        store.close()
        context.deleteDatabase("tencent_location_recording.db")
        val replacement = RecordingStore(context)
        assertNotEquals(firstId, replacement.readBatch(1)["journalId"])
        replacement.close()
    }

    @Test fun batchesAreBoundedAndOnlyAcknowledgedPrefixIsDeleted() {
        val store = RecordingStore(context)
        repeat(3) { store.append(it.toLong(), point()) }
        val first = store.readBatch(1)
        store.acknowledge(first["journalId"] as String, sequence(first))
        assertEquals(2, (store.readBatch(256)["locations"] as List<*>).size)
        assertFailsWith<IllegalArgumentException> { store.readBatch(257) }
        assertFailsWith<IllegalArgumentException> { store.acknowledge(first["journalId"] as String, -1) }
        store.close()
    }

    @Test fun failureMarkerOverridesStaleActiveStateUntilExplicitNewStart() {
        val store = RecordingStore(context)
        store.start(config())
        store.markFailure("disk full")
        assertEquals(false, store.getState()["isRecording"])
        assertEquals("disk full", store.getState()["lastError"])
        assertNull(store.activeConfig())
        store.start(config())
        assertEquals(true, store.getState()["isRecording"])
        assertNull(store.getState()["lastError"])
        store.close()
    }

    @Test fun sdkPointSnapshotPreservesTimestampAndRejectsInvalidRequiredNumbers() {
        val location = Mockito.mock(TencentLocation::class.java)
        Mockito.`when`(location.latitude).thenReturn(22.3)
        Mockito.`when`(location.longitude).thenReturn(114.2)
        Mockito.`when`(location.accuracy).thenReturn(3f)
        Mockito.`when`(location.time).thenReturn(1234L)
        Mockito.`when`(location.sourceProvider).thenReturn(TencentLocation.GPS_PROVIDER)
        val snapshot = LocationRecordingService.locationJson(location)!!
        assertEquals(1234L, snapshot.getLong("time"))
        assertEquals(0, snapshot.getInt("locationSource"))
        Mockito.`when`(location.latitude).thenReturn(Double.NaN)
        assertNull(LocationRecordingService.locationJson(location))
    }

    private fun point() = JSONObject("""{"code":0,"latitude":22.3,"longitude":114.2,"horizontalAccuracy":3.0,"time":1234}""")

    private fun config() = RecorderConfig.fromJson("""{"key":"test","intervalMs":1000,"coordinateType":1,
        "mockEnable":false,"requestLevel":0,"locMode":10,"allowGps":true,"allowCache":false,
        "gpsFirst":false,"gpsFirstTimeOutMs":5000,"notificationOptions":{"id":1,"channelId":"test",
        "channelName":"Test","notificationTitle":"Recording"}}""")

    @Suppress("UNCHECKED_CAST")
    private fun sequence(batch: Map<String, Any?>): Long =
        ((batch["locations"] as List<Map<String, Any?>>).first()["sequence"] as Long)
}
