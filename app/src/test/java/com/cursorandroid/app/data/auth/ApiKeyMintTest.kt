package com.cursorandroid.app.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiKeyMintTest {
    @Test
    fun storedKeySkipsCreate() {
        val decision = ApiKeyMint.decide("crsr_savedsecretvalue0001", listed = null)
        assertTrue(decision is ApiKeyMint.Decision.Reuse)
        assertEquals("crsr_savedsecretvalue0001", (decision as ApiKeyMint.Decision.Reuse).key)
    }

    @Test
    fun emptyAccountCreatesOnce() {
        assertTrue(ApiKeyMint.decide(null, emptyList()) is ApiKeyMint.Decision.Create)
        assertTrue(ApiKeyMint.decide(null, null) is ApiKeyMint.Decision.Create)
    }

    @Test
    fun namedKeyWithoutSecretDoesNotCreate() {
        val listed = listOf(
            ApiKeyMint.ListedKey(name = "cursor-android", masked = "crsr_...abcd", secret = null),
            ApiKeyMint.ListedKey(name = "Cursor Android API Key", masked = "crsr_...ef01", secret = null),
        )
        val decision = ApiKeyMint.decide(stored = null, listed = listed)
        assertTrue(decision is ApiKeyMint.Decision.Refuse)
        assertEquals(ApiKeyMint.REFUSE, (decision as ApiKeyMint.Decision.Refuse).message)
        assertTrue(ApiKeyMint.explainsLeftoverKeys(ApiKeyMint.REFUSE))
        assertTrue(ApiKeyMint.explainsLeftoverKeys("there's already a Cursor Android API key please delete it/them first"))
        assertTrue(ApiKeyMint.isExistingKeyFailure("api key already exists"))
        assertFalse(ApiKeyMint.isExistingKeyFailure("unauthorized"))
    }

    @Test
    fun reusableSecretIsKept() {
        val secret = "crsr_reusablesecretvalue0001"
        val listed = listOf(
            ApiKeyMint.ListedKey(name = "cursor-android", masked = "crsr_...0001", secret = secret),
        )
        val decision = ApiKeyMint.decide(null, listed)
        assertEquals(secret, (decision as ApiKeyMint.Decision.Reuse).key)
    }

    @Test
    fun otherNamesStillCreate() {
        val listed = listOf(ApiKeyMint.ListedKey(name = "cli", masked = "crsr_...zzzz", secret = null))
        assertTrue(ApiKeyMint.decide(null, listed) is ApiKeyMint.Decision.Create)
        assertTrue(ApiKeyMint.isAppKeyName("Cursor Android"))
        assertFalse(ApiKeyMint.isAppKeyName("cli"))
    }

    @Test
    fun parseReadsMaskedListAndFullSecret() {
        val masked = ApiKeyMint.parse(
            """{"apiKeys":[{"id":1,"maskedKey":"crsr_...e5f6","name":"cursor-android"}]}""",
        )
        assertEquals(1, masked?.size)
        assertEquals("cursor-android", masked!![0].name)
        assertNull(masked[0].secret)
        val full = ApiKeyMint.parse(
            """{"api_keys":[{"name":"Cursor Android API Key","apiKey":"crsr_fullsecretvalue00000001"}]}""",
        )
        assertEquals("crsr_fullsecretvalue00000001", full!![0].secret)
    }

    @Test
    fun parseFailureIsUnknown() {
        assertNull(ApiKeyMint.parse("not-json"))
    }
}
