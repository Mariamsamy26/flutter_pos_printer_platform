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
import com.sersoluciones.flutter_pos_printer_platform.bluetooth.BluetoothService
import com.sersoluciones.flutter_pos_printer_platform.usb.USBPrinterService
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import io.flutter.plugin.common.BinaryMessenger

class FlutterPosPrinterPlatformPlugin : FlutterPlugin, MethodCallHandler, PluginRegistry.RequestPermissionsResultListener,
    PluginRegistry.ActivityResultListener, ActivityAware {

    private val TAG = "FlutterPosPrinterPlatformPlugin"

    private var binaryMessenger: BinaryMessenger? = null
    private var channel: MethodChannel? = null
    private var messageChannel: EventChannel? = null
    private var messageUSBChannel: EventChannel? = null
    private var eventSink: EventChannel.EventSink? = null
    private var eventUSBSink: EventChannel.EventSink? = null
    private var context: Context? = null
    private var currentActivity: Activity? = null
    private var requestPermissionBT: Boolean = false
    private var isBle: Boolean = false
    private var isScan: Boolean = false
    lateinit var adapter: USBPrinterService
    private lateinit var bluetoothService: BluetoothService

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

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        isScan = false
        Log.d(TAG, "method call " + call.method.toString())
        when (call.method) {
            "connectPrinter" -> {
                val vendor: Int? = call.argument("vendor")
                val product: Int? = call.argument("product")
                val deviceAddress: String? = call.argument("deviceId")
                connectPrinterWithAddress(vendor, product, deviceAddress, result)
            }
            else -> result.notImplemented()
        }
    }

    private fun connectPrinterWithAddress(vendorId: Int?, productId: Int?, deviceAddress: String?, result: Result) {
        adapter.setHandler(usbHandler)
        val usbManager = context?.getSystemService(Context.USB_SERVICE) as UsbManager
        val deviceList = usbManager.deviceList.values

        var targetDevice: UsbDevice? = null

        if (!deviceAddress.isNullOrBlank()) {
            targetDevice = deviceList.firstOrNull { it.deviceId == deviceAddress }
            Log.d(TAG, "Trying to connect by full address: $deviceAddress, found: ${targetDevice != null}")
        }

        if (targetDevice == null && vendorId != null && productId != null&& deviceId!= null) {
            targetDevice = deviceList.firstOrNull { it.vendorId == vendorId && it.productId == productId && it.deviceId == deviceId}
            Log.d(TAG, "Fallback to vendor/product search: $vendorId/$productId, found: ${targetDevice != null}")
        }

        if (targetDevice != null) {
            val success = adapter.selectDevice(targetDevice.vendorId,targetDevice.productId,targetDevice.deviceId)
            result.success(success)
        } else {
            result.error("USB_DEVICE_NOT_FOUND", "No USB device found with given address or vendor/product", null)
        }
    }

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        this.context = binding.applicationContext
        this.binaryMessenger = binding.binaryMessenger
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        this.currentActivity = binding.activity
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {}
    override fun onDetachedFromActivityForConfigChanges() {}
    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {}
    override fun onDetachedFromActivity() {}
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray): Boolean = false
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean = false

    companion object {
        const val methodChannel = "com.sersoluciones.flutter_pos_printer_platform"
        const val eventChannelBT = "com.sersoluciones.flutter_pos_printer_platform/bt_state"
        const val eventChannelUSB = "com.sersoluciones.flutter_pos_printer_platform/usb_state"
    }
}
