package com.flutter_tencent_lbs_plugin.recorder

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject
import java.util.UUID
import java.io.File

/** The journal is shared through SQLite's file locks, never process-local state. */
internal class RecordingStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "tencent_location_recording.db", null, 1) {
    private val failureFile = File(context.filesDir, "tencent_location_recording_failure.txt")
    init {
        setWriteAheadLoggingEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS recording_state (
            id INTEGER PRIMARY KEY CHECK (id = 1), journal_id TEXT NOT NULL,
            initialized INTEGER NOT NULL DEFAULT 0, active INTEGER NOT NULL DEFAULT 0,
            config TEXT, last_error TEXT)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS locations (
            sequence INTEGER PRIMARY KEY AUTOINCREMENT,
            received_timestamp_ms INTEGER NOT NULL, location_json TEXT NOT NULL)""")
        db.insertWithOnConflict("recording_state", null, ContentValues().apply {
            put("id", 1)
            put("journal_id", UUID.randomUUID().toString())
        }, SQLiteDatabase.CONFLICT_IGNORE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("Unsupported recording journal upgrade: $oldVersion -> $newVersion")
    }

    fun getState(): Map<String, Any?> {
        // A SQLite write failure can also prevent updating the state row. A small separate
        // failure marker is a fallback, and the service also posts an error notification.
        val failure = if (failureFile.exists()) failureFile.readText() else null
        return readableDatabase.rawQuery(
            "SELECT initialized, active, last_error FROM recording_state WHERE id = 1", null,
        ).use {
            check(it.moveToFirst())
            mapOf("initialized" to (it.getInt(0) != 0), "isRecording" to (failure == null && it.getInt(1) != 0),
                "lastError" to (failure ?: it.getString(2)))
        }
    }

    fun activeConfig(): RecorderConfig? {
        if (failureFile.exists()) return null
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
        check(!failureFile.exists() || failureFile.delete()) { "Cannot clear previous recording failure" }
    }

    fun stop(error: String? = null) {
        writableDatabase.update("recording_state", ContentValues().apply {
            put("initialized", 1)
            put("active", 0)
            if (error == null) putNull("last_error") else put("last_error", error)
        }, "id = 1", null)
        if (error == null) check(!failureFile.exists() || failureFile.delete()) { "Cannot clear previous recording failure" }
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
        db.beginTransaction()
        try {
            db.insertOrThrow("locations", null, ContentValues().apply {
                put("received_timestamp_ms", receivedTimestampMs)
                put("location_json", location.toString())
            })
            db.update("recording_state", ContentValues().apply { putNull("last_error") },
                "id = 1 AND active = 1 AND last_error IS NOT NULL", null)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun readBatch(limit: Int): Map<String, Any?> {
        require(limit in 1..256) { "limit must be between 1 and 256" }
        val db = readableDatabase
        db.beginTransactionNonExclusive()
        try {
            val journalId = journalId(db)
            val locations = db.rawQuery(
                "SELECT sequence, received_timestamp_ms, location_json FROM locations ORDER BY sequence LIMIT ?",
                arrayOf(limit.toString()),
            ).use { cursor ->
                buildList<Map<String, Any?>> {
                    while (cursor.moveToNext()) {
                        val json = JSONObject(cursor.getString(2))
                        val location = json.keys().asSequence().associateWith { key ->
                            json.get(key).let { if (it === JSONObject.NULL) null else it }
                        }
                        add(mapOf("sequence" to cursor.getLong(0),
                            "receivedTimestampMs" to cursor.getLong(1), "location" to location))
                    }
                }
            }
            db.setTransactionSuccessful()
            return mapOf("journalId" to journalId, "locations" to locations)
        } finally {
            db.endTransaction()
        }
    }

    fun acknowledge(expectedJournalId: String, sequence: Long) {
        require(sequence >= 0) { "sequence must not be negative" }
        val db = writableDatabase
        db.beginTransaction()
        try {
            require(journalId(db) == expectedJournalId) { "Recording journal changed; read a new batch before acknowledging" }
            db.delete("locations", "sequence <= ?", arrayOf(sequence.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun journalId(db: SQLiteDatabase): String = db.rawQuery(
        "SELECT journal_id FROM recording_state WHERE id = 1", null,
    ).use { check(it.moveToFirst()); it.getString(0) }
}
