# RDS SCANNER — Smart Document Scanner, OCR & PDF Maker

<p align="center">
  <b>An Open Source, Privacy-First CamScanner Alternative for Android</b><br>
  <i>Developed by <b>Sami</b></i>
</p>

---

## 🌟 Overview

**RDS SCANNER** is a modern, high-performance Android document scanner and PDF maker built from scratch. It reverse-engineers and improves upon CamScanner by delivering:

- **100% Free & Open Source**: No subscriptions, no locked features.
- **Zero Ads & Zero Telemetry**: Clean interface without intrusive popups.
- **No Watermarks**: Professional, clean PDF exports every time.
- **100% On-Device & Private**: All scanning, image processing, OCR, and PDF generation happen locally on your phone.

---

## 🚀 Key Features

### 1. 📷 Smart Document Scanner (ML Kit)
- Automatic edge detection & corner perspective correction.
- Auto-capture and flash toggle.
- Support for multi-page scanning sessions.

### 2. 🎨 Enhancement Filters
- **Magic Color**: CamScanner's trademark look — whitens paper background, strengthens ink, and enhances clarity.
- **B&W / Monochrome**: High-contrast document thresholding for crisp printing and low file size.
- **Grayscale**: Smooth tone document rendering.
- **Original**: Unaltered raw capture.

### 3. 🆔 ID Card & Passport Dual-Sided Mode
- Captures Front and Back of an ID card, Driving License, or Passport.
- Automatically stitches both sides onto a single, printable standard A4 sheet with clean margins and labels.

### 4. 🔤 On-Device OCR (Text Extraction)
- Offline text recognition powered by Google ML Kit.
- One-tap text copying to clipboard and direct sharing as plain text.

### 5. ✍️ Digital Signature & Annotations
- Built-in signature pad allowing you to sign documents using finger or stylus.
- Stamps signature onto document pages before PDF generation.

### 6. 📄 Clean PDF Generator
- Native Android `PdfDocument` engine for fast, memory-safe compilation.
- Multiple quality and compression options.
- Direct sharing to WhatsApp, Gmail, Telegram, Google Drive, or Print.

---

## 🛠️ Architecture & Tech Stack

- **Platform:** Android 7.0+ (API 24 - 36)
- **Language:** Kotlin 2.2+
- **UI Toolkit:** Jetpack Compose + Material 3 (Material You)
- **Scanner:** Google Play Services ML Kit Document Scanner (`play-services-mlkit-document-scanner`)
- **OCR:** Google ML Kit Text Recognition (`play-services-mlkit-text-recognition`)
- **Image Loading:** Coil (`io.coil-kt:coil-compose`)
- **PDF Engine:** Android Native `android.graphics.pdf.PdfDocument`
- **Build System:** Gradle 9.1 with Android Gradle Plugin 9.0.1 (Java 21)

---

## 📱 How to Build & Run

### Prerequisites
- JDK 17 or JDK 21 (e.g. bundled in Android Studio)
- Android SDK with platform-tools and platform 36

### Build Debug APK:
```bash
./gradlew assembleDebug
```
The compiled APK will be located at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Install onto Device / Emulator:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 👨‍💻 Author & Credits

- **App Name:** RDS SCANNER
- **Developed by:** Sami (`Develop by Sami`)
- **License:** Apache License 2.0 (Open Source)
