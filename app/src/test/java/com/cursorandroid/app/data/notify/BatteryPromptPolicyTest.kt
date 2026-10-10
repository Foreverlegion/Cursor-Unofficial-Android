package com.cursorandroid.app.data.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryPromptPolicyTest {
    @Test
    fun firstSignInAsksUntilTheUserAnswers() {
        val first = BatteryPromptPolicy.decide(exempt = false, asked = false, knownExempt = false)
        assertTrue(first.show)
        assertFalse(first.asked)

        val skipped = BatteryPromptPolicy.decide(exempt = false, asked = true, knownExempt = false)
        assertFalse(skipped.show)
        assertTrue(skipped.asked)
    }

    @Test
    fun exemptionHidesThePromptUntilItIsRevoked() {
        val exempt = BatteryPromptPolicy.decide(exempt = true, asked = false, knownExempt = false)
        assertFalse(exempt.show)
        assertTrue(exempt.asked)
        assertTrue(exempt.knownExempt)

        val revoked = BatteryPromptPolicy.decide(exempt = false, asked = true, knownExempt = true)
        assertTrue(revoked.show)
        assertFalse(revoked.asked)
        assertFalse(revoked.knownExempt)

        val stillWaiting = BatteryPromptPolicy.decide(
            exempt = false,
            asked = revoked.asked,
            knownExempt = revoked.knownExempt,
        )
        assertTrue(stillWaiting.show)

        val skippedAfterRevoke = BatteryPromptPolicy.decide(exempt = false, asked = true, knownExempt = false)
        assertFalse(skippedAfterRevoke.show)
    }

    @Test
    fun explanationNamesWorkManagerAndDoze() {
        assertTrue(BatteryPromptPolicy.TITLE.isNotBlank())
        assertTrue(BatteryPromptPolicy.BODY.contains("WorkManager"))
        assertTrue(BatteryPromptPolicy.BODY.contains("Doze"))
        assertTrue(BatteryPromptPolicy.ALLOW.isNotBlank())
        assertTrue(BatteryPromptPolicy.SKIP.isNotBlank())
        assertEquals("Not now", BatteryPromptPolicy.SKIP)
    }
}
