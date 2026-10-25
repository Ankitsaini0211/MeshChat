package com.example.meshchat

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.bluetooth.BluetoothGattCharacteristic
import android.widget.Button
import android.widget.Toast
import androidx.compose.material3.OutlinedTextField

@Composable
fun BluetoothGattClientSection() {

    val context = LocalContext.current

    val bluetoothManager = remember {
        context.getSystemService(
            Context.BLUETOOTH_SERVICE
        ) as BluetoothManager
    }

    val bluetoothAdapter = bluetoothManager.adapter

    var clientStatus by remember {
        mutableStateOf("Ready to find a MeshChat server")
    }

    var messageText by remember{
        mutableStateOf("")
    }

    var connectedDevice by remember {
        mutableStateOf<BluetoothDevice?>(null)
    }

    // FIX: This must be BluetoothGatt, not BluetoothDevice.
    var bluetoothGatt by remember {
        mutableStateOf<BluetoothGatt?>(null)
    }

    var discoveredDevices by remember {
        mutableStateOf<List<BluetoothDevice>>(emptyList())
    }

    var isScanning by remember {
        mutableStateOf(false)
    }

    val scanCallback = remember {
        object : ScanCallback() {

            override fun onScanResult(
                callbackType: Int,
                result: ScanResult
            ) {
                val device = result.device

                if (discoveredDevices.none {
                        it.address == device.address
                    }
                ) {
                    discoveredDevices =
                        discoveredDevices + device
                }
            }

            override fun onScanFailed(errorCode: Int) {
                isScanning = false
                clientStatus = "Scan failed: $errorCode"
            }
        }
    }

    val gattCallback = remember {
        object : BluetoothGattCallback() {

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int
            ) {
                when {
                    status != BluetoothGatt.GATT_SUCCESS -> {
                        clientStatus = "Connection failed: $status"
                        gatt.close()
                    }

                    newState == BluetoothProfile.STATE_CONNECTED -> {
                        clientStatus =
                            "Connected! Discovering services..."

                        val started = gatt.discoverServices()

                        if (!started) {
                            clientStatus =
                                "Unable to start service discovery"
                        }
                    }

                    newState == BluetoothProfile.STATE_DISCONNECTED -> {
                        clientStatus = "Disconnected"
                        connectedDevice = null
                        gatt.close()
                    }
                }
            }

            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service =
                        gatt.getService(MESHCHAT_SERVICE_UUID)

                    clientStatus = if (service != null) {
                        "Connected! MeshChat service found."
                    } else {
                        "Connected, but MeshChat service not found."
                    }
                } else {
                    clientStatus =
                        "Service discovery failed: $status"
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                if (characteristic.uuid == MESHCHAT_RX_UUID) {
                    clientStatus = if (status == BluetoothGatt.GATT_SUCCESS) {
                        "Message write successful!"
                    } else {
                        "Message write failed: $status"
                    }

                    android.util.Log.d(
                        "MeshChatBLE",
                        "Write callback: status=$status"
                    )
                }
            }







        }
    }


    fun sendMessage() {

        val gatt = bluetoothGatt

        if(gatt == null) {
            clientStatus = "Connect to a MeshChat server first"
            return
        }

        val service = gatt.getService(MESHCHAT_SERVICE_UUID)

        if(service == null) {
            clientStatus = "MeshChat service not found"
            return
        }

        val rxCharacteristic =
            service.getCharacteristic(MESHCHAT_RX_UUID)

        if(rxCharacteristic == null) {
            clientStatus = "RX characteristic not found"
            return
        }

        val message = messageText.trim()

        if(message.isEmpty()) {
            clientStatus = "Type a message first"
            return
        }



        try {
            val messageBytes = message.toByteArray(Charsets.UTF_8)

            val success = if (android.os.Build.VERSION.SDK_INT >= 33) {
                gatt.writeCharacteristic(
                    rxCharacteristic,
                    messageBytes,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                ) == android.bluetooth.BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                rxCharacteristic.writeType =
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

                @Suppress("DEPRECATION")
                rxCharacteristic.value = messageBytes

                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(rxCharacteristic)

            }

            clientStatus = if(success) {
                "Sending message..."
            } else {
                "Unable to start message write"
            }
        } catch (e: SecurityException) {
            clientStatus = "Bluetooth permission missing"
        }



    }






    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {

        Text("MeshChat GATT Client")
        Text(clientStatus)

        OutlinedTextField(
            value = messageText,
            onValueChange = { messageText = it },
            label = { Text("Message") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {sendMessage()},
            enabled = bluetoothGatt != null
        )

        {
            Text("Send")
        }




        Button(
            onClick = {
                val hasScanPermission =
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.BLUETOOTH_SCAN
                            ) == PackageManager.PERMISSION_GRANTED

                val hasConnectPermission =
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) == PackageManager.PERMISSION_GRANTED

                when {
                    !hasScanPermission || !hasConnectPermission -> {
                        clientStatus =
                            "Grant Bluetooth permissions first"
                    }

                    bluetoothAdapter == null -> {
                        clientStatus =
                            "Bluetooth is not supported"
                    }

                    !bluetoothAdapter.isEnabled -> {
                        clientStatus =
                            "Turn Bluetooth on first"
                    }

                    else -> {
                        clientStatus = "Bluetooth is ready"
                    }
                }
            }
        ) {
            Text("Check Bluetooth")
        }

        Button(
            onClick = {
                try {
                    val scanner =
                        bluetoothAdapter?.bluetoothLeScanner

                    if (scanner == null) {
                        clientStatus =
                            "Bluetooth scanner unavailable"
                    } else {
                        discoveredDevices = emptyList()

                        val filter = ScanFilter.Builder()
                            .setServiceUuid(
                                ParcelUuid(MESHCHAT_SERVICE_UUID)
                            )
                            .build()

                        val settings = ScanSettings.Builder()
                            .setScanMode(
                                ScanSettings.SCAN_MODE_LOW_LATENCY
                            )
                            .build()

                        scanner.startScan(
                            listOf(filter),
                            settings,
                            scanCallback
                        )

                        isScanning = true
                        clientStatus =
                            "Searching for MeshChat devices..."
                    }
                } catch (e: SecurityException) {
                    clientStatus =
                        "Bluetooth permission missing"
                }
            },
            enabled = !isScanning
        ) {
            Text("Find MeshChat Devices")
        }

        Button(
            onClick = {
                try {
                    bluetoothAdapter?.bluetoothLeScanner
                        ?.stopScan(scanCallback)

                    isScanning = false
                    clientStatus = "Scanning stopped"
                } catch (e: SecurityException) {
                    clientStatus =
                        "Bluetooth permission missing"
                }
            },
            enabled = isScanning
        ) {
            Text("Stop Scanning")
        }

        Text("Discovered devices: ${discoveredDevices.size}")

        discoveredDevices.forEach { device ->

            Text("Device: ${device.address}")

            Button(
                onClick = {
                    try {
                        val hasConnectPermission =
                            Build.VERSION.SDK_INT <
                                    Build.VERSION_CODES.S ||
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.BLUETOOTH_CONNECT
                                    ) == PackageManager.PERMISSION_GRANTED

                        if (!hasConnectPermission) {
                            clientStatus =
                                "Grant Bluetooth Connect permission"
                            return@Button
                        }

                        // Stop scanning before connecting.
                        if (isScanning) {
                            bluetoothAdapter?.bluetoothLeScanner
                                ?.stopScan(scanCallback)

                            isScanning = false
                        }

                        // Close any previous connection.
                        bluetoothGatt?.close()
                        bluetoothGatt = null

                        connectedDevice = device
                        clientStatus =
                            "Connecting to ${device.address}..."

                        bluetoothGatt = device.connectGatt(
                            context,
                            false,
                            gattCallback,
                            BluetoothDevice.TRANSPORT_LE
                        )

                        if (bluetoothGatt == null) {
                            clientStatus =
                                "Unable to start GATT connection"
                        }

                    } catch (e: SecurityException) {
                        clientStatus =
                            "Bluetooth permission missing"
                    }
                }
            ) {
                Text("Connect")
            }
        }
    }
}