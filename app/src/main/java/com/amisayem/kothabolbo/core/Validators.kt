package com.amisayem.kothabolbo.core

import android.util.Patterns
import java.time.DateTimeException
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

object Validators {
    fun gmail(email: String): String? {
        val value = email.trim().lowercase(Locale.ROOT)
        return when {
            value.isBlank() -> "Email is required"
            !Patterns.EMAIL_ADDRESS.matcher(value).matches() -> "Enter a valid email address"
            !value.endsWith("@gmail.com") -> "Only Gmail addresses are supported"
            else -> null
        }
    }

    fun password(value: String): String? = when {
        value.length < 6 -> "Password must be at least 6 characters"
        else -> null
    }

    fun displayName(value: String): String? = when {
        value.trim().length < 2 -> "Name must contain at least 2 characters"
        value.trim().length > 60 -> "Name cannot exceed 60 characters"
        else -> null
    }

    fun e164(prefix: String, local: String): String? {
        val normalized = (prefix + local).replace(Regex("[\\s()-]"), "")
        return when {
            !normalized.matches(Regex("^\\+[1-9]\\d{7,14}$")) -> null
            else -> normalized
        }
    }

    fun validDate(day: Int, month: Int, year: Int): Boolean = try {
        val date = LocalDate.of(year, month, day)
        !date.isAfter(LocalDate.now(ZoneId.systemDefault())) && year >= 1900
    } catch (_: DateTimeException) {
        false
    }
}

object PrivacyMasker {
    fun email(value: String): String {
        val parts = value.split("@", limit = 2)
        if (parts.size != 2) return "Private"
        val local = parts[0]
        val visible = when {
            local.length <= 2 -> local.take(1) + "****"
            local.length <= 4 -> local.take(1) + "****" + local.takeLast(1)
            else -> local.take(2) + "****" + local.takeLast(2)
        }
        return "$visible@${parts[1]}"
    }

    fun phone(value: String): String = when {
        value.length < 6 -> "Private"
        else -> value.take(3) + "******" + value.takeLast(2)
    }

    fun dobPart(value: String, isPublic: Boolean): String = if (isPublic) value else "Private"
}
