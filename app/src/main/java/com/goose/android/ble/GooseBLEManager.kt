package com.goose.android.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android port of GooseBLEClient.swift.
 *
 * Scans for WHOOP 5.0 using the fd4b0001 and 61080001 service UUIDs,
 * connects, discovers services, subscribes to notification characteristics,
 * and emits raw BLE packets via [notificationFlow].
 */
@SuppressLint("MissingPermission")
class GooseBLEManager(private val context: Context) {

    // ────────── State ──────────
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<GooseDiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<GooseDiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _activeDevice = MutableStateFlow<GooseDiscoveredDevice?>(null)
    val activeDevice: StateFlow<GooseDiscoveredDevice?> = _activeDevice.asStateFlow()

    private val _liveHeartRate = MutableStateFlow<Int?>(null)
    val liveHeartRate: StateFlow<Int?> = _liveHeartRate.asStateFlow()

    private val _batteryLevel = MutableStateFlow<Int?>(null)
    val batteryLevel: StateFlow<Int?> = _batteryLevel.asStateFlow()

    private val _firmwareVersion = MutableStateFlow<String?>(null)
    val firmwareVersion: StateFlow<String?> = _firmwareVersion.asStateFlow()

    private val _deviceInfo = MutableStateFlow(DeviceInfo())
    val deviceInfo: StateFlow<DeviceInfo> = _deviceInfo.asStateFlow()

    private val _notificationEvents = MutableStateFlow<BLENotificationEvent?>(null)
    val notificationFlow: StateFlow<BLENotificationEvent?> = _notificationEvents.asStateFlow()

    private val _historicalSyncState = MutableStateFlow(HistoricalSyncState())
    val historicalSyncState: StateFlow<HistoricalSyncState> = _historicalSyncState.asStateFlow()

    private val _logMessages = MutableStateFlow<List<GooseLogMessage>>(emptyList())
    val logMessages: StateFlow<List<GooseLogMessage>> = _logMessages.asStateFlow()

    // ────────── Internal ──────────
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter
    private var scanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null
    private var activeDeviceAddress: String? = null
    private var commandSequence: UInt = 57u
    private var discoveredMap = mutableMapOf<String, GooseDiscoveredDevice>()
    var activeServiceFamily: String = "GOOSE" // "GOOSE" = fd4b, "GEN4" = 61080

    // ────────── Scan ──────────

    fun startScan() {
        if (_isScanning.value) return
        val adapter = bluetoothAdapter ?: run {
            log("BT adapter unavailable", level = LogLevel.Error)
            return
        }
        scanner = adapter.bluetoothLeScanner ?: run {
            log("BT LE scanner unavailable", level = LogLevel.Error)
            return
        }
        discoveredMap.clear()
        _discoveredDevices.value = emptyList()

        val filters = WhoopUUIDs.ALL_WHOOP_SERVICES.map { uuid ->
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(uuid))
                .build()
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner?.startScan(filters, settings, scanCallback)
        _isScanning.value = true
        log("BLE scan started for WHOOP 5.0")
    }

    fun stopScan() {
        if (!_isScanning.value) return
        scanner?.stopScan(scanCallback)
        _isScanning.value = false
        log("BLE scan stopped")
    }

    // ────────── Connect ──────────

    fun connect(device: GooseDiscoveredDevice) {
        stopScan()
        _connectionState.value = ConnectionState.Connecting(device.name)
        val btDevice = bluetoothAdapter?.getRemoteDevice(device.address) ?: return
        activeDeviceAddress = device.address
        _activeDevice.value = device

        gatt = btDevice.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        log("Connecting to ${device.name} (${device.address})")
    }

    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        commandCharacteristic = null
        activeDeviceAddress = null
        _connectionState.value = ConnectionState.Disconnected
        _activeDevice.value = null
        activeServiceFamily = "GOOSE"
        log("Disconnected")
    }

    /** Allows ViewModel to push HR values extracted by Rust protocol parser (K10 data packets) */
    fun updateLiveHR(bpm: Int) {
        if (bpm > 0) _liveHeartRate.value = bpm
    }

    // ────────── Client Hello ──────────

    /**
     * Sends the WHOOP 5.0 Client Hello handshake frame.
     * From GooseHello.swift: clientHelloFrameHex = "aa0108000001e67123019101363e5c8d"
     * Must be sent after all notifications are enabled, before any other commands.
     */
    fun sendClientHello() {
        val ch = commandCharacteristic ?: run {
            log("Client Hello blocked — no command characteristic", level = LogLevel.Warn)
            return
        }
        ch.value = CLIENT_HELLO_FRAME.clone()
        val written = gatt?.writeCharacteristic(ch) ?: false
        log("Client Hello sent (${CLIENT_HELLO_FRAME.size}B) written=$written")
    }

    // ────────── Commands ──────────

    /** Writes a command frame to WHOOP following the Goose framing protocol */
    fun sendCommand(commandNumber: Byte, payload: ByteArray = ByteArray(0)) {
        val ch = commandCharacteristic ?: run {
            log("No command characteristic available", level = LogLevel.Error)
            return
        }
        val seq = commandSequence++.toUByte()
        val frame = buildCommandFrame(commandNumber, seq.toByte(), payload)
        ch.value = frame
        val written = gatt?.writeCharacteristic(ch) ?: false
        log("CMD ${commandNumber.toUByte()} seq=${seq} payload=${payload.size}B written=$written")
    }

    fun startPhysiologyCapture() {
        WhoopCommands.startPhysiologyCaptureCommands().forEach { (cmd, payload) ->
            sendCommand(cmd, payload)
            Thread.sleep(50) // small inter-command gap
        }
    }

    fun stopPhysiologyCapture() {
        WhoopCommands.stopPhysiologyCaptureCommands().forEach { (cmd, payload) ->
            sendCommand(cmd, payload)
            Thread.sleep(50)
        }
    }

    fun requestHistoricalDataRange() {
        sendCommand(WhoopCommands.GET_DATA_RANGE)
    }

    fun requestHistoricalSync() {
        sendCommand(WhoopCommands.SEND_HISTORICAL_DATA)
    }

    fun ackHistoricalDataResult() {
        sendCommand(WhoopCommands.HISTORICAL_DATA_RESULT, WhoopCommands.HISTORICAL_DATA_RESULT_DEFAULT_PAYLOAD)
    }

    // ────────── Frame builder ──────────

    /**
     * Builds a WHOOP BLE command frame matching the format observed by Goose.
     * Frame layout: [typeTag, commandNumber, sequence, payload...]
     */
    private fun buildCommandFrame(commandNumber: Byte, sequence: Byte, payload: ByteArray): ByteArray {
        return byteArrayOf(WhoopUUIDs.PACKET_COMMAND, commandNumber, sequence) + payload
    }

    // ────────── Scan Callback ──────────

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val name = device.name ?: "WHOOP"
            if (!isLikelyWhoop(name, result)) return

            val discovered = GooseDiscoveredDevice(
                address = device.address,
                name = name,
                rssi = result.rssi
            )
            discoveredMap[device.address] = discovered
            _discoveredDevices.value = discoveredMap.values.sortedByDescending { it.rssi }
            log("Discovered: $name (${device.address}) RSSI=${result.rssi}")
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
            log("Scan failed: $errorCode", level = LogLevel.Error)
        }
    }

    private fun isLikelyWhoop(name: String, result: ScanResult): Boolean {
        val lowerName = name.lowercase()
        if (lowerName.contains("whoop")) return true
        // Also accept any device advertising WHOOP service UUIDs
        val uuids = result.scanRecord?.serviceUuids?.map { it.uuid } ?: return false
        return uuids.any { it in WhoopUUIDs.ALL_WHOOP_SERVICES }
    }

    // ────────── GATT Callback ──────────

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    val name = _activeDevice.value?.name ?: "WHOOP"
                    _connectionState.value = ConnectionState.Discovering(name)
                    log("Connected — discovering services")
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = ConnectionState.Disconnected
                    commandCharacteristic = null
                    log("Disconnected (status=$status)")
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                log("Service discovery failed: $status", level = LogLevel.Error)
                return
            }
            log("Services discovered — subscribing to notifications")
            setupCharacteristics(gatt)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val data = characteristic.value ?: return
            handleNotification(characteristic.uuid, data)
        }

        // API 33+ callback
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleNotification(characteristic.uuid, value)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            handleCharacteristicRead(characteristic.uuid, value)
        }

        // API 33+ (Android 13+) override — required so battery/firmware populate on modern devices
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            handleCharacteristicRead(characteristic.uuid, value)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            // Proceed to next notification subscription
            val nextPending = pendingNotifySetup.removeFirstOrNull()
            if (nextPending != null) {
                enableNotification(gatt, nextPending)
            } else {
                // All notifications enabled — device is ready
                _connectionState.value = ConnectionState.Connected(
                    _activeDevice.value?.name ?: "WHOOP"
                )
                log("Device ready — all notifications active")
                // Send Client Hello handshake (required by WHOOP 5.0 before any data flows)
                // Frame from GooseHello.swift: aa0108000001e67123019101363e5c8d
                sendClientHello()
                // Read battery and device info
                readCharacteristic(gatt, WhoopUUIDs.BATTERY_LEVEL)
                readCharacteristic(gatt, WhoopUUIDs.FIRMWARE_REVISION)
                readCharacteristic(gatt, WhoopUUIDs.MODEL_NUMBER)
            }
        }
    }

    private val pendingNotifySetup = ArrayDeque<BluetoothGattCharacteristic>()

    private fun setupCharacteristics(gatt: BluetoothGatt) {
        val allServices = gatt.services ?: return

        // Find command characteristic
        for (service in allServices) {
            val cmdChar = service.getCharacteristic(WhoopUUIDs.COMMAND_PRIMARY)
                ?: service.getCharacteristic(WhoopUUIDs.COMMAND_ALTERNATE)
            if (cmdChar != null) {
                commandCharacteristic = cmdChar
                this.gatt = gatt
                // Determine service family for device type reporting
                activeServiceFamily = if (service.uuid == WhoopUUIDs.SERVICE_PRIMARY) "GOOSE" else "GEN4"
                log("Command characteristic found: ${cmdChar.uuid} family=$activeServiceFamily")
                break
            }
        }

        // Collect all notify characteristics
        pendingNotifySetup.clear()
        for (service in allServices) {
            for (notifyUuid in WhoopUUIDs.ALL_NOTIFICATION_CHARACTERISTICS) {
                val char = service.getCharacteristic(notifyUuid)
                if (char != null && (char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0)) {
                    pendingNotifySetup.add(char)
                }
            }
            // Heart rate
            val hrChar = service.getCharacteristic(WhoopUUIDs.HEART_RATE_MEASUREMENT)
            if (hrChar != null) pendingNotifySetup.add(hrChar)
        }

        // Start enabling notifications one by one (CCCD writes must be sequential)
        val first = pendingNotifySetup.removeFirstOrNull()
        if (first != null) {
            enableNotification(gatt, first)
        }
    }

    private fun enableNotification(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(WhoopUUIDs.CLIENT_CHARACTERISTIC_CONFIG) ?: run {
            // No CCCD — just move on
            val next = pendingNotifySetup.removeFirstOrNull()
            if (next != null) enableNotification(gatt, next)
            return
        }
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        gatt.writeDescriptor(descriptor)
    }

    private fun readCharacteristic(gatt: BluetoothGatt, uuid: java.util.UUID) {
        for (service in gatt.services ?: return) {
            val char = service.getCharacteristic(uuid)
            if (char != null) {
                gatt.readCharacteristic(char)
                return
            }
        }
    }

    // ────────── Notification / Packet handling ──────────

    private fun handleNotification(uuid: java.util.UUID, data: ByteArray) {
        // Heart rate special-case (standard GATT 2A37)
        if (uuid == WhoopUUIDs.HEART_RATE_MEASUREMENT) {
            val hr = parseHeartRate(data)
            if (hr != null) {
                _liveHeartRate.value = hr
            }
            return
        }

        // Emit raw BLE event for Rust core processing
        val event = BLENotificationEvent(
            serviceCharacteristicUuid = uuid.toString(),
            data = data,
            capturedAt = System.currentTimeMillis()
        )
        _notificationEvents.value = event

        // Parse packet type tag
        if (data.isNotEmpty()) {
            parsePacketType(data, uuid)
        }
    }

    private fun handleCharacteristicRead(uuid: java.util.UUID, value: ByteArray) {
        val str = value.toString(Charsets.UTF_8).trim()
        when (uuid) {
            WhoopUUIDs.FIRMWARE_REVISION -> {
                _firmwareVersion.value = str
                _deviceInfo.value = _deviceInfo.value.copy(firmwareVersion = str)
                log("Firmware: $str")
            }
            WhoopUUIDs.MODEL_NUMBER -> {
                _deviceInfo.value = _deviceInfo.value.copy(modelNumber = str)
                log("Model: $str")
            }
            WhoopUUIDs.MANUFACTURER_NAME -> {
                _deviceInfo.value = _deviceInfo.value.copy(manufacturerName = str)
            }
            WhoopUUIDs.BATTERY_LEVEL -> {
                val level = value.firstOrNull()?.toInt()?.and(0xFF)
                _batteryLevel.value = level
                log("Battery: $level%")
            }
        }
    }

    private fun parsePacketType(data: ByteArray, uuid: java.util.UUID) {
        if (data.size < 2) return
        val packetType = data[0]
        when (packetType) {
            WhoopUUIDs.PACKET_COMMAND_RESPONSE -> {
                log("Command response: cmd=${data.getOrNull(1)?.toUByte()} seq=${data.getOrNull(2)?.toUByte()}")
            }
            WhoopUUIDs.PACKET_HISTORICAL_DATA -> {
                val currentCount = (_historicalSyncState.value.packetsReceived + 1)
                _historicalSyncState.value = _historicalSyncState.value.copy(packetsReceived = currentCount)
            }
            WhoopUUIDs.PACKET_METADATA -> {
                val kind = data.getOrNull(1)
                log("Metadata packet kind=$kind")
            }
            WhoopUUIDs.PACKET_EVENT -> {
                log("Event packet len=${data.size}")
            }
        }
    }

    private fun parseHeartRate(data: ByteArray): Int? {
        if (data.isEmpty()) return null
        val flags = data[0].toInt()
        return if (flags and 0x01 == 0) {
            // 8-bit HR value
            data.getOrNull(1)?.toInt()?.and(0xFF)
        } else {
            // 16-bit HR value
            val lo = data.getOrNull(1)?.toInt()?.and(0xFF) ?: return null
            val hi = data.getOrNull(2)?.toInt()?.and(0xFF) ?: return null
            lo or (hi shl 8)
        }
    }

    // ────────── Logging ──────────

    private fun log(message: String, level: LogLevel = LogLevel.Info) {
        val msg = GooseLogMessage(
            timestamp = System.currentTimeMillis(),
            level = level,
            source = "BLE",
            body = message
        )
        val current = _logMessages.value.takeLast(299)
        _logMessages.value = current + msg
        Log.println(level.androidPriority, TAG, message)
    }

    companion object {
        private const val TAG = "GooseBLE"

        /** Client Hello frame from GooseHello.swift — required by WHOOP 5.0 before data flows */
        val CLIENT_HELLO_FRAME = byteArrayOf(
            0xAA.toByte(), 0x01, 0x08, 0x00, 0x00, 0x01,
            0xE6.toByte(), 0x71.toByte(), 0x23, 0x01,
            0x91.toByte(), 0x01, 0x36, 0x3E, 0x5C.toByte(), 0x8D.toByte()
        )
    }
}

// ────────── Data classes ──────────

data class GooseDiscoveredDevice(
    val address: String,
    val name: String,
    val rssi: Int
)

data class BLENotificationEvent(
    val serviceCharacteristicUuid: String,
    val data: ByteArray,
    val capturedAt: Long
) {
    val deviceType: String get() = if (serviceCharacteristicUuid.lowercase().startsWith("610800")) "GEN4" else "GOOSE"
}

data class DeviceInfo(
    val firmwareVersion: String? = null,
    val modelNumber: String? = null,
    val hardwareRevision: String? = null,
    val softwareRevision: String? = null,
    val manufacturerName: String? = null
)

data class HistoricalSyncState(
    val status: String = "idle",
    val detail: String = "",
    val packetsReceived: Int = 0,
    val isActive: Boolean = false,
    val isFailed: Boolean = false
)

data class GooseLogMessage(
    val timestamp: Long,
    val level: LogLevel,
    val source: String,
    val body: String
)

enum class LogLevel(val androidPriority: Int) {
    Debug(android.util.Log.DEBUG),
    Info(android.util.Log.INFO),
    Warn(android.util.Log.WARN),
    Error(android.util.Log.ERROR)
}

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    data class Connecting(val deviceName: String) : ConnectionState()
    data class Discovering(val deviceName: String) : ConnectionState()
    data class Connected(val deviceName: String) : ConnectionState()

    val label: String get() = when (this) {
        is Disconnected -> "Disconnected"
        is Connecting -> "Connecting to $deviceName…"
        is Discovering -> "Setting up $deviceName…"
        is Connected -> "Connected — $deviceName"
    }

    val isConnected: Boolean get() = this is Connected
}
