package com.cursorandroid.app.data.notify

data class BatteryPromptDecision(
    val show: Boolean,
    val asked: Boolean,
    val knownExempt: Boolean,
)

object BatteryPromptPolicy {
    const val TITLE = "Unrestricted battery"
    const val BODY =
        "This app uses WorkManager to check for agent notifications while a run is active, then every 15 minutes. Android Doze stops that work when battery use is optimized. Unrestricted battery lets those checks keep running."
    const val ALLOW = "Allow unrestricted battery"
    const val SKIP = "Not now"

    fun decide(exempt: Boolean, asked: Boolean, knownExempt: Boolean): BatteryPromptDecision {
        if (exempt) {
            return BatteryPromptDecision(show = false, asked = true, knownExempt = true)
        }
        if (knownExempt) {
            return BatteryPromptDecision(show = true, asked = false, knownExempt = false)
        }
        return BatteryPromptDecision(show = !asked, asked = asked, knownExempt = false)
    }
}
