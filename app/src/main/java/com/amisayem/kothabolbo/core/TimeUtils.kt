package com.amisayem.kothabolbo.core

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object TimeUtils {
    fun millis(value: Any?): Long = when (value) {
        is Timestamp -> value.toDate().time
        is Date -> value.time
        is Number -> value.toLong()
        else -> 0L
    }

    fun chatTime(epochMs: Long): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epochMs))
    fun dateLabel(epochMs: Long): String = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(epochMs))
    fun relative(epochMs: Long, now: Long = System.currentTimeMillis()): String {
        val delta = (now - epochMs).coerceAtLeast(0)
        return when {
            delta < 60_000 -> "Just now"
            delta < 3_600_000 -> "${TimeUnit.MILLISECONDS.toMinutes(delta)}m ago"
            delta < 86_400_000 -> "${TimeUnit.MILLISECONDS.toHours(delta)}h ago"
            else -> "${TimeUnit.MILLISECONDS.toDays(delta)}d ago"
        }
    }

    fun isLive(epochMs: Long, now: Long = System.currentTimeMillis()): Boolean =
        epochMs > 0 && now - epochMs < AppConstants.AUTO_DELETE_MS
}
