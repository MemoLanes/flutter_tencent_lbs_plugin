package com.flutter_tencent_lbs_plugin.recorder

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.util.UUID

/** The journal is shared through SQLite's file locks, never process-local state. */
internal class RecordingStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "tencent_location_recording.db", null, 2) {
    private val failureFile = File(context.filesDir, "tencent_location_recording_failure.txt")

    init {
        setWriteAheadLoggingEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS recording_state (
            id INTEGER PRIMARY KEY CHECK (id = 1), journal_id TEXT NOT NULL,
            initialized INTEGER NOT NULL DEFAULT 0, active INTEGER NOT NULL DEFAULT 0,
            config TEXT, last_error TEXT, latest_sequence INTEGER,
            latest_received_timestamp_ms INTEGER, latest_location_json TEXT)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS locations (
            sequence INTEGER PRIMARY KEY AUTOINCREMENT,
            received_timestamp_ms INTEGER NOT NULL, location_json TEXT NOT NULL)""")
        db.insertWithOnConflict("recording_state", null, ContentValues().apply {
            put("id", 1)
            put("journal_id", UUID.randomUUID().toString())
        }, SQLiteDatabase.CONFLICT_IGNORE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // A second process may have completed this upgrade while this helper
        // waited for SQLiteOpenHelper's transaction to acquire the writer lock.
        if (db.version == newVersion) return
        check(oldVersion == 1 && newVersion == 2) {
            "Unsupported recording journal upgrade: $oldVersion -> $newVersion"
        }
        // SQLiteOpenHelper runs the upgrade in one transaction. Preserve the
        // journal identity and every pending row; seed the independent snapshot
        // from the newest point that v1 still has available.
        db.execSQL("ALTER TABLE recording_state ADD COLUMN latest_sequence INTEGER")
        db.execSQL("ALTER TABLE recording_state ADD COLUMN latest_received_timestamp_ms INTEGER")
        db.execSQL("ALTER TABLE recording_state ADD COLUMN latest_location_json TEXT")
        db.execSQL("""UPDATE recording_state SET
            latest_sequence = (SELECT sequence FROM locations ORDER BY sequence DESC LIMIT 1),
            latest_received_timestamp_ms = (SELECT received_timestamp_ms FROM locations ORDER BY sequence DESC LIMIT 1),
            latest_location_json = (SELECT location_json FROM locations ORDER BY sequence DESC LIMIT 1)
            WHERE id = 1""")
    }

    fun getState(): Map<String, Any?> {
        val failure = readFailureMarker()
        return readableDatabase.rawQuery(
            "SELECT initialized, active, last_error FROM recording_state WHERE id = 1", null,
        ).use { cursor ->
            check(cursor.moveToFirst())
            state(cursor, failure)
        }
    }

    /** UI status and newest durable fix, independent of journal consumption. */
    fun getSnapshot(): Map<String, Any?> {
        val failure = readFailureMarker()
        return readableDatabase.rawQuery("""SELECT journal_id, initialized, active, last_error,
            latest_sequence AS sequence, latest_received_timestamp_ms AS received_timestamp_ms,
            latest_location_json AS location_json FROM recording_state WHERE id = 1""", null,
        ).use { cursor ->
            check(cursor.moveToFirst())
            mapOf(
                "journalId" to cursor.getString(cursor.getColumnIndexOrThrow("journal_id")),
                "state" to state(cursor, failure),
                "latestLocation" to recordedLocation(cursor),
            )
        }
    }

    fun activeConfig(): RecorderConfig? {
        if (readFailureMarker() != null) return null
        return readableDatabase.rawQuery(
            "SELECT config FROM recording_state WHERE id = 1 AND active = 1", null,
        ).use { if (it.moveToFirst()) RecorderConfig.fromJson(it.getString(0)) else null }
    }

    fun start(config: RecorderConfig) {
        writableDatabase.update("recording_state", ContentValues().apply {
            put("initialized", 1)
            put("active", 1)
            put("config", config.json.toString())
            putNull("last_error")
        }, "id = 1", null)
        clearFailureMarker()
    }

    fun stop(error: String? = null) {
        writableDatabase.update("recording_state", ContentValues().apply {
            put("initialized", 1)
            put("active", 0)
            if (error == null) putNull("last_error") else put("last_error", error)
        }, "id = 1", null)
        if (error == null) clearFailureMarker()
    }

    fun markFailure(message: String) {
        failureFile.writeText(message)
    }

    fun reportError(message: String) {
        writableDatabase.update("recording_state", ContentValues().apply {
            put("last_error", message)
        }, "id = 1", null)
    }

    fun append(receivedTimestampMs: Long, location: JSONObject) {
        val db = writableDatabase
        val json = location.toString()
        db.beginTransaction()
        try {
            val sequence = db.insertOrThrow("locations", null, ContentValues().apply {
                put("received_timestamp_ms", receivedTimestampMs)
                put("location_json", json)
            })
            // The latest fix must never expose an uncommitted journal row.
            // ACK only deletes locations; it intentionally leaves this snapshot.
            db.execSQL("""UPDATE recording_state SET latest_sequence = ?,
                latest_received_timestamp_ms = ?, latest_location_json = ?,
                last_error = CASE WHEN active = 1 THEN NULL ELSE last_error END
                WHERE id = 1""", arrayOf<Any>(sequence, receivedTimestampMs, json))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun readBatch(limit: Int, throughSequence: Long? = null): Map<String, Any?> {
        require(limit in 1..256) { "limit must be between 1 and 256" }
        require(throughSequence == null || throughSequence > 0) {
            "throughSequence must be positive"
        }
        val filter = if (throughSequence == null) "" else "WHERE sequence <= ?"
        val arguments = if (throughSequence == null) arrayOf(limit.toString())
            else arrayOf(throughSequence.toString(), limit.toString())
        // One SELECT gives journal identity and rows from the same read
        // snapshot. A LEFT JOIN also returns the identity for an empty batch.
        // Unlike beginTransactionNonExclusive(), this never takes a writer lock.
        return readableDatabase.rawQuery("""SELECT state.journal_id, pending.sequence,
            pending.received_timestamp_ms, pending.location_json
            FROM recording_state AS state LEFT JOIN (
                SELECT sequence, received_timestamp_ms, location_json FROM locations
                $filter ORDER BY sequence LIMIT ?
            ) AS pending ON 1 = 1 WHERE state.id = 1 ORDER BY pending.sequence""", arguments,
        ).use { cursor ->
            check(cursor.moveToFirst())
            val journalId = cursor.getString(cursor.getColumnIndexOrThrow("journal_id"))
            val locations = buildList {
                do {
                    recordedLocation(cursor)?.let { add(it) }
                } while (cursor.moveToNext())
            }
            mapOf("journalId" to journalId, "locations" to locations)
        }
    }

    fun acknowledge(expectedJournalId: String, sequence: Long) {
        require(sequence >= 0) { "sequence must not be negative" }
        val db = writableDatabase
        db.beginTransaction()
        try {
            require(journalId(db) == expectedJournalId) {
                "Recording journal changed; read a new batch before acknowledging"
            }
            db.delete("locations", "sequence <= ?", arrayOf(sequence.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun state(cursor: Cursor, failure: String?): Map<String, Any?> = mapOf(
        "initialized" to (cursor.getInt(cursor.getColumnIndexOrThrow("initialized")) != 0),
        "isRecording" to (failure == null && cursor.getInt(cursor.getColumnIndexOrThrow("active")) != 0),
        "lastError" to (failure ?: cursor.getString(cursor.getColumnIndexOrThrow("last_error"))),
    )

    private fun recordedLocation(cursor: Cursor): Map<String, Any?>? {
        val sequenceColumn = cursor.getColumnIndexOrThrow("sequence")
        if (cursor.isNull(sequenceColumn)) return null
        val json = JSONObject(cursor.getString(cursor.getColumnIndexOrThrow("location_json")))
        val location = json.keys().asSequence().associateWith { key ->
            json.get(key).let { if (it === JSONObject.NULL) null else it }
        }
        return mapOf(
            "sequence" to cursor.getLong(sequenceColumn),
            "receivedTimestampMs" to cursor.getLong(cursor.getColumnIndexOrThrow("received_timestamp_ms")),
            "location" to location,
        )
    }

    private fun journalId(db: SQLiteDatabase): String = db.rawQuery(
        "SELECT journal_id FROM recording_state WHERE id = 1", null,
    ).use { check(it.moveToFirst()); it.getString(0) }

    private fun readFailureMarker(): String? {
        val descriptor = try {
            Os.open(failureFile.path, OsConstants.O_RDONLY, 0)
        } catch (error: ErrnoException) {
            // Only the open result decides absence: another process may create
            // or delete the marker immediately afterward. Other I/O errors fail.
            if (error.errno == OsConstants.ENOENT) return null
            throw error
        }
        return try {
            FileInputStream(descriptor).reader().use { it.readText() }
        } finally {
            // FileInputStream(FileDescriptor) does not own the fd on Android.
            if (descriptor.valid()) Os.close(descriptor)
        }
    }

    private fun clearFailureMarker() {
        check(failureFile.delete() || !failureFile.exists()) {
            "Cannot clear previous recording failure"
        }
    }
}
