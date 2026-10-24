package com.example.meshchat

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


    var gattServer by remember {
        mutableStateOf<BluetoothGattServer?>(null)
    }

    val rxCharacteristic = remember {
        BluetoothGattCharacteristic(
            MESHCHAT_RX_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
    }


    val txCharacteristic = remember {
        BluetoothGattCharacteristic(
            MESHCHAT_TX_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
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
                        connectedDeviceName = "Phone connected"
                        Log.d(
                            "MeshChatBLE",
                            "Client connected: ${device.address}"
                        )
                    }

                    newState == BluetoothProfile.STATE_DISCONNECTED -> {
                        connectedDeviceName = "No phone connected"
                        Log.d(
                            "MeshChatBLE",
                            "Client disconnected: ${device.address}"
                        )
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