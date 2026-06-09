package com.goose.android

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.goose.android.ble.*
import com.goose.android.data.*
import com.goose.android.store.*
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Main app ViewModel — wires BLE manager, Rust bridge, data store, and health data.
 * Mirrors GooseAppModel.swift:
 *   - BLE connection lifecycle → capture session lifecycle
 *   - Raw BLE packets → parse_frame_hex → live HR from K10 data packets
 *   - Accumulated packets → import_frame_batch → run algorithm scores
 */
@OptIn(FlowPreview::class)
class GooseViewModel(application: Application) : AndroidViewModel(application) {

    private val bleManager = GooseBLEManager(application)
    private val store = GooseDataStore(application)

    // ── BLE state passthrough ──────────────────────────────────────────────────
    val connectionState: StateFlow<ConnectionState> = bleManager.connectionState
    val isScanning: StateFlow<Boolean> = bleManager.isScanning
    val discoveredDevices: StateFlow<List<GooseDiscoveredDevice>> = bleManager.discoveredDevices
    val activeDevice: StateFlow<GooseDiscoveredDevice?> = bleManager.activeDevice
    val liveHeartRate: StateFlow<Int?> = bleManager.liveHeartRate
    val batteryLevel: StateFlow<Int?> = bleManager.batteryLevel
    val firmwareVersion: StateFlow<String?> = bleManager.firmwareVersion
    val logMessages: StateFlow<List<GooseLogMessage>> = bleManager.logMessages
    val historicalSyncState: StateFlow<HistoricalSyncState> = bleManager.historicalSyncState

    // ── Health data ────────────────────────────────────────────────────────────
    private val _health = MutableStateFlow(HealthSummary())
    val health: StateFlow<HealthSummary> = _health.asStateFlow()

    // ── Rust status ────────────────────────────────────────────────────────────
    private val _rustStatus = MutableStateFlow("not loaded")
    val rustStatus: StateFlow<String> = _rustStatus.asStateFlow()

    // ── Accumulated frame buffer (flushed every 30s or on disconnect) ─────────
    private val frameBuffer = mutableListOf<CapturedFrame>()
    private val frameBufferLock = Any()

    init {
        viewModelScope.launch {
            // Initialize Rust store in background
            val ready = store.initialize()
            _rustStatus.value = if (ready) "ready" else "stub"

            // Watch connection state → manage capture sessions
            connectionState.collect { state ->
                when (state) {
                    is ConnectionState.Connected -> {
                        val deviceName = state.deviceName
                        store.startCaptureSession(deviceName)
                    }
                    is ConnectionState.Disconnected -> {
                        flushFrameBuffer()
                        store.finishCaptureSession()
                        refreshHealthScores()
                    }
                    else -> {}
                }
            }
        }

        // Live HR from BLE standard HR service (2A37) — no Rust needed
        viewModelScope.launch {
            bleManager.liveHeartRate.collect { hr ->
                if (hr != null) {
                    _health.value = _health.value.copy(
                        vitals = _health.value.vitals.copy(heartRate = hr)
                    )
                }
            }
        }

        // Process raw BLE notification events
        viewModelScope.launch {
            bleManager.notificationFlow.filterNotNull().collect { event ->
                processNotificationEvent(event)
            }
        }

        // Periodic frame flush every 30 seconds (mirrors iOS periodic sync)
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                if (connectionState.value is ConnectionState.Connected) {
                    flushFrameBuffer()
                    refreshHealthScores()
                }
            }
        }
    }

    // ── BLE actions ───────────────────────────────────────────────────────────

    fun startScan() = bleManager.startScan()
    fun stopScan() = bleManager.stopScan()
    fun connect(device: GooseDiscoveredDevice) = bleManager.connect(device)

    fun disconnect() {
        viewModelScope.launch {
            flushFrameBuffer()
            store.finishCaptureSession()
        }
        bleManager.disconnect()
    }

    fun startPhysiology() = bleManager.startPhysiologyCapture()
    fun stopPhysiology() = bleManager.stopPhysiologyCapture()

    fun requestHistoricalSync() {
        viewModelScope.launch {
            bleManager.requestHistoricalDataRange()
            delay(500)
            bleManager.requestHistoricalSync()
        }
    }

    // ── Notification processing ────────────────────────────────────────────────

    private fun processNotificationEvent(event: BLENotificationEvent) {
        // Buffer all raw frames for batch import
        val hex = event.data.joinToString("") { "%02x".format(it) }
        val frame = CapturedFrame.fromNotification(
            hexData = hex,
            characteristicUuid = event.serviceCharacteristicUuid
        )
        synchronized(frameBufferLock) {
            frameBuffer.add(frame)
        }

        // Parse live HR from WHOOP K10 data packets via Rust protocol parser
        if (com.goose.android.rust.GooseRustBridge.isNativeAvailable()) {
            viewModelScope.launch {
                val parsed = store.parseFrame(hex, deviceTypeFromState())
                parsed?.heartRate?.let { hr ->
                    if (hr > 0) {
                        _health.value = _health.value.copy(
                            vitals = _health.value.vitals.copy(heartRate = hr)
                        )
                        bleManager.updateLiveHR(hr)
                    }
                }
            }
        }
    }

    // ── Frame buffer flush ─────────────────────────────────────────────────────

    private suspend fun flushFrameBuffer() {
        val toFlush: List<CapturedFrame>
        synchronized(frameBufferLock) {
            if (frameBuffer.isEmpty()) return
            toFlush = frameBuffer.toList()
            frameBuffer.clear()
        }
        val deviceModel = bleManager.activeDevice.value?.name ?: "WHOOP"
        val result = store.importFrameBatch(
            frames = toFlush,
            deviceModel = deviceModel,
            deviceType = deviceTypeFromState()
        )
        android.util.Log.i("GooseVM", "Flushed ${toFlush.size} frames → ${result.imported} imported, ${result.skipped} existing")
    }

    private fun deviceTypeFromState(): String = bleManager.activeServiceFamily.lowercase()

    // ── Health score refresh ───────────────────────────────────────────────────

    /**
     * Queries all health algorithm scores from Rust after frame import.
     * Mirrors iOS HealthDataStore.refresh() which triggers all metric runners.
     */
    private fun refreshHealthScores() {
        viewModelScope.launch {
            val recovery = store.queryRecoveryScore()
            val sleep = store.querySleepScore()
            val strain = store.queryStrainScore()

            var updated = _health.value

            recovery?.let {
                updated = updated.copy(
                    recovery = updated.recovery.copy(
                        scorePercent = it.score,
                        hrvRmssd = it.hrvRmssd,
                        restingHeartRate = it.restingHr
                    )
                )
            }

            sleep?.let {
                updated = updated.copy(
                    sleep = updated.sleep.copy(
                        scorePercent = it.score,
                        totalMinutes = it.totalMinutes,
                        remMinutes = it.remMinutes,
                        deepMinutes = it.deepMinutes,
                        lightMinutes = it.lightMinutes
                    )
                )
            }

            strain?.let {
                updated = updated.copy(
                    strain = updated.strain.copy(
                        dayStrain = it.dayStrain,
                        calories = it.calories,
                        avgHeartRate = it.avgHr
                    )
                )
            }

            if (updated != _health.value) {
                _health.value = updated
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            flushFrameBuffer()
            store.finishCaptureSession()
        }
        bleManager.disconnect()
    }
}
