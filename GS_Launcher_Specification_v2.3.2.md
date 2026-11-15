# GS Launcher — Final Specification v2.3.2

**Document type:** Final implementation-ready specification (no code)  
**Coding gate:** Implementation begins only after the user explicitly writes **Coding start**  
**Reference:** Niagara Launcher 1.16.36 (observable behavior only)  
**Target:** GS Launcher — original native Android launcher, Kotlin  
**Evidence base:** 20 screenshots (Screenshot_13–32), verified APK, reference repos  
**Version:** v2.3.2 — corrections on v2.3.1: Backup & Restore hard rules; launchable-only app drawer; default-one-row-per-app; HOME role API 26–28 vs 29+. **D1–D20, five exclusions, and all approved v2.3.1 behavior unchanged**  
**Date:** 2026-11-16  
**Application ID:** `com.amisayem.gs.launcher`  

**Rules for this document**
- Specification / research only — **no code, no Android Studio project, no source files**
- No feature is claimed implemented  
- Confirmed facts, technical recommendations, and residual technical risks are labeled  
- **Every decision D1–D20 is locked** (see §16)  

**Evidence labels**

| Label | Meaning |
|-------|---------|
| **[S-APK]** | Verified from APK (manifest, resources, DEX strings) — not full decompile |
| **[S-SHOT]** | Verified from provided screenshots |
| **[S-REPO]** | Verified from reference repository (non-Niagara code) |
| **[S-INFER]** | Inferred from available evidence |
| **[GS-CONF]** | Confirmed product requirement (user-approved decision) |
| **[GS-PROP]** | Technical recommendation (implementation detail; not a new product choice) |
| **[UNVER]** | Unverified Niagara behavior — not treated as a fact |

---

## Table of Contents

1. [Project Overview](#1-project-overview)  
2. [Reference APK and Research Verification](#2-reference-apk-and-research-verification)  
3. [Evidence and Confidence Matrix](#3-evidence-and-confidence-matrix)  
4. [Confirmed Feature Inventory](#4-confirmed-feature-inventory)  
5. [GS Launcher Feature Specifications](#5-gs-launcher-feature-specifications)  
6. [Five Excluded Features](#6-five-excluded-features)  
7. [UI/UX and Interaction Specifications](#7-uiux-and-interaction-specifications)  
8. [Android Architecture](#8-android-architecture)  
9. [Permissions and Privacy](#9-permissions-and-privacy)  
10. [Android Compatibility](#10-android-compatibility)  
11. [Data Storage](#11-data-storage)  
12. [Performance and Battery Requirements](#12-performance-and-battery-requirements)  
13. [Error Handling and Edge Cases](#13-error-handling-and-edge-cases)  
14. [Testing and Acceptance Criteria](#14-testing-and-acceptance-criteria)  
15. [Release Requirements](#15-release-requirements)  
16. [Final Decision Summary (D1–D20)](#16-final-decision-summary-d1d20)  
17. [Consistency Review (v2.3)](#17-consistency-review-v23)  
18. [Technical Risks and Open Technical Questions](#18-technical-risks-and-open-technical-questions)  

---

## 1. Project Overview

### 1.1 Product

| Field | Value |
|-------|-------|
| App name | **GS Launcher** |
| Application ID | **`com.amisayem.gs.launcher`** **[GS-CONF D13]** |
| Platform | Android |
| Language | Kotlin |
| UI toolkit | **Jetpack Compose only** |
| Architecture | Lightweight native launcher; **offline-first** |
| WebView / HTML / CSS / JS | **Prohibited** |
| Branding | Original GS Launcher only |
| Network in v1 | **No backend, no server, no Firebase SDK, no accounts, no analytics, no ads** — core features work fully offline **[GS-CONF]** |

### 1.2 Goal

Reproduce the **observable Niagara Launcher experience** through an original Kotlin implementation, **excluding only** the five categories in §6, and applying all locked decisions in §16.

### 1.3 Research status (honest)

| Activity | Status |
|----------|--------|
| Download APK + SHA-256 verify | Done |
| Unzip + inventory | Done |
| Manifest component audit (Androguard) | Done |
| Resource string dump (62,223 strings) | Done |
| DEX feature-string mining | Done |
| Full JADX / Smali decompile | **Not performed** |
| On-device runtime testing of reference | **Not performed** |
| Screenshot analysis (all 20) | Done |
| Implementation / coding | **Not started** |

**No claim of source-level verification of Niagara exists in this document.**  
**String extraction is not proof of complete feature behavior.**

### 1.4 Five exclusions (unchanged) **[GS-CONF]**

1. Custom Icons  
2. Custom Fonts  
3. Custom Themes  
4. Widgets & Widget Stack  
5. Backup & Restore  

### 1.5 Offline-first architecture constraints (user-mandatory)

| # | Requirement |
|---|-------------|
| 1 | **Offline-first**, lightweight native Android launcher |
| 2 | **No** backend, server, **Firebase SDK**, online account system, analytics, ads, or unnecessary network in **v1** |
| 3 | **All core launcher features** work **without internet** |
| 4 | Architecture stays **clean and modular** so Firebase *could* be added later for remote config / update-related capabilities — **without** adding Firebase now |
| 5 | **Do not add Firebase now**; **do not overengineer** solely for future integration |
| 6 | Keep the app **lightweight**; avoid unnecessary dependencies |
| 7 | **Firebase is not an APK update installer** — see §1.6 |

### 1.6 Future Firebase / online updates (clarification) **[GS-CONF + tech note]**

| Topic | Clarification |
|-------|----------------|
| Firebase in v1 | **Not included** |
| Future Firebase (if ever approved) | Could provide **remote configuration** and related **online service** capabilities in a later production release |
| Firebase ≠ app installer | Firebase **does not** install or update the launcher APK. App binary distribution remains **Google Play** (or another store/sideload the user chooses). “Online update-related capabilities” means **config/feature flags/services**, not self-updating the APK from a Firebase backend |
| Current app updates | Play Store / package installer path only — **no custom in-app APK updater in v1** |
| Abstraction for future Firebase | Clean module boundaries only; **no** extra DI/network layers “just in case” |

### 1.7 Locked scope consequences (summary)

| Decision | Effect |
|----------|--------|
| D1–D3 | **No weather** in v1 |
| D17 | **No web search suggestions** in v1 |
| D9 | **No Quick Lock** in v1 |
| D15 | **No work profile** support |
| D16 | **No contact search**; no `READ_CONTACTS` |
| D20 | **No Usage Breaker** |
| D11 | Notifications **view-only** (no inline reply/actions system) |
| D12 | Show **all installed launchable apps**; package visibility including **`QUERY_ALL_PACKAGES` if technically necessary**; one row per app |
| D13 | Package = **`com.amisayem.gs.launcher`** |
| D14 | **`allowBackup=false`**; no in-app backup UI/features |
| D18 | **Fixed icon size** — no setting |
| D19 | **Local rename** of displayed labels only |
| D4–D8, D10 | Favorites limit/reorder/folders; GS Button; search triggers — see §16 |

---

## 2. Reference APK and Research Verification

### 2.1 Authenticity

| Check | Result |
|-------|--------|
| Release | `https://github.com/md0331400/Test/releases/tag/rea` |
| Asset | `Niagara.Launcher_1.16.36.apk` (14,599,398 bytes) |
| SHA-256 computed | `39f312387db73af55e9913926be7b2dfd0f35e5cd7c640ec6fb8e83059f06e1c` |
| SHA-256 on release | `39f312387db73af55e9913926be7b2dfd0f35e5cd7c640ec6fb8e83059f06e1c` |
| Match | **Yes** |

### 2.2 Extraction inventory

**Present under** `/home/user/gs_launcher_research/apk_extracted/`:

| Artifact | Status |
|----------|--------|
| `AndroidManifest.xml` (binary AXML) | Present |
| `classes.dex`, `classes2.dex` | Present |
| `resources.arsc` | Present |
| `res/`, `assets/`, `lib/`, `META-INF/` | Present |

**Missing (full decompile not done):** `apktool.yml`, `smali/`, JADX Java tree.

**Reference APK metadata (Niagara, not GS):** package `bitpit.launcher`, versionName **1.16.36**, versionCode **1642**, minSdk **26**, targetSdk **36**, compileSdk **37**.  
**GS package:** `com.amisayem.gs.launcher` — **different product**.

### 2.3 Manifest components (reference only) **[S-APK]**

- Home: `bitpit.launcher.ui.HomeActivity` (`HOME`/`LAUNCHER`/`DEFAULT`)  
- Settings, themes, icons, shortcuts, notification batching, Sesame, purchase — reference features; many excluded or not in GS v1  
- `NotificationListener`, `LockScreenService`, widget restore receiver — reference; GS scope per §16  
- Firebase/Billing on reference — **not for GS**

### 2.4 Reference repositories

| Repo | Role |
|------|------|
| `morluto/rea` | Reverse-engineering **tooling**; **not** Niagara source |
| `md0331400/Test` release `rea` | Hosts the APK + generic archives; archives are **not** Niagara source |

### 2.5 Research limitations

1. APK extraction and resource inspection performed.  
2. Full JADX / Smali **not** performed.  
3. **No source-level verification** of Niagara control flow.  
4. String presence ≠ proven runtime behavior.  
5. Screenshots partial.  
6. No proprietary Niagara code/assets/branding will be reused.

---

## 3. Evidence and Confidence Matrix

| Feature | S-SHOT | S-APK | S-INFER | GS decision | Confidence | Notes |
|---------|:------:|:-----:|:-------:|:-----------:|------------|-------|
| Vertical list home + favorites | ✓ | ✓ | | Include | High | S14–15 |
| Normal Android icons + labels | ✓ | | | Include | High | Fixed size D18 |
| Wave alphabet + expand | ✓ | ✓ | | Include | High | S15–16 |
| Favorites onboarding + edit | ✓ | ✓ | | Include | High | S17–18 |
| Favorites limit 12 (apps+folders) | | | | **D4 locked** | Product decision | Count rule §5.1 |
| Drag reorder apps+folders | | edit exists | | **D5 locked** | Product decision | Edit Mode |
| Folder as favorite (1 item) | | | | **D6 locked** | Product decision | |
| Default-launcher prompt | ✓ | ✓ | | Include | High | S19 |
| Settings tree | ✓ | ✓ | | Include (no Quick Lock/Usage Breaker rows) | High | S20, S28–31 |
| Search overlay | ✓ | ✓ | | Include; triggers **D10** | High UI | Swipe down + icon |
| Context menu basics | | ✓ | ✓ | Include | Medium | App info/uninstall/favorites/rename |
| Pop-up folders (non-widget) | ✓ | ✓ | | Include | Medium | Details GS-PROP |
| GS Button | ✓ row | ✓ | | Position **D7**, actions **D8**; **Default OFF; Long-press = Settings** | High product | Tap=search, swipe=none |
| Quick Lock | ✓ row | ✓ | | **Excluded D9** | Not in v1 | |
| Weather | ✓ | ✓ | | **Excluded D1–D3** | Not in v1 | |
| Web suggestions | | ✓ | | **Excluded D17** | Not in v1 | |
| Contact search | | ✓ | | **Excluded D16** | Not in v1 | |
| Work profile | | ✓ | | **Excluded D15** | Not in v1 | |
| Usage Breaker | ✓ | ✓ | | **Excluded D20** | Not in v1 | |
| Notification reply | | | | **D11 view-only** | Product decision | |
| QUERY_ALL_PACKAGES | ref has | ✓ | | **D12 include if needed** | Product + policy risk | §9.2 |
| Package name | | | | **`com.amisayem.gs.launcher`** | **D13** | |
| allowBackup | | | | **false** | **D14** | §6.1 |
| Local rename | | ✓ edit name strings | | **D19 allow** | Product + medium tech | Launcher-only |
| Icon size setting | | ✓ size strings | | **D18 fixed, no setting** | Product decision | |
| Package monitoring | | | standard | LauncherApps + broadcasts | Design | No hidden PackageMonitor |

**Confidence scale:** High · Medium · Low · **Product decision** (locked by user).

---

## 4. Confirmed Feature Inventory

### 4.1 Included in GS v1

| Area | Features | Evidence / decision |
|------|----------|---------------------|
| Home | List home, favorites (≤12), clock/date, optional media card, wave alphabet; **no weather** | S15 + D1, D4 |
| App list | A–Z list, **normal system icons at fixed size**, labels, wave scrub; **all launchable apps; one row per app** | S14–16 + D12, D18, §8.4 |
| Favorites | Onboarding, edit multi-select, **max 12 items**, drag reorder apps+folders, **folders as favorites**, persist | S17–18 + D4–D6 |
| Search | Overlay; **swipe down on home** + **search icon**; same UI; Recently installed; local filter | S32 + D10; no web (D17); no contacts (D16) |
| Context actions | App info, Uninstall, Add/Remove favorites, **Rename display label (local)**, shortcuts, folder ops | D19 + S-APK/infer |
| Organization | Hide apps, Pop-up folders (apps/shortcuts only), Suggest often-used, Sort by usage | S28–30 |
| Productivity (included) | Home notifications (**view-only**), calendar agenda/preview, media player, music apps, notification summary | S28–29 + D11; **no Usage Breaker (D20)** |
| Gestures | Alphabet scrub; search via swipe-down + icon; **GS Button** bottom-right, **default OFF**, tap=search, swipe=none, **long-press=Settings** | D7, D8, D10 + audit |
| Settings | Productivity (no weather, no Quick Lock, no Usage Breaker); **Appearance** (clock, battery %); **Advanced** (rotation, haptics, **GS Button default OFF**); Help — no duplicate rows | D1, D9, D18, D20 + audit |
| System | Default home (API 26–28 legacy HOME / **29+ RoleManager**), launch, discovery launchable-only, package callbacks | D12–D15 + v2.3.2 |

### 4.2 Out of GS v1 (reference or prior locks)

| Feature | Treatment |
|---------|-----------|
| Five exclusions (icons/fonts/themes/widgets/backup) | **Excluded (1–5)** |
| Niagara Pro / billing / invite / Firebase / ads | Not in GS v1 |
| Weather | **D1–D3** |
| Web search suggestions | **D17** |
| Quick Lock | **D9** |
| Contact search | **D16** |
| Work profile | **D15** |
| Usage Breaker | **D20** |
| Inline notification reply / action system | **D11** (view-only) |
| Sesame / Digital Wellbeing telemetry | Not in v1 |
| User-facing icon size setting | **D18** (fixed size only) |

---

## 5. GS Launcher Feature Specifications

Behavior is **GS design** informed by evidence. Niagara-exact details may be **[UNVER]**.

---

### 5.1 Favorites — add, remove, reorder, limit

**Evidence:** [S-SHOT] S17–18; [S-APK] favorites keys; Niagara cap/UX **[UNVER]**.  
**Locked:** **D4, D5, D6**.

#### 5.1.1 Limit (D4) — **maximum 12 favorite items**

| Rule | Spec **[GS-CONF D4]** |
|------|------------------------|
| Max total | **12** favorite items |
| Individual app | Counts as **1** |
| Folder in favorites | Counts as **1** |
| Apps **inside** a favorite folder | **Do not** count toward the 12 |
| Onboarding tip | Soft guidance “~4–8 common”; **hard cap = 12** |
| Edit UI | Show **“N/12 selected”** (or equivalent); block add when 12 reached with clear message |
| Recommended badge | Optional on edit screen; algorithm **[UNVER]/[GS-PROP]** (usage if granted else recency) — does not override cap |

#### 5.1.2 Add

| Path | Behavior |
|------|----------|
| Onboarding | Suggestions checkboxes; **Done** saves; cannot exceed 12 |
| Edit favorites | Full app list (A–Z); checkbox; folder create/add path; Done commits |
| Context menu | **Add to favorites** — disabled when at 12/12 |
| Add folder to favorites | Create/select folder → mark as favorite → counts as **1** **[D6]** |

#### 5.1.3 Remove

| Path | Behavior |
|------|----------|
| Edit | Uncheck app or remove folder-from-favorites (folder entity may remain in list) |
| Context menu | Remove from favorites |
| Uninstall / hide | Drop from favorites automatically **[GS-PROP]** |

#### 5.1.4 Reorder (D5)

| Item | Spec **[GS-CONF D5]** |
|------|------------------------|
| Mode | **Edit Mode** on favorites editor |
| Gesture | **Long-press drag** to reorder |
| Scope | **Both apps and folders** as favorite rows |
| Persistence | Integer `sortOrder` per favorite row |
| Edge | Drag cancelled on remove; empty → Home CTA |

**Flow:** Open Edit Mode → toggle membership (≤12) → drag rows (apps + folders) → Done → Home order updates.

---

### 5.2 Pop-up folders

**Evidence:** [S-SHOT] S18, S30; [S-APK] folder/popup strings. Open-folder layout **[UNVER]**.  
**Widget pop-ups:** **excluded (4)** — members are **apps and shortcuts only**.

#### 5.2.1 Concepts

| Term | Meaning |
|------|---------|
| Folder | Named group shown as **one row** in list/favorites |
| Pop-up folder | Tap row → floating panel of member apps |

#### 5.2.2 Create / edit

- Entry: Edit favorites advanced **Create pop-up folder** **[S-SHOT]**; settings Folders; context menu  
- UI: name + multi-select apps  
- Save → folder row in list; optional **favorite** flag (counts as 1 toward 12) **[D6]**

#### 5.2.3 Open / close

| Action | Behavior |
|--------|----------|
| Tap folder | Open pop-up panel (anchor or centered sheet) |
| Tap member | Launch + close panel **[GS-PROP]** |
| Scrim / back / Home | Close panel |
| Long-press folder | Rename, Edit members, Remove folder, Add/Remove favorites |

#### 5.2.4 Membership

- Add/remove apps in folder edit  
- Uninstall member → auto-drop **[GS-PROP]**  
- Hidden apps cannot be added **[GS-PROP]**  
- Sort folder rows by **folder name** **[GS-PROP]**  
- Empty folder → empty state; all uninstalled → auto-delete **[GS-PROP]**

#### 5.2.5 Favorites interaction (D6)

- Folder may be a favorite row = **1 of 12**  
- Member apps inside do not consume favorite slots  

---

### 5.3 GS Button

**Evidence:** [S-SHOT] S30 row; [S-APK] tap/swipe keys. Niagara defaults **[UNVER]**.  
**Locked:** **D7, D8**.

#### 5.3.1 Position & default state (D7 + audit confirmation)

| Attribute | Value |
|-----------|--------|
| **Position** | **Bottom-right** of the home screen **[GS-CONF D7]** |
| Visible when | Enabled only → circular GS control; disabled → hidden |
| **Default state** | **OFF (confirmed decision)** — on first launch the button is hidden until the user enables it **[GS-CONF — audit v2.3.1]** |
| Enable location | Settings → Advanced (or GS Button row) toggle **[GS-PROP structure — see §7.1]** |

#### 5.3.2 Actions (D8) & long-press (audit confirmation)

| Gesture | Behavior |
|---------|----------|
| **Tap** | **Open Search** (same interface as D10) **[GS-CONF D8]** |
| **Swipe** (on button) | **No action** **[GS-CONF D8]** |
| **Long-press** | **Open GS Button settings** (enable/disable) **[GS-CONF — audit v2.3.1]** |

**v1 customization:** enable/disable only; tap/swipe behavior fixed by D8.  
**Excluded:** actions that require widgets/excluded features.

**Behavior matrix:** Button OFF → no tap/swipe/long-press handling (control not shown). Button ON → tap=search, swipe=no-op, long-press=settings.

---

### 5.4 Quick Lock — **EXCLUDED from v1 (D9)**

**Locked:** **D9 — Exclude Quick Lock from v1. Do not implement Accessibility-based screen locking.**

| Item | Spec |
|------|------|
| Feature | **Not in v1** |
| Settings row | **Omit** Quick Lock |
| Accessibility service for lock | **Do not create / do not request** |
| Double-tap alphabet to lock | **Not implemented** |
| Device admin lock / root lock | **Not in v1** |
| Reference evidence | S31, APK lock strings — **not implemented in GS** |

**Technical note (historical accuracy only, not a feature):** `DevicePolicyManager.lockNow()` can be used by an active device admin with `USES_POLICY_FORCE_LOCK` subject to OS rules; **GS does not use this in v1**.

**If ever revisited:** would require a new user decision, separate accessibility/privacy review, and version bump — **not planned**.

---

### 5.5 Weather — **EXCLUDED (D1–D3)**

No weather chip, forecast, settings row, location permission, or weather network use in v1.  
Provider / tap behavior: **N/A**.

---

### 5.6 Additional features

#### 5.6.1 App list & wave alphabet
- Localized alphabetical sort **[GS-PROP]**  
- Wave alphabet right edge; expand bubble **[S-SHOT]**  
- Sort by usage within letter when usage access granted **[S-SHOT toggle; D20 does not remove this toggle — Usage Breaker only is excluded]**  
- **Installed apps in drawer:** only **launchable** activities (see §8.4.1) — not hidden/system-only packages without a launcher activity  
- **Default: one row per app** (see §8.4.2) — multiple activities do not create duplicate rows  
- **All installed apps visible** subject to package visibility (**D12**) — “all” = all **launchable** apps the user can open from a home/launcher context

#### 5.6.2 Search (D10, D16, D17)

| Item | Spec |
|------|------|
| Open method 1 | **Swipe down on Home Screen** |
| Open method 2 | **Tap Search icon** |
| Interface | **Same** Search overlay for both **[GS-CONF D10]** |
| Filter | Local substring/case-insensitive on labels |
| Group | Recently installed **[S-SHOT]** |
| Contacts | **Excluded (D16)** — no `READ_CONTACTS` |
| Web suggestions | **Excluded (D17)** — no network |
| Launch | Tap result → launch; back → close |

#### 5.6.3 Hide apps
- Settings multi-select; excluded from list/search/favorites add **[S-SHOT S29]**

#### 5.6.4 Suggest Often-Used Apps
- Toggle (default ON in reference shot): suggestion strip using local usage/recency **[S-SHOT]**  
- Not Usage Breaker (D20)

#### 5.6.5 Notifications (D11)

| Item | Spec **[GS-CONF D11]** |
|------|------------------------|
| Home Screen Notifications | **View-only** display of notification content |
| Notification Summary | Bundle/list **view-only** |
| Inline reply | **Not in v1** |
| Notification action buttons system | **Not in v1** |
| Permission | Notification **Listener** access when feature enabled (not install-time) |
| `POST_NOTIFICATIONS` | Only if GS posts its **own** notifications (e.g. rare status); **Usage Breaker removed (D20)** — prefer omit unless another local notif is needed **[GS-PROP: omit unless required]** |

#### 5.6.6 Calendar / Media / Music apps
- As reference Instant Access where permissions granted  
- **Usage Breaker: excluded (D20)** — no timer, usage-break UI, survey, or usage report  

#### 5.6.7 Rename display label (D19)

| Item | Spec **[GS-CONF D19]** |
|------|------------------------|
| Scope | **Launcher display name only** |
| Must not change | Installed app label, package name, system info, other launchers |
| Storage | Local Room/DataStore mapping `pkg+class → customLabel` |
| Apply | GS list, favorites, search, folder members use custom label when set |
| Reset | **Reset to original** action: context menu **Reset name** / clear in edit UI; restore label from `PackageManager` |
| Exclusion 1 | Rename is **not** custom icons — icons unchanged |

#### 5.6.8 Toggles by screen (v2.3.1 structure)

| Screen | Toggles |
|--------|---------|
| **Appearance (SCR-11)** | Show clock/date (if any display options), **Show battery percentage** |
| **Advanced (SCR-15)** | Allow home rotation, Allow haptic feedback, **GS Button (default OFF)** |
| Not present | Icon size (D18 fixed); Quick Lock (D9); Weather (D1); Usage Breaker (D20) |

#### 5.6.9 Fixed icon size (D18)

| Item | Spec |
|------|------|
| Icon size | **Hardcoded** design constant (e.g. `40.dp` — final dp set at implementation; **no slider/setting**) |
| Settings | No “Icon size” row in v1 |
| Adaptive icons | System/adaptive icons rendered at fixed canvas size; normal icons only (no packs) |

---

## 6. Five Excluded Features

**Unchanged exclusions** **[GS-CONF]**:

| # | Category | GS rule |
|---|----------|---------|
| 1 | **Custom Icons** | No packs/per-app custom icons/Anycons. **Normal system icons** at **fixed size (D18)**. **Rename label (D19) allowed** — does not change icons |
| 2 | **Custom Fonts** | System default typeface only |
| 3 | **Custom Themes** | No theme store/editor/apply. Limited appearance: clock, battery, rotation, haptics only |
| 4 | **Widgets & Widget Stack** | No AppWidgetHost UI. Clock/media = native Compose views |
| 5 | **Backup & Restore** | **No in-app backup/restore** — no settings rows, buttons, menus, features, or related UI. **No** Firebase/cloud/Google Drive/own backup system. **`allowBackup=false` (D14)**; do not enable any manifest/system backup path intended for user data. See §6.1 |

**Silent re-addition of any of the five is forbidden.**

### 6.1 Backup & Restore — full exclusion (D14 + exclusion 5) **[GS-CONF v2.3.2]**

| # | Rule |
|---|------|
| 1 | **No in-app backup system** and **no in-app restore system** in v1 |
| 2 | **No** Backup/Restore **settings**, **buttons**, **menus**, **features**, or **related UI** anywhere in the app |
| 3 | **No** Firebase backup, **no** cloud backup, **no** Google Drive backup, **no** custom/own backup pipeline |
| 4 | **Android system-level backup is a separate concern** — GS does **not** provide user-facing backup/restore |
| 5 | **Manifest / configuration:** keep **`android:allowBackup="false"`** (D14). **Do not enable** any backup agent, `fullBackupContent`, `dataExtractionRules`, or similar configuration whose purpose is to let the system back up **user data** (favorites, labels, folders, settings) |
| 6 | App data (Room/DataStore) stays **on-device only**; wipe/reinstall loses it — **intended**, not a bug |
| 7 | No `INTERNET` or cloud SDK for backup — offline-first unchanged |

**Note:** Future Firebase (if ever approved, separate decision) is **not** a backup feature and does not reopen exclusion 5 unless the user explicitly changes that exclusion.

---

## 7. UI/UX and Interaction Specifications

### 7.1 Screens

| ID | Screen | Notes |
|----|--------|-------|
| SCR-01 | Welcome | Onboarding start |
| SCR-02 | Favorites onboarding | ≤12 |
| SCR-03 | Default launcher prompt | Legacy HOME resolver (API 26–28) or RoleManager (API 29+) |
| SCR-04 | Home | No weather; GS Button bottom-right |
| SCR-05 | Search | Opened by swipe-down **or** search icon |
| SCR-06 | Edit favorites | Count N/12; drag reorder apps+folders; create folder |
| SCR-07 | Context menu | Info, uninstall, favorites, **rename**, folder ops |
| SCR-08 | Pop-up folder | |
| SCR-09 | Settings root | No Themes/Pro Niagara rows |
| SCR-10 | Productivity | No weather, no Quick Lock, no Usage Breaker |
| SCR-11 | **Appearance** | **Display only:** clock/date on-off, **battery %**; **no icon size (D18)**; **no** rotation/haptics/GS Button here |
| SCR-12 | Hide apps | |
| SCR-13 | Folders management | |
| SCR-14 | Search settings | Minimal (no contacts/web toggles in v1) |
| SCR-15 | **Advanced** | **Behavior only:** home rotation, haptic feedback, **GS Button enable (default OFF)**; **no** battery/clock here |
| SCR-16 | Help | External links optional |
| SCR-17 | Permission rationales | Calendar, notifications, usage, package-visibility explanation if shown |
| ~~SCR-18~~ | ~~Quick Lock a11y~~ | **Removed (D9)** |

**Settings structure rule (v2.3.1 — duplicate resolved):** each toggle appears in **exactly one** screen. **Appearance (SCR-11) = what you see** (clock, battery %). **Advanced (SCR-15) = behavior** (rotation, haptics, GS Button). Productivity (SCR-10) = calendar/media/notifications/sort/suggest/smart-search only.

### 7.2 Home layout (Compose)

```
Scaffold
├─ Optional: Clock, Date, Battery %
├─ Optional Media card (native)
├─ Favorites rows (apps + folders) — max 12 items
├─ App list continuation
├─ Overlay: AlphabetWave (end)
└─ GS Button (bottom-right) when enabled — **default OFF (hidden)**
```

- **No weather chip (D1)**  
- **GS Button default OFF** — first-run home has no button until enabled **[GS-CONF]**  
- Favorites as first items in scroll **[GS-PROP]**  
- Search icon visible for D10 path  

### 7.3 Context menu (minimum v1)

| Item | Notes |
|------|-------|
| App info | System settings intent |
| Uninstall | Delete/uninstall intent |
| Add/Remove favorites | Respect 12 cap |
| **Rename** | Local display label only (D19) |
| **Reset name** | Restore original label (D19) |
| Shortcuts | System shortcuts if any |
| Folder ops | Add to folder / edit |

Layout: Compose popup/sheet; ~150–250 ms **[GS-PROP]**.

### 7.4 Gestures

| Gesture | Result | Decision |
|---------|--------|----------|
| Drag alphabet | Scrub/expand | Include |
| **Swipe down on home** | **Open search** | **D10** |
| **Tap search icon** | **Open same search** | **D10** |
| Long-press app | Context menu | Include |
| **GS Button tap** | **Open search** (only if enabled) | **D8** |
| **GS Button swipe** | **No action** | **D8** |
| **GS Button long-press** | **Open GS Button settings** | **Audit v2.3.1 confirmed** |
| Double-tap alphabet | ~~Quick Lock~~ **None (feature out)** | **D9** |
| Edit favorites drag | Reorder apps+folders | **D5** |
| Back / Home | Close / system home | Standard |

### 7.5 Animations
- Alphabet expand spring 150–250 ms **[GS-PROP]**  
- Search fade-scale; folder scale-from-anchor  
- Smooth list scroll  

### 7.6 Empty states
- No favorites (CTA to edit)  
- No search results  
- All apps hidden  
- Not default launcher  
- Favorites at 12/12 (add blocked message)  
- Weather N/A; Quick Lock N/A  

---

## 8. Android Architecture

### 8.1 Logical modules (not created yet)

```
:app
  ui/onboarding | ui/home | ui/search | ui/folder | ui/menu | ui/settings
  alphabet/     # WaveAlphabet
  data/         # Room, DataStore
  domain/       # favorites, folders, hidden, labels, sort
  system/       # LauncherApps callbacks, default-home role (API split), permissions
```

Single module OK **[GS-PROP]**.

**Offline-first / future Firebase:** no Firebase in v1; clean boundaries only; no over-engineering; **Firebase ≠ APK installer** (§1.6).

### 8.2 UI technology
**Jetpack Compose only** — no XML feature layouts, no WebView.

### 8.3 Package monitoring (supported APIs only)

**Do not use hidden `PackageMonitor`.**

| Event | Method |
|-------|--------|
| Install | `LauncherApps.Callback.onPackageAdded` + `ACTION_PACKAGE_ADDED` if needed |
| Update | `onPackageChanged` / `ACTION_PACKAGE_REPLACED` |
| Remove | `onPackageRemoved` / `ACTION_PACKAGE_REMOVED` |
| Enable/disable/suspend/available | **`onPackagesAvailable` / `onPackagesUnavailable`** (plural), `onPackageChanged`, `ACTION_PACKAGE_CHANGED` |
| Debounce | Coalesce bursts 200–500 ms **[GS-PROP]** |
| Lifecycle | Register onStart/resume; unregister stop/destroy |
| Boot receiver | Prefer omit **[GS-PROP]** |

### 8.4 App discovery & no duplicates (D12) — v2.3.2 clarified

| Item | Spec **[GS-CONF D12 + v2.3.2]** |
|------|----------------------------------|
| Goal | Display all **launchable** installed apps from the start |
| Primary APIs | `LauncherApps` + package visibility as required |
| `QUERY_ALL_PACKAGES` | **Use when technically necessary**; **minimum path** = LauncherApps + `<queries>` only (§9.2) |
| Drawer rows | **One row per app** by default (§8.4.2) |
| Refresh | Callbacks + cold-start full query |
| Hide list | Still filtered by user Hide list (user choice) |

#### 8.4.1 Launchable-only drawer (`LauncherApps.getActivityList()`)

**Behavior:**
1. **Primary API:** `LauncherApps.getActivityList(null, userHandle)` (or equivalent query of launchable activities) returns activities that are **eligible as launcher entries** for the given user.
2. **What appears:** activities with a **launchable** role for the home context (typically `MAIN`/`LAUNCHER` or otherwise returned by `LauncherApps` as home-launchable).
3. **What does not appear:** packages with **no launchable activity** (background services-only, hidden components, system internal packages without a launcher entry) are **not** added to the app drawer.
4. **System apps without launcher UI:** omitted unless they expose a launchable activity.
5. **Hidden from user:** after discovery, the **Hide list** may remove additional rows (user preference) — distinct from “not launchable.”
6. **Work profile:** **not in v1 (D15)** — only current user / personal profile scope.

`getActivityList()` does not replace package-visibility rules on API 30+ (§9.2).

#### 8.4.2 Duplicate apps — default one row per app

| Rule | Spec **[GS-CONF v2.3.2]** |
|------|---------------------------|
| **Default** | **One row per app** for the drawer |
| Multiple launchable activities in the **same package** | **Do not** create duplicate app rows |
| Row key (default) | **`packageName`** (app identity) |
| Launch target | Select the **appropriate / default launchable activity** (see below) |
| When a second row is allowed | Only if the user-visible design later requires activity-level entries — **not** default v1; not required for D12 |

**Default launch activity selection (package → activity):**
1. Prefer the activity that the system/resolver treats as the **default launcher entry** for that package (e.g. preferred/default `MAIN`+`LAUNCHER` when multiple exist).
2. Else prefer a single obvious entry (one launcher activity).
3. Else stable deterministic choice (e.g. lexicographically first launchable component) — **[GS-PROP]** so the same app always opens the same way.
4. Tap row → launch that chosen component; long-press/context menu unchanged.

**Dedupe rule:** collapse **same-package** launchable activities into **one row**; do not show N rows because an app has N launcher activities (aliases/multi-launch entries collapse to one).

**Not duplicates:** distinct packages remain distinct rows even if labels match.

**Uninstall/update:** on package change, re-resolve default activity; keep **one** row (§8.3 callbacks).

#### 8.5 Launching
1. Resolve **chosen launch activity** via LauncherApps (preferred component from §8.4.2)  
2. Start activity with safe options  
3. Failure → snackbar + list refresh + re-resolve component if needed  

### 8.6 Search architecture
- In-memory index of labels (custom rename overrides display)  
- Coroutines; cancel prior query  
- **No contacts, no web**  

### 8.7 Storage
Room: `Favorite` (type app|folder, sortOrder), `Folder`, `FolderMember`, `HiddenApp`, `CustomLabel`, `AppMeta`  
DataStore: toggles, onboarding, GS button enabled  

### 8.8 Gestures
Compose `pointerInput`; long-press drag for favorites; double-tap detector **not used for lock** (D9).

### 8.9 Lifecycle
- Refresh dirty on resume  
- Optional `NotificationListenerService` only if view-notif features enabled  
- No WebView  

### 8.10 Default launcher compatibility (API split) **[GS-CONF v2.3.2]**

| API band | Flow |
|----------|------|
| **26–28** | **Legacy HOME:** launcher manifest declares `Intent.CATEGORY_HOME` + `MAIN` (and `DEFAULT` as needed). Prompt uses **legacy default-app / resolver** flow (`Settings.ACTION_HOME_SETTINGS` or `ACTION_MAIN`+`CATEGORY_HOME` resolver pattern) so the user can pick GS as default home. |
| **29–36** | **`RoleManager.ROLE_HOME`:** use `RoleManager.isRoleHeld(ROLE_HOME)` / `createRequestRoleIntent(ROLE_HOME)` (or equivalent) when the platform role API is available; fall back to legacy HOME prompt if role API unavailable. |
| **Both** | Onboarding SCR-03 explains “set as default home”; Home role **essential** (§9.3). |
| **Survive reboot** | Default-home preference is **system-owned** — GS only verifies it is still default on resume (no private persistence of role). |

**Do not** invent a third path. **Do not** use device-admin or accessibility to force default home.  

---

## 9. Permissions and Privacy

### 9.1 Notifications: POST_NOTIFICATIONS vs Listener

| | `POST_NOTIFICATIONS` | Notification Listener |
|--|----------------------|------------------------|
| Purpose | Show **GS’s own** notifications (API 33+) | Read **other apps’** notifications |
| GS use | Prefer **omit** in v1 (no Usage Breaker D20; view-notif uses listener) | **Yes** when Home notifications / Summary enabled |
| User path | Runtime dialog | Settings → Notification access |

**Rules:** Listener only after feature enable + rationale; not at install.  
**D11:** view-only — no reply/action permission extras required.

### 9.2 Package visibility & `QUERY_ALL_PACKAGES` (D12) — v2.3.1 tiers

**User requirement (D12, unchanged):** show **all installed apps** from the beginning; use required package visibility including **`QUERY_ALL_PACKAGES` if technically necessary**; no duplicate entries.

#### 9.2.1 Minimum package visibility approach (works without QUERY_ALL_PACKAGES)

| Layer | Mechanism | Role |
|-------|-----------|------|
| **1. Minimum (always ship)** | `LauncherApps.getActivityList` / `LauncherApps.Callback` for current user | Primary discovery of launchable activities for a home app |
| **2. Minimum (always ship)** | Manifest `<queries>` with `MAIN` + `LAUNCHER` intent | Makes standard launchable packages visible on API 30+ |
| **3. Cold start** | Full query of resolved launcher activities; dedupe by `package+component` **[GS-CONF D12 / §8.4]** | Initial complete-as-possible list |

This **minimum stack** requires **no** `QUERY_ALL_PACKAGES` and is Play-policy-safe.

#### 9.2.2 QUERY_ALL_PACKAGES (D12 — when technically necessary)

| Item | Spec |
|------|------|
| Purpose | Guarantee **all installed apps** (including non-standard launcher intents / OEM gaps) |
| Decision | **D12:** include **when technically necessary** for the “all apps from the beginning” goal |
| Manifest | Normal permission `android.permission.QUERY_ALL_PACKAGES` |
| Play | **Permission declaration + use-case justification required**; launcher “list all apps” is a common pattern but **approval is not guaranteed** **[TECH RISK R1]** |
| Privacy policy | Disclose broad package visibility if shipped |

#### 9.2.3 Fallback behavior (if declaration rejected or permission omitted)

| Condition | Fallback |
|-----------|----------|
| Play rejects declaration / build ships without QAP | **Minimum stack only (§9.2.1)** |
| Resulting behavior | List shows **as many apps as visibility allows**; **completeness not guaranteed**; dedupe still applied; Hide list unaffected |
| User-visible gap | Rare/OEM/non-standard apps may be missing → QA documents gaps; no crash |
| In-app messaging | Optional (low-key) help text that some system apps may not appear **[GS-PROP]** |
| Hotfix path | Re-submission with declaration, or sideload/other distribution if policy blocks — **user distribution decision later** |

#### 9.2.4 Release limitation (documented for store submission)

> **Known release limitation:** Complete “all installed apps” listing depends on `QUERY_ALL_PACKAGES` approval. Without it, GS Launcher uses LauncherApps + `<queries>` and **cannot promise 100% package coverage** on every OEM/API 30+ device. This limitation is **accepted in the release notes / QA report**; it does not block offline core functionality.

**Do not claim Play approval is certain.**

**API 26–29 note:** package visibility restrictions start at **API 30** — on 26–29 the minimum stack already sees essentially all packages without QAP (see §10.1).

### 9.3 GS permission table (v1 final)

| Permission / access | Feature | Essential? | When |
|---------------------|---------|------------|------|
| HOME (default launcher) | Default launcher | **Yes** | Onboarding — **API split §8.10** |
| LauncherApps / queries | App list | **Yes** | Implicit |
| **QUERY_ALL_PACKAGES** | Complete app list **D12** | **When technically necessary** | Manifest + Play declaration; fallback §9.2.3 |
| Notification Listener | View notifications **D11** | Optional feature | Feature ON |
| `POST_NOTIFICATIONS` | GS own notifs | Prefer omit | Only if needed |
| `READ_CALENDAR` | Agenda/preview | Optional | Feature ON |
| `PACKAGE_USAGE_STATS` | Sort by usage / suggest | Optional | Feature ON |
| **`READ_CONTACTS`** | ~~Contact search~~ | **Do not request (D16)** | — |
| Location | ~~Weather~~ | **Do not request (D1)** | — |
| `VIBRATE` | Haptics | Normal | Install |
| `INTERNET` | ~~Weather/web~~ | **Not for core v1** | Omit unless later approved feature |
| Accessibility | ~~Quick Lock~~ | **Do not request (D9)** | — |
| `REQUEST_DELETE_PACKAGES` | Uninstall helper | Optional | Context |
| `RECEIVE_BOOT_COMPLETED` | Prefer omit | Prefer no | — |
| `SET_WALLPAPER` | Optional | Not required | — |
| Billing / Firebase / ads | — | **Exclude** | — |
| BIND_APPWIDGET | — | **Exclude** | — |

### 9.4 Privacy principles
- No GS backend / accounts / analytics / ads  
- Offline core  
- No weather location; no web suggestions; no contacts  
- Notification content on-device only  
- Custom labels stored locally only  
- Future Firebase: separate later decision; **not** APK self-update  

---

## 10. Android Compatibility

| Field | Value |
|-------|-------|
| **minSdkVersion** | **26** |
| **targetSdkVersion** | **36** (Play requirement effective **2026-08-31**) |
| **compileSdkVersion** | **36** |
| **Supported OS** | Android 8.0 (API 26) – Android 16 (API 36); newer best-effort |
| Build | Kotlin + AGP supporting compileSdk 36 |

### 10.1 Feature limits by version (verified pass — v2.3.1)

| Feature | API 26–27 (8.0–8.1) | API 28–29 (9–10) | API 30–32 (11–12L) | API 33–35 (13–15) | API 36 (16) |
|---------|---------------------|------------------|--------------------|--------------------|-------------|
| Core launcher / favorites / folders / search | ✓ | ✓ | ✓ | ✓ | ✓ |
| Wave alphabet / GS Button / rename | ✓ | ✓ | ✓ | ✓ | ✓ |
| **Package visibility restrictions** | **None (pre-30)** — minimum stack sees all packages | **None (pre-30)** | **Apply** — `<queries>` / QAP needed | Same | Same |
| `QUERY_ALL_PACKAGES` | Effectively unused (no visibility filter) | Same | **API 30+** permission + declaration path | Same | Same |
| Home default flow | **Legacy** `MAIN`+`HOME` + resolver | **Legacy** `MAIN`+`HOME` + resolver | **RoleManager** `ROLE_HOME` preferred (API 29+) | RoleManager | RoleManager |
| Notification listener access | ✓ Settings | ✓ | ✓ | ✓ | ✓ |
| `POST_NOTIFICATIONS` dialog | N/A | N/A | N/A | **If GS posts own notifs** | Same |
| `READ_CALENDAR` runtime | ✓ | ✓ | ✓ | ✓ | ✓ |
| Usage access | ✓ Settings | ✓ | ✓ | ✓ | ✓ |
| Quick Lock | **N/A (D9)** | N/A | N/A | N/A | N/A |
| Adaptive icons at fixed size (D18) | ✓ API 26+ | ✓ | ✓ | ✓ | ✓ |
| Predictive back | No | No | No | Best effort | **Honor under targetSdk 36** |
| FGS types | Avoid FGS v1 | Avoid | Avoid | Types if FGS used — **v1 avoids FGS** | Same |
| `allowBackup=false` | ✓ | ✓ | ✓ | ✓ | ✓ |
| Play targetSdk policy | — | — | — | — | **36 by 2026-08-31** |

**Compatibility findings (v2.3.1):**
1. **minSdk 26** safe for all included v1 features.  
2. **Package visibility only affects API 30+** — minimum stack must be correct there; QAP is an API 30+ permission.  
3. Notification split version-safe: listener to read; POST only for own notifs on API 33+.  
4. No work-profile / device-admin / a11y-lock in v1 → those version constraints N/A.  
5. **target/compile 36** for Play from **2026-08-31**; predictive back honored under target 36.  
6. **Default launcher:** **API 26–28 = legacy HOME intent/resolver**; **API 29+ = `RoleManager.ROLE_HOME`** — see §8.10.

**Reference APK:** min 26, target 36, compileSdk 37 **[S-APK]** — GS uses **min 26 / target 36 / compile 36**.

---

## 11. Data Storage

### 11.1 Room + DataStore

| Entity | Fields |
|--------|--------|
| `favorite` | `id`, `type` (app\|folder), `pkg?`, `class?`, `folderId?`, `sortOrder`, `addedAt` — **exactly ≤12 rows** |
| `folder` | `id`, `name`, `sortOrder` |
| `folder_member` | `folderId`, `pkg`, `class`, `sortOrder` |
| `hidden_app` | `pkg`, `class?`, `hiddenAt` |
| `custom_label` | `pkg`, `class`, `label`, `updatedAt` |
| `app_meta` | optional lastLaunch, installTime |

| DataStore | Keys |
|-----------|------|
| Productivity | calendar, media, notifications, sort-by-usage, suggest… **no weather, no usage breaker, no quick lock** |
| Appearance | **battery % (and any clock display flags)** |
| Advanced | rotation, haptics, **gs_button_enabled (default false = OFF)** |
| Onboarding | favorites done, default prompt |
| Search | (minimal; **no contacts/web prefs**) |

### 11.2 Rules
- **`android:allowBackup="false"`** **[GS-CONF D14 + §6.1]** — do **not** enable system backup of user data  
- **No** in-app backup/restore UI or features (exclusion 5)  
- **No** Firebase / cloud / Google Drive / custom backup pipeline  
- No cloud sync  
- Versioned migrations; corrupt DB → recreate + empty states  
- Custom labels local only — on-device; not exported by GS  

---

## 12. Performance and Battery Requirements

| Goal | Target **[GS-PROP]** |
|------|----------------------|
| Cold start | &lt; 800 ms mid; &lt; 1.5 s low-end |
| Scroll | 60 fps mid-range; 200+ apps |
| RAM idle home | &lt; ~100–120 MB PSS |
| APK size | &lt; ~8 MB release (ABI splits, few deps) |
| CPU | Debounced package events; no polling |
| Battery | **No network in v1**; no FGS |
| Offline | Airplane mode: core features OK |
| Dependencies | Kotlin, AndroidX, Compose, Room, Coroutines — **no WebView/Firebase/Billing/ads** |

---

## 13. Error Handling and Edge Cases

| Scenario | Handling |
|----------|----------|
| Launch race | Snackbar + refresh |
| Package storm | Debounce |
| Corrupt DB | Recreate; empty states |
| Not default home | Banner |
| Listener revoked | Disable notif features + prompt |
| Calendar denied | Hide agenda + re-prompt |
| Usage access denied | Sort falls back alphabetical; suggest uses recency |
| Weather / Quick Lock / Usage Breaker / Contacts / Web / Work profile | **N/A in v1** |
| Empty list | Retry empty state |
| RTL / font scale / TalkBack | Supported |
| Favorites at 12 | Block add with message |
| Rename invalid/empty | Reject or reset to original |
| QUERY_ALL_PACKAGES not granted / policy | **Fallback §9.2.3:** minimum visibility stack; list may miss rare apps; no crash; document gap |
| Duplicate packages after update | Dedupe by component key |
| Folder favorite + member count | Only folder row counts toward 12 |

---

## 14. Testing and Acceptance Criteria

### 14.1 Functional
- [ ] Default home survives reboot  
- [ ] Install/uninstall/update/disable reflected; **one row per app**; same-package multi-activity → **no duplicate rows** (**v2.3.2**)  
- [ ] Drawer = **launchable** apps only via `LauncherApps` path; no service-only packages as rows  
- [ ] Default launcher: **API 26–28 legacy HOME**; **API 29+ RoleManager**  
- [ ] **No** Backup/Restore settings, buttons, menus, or UI; **`allowBackup=false`**; no backup dataExtraction/fullBackup enablement  

- [ ] **All launchable apps visible** as far as package visibility allows (**D12**); **one row per app**; no multi-activity duplicate rows  
- [ ] Favorites: add/remove; **≤12**; folder=1; members not counted (**D4**)  
- [ ] Drag reorder apps + folders in Edit Mode (**D5**)  
- [ ] Folder can be favorite (**D6**)  
- [ ] Favorites persist force-stop + reboot  
- [ ] Search: **swipe down** and **search icon** open **same** UI (**D10**)  
- [ ] Search: no contacts, no web suggestions (**D16, D17**)  
- [ ] Context: info, uninstall, favorites, **rename + reset** (**D19**)  
- [ ] Rename does not change system label/package  
- [ ] Pop-up folders create/open/close/members  
- [ ] Hide apps works  
- [ ] **No** weather, Quick Lock, Usage Breaker UI (**D1, D9, D20**)  
- [ ] Notifications **view-only** (**D11**)  
- [ ] GS Button **bottom-right**; **default OFF on first launch**; tap=search; swipe=no-op; **long-press=Settings** (**D7, D8, audit**)  
- [ ] Settings: each toggle in **exactly one** screen (Appearance vs Advanced)  
- [ ] Icon size fixed; **no icon size setting** (**D18**)  
- [ ] **No** `READ_CONTACTS`, no a11y-for-lock, no weather location  
- [ ] Airplane mode: core flows work  
- [ ] **No** widget/theme/font/backup UI (five exclusions)  
- [ ] Package id **`com.amisayem.gs.launcher`**  
- [ ] **`allowBackup=false`**  

### 14.2 Compatibility
API 26, 28, 30, 33, 34, 35, 36 · small/tall · light/dark · 2 GB device · en + ar RTL smoke  

### 14.3 Non-functional
- [ ] §12 targets  
- [ ] Monkey 50k  
- [ ] Process death restore  
- [ ] TalkBack smoke  
- [ ] No Firebase/backend/analytics in dependencies  
- [ ] Offline acceptance  

### 14.4 Research integrity
- [ ] No claim of full APK decompile / source verification  
- [ ] No Niagara branding  
- [ ] Five exclusions regression  

---

## 15. Release Requirements

| Requirement | Detail |
|-------------|--------|
| Application ID | **`com.amisayem.gs.launcher`** |
| Min/target/compile | **26 / 36 / 36** |
| UI | Compose only |
| Exclusions | §6 enforced |
| Offline | No Firebase/backend/accounts; core offline |
| allowBackup | **false** |
| Privacy policy | Package visibility, optional calendar/usage/notification listener |
| Play | **`QUERY_ALL_PACKAGES` declaration — approval not guaranteed**; **release limitation:** without approval, app-list completeness falls back to §9.2.3 |
| Store screenshots | Own captures |
| Spec of record | **This v2.3.2 document** |
| Coding start | **Not yet** |

---

## 16. Final Decision Summary (D1–D20)

**All decisions are FINAL. No unresolved product decisions remain.**

| ID | Topic | **Final decision** | Spec impact |
|----|-------|--------------------|-------------|
| **D1** | Weather in v1 | **C — No** | No weather UI/perms |
| **D2** | Weather provider | **N/A** | None |
| **D3** | Weather tap | **N/A** | None |
| **D4** | Favorites limit | **Max 12 items**; app=1; folder=1; **members inside folder do not count** | §5.1, UI count N/12 |
| **D5** | Favorite reorder | **Drag in Edit Mode**; apps **and** folders | §5.1.4 |
| **D6** | Folder as favorite | **Yes**; counts as **1** | §5.1, §5.2 |
| **D7** | GS Button position | **Bottom-right**; **Default OFF**; **Long-press = Settings** | §5.3, §7.2, §7.4 |
| **D8** | GS Button actions | **Tap = Search; Swipe = none** | §5.3.2 |
| **D9** | Quick Lock | **Excluded from v1**; **no** a11y lock | §5.4 |
| **D10** | Search activation | **Swipe down on Home** + **Search icon** → **same** search UI | §5.6.2, §7.4 |
| **D11** | Notifications | **View-only**; **no** inline reply/action system | §5.6.5 |
| **D12** | App discovery | **All installed launchable apps**; visibility incl. **`QUERY_ALL_PACKAGES` if necessary**; **one row per app**; no duplicates | §8.4, §9.2 |
| **D13** | Package name | **`com.amisayem.gs.launcher`** | Everywhere |
| **D14** | Backup | **`allowBackup=false`**; **no in-app backup/restore** | §6.1, §11.2 |
| **D15** | Work profile | **None in v1** | §4.2 |
| **D16** | Contact search | **Excluded**; **no** `READ_CONTACTS` | §5.6.2, §9.3 |
| **D17** | Web suggestions | **Excluded (B)** | §5.6.2 |
| **D18** | Icon size | **Fixed/hardcoded**; **no setting** | §5.6.9 |
| **D19** | Rename label | **Allowed**; launcher-only; local persist; **reset to original** | §5.6.7 |
| **D20** | Usage Breaker | **Fully excluded** | §4.2, §5.6.6 |

**Preserved non-D requirements:** offline-first; no backend/Firebase/accounts/ads; native Kotlin; Compose; no HTML/CSS/JS/WebView; five exclusions; Firebase ≠ APK installer.

---

## 17. Consistency Review (v2.3 / v2.3.1 / v2.3.2)

| Check | Result |
|-------|--------|
| Package name `com.amisayem.gs.launcher` in §1, §15, testing | **Consistent** |
| Favorites 12-cap: apps+folders=1; members excluded | **Specified in §5.1, storage §11, tests §14** |
| Quick Lock absent from features, settings, gestures, tests as “must have” | **Omitted / marked N/A** |
| Contact search / `READ_CONTACTS` | **Excluded** |
| Work profile | **Excluded** |
| Usage Breaker | **Excluded**; sort-by-usage **retained** (different feature) |
| Weather / web suggestions | **Excluded** |
| Notification reply | **View-only** |
| INTERNET / Firebase / backend | **Not required for core; not in deps** |
| allowBackup | **false** + full Backup exclusion rules §6.1 |
| App drawer | **Launchable-only** (`getActivityList`) §8.4.1 |
| Duplicate rows | **One row per app** default; default launch activity §8.4.2 |
| Default home | **API 26–28 legacy / 29+ RoleManager** §8.10 |
| QUERY_ALL_PACKAGES | **D12 + Play policy risk documented** |
| Rename local-only + reset | **§5.6.7** |
| Icon size fixed | **§5.6.9** |
| GS Button position/actions | **D7/D8 + Default OFF + long-press=Settings (§5.3, §7)** |
| Search dual trigger same UI | **D10** |
| Evidence labels preserved; no false JADX/runtime claims | **§1.3, §2.5** |
| Five exclusions intact | **§1.4, §6** |
| Firebase ≠ APK update installer | **§1.6** |
| SCR-11 vs SCR-15 duplicate toggles | **Resolved v2.3.1:** Appearance vs Advanced split; each toggle once |
| QUERY_ALL_PACKAGES tiers | **Minimum stack + QAP-if-necessary + fallback + release limitation (§9.2)** |
| Android 26–36 matrix | **Verified pass §10.1** |
| No code / no project claimed | **End of document** |

**Contradictions found in v2.2 and resolved in v2.3:**
1. Unresolved D-list vs “final” product → **all D1–D20 locked**.  
2. Quick Lock still specified as v1 a11y feature → **removed (D9)**.  
3. Contacts/web “optional open” vs final excludes → **hard excluded (D16/D17)**.  
4. Favorites “no hard cap” proposal vs final 12 → **12 cap (D4)**.  
5. QUERY_ALL_PACKAGES “contingency only” vs D12 → **include if necessary + policy risk**.  
6. Package name placeholder vs final ID → **`com.amisayem.gs.launcher`**.  
7. Usage Breaker in Productivity list → **removed (D20)**; sort-by-usage kept.  
8. allowBackup as soft prop → **hard false (D14)**.  
9. Icon size as optional setting → **fixed only (D18)**.  
10. Notification reply “maybe” → **view-only (D11)**.

**Contradictions found in v2.3 and resolved in v2.3.1:**
1. GS Button default OFF was `[GS-PROP]` / ambiguous → **confirmed [GS-CONF]: Default OFF**.  
2. GS Button long-press was `[GS-PROP]` → **confirmed [GS-CONF]: Settings**.  
3. SCR-11 / SCR-15 shared battery/rotation/haptics/GS Button rows → **single-screen ownership** (Appearance vs Advanced).  
4. QUERY_ALL_PACKAGES only “if necessary” without fallback story → **§9.2 minimum / QAP / fallback / release limitation**.

**Corrections in v2.3.2 (on v2.3.1):**
1. Backup & Restore → **hard no in-app features/UI; no cloud/Firebase/Drive; allowBackup false + no user-data backup config** (§6.1).  
2. App drawer → **launchable-only** via `LauncherApps.getActivityList()` (§8.4.1).  
3. Duplicates → **one row per app**; default launchable activity selection (§8.4.2).  
4. Default launcher → **API 26–28 legacy HOME / 29+ RoleManager** (§8.10).  
5. Compatibility table coarse → **verified API 26–36 columns** (visibility starts API 30, etc.).

---

## 18. Technical Risks and Open Technical Questions

**These are not product decisions; they are implementation/policy risks.**

| ID | Risk / question | Severity | Notes |
|----|-----------------|----------|-------|
| R1 | **Google Play approval of `QUERY_ALL_PACKAGES`** | **High** | Not guaranteed; **fallback = minimum visibility stack (§9.2.3)**; **release limitation:** incomplete list possible without approval |
| R2 | Exact fixed icon size dp / touch targets | Medium | Choose at UI implementation; must pass a11y |
| R3 | Favorites drag UX on Compose stability | Medium | Fallback: up/down controls if drag flaky |
| R4 | Pop-up folder layout (Niagara exact) **[UNVER]** | Low | Original design OK |
| R5 | Recommended-badge algorithm | Low | Optional; does not affect 12 cap |
| R6 | Sort-by-usage needs usage access fallback | Low | Alphabetical fallback |
| R7 | View-only notification UX design | Medium | Must not imply reply |
| R8 | OEM package visibility quirks | Medium | QA matrix |
| R9 | Future Firebase remote config vs offline-first | Low | Only if user later approves; not APK installer |
| R10 | No runtime/JADX verification of Niagara | Known limitation | Preserved |

**Unresolved product decisions:** **None (D1–D20 final).**  
**Open items above are technical only.**

---

**End of GS Launcher Final Specification v2.3.2.**  
**No coding was started. No Android project or source files were created.**
