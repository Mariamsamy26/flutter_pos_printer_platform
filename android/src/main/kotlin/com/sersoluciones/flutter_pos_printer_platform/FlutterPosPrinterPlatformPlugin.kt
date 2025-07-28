package com.sersoluciones.flutter_pos_printer_platform

import android.app.Activity
import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import androidx.annotation.NonNull
import com.sersoluciones.flutter_pos_printer_platform.usb.USBPrinterService
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry

class FlutterPosPrinterPlatformPlugin : FlutterPlugin, MethodChannel.MethodCallHandler,
    PluginRegistry.RequestPermissionsResultListener, PluginRegistry.ActivityResultListener,
    ActivityAware {

    private val TAG = "FlutterPosPrinterPlatformPlugin"

    private var channel: MethodChannel? = null
    private var messageUSBChannel: EventChannel? = null
    private var eventUSBSink: EventChannel.EventSink? = null
    private var context: Context? = null
    private var currentActivity: Activity? = null
    private lateinit var adapter: USBPrinterService

    private val usbHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            when (msg.what) {
                USBPrinterService.STATE_USB_CONNECTED -> eventUSBSink?.success(2)
                USBPrinterService.STATE_USB_CONNECTING -> eventUSBSink?.success(1)
                USBPrinterService.STATE_USB_NONE -> eventUSBSink?.success(0)
            }
        }
    }

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = binding.applicationContext
        adapter = USBPrinterService(context!!)
        adapter.setHandler(usbHandler)

        channel = MethodChannel(binding.binaryMessenger, "com.sersoluciones.flutter_pos_printer_platform")
        channel?.setMethodCallHandler(this)

        messageUSBChannel = EventChannel(binding.binaryMessenger, "com.sersoluciones.flutter_pos_printer_platform/usb_state")
        messageUSBChannel?.setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                eventUSBSink = events
            }

            override fun onCancel(arguments: Any?) {
                eventUSBSink = null
            }
        })
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: MethodChannel.Result) {
        when (call.method) {
            "connectPrinter" -> {
                val vendorId = call.argument<Int>("vendor")
                val productId = call.argument<Int>("product")
                val deviceAddress = call.argument<String>("deviceId")
                connectPrinterWithAddress(vendorId, productId, deviceAddress, result)
            }
            else -> result.notImplemented()
        }
    }

    private fun connectPrinterWithAddress(vendorId: Int?, productId: Int?, deviceAddress: String?, result: MethodChannel.Result) {
    val usbManager = context?.getSystemService(Context.USB_SERVICE) as UsbManager
    val deviceList = usbManager.deviceList.values

    var targetDevice: UsbDevice? = null

    if (!deviceAddress.isNullOrBlank()) {
        // ✅ هنا التعديل الصحيح
        targetDevice = deviceList.firstOrNull { it.deviceId == deviceAddress }
    }

    if (targetDevice == null && vendorId != null && productId != null) {
        targetDevice = deviceList.firstOrNull {
            it.vendorId == vendorId && it.productId == productId && it.deviceId == deviceAddress
        }
    }

    if (targetDevice != null) {
        adapter.setHandler(usbHandler)
        val connected = adapter.connect(targetDevice)
        result.success(connected)
    } else {
        result.error("USB_DEVICE_NOT_FOUND", "No USB device found", null)
    }
}


    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel?.setMethodCallHandler(null)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        currentActivity = binding.activity
    }

    override fun onDetachedFromActivityForConfigChanges() {
        currentActivity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        currentActivity = binding.activity
    }

    override fun onDetachedFromActivity() {
        currentActivity = null
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray): Boolean {
        return false
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?): Boolean {
        return false
    }
}
