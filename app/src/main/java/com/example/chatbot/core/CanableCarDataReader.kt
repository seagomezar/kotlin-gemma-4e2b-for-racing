package com.example.chatbot.core

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.example.chatbot.models.TelemetryPacket
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Reads telemetry from a CANable-compatible USB serial adapter.
 *
 * The reader owns the Android USB permission flow, configures the adapter for
 * SLCAN output, emits raw diagnostic streams, and publishes decoded telemetry
 * packets for the dashboard and coaching pipelines.
 */
class CanableCarDataReader(
    context: Context,
    private val baudRate: Int = DEFAULT_BAUD_RATE
) {
    private val appContext = context.applicationContext
    private val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
    private val decoder = CanableTelemetryDecoder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var port: UsbSerialPort? = null
    private var readJob: Job? = null
    private var socket: java.net.Socket? = null
    private var simulatorJob: Job? = null
    private var permissionReceiver: BroadcastReceiver? = null
    private var receiverRegistered = false
    private val lineBuffer = StringBuilder()

    private val _telemetryFlow = MutableSharedFlow<TelemetryPacket>(replay = 1)
    val telemetryFlow: SharedFlow<TelemetryPacket> = _telemetryFlow.asSharedFlow()

    private val _rawFrameFlow = MutableSharedFlow<String>(replay = 1)
    val rawFrameFlow: SharedFlow<String> = _rawFrameFlow.asSharedFlow()

    private val _rawHexFlow = MutableSharedFlow<String>(replay = 1)
    val rawHexFlow: SharedFlow<String> = _rawHexFlow.asSharedFlow()

    private val _statusFlow = MutableStateFlow(CanableStatus.DISCONNECTED)
    val statusFlow: StateFlow<CanableStatus> = _statusFlow.asStateFlow()

    /**
     * Finds a supported USB serial device, requests permission if needed, and
     * opens the CANable read loop once permission is available.
     * Can also connect to a TCP socket simulator over USB (ADB reverse).
     */
    fun start(useSimulator: Boolean = false, host: String = "127.0.0.1", portNum: Int = 8080) {
        if (useSimulator) {
            startSimulation(host, portNum)
            return
        }

        val driver = findCanableDriver()
        if (driver == null) {
            _statusFlow.value = if (usbManager.deviceList.isEmpty()) {
                CanableStatus.NO_DEVICE
            } else {
                CanableStatus.UNSUPPORTED_DEVICE
            }
            _rawHexFlow.tryEmit(usbDeviceSummary())
            return
        }

        val device = driver.device
        if (!usbManager.hasPermission(device)) {
            registerPermissionReceiver()
            requestPermission(device)
            _statusFlow.value = CanableStatus.WAITING_FOR_PERMISSION
            return
        }

        open(driver)
    }

    /**
     * Connects to a TCP socket simulator (typically forwarded via adb reverse).
     * Reads SLCAN lines and feeds them into the exact same processing pipeline.
     */
    private fun startSimulation(host: String, port: Int) {
        if (simulatorJob?.isActive == true) return
        stop() // Ensure clean state before connecting

        _statusFlow.value = CanableStatus.SIMULATING

        simulatorJob = scope.launch(Dispatchers.IO) {
            var currentSocket: java.net.Socket? = null
            try {
                Log.d(TAG, "Connecting to CAN simulator at $host:$port")
                currentSocket = java.net.Socket(host, port)
                socket = currentSocket
                val inputStream = currentSocket.getInputStream()
                val buffer = ByteArray(READ_BUFFER_BYTES)

                while (isActive) {
                    val length = inputStream.read(buffer)
                    if (length == -1) {
                        Log.d(TAG, "Simulator TCP connection closed by peer")
                        break
                    }
                    if (length > 0) {
                        _rawHexFlow.tryEmit(buffer.toHexString(length))
                        appendSerialChunk(String(buffer, 0, length, StandardCharsets.US_ASCII))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Simulator connection error", e)
                _statusFlow.value = CanableStatus.ERROR
            } finally {
                try {
                    currentSocket?.close()
                } catch (ignored: Exception) {}
                if (socket == currentSocket) {
                    socket = null
                }
                if (_statusFlow.value == CanableStatus.SIMULATING) {
                    _statusFlow.value = CanableStatus.DISCONNECTED
                }
            }
        }
    }

    /**
     * Stops the active read loop, clears partial serial input, closes the USB
     * port, unregisters permission receivers, and marks the reader disconnected.
     */
    fun stop() {
        readJob?.cancel()
        readJob = null
        simulatorJob?.cancel()
        simulatorJob = null
        try {
            socket?.close()
        } catch (ignored: Exception) {}
        socket = null
        lineBuffer.clear()
        closePort()
        unregisterPermissionReceiver()
        _statusFlow.value = CanableStatus.DISCONNECTED
    }

    /**
     * Fully tears down the reader. Call this when the owning screen or activity
     * is being disposed so the internal coroutine scope is cancelled.
     */
    fun close() {
        stop()
        scope.cancel()
    }

    // ---------- USB discovery & permission ----------

    /**
     * Returns the first serial driver reported by usb-serial-for-android, with
     * a CDC ACM fallback for CANable devices the default prober does not match.
     */
    private fun findCanableDriver(): UsbSerialDriver? {
        val defaultProber = UsbSerialProber.getDefaultProber()
        val drivers = defaultProber.findAllDrivers(usbManager)
        if (drivers.isNotEmpty()) return drivers.first()

        return usbManager.deviceList.values
            .firstOrNull(::looksLikeCdcDevice)
            ?.let(::CdcAcmSerialDriver)
    }

    /**
     * Checks whether an Android USB device exposes CDC communication or data
     * interfaces, which is enough to try the generic CDC ACM serial driver.
     */
    private fun looksLikeCdcDevice(device: UsbDevice): Boolean {
        for (index in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(index)
            if (
                usbInterface.interfaceClass == UsbConstants.USB_CLASS_COMM ||
                usbInterface.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA
            ) {
                return true
            }
        }
        return false
    }

    /**
     * Builds a short diagnostic string describing visible Android USB devices
     * when no supported serial driver can be opened.
     */
    private fun usbDeviceSummary(): String {
        val devices = usbManager.deviceList.values
        if (devices.isEmpty()) return "No Android USB devices visible"

        return devices.joinToString(separator = "\n") { device ->
            "USB device vid=0x%04X pid=0x%04X class=%d interfaces=%d".format(
                device.vendorId,
                device.productId,
                device.deviceClass,
                device.interfaceCount
            )
        }
    }

    /**
     * Starts Android's USB permission prompt for the selected CANable device.
     */
    private fun requestPermission(device: UsbDevice) {
        val flags = PendingIntent.FLAG_IMMUTABLE
        val permissionIntent = PendingIntent.getBroadcast(
            appContext,
            0,
            Intent(ACTION_USB_PERMISSION).setPackage(appContext.packageName),
            flags
        )
        usbManager.requestPermission(device, permissionIntent)
    }

    /**
     * Registers a one-shot style broadcast receiver that resumes opening the
     * CANable serial port after Android grants USB permission.
     */
    private fun registerPermissionReceiver() {
        if (receiverRegistered) return

        permissionReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != ACTION_USB_PERMISSION) return

                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }

                if (granted && device != null) {
                    findCanableDriver()?.takeIf { it.device == device }?.let(::open)
                } else {
                    _statusFlow.value = CanableStatus.PERMISSION_DENIED
                }
            }
        }

        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            appContext.registerReceiver(permissionReceiver, filter)
        }
        receiverRegistered = true
    }

    /**
     * Unregisters the USB permission receiver if it is currently active.
     */
    private fun unregisterPermissionReceiver() {
        if (!receiverRegistered) return
        permissionReceiver?.let(appContext::unregisterReceiver)
        permissionReceiver = null
        receiverRegistered = false
    }

    // ---------- Serial connection ----------

    /**
     * Opens and configures the USB serial port, initializes SLCAN mode, and
     * starts the background read loop.
     */
    private fun open(driver: UsbSerialDriver) {
        if (readJob?.isActive == true) return

        try {
            val connection = usbManager.openDevice(driver.device)
            if (connection == null) {
                _statusFlow.value = CanableStatus.PERMISSION_DENIED
                return
            }

            val openedPort = driver.ports.first()
            openedPort.open(connection)
            openedPort.setParameters(
                baudRate,
                DATA_BITS,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )
            openedPort.setDTR(true)
            openedPort.setRTS(true)
            initializeSlcanChannel(openedPort)
            port = openedPort
            _statusFlow.value = CanableStatus.CONNECTED
            startReading(openedPort)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to open CANable serial port", e)
            closePort()
            _statusFlow.value = CanableStatus.ERROR
        }
    }

    /**
     * Reads raw bytes from the serial port, emits hex diagnostics, and feeds
     * ASCII SLCAN text chunks into the line assembler.
     */
    private fun startReading(openedPort: UsbSerialPort) {
        readJob = scope.launch {
            val buffer = ByteArray(READ_BUFFER_BYTES)

            while (isActive) {
                try {
                    val length = openedPort.read(buffer, READ_TIMEOUT_MS)
                    if (length > 0) {
                        _rawHexFlow.tryEmit(buffer.toHexString(length))
                        appendSerialChunk(String(buffer, 0, length, StandardCharsets.US_ASCII))
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "CANable read failed", e)
                    _statusFlow.value = CanableStatus.ERROR
                    break
                }
            }
        }
    }

    /**
     * Sends the SLCAN commands needed to close any previous channel state, set
     * the bitrate, and open the CAN channel for streaming.
     */
    private fun initializeSlcanChannel(openedPort: UsbSerialPort) {
        writeSlcanCommand(openedPort, "C")
        writeSlcanCommand(openedPort, "S8")
        writeSlcanCommand(openedPort, "O")
    }

    /**
     * Writes a single ASCII SLCAN command terminated by carriage return.
     */
    private fun writeSlcanCommand(openedPort: UsbSerialPort, command: String) {
        openedPort.write("$command\r".toByteArray(StandardCharsets.US_ASCII), WRITE_TIMEOUT_MS)
    }

    // ---------- SLCAN line parsing ----------

    /**
     * Appends incoming serial text to a line buffer and dispatches complete
     * CR/LF-terminated SLCAN frames for decoding.
     */
    private fun appendSerialChunk(chunk: String) {
        for (char in chunk) {
            if (char == '\r' || char == '\n') {
                val line = lineBuffer.toString()
                lineBuffer.clear()
                decodeLine(line)
            } else {
                lineBuffer.append(char)
            }
        }
    }

    /**
     * Emits a raw SLCAN frame and, when it maps to a supported CAN ID, publishes
     * the decoded telemetry packet.
     */
    private fun decodeLine(line: String) {
        if (line.isBlank()) return
        _rawFrameFlow.tryEmit(line)
        val frame = decoder.parseSlcanFrame(line) ?: return
        val packet = decoder.decode(frame) ?: return
        _telemetryFlow.tryEmit(packet)
    }

    /**
     * Closes the currently open serial port and clears the reference, logging
     * close failures without throwing into UI lifecycle cleanup.
     */
    private fun closePort() {
        try {
            port?.close()
        } catch (e: IOException) {
            Log.w(TAG, "Error closing CANable serial port", e)
        } finally {
            port = null
        }
    }

    /**
     * Formats a prefix of a byte array as uppercase hexadecimal for diagnostics.
     */
    private fun ByteArray.toHexString(length: Int): String {
        return take(length).joinToString(" ") { byte -> "%02X".format(byte) }
    }

    companion object {
        private const val TAG = "CanableCarDataReader"
        private const val ACTION_USB_PERMISSION = "com.example.chatbot.USB_PERMISSION"
        private const val DEFAULT_BAUD_RATE = 115200
        private const val DATA_BITS = 8
        private const val READ_BUFFER_BYTES = 256
        private const val READ_TIMEOUT_MS = 1000
        private const val WRITE_TIMEOUT_MS = 1000
    }
}
