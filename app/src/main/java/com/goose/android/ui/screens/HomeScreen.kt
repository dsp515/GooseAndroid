package com.goose.android.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goose.android.ble.ConnectionState
import com.goose.android.ble.GooseDiscoveredDevice
import com.goose.android.data.*
import com.goose.android.ui.components.*
import com.goose.android.ui.theme.GooseColors

// ─────────────────────────────────────────────────────────────────────────────
// HOME SCREEN — Mirrors HomeDashboardView.swift
// Shows recovery ring + score summaries + device connection bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(
    health: HealthSummary,
    connectionState: ConnectionState,
    liveHR: Int?,
    battery: Int?,
    onConnectClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(GooseColors.DeepBackground),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // ── Connection header ──
        item {
            HomeConnectionHeader(
                connectionState = connectionState,
                liveHR = liveHR,
                battery = battery,
                onConnectClick = onConnectClick
            )
        }

        // ── Recovery ring (hero element) ──
        item {
            Spacer(Modifier.height(24.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RecoveryRing(
                    score = health.recovery.scorePercent,
                    size = 220.dp,
                    strokeWidth = 18.dp
                )

                Spacer(Modifier.height(16.dp))

                // Recovery label underneath ring
                if (health.recovery.isAvailable) {
                    Text(
                        text = recoveryLabel(health.recovery.scorePercent),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = GooseColors.TextSecondary
                        )
                    )
                } else {
                    Text(
                        text = "Connect WHOOP 5.0 to sync",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = GooseColors.TextTertiary
                        )
                    )
                }
            }
        }

        // ── Quick stats row ──
        item {
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickStatChip(
                    label = "HRV",
                    value = health.recovery.hrvRmssd?.let { "%.0f".format(it) } ?: "—",
                    unit = "ms",
                    modifier = Modifier.weight(1f)
                )
                QuickStatChip(
                    label = "RHR",
                    value = health.recovery.restingHeartRate?.toString() ?: "—",
                    unit = "bpm",
                    modifier = Modifier.weight(1f)
                )
                QuickStatChip(
                    label = "SpO₂",
                    value = health.recovery.spO2?.let { "%.1f".format(it) } ?: "—",
                    unit = "%",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ── Today's summary cards ──
        item {
            Spacer(Modifier.height(24.dp))
            GooseSectionHeader(
                title = "Today",
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        item {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Sleep card
                GooseMetricCard(
                    label = "Sleep",
                    value = if (health.sleep.isAvailable) "%.1f".format(health.sleep.totalHours) else "—",
                    unit = "h",
                    valueColor = health.sleep.scoreColor.toColor(),
                    subtitle = if (health.sleep.isAvailable) "Score ${health.sleep.scorePercent}%" else "No sleep data",
                    badge = if (health.sleep.isAvailable) null else "Unavailable"
                ) {
                    if (health.sleep.isAvailable) {
                        SleepTimelineBar(
                            deepMinutes = health.sleep.deepMinutes,
                            remMinutes = health.sleep.remMinutes,
                            lightMinutes = health.sleep.lightMinutes,
                            awakeMinutes = health.sleep.awakeMinutes
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SleepLegendDot("Deep", GooseColors.SleepDeep)
                            SleepLegendDot("REM", GooseColors.SleepREM)
                            SleepLegendDot("Light", GooseColors.SleepLight)
                            SleepLegendDot("Awake", GooseColors.SleepAwake)
                        }
                    }
                }

                // Strain card
                GooseMetricCard(
                    label = "Strain",
                    value = if (health.strain.isAvailable) "%.1f".format(health.strain.dayStrain) else "—",
                    valueColor = health.strain.strainColor.toColor(),
                    subtitle = if (health.strain.isAvailable)
                        "${health.strain.calories} cal · Avg ${health.strain.avgHeartRate} bpm"
                    else "No strain data",
                    badge = if (health.strain.isAvailable) null else "Unavailable"
                )

                // Live vitals card (real-time)
                if (liveHR != null) {
                    GooseMetricCard(
                        label = "Heart Rate · Live",
                        value = "$liveHR",
                        unit = "bpm",
                        valueColor = GooseColors.RedMetric
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeConnectionHeader(
    connectionState: ConnectionState,
    liveHR: Int?,
    battery: Int?,
    onConnectClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(GooseColors.CardBackground, GooseColors.DeepBackground)
                )
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ConnectionStatusDot(
                    isConnected = connectionState.isConnected,
                    isAnimating = connectionState is ConnectionState.Connecting ||
                            connectionState is ConnectionState.Discovering
                )
                Text(
                    text = connectionState.label,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (connectionState.isConnected) GooseColors.TextPrimary
                        else GooseColors.TextSecondary
                    )
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                battery?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.BatteryStd,
                            contentDescription = "Battery",
                            tint = if (it > 20) GooseColors.GreenMetric else GooseColors.RedMetric,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = "$it%",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = GooseColors.TextSecondary
                            )
                        )
                    }
                }

                if (!connectionState.isConnected) {
                    OutlinedButton(
                        onClick = onConnectClick,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        border = BorderStroke(1.dp, GooseColors.GooseBlue),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Connect",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = GooseColors.GooseBlue
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickStatChip(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = GooseColors.CardBackground),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, GooseColors.CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = GooseColors.TextPrimary
                )
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = GooseColors.TextTertiary
                )
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = GooseColors.TextSecondary,
                    letterSpacing = 0.5.sp
                )
            )
        }
    }
}

@Composable
private fun SleepLegendDot(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = GooseColors.TextTertiary
            )
        )
    }
}

private fun recoveryLabel(score: Int): String = when {
    score >= 67 -> "Ready to Perform"
    score >= 34 -> "Moderate — Train carefully"
    score > 0 -> "Rest & Recover"
    else -> "Unavailable"
}

private fun MetricColor.toColor(): Color = when (this) {
    MetricColor.Green -> GooseColors.GreenMetric
    MetricColor.Yellow -> GooseColors.YellowMetric
    MetricColor.Orange -> GooseColors.OrangeMetric
    MetricColor.Red -> GooseColors.RedMetric
    MetricColor.Gray -> GooseColors.TextTertiary
}

// Extension on GooseSectionHeader to support modifier
@Composable
fun GooseSectionHeader(title: String, trailingText: String? = null, onTrailingClick: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                color = GooseColors.TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        )
        trailingText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = GooseColors.GooseBlue
                ),
                modifier = if (onTrailingClick != null)
                    Modifier.clickable(onClick = onTrailingClick)
                else Modifier
            )
        }
    }
}
