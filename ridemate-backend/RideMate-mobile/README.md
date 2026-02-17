# RideMate Android App

Production-oriented Android wrapper for the RideMate web app.

## What this app does

- Loads RideMate using a hardened `WebView`
- Supports file uploads from HTML inputs
- Supports geolocation permission flow
- Supports swipe-to-refresh and in-web back navigation
- Opens external schemes (`tel:`, `mailto:`, maps links) in native apps

## Project structure

- `app/src/main/java/com/rydius/mobile/MainActivity.kt`: Android shell and WebView controls
- `app/src/main/res/layout/activity_main.xml`: UI container
- `app/src/main/res/xml/network_security_config.xml`: local HTTP allowlist for dev
- `app/build.gradle.kts`: app config and `BASE_URL` build config

## Configure backend URL

Set `RIDEMATE_BASE_URL` in `local.properties` (not committed):

```properties
RIDEMATE_BASE_URL=http://10.0.2.2:3000
```

For a physical device, use your machine's LAN IP:

```properties
RIDEMATE_BASE_URL=http://192.168.1.20:3000
```

For release builds, point to your HTTPS domain:

```properties
RIDEMATE_BASE_URL=https://your-production-domain.com
```

## Build

1. Open `ridemate-backend/RideMate-mobile` in Android Studio.
2. Let Gradle sync and install SDK/Build Tools if prompted.
3. Run `app` on emulator/device.

CLI build (optional):

```bash
./gradlew :app:assembleDebug
```

## Notes

- Android Gradle Plugin requires JDK 17 for builds.
- Keep production on HTTPS.
- Do not commit `local.properties`.
