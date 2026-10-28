package com.example.meshchat

import android.bluetooth.le.ScanSettings
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.meshchat.ui.theme.MeshChatTheme
import androidx.compose.ui.unit.dp
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.Row
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlin.contracts.contract
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.util.packInts
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.mutableStateListOf
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.os.ParcelUuid
import java.util.UUID
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCbrt


data class Message (
    val text: String,
    val sender: String,
    val isMine: Boolean,
    val time : String
)



class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MeshChatTheme {
                BluetoothGattClientSection()
            }
        }
    }
}






@Composable
fun MessageBubble(message: Message) {


    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isMine) {
            Arrangement.End
        }else {
            Arrangement.Start
        }
    ) {

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if(message.isMine) {
                    MaterialTheme.colorScheme.primaryContainer
                } else{
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = message.sender,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = message.time,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }


    }


}





@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}


@Composable
fun MessageInputDemo() {

    var message by remember {
        mutableStateOf("")
    }

    var messages by remember {
        mutableStateOf(listOf<Message>())
    }


    Column{

        Row {
            OutlinedTextField(
                value = message,
                onValueChange = {newValue ->
                    message = newValue
                },
                label = {
                    Text("Type a message")
                }
            )

            Button(
                onClick = {
                    val newMessage = message.trim()

                    if(newMessage.isNotEmpty()) {

                        val currentTime = System.currentTimeMillis()

                        val formattedTime = formatTimestamp(currentTime)

                        val outgoingMessage = Message(
                            text = newMessage,
                            sender = "Me",
                            isMine = false,
                            time = formattedTime
                        )
                        messages = messages+outgoingMessage
                        message = ""
                    }
                }
            ) {
                Text("Send")
            }
        }



        LazyColumn {
            items(messages) { currentMessage ->
                MessageBubble(message=currentMessage)
            }
        }

    }


}

@Composable
fun BluetoothScannerSection() {

    val context = LocalContext.current

    val bluetoothManager = remember {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }

    val bluetoothAdapter = bluetoothManager.adapter
    val scanner = bluetoothAdapter?.bluetoothLeScanner

    var isScanning by remember {mutableStateOf(false)}
    var scanStatus by remember { mutableStateOf("Ready to scan") }
    var scanResultCount by remember { mutableStateOf(0) }


    val nearbyDevices = remember {
        mutableStateListOf<String>()
    }

    val scanCallback = remember {
        object : ScanCallback() {


            override fun onScanResult(
                callbackType : Int,
                result : ScanResult
            ){
                android.util.Log.d(
                    "MeshChatBLE",
                    "RESULT: ${result.device.address}, RSSI=${result.rssi}"
                )
                scanResultCount++
                val device = result.device

                val deviceName = try {
                    device.name ?:"Unnamed device"
                } catch (e: SecurityException) {
                    " Name unavailable"
                }

                val deviceAddress = device.address

                val deviceInfo = "$deviceName — $deviceAddress"

                if(deviceInfo !in nearbyDevices) {
                    nearbyDevices.add(deviceInfo)
                }

            }

            override fun onScanFailed(errorCode: Int) {
                android.util.Log.e(
                    "MeshChatBLE",
                    "SCAN FAILED: errorCode=$errorCode"
                )
                scanStatus = "Scan failed: error $errorCode"
                isScanning = false
            }
        }
    }

    DisposableEffect(isScanning, scanner) {
        if (!isScanning || scanner == null) {
            onDispose { }
        } else {
            try {
                val settings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build()

                scanner.startScan(null, settings, scanCallback)
                scanStatus = "Scanning for nearby BLE devices..."
            } catch (e: SecurityException) {
                scanStatus = "Bluetooth permission missing"
                isScanning = false
            } catch (e: IllegalStateException) {
                scanStatus = "Unable to start scanning"
                isScanning = false
            }

            onDispose {
                try {
                    scanner.stopScan(scanCallback)
                } catch (_: SecurityException) {
                    // Permission may have been revoked.
                }
            }
        }
    }


    Column {
        Text(
            text = "Nearby Bluetooth Devices",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))


        Text(text = scanStatus)
        Text("Scan results received: $scanResultCount")

        Row {
            Button(
                onClick = {
                    val hasPermission =
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

                        !hasPermission || ! hasConnectPermission ->
                            scanStatus = "Grant Bluetooth permissions first"

                        bluetoothAdapter == null ->
                            scanStatus = "Bluetooth is not supported"


                        !bluetoothAdapter.isEnabled ->
                            scanStatus = "Turn Bluetooth on first"

                        scanner == null ->
                            scanStatus = "BLE scanning is unavailable"

                        else -> {
                            nearbyDevices.clear()
                            scanResultCount = 0
                            scanStatus = "Starting scan..."
                            isScanning = true
                        }

                    }

                },
                enabled = !isScanning

            ) {
                Text("Start Scan")
            }


            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    isScanning = false
                    scanStatus = "Scan stopped"

                },
                enabled = isScanning
            ) {
                Text("stop scan")
            }


        }

        Spacer(modifier = Modifier.height(8.dp))


        Column {
            nearbyDevices.forEach { device ->
                Text(
                    text = device,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
        }


    }

}




@Composable
fun BluetoothPermissionSection() {
    val context = LocalContext.current

    //choose permissions based on android version.
    val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else{
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    // check whether permission has already been granted

    var permissionStatus by remember {
        mutableStateOf(
            if(requiredPermissions.all{
                ContextCompat.checkSelfPermission(
                        context,
                        it
                        ) == PackageManager.PERMISSION_GRANTED
                }) {
                "Bluetooth permissions granted"
            } else{
                "Bluetooth permissions not granted"
            }
        )
    }



    // Android displays the permission dialog when this launcher runs.

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionStatus = if (results.values.all { it }) {
            "Bluetooth permissions granted"
        } else {
            "Some permissions were denied"
        }
    }

    Column {
        Text(text = permissionStatus)

        Button(
            onClick = {
                permissionLauncher.launch(requiredPermissions)
            }
        ) {
            Text("Grant Bluetooth Permissions")
        }
    }





}




@Composable
fun BluetoothStatusSection() {




    val context = LocalContext.current

    val bluetoothManager = remember {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }

    val bluetoothAdapter = bluetoothManager.adapter

    val hasBluetoothPermission =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED

    // Changing this value triggers recomposition.
    var bluetoothState by remember {
        mutableIntStateOf(
            bluetoothAdapter?.state ?: BluetoothAdapter.STATE_OFF
        )
    }

    DisposableEffect(bluetoothAdapter) {
        if (bluetoothAdapter == null) {
            onDispose { }
        } else {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context?,
                    intent: Intent?
                ) {
                    if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                        bluetoothState = intent.getIntExtra(
                            BluetoothAdapter.EXTRA_STATE,
                            BluetoothAdapter.ERROR
                        )
                    }
                }
            }

            val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)

            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )

            onDispose {
                context.unregisterReceiver(receiver)
            }
        }
    }

    val status = when {
        bluetoothAdapter == null -> "Bluetooth is not supported"
        !hasBluetoothPermission -> "Bluetooth permission is missing"
        bluetoothState == BluetoothAdapter.STATE_ON -> "Bluetooth is enabled"
        bluetoothState == BluetoothAdapter.STATE_OFF -> "Bluetooth is disabled"
        bluetoothState == BluetoothAdapter.STATE_TURNING_ON -> "Bluetooth is turning on..."
        bluetoothState == BluetoothAdapter.STATE_TURNING_OFF -> "Bluetooth is turning off..."
        else -> "Bluetooth state unknown"
    }

    Text(text = status)
}




fun formatTimestamp(timestamp: Long) : String {
    val instant = Instant.ofEpochMilli(timestamp)
    val formatter = DateTimeFormatter.ofPattern("hh:mm a")
        .withZone(ZoneId.systemDefault())

    return formatter.format(instant)
}



@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MeshChatTheme {
        Greeting("Android")
    }
}