package com.flutter_tencent_lbs_plugin.recorder

import android.database.sqlite.SQLiteDatabase
import android.system.ErrnoException
import android.system.OsConstants
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
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, shadows = [RecorderMarkerLinuxShadow::class])
class RecordingStoreTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun before() { clean() }
    @After fun after() {
        clean()
        assertTrue(RecorderMarkerLinuxShadow.openedDescriptors.all { !it.valid() }, "Marker read leaked a descriptor")
    }

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

    @Test fun latestSnapshotSurvivesAcknowledgementAndDoesNotDependOnPendingRows() {
        val store = RecordingStore(context)
        assertNull(store.getSnapshot()["latestLocation"])
        store.start(config())
        store.append(5678, point())
        val batch = store.readBatch(256)
        val snapshot = store.getSnapshot()
        assertEquals(batch["journalId"], snapshot["journalId"])
        assertEquals((batch["locations"] as List<*>).single(), snapshot["latestLocation"])
        assertEquals(store.getState(), snapshot["state"])
        store.acknowledge(batch["journalId"] as String, sequence(batch))
        assertTrue((store.readBatch(256)["locations"] as List<*>).isEmpty())
        assertEquals(snapshot, store.getSnapshot())
        store.close()

        val reopened = RecordingStore(context)
        assertEquals(snapshot, reopened.getSnapshot())
        reopened.close()
    }

    @Test fun boundedBatchDoesNotChasePointsAppendedAfterTheSnapshot() {
        val store = RecordingStore(context)
        store.append(1, point())
        val initial = store.readBatch(256)
        val boundary = sequence(initial)
        store.append(2, point())
        assertEquals(initial, store.readBatch(256, boundary))
        store.acknowledge(initial["journalId"] as String, boundary)
        val empty = store.readBatch(256, boundary)
        assertEquals(initial["journalId"], empty["journalId"])
        assertTrue((empty["locations"] as List<*>).isEmpty())
        assertEquals(1, (store.readBatch(256)["locations"] as List<*>).size)
        assertFailsWith<IllegalArgumentException> { store.readBatch(256, 0) }
        store.close()
    }

    @Test fun failedAppendPublishesNeitherJournalRowNorLatestSnapshot() {
        val store = RecordingStore(context)
        store.append(1, point())
        val beforeBatch = store.readBatch(256)
        val beforeSnapshot = store.getSnapshot()
        store.writableDatabase.execSQL("""CREATE TRIGGER fail_latest_update
            BEFORE UPDATE OF latest_sequence ON recording_state
            BEGIN SELECT RAISE(ABORT, 'injected snapshot failure'); END""")
        assertFailsWith<android.database.SQLException> { store.append(2, point()) }
        assertEquals(beforeBatch, store.readBatch(256))
        assertEquals(beforeSnapshot, store.getSnapshot())
        store.close()
    }

    @Test fun versionOneMigrationPreservesIdentityPendingRowsAndRecordingIntent() {
        val old = SQLiteDatabase.openOrCreateDatabase(
            context.getDatabasePath("tencent_location_recording.db"), null,
        )
        old.execSQL("""CREATE TABLE recording_state (
            id INTEGER PRIMARY KEY CHECK (id = 1), journal_id TEXT NOT NULL,
            initialized INTEGER NOT NULL DEFAULT 0, active INTEGER NOT NULL DEFAULT 0,
            config TEXT, last_error TEXT)""")
        old.execSQL("""CREATE TABLE locations (
            sequence INTEGER PRIMARY KEY AUTOINCREMENT,
            received_timestamp_ms INTEGER NOT NULL, location_json TEXT NOT NULL)""")
        old.execSQL("""INSERT INTO recording_state
            (id, journal_id, initialized, active, config) VALUES (1, 'v1-journal', 1, 1, ?)""",
            arrayOf(config().json.toString()))
        old.execSQL("INSERT INTO locations VALUES (7, 111, ?)", arrayOf(point().toString()))
        old.execSQL("INSERT INTO locations VALUES (9, 222, ?)", arrayOf(point().toString()))
        old.version = 1
        old.close()

        val migrated = RecordingStore(context)
        val batch = migrated.readBatch(256)
        assertEquals("v1-journal", batch["journalId"])
        @Suppress("UNCHECKED_CAST")
        val rows = batch["locations"] as List<Map<String, Any?>>
        assertEquals(listOf(7L, 9L), rows.map { it["sequence"] })
        assertEquals(rows.last(), migrated.getSnapshot()["latestLocation"])
        assertEquals(true, migrated.getState()["isRecording"])
        assertEquals(config().json.toString(), migrated.activeConfig()!!.json.toString())
        // A helper that observed v1 before waiting for the other process's
        // upgrade transaction must not attempt the ALTER statements again.
        migrated.onUpgrade(migrated.writableDatabase, 1, 2)
        assertEquals(rows.last(), migrated.getSnapshot()["latestLocation"])
        migrated.acknowledge("v1-journal", 9)
        assertEquals(rows.last(), migrated.getSnapshot()["latestLocation"])
        migrated.append(333, point())
        assertTrue(sequence(migrated.readBatch(1)) > 9)
        migrated.close()
    }

    @Test fun readersDoNotNeedTheWriterLockAndObserveOneCommittedSnapshot() {
        val writer = RecordingStore(context)
        val reader = RecordingStore(context)
        writer.append(1, point())
        val committedBatch = reader.readBatch(256)
        val committedSnapshot = reader.getSnapshot()
        val executor = Executors.newSingleThreadExecutor()
        val db = writer.writableDatabase
        db.beginTransactionNonExclusive()
        try {
            writer.append(2, point()) // This remains uncommitted in the outer transaction.
            val reading = executor.submit<Pair<Map<String, Any?>, Map<String, Any?>>> {
                Pair(reader.readBatch(256), reader.getSnapshot())
            }
            val observed = reading.get(3, TimeUnit.SECONDS)
            assertEquals(committedBatch, observed.first)
            assertEquals(committedSnapshot, observed.second)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            executor.shutdown()
            executor.awaitTermination(3, TimeUnit.SECONDS)
            reader.close()
        }
        assertEquals(2, (writer.readBatch(256)["locations"] as List<*>).size)
        writer.close()
    }

    @Test fun missingMarkerIsBenignButOtherOpenErrorsStillFail() {
        val marker = File(context.filesDir, "tencent_location_recording_failure.txt")
        val store = RecordingStore(context)
        // Both clear operations tolerate the marker already having disappeared.
        store.start(config())
        store.stop()
        assertNull(store.getState()["lastError"])
        assertTrue(marker.mkdir())
        // The default shadow rejects a directory at open with EIO. On Android
        // O_RDONLY can open it and reading fails with EISDIR. Neither is absence.
        val error = assertFailsWith<ErrnoException> { store.getState() }
        assertEquals(OsConstants.EIO, error.errno)
        marker.delete()
        assertNull(store.getState()["lastError"])
        store.close()
    }

    @Test fun markerCreatedAfterMissingOpenIsObservedByTheNextQuery() {
        val store = RecordingStore(context)
        store.start(config())
        RecorderMarkerLinuxShadow.createOnMissingOpen = "new failure"
        // open observed ENOENT, even though the file now exists. A later stat
        // must not replace that original result and fail this status query.
        @Suppress("UNCHECKED_CAST")
        val state = store.getSnapshot()["state"] as Map<String, Any?>
        assertEquals(true, state["isRecording"])
        assertNull(state["lastError"])
        assertEquals("new failure", store.getState()["lastError"])
        assertEquals(false, store.getState()["isRecording"])
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
