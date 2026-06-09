package com.goose.android

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.viewmodel.compose.viewModel
import com.goose.android.ui.screens.*
import com.goose.android.ui.theme.GooseColors
import com.goose.android.ui.theme.GooseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GooseTheme {
                GooseApp()
            }
        }
    }
}

// ── Required BLE permissions (mirrors iOS ensureCentral() flow) ───────────────

private fun blePermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_FINE_LOCATION
    )
} else {
    arrayOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.ACCESS_FINE_LOCATION
    )
}

@SuppressLint("MissingPermission")
@Composable
fun GooseApp() {
    val context = LocalContext.current
    val vm: GooseViewModel = viewModel()

    // ── Permission state ──────────────────────────────────────────────────────
    var permissionsGranted by remember {
        mutableStateOf(
            blePermissions().all {
                ContextCompat.checkSelfPermission(context, it) == PermissionChecker.PERMISSION_GRANTED
            }
        )
    }
    var permissionsDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
        permissionsDenied = !permissionsGranted
    }

    // ── Bluetooth enable ──────────────────────────────────────────────────────
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* result ignored — state checked when scanning */ }

    // ── App state ─────────────────────────────────────────────────────────────
    val connectionState by vm.connectionState.collectAsState()
    val isScanning by vm.isScanning.collectAsState()
    val discoveredDevices by vm.discoveredDevices.collectAsState()
    val activeDevice by vm.activeDevice.collectAsState()
    val liveHR by vm.liveHeartRate.collectAsState()
    val battery by vm.batteryLevel.collectAsState()
    val firmwareVersion by vm.firmwareVersion.collectAsState()
    val logMessages by vm.logMessages.collectAsState()
    val health by vm.health.collectAsState()

    var selectedTab by remember { mutableStateOf(GooseTab.Home) }

    // ── Safe scan launcher — requests permissions first like iOS requestBluetooth() ──
    val onStartScan: () -> Unit = {
        if (!permissionsGranted) {
            permissionLauncher.launch(blePermissions())
        } else {
            val btAdapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            if (btAdapter != null && !btAdapter.isEnabled) {
                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } else {
                vm.startScan()
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(GooseColors.DeepBackground),
        containerColor = GooseColors.DeepBackground,
        contentColor = GooseColors.TextPrimary,
        bottomBar = {
            GooseBottomBar(selected = selectedTab, onSelect = { selectedTab = it })
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                GooseTab.Home -> HomeScreen(
                    health = health,
                    connectionState = connectionState,
                    liveHR = liveHR,
                    battery = battery,
                    onConnectClick = { selectedTab = GooseTab.Device }
                )

                GooseTab.Health -> HealthScreen(health = health)

                GooseTab.Device -> {
                    if (permissionsDenied) {
                        // Show permission rationale (mirrors iOS onboarding permission screen)
                        BluetoothPermissionRequired(
                            onRetry = {
                                permissionsDenied = false
                                permissionLauncher.launch(blePermissions())
                            }
                        )
                    } else {
                        ConnectScreen(
                            connectionState = connectionState,
                            isScanning = isScanning,
                            discoveredDevices = discoveredDevices,
                            activeDevice = activeDevice,
                            firmwareVersion = firmwareVersion,
                            batteryLevel = battery,
                            onStartScan = onStartScan,
                            onStopScan = vm::stopScan,
                            onConnect = vm::connect,
                            onDisconnect = vm::disconnect,
                            onStartPhysiology = vm::startPhysiology,
                            onStopPhysiology = vm::stopPhysiology,
                            onHistoricalSync = vm::requestHistoricalSync
                        )
                    }
                }

                GooseTab.More -> MoreScreen(
                    logMessages = logMessages,
                    health = health
                )
            }
        }
    }
}

// ── Permission rationale screen (shown if user denies BLE permissions) ────────

@Composable
private fun BluetoothPermissionRequired(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GooseColors.DeepBackground)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Bluetooth,
            contentDescription = null,
            tint = GooseColors.GooseBlue,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Bluetooth Access Required",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = GooseColors.TextPrimary
            ),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Goose needs Bluetooth permission to scan for and connect to your WHOOP 5.0 band. No data leaves your device.",
            style = MaterialTheme.typography.bodyMedium.copy(color = GooseColors.TextSecondary),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = GooseColors.GooseBlue)
        ) {
            Text("Grant Bluetooth Permission")
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "You can also enable it in Settings → Apps → Goose → Permissions",
            style = MaterialTheme.typography.labelSmall.copy(color = GooseColors.TextTertiary),
            textAlign = TextAlign.Center
        )
    }
}

// ── Bottom navigation ─────────────────────────────────────────────────────────

@Composable
private fun GooseBottomBar(selected: GooseTab, onSelect: (GooseTab) -> Unit) {
    NavigationBar(
        containerColor = GooseColors.CardBackground,
        contentColor = GooseColors.TextPrimary,
        tonalElevation = 0.dp
    ) {
        GooseTab.values().forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = {
                    Icon(
                        imageVector = if (tab == selected) tab.selectedIcon else tab.icon,
                        contentDescription = tab.label
                    )
                },
                label = {
                    Text(text = tab.label, style = MaterialTheme.typography.labelSmall)
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = GooseColors.TextPrimary,
                    selectedTextColor = GooseColors.TextPrimary,
                    indicatorColor = GooseColors.SurfaceVariant,
                    unselectedIconColor = GooseColors.TabInactive,
                    unselectedTextColor = GooseColors.TabInactive
                )
            )
        }
    }
}

enum class GooseTab(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    Home("Home", Icons.Outlined.Home, Icons.Filled.Home),
    Health("Health", Icons.Outlined.Favorite, Icons.Filled.Favorite),
    Device("Device", Icons.Outlined.Watch, Icons.Filled.Watch),
    More("More", Icons.Outlined.MoreHoriz, Icons.Filled.MoreHoriz)
}
