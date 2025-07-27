package com.sersoluciones.flutter_pos_printer_platform

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.startActivityForResult
import com.sersoluciones.flutter_pos_printer_platform.bluetooth.BluetoothConnection
import com.sersoluciones.flutter_pos_printer_platform.bluetooth.BluetoothConstants
import com.sersoluciones.flutter_pos_printer_platform.bluetooth.BluetoothService
import com.sersoluciones.flutter_pos_printer_platform.bluetooth.BluetoothService.Companion.TAG
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

    private final var TAG = "FlutterPosPrinterPlatformPlugin"

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
                val deviceId: String? = call.argument("deviceId")
                connectPrinterWithAddress(vendor, product, deviceId, result)
            }
            // keep all other calls the same...
            else -> result.notImplemented()
        }
    }

    private fun connectPrinterWithAddress(vendorId: Int?, productId: Int?, deviceAddress: String?, result: Result) {
        adapter.setHandler(usbHandler)
        val usbManager = context?.getSystemService(Context.USB_SERVICE) as UsbManager
        val deviceList = usbManager.deviceList.values

        var targetDevice: UsbDevice? = null

        if (!deviceAddress.isNullOrBlank()) {
            targetDevice = deviceList.firstOrNull { it.deviceName == deviceAddress }
        }

        if (targetDevice == null && vendorId != null && productId != null) {
            targetDevice = deviceList.firstOrNull { it.vendorId == vendorId && it.productId == productId }
        }

        if (targetDevice != null) {
            if (!adapter.selectDevice(targetDevice.vendorId, targetDevice.productId)) {
                result.success(false)
            } else {
                result.success(true)
            }
        } else {
            result.error("USB_DEVICE_NOT_FOUND", "No USB device found with given vendor/product ID or device address", null)
        }
    }

    // other methods stay unchanged

    companion object {
        const val PERMISSION_ALL = 1
        const val PERMISSION_ENABLE_BLUETOOTH = 999
        const val methodChannel = "com.sersoluciones.flutter_pos_printer_platform"
        const val eventChannelBT = "com.sersoluciones.flutter_pos_printer_platform/bt_state"
        const val eventChannelUSB = "com.sersoluciones.flutter_pos_printer_platform/usb_state"
    }
}
