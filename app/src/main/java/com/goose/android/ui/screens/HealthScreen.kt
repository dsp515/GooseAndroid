package com.goose.android.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goose.android.data.*
import com.goose.android.ui.components.*
import com.goose.android.ui.theme.GooseColors

// ─────────────────────────────────────────────────────────────────────────────
// HEALTH SCREEN — Mirrors HealthView.swift / HealthDashboardViews.swift
// Tabs: Recovery, Sleep, Strain, Stress, Cardio, Energy
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HealthScreen(
    health: HealthSummary,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(HealthTab.Recovery) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GooseColors.DeepBackground)
    ) {
        // Horizontal tab selector
        HealthTabBar(
            selected = selectedTab,
            onSelect = { selectedTab = it }
        )

        // Tab content
        when (selectedTab) {
            HealthTab.Recovery -> RecoveryTabContent(health.recovery)
            HealthTab.Sleep -> SleepTabContent(health.sleep)
            HealthTab.Strain -> StrainTabContent(health.strain)
            HealthTab.Stress -> StressTabContent(health.stress)
            HealthTab.Cardio -> CardioTabContent(health.cardio)
            HealthTab.Energy -> EnergyTabContent(health.energy)
        }
    }
}

enum class HealthTab(val label: String) {
    Recovery("Recovery"),
    Sleep("Sleep"),
    Strain("Strain"),
    Stress("Stress"),
    Cardio("Cardio"),
    Energy("Energy")
}

@Composable
private fun HealthTabBar(selected: HealthTab, onSelect: (HealthTab) -> Unit) {
    ScrollableTabRow(
        selectedTabIndex = HealthTab.values().indexOf(selected),
        containerColor = GooseColors.CardBackground,
        contentColor = GooseColors.TextPrimary,
        edgePadding = 16.dp,
        divider = { GooseDivider() }
    ) {
        HealthTab.values().forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                text = {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (tab == selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (tab == selected) GooseColors.TextPrimary else GooseColors.TextSecondary
                        )
                    )
                }
            )
        }
    }
}

// ── Recovery Tab ──────────────────────────────────────────────────────────────

@Composable
private fun RecoveryTabContent(recovery: RecoverySnapshot) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                RecoveryRing(
                    score = recovery.scorePercent,
                    size = 200.dp,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        }

        if (!recovery.isAvailable) {
            item {
                GooseEmptyState(
                    title = "Recovery Unavailable",
                    subtitle = "Connect your WHOOP 5.0 and complete a sync to see recovery data."
                )
            }
        } else {
            item {
                GooseMetricCard(label = "HRV", value = recovery.hrvRmssd?.let { "%.1f".format(it) } ?: "—", unit = "ms rMSSD") {
                    Text(
                        text = "Heart Rate Variability — higher is better",
                        style = MaterialTheme.typography.bodySmall.copy(color = GooseColors.TextTertiary)
                    )
                }
            }
            item {
                GooseMetricCard(
                    label = "Resting Heart Rate",
                    value = recovery.restingHeartRate?.toString() ?: "—",
                    unit = "bpm",
                    valueColor = GooseColors.RedMetric
                )
            }
            recovery.spO2?.let { spO2 ->
                item {
                    GooseMetricCard(label = "SpO₂", value = "%.1f".format(spO2), unit = "%")
                }
            }
            recovery.skinTemperature?.let { temp ->
                item {
                    GooseMetricCard(label = "Skin Temperature", value = "%.1f".format(temp), unit = "°C")
                }
            }
        }
    }
}

// ── Sleep Tab ─────────────────────────────────────────────────────────────────

@Composable
private fun SleepTabContent(sleep: SleepSnapshot) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!sleep.isAvailable) {
            item {
                GooseEmptyState(
                    title = "Sleep Unavailable",
                    subtitle = "Sleep data appears after a complete overnight sync."
                )
            }
        } else {
            // Sleep score card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = GooseColors.CardBackground),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, GooseColors.CardBorder)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "SLEEP SCORE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = GooseColors.TextSecondary,
                                letterSpacing = 1.5.sp
                            )
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${sleep.scorePercent}%",
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = sleep.scoreColor.toColor()
                            )
                        )
                        Spacer(Modifier.height(16.dp))
                        SleepTimelineBar(
                            deepMinutes = sleep.deepMinutes,
                            remMinutes = sleep.remMinutes,
                            lightMinutes = sleep.lightMinutes,
                            awakeMinutes = sleep.awakeMinutes,
                            height = 14.dp
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            SleepStageDetail("Deep", sleep.deepMinutes, GooseColors.SleepDeep)
                            SleepStageDetail("REM", sleep.remMinutes, GooseColors.SleepREM)
                            SleepStageDetail("Light", sleep.lightMinutes, GooseColors.SleepLight)
                            SleepStageDetail("Awake", sleep.awakeMinutes, GooseColors.SleepAwake)
                        }
                    }
                }
            }

            // Stats
            item {
                GooseMetricCard(
                    label = "Total Sleep",
                    value = "%.1f".format(sleep.totalHours),
                    unit = "h"
                ) {
                    GooseStatRow("Efficiency", "%.0f".format(sleep.efficiency * 100.0), "%")
                    GooseStatRow("Latency", "${sleep.latencyMinutes}", "min")
                }
            }
        }
    }
}

@Composable
private fun SleepStageDetail(label: String, minutes: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "${minutes / 60}h ${minutes % 60}m",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = GooseColors.TextTertiary
            )
        )
    }
}

// ── Strain Tab ────────────────────────────────────────────────────────────────

@Composable
private fun StrainTabContent(strain: StrainSnapshot) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!strain.isAvailable) {
            item { GooseEmptyState("Strain Unavailable", "Strain data requires a synced workout.") }
        } else {
            item {
                GooseMetricCard(
                    label = "Day Strain",
                    value = "%.1f".format(strain.dayStrain),
                    valueColor = strain.strainColor.toColor(),
                    subtitle = "WHOOP strain scale 0–21"
                ) {
                    // Strain gauge bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(GooseColors.SurfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = (strain.dayStrain.toFloat() / 21f).coerceIn(0f, 1f))
                                .clip(RoundedCornerShape(4.dp))
                                .background(strain.strainColor.toColor())
                        )
                    }
                }
            }
            item {
                GooseMetricCard(label = "Heart Rate", value = "${strain.avgHeartRate}", unit = "bpm avg") {
                    GooseStatRow("Max HR", "${strain.maxHeartRate}", "bpm", GooseColors.RedMetric)
                }
            }
            item {
                GooseMetricCard(label = "Calories", value = "${strain.calories}", unit = "kcal")
            }
        }
    }
}

// ── Stress Tab ────────────────────────────────────────────────────────────────

@Composable
private fun StressTabContent(stress: StressSnapshot) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!stress.isAvailable) {
            item { GooseEmptyState("Stress Unavailable", "Stress score requires HRV and resting HR data.") }
        } else {
            item {
                GooseMetricCard(
                    label = "Stress Score",
                    value = "${stress.stressScore}",
                    unit = "/ 100",
                    valueColor = when {
                        stress.stressScore >= 70 -> GooseColors.RedMetric
                        stress.stressScore >= 40 -> GooseColors.YellowMetric
                        else -> GooseColors.GreenMetric
                    }
                )
            }
            item {
                GooseMetricCard(label = "HRV Drop", value = "%.1f".format(stress.hrvDrop), unit = "ms")
            }
        }
    }
}

// ── Cardio Tab ────────────────────────────────────────────────────────────────

@Composable
private fun CardioTabContent(cardio: CardioSnapshot) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GooseMetricCard(
                label = "VO₂ Max",
                value = cardio.vo2Max?.let { "%.1f".format(it) } ?: "—",
                unit = "ml/kg/min",
                badge = if (cardio.vo2Max == null) "Unavailable" else null
            )
        }
        item {
            GooseMetricCard(
                label = "Cardio Load",
                value = cardio.cardioLoad?.let { "%.1f".format(it) } ?: "—",
                badge = if (cardio.cardioLoad == null) "Unavailable" else null
            )
        }
    }
}

// ── Energy Tab ────────────────────────────────────────────────────────────────

@Composable
private fun EnergyTabContent(energy: EnergySnapshot) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GooseMetricCard(
                label = "Energy Bank",
                value = "${energy.energyBankPercent}",
                unit = "%",
                badge = if (energy.energyBankPercent == 0) "Unavailable" else null
            )
        }
        item {
            GooseMetricCard(label = "Calories Burned", value = "${energy.caloriesBurned}", unit = "kcal")
        }
    }
}

private fun MetricColor.toColor(): Color = when (this) {
    MetricColor.Green -> GooseColors.GreenMetric
    MetricColor.Yellow -> GooseColors.YellowMetric
    MetricColor.Orange -> GooseColors.OrangeMetric
    MetricColor.Red -> GooseColors.RedMetric
    MetricColor.Gray -> GooseColors.TextTertiary
}
