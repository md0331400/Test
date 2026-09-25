# Test Record & Manual Release Matrix

## Automated/build verification completed in this workspace

- `compileDebugKotlin` — passed.
- `compileReleaseKotlin` — passed.
- `testDebugUnitTest` — passed (privacy masking, E.164 normalization, and exact 24-hour cutoff boundary).
- `assembleDebug` — passed; debug APK signed by the generated Android debug key.
- `assembleRelease` — passed with R8 + resource shrinking; intentionally unsigned without developer secrets.
- `bundleRelease` — passed; intentionally unsigned.
- APK inspection — package `com.amisayem.kothabolbo`, versionCode `40`, versionName `1.0.40`, minSdk `26`, targetSdk `36`; R8 release is one DEX.
- Secret/static checks — the only `AIza…` value is inside the supplied Firebase client configuration; no WebView, JS bridge, refresh-token store, ImageKit private key, fake keystore, `TODO()`, or placeholder URL exists in production source.

The sandbox has no Android emulator/device and no privileged access to the live Firebase, Vercel, ImageKit, Google OAuth, or Drive consoles. The following backend/device checks are therefore a **release-gating manual matrix**, not falsely marked as passed.

## Manual device matrix (two real accounts / two devices)

### Install / boot / navigation

- [ ] Clean install shows official icon via SplashScreen API, required privacy gate, then all four onboarding pages.
- [ ] Back leaves the first-run gate; Feed/Profile/Settings back returns to Chat before exiting.
- [ ] Airplane-mode cold boot paints cached profile/chats and shows the offline banner; 12-second fallback shows Retry.
- [ ] Rotation remains portrait; keyboard resize does not obscure the composer.

### Authentication / signup

- [ ] Unknown-email login gives the pre-check error; invalid password, disabled user, rate limit, and no-network errors are friendly.
- [ ] Forgot-password email is received.
- [ ] Seven signup steps enforce Gmail, year 1900..today, gender, E.164 phone, email/phone uniqueness, password 6+, match, and terms.
- [ ] All country prefixes work: +880/+91/+92/+1/+44/+971/Other.
- [ ] Optional image over 1 MB is compressed; account still exists with DiceBear fallback if upload fails.
- [ ] Google new-user flow forces phone/DOB/gender completion and auto-binds the selected Gmail.
- [ ] Debug and production Google sign-in both work after registering SHA-1/SHA-256; deliberate bad SHA surfaces error 10 guidance.
- [ ] Google account can set up a password; password account requires old-password re-auth to change it.

### One-to-one chat

- [ ] People Search excludes self, masks private contact fields, and opens profile/chat.
- [ ] Self deep link is ignored; signed-out notification deep link waits until login.
- [ ] Text appears optimistically as Sending → Sent → Delivered → Seen on two devices.
- [ ] Image preview/compression/upload, voice record/pause/resume/discard/send, and <1000-byte rejection work.
- [ ] Voice is m4a/AAC at 24 kbps / 16 kHz and duration is stored in seconds.
- [ ] Reply quote, jump/highlight target behavior, missing-target message, edit-own-text, and `(edited)` marker work.
- [ ] Swipe-to-reply direction is sent← / received→; long press shows ownership-correct actions.
- [ ] Delete for Me remains hidden after refresh/restart (regression for resurrection bug).
- [ ] Delete for Everyone removes on both devices; Clear is soft for one side and physical after both clear; Remove List never deletes messages.
- [ ] Reopening a removed contact restores `deletedFor` visibility but never resurrects per-message Delete for Me.
- [ ] Draft survives restart; 24-hour banner, date dividers, unread badges, new-message jump, and search-by-name work.
- [ ] RTDB presence uses `online/lastSeen`; typing writes exact fields, turns off at 2.2 s, hides at 3.5 s, and rejects >6 s stale entries.
- [ ] At 24-hour boundary UI hides immediately; in-app 60-second cleanup removes document/media; WorkManager is a safety net.

### Notifications

- [ ] POST_NOTIFICATIONS prompt appears where required; channel is exactly `chat_messages_v3`, HIGH, custom sound/vibration.
- [ ] Background data push creates circular-avatar MessagingStyle grouped by stable sender hash.
- [ ] Foreground app suppresses system notification and plays in-app message sound only for fresh incoming rows.
- [ ] Tap opens the correct chat; notification cancels on open.
- [ ] Inline Reply and Like (`👍`) send without opening; stale-session failure notification opens once and auto-retries after 350 ms without duplicates.
- [ ] Token startup/new-token sync and logout `arrayRemove` work across two devices.

### Feed / stories / profile

- [ ] Post text/image limits, public/private client filtering, like toggle/count floor, share payload, own delete + ImageKit cleanup, search, and no-comment UI.
- [ ] Text/image/video story limits (5 KB/1 MB/50 MB), others-first bar, one own card, Create card, viewer controls, and own delete.
- [ ] Expired stories disappear immediately and are physically deleted by the shared 60-second cleaner under the reviewed rules.
- [ ] Profile masks email/phone and each DOB part; all-private DOB renders `Private`.
- [ ] Profile edit cooldown persists in Firestore for 7 days; bio remains editable; changed phone is duplicate-checked.
- [ ] Photo/cover replacement deletes or queues the old ImageKit file; every image uses the unified 1 MB policy.

### Drive / settings / destructive paths

- [ ] First Drive selection binds case-insensitively; different Gmail is hard-blocked.
- [ ] Automatic restore occurs once per process; manual restore works repeatedly; backup always ON and debounces 20 seconds.
- [ ] Backup is `kothabolbo_backup.json` in `appDataFolder`; restore-before-backup, uid mismatch block, merge-by-ID, and local-only archive verified.
- [ ] Each `DRIVE_*` path is exercised: no file, empty/corrupt, quota, revoked permission, network error, foreign uid.
- [ ] Delete Drive backup removes the Drive file and all local `kb_drive_*` binding keys.
- [ ] Theme mode/accent persist; inactive months persist but no deletion happens (honest limitation text shown).
- [ ] Firestore update dialog handles optional/forced and opens `updateUrl` externally—no Play in-app update API.
- [ ] Logout writes offline, removes FCM token, signs out Google/Firebase, and returns to login.
- [ ] Account deletion requires two steps and the exact phrase `i want delete my Account`; stale auth blocks before data deletion; fresh auth completes documented scope.

### Release/security

- [ ] Review and emulator-test `docs/firestore.rules` and `docs/database.rules.json`; deploy only after approval.
- [ ] Confirm release SHA values against Firebase/Google Cloud.
- [ ] Sign AAB with the existing production upload identity; verify Play App Signing acceptance.
- [ ] Exercise Vercel authentication/authorization and ensure private ImageKit/service credentials exist only server-side.
- [ ] Run Play pre-launch report and accessibility scanner (TalkBack labels, touch targets, contrast, font scaling).
