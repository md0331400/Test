# Kotha Bolbo Native Rebuild — Implementation Summary

## Delivered

A completely fresh native Android application—no WebView, no converted HTML, and no JS bridge—under package `com.amisayem.kothabolbo`.

- Kotlin 2.1.21, Jetpack Compose Material 3, Navigation Compose, ViewModel, Repository pattern, Coroutines/Flow.
- Room archive/cache/drafts/media queue + Preferences DataStore; Firebase Firestore persistent cache and RTDB persistence.
- Firebase Auth/Firestore/RTDB/FCM/Analytics using the supplied project configuration.
- Native 7-step signup, Gmail-only rules, Google sign-in/profile completion, password reset/link/change.
- Chat list, live active strip, directory search, one-to-one conversation, text/image/voice, reply/edit/delete/clear/remove, drafts, delivery/read ladder, typing/presence, 24-hour retention.
- Native feed/posts/likes/share/search and 24-hour text/image/video stories with a fullscreen viewer.
- Own/other profile, privacy masks, profile editing and 7-day cooldown, photo/cover uploads.
- Settings: theme/appearance, privacy, inactive preference (honestly not enforced), password, legal links, exact two-step deletion, logout.
- ImageKit signing through Vercel; no private key in the app. Unified 1 MB image compression policy.
- Google Drive `appDataFolder` backup/restore with Gmail lock, restore-before-backup, local archive, 20-second auto-backup debounce, explicit error codes, and backup deletion.
- FCM MessagingStyle channel `chat_messages_v3`, stable conversation IDs, foreground suppression, inline Reply and Like without launching the UI, duplicate-safe stale-session fallback.
- Firestore `app_config/version` update flow (optional or forced); no Play in-app-update dependency.
- Dark-first navy/cyan/purple brand, official unchanged artwork, light/system modes and accents, SplashScreen API, portrait lock, offline banner/cache boot.

## Architecture boundaries

Compose screens only render state and call ViewModel intents. The ViewModel coordinates repositories; Firebase, Vercel, ImageKit, Drive, Room, and DataStore operations live outside screens. `AppContainer` provides explicit manual dependency injection. The flat `messages` collection remains intact; equality-only sender/receiver listeners are merged and sorted on-device, avoiding composite indexes and full message collection scans.

## Important fixes / deliberate decisions

1. **Delete-for-Me resurrection fixed:** both role flag and `deletedFor` are written; every cloud/archive/list filter checks both; archive deletion remains local.
2. **Notification action security fixed:** Firebase SDK persisted auth replaces the old hardcoded Web API key and plaintext refresh-token store.
3. **All images standardized to 1 MB with compression,** resolving the old profile/cover 10 MB inconsistency.
4. **Daily 18:00 reminder consciously removed:** it never had a first schedule, had no registered boot receiver, and is absent from the requested product feature set. No broken half-feature ships.
5. Dead privacy trap/orphan bridge paths (`openUserProfileModal`, `onNativeFileSelected`) and HTML/XSS surface were not ported.
6. Feed/story privacy remains client-filtered for backend parity and is explicitly not represented as server privacy.
7. Proposed backend rules are separate files and explicitly unverified; no claim was made that unknown live rules were changed.

## Build outputs

See `/artifacts` and `artifacts/SHA256SUMS.txt`:

- `KothaBolbo-v1.0.40-debug.apk` — installable, debug-signed.
- `KothaBolbo-v1.0.40-release-unsigned.apk` — R8/resource-shrunk, requires production signing.
- `KothaBolbo-v1.0.40-release-unsigned.aab` — R8/resource-shrunk, requires production signing.

The developer release keystore and passwords were intentionally not supplied, generated, or committed.

## Remaining external configuration

Full details: `docs/FIREBASE-SETUP.md` and `docs/BUILD-RELEASE.md`.

- Review/test/deploy proposed Firestore/RTDB rules.
- Register debug/release SHA-1 and SHA-256 and refresh `google-services.json` if needed.
- Enable Drive API/consent scope and validate Vercel endpoint auth contracts.
- Maintain `app_config/version` and sign with the existing production upload identity.
- Run the live two-device matrix in `docs/TESTING.md`.

## Known limitations (not hidden)

- Live Firebase/Vercel/ImageKit/Drive behavior could not be exercised without console access and real devices; compilation/unit/static verification is complete, manual backend gates remain listed.
- Message/story physical expiry is client-triggered (startup + 60-second foreground cleaner + inexact WorkManager). If all clients are closed, documents may remain until a client runs; UI still hides expired content immediately. A reviewed server TTL/cleanup is the only strict always-on guarantee.
- Feed/story private visibility is client-side only under the current data model.
- Unique email/phone pre-checks are best-effort; truly race-proof phone uniqueness needs server-owned reservations/functions.
- Drive backs up JSON and media URLs, not media bytes; temporary media URLs can expire.
- Google Sign-In/GoogleAuthUtil are deprecated APIs retained specifically for proven `drive.appdata` parity; a future Identity/Credential Manager migration needs backup compatibility testing.
- Inactive-account duration is stored but deliberately not enforced.
- No groups, calls, friend requests, blocking, forwarding, message reactions (except notification Like), comments, or moderation—these are intentionally not product features.
