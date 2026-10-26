# ⚡ TeleCloud

<p align="center">
  <strong>Ultra-Fast, Zero-Bloat Cloud Media Streamer & Native Downloader for Android</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Release-v2.2.0-0284c7?style=for-the-badge&logo=android" alt="Release v2.2.0">
  <img src="https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-10b981?style=for-the-badge&logo=android" alt="Platform">
  <img src="https://img.shields.io/badge/Architecture-Kotlin%20%2B%20WebViewAssetLoader-38bdf8?style=for-the-badge" alt="Architecture">
  <img src="https://img.shields.io/badge/Auth-Quantum%20Gateway%20Protected-10b981?style=for-the-badge" alt="Auth">
  <img src="https://img.shields.io/badge/Signing-Persistent%20Release%20Keystore-8b5cf6?style=for-the-badge" alt="Signing">
</p>

---

## 📖 Overview

**TeleCloud** transforms your Telegram Cloud Storage into a high-performance, personal streaming and media download application. 

Engineered with a **Zero-Bloat** philosophy, TeleCloud bypasses memory-heavy browser caches and complex client-side serialization, pairing a GPU-accelerated AMOLED interface with Android’s official native subsystems (`DownloadManager`, `WindowInsetsControllerCompat`, and native `Intent` dispatchers).

---

## ✨ Features

- **🔐 Quantum Gateway In-App Authentication:**
  - Full support for protected cloud backends requiring username & password authentication.
  - Seamless in-app Login modal with secure local credential persistence and silent auto-login.
  - Native cookie synchronization across WebView, official `DownloadManager`, and external players.

- **🚀 Ultra-Fast Cloud Video Streaming:**
  - Direct hardware-accelerated playback (`View.LAYER_TYPE_HARDWARE`).
  - Granular playback speed control: `0.75x`, `1.0x`, `1.25x`, `1.5x`, `2.0x`.
  - Seamless **Loop Single** or **Auto-Play Next** modes.
  - Native display keep-awake (`FLAG_KEEP_SCREEN_ON`) automatically active during playback.

- **📱 1-Tap External Player Support:**
  - Launch high-bitrate or surround-audio streams directly in dedicated players such as **VLC**, **MX Player**, or **Nova Video Player** with session cookie header injection.

- **💾 Native Android Background Downloads:**
  - Offloaded directly to Android’s official `DownloadManager`.
  - Downloads proceed in the background with system status bar progress, pause/resume capability, and automatic saving to `/sdcard/Download/`.
  - Zero RAM penalty when downloading large video files.

- **🔄 Persistent Signing & In-Place Updates:**
  - Built with a dedicated, permanent release keystore.
  - **No uninstallation required** when upgrading between versions.
  - Integrated **Check for Updates** button in Settings with automated GitHub release detection and 1-tap APK update download.

- **🌓 Pure AMOLED Dark Theme (`#080c14`):**
  - Battery-friendly interface optimized for OLED/AMOLED screens.
  - Real-time search by filename, caption, or folder.
  - Dynamic folder classification chips.

- **🛡️ Server Redundancy & Dynamic Host Switching:**
  - Built-in automatic failover between primary VPS server and backup server.
  - Customizable server host URL directly from the in-app Settings modal.

---

## 🏗️ Architecture

```
                               ┌────────────────────────────────────────┐
                               │           AMOLED User Interface        │
                               │   (HTML5 / Vanilla JS / CSS3 - LTR)    │
                               │   • Quantum Gateway Login Modal        │
                               └──────────────────┬─────────────────────┘
                                                  │ (WebViewAssetLoader)
                                                  ▼
                               ┌────────────────────────────────────────┐
                               │          Android Native Layer          │
                               │  com.acmods.telecloud.MainActivity     │
                               │  • CookieManager / Session Sync        │
                               └───────┬────────────────────────┬───────┘
                                       │                        │
                   (DownloadManager)   ▼                        ▼   (Intent.ACTION_VIEW)
                ┌───────────────────────────┐         ┌──────────────────────────┐
                │  Official System Downloads│         │  External Video Players  │
                │     (/sdcard/Download/)   │         │     (VLC / MX Player)    │
                │     with Cookie Headers   │         │     with Cookie Headers  │
                └───────────────────────────┘         └──────────────────────────┘
                                       ▲
                                       │ (Authenticated Streaming & Metadata)
                ┌──────────────────────┴─────────────────────────────────┐
                │                  Cloud Backend Servers                 │
                │     • Quantum Gateway Protected VPS                    │
                │     • Automatic Fallback Proxy Server                  │
                └────────────────────────────────────────────────────────┘
```

---

## 📥 Installation

1. Navigate to the [Latest Releases](https://github.com/Ayoubvvch/TeleCloud/releases/latest) page.
2. Download `TeleCloud-v2.2.0.apk`.
3. Open the APK on your Android device (Android 8.0 Oreo or newer) and confirm installation.
4. Future updates can be checked and downloaded directly from the **Settings (⚙️) > Check for Updates** menu inside the app.

---

## 🛠️ Building from Source

### Prerequisites
- **JDK 17** (Temurin or OpenJDK recommended)
- **Android SDK** (API Level 34, Build Tools 34.0.0)
- **Gradle 8.6+** (included via wrapper)

### Build Steps

1. **Clone the repository:**
   ```bash
   git clone https://github.com/Ayoubvvch/TeleCloud.git
   cd TeleCloud
   ```

2. **Assemble Signed Release APK:**
   ```bash
   ./gradlew assembleRelease
   ```
   *The signed APK will be generated at:*  
   `app/build/outputs/apk/release/app-release.apk`

---

## 📄 License

This project is open-source under the MIT License. Developed and maintained by [@Ayoubvvch](https://github.com/Ayoubvvch).
