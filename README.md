# 🪞 Mirror — Natural Look (Android)

A native Android mirror app built with **Kotlin + CameraX**.  
Full-screen front camera, auto-rotate, pinch-to-zoom, Dark & Silver themes.  
Monetised with **30-day free trial → $5 lifetime ad-free** via Google Play Billing + AdMob ads.

---

## 📁 Project Structure

```
MirrorNaturalLook/
├── app/
│   ├── build.gradle                         ← Dependencies
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml              ← Permissions, activities
│       ├── java/com/mirrornaturallook/
│       │   ├── MirrorApp.kt                 ← Application class (AdMob init)
│       │   ├── SplashActivity.kt            ← Instant launch (no delay)
│       │   ├── MirrorActivity.kt            ← Main mirror: camera, zoom, theme, ads
│       │   ├── PaywallActivity.kt           ← $5 upgrade screen
│       │   ├── PremiumManager.kt            ← 30-day trial + purchase state
│       │   ├── BillingManager.kt            ← Google Play Billing $5 IAP
│       │   └── AdManager.kt                 ← AdMob banner/interstitial/rewarded
│       └── res/
│           ├── layout/
│           │   ├── activity_mirror.xml      ← Full-screen mirror UI
│           │   └── activity_paywall.xml     ← Upgrade screen
│           ├── values/
│           │   ├── colors.xml
│           │   ├── strings.xml
│           │   └── themes.xml
│           └── drawable/                    ← Backgrounds, cards, gradients
├── build.gradle                             ← Project-level config
├── settings.gradle
├── .github/workflows/build.yml             ← Auto-build APK + AAB on push
└── README.md
```

---

## 🚀 Step 1 — Open in Android Studio

1. Download **Android Studio Hedgehog** or newer from developer.android.com
2. **File → Open** → select the `MirrorNaturalLook/` folder
3. Let Gradle sync (first time takes 2–5 minutes downloading dependencies)
4. Plug in your Android phone (USB debugging on) OR create an emulator
5. Press **▶ Run** — app launches immediately

---

## 📋 Step 2 — Required Replacements Before Publishing

### 2a. AdMob App ID (AndroidManifest.xml)
```xml
<!-- Replace this in AndroidManifest.xml -->
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="ca-app-pub-XXXXXXXXXXXXXXXX~XXXXXXXXXX" />
```
Get your App ID from: **admob.google.com → Apps → Add App**

### 2b. AdMob Ad Unit IDs (AdManager.kt)
```kotlin
const val BANNER_AD_UNIT_ID      = "ca-app-pub-YOUR_ID/YOUR_BANNER_UNIT"
const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-YOUR_ID/YOUR_INTERSTITIAL_UNIT"
const val REWARDED_AD_UNIT_ID    = "ca-app-pub-YOUR_ID/YOUR_REWARDED_UNIT"
```
Create 3 ad units in AdMob console and replace the test IDs.

### 2c. Google Play Billing Product ID (PremiumManager.kt)
```kotlin
const val PRODUCT_ID_LIFETIME = "mirror_lifetime_premium"
```
This must exactly match the product ID you create in Google Play Console.

### 2d. Package name (build.gradle + AndroidManifest.xml)
Replace `com.mirrornaturallook` with your own unique package name e.g. `com.yourname.mirrornaturallook`

---

## 💳 Step 3 — Google Play Setup

1. Go to **play.google.com/console** → Create app
2. App name: `Mirror — Natural Look`
3. App details → fill in description, screenshots, privacy policy
4. **Monetise → In-app products** → Create product:
   - Product ID: `mirror_lifetime_premium`
   - Type: **One-time purchase** (not subscription)
   - Price: **$4.99 USD**
   - Status: **Active**
5. **Monetise → AdMob** → link your AdMob account

---

## 🏗️ Step 4 — Build for Play Store

### Generate release keystore (one time only)
```bash
keytool -genkey -v \
  -keystore mirror-release.jks \
  -alias mirror \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```
⚠️ Keep this file safe — you'll need it for every update forever.

### Add signing config to app/build.gradle
```gradle
android {
    signingConfigs {
        release {
            storeFile file('mirror-release.jks')
            storePassword 'YOUR_STORE_PASSWORD'
            keyAlias 'mirror'
            keyPassword 'YOUR_KEY_PASSWORD'
        }
    }
    buildTypes {
        release {
            signingConfig signingConfigs.release
        }
    }
}
```

### Build AAB
```bash
./gradlew bundleRelease
# Output: app/build/outputs/bundle/release/app-release.aab
```

Upload the `.aab` file to Play Console → Production track.

---

## 🐙 Step 5 — Push to GitHub

```bash
cd MirrorNaturalLook
git init
git add .
git commit -m "Initial release — Mirror Natural Look"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/mirror-natural-look-android.git
git push -u origin main
```

GitHub Actions will automatically build the APK and AAB on every push.  
Find them in: **GitHub → Actions → latest run → Artifacts**

---

## 📱 Features Summary

| Feature | Implementation |
|---|---|
| Front camera mirror | CameraX with horizontal flip (`scaleX = -1`) |
| Auto-rotate | `screenOrientation="fullSensor"` + config changes handled |
| Pinch-to-zoom | Multi-touch `MotionEvent` + `camera.cameraControl.setZoomRatio` |
| Zoom buttons | 1× / 1.5× / 2× / 2.5× / 3× tap buttons |
| Dark theme | Radial dark gradient fallback behind camera |
| Silver theme | Linear silver gradient fallback behind camera |
| Keep screen on | `FLAG_KEEP_SCREEN_ON` |
| Full-screen | `WindowInsetsControllerCompat` hides system bars |
| Auto-hide UI | Controls fade after 4 seconds, tap to show |
| 30-day trial | `PremiumManager` with `EncryptedSharedPreferences` |
| $5 lifetime IAP | Google Play Billing v6 |
| Restore purchase | `queryPurchasesAsync` on startup + Paywall button |
| Banner ads | AdMob banner in bottom container |
| Interstitial ads | Shown every 3rd session |
| Rewarded ads | "Watch ad = 1hr no ads" button |

---

## 🔧 Key Files to Customise

| File | What to change |
|---|---|
| `PremiumManager.kt` | Trial days (default 30), product ID |
| `AdManager.kt` | Replace test Ad Unit IDs with real ones |
| `AndroidManifest.xml` | Replace AdMob App ID |
| `res/values/strings.xml` | App name, button text |
| `res/values/colors.xml` | Gold colour, backgrounds |
| `PaywallActivity.kt` | Price display, feature list |

---

## 📸 Play Store Requirements

Before submitting you'll need:
- **App icon**: 512×512 PNG — create at makeappicon.com
- **Feature graphic**: 1024×500 PNG
- **Screenshots**: Minimum 2 phone screenshots
- **Privacy Policy URL**: Use privacypolicygenerator.info (required — app uses camera)
- **Content rating**: Complete questionnaire in Play Console

---

Built with ❤️ — Kotlin · CameraX · Google Play Billing · AdMob
