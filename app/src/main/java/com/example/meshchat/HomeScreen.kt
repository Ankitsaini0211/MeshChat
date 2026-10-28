package com.example.meshchat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.meshchat.ui.components.DiagnosticsCard
import com.example.meshchat.ui.components.EmptyState
import com.example.meshchat.ui.components.MeshChatTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    bluetoothStatus: String = "Bluetooth Ready",
    isAdvertising: Boolean = false,
    isConnected: Boolean = false,
    connectedPeerName: String? = null,
    lastMessage: String? = null,
    onNewChatClick: () -> Unit,
    onOpenChatClick: (() -> Unit)? = null,
    // Diagnostic Parameters
    bluetoothAdapterStatus: String = "Enabled",
    hasPermissions: Boolean = true,
    isGattServerRunning: Boolean = false,
    isScanning: Boolean = false,
    isNotificationsSubscribed: Boolean = false,
    lastError: String? = null
) {
    Scaffold(
        topBar = {
            MeshChatTopBar(
                title = "MeshChat",
                subtitle = "Offline nearby messaging"
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
            // Network & Bluetooth Status Banner Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "BLE Mesh Status",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isConnected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else if (isAdvertising && isGattServerRunning) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Text(
                                text = when {
                                    isConnected -> "● Connected"
                                    isAdvertising && isGattServerRunning -> "● Discoverable"
                                    else -> "○ Offline"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = bluetoothStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Diagnostics Component
            DiagnosticsCard(
                bluetoothAdapterStatus = bluetoothAdapterStatus,
                hasPermissions = hasPermissions,
                isGattServerRunning = isGattServerRunning,
                isAdvertising = isAdvertising,
                isScanning = isScanning,
                connectedPeer = connectedPeerName,
                isNotificationsSubscribed = isNotificationsSubscribed,
                lastError = lastError
            )

            // Active Connection / Conversations Section
            Text(
                text = "Conversations",
                style = MaterialTheme.typography.titleMedium
            )

            if (isConnected && onOpenChatClick != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenChatClick() },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "💬",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(10.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = connectedPeerName ?: "Connected Mesh Peer",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )

                            Text(
                                text = lastMessage ?: "Active Bluetooth session",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }

                        Button(onClick = onOpenChatClick) {
                            Text("Open")
                        }
                    }
                }
            } else {
                EmptyState(
                    icon = "💬",
                    title = "Your conversations",
                    description = "No active nearby sessions. Connect to a nearby MeshChat device to start chatting offline.",
                    actionLabel = "New Chat",
                    onActionClick = onNewChatClick
                )
            }
        }
    }
}
