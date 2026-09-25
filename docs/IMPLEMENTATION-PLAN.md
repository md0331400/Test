# Kotha Bolbo — Native Android Rebuild: Implementation Plan

Fresh native rebuild per `KothaBolbo-TECHNICAL-REPORT.md` (source of truth).
Old WebView implementation in `md0331400/Test` is **reference only** — nothing is ported mechanically.

## 1. Project architecture

- Single `:app` module, package `com.amisayem.kothabolbo`, Kotlin, **Jetpack Compose + Material 3**, MVVM.
- Layers: **UI (Compose screens + ViewModels) → Repository → Firebase / Vercel API / ImageKit / Drive / Room / DataStore**.
- Manual DI via `AppContainer` (no Hilt — avoids unnecessary abstraction at this app size).
- Coroutines + `StateFlow` everywhere; `callbackFlow` bridges for Firebase listeners.
- One `MainActivity` (portrait, singleTop) hosting a Navigation-Compose graph; Android-native back stack (no browser-style history).
- Versions: AGP 8.11.1, Gradle 8.14.3, Kotlin 2.1.21 (+ Compose compiler plugin), KSP 2.1.21-2.0.2, compile/target SDK 36, minSdk 26, versionCode 40, versionName 1.0.40.

## 2. Firebase setup

- Reuse shipped `google-services.json` (project `kotha-bolbo-aso`, RTDB asia-southeast1).
- SDKs: firebase-auth, firebase-firestore (persistent disk cache ON, 100 MB), firebase-database (RTDB persistence ON), firebase-messaging, firebase-analytics, BoM 33.16.0.
- **Query redesign (per report §26.D):** no full-collection `orderBy(timestamp)` scans of `messages`. Equality-only queries (`senderId==me`, `receiverId==me`, conversation pairs) + client-side sort → **no composite indexes required**; chat list = 2 message listeners + `chatContacts` listener merged locally.
- Security rules are UNKNOWN (not in repo). Proposed least-privilege rules that still support the app's queries are provided in `docs/firestore.rules` + `docs/database.rules.json`, clearly separated from client work (deploy = backend task, see FIREBASE-SETUP.md).

## 3. Navigation

Routes: `root → privacyGate → onboarding → login → signup → main{chatList | feed | profileTab | settings}` + stack screens `conversation/{uid}`, `profile/{uid}`, `createPost`, `storyViewer/{storyId}`, `driveBackup`. Bottom bar order (per rebuild spec): **Chat · Feed · Profile · Settings**, Chat default, unread badge (cap 99+). Back behavior: dialogs → sheet → conversation → list → tab → exit (Predictive-back-friendly, no history emulation). Deep link: intent extra `chat=<uid>` (+ self-guard + pending queue across login).

## 4. Authentication

- Login: email pre-check (`users where email==`) → "No account found…" path; friendly Firebase error map (§3.7); show/hide password; forgot-password dialog; welcome toast on fresh login.
- Signup: exact **7 steps** (name → DOB wheel picker 1900..now + gender → Gmail-only email + duplicate check → E.164 phone with BD/IN/PK/US/UK/AE/Other + duplicate check → password ≥6 + confirm → optional photo ≤1MB → summary + terms). Creates `users/{uid}` with all §4.1 fields, privacy flags default false, DiceBear PNG fallback avatar, `new_user` flag → completion nag + backup nag + POST_NOTIFICATIONS request.
- Google Sign-In: play-services-auth with web client ID (`343828860232-n7korusids9jcse961go8tqne8abob59.apps.googleusercontent.com`) + `drive.appdata` scope; two flows: `auth` (Firebase credential sign-in; new user → create doc + auto-bind Gmail for Drive) vs `drive` (account picker only, never signs into Firebase). Error-10 SHA guidance dialog.
- Set-Up-Password (LINK credential) vs Change-Password (re-auth + update) split for Google-created accounts.

## 5. Local storage

- **DataStore (prefs):** `kb_onboarding_done_v1`, appearance (system/dark/light), accent theme, `kb_inactive_months` (default 12), `kb_new_user_{uid}`, Drive binding (`kb_drive_email_{uid}`, `kb_drive_bound_at_{uid}`, `kb_drive_last_{uid}`), FCM token.
- **Room (`kotha.db`):** `restored_messages` (Drive restore archive, uid-keyed, never re-uploaded), `media_delete_queue` (`kb_imagekit_delete_queue` equivalent: fileId + expiresAt), `chat_cache` (instant first paint), `profile_cache`, `drafts` (`kb_draft_{me}_{them}`).
- No plaintext refresh tokens anywhere (old `native_session` risk removed — notification actions use the Firebase SDK's own persisted session).

## 6. Chat

- Flat `messages` collection; threads derived from (me,them)/(them,me) equality queries; **24 h cutoff filter** on every surface + physical cleanup (app-start, periodic in-app loop, WorkManager safety net) + `/api/cleanup-old-media` sweep.
- Status ladder Sending→Sent→Delivered→Seen with exact flip conditions; delivered flipped by receiver client; read+seen batched on conversation open.
- Delete-for-Me = per-side flags (`deletedBySender/deletedByReceiver`) honored **in all filters** (fixes resurrection bug); Delete-for-Everyone = physical (own only); Clear Conversation = `deletedFor arrayUnion(me)` + physical delete when both sides cleared; Remove-from-list = `chatContacts.removed` only, never touches messages; re-open un-removes contact + `arrayRemove(me)` from `deletedFor` (legacy-data parity).
- Typing via RTDB `typing/{me}_{them}` (2.2 s auto-off write; 6 s staleness + 3.5 s UI hide read). Presence via RTDB `status/{uid}` + `onDisconnect` — Firestore `users.online` ignored.
- Chat list: cache-first (Room) → live merge of contacts + 2 message listeners + archive previews; unread badges; row swipes (right >60dp Remove / left >60dp Clear, confirm + haptic); search filters by name; tap-empty opens People Search (directory scan, masked phone/email, Message/Profile actions).
- Conversation: date dividers, sorted-insert, reply quotes (+ jump & 1.5 s highlight), `(edited)`, swipe-to-reply (sent←/received→, ~170dp trigger), 520 ms long-press sheet (Reply/Edit/Delete-for-Me/Delete-for-Everyone by ownership), new-messages jump FAB, autoscroll rules, drafts, 24 h notice banner, notification cancel on open, self-chat guard.

## 7. Media

- ImageKit REST via OkHttp: signature from `/api/imagekit-auth`, multipart upload (`file,fileName,publicKey,folder,signature,token,expire`), 30 s timeout, folders `avatars/covers/chat_images/voice_messages/stories/feed`; public key `public_o5b4VUN6FacV5cteyv` (client-side by design); **private key never in app**.
- Unified limit resolution (report's 1MB-vs-10MB conflict): **all images ≤ 1 MB everywhere** — auto-compressed (quality/downscale) to fit; video stories ≤ 50 MB (reject above); voice m4a/AAC 24 kbps/16 kHz native `MediaRecorder` (pause/resume/timer/discard/send, reject < 1 KB).
- Chat images/voice = temporary media → queued into Room `media_delete_queue` with 24 h expiry; queue drained every 60 s in-app + by WorkManager + `/api/delete-imagekit-file` (Bearer ID token).

## 8. Feed

`posts` listener (createdAt desc), client privacy filter (`public || authorId==me` — documented as client-side only, matching current backend), like toggle (`likes` array + `likeCount` floor 0), share sheet (`text + imageUrl + " — via Kotha Bolbo"`), own-delete (+ImageKit), search by text/author, empty states, denormalized author fields frozen at post time. No comments (not a product feature).

## 9. Stories

`stories` listener; `expiresAt = createdAt + 24h`; display filter immediate; physical delete + media queue by 60 s in-app cleaner and WorkManager (shared-cleanup model preserved). Create: text ≤5KB / image ≤1MB / video ≤50MB + caption + privacy → ImageKit `stories`. Viewer: fullscreen, image contain, native `VideoView` controls + autoplay, text centered, close ✕, no auto-advance. Story bar: deterministic name-gradient tiles, others first, ≤1 own card, Create Story card.

## 10. Profiles

Own tab (cover/avatar/gender pill/bio/Create Post/Edit Profile/Change Photo/Change Cover/Details/Contact/own posts) + Profile page (self/other, masked contacts per privacy flags, Send Message hidden for self, replace-semantics navigation). 7-day cooldown on name/phone/gender/birthday via server `lastProfileEdit` (survives restart; remaining-days message; bio exempt; phone uniqueness re-check). Masking formulas exactly §4.4. DiceBear PNG deterministic avatars (seed=name, gender param).

## 11. Settings

My Profile card (tap=change / long-press=view media), Account (profile, change/set password, privacy, inactive account 1/3/6/12/18/24 default 12 — stored, explicitly **not enforced**, honest UI note), Delete Account (2-step, exact phrase `i want delete my Account`, full §31 scope, recent-login re-auth handling), App (theme accents + dark/light/system, Drive backup, Privacy Policy & Data via Custom Tabs), About (Md. Abu Sayem, support.amisayem@gmail.com, version line), Logout (presence off, FCM token arrayRemove, Google sign-out, success banner).

## 12. Notifications

FCM token lifecycle (cold start + onNewToken → prefs + `users/{uid}.fcmToken/fcmTokens[]/fcmTokenUpdatedAt`). Channel `chat_messages_v3` (HIGH, `notification_sound`, vibrate 0/220/90/220, badge). MessagingStyle + Person (circular avatar, LruCache, DiceBear PNG fallback), stable id = `senderId.hashCode()`, foreground suppression via ProcessLifecycleOwner. Every send → POST `/api/send-notification` (`receiverId,title,body,icon,image,url=/​?chat=<senderUid>,messageType,messageId`, Bearer ID token). **Reply (RemoteInput) / Like (`👍`) without opening the app**: receiver uses Firebase SDK persisted auth (no hardcoded API key, no plaintext refresh token) → Firestore write + push; failure → open conversation with pending auto-send (duplicate-safe: pending action consumed once).

## 13. Google Drive

`appDataFolder` REST (scope `drive.appdata`, token via `GoogleAuthUtil`), file `kothabolbo_backup.json`, schema `{version:1,createdAt,user:{uid,…},messages:[…]}`. Gmail lock (case-insensitive, hard-block different account), auto-backup ON when bound, 20 s debounce, restore-before-backup, auto-restore once per session (+1.5 s after binding), manual restore resets guard, restore only when `backup.user.uid == current uid`, archive merge by id, media binaries never backed up. Error codes `DRIVE_STORAGE_FULL / DRIVE_PERMISSION_ERROR / DRIVE_BACKUP_NOT_FOUND / DRIVE_BACKUP_EMPTY / DRIVE_RESTORE_ERROR / DRIVE_BACKUP_ERROR` with the documented alert flows.

## 14. Update system

Once per session: `app_config/version` → compare `latestVersionCode > versionCode(40)` → dialog (force hides Later + non-dismissible) → Update Now opens `updateUrl` externally. No Play in-app update APIs.

## 15. Testing

Compile-verify full release+debug builds (R8 on). Unit-testable pure logic (masking, validators, time formats, retention windows, backup merge) covered by local JVM tests (`test/`). Manual device test matrix from spec §49 documented in `docs/TESTING.md` (backend-dependent flows need the live Firebase/Vercel project — cannot be executed headless here; every such dependency is isolated behind a repository and listed in FIREBASE-SETUP.md).

## 16. Release build

Release signing reads optional `release.properties` (storeFile/storePassword/keyAlias/keyPassword) — never committed; absent file → unsigned release build still possible for verification. R8 minify + resource shrink, hardened ProGuard rules, no secrets in APK beyond Firebase client config (by design), permissions trimmed (INTERNET, ACCESS_NETWORK_STATE, POST_NOTIFICATIONS, RECORD_AUDIO, VIBRATE — CAMERA/USE_BIOMETRIC dropped as unnecessary), cleartext blocked, `allowBackup=false`, portrait.

## Deliberate decisions (documented, not silent)

1. **Daily 18:00 reminder: dropped.** It never actually fired in production (first alarm never scheduled, boot receiver unregistered) and is absent from the rebuild feature spec. No broken half-feature is shipped. (§42 allows deliberate removal.)
2. **Profile/cover limit unified to 1 MB** (with auto-compression) instead of reproducing the 10 MB inconsistency (§39).
3. **XSS risk eliminated** by native rendering; `openUserProfileModal` privacy trap and `onNativeFileSelected` orphan path not ported (§42).
4. **Notification Reply/Like** uses Firebase SDK session instead of hardcoded web API key + plaintext refresh tokens (§40).
5. **Equality-only Firestore queries** replace full-collection scans; matching proposed security rules delivered separately in `docs/` (§40/§41).
6. Feed/story privacy remains **client-side filtered** (parity with live backend); server-side enforcement is documented as a separate backend change (§41).
