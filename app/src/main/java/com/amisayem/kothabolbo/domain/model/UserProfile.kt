package com.amisayem.kothabolbo.domain.model

import com.amisayem.kothabolbo.core.PrivacyMasker
import com.google.firebase.Timestamp

data class UserProfile(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val phone: String = "",
    val gender: String = "",
    val birthDay: Int = 0,
    val birthMonth: Int = 0,
    val birthYear: Int = 0,
    val dobPublicDay: Boolean = false,
    val dobPublicMonth: Boolean = false,
    val dobPublicYear: Boolean = false,
    val emailPublic: Boolean = false,
    val phonePublic: Boolean = false,
    val bio: String = "",
    val photoURL: String = "",
    val photoFileId: String = "",
    val coverURL: String = "",
    val coverFileId: String = "",
    val createdAt: Timestamp? = null,
    val lastProfileEdit: Timestamp? = null,
    val fcmToken: String = "",
    val fcmTokens: List<String> = emptyList(),
    val googleLinked: Boolean = false
) {
    val shownEmail: String get() = if (emailPublic) email else PrivacyMasker.email(email)
    val shownPhone: String get() = if (phonePublic) phone else PrivacyMasker.phone(phone)
    val avatar: String get() = photoURL.ifBlank { diceBearUrl(displayName, gender) }
    val birthDateLabel: String get() = if (!dobPublicDay && !dobPublicMonth && !dobPublicYear) "Private" else listOf(
        PrivacyMasker.dobPart(birthDay.toString().padStart(2, '0'), dobPublicDay),
        PrivacyMasker.dobPart(birthMonth.toString().padStart(2, '0'), dobPublicMonth),
        PrivacyMasker.dobPart(birthYear.toString(), dobPublicYear)
    ).joinToString("/")

    companion object {
        fun diceBearUrl(name: String, gender: String = ""): String {
            val seed = java.net.URLEncoder.encode(name.ifBlank { "Kotha Bolbo" }, "UTF-8")
            val genderParam = when (gender.lowercase()) {
                "male", "female" -> "&gender=${gender.lowercase()}"
                else -> ""
            }
            return "https://api.dicebear.com/7.x/avataaars/png?seed=$seed$genderParam"
        }
    }
}

data class SignupDraft(
    val displayName: String = "",
    val birthDay: Int = 1,
    val birthMonth: Int = 1,
    val birthYear: Int = 2000,
    val gender: String = "",
    val email: String = "",
    val countryPrefix: String = "+880",
    val phoneLocal: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val photoUri: String? = null,
    val acceptedTerms: Boolean = false
)
