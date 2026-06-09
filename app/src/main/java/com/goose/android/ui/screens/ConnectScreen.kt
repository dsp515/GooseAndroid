package com.goose.android.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goose.android.ble.ConnectionState
import com.goose.android.ble.GooseDiscoveredDevice
import com.goose.android.ui.components.*
import com.goose.android.ui.theme.GooseColors

// ─────────────────────────────────────────────────────────────────────────────
// CONNECT SCREEN — Mirrors ConnectionView.swift + DeviceView.swift
// BLE scan, device list, connect/disconnect
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ConnectScreen(
    connectionState: ConnectionState,
    isScanning: Boolean,
    discoveredDevices: List<GooseDiscoveredDevice>,
    activeDevice: GooseDiscoveredDevice?,
    firmwareVersion: String?,
    batteryLevel: Int?,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (GooseDiscoveredDevice) -> Unit,
    onDisconnect: () -> Unit,
    onStartPhysiology: () -> Unit,
    onStopPhysiology: () -> Unit,
    onHistoricalSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(GooseColors.DeepBackground),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // ── Device panel (if connected) ──
        if (connectionState.isConnected && activeDevice != null) {
            item {
                ConnectedDevicePanel(
                    device = activeDevice,
                    firmwareVersion = firmwareVersion,
                    batteryLevel = batteryLevel,
                    onDisconnect = onDisconnect,
                    onStartPhysiology = onStartPhysiology,
                    onStopPhysiology = onStopPhysiology,
                    onHistoricalSync = onHistoricalSync
                )
            }
        }

        // ── Scan button + status ──
        item {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!connectionState.isConnected) {
                    GooseSectionHeader("Scan for Devices")
                    
                    Button(
                        onClick = if (isScanning) onStopScan else onStartScan,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isScanning) GooseColors.SurfaceVariant else GooseColors.GooseBlue
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = GooseColors.TextPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Filled.Bluetooth, contentDescription = null)
                            }
                            Text(
                                text = if (isScanning) "Scanning…" else "Scan for WHOOP 5.0",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }

        // ── Device list ──
        if (discoveredDevices.isNotEmpty()) {
            item {
                GooseSectionHeader(
                    "Found Devices",
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            items(discoveredDevices) { device ->
                DeviceListItem(
                    device = device,
                    isSelected = device.address == activeDevice?.address,
                    connectionState = connectionState,
                    onConnect = { onConnect(device) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        } else if (isScanning) {
            item {
                GooseEmptyState(
                    title = "Searching…",
                    subtitle = "Make sure your WHOOP 5.0 is nearby and not connected to another device."
                )
            }
        } else if (!connectionState.isConnected) {
            item {
                GooseEmptyState(
                    title = "No Devices",
                    subtitle = "Tap Scan to search for nearby WHOOP 5.0 bands."
                )
            }
        }
    }
}

@Composable
private fun ConnectedDevicePanel(
    device: GooseDiscoveredDevice,
    firmwareVersion: String?,
    batteryLevel: Int?,
    onDisconnect: () -> Unit,
    onStartPhysiology: () -> Unit,
    onStopPhysiology: () -> Unit,
    onHistoricalSync: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = GooseColors.CardBackground),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, GooseColors.GreenMetric.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ConnectionStatusDot(isConnected = true)
                        Text(
                            text = device.name,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = GooseColors.TextPrimary
                            )
                        )
                    }
                    Text(
                        text = device.address,
                        style = MaterialTheme.typography.bodySmall.copy(color = GooseColors.TextTertiary)
                    )
                }

                batteryLevel?.let {
                    Column(horizontalAlignment = Alignment.End) {
                        Icon(
                            imageVector = Icons.Filled.BatteryStd,
                            contentDescription = "Battery",
                            tint = if (it > 20) GooseColors.GreenMetric else GooseColors.RedMetric,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "$it%",
                            style = MaterialTheme.typography.bodySmall.copy(color = GooseColors.TextSecondary)
                        )
                    }
                }
            }

            firmwareVersion?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Firmware $it",
                    style = MaterialTheme.typography.labelSmall.copy(color = GooseColors.TextTertiary)
                )
            }

            GooseDivider(modifier = Modifier.padding(vertical = 16.dp))

            // Action buttons
            Text(
                text = "ACTIONS",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = GooseColors.TextTertiary,
                    letterSpacing = 1.5.sp
                )
            )
            Spacer(Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DeviceActionButton(
                    label = "Start Physiology Capture",
                    icon = Icons.Filled.MonitorHeart,
                    color = GooseColors.GooseBlue,
                    onClick = onStartPhysiology
                )
                DeviceActionButton(
                    label = "Sync Historical Data",
                    icon = Icons.Filled.Sync,
                    color = GooseColors.GoosePurple,
                    onClick = onHistoricalSync
                )
                DeviceActionButton(
                    label = "Disconnect",
                    icon = Icons.Filled.BluetoothDisabled,
                    color = GooseColors.RedMetric,
                    onClick = onDisconnect,
                    outlined = true
                )
            }
        }
    }
}

@Composable
private fun DeviceActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    outlined: Boolean = false
) {
    if (outlined) {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, color),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = color)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(label, style = MaterialTheme.typography.labelLarge)
            }
        }
    } else {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.15f), contentColor = color),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun DeviceListItem(
    device: GooseDiscoveredDevice,
    isSelected: Boolean,
    connectionState: ConnectionState,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnecting = isSelected && (connectionState is ConnectionState.Connecting ||
            connectionState is ConnectionState.Discovering)
    val isConnected = isSelected && connectionState.isConnected

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) GooseColors.SurfaceVariant else GooseColors.CardBackground
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = 1.dp,
            color = when {
                isConnected -> GooseColors.GreenMetric.copy(alpha = 0.5f)
                isSelected -> GooseColors.GooseBlue.copy(alpha = 0.4f)
                else -> GooseColors.CardBorder
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isConnected, onClick = onConnect)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(GooseColors.SurfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Watch,
                        contentDescription = null,
                        tint = GooseColors.GooseBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            color = GooseColors.TextPrimary
                        )
                    )
                    Text(
                        text = "RSSI ${device.rssi} dBm · ${device.address.takeLast(8)}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = GooseColors.TextTertiary
                        )
                    )
                }
            }

            when {
                isConnecting -> CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = GooseColors.GooseBlue,
                    strokeWidth = 2.dp
                )
                isConnected -> Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Connected",
                    tint = GooseColors.GreenMetric,
                    modifier = Modifier.size(20.dp)
                )
                else -> Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = "Connect",
                    tint = GooseColors.TextTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
