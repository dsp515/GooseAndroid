package com.goose.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goose.android.ble.GooseLogMessage
import com.goose.android.ble.LogLevel
import com.goose.android.data.HealthSummary
import com.goose.android.ui.components.*
import com.goose.android.ui.theme.GooseColors
import com.goose.android.rust.GooseRustBridge
import java.text.SimpleDateFormat
import java.util.*

// ─────────────────────────────────────────────────────────────────────────────
// MORE / DEBUG SCREEN — Mirrors MoreView.swift + MoreDebugViews.swift
// Settings, device info, debug log, BLE packet log
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MoreScreen(
    logMessages: List<GooseLogMessage>,
    health: HealthSummary,
    modifier: Modifier = Modifier
) {
    var selectedSection by remember { mutableStateOf(MoreSection.Debug) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GooseColors.DeepBackground)
    ) {
        // Section tab bar
        ScrollableTabRow(
            selectedTabIndex = MoreSection.values().indexOf(selectedSection),
            containerColor = GooseColors.CardBackground,
            contentColor = GooseColors.TextPrimary,
            edgePadding = 16.dp,
            divider = { GooseDivider() }
        ) {
            MoreSection.values().forEach { section ->
                Tab(
                    selected = section == selectedSection,
                    onClick = { selectedSection = section },
                    text = {
                        Text(
                            text = section.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (section == selectedSection) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (section == selectedSection) GooseColors.TextPrimary else GooseColors.TextSecondary
                            )
                        )
                    }
                )
            }
        }

        when (selectedSection) {
            MoreSection.Debug -> DebugLogSection(logMessages)
            MoreSection.About -> AboutSection()
            MoreSection.Privacy -> PrivacySection()
        }
    }
}

enum class MoreSection(val label: String) {
    Debug("Debug Log"),
    About("About"),
    Privacy("Privacy")
}

@Composable
private fun DebugLogSection(messages: List<GooseLogMessage>) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    if (messages.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            GooseEmptyState(
                title = "No Log Messages",
                subtitle = "BLE events and debug output will appear here."
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(messages.reversed()) { msg ->
                LogMessageRow(msg, timeFormat)
            }
        }
    }
}

@Composable
private fun LogMessageRow(msg: GooseLogMessage, timeFormat: SimpleDateFormat) {
    val levelColor = when (msg.level) {
        LogLevel.Error -> GooseColors.RedMetric
        LogLevel.Warn -> GooseColors.YellowMetric
        LogLevel.Info -> GooseColors.TextSecondary
        LogLevel.Debug -> GooseColors.TextTertiary
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = timeFormat.format(Date(msg.timestamp)),
            style = MaterialTheme.typography.labelSmall.copy(
                color = GooseColors.TextTertiary,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            ),
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = msg.body,
            style = MaterialTheme.typography.labelSmall.copy(
                color = levelColor,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AboutSection() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = GooseColors.CardBackground),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, GooseColors.CardBorder)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Goose for Android",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = GooseColors.TextPrimary
                        )
                    )
                    Text(
                        text = "Android port of the Goose WHOOP 5.0 companion app.\nLocal-first health data from your WHOOP 5.0 band — no WHOOP servers required.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = GooseColors.TextSecondary
                        )
                    )

                    GooseDivider()

                    GooseStatRow("Version", "1.0.0-alpha")
                    GooseStatRow("Original", "github.com/b-nnett/goose")
                    GooseStatRow("Rust Core", if (GooseRustBridge.isNativeAvailable()) "Loaded ✓" else "Stub mode")
                    GooseStatRow("Target", "WHOOP 5.0")
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = GooseColors.CardBackground),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, GooseColors.CardBorder)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Independence",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = GooseColors.TextPrimary
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Goose is an independent project not affiliated with WHOOP. It communicates with WHOOP 5.0 bands over Bluetooth using services exposed by the device. Product names are used only to describe compatibility.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = GooseColors.TextSecondary
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacySection() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GooseMetricCard(
                label = "Data Storage",
                value = "Local Only",
                subtitle = "Health data stays on your device. No cloud upload.",
                valueColor = GooseColors.GreenMetric
            )
        }
        item {
            GooseMetricCard(
                label = "Bluetooth",
                value = "On-device",
                subtitle = "BLE packets are processed locally through the Rust core. Nothing leaves the app.",
                valueColor = GooseColors.GreenMetric
            )
        }
        item {
            GooseMetricCard(
                label = "Coach",
                value = "Local",
                subtitle = "Coach responses use local metric summaries shown in the app. No external AI calls by default.",
                valueColor = GooseColors.GreenMetric
            )
        }
    }
}
