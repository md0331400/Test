# Build & Release

## Toolchain

- JDK 17
- Android SDK Platform 36 / Build Tools 36
- Gradle 8.14.3
- AGP 8.11.1 / Kotlin 2.1.21 / KSP 2.1.21-2.0.2

A re-runnable sandbox installer is provided at `/home/user/setup-env.sh`. For a normal development machine, use Android Studio's bundled JDK 17 and SDK Manager.

## Commands

```bash
./gradlew testDebugUnitTest assembleDebug
./gradlew assembleRelease       # unsigned unless release.properties exists
./gradlew bundleRelease         # Play/App Bundle, signed only with developer keystore
```

## Release signing (secrets never committed)

Create `release.properties` in the project root:

```properties
storeFile=kothabolbo-release.jks
storePassword=YOUR_SECRET
keyAlias=kothabolbo
keyPassword=YOUR_SECRET
```

Place that developer-held keystore in the project root. Both files and all `*.jks`/`*.keystore` files are ignored. With no properties file, release compilation still runs but outputs an unsigned APK for verification.

Before shipping:

1. Preserve the existing production signing identity; never create a replacement if Play App Signing expects the old upload key.
2. Register release SHA-1/SHA-256 in Firebase and obtain the refreshed `google-services.json`.
3. Run the manual backend/device matrix in `docs/TESTING.md`.
4. Build a signed AAB and inspect its bundle manifest with `bundletool` (Android `apkanalyzer` parses APKs, not the AAB layout); inspect a generated APK with `apkanalyzer`, then verify neither artifact contains an ImageKit private key, service-account JSON, refresh token, or release password.
5. Keep `versionCode=40`, `versionName=1.0.40` for this requested release unless the Play track already contains code 40.
