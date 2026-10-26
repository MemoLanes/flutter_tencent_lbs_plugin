package com.flutter_tencent_lbs_plugin.recorder

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.Executors

/** UI-process adapter. Reads and acknowledgements do not start, bind, or wake the service. */
internal class RecorderChannel(private val context: Context, messenger: BinaryMessenger) :
    MethodChannel.MethodCallHandler {
    private val channel = MethodChannel(messenger, "com.memolanes/tencent_location_recorder")
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val store = RecordingStore(context)
    private val pending = mutableSetOf<PendingCommand>()
    private var closed = false

    init { channel.setMethodCallHandler(this) }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        try {
            when (call.method) {
                "startRecording" -> {
                    val config = RecorderConfig.fromArguments(call.arguments)
                    command(LocationRecordingService.ACTION_START, config, result)
                }
                "stopRecording" -> command(LocationRecordingService.ACTION_STOP, null, result)
                "getState" -> query(result) { store.getState() }
                "getSnapshot" -> query(result) { store.getSnapshot() }
                "readBatch" -> {
                    val limit = call.argument<Number>("limit")?.toInt() ?: 256
                    require(limit in 1..256) { "limit must be between 1 and 256" }
                    val throughSequence = call.argument<Number>("throughSequence")?.toLong()
                    require(throughSequence == null || throughSequence > 0) {
                        "throughSequence must be positive"
                    }
                    query(result) { store.readBatch(limit, throughSequence) }
                }
                "acknowledge" -> {
                    val journalId = requireNotNull(call.argument<String>("journalId")) { "journalId is required" }
                    val sequence = requireNotNull(call.argument<Number>("sequence")) { "sequence is required" }.toLong()
                    query(result) { store.acknowledge(journalId, sequence); null }
                }
                else -> result.notImplemented()
            }
        } catch (error: Exception) { result.error("recording_error", error.message, null) }
    }

    private fun query(result: MethodChannel.Result, operation: () -> Any?) {
        io.execute {
            try {
                val value = operation()
                main.post { if (!closed) result.success(value) }
            } catch (error: Exception) {
                main.post { if (!closed) result.error("recording_error", error.message, null) }
            }
        }
    }

    private fun command(action: String, config: RecorderConfig?, result: MethodChannel.Result) {
        val request = PendingCommand(result)
        pending.add(request)
        val receiver = object : ResultReceiver(main) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                request.finish(if (resultCode == 0) null else resultData?.getString("error") ?: "Recording command failed")
            }
        }
        val intent = Intent(context, LocationRecordingService::class.java).apply {
            this.action = action
            putExtra(LocationRecordingService.EXTRA_RESULT, receiver)
            config?.let { putExtra(LocationRecordingService.EXTRA_CONFIG, it.json.toString()) }
        }
        try {
            if (action == LocationRecordingService.ACTION_START && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else context.startService(intent)
            main.postDelayed(request.timeout, COMMAND_TIMEOUT_MS)
        } catch (error: Exception) { request.finish(error.message ?: error.toString()) }
    }

    fun close() {
        closed = true
        channel.setMethodCallHandler(null)
        pending.toList().forEach { it.finish("Flutter engine detached before recording command completed") }
        io.execute { store.close() }
        io.shutdown()
    }

    private inner class PendingCommand(private val result: MethodChannel.Result) {
        private var complete = false
        val timeout = Runnable { finish("Recording command timed out; query getState before retrying") }
        fun finish(error: String?) {
            if (complete) return
            complete = true
            main.removeCallbacks(timeout)
            pending.remove(this)
            if (error == null) result.success(null) else result.error("recording_error", error, null)
        }
    }

    companion object { private const val COMMAND_TIMEOUT_MS = 30_000L }
}
