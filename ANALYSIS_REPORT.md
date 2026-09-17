# Redmi 7 (onclite) — Kernel Driver `.sh` Analysis Report

**Date:** 2026-09-17
**Repo analyzed:** https://github.com/md0331400/Test @ commit `8cd12d2`
**Mode:** Static analysis only. Kono file load/insert/execute kora hoyni. Kono existing file modify kora hoyni.

---

## 0. Artifacts (workspace)

| Path | Ki |
|---|---|
| `/home/user/repo/4.9.186.sh` | Original (clone, untouched) |
| `/home/user/repo/Driver.sh` | Original (clone, untouched) |
| `/home/user/analysis/payload.b64` | `4.9.186.sh` theke extract kora base64 payload (280,411 bytes) |
| `/home/user/analysis/embedded.ko` | Decode kora kernel module (207,576 bytes) — **load kora hoyni** |

```
sha256  4.9.186.sh   b3b3c3d96869c7cb9f2f6ff3e27e721120e4c2d18fc43918c55a7eeb5cc695d4
sha256  Driver.sh    d4837895eaaa139db88b815b0686fb550ce771a1772795577402108a188403d3
sha256  embedded.ko  90536af90fa104cde48a6a55c5caffe96108948616a9d0f63ddc987d355507ba
```

---

## 1. Current Kernel — **UNKNOWN (device output dorkar)**

Ami sandbox-e achi, tomar device-e na. Tai ei section guess na kore **pending** rakha holo.
Device theke ei command gulo chaliye output paste koro (Section 8 dekhe nao).

---

## 2. Existing `4.9.186.sh` — full static analysis

### 2.1 Script structure (3,682 lines / 281,228 bytes)

| # | Line(s) | Ki kore |
|---|---|---|
| 1 | 1 | `#!/system/bin/sh` (Android mksh) |
| 2 | 3–16 | ANSI color variables (`ESC_SEQ`, `COLOR_SEQ`, `BLUE`, `YELLOW`, `NC`) — comments Chinese-te |
| 3 | 20–23 | Root check: `[ "$(id -u)" != "0" ]` → exit 1 |
| 4 | 25–3667 | `MODULE_BASE64="..."` — single variable spanning 3,643 lines (280,411 bytes of base64 data) |
| 5 | 3669 | `TEMP_MODULE="/data/nh.ko"` (hardcoded) |
| 6 | 3670 | `echo "$MODULE_BASE64" \| base64 -d > "$TEMP_MODULE"` |
| 7 | 3673 | `ERROR_MSG=$(insmod "$TEMP_MODULE" 2>&1)` |
| 8 | 3674–3683 | Success → `rm -f "$TEMP_MODULE"`. Failure → print, `sleep 5`, **`reboot`** (line 3681), `exit 1` |

⚠️ `ERROR_MSG` capture kora hoy **kintu kokhono print/use kora hoy na** (grep confirm: shudhu line 3673-e ache). Mane `insmod` keno fail korlo — user kokhono dekhte pabe na.

### 2.2 Safety gaps in the existing wrapper (guru­t­­o­p­ur­n­o)

Existing script-e **ei check gulo ekdom nei**:

- ❌ Kernel version check (`uname -r` compare) — nei
- ❌ Architecture check (`aarch64`) — nei
- ❌ Vermagic check — nei
- ❌ `modinfo` / ELF validation — nei
- ❌ Already-loaded module check (`lsmod`) — nei
- ❌ SELinux / `/data` mount / free space check — nei
- ❌ `insmod` error output dekhanor byabostha nei (`ERROR_MSG` capture kore kintu kokhono print kore na)
- ❌ Temp path hardcoded `/data/nh.ko` (collision risk)
- ⚠️ **Failure hole direct `reboot`** — eta dangerous: je kernel-e module reject korbe, oi device bar bar reboot khabe

### 2.3 Payload format

- Encoding: **plain base64**, kono gzip/xz/zip wrapper nei
- `base64 -d` diye straight ELF pawa jay
- Decoded size: **207,576 bytes**

### 2.4 Embedded module (`embedded.ko`)

```
file:       ELF 64-bit LSB relocatable, ARM aarch64, version 1 (SYSV),
            BuildID[xxHash]=b3d349b27a81867f, with debug_info, not stripped
ELF Type:   REL (Relocatable file)          <- proper .ko
Machine:    AArch64
Sections:   38
Compiler:   clang version 17.0.0            (.comment)
Debug info: FULL DWARF present (.debug_info 53 KB, .debug_line, .debug_str,
            .debug_line_str, .debug_frame, .debug_addr, .debug_rnglists)
```

**`.modinfo` (complete):**

```
license=GPL
vermagic=4.9.186-perf-gdcfb1676-dirty SMP preempt mod_unload modversions aarch64
depends=
```

| modinfo field | Value | Note |
|---|---|---|
| `name` | `5.10_A12` | `.gnu.linkonce.this_module` theke (896-byte `struct module`) |
| `license` | `GPL` | ✅ |
| `vermagic` | `4.9.186-perf-gdcfb1676-dirty SMP preempt mod_unload modversions aarch64` | |
| `depends` | *(empty)* | kono external module dependency nei |
| `srcversion` | **NEI** | modpost theke generate hoyni |
| `intree` | **NEI** | out-of-tree → kernel `TAINT_OOT_MODULE` set korbe |
| `sig_id`/signature | **NEI** | unsigned module |
| `__versions` | **section present, size = 0 bytes** | 🔴 KEY FINDING (Section 4) |

**Build provenance (DWARF `.debug_line_str` theke):**

```
/root/mk/RT/entry.c            <- driver source
/root/mk/RT/5.10-A12.mod.c     <- generated modpost file
/root/mk/RT/comm.h  /root/mk/RT/memory.h  /root/mk/RT/process.h
/root/neihe/4.9.186/out        <- kernel build/output tree
../arch/arm64/include/asm      <- arm64 headers
```
→ Eta ekta **out-of-tree build**, 4.9.186 kernel tree-er headers use kore.

### 2.5 Defined functions / data

```
FUNC  init_module                628 B   (.init.text)
FUNC  cleanup_module             148 B   (.exit.text)
FUNC  get_rand_str               428 B
FUNC  translate_linear_address   132 B   page-table walk
FUNC  read_physical_address      172 B   ioremap_cache + memstart_addr
FUNC  write_physical_address     196 B
FUNC  read_process_memory        320 B   find_vpid → pid_task → get_task_mm
FUNC  write_process_memory       320 B
FUNC  dispatch_ioctl             668 B
FUNC  dispatch_open               60 B
FUNC  dispatch_close              40 B
OBJ   dispatch_functions         240 B   struct file_operations
OBJ   memdev                     120 B   struct cdev
OBJ   mem_tool_dev_t               4 B   dev_t
OBJ   mem_tool_class               8 B   struct class*
OBJ   devicename                   8 B   char[8]  (GLOBAL)
```

### 2.6 Kernel symbols required (31 undefined)

```
__arch_copy_from_user  __arch_copy_to_user  __check_object_size  __class_create
__iounmap  __rcu_read_lock  __rcu_read_unlock  alloc_chrdev_region  cdev_add
cdev_del  cdev_init  class_destroy  d_path  device_create  device_destroy
find_vma  find_vpid  get_random_bytes  get_task_mm  ioremap_cache  kobject_del
memset  memstart_addr  mmput  pfn_valid  pid_task  printk  remove_proc_entry
strcmp  strrchr  unregister_chrdev_region
```

Verified against Linux v4.9 source:
- `pfn_valid` → `EXPORT_SYMBOL(pfn_valid)` in `arch/arm64/mm/init.c:152` ✅
- `memstart_addr` → `arch/arm64/mm/init.c:58` = `s64 memstart_addr __ro_after_init = -1;`
  **mainline v4.9-e `EXPORT_SYMBOL(memstart_addr)` NEI.**
  🔴 Mane ei module shudhu sei vendor kernel-e cholbe jekhane Qualcomm/Xiaomi tree
  `memstart_addr` export kore. **Target kernel-e verify korte hobe** (Section 8 command deya ache).

### 2.7 Runtime behaviour (disassembly theke — Capstone + relocation resolution)

**`init_module`:**
1. `devicename[8]` = `"BYNb"` prefix + 5 random chars from a small alphabet (`ABCDEFG...`),
   `devicename[6] = '\0'` → **6-char randomized device name**
2. `alloc_chrdev_region(&mem_tool_dev_t, 0, 1, devicename)`
3. `cdev_init(&memdev, &dispatch_functions)` + `cdev_add(&memdev, mem_tool_dev_t, 1)`
4. `__class_create(THIS_MODULE, devicename, &driver_entry.__key)`
5. `device_create(mem_tool_class, NULL, mem_tool_dev_t, NULL, devicename)`
   → node create hoy **`/dev/<randomized-name>`**
6. **Anti-detection:**
   ```
   remove_proc_entry("uevents_records", NULL);
   remove_proc_entry("sched_debug", NULL);
   kobject_del(&__this_module + 8);
   ```
   → module nijei `lsmod` / `/sys/module` theke hide kore, ar duto `/proc` entry delete kore

**`dispatch_ioctl` command codes (disassembly confirmed):**

| cmd | Branch | Behaviour |
|---|---|---|
| `0x801` | 0x694 | `copy_from_user(0x20 B struct)` → `read_process_memory(pid, addr, size)` |
| `0x802` | 0x7e8 | `copy_from_user(0x20 B struct)` → `write_process_memory(...)` |
| `0x803` | 0x6e4 | `copy_from_user(0x18 B struct)` → `find_vpid`/`pid_task`/`get_task_mm` → walk VMA list → `d_path()` + `strrchr('/')` + `strcmp()` → **process name diye module base address ber kore**, result `copy_to_user` |
| onyano | 0x83c | `mov x0, #-0x16` → returns `-EINVAL` |

Return codes (disassembly confirmed): `0` success, `-EFAULT` (`-0xe`, site 0x898), `-EINVAL` (`-0x16`, site 0x83c), `-EIO` (`-5`, site 0x834).

**Physical memory access path:**
`translate_linear_address()` → page-table walk (`mm->pgd` → pud → pmd → pte) →
`read/write_physical_address()` → `pfn_valid()` + `ioremap_cache()` + `__arch_copy_to/from_user()` + `__iounmap()`.

### 2.8 ⚠️ Ei driver ki kore — honest note

Eta ekta **process memory read/write char-device driver**:
- Root userspace process theke **onno jekono process-er memory read/write** kora jay (pid diye)
- Physical memory direct read/write kora jay
- Nijei `lsmod` theke hide kore + `/proc/sched_debug`, `/proc/uevents_records` delete kore

Ei combination (randomized `/dev` name + self-hiding + proc-entry removal) sadharonoto
**game-cheat driver**-er pattern. Legitimate use-o ache (debugging, memory forensics,
security research on your own device). Kintu jodi uddessho online multiplayer game-e
cheat/anti-cheat bypass hoy, seta ami support korbo na — Section 9-e question deya ache.

---

## 3. `Driver.sh` — actually ki

### 3.1 `file` / header output

```
file: ELF 64-bit LSB pie executable, ARM aarch64, version 1 (SYSV),
      dynamically linked, interpreter /system/bin/linker64, for Android 24,
      built by NDK r27 (12077973),
      BuildID[sha1]=154ce3befad83742b3fff0445988e2684405b675, stripped

ELF Type:      DYN (Position-Independent Executable)
Machine:       AArch64
Size:          453,624 bytes
Entry point:   0x235b0
.comment:      Android (12027248, based on r522817) clang version 18.0.1
Stripped:      YES
```

🔴 **`Driver.sh` kono shell script na. Eta ekta Android aarch64 userspace executable.**
`.sh` extension ta pure misnomer. `sh Driver.sh` chalale kaj korbe na.

### 3.2 Dynamic linkage

```
NEEDED: liblog.so, libandroid.so, libm.so, libdl.so, libc.so
Static: libc++ (cxxabi/demangle) + libunwind  (statically linked, ~250 KB)
Target: Android API 24 (Android 7.0+)
```

### 3.3 Interesting imports

```
ioctl   __open_2   opendir   readdir   closedir   strcmp   strncmp
printf  snprintf   vsnprintf  sscanf   __system_property_get  syscall  getpid
memcpy  memset  memmove  memcmp  malloc  realloc  free  posix_memalign
openlog  syslog  closelog  abort  __assert2  dl_iterate_phdr
```

### 3.4 Strings

- Plaintext useful string kom — beshirbhag libc++ / libunwind runtime noise
- Notable: `exynos9810`, `ro.arch` (`__system_property_get` er sathe), `K01234567` + `89` (charset-like)
- 🔍 **`/dev` string puro file-e kothao plaintext-e nei** (raw grep confirm kora)
  → device node path runtime-e build hoy ba argv theke ase. **Eta ami fully verify korini** (stripped binary, 277 KB `.text`).

### 3.5 Relationship with `4.9.186.sh`

- `Driver.sh` er `.text`-e ioctl command immediate **`0x803`** pawa geche (2 sites: `0x338`, `0xc1c`)
- `0x803` = `embedded.ko`-er `dispatch_ioctl`-er "get module base by process name" branch
- → **Duto matched pair.** `Driver.sh` = userspace client, `4.9.186.sh` = kernel module loader.
- Kintu tara ek file-e nei: `4.9.186.sh` shudhu `.ko` load kore, `Driver.sh` ke spawn kore na
  (script-e kono exec call nei).

### 3.6 ⚠️ Warning

`Driver.sh` ekta **stripped, unknown-provenance binary** je root privilege-e cholbe.
Static analysis chara run kora uchit na. Ami run korini.

---

## 4. Compatibility reality — 4.9.186 → 4.9.337 (kernel source theke verified)

Linux **v4.9** `kernel/module.c` fetch kore pore verify kora hoyeche:

### 4.1 Vermagic check — the first token is SKIPPED

```c
/* kernel/module.c:2883 */
info->index.vers = find_sec(info, "__versions");

/* kernel/module.c:2959 */
} else if (!same_magic(modmagic, vermagic, info->index.vers)) {

/* kernel/module.c:1336 */
/* First part is kernel version, which we ignore if module has crcs. */
static inline int same_magic(const char *amagic, const char *bmagic, bool has_crcs)
{
        if (has_crcs) {
                amagic += strcspn(amagic, " ");
                bmagic += strcspn(bmagic, " ");
        }
        return strcmp(amagic, bmagic) == 0;
}
```

Amader module-e `__versions` section **ache** (index 32) → `info->index.vers = 32` (non-zero)
→ `has_crcs = true` → **`4.9.186-perf-gdcfb1676-dirty` token ta compare-i hoy na.**

Shudhu ei part ta exact match korte hoy:
```
 SMP preempt mod_unload modversions aarch64
```

### 4.2 CRC check — empty `__versions` bypass kore

```c
/* kernel/module.c check_version() */
	/* No versions at all?  modprobe --force does this. */
	if (versindex == 0)
		return try_to_force_load(mod, symname) == 0;

	versions = (void *) sechdrs[versindex].sh_addr;
	num_versions = sechdrs[versindex].sh_size / sizeof(struct modversion_info);
	/* num_versions == 0 here, loop runs zero times */

	/* Broken toolchain. Warn once, then let it go.. */
	pr_warn_once("%s: no symbol version for %s\n", mod->name, symname);
	return 1;          /* <-- ACCEPTED */
```

Amader `__versions` present kintu size 0 → loop zero times → falls through → **return 1 (OK)**.

### 4.3 `module_layout` guard-o bypass hoy

```c
/* kernel/module.c:2940 */
if (!check_modstruct_version(info->sechdrs, info->index.vers, mod))
	return ERR_PTR(-ENOEXEC);

/* check_modstruct_version() -> check_version(..., "module_layout", ...) */
```
Same `check_version()` → same bypass. Mane `struct module` layout mismatch dhora **pore na**.

### 4.4 Conclusion

| Question | Answer |
|---|---|
| Rename `4.9.186.sh` → `4.9.337.sh` kaj korbe? | ❌ Na. Kernel filename dekhe na. |
| Binary-te `vermagic` string edit korle kaj korbe? | ❌ Na. (a) first token already ignored, (b) actual layout/symbol mismatch thik hoy na. **Eta fake compatibility — tomar rule #4 onujayi korbo na.** |
| Existing `.ko` 4.9.337-e `insmod` pass korbe? | ✅ **Somvob** — jodi target kernel aarch64 + `CONFIG_MODVERSIONS=y` + vermagic tail exact ` SMP preempt mod_unload modversions aarch64` hoy. |
| Pass korlei safe? | ⚠️ **NA.** `module_layout` CRC guard bypass hoye geche. `struct module` / `task_struct` / `mm_struct` / `vm_area_struct` layout target-e different hole → memory corruption → **kernel panic / bootloop**. |
| `memstart_addr` resolve korbe? | ❓ Mainline v4.9-e export nei. Vendor tree-te thakte pare. **Device-e verify korte hobe.** |

### 4.5 Kichu relevant / kichu na

| Factor | Relevant? | Keno |
|---|---|---|
| kernel version string (4.9.186 vs 4.9.337) | ⚠️ Partially | `same_magic` ignore kore (crcs present), kintu layout difference er proxy |
| vermagic tail (`SMP preempt mod_unload modversions aarch64`) | ✅ **Hard blocker** | Exact match lagbei |
| `CONFIG_MODVERSIONS` | ✅ | `y` na hole `same_magic` full string compare korbe → block |
| `CONFIG_MODULE_UNLOAD` | ✅ | vermagic tail-e `mod_unload` ache; target-e na thakle mismatch |
| `CONFIG_PREEMPT` | ✅ | vermagic tail-e `preempt` ache |
| `__versions` CRC | ⚠️ Bypassed | Empty section |
| `struct module` layout | ✅ **Real risk** | Guard bypassed, tai verify korte hobe |
| Exported symbol set | ✅ | Especially `memstart_addr` |
| Compiler/toolchain | ⚠️ Moderate | Reference = clang 17; ABI stable, kintu vendor kernel specific clang chate pare |
| Architecture | ✅ Hard | aarch64 fixed |
| Vendor (Qualcomm/Xiaomi) kernel changes | ✅ **Biggest unknown** | SD632/msm8953 tree-te custom patches |
| `CONFIG_MODULE_SIG_FORCE` | ✅ | `y` hole unsigned module reject hobe |
| SELinux enforcing | ✅ | `/dev` node create + ioctl block hote pare |

---

## 5. Target `4.9.337` — ki lagbe / ki ache / ki nei

### What is required
```
1. Target kernel-er EXACT source tree (same git commit, same vendor patches)
2. Target kernel-er .config           -> CONFIG_MODVERSIONS / MODULE_UNLOAD / PREEMPT / MODULE_SIG_FORCE
3. Module.symvers                     -> real CRC (recommended, not strictly required)
4. include/generated/* + include/config/*   (kernel prepare run korle hoy)
5. Driver source (entry.c)            -> 🔴 MISSING
6. Cross toolchain (clang for arm64)
7. Device-er actual vermagic string   -> 🔴 UNKNOWN
8. /proc/kallsyms check for memstart_addr export -> 🔴 UNKNOWN
```

### What is available (now)
```
✅ Full reference wrapper  (4.9.186.sh)  — proven structure
✅ Reference .ko           (with FULL DWARF debug info)
✅ Driver source structure recoverable from DWARF:
     /root/mk/RT/entry.c  — function names, types, locals, line numbers sob present
     (entry.c, comm.h, memory.h, process.h)
✅ Complete ioctl ABI reverse-engineered (0x801 / 0x802 / 0x803)
✅ Userspace client (Driver.sh ELF)
```

### What is missing
```
🔴 Target kernel-er exact vermagic string        (device output lagbe)
🔴 Target kernel source / headers / .config
🔴 Module.symvers for target
🔴 Driver source code (entry.c) — reconstructed korte hobe
🔴 Confirmation: memstart_addr target-e exported kina
🔴 Confirmation: CONFIG_MODULES=y, MODULE_SIG_FORCE=n
```

### Can it be built?
**Somvob, kintu ekhono na.** Missing jinisgulo jogaar na korle valid `.ko` banano jabe na.
Bhalo khobor: `.ko`-te **full DWARF** ache, tai `entry.c` accurate vabe reconstruct kora jabe
(function signatures, struct types, local variables, line numbers — sob recoverable).

**Fake compatibility create kora hobe na** — vermagic patch / rename / CRC strip — kichui na.

---

## 6. Implementation Plan

### Phase 0 — Device info collect (**eta age lagbe**)
Device theke read-only command output collect (Section 8).

### Phase 1 — Target kernel confirm
- `uname -r` theke exact vermagic tail ber kora
- `/proc/config.gz` (thakle) ba `/proc/version` theke config infer kora
- `memstart_addr` export check via `/proc/kallsyms`
- `CONFIG_MODULES`, `CONFIG_MODULE_SIG_FORCE`, SELinux mode check

### Phase 2 — Kernel source jogaar
Option A: Xiaomi/Qualcomm opensource `msm-4.9` onclite tree (exact commit match kore)
Option B: jei custom ROM kernel cholche tar GitHub repo
Option C: `/proc/config.gz` + downloaded `kernel-headers` diye headers-only build
→ **Ki route niccho seta bolo.**

### Phase 3 — `entry.c` reconstruct
- `embedded.ko`-er DWARF theke (`readelf --debug-dump=info/decodedline`)
- Function-by-function: `entry.c`, `comm.h`, `memory.h`, `process.h`
- ioctl ABI (0x801/0x802/0x803) + struct layouts match rakha hobe jate `Driver.sh` client kaj kore

### Phase 4 — Out-of-tree build
```
make -C <kernel-tree> M=$PWD ARCH=arm64 CROSS_COMPILE=aarch64-linux-gnu- \
     CC=<clang> modules
```
Verify (load na kore):
```
modinfo 5.10_A12.ko          -> vermagic must EXACTLY match uname -r + tail
readelf -Ws 5.10_A12.ko      -> __versions POPULATED with real CRCs (not empty)
readelf -h  5.10_A12.ko      -> Machine: AArch64, Type: REL
```

### Phase 5 — Wrapper `.sh` generate
Existing structure follow kore, **plus** je check gulo existing-e nei segulo add:
```
#!/system/bin/sh
  root check
  uname -r  == target vermagic check     (NEW)
  arch check (uname -m == aarch64)       (NEW)
  /proc/kallsyms memstart_addr check     (NEW)
  lsmod duplicate check                  (NEW)
  MODULE_BASE64="..."  (same plain base64 format)
  unique temp path (mktemp, not /data/nh.ko)
  base64 -d  -> temp .ko
  sha256 verify (NEW)
  insmod, error output DEKHANO (NEW)
  success -> rm temp;  failure -> rm temp + exit (NO auto-reboot)   (CHANGED)
  trap cleanup on exit                   (NEW)
```

### Phase 6 — Staged test (safe)
1. Script **na** — prothome manual `insmod` + `dmesg | tail -30` capture
2. `/dev` node verify
3. `ioctl 0x803` test with a harmless target process
4. `rmmod` test (vermagic-e `mod_unload` ache, tai unload supported)
5. Tarpor full wrapper run

---

## 7. Files To Create / Modify

**Existing files-e kono change hobe na.** `4.9.186.sh` ar `Driver.sh` untouched thakbe.

```
kernel-driver/                      <- notun directory
├── src/
│   ├── entry.c                     (Phase 3 — DWARF theke reconstruct)
│   ├── comm.h
│   ├── memory.h
│   ├── process.h
│   ├── Makefile
│   └── Kbuild
├── tools/
│   └── verify-ko.sh                (modinfo/readelf/vermagic checker)
└── 4.9.337.sh                      (Phase 5 — final wrapper)
```

Notun file count: **7**. Existing file modify: **0**.

---

## 8. Device commands (copy-paste ready)

Root shell (`adb shell` → `su`) theke:

```sh
echo "=== 1. KERNEL ==="
uname -a
cat /proc/version
echo
echo "=== 2. DEVICE ==="
getprop ro.product.device
getprop ro.product.model
getprop ro.build.version.release
getprop ro.build.version.sdk
getprop ro.build.display.id
getprop ro.boot.hardware
echo
echo "=== 3. ARCH / ABI ==="
uname -m
getprop ro.product.cpu.abi
echo
echo "=== 4. MODULE SUPPORT ==="
ls -l /system/bin/insmod /system/bin/rmmod /system/bin/modinfo 2>/dev/null
grep -E 'CONFIG_MODULES|CONFIG_MODULE_UNLOAD|CONFIG_MODULE_SIG|CONFIG_MODVERSIONS|CONFIG_PREEMPT|CONFIG_ARM64' /proc/config.gz 2>/dev/null || echo "(/proc/config.gz nei — zcat try koro)"
zcat /proc/config.gz 2>/dev/null | grep -E 'CONFIG_MODULES=|CONFIG_MODULE_UNLOAD=|CONFIG_MODULE_SIG_FORCE=|CONFIG_MODVERSIONS=|CONFIG_PREEMPT=|CONFIG_SMP=' || echo "config.gz readable na"
echo
echo "=== 5. LOADED MODULES ==="
lsmod 2>/dev/null | head -20 || cat /proc/modules | head -20
echo
echo "=== 6. SELINUX ==="
getenforce 2>/dev/null
echo
echo "=== 7. SYMBOL EXPORT CHECK (critical) ==="
grep -w memstart_addr /proc/kallsyms 2>/dev/null || echo "memstart_addr kallsyms-e nei"
grep -wE 'pfn_valid|ioremap_cache|__arch_copy_to_user|get_task_mm|find_vma|d_path' /proc/kallsyms 2>/dev/null | head
echo
echo "=== 8. KPTR RESTRICT ==="
cat /proc/sys/kernel/kptr_restrict
echo
echo "=== 9. TOOLS ==="
which base64 sha256sum mktemp insmod 2>/dev/null
```

---

## 9. Open questions

1. Target kernel `4.9.337` — eta stock MIUI kernel, naki custom ROM/custom kernel build?
2. Kernel source/headers tomar kache ache kina? (Xiaomi opensource, custom ROM repo, ba headers package)
3. Uddessho ki — debugging/research, naki onno kichu? (Section 2.8 dekhe nao)
4. `Driver.sh` client ta o use korbe kina, naki shudhu driver + wrapper lagbe?
