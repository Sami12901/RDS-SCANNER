# RDS SCANNER — CamScanner & Adobe Acrobat Reader: Edit PDF Super App

<p align="center">
  <img src="assets/logo.png" alt="RDS SCANNER Logo" width="140" style="border-radius: 28px;" />
</p>

<p align="center">
  <b>The Ultimate Privacy-First Powerhouse App combining CamScanner & Adobe Acrobat Reader: Edit PDF</b><br>
  <i>100% Free, Ad-Free & Open-Source for Android & Apple iOS • Developed with ❤️ by <b><a href="https://github.com/Sami12901">Sami (@Sami12901)</a></b></i>
</p>

<p align="center">
  <a href="https://github.com/Sami12901/RDS-SCANNER/releases/latest"><img src="https://img.shields.io/github/v/release/Sami12901/RDS-SCANNER?color=00A86B&style=flat-square" alt="Latest Release" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache_2.0-blue.svg?style=flat-square" alt="License" /></a>
  <img src="https://img.shields.io/badge/Platform-Android_&_iOS_(Apple)-3DDC84?style=flat-square&logo=apple&logoColor=white" alt="Platform" />
  <img src="https://img.shields.io/badge/Android-Kotlin_Compose-7F52FF?style=flat-square&logo=android&logoColor=white" alt="Android" />
  <img src="https://img.shields.io/badge/iOS-Swift_SwiftUI_PDFKit-F05138?style=flat-square&logo=swift&logoColor=white" alt="iOS" />
  <img src="https://img.shields.io/badge/Privacy-100%25_On--Device-success?style=flat-square" alt="Privacy" />
  <img src="https://img.shields.io/badge/Ads-Zero_Ads-brightgreen?style=flat-square" alt="No Ads" />
</p>

---

## 📥 Download RDS SCANNER (v1.4.0 Super App)

Get the latest release for your phone:

<p align="center">
  <a href="https://github.com/Sami12901/RDS-SCANNER/releases/download/v1.4.0/RDS-SCANNER-v1.4.0.apk">
    <img src="https://img.shields.io/badge/Download_Android_APK-v1.4.0_(Signed_15.7_MB)-00A86B?style=for-the-badge&logo=android&logoColor=white" alt="Download Android APK" />
  </a>
  &nbsp;&nbsp;
  <a href="https://github.com/Sami12901/RDS-SCANNER/releases/download/v1.4.0/RDSScanner-v1.4.0.ipa">
    <img src="https://img.shields.io/badge/Download_iPhone_IPA-v1.4.0_(iOS_16+)-000000?style=for-the-badge&logo=apple&logoColor=white" alt="Download iPhone IPA" />
  </a>
</p>

> 🍏 **How to Install on iPhone**: Download **`RDSScanner-v1.4.0.ipa`** and install with **[Sideloadly](https://sideloadly.io/)**, **[AltStore](https://altstore.io/)**, **TrollStore**, or **Scarlet** (no jailbreak required). Or build the source project in Xcode with `RDSScanner-iOS-v1.4.0.zip`.

> View all release files on the **[Releases Page](https://github.com/Sami12901/RDS-SCANNER/releases)**.

---

## 🌟 The Power of Combining CamScanner & Adobe Acrobat

Commercial apps like CamScanner and Adobe Acrobat Reader charge steep monthly subscriptions ($9.99/mo to $19.99/mo), plague users with intrusive ads, force ugly watermarks, and upload private documents to remote cloud servers.

**RDS SCANNER v1.4.0** unites the scanning power of CamScanner with the professional PDF viewing, markup, editing, and tool suites of Adobe Acrobat into **one single, lightning-fast, 100% offline app**:

| Capability | CamScanner / Adobe Acrobat (Paid) | RDS SCANNER Super App (Free & Open Source by Sami) |
| :--- | :--- | :--- |
| **Document Scanning** | Auto edge detection, auto-capture | **Google ML Kit Document Scanner** + CameraX |
| **Magic Color 2.0** | Trademark paper whitening | Custom Magic Color with ink vibrancy boost ($1.25\times$) |
| **PDF Viewing & Scrolling** | Basic or cloud-synced reader | Continuous vertical scrolling + **Dark / Night reading mode** |
| **PDF Markup & Annotation** | Locked behind Acrobat Pro paywall | **Highlighter (Yellow, Green, Cyan, Pink)** + Freehand Pen |
| **Type on PDF & Notes** | Paid Adobe Acrobat subscription | **Type on PDF text boxes** + Sticky note comment pins |
| **Geometric Shapes** | Paid feature | Rectangles, Circles, Arrows, and Lines |
| **Page Organizer** | Paid feature | Rotate $90^\circ$, delete pages, and reorder |
| **PDF Tools Suite** | Paid subscription | **Merge, Split, Compress, Watermark, Convert & OCR** |
| **Digital Signatures** | Paid e-sign tier | Finger/stylus signature pad with scalable placement |
| **Privacy & Telemetry** | Scans uploaded to corporate clouds | **100% On-Device & Zero Telemetry** |
| **Cost & Advertisements** | Costly monthly fees + Ads | **100% Free, Ad-Free & Watermark-Free** |

---

## 🧭 The 3 Unified Hubs

RDS SCANNER is architected with a clean Material 3 Bottom Navigation bar consisting of 3 dedicated hubs:

```
┌─────────────────────────────────────────────────────────────┐
│                       RDS SCANNER v1.4.0                    │
├───────────────────┬─────────────────────┬───────────────────┤
│   HUB 1: SCANNER  │    HUB 2: TOOLS     │   HUB 3: READER   │
│   (CamScanner)    │   (Acrobat Suite)   │  (Acrobat Reader) │
│                   │                     │                   │
│ • Camera Edge     │ • Merge PDFs        │ • Continuous View │
│ • Magic Color 2.0 │ • Split PDF         │ • Highlighter     │
│ • B&W Binarize    │ • Compress PDF      │ • Freehand Pen    │
│ • ID Card Stitch  │ • Add Watermark     │ • Type on PDF     │
│ • Offline OCR     │ • Images to PDF     │ • Sticky Notes    │
│ • Signature Pad   │ • PDF to Images     │ • Page Organizer  │
│ • Document Lib    │ • Full OCR Text     │ • Night Mode      │
└───────────────────┴─────────────────────┴───────────────────┘
```

---

## 🛠️ Tech Stack & Implementation Details

- **UI Framework:** Jetpack Compose + Material 3 (Android) / SwiftUI (iOS)
- **PDF Engine:** Native Android `PdfRenderer` + `PdfDocument` (zero C++ bloat) / Apple `PDFKit` (iOS)
- **Annotation System:** Real-time hardware-accelerated Compose `Canvas` + Lossless 300 DPI `PdfBakingEngine`
- **Scanner Engine:** Google Play Services ML Kit Document Scanner + CameraX
- **OCR Engine:** Google ML Kit Text Recognition (100% Offline)
- **Image Processing:** Multi-threaded RGB/HSV matrix filters with adaptive block binarization
- **Storage:** Private app-specific sandbox with JSON metadata (zero cloud sync)

---

## 💻 How to Build from Source

### Prerequisites
- JDK 17 or JDK 21
- Android SDK (API 24 to 36)

### 🤖 Android Build
```bash
# Clone the repository
git clone https://github.com/Sami12901/RDS-SCANNER.git
cd RDS-SCANNER

# Build signed release APK
./gradlew assembleRelease

# Output APK path:
# release/RDS-SCANNER-v1.4.0.apk
```

### 🍏 Apple / iPhone (iOS) Build
```bash
# Open Xcode project on macOS
open ios/RDSScanner/RDSScanner.xcodeproj

# Or build via terminal:
xcodebuild -project ios/RDSScanner/RDSScanner.xcodeproj \
  -scheme RDSScanner \
  -configuration Release \
  -sdk iphoneos \
  build
```

---

## 👨‍💻 Author & Credits

- **Application Name:** RDS SCANNER
- **Developed by:** **[Sami](https://github.com/Sami12901)** (`Develop by Sami`)
- **License:** [Apache License 2.0](LICENSE) — Free & Open Source for everyone.

<p align="center">
  <b>If you love RDS SCANNER, don't forget to give this repository a ⭐ star!</b>
</p>
