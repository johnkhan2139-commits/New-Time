# TIME – Firebase सेटअप और Android APK गाइड (हिन्दी)

> **वर्तमान स्थिति (Honest Status):**
> - **प्रोविज़न्ड व सक्रिय (Active):** Firebase प्रोजेक्ट (`gen-lang-client-0418089832`), **Google Sign-In (Credential Manager)**, **Cloud Firestore Database** (`ai-studio-android-time-96f54aac-a0c4-4798-a38b-c20dc1a3ccc5`), और **Zero-Trust Security Rules** ऐप में जुड़े और सेटअप हैं।
> - **मैनुअल Firebase Console सेटअप आवश्यक:** लाइव **SMS Phone OTP (`PhoneAuthManager`)**, **बड़े वीडियो क्लिप के लिए Firebase Storage**, **बैकग्राउंड FCM Push Notifications**, और **प्रोडक्शन TURN सर्वर** के लिए आपको नीचे दिए गए चरण Firebase Console में चालू करने होंगे।

---

## 1. Firebase Console सेटअप (हिन्दी में)

1. **Firebase Console खोलें**: [https://console.firebase.google.com](https://console.firebase.google.com) में प्रोजेक्ट `gen-lang-client-0418089832` चुनें।
2. **Phone Authentication (मोबाइल OTP लॉगिन) चालू करें**:
   - **Authentication -> Sign-in method -> Phone** को Enable करें।
   - असली मोबाइल नंबर SMS OTP के लिए **Google Cloud Console** में **Play Integrity API** सक्रिय करें और अपना **SHA-1 / SHA-256** फिंगरप्रिंट ऐप सेटिंग्स में सुनिश्चित करें।
3. **Firebase Storage (फुल वीडियो फ़ाइल अपलोड के लिए)**:
   - अभी फोटो और वीडियो की थंबनेल-फ्रेम झलक Firestore से शेयर होती है। पूरे वीडियो डाउनलोड/स्ट्रीम के लिए Firebase Console में **Storage** चालू करें और `request.auth != null` आधारित Storage Rules लगाएं।
4. **Firebase Cloud Messaging (FCM) Push**:
   - जब दोनों यूज़र ऐप खोलते हैं तब Firestore रियल-टाइम सिंक से तुरंत मैसेज और कॉल रिंग आती है। बंद ऐप में बैकग्राउंड पुश नोटिफिकेशन के लिए FCM और Cloud Functions जोड़ें।
5. **Firebase App Check**:
   - **App Check** में अपने Android ऐप (`com.aistudio.timechat.kpwmxr`) के लिए **Play Integrity** (रिलीज़) और **Debug provider** रजिस्टर करें।
6. **WebRTC TURN सर्वर**:
   - अलग-अलग मोबाइल नेटवर्क पर वीडियो और ऑडियो कॉलिंग के लिए **coturn** या कोई TURN सेवा सेट करें और ऐप के **Call Studio -> Configure TURN** में अपना `turn:` URL, username और credential सेव करें।

---

## 2. Android APK बिल्ड निर्देश (Android Build Instructions)

### Debug APK बनाने के लिए:
```bash
gradle :app:assembleDebug
```
आउटपुट पाथ: `app/build/outputs/apk/debug/app-debug.apk`

### Release APK बनाने के लिए:
1. अपना upload keystore बनाएं (`keytool -genkey -v -keystore my-upload-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload`)।
2. Environment variables सेट करें: `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`।
3. कमांड चलाएं:
```bash
gradle :app:assembleRelease
```
आउटपुट पाथ: `app/build/outputs/apk/release/app-release.apk`

### GitHub Actions से APK बनाते समय Firebase Config (`GOOGLE_SERVICES_JSON`):
- AI Studio से GitHub push करते समय सुरक्षा कारणों से `app/google-services.json` रिपॉजिटरी में नहीं जाता।
- GitHub APK में इसे ऑटोमैटिक जोड़ने के लिए GitHub रिपॉजिटरी के **Settings → Secrets and variables → Actions → New repository secret** में **`GOOGLE_SERVICES_JSON`** नाम से नया secret बनाएं और `app/google-services.json` का पूरा JSON पेस्ट करें।
- इसके अलावा ऐप के अंदर भी **Import `google-services.json` File**, **Paste Text**, या **Enter Firebase Keys Manually** विकल्प मौजूद हैं।

---

## 3. iPhone (iOS) वर्ज़न के लिए निर्देश

> यह AI Studio कंटेनर केवल **Android APK** बिल्ड और टेस्ट कर सकता है। **iPhone (.ipa)** ऐप बनाने के लिए **Mac + Xcode** और Apple Developer अकाउंट आवश्यक होता है।
- रिपॉजिटरी के `ios-companion/` फ़ोल्डर में iPhone के लिए SwiftUI + Firebase + Firestore स्कीमा फाइलें (`TimeModels.swift`, `TimeAuthAndChatView.swift`, `README_IOS.md`) जोड़ दी गई हैं।
- जब iPhone यूज़र लॉग-इन करेगा तो उसका `devicePlatform = "IPHONE"` Firestore में सेव होगा और Android व iPhone यूज़र एक ही Firebase प्रोजेक्ट (`gen-lang-client-0418089832`) के ज़रिए आपस में चैट और WebRTC कॉल कर पाएंगे।
