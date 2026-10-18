# Kotha Bolbo 💬

A native Android messenger built as a Kotlin **WebView shell** that loads a single local
`index.html` (HTML/CSS/JS). It uses **Firebase** (Auth, Firestore, Realtime Database,
Cloud Messaging, Analytics), **Google Sign-In / Drive** for optional chat backup, and
**ImageKit** for media hosting.

> ⚠️ This is the **GitHub-source** copy. It intentionally does **not** include the
> release keystore (`kothabolbo-release.jks`) or `release.properties` — those live only
> on your machine and **must never be committed**. See `.gitignore`.

---

## ✨ Features

- **Feed · Chat · Profile · Settings** bottom navigation
- Real-time 1-to-1 chat with text, images and voice messages
- **Profile tab** (social-profile layout): cover photo + avatar + bio + about + contact info
- **Edit profile**: name, phone, gender, birthday, bio (with existing 7-day change rules)
- Image zoom / full-screen viewer for profile & cover photos
- Friend search opened from the Chat search bar (people discovery)
- Multi-step registration (name → birthday → email → phone → password → photo → terms)
- First-launch onboarding + privacy policy / terms acceptance
- Native Android **MessagingStyle** notifications (sender identity + avatar, Reply / Like)
- Optional **Google Drive backup** (auto, silent) + on-device restore
- Themes (accent) + light/dark appearance modes
- Privacy controls for phone / email / date of birth visibility

## 🧱 Tech stack

| Layer | Tech |
|---|---|
| Language | Kotlin, HTML/CSS/JS |
| UI shell | `AndroidWebView` (loads `assets/index.html`) |
| Auth | Firebase Authentication (email/password + Google) |
| Database | Cloud Firestore (messages/users/contacts) + Realtime Database (presence) |
| Push | Firebase Cloud Messaging |
| Media | ImageKit (authenticated upload via Vercel backend) |
| Backup | Google Drive (app data folder) |
| SDK | `minSdk 26`, `targetSdk 36`, `compileSdk 36` |

## 🚀 Build (release APK)

Requires Android **SDK Platform 36**.

Create `release.properties` in the project root:

```properties
storeFile=kothabolbo-release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=kothabolbo
keyPassword=YOUR_KEY_PASSWORD
```

Place `kothabolbo-release.jks` alongside it, then:

```bash
./gradlew :app:assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

> Pressing `--no-daemon` helps on low-memory devices.
>
> Release `lintVital` is disabled in `app/build.gradle.kts` so the build does not
> fail on mobile/SSL network issues that block lint. This does not affect the APK.

## 🔐 Security notes

- `.gitignore` blocks keystores, signing properties, `.idea/`, `build/`, and `local.properties`.
- Passwords are never stored in Firestore (Firebase Auth handles them).
- Cleartext HTTP is disabled (`network_security_config.xml`).
- The web UI logs nothing sensitive.

## 📄 Legal

- Play Store / privacy / account-deletion pages live in [`playstore-web/`](playstore-web/).
- Support: **support.amisayem@gmail.com**

## 👤 Author

Made by **Md. Abu Sayem** — “Kotha Bolbo”, created by Sayem.
