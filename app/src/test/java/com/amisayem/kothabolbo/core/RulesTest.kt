package com.amisayem.kothabolbo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {
    @Test fun emailMaskKeepsEdgesAndDomain() {
        assertEquals("ab****yz@gmail.com", PrivacyMasker.email("abcdefyz@gmail.com"))
    }

    @Test fun phoneMaskUsesRequiredFormula() {
        assertEquals("+88******45", PrivacyMasker.phone("+8801712345"))
    }

    @Test fun phoneValidationProducesE164() {
        assertEquals("+8801712345678", Validators.e164("+880", "1712345678"))
        assertNull(Validators.e164("+880", "12"))
    }

    @Test fun retentionWindowIsStrictlyTwentyFourHours() {
        val now = 1_000_000_000L
        assertTrue(TimeUtils.isLive(now - AppConstants.AUTO_DELETE_MS + 1, now))
        assertFalse(TimeUtils.isLive(now - AppConstants.AUTO_DELETE_MS, now))
    }
}
