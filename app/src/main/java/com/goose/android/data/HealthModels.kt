package com.goose.android.data

/**
 * Health data models — port of HealthDataTypes.swift and HealthModels.swift.
 * These represent the parsed output from the Rust core bridge.
 */

data class RecoverySnapshot(
    val scorePercent: Int = 0,          // 0-100
    val hrvRmssd: Double? = null,       // ms
    val restingHeartRate: Int? = null,  // bpm
    val skinTemperature: Double? = null,// °C
    val spO2: Double? = null,           // %
    val readinessStatus: String = "unavailable",
    val capturedAt: Long = 0L
) {
    val scoreColor: MetricColor get() = when {
        scorePercent >= 67 -> MetricColor.Green
        scorePercent >= 34 -> MetricColor.Yellow
        else -> MetricColor.Red
    }
    val isAvailable: Boolean get() = scorePercent > 0
}

data class SleepSnapshot(
    val scorePercent: Int = 0,
    val totalMinutes: Int = 0,
    val remMinutes: Int = 0,
    val lightMinutes: Int = 0,
    val deepMinutes: Int = 0,
    val awakeMinutes: Int = 0,
    val efficiency: Double = 0.0,         // 0-1
    val latencyMinutes: Int = 0,
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val status: String = "unavailable"
) {
    val isAvailable: Boolean get() = totalMinutes > 0
    val totalHours: Double get() = totalMinutes / 60.0
    val scoreColor: MetricColor get() = when {
        scorePercent >= 67 -> MetricColor.Green
        scorePercent >= 34 -> MetricColor.Yellow
        else -> MetricColor.Red
    }
}

data class StrainSnapshot(
    val dayStrain: Double = 0.0,        // 0-21 (WHOOP strain scale)
    val workoutStrain: Double = 0.0,
    val avgHeartRate: Int = 0,
    val maxHeartRate: Int = 0,
    val calories: Int = 0,
    val status: String = "unavailable"
) {
    val isAvailable: Boolean get() = dayStrain > 0.0
    val strainColor: MetricColor get() = when {
        dayStrain >= 18 -> MetricColor.Red
        dayStrain >= 14 -> MetricColor.Orange
        dayStrain >= 10 -> MetricColor.Yellow
        else -> MetricColor.Green
    }
}

data class StressSnapshot(
    val stressScore: Int = 0,     // 0-100
    val hrvDrop: Double = 0.0,
    val status: String = "unavailable"
) {
    val isAvailable: Boolean get() = stressScore > 0
}

data class CardioSnapshot(
    val vo2Max: Double? = null,
    val cardioLoad: Double? = null,
    val status: String = "unavailable"
)

data class EnergySnapshot(
    val energyBankPercent: Int = 0,
    val caloriesBurned: Int = 0,
    val caloriesIn: Int = 0,
    val status: String = "unavailable"
)

data class VitalsSnapshot(
    val heartRate: Int? = null,
    val hrv: Double? = null,
    val spO2: Double? = null,
    val skinTemp: Double? = null,
    val bloodOxygen: Double? = null,
    val respiratoryRate: Double? = null,
    val capturedAt: Long = System.currentTimeMillis()
)

data class HealthSummary(
    val recovery: RecoverySnapshot = RecoverySnapshot(),
    val sleep: SleepSnapshot = SleepSnapshot(),
    val strain: StrainSnapshot = StrainSnapshot(),
    val stress: StressSnapshot = StressSnapshot(),
    val cardio: CardioSnapshot = CardioSnapshot(),
    val energy: EnergySnapshot = EnergySnapshot(),
    val vitals: VitalsSnapshot = VitalsSnapshot()
)

enum class MetricColor {
    Green, Yellow, Orange, Red, Gray;

    val hexColor: Long get() = when (this) {
        Green -> 0xFF4ADE80L
        Yellow -> 0xFFFBBF24L
        Orange -> 0xFFFB923CL
        Red -> 0xFFFF6B6BL
        Gray -> 0xFF6B7280L
    }
}

data class SleepStageSegment(
    val stage: SleepStage,
    val startMinute: Int,
    val durationMinutes: Int
)

enum class SleepStage(val label: String, val color: Long) {
    Deep("Deep", 0xFF6366F1L),
    REM("REM", 0xFF8B5CF6L),
    Light("Light", 0xFF60A5FAL),
    Awake("Awake", 0xFFFF6B6BL)
}

data class HeartRateSample(
    val bpm: Int,
    val timestamp: Long,
    val source: String = "ble"
)

data class HRVSample(
    val rmssd: Double,
    val rrIntervalCount: Int,
    val timestamp: Long,
    val source: String = "ble"
)
