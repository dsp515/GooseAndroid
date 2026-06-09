package com.goose.android.ble

import java.util.UUID

/**
 * WHOOP 5.0 BLE service and characteristic UUIDs.
 * Extracted from GooseBLEClient.swift.
 *
 * WHOOP 5.0 uses two service families:
 *   fd4b0001-* family (primary)
 *   61080001-* family (alternate)
 */
object WhoopUUIDs {

    // Primary WHOOP service UUIDs
    val SERVICE_PRIMARY: UUID = UUID.fromString("fd4b0001-cce1-4033-93ce-002d5875f58a")
    val SERVICE_ALTERNATE: UUID = UUID.fromString("61080001-8d6d-82b8-614a-1c8cb0f8dcc6")

    // Command write characteristics (one per service family)
    val COMMAND_PRIMARY: UUID = UUID.fromString("fd4b0002-cce1-4033-93ce-002d5875f58a")
    val COMMAND_ALTERNATE: UUID = UUID.fromString("61080002-8d6d-82b8-614a-1c8cb0f8dcc6")

    // Notification characteristics (subscribe for incoming packets)
    val NOTIFY_PRIMARY_1: UUID = UUID.fromString("fd4b0003-cce1-4033-93ce-002d5875f58a")
    val NOTIFY_PRIMARY_2: UUID = UUID.fromString("fd4b0004-cce1-4033-93ce-002d5875f58a")
    val NOTIFY_PRIMARY_3: UUID = UUID.fromString("fd4b0005-cce1-4033-93ce-002d5875f58a")
    val NOTIFY_DEBUG_PRIMARY: UUID = UUID.fromString("fd4b0007-cce1-4033-93ce-002d5875f58a")
    val NOTIFY_ALTERNATE_1: UUID = UUID.fromString("61080003-8d6d-82b8-614a-1c8cb0f8dcc6")
    val NOTIFY_ALTERNATE_2: UUID = UUID.fromString("61080004-8d6d-82b8-614a-1c8cb0f8dcc6")
    val NOTIFY_ALTERNATE_3: UUID = UUID.fromString("61080005-8d6d-82b8-614a-1c8cb0f8dcc6")
    val NOTIFY_DEBUG_ALTERNATE: UUID = UUID.fromString("61080007-8d6d-82b8-614a-1c8cb0f8dcc6")

    // Standard GATT services
    val HEART_RATE_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    val HEART_RATE_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    val BATTERY_SERVICE: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    val BATTERY_LEVEL: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
    val BATTERY_LEVEL_STATUS: UUID = UUID.fromString("00002bed-0000-1000-8000-00805f9b34fb")
    val DEVICE_INFORMATION: UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
    val MODEL_NUMBER: UUID = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb")
    val FIRMWARE_REVISION: UUID = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")
    val HARDWARE_REVISION: UUID = UUID.fromString("00002a27-0000-1000-8000-00805f9b34fb")
    val SOFTWARE_REVISION: UUID = UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb")
    val MANUFACTURER_NAME: UUID = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb")

    // CCCD descriptor for enabling notifications
    val CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    val ALL_WHOOP_SERVICES = listOf(SERVICE_PRIMARY, SERVICE_ALTERNATE)
    val ALL_NOTIFICATION_CHARACTERISTICS = listOf(
        NOTIFY_PRIMARY_1, NOTIFY_PRIMARY_2, NOTIFY_PRIMARY_3, NOTIFY_DEBUG_PRIMARY,
        NOTIFY_ALTERNATE_1, NOTIFY_ALTERNATE_2, NOTIFY_ALTERNATE_3, NOTIFY_DEBUG_ALTERNATE
    )
    val ALL_COMMAND_CHARACTERISTICS = listOf(COMMAND_PRIMARY, COMMAND_ALTERNATE)

    // V5 packet type bytes (from GooseBLEClient.swift V5PacketType)
    const val PACKET_COMMAND: Byte = 35
    const val PACKET_COMMAND_RESPONSE: Byte = 36
    const val PACKET_PUFFIN_COMMAND_RESPONSE: Byte = 38
    const val PACKET_EVENT: Byte = 48
    const val PACKET_HISTORICAL_DATA: Byte = 47
    const val PACKET_METADATA: Byte = 49
    const val PACKET_HISTORICAL_IMU_DATA_STREAM: Byte = 52
    const val PACKET_PUFFIN_METADATA: Byte = 56
}

/**
 * WHOOP 5.0 command numbers extracted from GooseBLEClient.swift.
 */
object WhoopCommands {
    // Physiology / sensor stream (from SensorStreamCommandKind)
    const val TOGGLE_REALTIME_HR: Byte = 3
    const val SEND_R10_R11_REALTIME: Byte = 63
    const val TOGGLE_IMU_MODE: Byte = 106.toByte()
    const val TOGGLE_PERSISTENT_R21: Byte = 154.toByte()
    const val ENABLE_OPTICAL_DATA: Byte = 107.toByte()
    const val TOGGLE_OPTICAL_MODE: Byte = 108.toByte()
    const val TOGGLE_PERSISTENT_R20: Byte = 153.toByte()
    const val ENTER_HIGH_FREQ_SYNC: Byte = 96.toByte()
    const val EXIT_HIGH_FREQ_SYNC: Byte = 97.toByte()

    // Historical data (from HistoricalCommandKind)
    const val GET_DATA_RANGE: Byte = 34
    const val SEND_HISTORICAL_DATA: Byte = 22
    const val HISTORICAL_DATA_RESULT: Byte = 23

    // Clock (from ClockCommandKind)
    const val GET_CLOCK: Byte = 11
    const val SET_CLOCK: Byte = 10

    // Alarm (from AlarmCommandKind)
    const val SET_ALARM_TIME: Byte = 66
    const val GET_ALARM_TIME: Byte = 67
    const val RUN_ALARM: Byte = 68
    const val DISABLE_ALARM: Byte = 69

    // Debug / research
    const val BODY_LOCATION_AND_STATUS: Byte = 84.toByte()
    const val RESEARCH_PACKET: Byte = 132.toByte()
    const val GET_EXTENDED_BATTERY_INFO: Byte = 98.toByte()
    const val GET_BATTERY_PACK_INFO: Byte = 151.toByte()
    const val GET_LED_DRIVE: Byte = 40
    const val GET_TIA_GAIN: Byte = 42
    const val GET_BIAS_OFFSET: Byte = 44

    /** Default ACK payload for HISTORICAL_DATA_RESULT */
    val HISTORICAL_DATA_RESULT_DEFAULT_PAYLOAD = byteArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 0)

    /** Start physiology capture command sequence */
    fun startPhysiologyCaptureCommands(): List<Pair<Byte, ByteArray>> = listOf(
        TOGGLE_REALTIME_HR to byteArrayOf(1),
        SEND_R10_R11_REALTIME to byteArrayOf(1),
        TOGGLE_IMU_MODE to byteArrayOf(1, 1),
        TOGGLE_PERSISTENT_R21 to byteArrayOf(1, 1),
        ENABLE_OPTICAL_DATA to byteArrayOf(1, 1),
        TOGGLE_OPTICAL_MODE to byteArrayOf(1, 1),
        TOGGLE_PERSISTENT_R20 to byteArrayOf(1, 1),
    )

    /** Stop physiology capture command sequence */
    fun stopPhysiologyCaptureCommands(): List<Pair<Byte, ByteArray>> = listOf(
        TOGGLE_PERSISTENT_R20 to byteArrayOf(1, 0),
        TOGGLE_OPTICAL_MODE to byteArrayOf(1, 0),
        ENABLE_OPTICAL_DATA to byteArrayOf(1, 0),
        TOGGLE_PERSISTENT_R21 to byteArrayOf(1, 0),
        TOGGLE_IMU_MODE to byteArrayOf(1, 0),
        SEND_R10_R11_REALTIME to byteArrayOf(0),
        TOGGLE_REALTIME_HR to byteArrayOf(0),
    )
}
