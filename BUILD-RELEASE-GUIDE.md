# Kotha Bolbo — Release APK বানানোর গাইড (AndroidIDE)

## ⚠️ সবচেয়ে গুরুত্বপূর্ণ (মনে রাখবেন)
- `kothabolbo-release.jks` আর `release.properties` — এই দুটো ফাইল **কখনো কাউকে দেবেন না**, কোথাও আপলোড করবেন না, ZIP-এ দেবেন না। এগুলো আপনার personal signing key। হারালে আবার update দিতে পারবেন না।
- এই ফাইল দুটো **নিজের ডিভাইসের প্রজেক্ট ফোল্ডারে** রাখুন।
- **প্রতিবার নতুন ZIP extract করলে** এই দুটো ফাইল আগের মতোই বসিয়ে নেবেন — ZIP-এ এগুলো থাকে না।

---

## ধাপ ১ — `release.properties` ফাইল বানান (প্রজেক্ট রুটে)

ফাইলটা এমন নামে বানান: **`release.properties`** (একদম প্রজেক্টের রুটে, `settings.gradle.kts`-এর পাশে)

নিচের টেমপ্লেট কপি করে ভরুন:

```
storeFile=kothabolbo-release.jks
storePassword=আপনার_কিসটোর_পাসওয়ার্ড
keyAlias=kothabolbo
keyPassword=আপনার_কি_পাসওয়ার্ড
```

- `kothabolbo-release.jks` — আপনার কিসটোর ফাইলের নাম (এই নামেই রাখুন, নাম বদলাবেন না)
- `storePassword` — কিসটোরের পাসওয়ার্ড
- `keyAlias` = **`kothabolbo`** (আপনার build.gradle.kts-এ এটাই আছে)
- `keyPassword` — key-র পাসওয়ার্ড

> এই দুটো পাসওয়ার্ডের সাথে নিচের ডায়ালগে যে পাসওয়ার্ড দিবেন সেটা **একই** হতে হবে।

---

## ধাপ ২ — কিসটোর ফাইল

**যদি আগে `kothabolbo-release.jks` বানিয়ে থাকে** → ধাপ ৩-এ যান, "CHOOSE" দিয়ে ওই ফাইল সিলেক্ট করবেন।

**যদি না থাকে** → ডায়ালগে **CREATE NEW...** চাপুন:

- **Key store file**: নাম দিন `kothabolbo-release.jks` (জায়গা: প্রজেক্ট রুট বা যেকোনো নিরাপদ ফোল্ডার)
- **Key store password / Confirm**: একটা শক্তিশালী পাসওয়ার্ড দিন (মনে রাখুন!)
- **Key alias**: `kothabolbo`
- **Key password / Confirm**: key-র পাসওয়ার্ড (store password থেকে আলাদা হলেও চলবে)
- **Validity (days)**: 10000 (বেশি ভালো)
- **OK / Create**

> বানানোর পরে উপরের `release.properties`-এ এই পাসওয়ার্ডগুলো বসান।

---

## ধাপ ৩ — Generate Signed Bundle / APK ডায়ালগ ভরুন

আপনার স্ক্রিনশটের ফিল্ডগুলো এভাবে:

| Field | কী দিবেন |
|---|---|
| **Build type** | **APK** (leave as is) |
| **Module** | **app** (leave as is) |
| **Key store path** | **CHOOSE** → আপনার `kothabolbo-release.jks` ফাইলটা সিলেক্ট করুন |
| **Key store password** | `release.properties`-এর `storePassword` |
| **Key alias** | `kothabolbo` |
| **Key password** | `release.properties`-এর `keyPassword` |
| **Remember passwords** | ✅ টিক দিতে পারেন |

তারপর **GENERATE** চাপুন।

---

## ধাপ ৪ — 결과 APK

বিল্ড শেষে APK পাবেন এখানে:

```
app/build/outputs/apk/release/app-release.apk
```

এই ফাইলটাই install/share করবেন (ডিভাইসে পাসওয়ার্ড দাতে হবে)। **`debug` APK নয় — release APK ইন্সটল করতে হবে**, নাহলে "harmful" দেখাতে পারে।

---

## নোটিফিকেশন (MessagingStyle) — নতুন native কোড
এবার `app/src/main/java/com/amisayem/kothabolbo/NotificationHelper.kt`-এ `MessagingStyle` + `Person` ব্যবহার হচ্ছে। প্রজেক্ট **API 36 (compileSdk 36)** — AndroidIDE-এ **Android SDK Platform 36** install করা আছে কিনা যাচাই করুন; না থাকলে build error আসবে।

## "harmful app" — সত্যি কথা
- **সাইন করা release APK**-তে warning **অনেক কমে** আসে (debug keystore-এর কারণে আসা "harmful" সেটা ফিক্স হয়)।
- তবে **একদম শূন্য হবে না** যতক্ষণ Google Play-তে publish না করেন — কারণ Play Protect নতুন/অজানা অ্যাপকে প্রথমে flag করে। চূড়ান্ত সমাধান Play Console-এ আপলোড।
