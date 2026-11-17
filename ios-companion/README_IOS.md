# TIME – iPhone (iOS) Companion Starter (SwiftUI + Firebase + WebRTC)

> **Important Honest Note:**
> - **Android APK** is compiled, tested, and packaged directly in this AI Studio environment (`app/`).
> - **iPhone (`.ipa`)** **cannot** be compiled on Linux without **macOS + Xcode** and an **Apple Developer** signing account.
> - This `ios-companion/` folder provides the matching Swift models and SwiftUI starter view that connect to the **exact same Firebase project (`gen-lang-client-0418089832`)** and **Cloud Firestore database (`ai-studio-android-time-96f54aac-a0c4-4798-a38b-c20dc1a3ccc5`)** so iPhone and Android users can message and call each other seamlessly.

## iPhone Setup Steps (macOS Xcode)

1. Open **Xcode** on a Mac -> **File -> New -> Project -> iOS App** (`Product Name: TIME`, `Interface: SwiftUI`, `Bundle Identifier: com.aistudio.timechat.ios`).
2. Copy `TimeModels.swift` and `TimeAuthAndChatView.swift` into your Xcode target.
3. Open **Firebase Console** (`gen-lang-client-0418089832`) -> **Project Settings -> Add App -> Apple (iOS)**, register your Bundle ID, download **`GoogleService-Info.plist`**, and drag it into your Xcode target.
4. In Xcode -> **File -> Add Package Dependencies**:
   - `https://github.com/firebase/firebase-ios-sdk` (`FirebaseAuth`, `FirebaseFirestore`, `FirebaseMessaging`)
   - `https://github.com/google-sign-in/google-sign-in-ios` (`GoogleSignIn`, `GoogleSignInSwift`)
   - `https://github.com/stasel/WebRTC` (`WebRTC`)
5. Add required keys to `Info.plist`:
   - `NSCameraUsageDescription`: `"TIME uses camera for WebRTC video calling and profile photos."`
   - `NSMicrophoneUsageDescription`: `"TIME uses microphone for live audio and video calls."`
   - `NSPhotoLibraryUsageDescription`: `"TIME lets you pick photos and videos to share in chats."`
6. When an iPhone user signs in, their `/users/{uid}` document sets `devicePlatform = "IPHONE"`, which automatically renders the **iPhone** badge in the TIME Android directory!

---

## हिन्दी सारांश (iPhone सेटअप)

- यह AI Studio बिल्ड एनवायरनमेंट केवल **Android APK** बना सकता है; **iPhone ऐप (`.ipa`)** बनाने के लिए **Mac + Xcode** ज़रूरी होता है।
- इसलिए इस प्रोजेक्ट के `ios-companion/` फ़ोल्डर में iPhone वर्ज़न का **SwiftUI + Firebase** कोड जोड़ा गया है, जो आपके उसी **Firebase (`gen-lang-client-0418089832`)** और **Firestore (`ai-studio-android-time-96f54aac-a0c4-4798-a38b-c20dc1a3ccc5`)** से जुड़ता है।
- Mac पर Xcode में इन फाइलों और `GoogleService-Info.plist` को जोड़कर रन करने पर **Android और iPhone यूज़र एक साथ चैट, ग्रुप चैट और WebRTC कॉल** कर पाएंगे।
