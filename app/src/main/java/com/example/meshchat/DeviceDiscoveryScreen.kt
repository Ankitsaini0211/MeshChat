package com.example.meshchat

import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.meshchat.ui.components.DeviceCard
import com.example.meshchat.ui.components.EmptyState
import com.example.meshchat.ui.components.MeshChatTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDiscoveryScreen(
    isScanning: Boolean,
    statusText: String,
    discoveredDevices: List<BluetoothDevice>,
    connectingDeviceAddress: String? = null,
    onStartScanClick: () -> Unit,
    onStopScanClick: () -> Unit,
    onConnectDeviceClick: (BluetoothDevice) -> Unit,
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            MeshChatTopBar(
                title = "New Chat",
                subtitle = "Find nearby MeshChat users",
                onBackClick = onBackClick
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Bluetooth Discovery",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onStartScanClick,
                            enabled = !isScanning,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isScanning) "Scanning…" else "Scan for Devices")
                        }

                        if (isScanning) {
                            OutlinedButton(
                                onClick = onStopScanClick
                            ) {
                                Text("Stop")
                            }
                        }
                    }
                }
            }

            Text(
                text = "Nearby Devices (${discoveredDevices.size})",
                style = MaterialTheme.typography.titleMedium
            )

            if (discoveredDevices.isEmpty()) {
                EmptyState(
                    icon = "🔍",
                    title = if (isScanning) "Searching for devices…" else "No devices found",
                    description = if (isScanning) {
                        "Keep another phone nearby running MeshChat in Advertising mode."
                    } else {
                        "Tap 'Scan for Devices' to discover nearby MeshChat users over BLE."
                    }
                )
            } else {
                discoveredDevices.forEach { device ->
                    DeviceCard(
                        device = device,
                        isConnecting = connectingDeviceAddress == device.address,
                        onConnectClick = { onConnectDeviceClick(device) }
                    )
                }
            }
        }
    }
}
