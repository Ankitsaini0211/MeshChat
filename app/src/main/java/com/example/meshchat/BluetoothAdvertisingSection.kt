package com.example.meshchat

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.util.UUID

internal val MESHCHAT_SERVICE_UUID: UUID =
    UUID.fromString("7b6e0001-7d4a-4c3b-9a2e-1f8b6c5d4a30")

internal val MESHCHAT_RX_UUID: UUID =
    UUID.fromString("7b6e0002-7d4a-4c3b-9a2e-1f8b6c5d4a30")

internal val MESHCHAT_TX_UUID: UUID =
    UUID.fromString("7b6e0003-7d4a-4c3b-9a2e-1f8b6c5d4a30")



@Composable
fun BluetoothAdvertisingSection() {

    val context = LocalContext.current

    val bluetoothManager = remember {
        context.getSystemService(Context.BLUETOOTH_SERVICE)
                as BluetoothManager
    }

    val bluetoothAdapter = bluetoothManager.adapter
    val advertiser = bluetoothAdapter?.bluetoothLeAdvertiser

    var status by remember {
        mutableStateOf("Ready to Advertise")
    }


    var isAdvertising by remember {
        mutableStateOf(false)
    }


    val advertiseCallback = remember {
        object : AdvertiseCallback() {
            override fun onStartSuccess(
                settingsInEffect: AdvertiseSettings?
            ) {
                isAdvertising = true
                status = " MeshChat is advertising"
            }

            override fun onStartFailure(errorCode: Int) {
                isAdvertising = false
                status = "Advertising failed: error $errorCode"
            }


        }
    }



    Column{
        Text("MeshChat Advertising")
        Text(status)

        Button(
            onClick = {
                val hasAdvertisePermission =
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_ADVERTISE
                        ) == PackageManager.PERMISSION_GRANTED


                val hasConnectPermission =
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) == PackageManager.PERMISSION_GRANTED

                when{
                    !hasAdvertisePermission || !hasConnectPermission -> {
                        status = "Grant Bluetooth permissions first"
                    }

                    bluetoothAdapter == null -> {
                        status = "Bluetooth is not supported"
                    }

                    !bluetoothAdapter.isEnabled -> {
                        status = "Turn Bluetooth on first"
                    }

                    advertiser == null -> {
                        status = "BLE advertising is unavailable"
                    }


                    else -> {
                        val settings = AdvertiseSettings.Builder()
                            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                            .setConnectable(true)
                            .setTimeout(0)
                            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                            .build()

                        val data = AdvertiseData.Builder()
                            .setIncludeDeviceName(false)
                            .addServiceUuid(
                                ParcelUuid(MESHCHAT_SERVICE_UUID)
                            )
                            .build()

                        try{
                            advertiser.startAdvertising(

                                settings,
                                data,
                                advertiseCallback

                            )
                            status = "Starting advertising..."
                        } catch (e: SecurityException){
                            status = "Bluetooth permission missing"
                        }

                    }

                }
            },
            enabled = !isAdvertising
        ) {
            Text("Start Advertising")
        }

        Button(
            onClick = {
                try {
                    advertiser?.stopAdvertising(advertiseCallback)
                    isAdvertising = false
                    status = "Advertising stopped"
                } catch (e: SecurityException) {
                    status = "Bluetooth permission missing"
                }
            },
            enabled = isAdvertising
        ) {
            Text("Stop Advertising")
        }

    }


}
