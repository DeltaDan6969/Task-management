# ShiftTask iOS Application (Native Swift & SwiftUI)

This directory contains the native iOS version of **ShiftTask**, built in Swift 5.9 and SwiftUI for iOS 16+.

## 🔗 Shared Cloud Architecture (iOS ↔ Android ↔ Web)
The iOS app connects directly to the exact same **Google Cloud Firestore** backend as the Android app and Web application:
- **Project ID**: `gen-lang-client-0968461451`
- **Database ID**: `ai-studio-android-shifttas-347ba838-628f-49f4-a473-be4ef8849c02`
- **Synchronized Collections**:
  1. `store_config`: Store branding and shift kiosk settings.
  2. `categories`: Shift categories (`MORNING TASKS`, `EVENING TASKS`, etc.).
  3. `tasks`: All retail checklist tasks, priority badges, and recurring/one-time schedules.
  4. `users`: Staff roster with SHA-256 salted PIN hashes.
  5. `completions`: Real-time task sign-offs, partial checklists, and skip reasons.
  6. `task_notes`: Shift hand-over observation notes.
  7. `task_photos`: Fridge/chiller and compliance audit photos.

Any update made in iOS reflects **instantly** in the Android APK and the browser Web App, and vice versa.

---

## 🚀 Option 1: Open in Xcode (Mac)

1. Open Xcode on your Mac.
2. Select **File > Open...** and choose the `ios/ShiftTask` folder (or open `Package.swift`).
3. Xcode will automatically resolve the **Firebase iOS SDK** via Swift Package Manager (`FirebaseFirestore`, `FirebaseAuth`, `FirebaseStorage`).
4. Select your target device (iPhone 15/16 Simulator or connected physical iPhone).
5. Press **Cmd + R** to run!

---

## 📱 Option 2: 1-Tap iPhone / iPad Installation (No Mac Required)
Store staff and managers can also install ShiftTask directly onto any iPhone or iPad right now using the iOS Web App Clip:

1. Open **Safari** on your iPhone or iPad.
2. Navigate to your ShiftTask Web App URL.
3. Tap the **Share** button (box with upward arrow `⎋`) at the bottom of Safari.
4. Scroll down and tap **"Add to Home Screen"** (`⊞`).
5. Tap **Add**.

ShiftTask will be installed onto your iOS Home Screen with its own app icon, launching in full-screen standalone mode with native haptics, camera audit support, and Night-Shift theme toggle!
