package com.flutter_tencent_lbs_plugin

import io.flutter.embedding.engine.plugins.FlutterPlugin
import com.flutter_tencent_lbs_plugin.pigeon.TencentLBSFlutterApi
import com.flutter_tencent_lbs_plugin.pigeon.TencentLBSHostApi
import com.flutter_tencent_lbs_plugin.pigeon.TencentLBSHostApiImpl
import com.flutter_tencent_lbs_plugin.recorder.RecorderChannel

class FlutterTencentLBSPlugin : FlutterPlugin {
    private var recorderChannel: RecorderChannel? = null

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        val messenger = binding.binaryMessenger
        val flutterApi = TencentLBSFlutterApi(messenger)
        val hostApi = TencentLBSHostApiImpl(binding.applicationContext, flutterApi)
        TencentLBSHostApi.setUp(messenger, hostApi)
        recorderChannel = RecorderChannel(binding.applicationContext, messenger)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        TencentLBSHostApi.setUp(binding.binaryMessenger, null)
        recorderChannel?.close()
        recorderChannel = null
    }
}
