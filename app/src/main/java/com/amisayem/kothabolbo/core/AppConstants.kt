package com.amisayem.kothabolbo.core

import com.amisayem.kothabolbo.BuildConfig

object AppConstants {
    const val APP_NAME = "Kotha Bolbo"
    const val DEVELOPER = "Md. Abu Sayem"
    const val SUPPORT_EMAIL = "support.amisayem@gmail.com"
    const val PRIVACY_POLICY_URL = "https://kothabolbo-privacy-policy.vercel.app"
    const val ACCOUNT_DELETION_URL = "https://kothabolbo-account-deletion.vercel.app"

    const val USERS = "users"
    const val MESSAGES = "messages"
    const val CONTACTS = "chatContacts"
    const val POSTS = "posts"
    const val STORIES = "stories"
    const val APP_CONFIG = "app_config"
    const val VERSION_DOC = "version"
    const val STATUS = "status"
    const val TYPING = "typing"

    const val API_BASE = BuildConfig.API_BASE_URL
    const val IMAGEKIT_PUBLIC_KEY = BuildConfig.IMAGEKIT_PUBLIC_KEY
    const val IMAGEKIT_CDN = BuildConfig.IMAGEKIT_CDN_BASE
    const val GOOGLE_WEB_CLIENT_ID = BuildConfig.GOOGLE_WEB_CLIENT_ID
    const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    const val DRIVE_BACKUP_FILE = "kothabolbo_backup.json"

    const val AUTO_DELETE_MS = 24L * 60L * 60L * 1_000L
    const val STORY_LIFETIME_MS = AUTO_DELETE_MS
    const val TYPING_WRITE_OFF_MS = 2_200L
    const val TYPING_STALE_MS = 6_000L
    const val BACKUP_DEBOUNCE_MS = 20_000L
    const val PROFILE_EDIT_COOLDOWN_MS = 7L * 24L * 60L * 60L * 1_000L
    const val MAX_TEXT_LENGTH = 5_000
    const val MAX_IMAGE_BYTES = 1L * 1024L * 1024L
    const val MAX_STORY_VIDEO_BYTES = 50L * 1024L * 1024L

    const val NOTIFICATION_CHANNEL_ID = "chat_messages_v3"
    const val NOTIFICATION_REPLY_KEY = "kb_inline_reply"
    const val ACTION_REPLY = "com.amisayem.kothabolbo.ACTION_REPLY"
    const val ACTION_LIKE = "com.amisayem.kothabolbo.ACTION_LIKE"
    const val EXTRA_SENDER_ID = "senderId"
    const val EXTRA_MESSAGE_ID = "messageId"
    const val EXTRA_SENDER_NAME = "senderName"
    const val EXTRA_CHAT = "chat"

    const val DELETE_ACCOUNT_PHRASE = "i want delete my Account"

    val COUNTRY_CODES = listOf(
        CountryCode("Bangladesh", "+880", "BD"),
        CountryCode("India", "+91", "IN"),
        CountryCode("Pakistan", "+92", "PK"),
        CountryCode("United States", "+1", "US"),
        CountryCode("United Kingdom", "+44", "GB"),
        CountryCode("United Arab Emirates", "+971", "AE"),
        CountryCode("Other", "+", "OTHER")
    )
}

data class CountryCode(val name: String, val prefix: String, val iso: String)
