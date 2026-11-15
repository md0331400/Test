# GS Launcher — v2.3.1 → v2.3.2 Change Summary

**Date:** 2026-11-16  
**Type:** Specification correction only (no code)  
**Input:** `GS_Launcher_Specification_v2.3.1.md`  
**Output:** **`GS_Launcher_Specification_v2.3.2.md`**

---

## Corrections applied

### 1. Backup & Restore (§6.1 new + exclusion 5 + §11.2 + D14)
- **No** in-app backup or restore system  
- **No** Backup/Restore settings, buttons, menus, features, or related UI  
- **No** Firebase / cloud / Google Drive / own backup pipeline  
- System-level backup is **separate**; **`allowBackup=false`**; **do not enable** `fullBackupContent` / `dataExtractionRules` / backup agent for **user data**  
- Favorites/labels/settings = **on-device only**; reinstall wipe = intended  

### 2. Installed apps / app drawer (§8.4.1 new)
- Drawer shows **launchable apps only**  
- Explains `LauncherApps.getActivityList()` behavior  
- **No** hidden/service-only packages without a launcher activity  

### 3. Duplicate apps (§8.4.2 new)
- **Default: one row per app** (`packageName`)  
- Multiple launchable activities → **no duplicate rows**  
- **Default/appropriate launch activity** selection rules (preferred/default MAIN+LAUNCHER, else stable choice)  
- Distinct packages remain distinct  

### 4. Default launcher compatibility (§8.10 new + §10.1 + tests)
- **API 26–28:** legacy **HOME** intent + resolver / default-home settings  
- **API 29–36:** **`RoleManager.ROLE_HOME`** (legacy fallback if needed)  
- SCR-03 + §9.3 + tests updated  

---

## Unchanged (approved v2.3.1 preserved)

| Area | Status |
|------|--------|
| **D1–D20** product outcomes | Unchanged (D12 wording = launchable scope; D14 expanded only per above) |
| Five exclusions | Unchanged (Backup row text strengthened) |
| **GS Button** | **Default OFF; Tap=Search; Swipe=none; Long-press=Settings** |
| Weather / Web / Backend / Firebase / online features | **Not added** |
| Settings structure SCR-11/15 | Unchanged from v2.3.1 |
| QUERY_ALL tiers §9.2 | Unchanged from v2.3.1 |
| Package ID, allowBackup=false, 26/36/36 | Unchanged |

---

## Unresolved product decisions

**None introduced.** All v2.3.1 decisions remain final.  
(Play QUERY_ALL approval remains a **technical/policy risk**, not a new product decision.)

---

## Confirmation

| Item | Status |
|------|--------|
| v2.3.2 specification complete | **Yes** |
| Only listed corrections applied | **Yes** |
| No coding / no Android project | **Yes** |

---

*End of change summary.*
