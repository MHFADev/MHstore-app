# MHStore Admin APK
> Native Android admin dashboard · Real-time orders + FCM push notifications

## 📱 Features
- 🔐 **PIN login** (6-digit, set on first run)
- 📊 **Real-time order dashboard** with 4 stats cards
- 🔔 **Push notifications** for every new order (FCM)
- 📋 **Order list** with tab filters (All / Pending / In Progress / Done)
- 📄 **Order detail** with full customer info & service specs
- ⚙️ **Status management** (Pending → Confirmed → In Progress → Done)
- 💬 **WhatsApp quick-contact** from each order
- 🌙 **Dark theme** throughout

## 🛠 Build Instructions

### Prerequisites
- **Android Studio** Giraffe (2022.3.1) or newer
- **Java** 11+
- **Firebase project** (same as the web form)

### Steps

#### 1. Firebase Setup
1. Go to [console.firebase.google.com](https://console.firebase.google.com)
2. Select your project → **Project Settings** → **Your Apps**
3. Click **Add App** → Android
4. Package name: `com.mhstore.admin`
5. Download `google-services.json`
6. **Copy** `google-services.json` into `app/` folder
   (rename from `google-services.json.template` — see that file for reference)

#### 2. Open in Android Studio
```bash
# Open the MHStoreAdmin folder in Android Studio
# File → Open → select /apk/MHStoreAdmin
```

#### 3. Sync & Build
- Wait for Gradle sync (first time downloads dependencies — requires internet)
- **Build → Build Bundle(s) / APK(s) → Build APK(s)**
- APK appears at: `app/build/outputs/apk/debug/app-debug.apk`

#### 4. Install on Android
```bash
# Via ADB
adb install app/build/outputs/apk/debug/app-debug.apk

# Or: copy APK to phone → open file manager → tap APK
# Allow "Install from unknown sources" if prompted
```

#### 5. First Run
1. Open **MHStore Admin** app
2. Grant **notification permission** when prompted
3. Set your **6-digit PIN**
4. You're in! The app subscribes to topic `mhstore_admin` automatically

## 📂 Project Structure
```
MHStoreAdmin/
├── app/src/main/
│   ├── java/com/mhstore/admin/
│   │   ├── MHStoreApp.java              ← Application class + notification channel
│   │   ├── activities/
│   │   │   ├── SplashActivity.java      ← Logo screen + routing
│   │   │   ├── LoginActivity.java       ← PIN setup/verify + FCM subscribe
│   │   │   ├── MainActivity.java        ← Dashboard + real-time order list
│   │   │   └── OrderDetailActivity.java ← Full order view + status update
│   │   ├── adapters/
│   │   │   └── OrderAdapter.java        ← RecyclerView adapter
│   │   ├── models/
│   │   │   └── Order.java               ← Firestore data model
│   │   ├── services/
│   │   │   └── MHFirebaseMessagingService.java ← FCM handler
│   │   └── utils/
│   │       ├── PrefManager.java         ← SharedPreferences wrapper
│   │       └── Constants.java           ← App constants
│   └── res/
│       ├── layout/                      ← XML layouts
│       ├── drawable/                    ← Shapes, icons, backgrounds
│       ├── values/                      ← Colors, strings, themes
│       └── xml/                         ← Config files
├── google-services.json                 ← Add your own! (see .template)
└── README.md
```

## 🔔 Notification Flow
```
Customer submits order
    → Saved to Firestore
    → Vercel API /api/notify called
    → Firebase Admin SDK sends FCM to topic "mhstore_admin"
    → All admin devices (subscribed to topic) receive notification
    → Tap notification → Opens order detail
```

## 📋 Firestore Rules Required
```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /orders/{orderId} {
      allow create: if true;
      allow read, update: if request.auth != null; // Customize as needed
    }
  }
}
```

## 🚀 Release APK (Signed)
For production, create a signing key:
```bash
keytool -genkey -v -keystore mhstore-key.jks -alias mhstore -keyalg RSA -keysize 2048 -validity 10000
```
Then configure `signingConfigs` in `app/build.gradle`.

---
**MHStore Development Partner** · mhstore.web.id · #vibecoder
