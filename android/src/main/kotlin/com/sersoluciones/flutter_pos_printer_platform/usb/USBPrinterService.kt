package com.sersoluciones.flutter_pos_printer_platform.usb

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.*
import android.os.Handler
import android.util.Base64
import android.util.Log
import android.widget.Toast
import com.sersoluciones.flutter_pos_printer_platform.R
import java.nio.charset.Charset
import java.util.*

class USBPrinterService private constructor(private var mHandler: Handler?) {
    private var mContext: Context? = null
    private var mUSBManager: UsbManager? = null
    private var mPermissionIndent: PendingIntent? = null
    private var mUsbDevice: UsbDevice? = null
    private var mUsbDeviceConnection: UsbDeviceConnection? = null
    private var mUsbInterface: UsbInterface? = null
    private var mEndPoint: UsbEndpoint? = null
    var state: Int = STATE_USB_NONE

    fun setHandler(handler: Handler?) {
        mHandler = handler
    }

    private val mUsbDeviceReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if ((ACTION_USB_PERMISSION == action)) {
                synchronized(this) {
                    val usbDevice: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        Log.i(
                            LOG_TAG,
                            "✅ Success get permission for device ${usbDevice?.deviceId}, vendor_id: ${usbDevice?.vendorId} product_id: ${usbDevice?.productId}"
                        )
                        mUsbDevice = usbDevice
                        state = STATE_USB_CONNECTED
                        mHandler?.obtainMessage(STATE_USB_CONNECTED)?.sendToTarget()
                    } else {
                        Toast.makeText(context, mContext?.getString(R.string.user_refuse_perm) + ": ${usbDevice!!.deviceName}", Toast.LENGTH_LONG).show()
                        state = STATE_USB_NONE
                        mHandler?.obtainMessage(STATE_USB_NONE)?.sendToTarget()
                    }
                }
            } else if ((UsbManager.ACTION_USB_DEVICE_DETACHED == action)) {
                if (mUsbDevice != null) {
                    Toast.makeText(context, mContext?.getString(R.string.device_off), Toast.LENGTH_LONG).show()
                    closeConnectionIfExists()
                    state = STATE_USB_NONE
                    mHandler?.obtainMessage(STATE_USB_NONE)?.sendToTarget()
                }
            }
        }
    }

    fun init(reactContext: Context?) {
        mContext = reactContext
        mUSBManager = mContext!!.getSystemService(Context.USB_SERVICE) as UsbManager
        mPermissionIndent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            PendingIntent.getBroadcast(mContext, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE)
        } else {
            PendingIntent.getBroadcast(mContext, 0, Intent(ACTION_USB_PERMISSION), 0)
        }
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        mContext!!.registerReceiver(mUsbDeviceReceiver, filter)
        Log.v(LOG_TAG, "📦 ESC/POS Printer initialized")
    }

    fun closeConnectionIfExists() {
        if (mUsbDeviceConnection != null) {
            mUsbDeviceConnection!!.releaseInterface(mUsbInterface)
            mUsbDeviceConnection!!.close()
            mUsbInterface = null
            mEndPoint = null
            mUsbDevice = null
            mUsbDeviceConnection = null
        }
    }

    val deviceList: List<UsbDevice>
        get() {
            if (mUSBManager == null) {
                Toast.makeText(mContext, mContext?.getString(R.string.not_usb_manager), Toast.LENGTH_LONG).show()
                return emptyList()
            }
            return ArrayList(mUSBManager!!.deviceList.values)
        }

    fun selectDevice(vendorId: Int, productId: Int): Boolean {
        synchronized(printLock) {
            closeConnectionIfExists()
            val usbDevices: List<UsbDevice> = deviceList
            usbDevices.forEach {
                Log.d(LOG_TAG, "🔍 Device: ${it.deviceName} | Vendor=${it.vendorId} | Product=${it.productId}")
            }
            for (usbDevice: UsbDevice in usbDevices) {
                if ((usbDevice.vendorId == vendorId) && (usbDevice.productId == productId)) {
                    Log.v(LOG_TAG, "Request for device: vendor_id=${usbDevice.vendorId}, product_id=${usbDevice.productId}")
                    mUSBManager!!.requestPermission(usbDevice, mPermissionIndent)
                    state = STATE_USB_CONNECTING
                    mHandler?.obtainMessage(STATE_USB_CONNECTING)?.sendToTarget()
                    return true
                }
            }
            return false
        }
    }

    fun selectDeviceByName(deviceName: String): Boolean {
        synchronized(printLock) {
            closeConnectionIfExists()
            val usbDevices: List<UsbDevice> = deviceList
            usbDevices.forEach {
                Log.d(LOG_TAG, "🔍 Device: ${it.deviceName} | Vendor=${it.vendorId} | Product=${it.productId}")
            }
            for (usbDevice in usbDevices) {
                if (usbDevice.deviceName == deviceName) {
                    Log.v(LOG_TAG, "📌 Requesting device by name: ${usbDevice.deviceName}")
                    mUSBManager?.requestPermission(usbDevice, mPermissionIndent)
                    state = STATE_USB_CONNECTING
                    mHandler?.obtainMessage(STATE_USB_CONNECTING)?.sendToTarget()
                    return true
                }
            }
            Log.e(LOG_TAG, "❌ No USB device found with name: $deviceName")
            return false
        }
    }

    private fun openConnection(): Boolean {
        if (mUsbDevice == null || mUSBManager == null) {
            Log.e(LOG_TAG, "❌ USB Device or Manager not initialized")
            return false
        }
        if (mUsbDeviceConnection != null) return true

        val usbInterface = mUsbDevice!!.getInterface(0)
        for (i in 0 until usbInterface.endpointCount) {
            val ep = usbInterface.getEndpoint(i)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK && ep.direction == UsbConstants.USB_DIR_OUT) {
                val usbDeviceConnection = mUSBManager!!.openDevice(mUsbDevice)
                if (usbDeviceConnection == null) {
                    Log.e(LOG_TAG, "❌ Failed to open USB Connection")
                    return false
                }
                if (usbDeviceConnection.claimInterface(usbInterface, true)) {
                    Toast.makeText(mContext, "✅ تم الاتصال بالطابعة بنجاح", Toast.LENGTH_SHORT).show()
                    mEndPoint = ep
                    mUsbInterface = usbInterface
                    mUsbDeviceConnection = usbDeviceConnection
                    return true
                } else {
                    usbDeviceConnection.close()
                    Log.e(LOG_TAG, "❌ Failed to claim USB interface")
                    return false
                }
            }
        }
        return true
    }

    fun printText(text: String): Boolean {
        Log.v(LOG_TAG, "🖨️ Printing text")
        return if (openConnection()) {
            Thread {
                synchronized(printLock) {
                    val bytes = text.toByteArray(Charset.forName("UTF-8"))
                    val b = mUsbDeviceConnection!!.bulkTransfer(mEndPoint, bytes, bytes.size, 100000)
                    Log.i(LOG_TAG, "✅ Return code: $b")
                }
            }.start()
            true
        } else false
    }

    fun printRawData(data: String): Boolean {
        Log.v(LOG_TAG, "🖨️ Printing raw data")
        return if (openConnection()) {
            Thread {
                synchronized(printLock) {
                    val bytes = Base64.decode(data, Base64.DEFAULT)
                    val b = mUsbDeviceConnection!!.bulkTransfer(mEndPoint, bytes, bytes.size, 100000)
                    Log.i(LOG_TAG, "✅ Return code: $b")
                }
            }.start()
            true
        } else false
    }

    fun printBytes(bytes: ArrayList<Int>): Boolean {
        Log.v(LOG_TAG, "🖨️ Printing bytes")
        return if (openConnection()) {
            val chunkSize = mEndPoint!!.maxPacketSize
            Thread {
                synchronized(printLock) {
                    val byteData = ByteArray(bytes.size) { i -> bytes[i].toByte() }
                    val chunks = byteData.size / chunkSize + if (byteData.size % chunkSize > 0) 1 else 0
                    for (i in 0 until chunks) {
                        val start = i * chunkSize
                        val end = minOf(byteData.size, start + chunkSize)
                        val buffer = Arrays.copyOfRange(byteData, start, end)
                        val b = mUsbDeviceConnection!!.bulkTransfer(mEndPoint, buffer, buffer.size, 100000)
                        Log.i(LOG_TAG, "📤 Chunk $i return code: $b")
                    }
                }
            }.start()
            true
        } else false
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        private var mInstance: USBPrinterService? = null
        private const val LOG_TAG = "ESC POS Printer"
        private const val ACTION_USB_PERMISSION = "com.flutter_pos_printer.USB_PERMISSION"

        const val STATE_USB_NONE = 0
        const val STATE_USB_CONNECTING = 2
        const val STATE_USB_CONNECTED = 3

        private val printLock = Any()

        fun getInstance(handler: Handler): USBPrinterService {
            if (mInstance == null) {
                mInstance = USBPrinterService(handler)
            }
            return mInstance!!
        }
    }
}
