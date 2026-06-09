package com.goose.android.store

import android.content.Context
import android.util.Log
import com.goose.android.rust.GooseRustBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Goose Data Store — Android port of the iOS HealthDataStore + historical sync pipeline.
 *
 * The Rust core uses a SQLite database as its backing store.
 * All bridge method names and arg shapes are verified against:
 *   - bridge.rs (CaptureStartSessionArgs, CaptureFinishSessionArgs, CaptureImportFrameBatchArgs,
 *                RecoveryFeatureScoreArgs, SleepFeatureScoreArgs, StrainFeatureScoreArgs)
 *   - capture_import.rs (CapturedFrameInput fields)
 *   - store.rs (CaptureSessionInput fields)
 */
class GooseDataStore(context: Context) {

    val databasePath: String = File(context.filesDir, "goose.sqlite").absolutePath

    private var captureSessionId: String? = null
    private var captureSessionFrameCount: Int = 0
    private var isInitialized = false

    // ── Init ──────────────────────────────────────────────────────────────────

    /**
     * Initializes the Rust SQLite store and applies default algorithm preferences.
     * Must be called before any other store operations.
     *
     * Bridge calls:
     *   storage.check    → { database_path, self_test }
     *   settings.apply_default_algorithm_preferences → { database_path, scope }
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext true
        if (!GooseRustBridge.isNativeAvailable()) {
            Log.w(TAG, "Rust native not available — store in stub mode")
            return@withContext false
        }
        try {
            GooseRustBridge.request(
                "storage.check",
                mapOf("database_path" to databasePath, "self_test" to false)
            )
            GooseRustBridge.request(
                "settings.apply_default_algorithm_preferences",
                mapOf("database_path" to databasePath, "scope" to "default")
            )
            isInitialized = true
            Log.i(TAG, "GooseDataStore initialized at $databasePath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "GooseDataStore initialization failed: $e")
            false
        }
    }

    // ── Capture Session ───────────────────────────────────────────────────────

    /**
     * Starts a new BLE capture session.
     *
     * Bridge: capture.start_session
     * Args (from CaptureStartSessionArgs in bridge.rs):
     *   database_path: String       (required)
     *   session_id: String          (required)
     *   source: String              (required)
     *   started_at_unix_ms: i64     (required — unix epoch ms, NOT ISO string)
     *   device_model: String        (required — the device name/model)
     *   active_device_id: Option<String> (optional)
     *   provenance: serde_json::Value    (optional, defaults to {})
     */
    suspend fun startCaptureSession(deviceName: String): String? = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext null
        try {
            val sessionId = "android-${UUID.randomUUID()}"
            val nowMs = System.currentTimeMillis()
            GooseRustBridge.request(
                "capture.start_session",
                mapOf(
                    "database_path" to databasePath,
                    "session_id" to sessionId,
                    "source" to "ble.android",
                    "started_at_unix_ms" to nowMs,
                    "device_model" to deviceName
                )
            )
            captureSessionId = sessionId
            captureSessionFrameCount = 0
            Log.i(TAG, "Capture session started: $sessionId device=$deviceName")
            sessionId
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start capture session: $e")
            null
        }
    }

    /**
     * Finishes the current BLE capture session.
     *
     * Bridge: capture.finish_session
     * Args (from CaptureFinishSessionArgs in bridge.rs):
     *   database_path: String     (required)
     *   session_id: String        (required)
     *   ended_at_unix_ms: i64     (required — unix epoch ms, NOT ISO string)
     *   frame_count: i64          (optional, defaults 0)
     */
    suspend fun finishCaptureSession(): Boolean = withContext(Dispatchers.IO) {
        val sid = captureSessionId ?: return@withContext false
        if (!isInitialized) return@withContext false
        try {
            GooseRustBridge.request(
                "capture.finish_session",
                mapOf(
                    "database_path" to databasePath,
                    "session_id" to sid,
                    "ended_at_unix_ms" to System.currentTimeMillis(),
                    "frame_count" to captureSessionFrameCount.toLong()
                )
            )
            Log.i(TAG, "Capture session finished: $sid frames=$captureSessionFrameCount")
            captureSessionId = null
            captureSessionFrameCount = 0
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to finish capture session: $e")
            false
        }
    }

    // ── Frame Import ──────────────────────────────────────────────────────────

    /**
     * Imports a batch of raw BLE frames into the Rust store.
     *
     * Bridge: capture.import_frame_batch
     * Args (from CaptureImportFrameBatchArgs in bridge.rs):
     *   database_path: String         (required)
     *   parser_version: String        (optional, has default)
     *   include_timeline_rows: bool   (optional, default true)
     *   compact_raw_payloads: bool    (optional, default true)
     *   include_results: bool         (optional, default true)
     *   frames: Vec<CapturedFrameInput>  (required)
     *
     * CapturedFrameInput fields (from capture_import.rs):
     *   evidence_id: String           (required — unique ID for dedup)
     *   frame_id: Option<String>      (optional)
     *   source: String                (required — e.g. "ble.android")
     *   captured_at: String           (required — ISO 8601 timestamp)
     *   device_model: String          (required — device name)
     *   frame_hex: String             (required — raw hex bytes)
     *   sensitivity: String           (required — e.g. "user_owned")
     *   capture_session_id: Option<String>  (optional)
     *   device_type: DeviceType       (optional, default "goose")
     *
     * Response: CapturedFrameBatchImportReport
     *   frames_inserted: usize        (newly decoded frames)
     *   raw_inserted: usize           (newly stored raw evidence)
     *   frames_existing: usize        (already in DB)
     */
    suspend fun importFrameBatch(
        frames: List<CapturedFrame>,
        deviceModel: String = "WHOOP 5.0",
        deviceType: String = "goose"
    ): ImportResult = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext ImportResult.stubbed()
        if (frames.isEmpty()) return@withContext ImportResult.empty()
        val sid = captureSessionId
        try {
            val frameList = frames.map { frame ->
                buildMap {
                    // evidence_id must be globally unique per frame for dedup
                    put("evidence_id", frame.evidenceId)
                    put("source", "ble.android")
                    put("captured_at", isoFromMs(frame.capturedAtMs))
                    put("device_model", deviceModel)
                    put("frame_hex", frame.hexData)
                    put("sensitivity", "user_owned")
                    put("device_type", deviceType)
                    if (sid != null) put("capture_session_id", sid)
                }
            }
            val result = GooseRustBridge.request(
                "capture.import_frame_batch",
                buildMap {
                    put("database_path", databasePath)
                    put("frames", frameList)
                    // Minimize overhead: skip timeline and full results in live mode
                    put("include_timeline_rows", false)
                    put("compact_raw_payloads", true)
                    put("include_results", false)
                }
            )
            val inserted = (result["frames_inserted"] as? Number)?.toInt() ?: 0
            val rawInserted = (result["raw_inserted"] as? Number)?.toInt() ?: 0
            val existing = (result["frames_existing"] as? Number)?.toInt() ?: 0
            captureSessionFrameCount += frames.size
            Log.i(TAG, "Frame batch imported: raw=$rawInserted frames=$inserted existing=$existing")
            ImportResult(imported = inserted, skipped = existing, error = null)
        } catch (e: Exception) {
            Log.e(TAG, "Frame batch import failed: $e")
            ImportResult(imported = 0, skipped = 0, error = e.message)
        }
    }

    // ── Health Metric Queries ─────────────────────────────────────────────────

    /**
     * Computes recovery score for the last 24h window.
     *
     * Bridge: metrics.recovery_score_from_features
     * Args (from RecoveryFeatureScoreArgs in bridge.rs):
     *   database_path: String    (required)
     *   start: String            (ISO date, default "2020-01-01")
     *   end: String              (ISO date, default today)
     *   resting_start / resting_end: optional sub-window for resting HR
     *   hrv_start / hrv_end: optional sub-window for HRV
     *   hrv_baseline_start / hrv_baseline_end: optional baseline window
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun queryRecoveryScore(): RecoveryResult? = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext null
        try {
            val (start, end) = last24hWindow()
            val result = GooseRustBridge.request(
                "metrics.recovery_score_from_features",
                mapOf(
                    "database_path" to databasePath,
                    "start" to start,
                    "end" to end
                )
            )
            // Result is the nested score_result object from the report
            val scoreResult = result["score_result"] as? Map<*, *> ?: result
            val score = (scoreResult["score_0_to_100"] as? Number)?.toInt()
                ?: (result["score_0_to_100"] as? Number)?.toInt()
                ?: return@withContext null
            val hrv = (scoreResult["hrv_rmssd_ms"] as? Number)?.toDouble()
                ?: (result["hrv_rmssd_ms"] as? Number)?.toDouble()
            val rhr = (scoreResult["resting_hr_bpm"] as? Number)?.toInt()
                ?: (result["resting_hr_bpm"] as? Number)?.toInt()
            RecoveryResult(score = score, hrvRmssd = hrv, restingHr = rhr)
        } catch (e: Exception) {
            Log.d(TAG, "Recovery query: ${e.message}")
            null
        }
    }

    /**
     * Computes sleep score for the last sleep window.
     *
     * Bridge: metrics.sleep_score_from_features
     * Args: database_path, start, end (ISO date strings)
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun querySleepScore(): SleepResult? = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext null
        try {
            val (start, end) = last24hWindow()
            val result = GooseRustBridge.request(
                "metrics.sleep_score_from_features",
                mapOf(
                    "database_path" to databasePath,
                    "start" to start,
                    "end" to end
                )
            )
            val scoreResult = result["score_result"] as? Map<*, *> ?: result
            val score = (scoreResult["score_0_to_100"] as? Number)?.toInt()
                ?: (result["score_0_to_100"] as? Number)?.toInt()
                ?: return@withContext null
            val totalMin = (scoreResult["total_sleep_minutes"] as? Number)?.toInt()
                ?: (result["total_sleep_minutes"] as? Number)?.toInt() ?: 0
            val remMin = (scoreResult["rem_minutes"] as? Number)?.toInt()
                ?: (result["rem_minutes"] as? Number)?.toInt() ?: 0
            val deepMin = (scoreResult["deep_minutes"] as? Number)?.toInt()
                ?: (result["deep_minutes"] as? Number)?.toInt() ?: 0
            val lightMin = (scoreResult["light_minutes"] as? Number)?.toInt()
                ?: (result["light_minutes"] as? Number)?.toInt() ?: 0
            SleepResult(
                score = score,
                totalMinutes = totalMin,
                remMinutes = remMin,
                deepMinutes = deepMin,
                lightMinutes = lightMin
            )
        } catch (e: Exception) {
            Log.d(TAG, "Sleep query: ${e.message}")
            null
        }
    }

    /**
     * Computes strain score for today.
     *
     * Bridge: metrics.strain_score_from_features
     * Args (from StrainFeatureScoreArgs in bridge.rs):
     *   database_path, start, end (ISO date strings),
     *   optional resting_start/resting_end sub-window
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun queryStrainScore(): StrainResult? = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext null
        try {
            val (start, end) = last24hWindow()
            val result = GooseRustBridge.request(
                "metrics.strain_score_from_features",
                mapOf(
                    "database_path" to databasePath,
                    "start" to start,
                    "end" to end
                )
            )
            val scoreResult = result["score_result"] as? Map<*, *> ?: result
            val strain = (scoreResult["score_0_to_21"] as? Number)?.toDouble()
                ?: (result["score_0_to_21"] as? Number)?.toDouble()
                ?: return@withContext null
            val calories = (scoreResult["calories"] as? Number)?.toInt()
                ?: (result["calories"] as? Number)?.toInt() ?: 0
            val avgHr = (scoreResult["avg_heart_rate_bpm"] as? Number)?.toInt()
                ?: (result["avg_heart_rate_bpm"] as? Number)?.toInt() ?: 0
            StrainResult(dayStrain = strain, calories = calories, avgHr = avgHr)
        } catch (e: Exception) {
            Log.d(TAG, "Strain query: ${e.message}")
            null
        }
    }

    /**
     * Parses a single BLE frame through the Rust protocol parser.
     * Used for live HR extraction from WHOOP K10 data packets.
     *
     * Bridge: protocol.parse_frame_hex
     * Args (from ParseFrameArgs in bridge.rs):
     *   frame_hex: String          (required)
     *   device_type: String        (optional, defaults to "goose")
     *
     * Response includes `compact.heart_rate` for K10 data packets.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun parseFrame(hexData: String, deviceType: String = "goose"): ParsedFrameResult? =
        withContext(Dispatchers.IO) {
            if (!isInitialized) return@withContext null
            try {
                val result = GooseRustBridge.request(
                    "protocol.parse_frame_hex",
                    mapOf("frame_hex" to hexData, "device_type" to deviceType)
                )
                val compact = result["compact"] as? Map<*, *>
                val hr = (compact?.get("heart_rate") as? Number)?.toInt()
                val payloadKind = compact?.get("payload_kind") as? String ?: "unknown"
                val summary = compact?.get("summary") as? String ?: ""
                ParsedFrameResult(heartRate = hr, payloadKind = payloadKind, summary = summary)
            } catch (e: Exception) {
                null
            }
        }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun isoFromMs(ms: Long): String = iso8601Format.format(Date(ms))

    /**
     * Returns (start, end) as ISO date strings "yyyy-MM-dd" for the last 24h window.
     * The score methods accept ISO dates with default_correlation_start/end defaults.
     */
    private fun last24hWindow(): Pair<String, String> {
        val end = Date()
        val start = Date(end.time - 24 * 60 * 60 * 1000L)
        return isoDateFormat.format(start) to isoDateFormat.format(end)
    }

    companion object {
        private const val TAG = "GooseDataStore"

        private val iso8601Format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}

// ── Data classes ──────────────────────────────────────────────────────────────

/**
 * A raw BLE frame captured from a WHOOP characteristic notification.
 * @param evidenceId  A stable unique ID for deduplication (use timestamp + UUID)
 * @param hexData     The raw notification bytes as lowercase hex string
 * @param capturedAtMs  Unix epoch milliseconds when the notification was received
 * @param characteristicUuid  The GATT characteristic UUID this came from
 */
data class CapturedFrame(
    val evidenceId: String,
    val hexData: String,
    val capturedAtMs: Long,
    val characteristicUuid: String
) {
    companion object {
        fun fromNotification(hexData: String, characteristicUuid: String): CapturedFrame {
            val nowMs = System.currentTimeMillis()
            return CapturedFrame(
                evidenceId = "android-${nowMs}-${UUID.randomUUID()}",
                hexData = hexData,
                capturedAtMs = nowMs,
                characteristicUuid = characteristicUuid
            )
        }
    }
}

data class ImportResult(
    val imported: Int,
    val skipped: Int,
    val error: String?
) {
    val success: Boolean get() = error == null
    companion object {
        fun empty() = ImportResult(0, 0, null)
        fun stubbed() = ImportResult(0, 0, "stub_mode")
    }
}

data class RecoveryResult(
    val score: Int,            // 0-100
    val hrvRmssd: Double?,     // ms
    val restingHr: Int?        // bpm
)

data class SleepResult(
    val score: Int,
    val totalMinutes: Int,
    val remMinutes: Int,
    val deepMinutes: Int,
    val lightMinutes: Int
)

data class StrainResult(
    val dayStrain: Double,     // 0-21
    val calories: Int,
    val avgHr: Int
)

data class ParsedFrameResult(
    val heartRate: Int?,
    val payloadKind: String,
    val summary: String
)
