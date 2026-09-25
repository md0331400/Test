# Firebase / Backend Setup — Remaining Operator Work

> Client implementation is complete, but live backend configuration and rules were not available for verification. Nothing in this document is claimed to be deployed.

## Existing project used by the app

- Firebase project: `kotha-bolbo-aso`
- Android package: `com.amisayem.kothabolbo`
- Firebase Auth, Firestore, RTDB, FCM, and Analytics are initialized from the supplied `app/google-services.json`.
- RTDB endpoint is inherited from that file (asia-southeast1).
- Vercel API base: `https://kothabolbo.vercel.app`
- ImageKit public key in the APK: `public_o5b4VUN6FacV5cteyv` (public by design). **Never put the ImageKit private key in the APK.**

## Required console checks

1. **Authentication**
   - Enable Email/Password and Google providers.
   - Verify support email and OAuth consent screen.
   - Add release and debug SHA-1 **and SHA-256** fingerprints to the Android Firebase app.
   - Keep the Web OAuth client `343828860232-n7korusids9jcse961go8tqne8abob59.apps.googleusercontent.com`; Google sign-in requests its ID token.
   - Download a fresh `google-services.json` after changing SHA fingerprints.
2. **Google Drive API**
   - Enable Google Drive API for the OAuth project.
   - Ensure the consent screen allows `https://www.googleapis.com/auth/drive.appdata`.
   - The app uses `GoogleSignIn` + `GoogleAuthUtil` because this exactly preserves the proven `appDataFolder` contract. These APIs are deprecated but still supported; migration should be planned separately and regression-tested against existing backups.
3. **FCM/Vercel**
   - `/api/send-notification` must verify a Firebase ID token and accept exactly: `receiverId,title,body,icon,image,url,messageType,messageId`.
   - Send **data messages** so MessagingStyle, foreground suppression, Reply, and Like are controlled by the app.
   - `url` remains `/?chat=<senderUid>`.
4. **ImageKit/Vercel**
   - `/api/imagekit-auth`: verify Firebase ID token; return `token`, `signature`, `expire` from server-side ImageKit SDK.
   - `/api/delete-imagekit-file`: verify token; authorize the uid/file relationship where feasible; delete with private key server-side.
   - `/api/cleanup-old-media`: verify token; delete eligible temporary media older than 24h.
   - Configure CORS for the production web app if it still uses these endpoints; Android OkHttp itself is not subject to browser CORS.
5. **App update document**
   - Create/update `app_config/version` with `latestVersionCode` (number), `latestVersionName` (string), `updateUrl` (HTTPS), `forceUpdate` (boolean), and `message` (string).
   - Do not add Play in-app update APIs; this Firestore document is the product contract.

## Rules deployment (separate backend change)

- Review `docs/firestore.rules` and `docs/database.rules.json` in the Emulator Suite and Rules Playground.
- They are proposed, least-privilege rules designed for the new equality-only queries; **they are unverified against the live project and are not auto-deployed**.
- `docs/firestore.indexes.json` intentionally has no composite indexes. Message listeners query only one equality field (`senderId == me` or `receiverId == me`) and merge/sort on-device.
- Feed/story privacy intentionally remains client-side for parity. The proposed rules therefore allow authenticated reads and do **not** claim server-side privacy. Server enforcement needs a redesigned query/data model.
- Unique email/phone checks are client transactions/queries, not a cryptographic uniqueness guarantee. Strong uniqueness requires server-owned reservation documents or callable functions.

## Operational cleanup

The app filters expired messages/stories immediately, then runs physical cleanup at startup, in a periodic WorkManager task, and via server cleanup endpoint. Android periodic work is inexact (15-minute platform minimum); backend TTL/cron is still recommended as a defense in depth once its authorization/data-loss behavior is tested.
