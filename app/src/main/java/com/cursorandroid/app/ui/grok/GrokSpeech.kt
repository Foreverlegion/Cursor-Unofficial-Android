package com.cursorandroid.app.ui.grok

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

class GrokSpeechEngine(
    context: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var handsFree = false
    private var pushToTalk = false
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults?.text()?.orEmpty().orEmpty()
            if (text.isNotBlank()) onPartial(text)
        }

        override fun onResults(results: Bundle?) {
            val text = results?.text()?.orEmpty().orEmpty()
            if (text.isNotBlank()) onFinal(text)
            if (handsFree) startListening()
        }

        override fun onError(error: Int) {
            if (error == SpeechRecognizer.ERROR_NO_MATCH && handsFree) {
                startListening()
                return
            }
            if (error == SpeechRecognizer.ERROR_CLIENT && !handsFree && !pushToTalk) return
            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Microphone error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                SpeechRecognizer.ERROR_NETWORK -> "Network error during speech recognition"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> if (handsFree) "" else "No speech heard"
                else -> if (handsFree) "" else "Speech recognition failed"
            }
            if (message.isNotBlank()) onError(message)
            if (handsFree && error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                startListening()
            }
        }
    }

    init {
        if (SpeechRecognizer.isRecognitionAvailable(appContext)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
                setRecognitionListener(listener)
            }
        }
        tts = TextToSpeech(appContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = Locale.getDefault()
            }
        }
    }

    val available: Boolean get() = recognizer != null

    fun startPushToTalk() {
        if (recognizer == null) {
            onError("Speech recognition unavailable")
            return
        }
        pushToTalk = true
        handsFree = false
        startListening()
    }

    fun stopPushToTalk() {
        pushToTalk = false
        recognizer?.stopListening()
    }

    fun setHandsFree(enabled: Boolean) {
        handsFree = enabled
        if (enabled) {
            pushToTalk = false
            startListening()
        } else {
            recognizer?.stopListening()
        }
    }

    fun speak(text: String) {
        if (!ttsReady || text.isBlank()) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "grok-reply")
    }

    fun stopSpeak() {
        tts?.stop()
    }

    fun release() {
        handsFree = false
        pushToTalk = false
        recognizer?.destroy()
        recognizer = null
        tts?.shutdown()
        tts = null
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer?.startListening(intent)
    }

    private fun Bundle.text(): String? =
        getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
}
