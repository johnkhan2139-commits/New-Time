# TIME – Firebase सेटअप और Android APK गाइड (हिन्दी)

> **महत्वपूर्ण स्थिति नोट (Honest Status):**
> - इस AI Studio बिल्ड सेशन में `TIME_App_Public_Fixed_v2.zip` प्रोजेक्ट उपलब्ध नहीं था और यह वातावरण केवल **Native Android (Kotlin + Jetpack Compose)** ऐप्स बिल्ड करता है (Flutter/Dart नहीं)।
> - साथ ही, Firebase सेटअप प्रॉम्प्ट को अस्वीकार (Decline) किए जाने के कारण लाइव **Phone OTP Auth, Cloud Firestore Sync, Firebase Storage, FCM Push Notifications, App Check, और Remote WebRTC Signaling** अभी ऑफलाइन हैं।
> - नीचे दिए गए चरणों का पालन करके आप अपने असली Firebase प्रोजेक्ट को जोड़ सकते हैं और Debug / Release APK बना सकते हैं।

---

## 1. Firebase प्रोजेक्ट सेटअप (हिन्दी में)

1. **Firebase Console खोलें**: [https://console.firebase.google.com](https://console.firebase.google.com) पर जाएं और **Add project** पर क्लिक करें (नाम: `TIME`)।
2. **Android ऐप रजिस्टर करें**:
   - **Android package name**: `com.aistudio.timechat.kpwmxr`
   - **App nickname**: `TIME`
   - **Debug signing certificate SHA-1**: अपने कीस्टोर से निकालकर जोड़ें।
3. **`google-services.json` जोड़ें**:
   - डाउनलोड की गई फाइल को `app/google-services.json` में रखें।
   - कभी भी नकली या placeholder क्रेडेंशियल इस्तेमाल न करें।
4. **Phone Authentication चालू करें**:
   - Firebase Console -> **Authentication** -> **Sign-in method** -> **Phone** को Enable करें।
   - असली मोबाइल नंबर OTP के लिए **Play Integrity API** (Google Cloud Console) को सक्रिय करें।
5. **Cloud Firestore और Security Rules**:
   - **Firestore Database** बनाएं।
   - कम से कम यह सुनिश्चित करें कि `request.auth != null` के बिना कोई भी यूज़र डेटा न पढ़/लिख सके और `users/{uid}`, `chats/{chatId}`, `calls/{callId}`, `reports/{reportId}` पर सख्त ownership नियम लागू हों।
6. **Firebase Storage और Rules**:
   - फोटो और वीडियो अपलोड के लिए **Storage** चालू करें और केवल authenticated यूज़र्स को सीमित साइज (जैसे 25 MB तक) की मीडिया फाइल अपलोड की अनुमति दें।
7. **Firebase Cloud Messaging (FCM)**:
   - चैट और इनकमिंग वीडियो कॉल नोटिफिकेशन के लिए **Cloud Messaging** सक्रिय करें। Android 13+ डिवाइस के लिए ऐप में `POST_NOTIFICATIONS` परमिशन पहले से जोड़ी गई है।
8. **Firebase App Check**:
   - **App Check** में अपने Android ऐप के लिए **Play Integrity** (रिलीज़) और **Debug provider** (टेस्टिंग) रजिस्टर करें।
9. **WebRTC TURN सर्वर**:
   - प्रोडक्शन वीडियो कॉलिंग (NAT/Firewall के पार) के लिए **coturn** या कोई TURN सेवा सेट करें और ऐप के **Call Studio -> Configure TURN** में अपना `turn:` URL, username और credential सेव करें।

---

## 2. Android APK बिल्ड निर्देश (Android Build Instructions)

### Debug APK बनाने के लिए:
```bash
gradle :app:assembleDebug
```
आउटपुट पाथ: `app/build/outputs/apk/debug/app-debug.apk`

### Release APK बनाने के लिए:
1. अपना upload keystore बनाएं (`keytool -genkey -v -keystore my-upload-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload`)।
2. Environment variables सेट करें:
   - `KEYSTORE_PATH`
   - `STORE_PASSWORD`
   - `KEY_PASSWORD`
3. कमांड चलाएं:
```bash
gradle :app:assembleRelease
```
आउटपुट पाथ: `app/build/outputs/apk/release/app-release.apk`
