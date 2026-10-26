package com.example.meshchat

import android.bluetooth.BluetoothGattDescriptor
import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.ui.unit.dp
import java.util.UUID


@Composable
fun BluetoothGattServerSection() {

    val context = LocalContext.current

    val bluetoothManager = remember {
        context.getSystemService(Context.BLUETOOTH_SERVICE)
                as BluetoothManager
    }

    val bluetoothAdapter = bluetoothManager.adapter


    var serverStatus by remember {
        mutableStateOf("GATT server is not running")
    }


    var isServerRunning by remember {
        mutableStateOf(false)
    }


    var connectedDeviceName by remember {
        mutableStateOf("No phone connected")
    }

    var receivedMessage by remember {
        mutableStateOf("No messages received yet")
    }

    var receivedMessages by remember {
        mutableStateOf<List<ChatMessage>>(emptyList())
    }

    var serverMessageText by remember {
        mutableStateOf("")
    }


    var gattServer by remember {
        mutableStateOf<BluetoothGattServer?>(null)
    }

    var connectedDevice by remember {
        mutableStateOf<BluetoothDevice?>(null)
    }

    val rxCharacteristic = remember {
        BluetoothGattCharacteristic(
            MESHCHAT_RX_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
    }


    val cccdDescriptor = remember {
        BluetoothGattDescriptor(
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
            BluetoothGattDescriptor.PERMISSION_READ or
                    BluetoothGattDescriptor.PERMISSION_WRITE
        )
    }


    val txCharacteristic = remember {
        BluetoothGattCharacteristic(
            MESHCHAT_TX_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
    }

    txCharacteristic.addDescriptor(cccdDescriptor)

    var notificationsEnabled by remember {
        mutableStateOf(false)
    }


    fun sendNotification(message: String) {
        val server = gattServer
        val device = connectedDevice

        if (server == null || device == null) {
            Log.e("MeshChatBLE", "No connected client")
            return
        }

        if (!notificationsEnabled) {
            Log.e("MeshChatBLE", "Client notifications are disabled")
            return
        }

        val bytes = message.toByteArray(Charsets.UTF_8)

        try {
            val success = if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            ) {
                server.notifyCharacteristicChanged(
                    device,
                    txCharacteristic,
                    false,
                    bytes
                ) == android.bluetooth.BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                txCharacteristic.value = bytes

                @Suppress("DEPRECATION")
                server.notifyCharacteristicChanged(
                    device,
                    txCharacteristic,
                    false
                )
            }

            Log.d("MeshChatBLE", "Notification initiated: $success")

            if (success) {
                receivedMessages = receivedMessages + ChatMessage(
                    text = message,
                    isSent = true
                )
            } else {
                Log.e("MeshChatBLE", "Failed to initiate notification")
            }
        } catch (e: SecurityException) {
            Log.e(
                "MeshChatBLE",
                "Bluetooth permission denied while sending notification",
                e
            )
        }
    }




    val gattServerCallback = remember {
        object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(
                device: BluetoothDevice,
                status: Int,
                newState: Int
            ) {
                when {
                    status != BluetoothGatt.GATT_SUCCESS -> {
                        connectedDeviceName = "Connection error: $status"
                        Log.e(
                            "MeshChatBLE",
                            "Connection error: status=$status"
                        )
                    }

                    newState == BluetoothProfile.STATE_CONNECTED -> {
                        connectedDevice = device
                        connectedDeviceName = "Phone connected"
                        Log.d(
                            "MeshChatBLE",
                            "Client connected: ${device.address}"
                        )
                    }

                    newState == BluetoothProfile.STATE_DISCONNECTED -> {
                        connectedDevice = null
                        notificationsEnabled = false
                        connectedDeviceName = "No phone connected"
                        Log.d(
                            "MeshChatBLE",
                            "Client disconnected: ${device.address}"
                        )
                    }
                }

            }


            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray
            ) {
                if (characteristic.uuid == MESHCHAT_RX_UUID) {

                    val message = value.toString(Charsets.UTF_8)

                    receivedMessage = message

                    receivedMessages = receivedMessages + ChatMessage(
                        text = receivedMessage,
                        isSent = false
                    )

                    Log.d(
                        "MeshChatBLE",
                        "Message received: $message"
                    )

                    if (responseNeeded) {
                        val hasConnectPermission =
                            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.BLUETOOTH_CONNECT
                                    ) == PackageManager.PERMISSION_GRANTED

                        if (hasConnectPermission) {
                            try {
                                gattServer?.sendResponse(
                                    device,
                                    requestId,
                                    BluetoothGatt.GATT_SUCCESS,
                                    offset,
                                    null
                                )
                            } catch (e: SecurityException) {
                                Log.e(
                                    "MeshChatBLE",
                                    "Permission missing while sending response",
                                    e
                                )
                            }
                        } else {
                            Log.e(
                                "MeshChatBLE",
                                "BLUETOOTH_CONNECT permission not granted"
                            )
                        }
                    }
                } else {
                    if (responseNeeded) {
                        val hasConnectPermission =
                            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.BLUETOOTH_CONNECT
                                    ) == PackageManager.PERMISSION_GRANTED

                        if (hasConnectPermission) {
                            try {
                                gattServer?.sendResponse(
                                    device,
                                    requestId,
                                    BluetoothGatt.GATT_SUCCESS,
                                    offset,
                                    null
                                )
                            } catch (e: SecurityException) {
                                Log.e(
                                    "MeshChatBLE",
                                    "Permission missing while sending response",
                                    e
                                )
                            }
                        } else {
                            Log.e(
                                "MeshChatBLE",
                                "BLUETOOTH_CONNECT permission not granted"
                            )
                        }
                    }
                }
            }

            override fun onDescriptorWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                descriptor: BluetoothGattDescriptor,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray
            ) {
                val isCccd = descriptor.uuid == UUID.fromString(
                    "00002902-0000-1000-8000-00805f9b34fb"
                )

                if(isCccd) {
                    notificationsEnabled = value.contentEquals(
                        byteArrayOf(0x01, 0x00)
                    )

                    Log.d(
                        "MeshChatBLE",
                        "Notifications enabled: $notificationsEnabled"
                    )
                }


                if(responseNeeded) {
                    val hasConnectPermission =
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.BLUETOOTH_CONNECT
                                ) == PackageManager.PERMISSION_GRANTED


                    if(hasConnectPermission) {
                        try{
                            gattServer?.sendResponse(
                                device,
                                requestId,
                                if(isCccd){
                                    BluetoothGatt.GATT_SUCCESS
                                 } else {
                                    BluetoothGatt.GATT_FAILURE
                                },
                                offset,
                                null
                            )
                        } catch (e: SecurityException) {
                            Log.e(
                                "MeshChatBLE",
                                "Permission missing while responding to descriptor write",
                                e
                            )
                        }
                    }
                }

            }



            override fun onServiceAdded(
                status: Int,
                service: BluetoothGattService
            ) {
                if(status == BluetoothGatt.GATT_SUCCESS) {
                    isServerRunning = true
                    serverStatus = "MeshChat GATT service registered"
                    Log.d("MeshChatBLE", "MeshChat service registered")
                } else {
                    serverStatus = "Service registration failed: $status"
                    Log.e(
                        "MeshChatBLE",
                        "Service registration failed: $status"
                    )
                }
            }



        }
    }









    Column {
        Text("MeshChat GATT Server")
        Text(serverStatus)
        Text(connectedDeviceName)
        Text(
            text = if(notificationsEnabled) {
                "Client notifications: Enabled"
            } else{
                "Client notifications: Disabled"
            }
        )
        Text("Received message: $receivedMessage")

        if(receivedMessages.isEmpty()) {
            Text("No messages received yet.")
        }



        receivedMessages.forEach { message ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = message.text,
                        modifier = Modifier.padding(12.dp)
                    )
                }


            }
        }


        OutlinedTextField(
            value = serverMessageText,
            onValueChange = {serverMessageText=it},
            label = {Text("Message to client")},
            modifier = Modifier.fillMaxWidth(),
            singleLine = true

        )

        Button(
            onClick = {
                val message = serverMessageText.trim()
                if(message.isNotEmpty()) {
                    sendNotification(message)
                    serverMessageText = ""
                }
            },
            enabled = connectedDevice != null && notificationsEnabled
        ) {
            Text("Send Message")
        }

        Button(

            onClick = {
                val hasConnectPermission =
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) == PackageManager.PERMISSION_GRANTED

                when {
                    !hasConnectPermission -> {
                        serverStatus = "Grant Bluetooth permissions first"
                    }


                    bluetoothAdapter == null -> {
                        serverStatus = "Bluetooth is not supported"
                    }


                    !bluetoothAdapter.isEnabled -> {
                        serverStatus = "Turn Bluetooth on first"
                    }


                    isServerRunning -> {
                        serverStatus = "GATT server is already running"
                    }

                    else -> {
                        try {
                            val server = bluetoothManager.openGattServer(
                                context,
                                gattServerCallback
                            )

                            if(server == null) {
                                serverStatus = "Unable to open GATT server"
                            } else {
                                gattServer = server


                                val service = BluetoothGattService(

                                    MESHCHAT_SERVICE_UUID,
                                    BluetoothGattService.SERVICE_TYPE_PRIMARY
                                )

                                service.addCharacteristic(rxCharacteristic)
                                service.addCharacteristic(txCharacteristic)


                                val accepted = server.addService(service)

                                if (accepted) {
                                    serverStatus = "Registering MeshChat service..."
                                } else {
                                    server.close()
                                    gattServer = null
                                    serverStatus = "Unable to register MeshChat service"
                                }
                            }
                        } catch (e: SecurityException) {
                            serverStatus = "Bluetooth permission missing"
                        }
                    }


                }
            },
            enabled = !isServerRunning
        ) {
            Text("Start GATT Server")
        }









    }




}