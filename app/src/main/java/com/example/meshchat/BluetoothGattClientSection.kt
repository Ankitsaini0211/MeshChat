package com.example.meshchat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.meshchat.ui.screens.ChatScreen

private enum class AppScreen {
    HOME,
    DISCOVERY,
    CHAT
}

@Composable
fun BluetoothGattClientSection() {
    val context = LocalContext.current
    val bleManager = remember { MeshBleManager(context.applicationContext) }

    // Recompose when MeshBleManager notifies state change
    var stateTick by remember { mutableIntStateOf(0) }
    DisposableEffect(bleManager) {
        bleManager.onStateChanged = {
            stateTick++
        }
        onDispose {
            bleManager.onStateChanged = null
        }
    }

    // Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            bleManager.logEvent("Permissions granted by user")
        } else {
            bleManager.logEvent("Some permissions were denied", isError = true)
        }
    }

    // Auto-request permissions and start GATT Server + Advertiser on launch
    LaunchedEffect(bleManager.checkPermissions(), bleManager.bluetoothAdapter?.isEnabled) {
        if (!bleManager.checkPermissions()) {
            permissionLauncher.launch(bleManager.requiredPermissions)
        } else if (bleManager.bluetoothAdapter?.isEnabled == true) {
            bleManager.startGattServer()
            bleManager.startAdvertising()
        }
    }

    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }

    // Recompose dependency on stateTick
    @Suppress("UNUSED_VARIABLE")
    val currentTick = stateTick

    when (currentScreen) {
        AppScreen.HOME -> {
            HomeScreen(
                bluetoothStatus = bleManager.clientStatus,
                isAdvertising = bleManager.isAdvertising,
                isConnected = bleManager.isConnected,
                connectedPeerName = bleManager.activePeerName,
                lastMessage = bleManager.messages.lastOrNull()?.text,
                onNewChatClick = {
                    if (!bleManager.checkPermissions()) {
                        permissionLauncher.launch(bleManager.requiredPermissions)
                    } else {
                        currentScreen = AppScreen.DISCOVERY
                    }
                },
                onOpenChatClick = { currentScreen = AppScreen.CHAT },
                // Diagnostics
                bluetoothAdapterStatus = if (bleManager.bluetoothAdapter?.isEnabled == true) "Enabled" else "Disabled",
                hasPermissions = bleManager.checkPermissions(),
                isGattServerRunning = bleManager.isGattServerRunning,
                isScanning = bleManager.isScanning,
                isNotificationsSubscribed = bleManager.clientStatus.contains("Notifications enabled") || bleManager.serverNotificationsEnabled,
                lastError = bleManager.lastLogMessage
            )
        }

        AppScreen.DISCOVERY -> {
            // Auto start scanning when Discovery screen is active
            DisposableEffect(Unit) {
                bleManager.startScanning()
                onDispose {
                    bleManager.stopScanning()
                }
            }

            DeviceDiscoveryScreen(
                isScanning = bleManager.isScanning,
                statusText = bleManager.clientStatus,
                discoveredDevices = bleManager.discoveredDevices,
                connectingDeviceAddress = bleManager.connectedClientDevice?.address,
                onStartScanClick = { bleManager.startScanning() },
                onStopScanClick = { bleManager.stopScanning() },
                onConnectDeviceClick = { device ->
                    bleManager.connectToDevice(device)
                    currentScreen = AppScreen.CHAT
                },
                onBackClick = { currentScreen = AppScreen.HOME }
            )
        }

        AppScreen.CHAT -> {
            ChatScreen(
                peerName = bleManager.activePeerName,
                isConnected = bleManager.isConnected,
                statusText = bleManager.clientStatus,
                messages = bleManager.messages,
                onSendClick = { text -> bleManager.enqueueMessage(text) },
                onBackClick = { currentScreen = AppScreen.HOME }
            )
        }
    }
}
