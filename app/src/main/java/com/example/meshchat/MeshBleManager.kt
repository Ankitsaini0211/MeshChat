package com.example.meshchat

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.ArrayDeque
import java.util.UUID

class MeshBleManager(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    val cccUuid: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    val requiredPermissions: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    // ─── Public state (UI reads these) ──────────────────────────────────────
    var clientStatus: String = "Initializing MeshChat BLE…"
        private set

    var lastLogMessage: String? = null
        private set

    var isScanning: Boolean = false
        private set

    var isAdvertising: Boolean = false
        private set

    var isGattServerRunning: Boolean = false
        private set

    var serverNotificationsEnabled: Boolean = false
        private set

    var discoveredDevices: List<BluetoothDevice> = emptyList()
        private set

    var messages: List<ChatMessage> = emptyList()
        private set

    var connectedClientDevice: BluetoothDevice? = null
        private set

    var connectedServerDevice: BluetoothDevice? = null
        private set

    var bluetoothGatt: BluetoothGatt? = null
        private set

    var gattServer: BluetoothGattServer? = null
        private set

    // ─── FIX #1: explicit connection state ─────────────────────────────────
    // Do NOT derive "connected" from clientStatus text — it gets overwritten
    // every send ("Message write successful!"), which is what was disabling
    // the Send button after the first message.
    private var clientConnected: Boolean = false
    private var clientNotificationsReady: Boolean = false

    val isConnected: Boolean
        get() = (clientConnected && clientNotificationsReady) ||
                (isGattServerRunning && connectedServerDevice != null && serverNotificationsEnabled)

    // ─── Write queue state ──────────────────────────────────────────────────
    private val outgoingQueue = ArrayDeque<String>()
    private var isWriteInFlight: Boolean = false
    private var isAwaitingNoResponseCallback: Boolean = false

    private val writeWatchdog = Runnable {
        if (isWriteInFlight) {
            logEvent("Write watchdog fired — no callback received", isError = true)
            isWriteInFlight = false
            if (outgoingQueue.isNotEmpty()) outgoingQueue.removeFirst()
            clientStatus = "Message write timed out"
            notifyStateChanged()
            processNextWriteInQueue()
        }
    }

    var onStateChanged: (() -> Unit)? = null

    private fun notifyStateChanged() {
        mainHandler.post { onStateChanged?.invoke() }
    }

    fun logEvent(message: String, isError: Boolean = false) {
        if (isError) Log.e("MeshChatBLE", message) else Log.d("MeshChatBLE", message)
        lastLogMessage = message
        notifyStateChanged()
    }

    fun checkPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    // ─── GATT characteristics ──────────────────────────────────────────────
    val rxCharacteristic = BluetoothGattCharacteristic(
        MESHCHAT_RX_UUID,
        BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
        BluetoothGattCharacteristic.PERMISSION_WRITE
    )

    val cccdDescriptor = BluetoothGattDescriptor(
        cccUuid,
        BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
    )

    val txCharacteristic = BluetoothGattCharacteristic(
        MESHCHAT_TX_UUID,
        BluetoothGattCharacteristic.PROPERTY_NOTIFY,
        BluetoothGattCharacteristic.PERMISSION_READ
    ).apply {
        addDescriptor(cccdDescriptor)
    }

    // ─── GATT Server callback ──────────────────────────────────────────────
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(
            device: BluetoothDevice,
            status: Int,
            newState: Int
        ) {
            when {
                status != BluetoothGatt.GATT_SUCCESS -> {
                    logEvent("GATT Server connection error for ${device.address}: status=$status", isError = true)
                }
                newState == BluetoothProfile.STATE_CONNECTED -> {
                    connectedServerDevice = device
                    clientStatus = "Device connected: ${getDeviceName(device)}"
                    logEvent("Peer connected to GATT Server: ${device.address}")
                    notifyStateChanged()
                }
                newState == BluetoothProfile.STATE_DISCONNECTED -> {
                    connectedServerDevice = null
                    serverNotificationsEnabled = false
                    clientStatus = "Device disconnected"
                    logEvent("Peer disconnected from GATT Server: ${device.address}")
                    notifyStateChanged()
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
            logEvent("GATT Server write req: uuid=${characteristic.uuid}, reqId=$requestId, len=${value.size}")

            if (characteristic.uuid == MESHCHAT_RX_UUID) {
                val receivedText = value.toString(Charsets.UTF_8)
                mainHandler.post {
                    messages = messages + ChatMessage(text = receivedText, isSent = false)
                    clientStatus = "Message received from ${getDeviceName(device)}"
                    notifyStateChanged()
                }
                logEvent("GATT Server received RX write: $receivedText")
            } else {
                logEvent("GATT Server write for unknown char: ${characteristic.uuid}", isError = true)
            }

            if (responseNeeded) {
                try {
                    // FIX #6: write response payload should be empty, not the received value
                    val sent = gattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        offset,
                        null
                    ) ?: false
                    logEvent("sendResponse result: $sent for reqId=$requestId")
                } catch (e: SecurityException) {
                    logEvent("Permission missing sending write response", isError = true)
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
            if (descriptor.uuid == cccUuid) {
                // Accept both ENABLE_NOTIFICATION_VALUE (0x01,0x00) and
                // ENABLE_INDICATION_VALUE (0x02,0x00)
                serverNotificationsEnabled =
                    value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ||
                            value.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                logEvent("GATT Server CCCD write: notificationsEnabled=$serverNotificationsEnabled")

                if (responseNeeded) {
                    try {
                        val sent = gattServer?.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            offset,
                            null
                        ) ?: false
                        logEvent("CCCD sendResponse result: $sent for reqId=$requestId")
                    } catch (e: SecurityException) {
                        logEvent("Permission missing sending CCCD response", isError = true)
                    }
                }
                notifyStateChanged()
            } else if (responseNeeded) {
                try {
                    gattServer?.sendResponse(
                        device, requestId, BluetoothGatt.GATT_FAILURE, offset, null
                    )
                } catch (_: SecurityException) { }
            }
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                isGattServerRunning = true
                logEvent("MeshChat GATT service added successfully")
            } else {
                isGattServerRunning = false
                logEvent("GATT service registration failed: status=$status", isError = true)
            }
            notifyStateChanged()
        }
    }

    // ─── Advertiser callback ───────────────────────────────────────────────
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            isAdvertising = true
            logEvent("BLE Advertising started successfully")
            notifyStateChanged()
        }

        override fun onStartFailure(errorCode: Int) {
            isAdvertising = false
            logEvent("BLE Advertising failed: errorCode=$errorCode", isError = true)
            notifyStateChanged()
        }
    }

    fun startGattServer() {
        if (isGattServerRunning || bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return
        if (!checkPermissions()) return

        try {
            val server = bluetoothManager?.openGattServer(context, gattServerCallback)
            if (server != null) {
                gattServer = server
                val service = BluetoothGattService(
                    MESHCHAT_SERVICE_UUID,
                    BluetoothGattService.SERVICE_TYPE_PRIMARY
                )
                service.addCharacteristic(rxCharacteristic)
                service.addCharacteristic(txCharacteristic)
                val added = server.addService(service)
                logEvent("openGattServer succeeded. addService initiated: $added")
            } else {
                logEvent("openGattServer returned null", isError = true)
            }
        } catch (e: SecurityException) {
            logEvent("Permission missing when starting GATT server", isError = true)
        }
    }

    fun startAdvertising() {
        if (isAdvertising || bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return
        if (!checkPermissions()) return

        val advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            logEvent("BLE Advertiser unavailable on this device", isError = true)
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(MESHCHAT_SERVICE_UUID))
            .build()

        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        try {
            advertiser.startAdvertising(settings, data, scanResponse, advertiseCallback)
            logEvent("Initiated BLE Advertising...")
        } catch (e: SecurityException) {
            logEvent("Permission missing starting BLE advertising", isError = true)
        }
    }

    // ─── Scanner ───────────────────────────────────────────────────────────
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (discoveredDevices.none { it.address == device.address }) {
                discoveredDevices = discoveredDevices + device
                logEvent("Discovered MeshChat device: ${getDeviceName(device)} (${device.address})")
                notifyStateChanged()
            }
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            clientStatus = "Scan failed: error $errorCode"
            logEvent("BLE Scan failed with errorCode=$errorCode", isError = true)
            notifyStateChanged()
        }
    }

    fun startScanning() {
        if (!checkPermissions()) return
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            clientStatus = "Turn Bluetooth on first"
            notifyStateChanged()
            return
        }

        val scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            clientStatus = "Bluetooth scanner unavailable"
            notifyStateChanged()
            return
        }

        try {
            discoveredDevices = emptyList()
            val filter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(MESHCHAT_SERVICE_UUID))
                .build()

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            scanner.startScan(listOf(filter), settings, scanCallback)
            isScanning = true
            clientStatus = "Searching for MeshChat devices..."
            logEvent("BLE Scanner started successfully")
            notifyStateChanged()
        } catch (e: SecurityException) {
            clientStatus = "Bluetooth permission missing"
            notifyStateChanged()
        }
    }

    fun stopScanning() {
        if (!isScanning) return
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            isScanning = false
            clientStatus = "Scanning stopped"
            logEvent("BLE Scanner stopped")
            notifyStateChanged()
        } catch (e: SecurityException) {
            logEvent("Permission missing stopping scanner", isError = true)
        }
    }

    // ─── GATT Client callback ──────────────────────────────────────────────
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when {
                status != BluetoothGatt.GATT_SUCCESS -> {
                    mainHandler.post {
                        clientConnected = false
                        clientNotificationsReady = false
                        connectedClientDevice = null
                        isWriteInFlight = false
                        isAwaitingNoResponseCallback = false
                        mainHandler.removeCallbacks(writeWatchdog)
                        outgoingQueue.clear()
                        clientStatus = "Connection error: status $status"
                        notifyStateChanged()
                    }
                    logEvent("GATT Client connection error: status=$status", isError = true)
                    try { gatt.close() } catch (_: SecurityException) {}
                }

                newState == BluetoothProfile.STATE_CONNECTED -> {
                    mainHandler.post {
                        clientConnected = true
                        clientNotificationsReady = false
                        connectedClientDevice = gatt.device
                        clientStatus = "Connected! Discovering services..."
                        notifyStateChanged()
                    }
                    logEvent("GATT Client connected to ${gatt.device.address}. Discovering services...")
                    val started = try { gatt.discoverServices() } catch (_: SecurityException) { false }
                    if (!started) {
                        mainHandler.post {
                            clientStatus = "Unable to start service discovery"
                            notifyStateChanged()
                        }
                    }
                }

                newState == BluetoothProfile.STATE_DISCONNECTED -> {
                    mainHandler.post {
                        clientConnected = false
                        clientNotificationsReady = false
                        connectedClientDevice = null
                        isWriteInFlight = false
                        isAwaitingNoResponseCallback = false
                        mainHandler.removeCallbacks(writeWatchdog)
                        outgoingQueue.clear()
                        clientStatus = "Disconnected"
                        notifyStateChanged()
                    }
                    logEvent("GATT Client disconnected from ${gatt.device.address}")
                    try { gatt.close() } catch (_: SecurityException) {}
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                clientStatus = "Service discovery failed: status $status"
                logEvent("GATT Service discovery failed: $status", isError = true)
                notifyStateChanged()
                return
            }

            try {
                val service = gatt.getService(MESHCHAT_SERVICE_UUID)
                if (service == null) {
                    clientStatus = "MeshChat service not found"
                    logEvent("MeshChat service UUID not found on server", isError = true)
                    notifyStateChanged()
                    return
                }

                val txChar = service.getCharacteristic(MESHCHAT_TX_UUID)
                if (txChar == null) {
                    clientStatus = "TX characteristic not found"
                    logEvent("TX characteristic UUID not found on server", isError = true)
                    notifyStateChanged()
                    return
                }

                val notificationEnabled = gatt.setCharacteristicNotification(txChar, true)
                if (!notificationEnabled) {
                    clientStatus = "Failed to enable local notifications"
                    logEvent("Failed setCharacteristicNotification", isError = true)
                    notifyStateChanged()
                    return
                }

                val cccd = txChar.getDescriptor(cccUuid)
                if (cccd == null) {
                    clientStatus = "CCCD descriptor not found"
                    logEvent("CCCD descriptor not found on TX char", isError = true)
                    notifyStateChanged()
                    return
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val res = gatt.writeDescriptor(
                        cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    )
                    clientStatus = if (res == BluetoothStatusCodes.SUCCESS)
                        "Subscribing to notifications…" else "CCCD write failed: $res"
                } else {
                    @Suppress("DEPRECATION")
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    val started = gatt.writeDescriptor(cccd)
                    clientStatus = if (started) "Subscribing to notifications…"
                    else "Failed to start CCCD write"
                }
                notifyStateChanged()
            } catch (e: SecurityException) {
                clientStatus = "Bluetooth permission missing"
                notifyStateChanged()
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (descriptor.uuid != cccUuid) return
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // FIX #1: real flag, not text matching
                mainHandler.post {
                    clientNotificationsReady = true
                    clientStatus = "Connected! Notifications enabled"
                    notifyStateChanged()
                }
                logEvent("CCCD notification subscription successful!")
                // If messages were queued before subscription completed, start flushing
                mainHandler.post { processNextWriteInQueue() }
            } else {
                mainHandler.post {
                    clientNotificationsReady = false
                    clientStatus = "Notification subscription failed: $status"
                    notifyStateChanged()
                }
                logEvent("CCCD write failed with status $status", isError = true)
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == MESHCHAT_TX_UUID) {
                characteristic.value?.let { handleIncomingMessage(it) }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == MESHCHAT_TX_UUID) {
                handleIncomingMessage(value)
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (characteristic.uuid != MESHCHAT_RX_UUID) return
            mainHandler.post {
                // FIX #2: if we just fired a NO_RESPONSE write, any callback that
                // arrives within the settle window belongs to that write — ignore it,
                // the scheduled timeout will advance the queue.
                if (isAwaitingNoResponseCallback) {
                    logEvent("Ignoring stale NO_RESPONSE onCharacteristicWrite callback")
                    return@post
                }
                if (!isWriteInFlight) {
                    logEvent("Ignoring onCharacteristicWrite with no write in flight")
                    return@post
                }

                mainHandler.removeCallbacks(writeWatchdog)
                val sentText = if (outgoingQueue.isNotEmpty()) outgoingQueue.removeFirst() else null

                if (status == BluetoothGatt.GATT_SUCCESS && sentText != null) {
                    messages = messages + ChatMessage(text = sentText, isSent = true)
                    clientStatus = "Message sent!"
                    logEvent("Write confirmed: '$sentText'")
                } else {
                    // FIX #3: don't silently drop; log the failed text
                    clientStatus = "Message write failed: $status"
                    logEvent("Write failed for '$sentText' (status=$status)", isError = true)
                }

                isWriteInFlight = false
                notifyStateChanged()
                processNextWriteInQueue()
            }
        }
    }

    private fun handleIncomingMessage(value: ByteArray) {
        val text = value.toString(Charsets.UTF_8)
        mainHandler.post {
            messages = messages + ChatMessage(text = text, isSent = false)
            clientStatus = "Message received!"
            notifyStateChanged()
        }
        logEvent("Client received notification payload: '$text'")
    }

    fun connectToDevice(device: BluetoothDevice) {
        if (!checkPermissions()) return
        if (isScanning) stopScanning()

        try {
            bluetoothGatt?.close()
            bluetoothGatt = null

            clientConnected = false
            clientNotificationsReady = false
            isWriteInFlight = false
            isAwaitingNoResponseCallback = false
            mainHandler.removeCallbacks(writeWatchdog)
            outgoingQueue.clear()

            connectedClientDevice = device
            clientStatus = "Connecting to ${getDeviceName(device)}…"
            logEvent("Connecting GATT Client to ${device.address}")
            notifyStateChanged()

            bluetoothGatt = device.connectGatt(
                context,
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE
            )

            if (bluetoothGatt == null) {
                clientStatus = "Unable to start GATT connection"
                notifyStateChanged()
            }
        } catch (e: SecurityException) {
            clientStatus = "Bluetooth permission missing"
            notifyStateChanged()
        }
    }

    // ─── Message queue ─────────────────────────────────────────────────────
    fun enqueueMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        // FIX #7: queue mutation must be serialized with onCharacteristicWrite
        mainHandler.post {
            outgoingQueue.addLast(trimmed)
            logEvent("enqueueMessage: added '$trimmed' (queue=${outgoingQueue.size}, inFlight=$isWriteInFlight)")
            notifyStateChanged()
            processNextWriteInQueue()
        }
    }

    private fun processNextWriteInQueue() {
        if (isWriteInFlight || isAwaitingNoResponseCallback || outgoingQueue.isEmpty()) return

        val textToSend = outgoingQueue.peekFirst() ?: return
        val bytes = textToSend.toByteArray(Charsets.UTF_8)

        // ── Path 1: client GATT link ──
        val gatt = bluetoothGatt
        if (gatt != null && clientConnected && clientNotificationsReady) {
            val rxChar = gatt.getService(MESHCHAT_SERVICE_UUID)
                ?.getCharacteristic(MESHCHAT_RX_UUID)
            if (rxChar != null) {
                val defaultCode = writeChar(gatt, rxChar, bytes,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)

                if (defaultCode == BluetoothStatusCodes.SUCCESS) {
                    isWriteInFlight = true
                    clientStatus = "Sending message…"
                    logEvent("DEFAULT write initiated for: '$textToSend'")
                    notifyStateChanged()
                    mainHandler.removeCallbacks(writeWatchdog)
                    mainHandler.postDelayed(writeWatchdog, 5_000L)   // FIX #5
                    return
                }

                logEvent("DEFAULT write failed (code=$defaultCode), trying NO_RESPONSE", isError = true)
                val noRespCode = writeChar(gatt, rxChar, bytes,
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)

                if (noRespCode == BluetoothStatusCodes.SUCCESS) {
                    // FIX #2: complete immediately, then guard against a late callback
                    val sentText = outgoingQueue.removeFirst()
                    messages = messages + ChatMessage(text = sentText, isSent = true)
                    clientStatus = "Message sent!"
                    logEvent("NO_RESPONSE write succeeded for: '$sentText'")
                    isAwaitingNoResponseCallback = true
                    notifyStateChanged()
                    mainHandler.postDelayed({
                        isAwaitingNoResponseCallback = false
                        processNextWriteInQueue()
                    }, 100L)
                    return
                }

                logEvent("NO_RESPONSE write also failed (code=$noRespCode)", isError = true)
                outgoingQueue.removeFirst()
                clientStatus = "Message write failed (code $noRespCode)"
                notifyStateChanged()
                processNextWriteInQueue()
                return
            }
        }

        // ── Path 2: server notifications ──
        val server = gattServer
        val sDevice = connectedServerDevice
        if (server != null && sDevice != null && serverNotificationsEnabled) {
            try {
                val initiated = if (Build.VERSION.SDK_INT >= 33) {
                    server.notifyCharacteristicChanged(
                        sDevice, txCharacteristic, false, bytes
                    ) == BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    txCharacteristic.value = bytes
                    @Suppress("DEPRECATION")
                    server.notifyCharacteristicChanged(sDevice, txCharacteristic, false)
                }

                val sentText = outgoingQueue.removeFirst()
                if (initiated) {
                    messages = messages + ChatMessage(text = sentText, isSent = true)
                    clientStatus = "Message sent!"
                    logEvent("Server notification sent: '$sentText'")
                } else {
                    clientStatus = "Failed to send notification"
                    logEvent("Server notify failed for: '$sentText'", isError = true)
                }
                notifyStateChanged()
                processNextWriteInQueue()
            } catch (e: SecurityException) {
                outgoingQueue.removeFirst()
                logEvent("Permission missing sending server notification", isError = true)
                notifyStateChanged()
                processNextWriteInQueue()
            }
            return
        }

        // ── Path 3: no path available — fail the message instead of stranding it ──
        // FIX #4
        logEvent("No connected peer to send message", isError = true)
        outgoingQueue.removeFirst()
        clientStatus = "Not connected"
        notifyStateChanged()
    }

    private fun writeChar(
        gatt: BluetoothGatt,
        char: BluetoothGattCharacteristic,
        bytes: ByteArray,
        writeType: Int
    ): Int {
        return try {
            if (Build.VERSION.SDK_INT >= 33) {
                gatt.writeCharacteristic(char, bytes, writeType)
            } else {
                @Suppress("DEPRECATION")
                char.writeType = writeType
                @Suppress("DEPRECATION")
                char.value = bytes
                @Suppress("DEPRECATION")
                if (gatt.writeCharacteristic(char)) BluetoothStatusCodes.SUCCESS
                else BluetoothStatusCodes.ERROR_UNKNOWN
            }
        } catch (e: SecurityException) {
            logEvent("Permission missing during writeCharacteristic", isError = true)
            BluetoothStatusCodes.ERROR_MISSING_BLUETOOTH_CONNECT_PERMISSION
        }
    }

    fun getDeviceName(device: BluetoothDevice?): String {
        if (device == null) return "Nearby device"
        return try {
            device.name?.ifBlank { null } ?: device.address ?: "Nearby device"
        } catch (e: SecurityException) {
            device.address ?: "Nearby device"
        }
    }

    val activePeerName: String
        get() = getDeviceName(connectedClientDevice ?: connectedServerDevice)
}