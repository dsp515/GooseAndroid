# 🦆 Goose Android

> **Android port of the [Goose](https://github.com/b-nnett/goose) WHOOP 5.0 open-source client** — a production-ready BLE health data pipeline backed by the original Rust core.

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%208.0+-4ADE80?style=for-the-badge&logo=android" />
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin" />
  <img src="https://img.shields.io/badge/Core-Rust%20FFI-CE422B?style=for-the-badge&logo=rust" />
  <img src="https://img.shields.io/badge/BLE-WHOOP%205.0-00C4B3?style=for-the-badge" />
  <img src="https://img.shields.io/badge/Build-Passing-4ADE80?style=for-the-badge" />
</p>

---

## 📖 Overview

Goose Android is a **full feature-parity Android port** of the iOS Swift app from the [b-nnett/goose](https://github.com/b-nnett/goose) repository. It connects to your **WHOOP 5.0** device over BLE and runs the same Rust-backed health algorithms to compute Recovery, Sleep, and Strain scores — without relying on the WHOOP app or WHOOP cloud.

### Key Features

| Feature | Status |
|---|---|
| BLE scan & connect to WHOOP 5.0 (fd4b / 61080 service UUIDs) | ✅ |
| WHOOP 5.0 Client Hello handshake | ✅ |
| Live heart rate (standard GATT 2A37 + Rust K10 parser) | ✅ |
| GOOSE & GEN4 device family detection | ✅ |
| Raw BLE frame buffering & 30s batch flush | ✅ |
| Rust FFI bridge (`goose_bridge_handle_json`) | ✅ |
| SQLite capture sessions via Rust core | ✅ |
| Recovery / Sleep / Strain score computation | ✅ (requires `.so`) |
| Historical data sync (range poll + packet streaming) | ✅ |
| Battery & firmware info reads | ✅ |
| Full Material 3 UI — Connect, Health, More screens | ✅ |
| Android 12+ runtime Bluetooth permission flow | ✅ |

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────┐
│                   Android App                       │
│  ┌──────────────┐   ┌────────────────────────────┐  │
│  │ GooseViewModel│──▶│    GooseBLEManager         │  │
│  │  (LiveData)  │   │  (GATT + Scan + Commands)  │  │
│  └──────┬───────┘   └────────────────────────────┘  │
│         │                                            │
│  ┌──────▼───────┐   ┌────────────────────────────┐  │
│  │GooseDataStore│──▶│    GooseRustBridge (JNI)   │  │
│  │  (SQLite)    │   │  goose_bridge_handle_json  │  │
│  └──────────────┘   └────────────────────────────┘  │
│                              │                       │
│                    ┌─────────▼──────────┐           │
│                    │  libgoose_core.so  │           │
│                    │  (Rust core — arm64│           │
│                    └────────────────────┘           │
└─────────────────────────────────────────────────────┘
```

### Data Flow

```
WHOOP BLE notification
        │
        ▼
GooseBLEManager.handleNotification()
        │
        ├──▶ GATT 2A37 Heart Rate → live HR update
        │
        └──▶ BLENotificationEvent → GooseViewModel
                        │
                        ├──▶ CapturedFrame buffer
                        │
                        └──▶ Rust parse_frame_hex → live K10 HR
                                      │
                        Every 30s ──▶ capture.import_frame_batch
                                      │
                                      └──▶ metrics.recovery/sleep/strain
                                                    │
                                                    └──▶ HealthSummary → UI
```

---

## 🚀 Quick Start

### Requirements

- Android Studio Hedgehog or newer
- Android device with API 26+ (Android 8.0)
- Physical WHOOP 5.0 device (BLE scanning doesn't work on emulators)
- `libgoose_core.so` for live score computation (see below)

### Build & Install

```bash
# Clone this repo
git clone https://github.com/dsp515/GooseAndroid.git
cd GooseAndroid

# Build debug APK
./gradlew assembleDebug

# Install to connected device
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or just **[download the latest APK](app/build/outputs/apk/debug/app-debug.apk)** directly.

### Adding the Rust Native Library

The app runs in **stub mode** without `libgoose_core.so` (BLE and UI work, scores show 0). To enable full score computation:

1. Build the Rust core from [b-nnett/goose](https://github.com/b-nnett/goose) for Android:
   ```bash
   # From the original repo Scripts/
   ./build_android_rust.sh   # or build_android_rust.ps1 on Windows
   ```
2. Copy the output:
   ```
   app/src/main/jniLibs/arm64-v8a/libgoose_core.so
   ```
3. Rebuild: `./gradlew assembleDebug`

---

## 📱 Screenshots

| Connect Screen | Health Dashboard | More / Logs |
|---|---|---|
| Scan for WHOOP 5.0 | Recovery · Sleep · Strain | Device info + BLE logs |

---

## 📦 APK Download

The pre-built debug APK is included in this repository:

**[`app/build/outputs/apk/debug/app-debug.apk`](app/build/outputs/apk/debug/app-debug.apk)**

> ⚠️ This is a debug build. It runs in stub mode — BLE connection and all UI screens work fully, but health scores require the Rust `.so` (see above).

---

## 🔧 Project Structure

```
GooseAndroid/
├── app/src/main/java/com/goose/android/
│   ├── MainActivity.kt            # Entry point, permission flow
│   ├── GooseViewModel.kt          # Central event hub, BLE ↔ Rust wiring
│   ├── GooseApplication.kt        # App lifecycle
│   ├── ble/
│   │   ├── GooseBLEManager.kt     # BLE scan, connect, GATT callbacks
│   │   ├── WhoopUUIDs.kt          # Service & characteristic UUIDs
│   │   └── GooseBLEService.kt     # Background BLE service wrapper
│   ├── rust/
│   │   └── GooseRustBridge.kt     # JNI bridge (JSON over FFI)
│   ├── store/
│   │   └── GooseDataStore.kt      # SQLite session + frame import + metrics
│   ├── data/
│   │   └── HealthModels.kt        # Recovery, Sleep, Strain, Vitals models
│   └── ui/
│       ├── screens/
│       │   ├── ConnectScreen.kt   # BLE device list + connect UI
│       │   ├── HealthScreen.kt    # Recovery, Sleep, Strain dashboard
│       │   └── MoreScreen.kt      # Device info, logs, settings
│       └── components/
│           └── GooseComponents.kt # Shared UI components
├── app/src/main/jniLibs/
│   └── arm64-v8a/                 # Place libgoose_core.so here
└── app/build/outputs/apk/debug/
    └── app-debug.apk              # Pre-built APK
```

---

## 🦀 Bridge API Reference

All Rust communication uses JSON over `goose_bridge_handle_json()`:

```kotlin
// Request schema
{
  "schema": "goose.bridge.request.v1",
  "request_id": "...",
  "method": "capture.import_frame_batch",
  "args": { ... }
}
```

| Method | Args | Purpose |
|---|---|---|
| `storage.check` | `database_path`, `self_test` | Initialize SQLite |
| `settings.apply_default_algorithm_preferences` | `database_path`, `scope` | Register algorithms |
| `capture.start_session` | `session_id`, `source`, `started_at_unix_ms`, `device_model` | Open capture window |
| `capture.import_frame_batch` | `database_path`, `frames[]` | Persist raw BLE frames |
| `capture.finish_session` | `session_id`, `ended_at_unix_ms`, `frame_count` | Close capture window |
| `protocol.parse_frame_hex` | `frame_hex`, `device_type` | Live HR from K10 packets |
| `metrics.recovery_score_from_features` | `database_path`, `start`, `end` | Recovery score 0-100 |
| `metrics.sleep_score_from_features` | `database_path`, `start`, `end` | Sleep score 0-100 |
| `metrics.strain_score_from_features` | `database_path`, `start`, `end` | Strain score 0-21 |

---

## 📜 Attribution & Credits

This project is an **Android port** of the original iOS [Goose](https://github.com/b-nnett/goose) project by [@b-nnett](https://github.com/b-nnett).

- **Original repository**: https://github.com/b-nnett/goose
- **BLE protocol reference**: [openwhoop](https://github.com/OpenWhoop/openwhoop) (referenced by Goose Rust core)
- **Rust core**: All health algorithms, SQLite schema, and FFI bridge are from the original Goose Rust core — no modifications made to Rust source
- **iOS reference implementation**: `GooseSwift/` from the original repo was used as the specification for this Android port

### What Was Ported

| iOS (Swift) | Android (Kotlin) |
|---|---|
| `GooseBLEClient.swift` | `GooseBLEManager.kt` |
| `GooseHello.swift` | Client Hello in `GooseBLEManager.kt` |
| `HealthDataStore.swift` | `GooseDataStore.kt` |
| `GooseAppModel.swift` | `GooseViewModel.kt` |
| `GooseRustBridge.swift` | `GooseRustBridge.kt` |
| `ConnectionView.swift` | `ConnectScreen.kt` |
| `HealthView.swift` | `HealthScreen.kt` |

---

## ⚖️ License

This project inherits the license of the original [b-nnett/goose](https://github.com/b-nnett/goose) repository. See [LICENSE](LICENSE) for details.

---

<p align="center">Built with ❤️ as an Android port of the open-source Goose WHOOP client</p>
