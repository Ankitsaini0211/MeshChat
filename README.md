# MeshChat — Nearby BLE Offline Messaging

**MeshChat** is an Android application built with **Kotlin**, **Jetpack Compose**, and **Material 3**. It enables direct, off-grid peer-to-peer messaging between nearby Android devices using **Bluetooth Low Energy (BLE)** — without requiring internet access, cell service, user accounts, or central servers.

---

## 📱 Feature & Verification Status

> [!IMPORTANT]  
> **Testing Status**: Development, local build compilation, state management, and UI flows have been verified via Android Studio & Emulator tooling. Multi-message device-to-device communication between two physical phones is **In Progress / Awaiting Physical Two-Phone Verification**.

| Feature | Description | Status |
| :--- | :--- | :--- |
| **Material 3 UI & Navigation** | Polished Home, Device Discovery, and Chat screens | ✅ **Completed & Verified** |
| **BLE Runtime Permissions** | Auto-request permissions on Android 12+ (`BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT`) and legacy API 26–30 | ✅ **Completed & Verified** |
| **BLE Advertising** | Connectable BLE advertising broadcasting `MESHCHAT_SERVICE_UUID` with device name in Scan Response | ✅ **Completed & Verified** |
| **GATT Server Initialization** | Auto-registers `MESHCHAT_SERVICE_UUID` with RX (write) and TX (notify) characteristics + CCCD descriptor | ✅ **Completed & Verified** |
| **BLE Device Discovery** | Scans for nearby MeshChat nodes using service UUID filter and renders real `DeviceCard` entries | ✅ **Completed & Verified** |
| **BLE GATT Client Connection** | Connects to selected BLE peer, discovers services, and subscribes to TX CCCD notifications | ✅ **Completed & Verified** |
| **Serialized Message Write Queue** | Transmits client messages sequentially via `WRITE_TYPE_DEFAULT` with error recovery | 🔄 **In Progress (Awaiting Two-Phone Verification)** |
| **Bidirectional Messaging** | RX characteristic writes (Client → Server) & TX characteristic notifications (Server → Client) | 🔄 **In Progress (Awaiting Two-Phone Verification)** |
| **System Diagnostics UI** | Expandable card displaying live Bluetooth adapter, permission, server, advertiser, and connection metrics | ✅ **Completed & Verified** |

---

## 🛠️ Technology Stack

- **Language**: Kotlin (`2.2.10`)
- **UI Framework**: Jetpack Compose (`Compose BOM 2026.02.01`, Material 3)
- **Min SDK**: API 26 (Android 8.0)
- **Target & Compile SDK**: API 37
- **Build System**: Android Gradle Plugin (`AGP 9.4.1`)
- **Bluetooth Stack**: Native Android BLE APIs (`BluetoothGatt`, `BluetoothGattServer`, `BluetoothLeAdvertiser`, `BluetoothLeScanner`)

---

## 🏗️ BLE Architecture Overview

MeshChat operates as a **symmetrical peer-to-peer BLE node**. On launch, after permissions are granted:

1. **GATT Server**: Opens a local `BluetoothGattServer` listening on `MESHCHAT_SERVICE_UUID`:
   - **RX Characteristic (`7b6e0002-...`)**: Accepts write requests (`PROPERTY_WRITE`, `PROPERTY_WRITE_NO_RESPONSE`). On write request, returns `sendResponse(device, requestId, GATT_SUCCESS, offset, value)` with the received byte payload.
   - **TX Characteristic (`7b6e0003-...`)**: Configured with `PROPERTY_NOTIFY` and CCCD descriptor (`0x00002902-...`) for sending server-to-client notifications.
2. **BLE Advertiser**: Broadcasts connectable advertisement packets (`MESHCHAT_SERVICE_UUID` in primary payload, device name in Scan Response).
3. **GATT Client & Scanner**:
   - Scanning is activated when opening the **New Chat / Device Discovery** screen.
   - Connecting to a discovered node opens a `BluetoothGatt` link, discovers services, and writes `0x01, 0x00` to the remote CCCD descriptor to subscribe to incoming messages.
4. **Message Serializer Queue**: Outgoing client messages are queued in a FIFO `ArrayDeque` in `MeshBleManager` and processed sequentially with write-with-response confirmation.

### GATT UUID Constants

- **Service UUID**: `7b6e0001-7d4a-4c3b-9a2e-1f8b6c5d4a30`
- **RX Characteristic (Client → Server)**: `7b6e0002-7d4a-4c3b-9a2e-1f8b6c5d4a30`
- **TX Characteristic (Server → Client)**: `7b6e0003-7d4a-4c3b-9a2e-1f8b6c5d4a30`
- **CCCD Descriptor**: `00002902-0000-1000-8000-00805f9b34fb`

---

## 🔒 Permissions & Security Declarations

MeshChat declares the following permissions in `AndroidManifest.xml`:

- `android.permission.BLUETOOTH_SCAN` with `android:usesPermissionFlags="neverForLocation"` (Android 12+).
- `android.permission.BLUETOOTH_ADVERTISE` (Android 12+).
- `android.permission.BLUETOOTH_CONNECT` (Android 12+).
- `android.permission.ACCESS_FINE_LOCATION` with `android:maxSdkVersion="30"` (legacy fallback for API 26–30).

---

## 📋 Manual Two-Phone Testing Checklist

To verify end-to-end communication on physical hardware:

1. [ ] Install MeshChat on **Phone A** and **Phone B**.
2. [ ] Launch MeshChat on both phones and grant requested Bluetooth permissions.
3. [ ] Expand **BLE System Diagnostics** on both phones:
   - Verify `Bluetooth Adapter: Enabled`
   - Verify `Permissions: Granted`
   - Verify `GATT Server: Active (Listening)`
   - Verify `BLE Advertiser: Active (Broadcasting)`
4. [ ] On **Phone A**, tap **New Chat** → **Scan for Devices**.
5. [ ] Confirm **Phone B** appears in Phone A's discovered devices list.
6. [ ] Tap **Connect** on Phone A.
7. [ ] Confirm Phone A displays `● Connected` and opens the Chat screen.
8. [ ] Confirm Phone B's Home screen updates to display `Device connected: Phone A`.
9. [ ] Send **Message 1** from Phone A → Verify Phone B receives Message 1.
10. [ ] Send **Message 2, 3, 4, 5** sequentially from Phone A → Verify all messages arrive on Phone B in order.
11. [ ] Send **reply messages** from Phone B to Phone A → Verify Phone A receives notifications.

---

## ⚠️ Known Limitations & Scope

- **Direct Peer-to-Peer**: Communication occurs directly between two nearby devices within physical Bluetooth LE range (~10–30 meters).
- **Single Connection Focus**: Optimized for 1-on-1 direct messaging sessions. Multi-hop mesh routing or relay networking is not currently implemented.
- **Offline Storage**: Active conversation state is stored in memory during the application lifecycle.

---

## 🚀 Building & Running

1. Clone the repository:
   ```bash
   git clone https://github.com/Ankitsaini0211/MeshChat.git
   ```
2. Open the project in **Android Studio (Ladybug or newer)**.
3. Connect an Android device (Android 8.0 / API 26 or higher).
4. Run the `:app` configuration (`Shift + F10` or click **Run**).
