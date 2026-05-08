package com.example.chatbot.core

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.chatbot.models.CoachingPayload
import java.util.Locale

class AudioDeliveryManager(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var mediaPlayer: MediaPlayer? = null

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("AudioDeliveryManager", "The Language specified is not supported!")
            } else {
                isInitialized = true
                Log.d("AudioDeliveryManager", "TTS Initialized successfully.")
            }
        } else {
            Log.e("AudioDeliveryManager", "Initialization Failed!")
        }
    }

    fun deliverInstruction(payload: CoachingPayload) {
        if (payload.audioFile != null) {
            playAudioFromUrl(payload.audioFile)
        } else if (isInitialized) {
            // Log audio latency
            LatencyTracker.markAudioPlaybackStarted()
            
            val textToSpeak = payload.instruction
            tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "coaching_utterance")
            Log.d("AudioDeliveryManager", "Speaking: $textToSpeak")
        } else {
            Log.w("AudioDeliveryManager", "TTS not initialized, cannot speak: ${payload.instruction}")
        }
    }

    private fun playAudioFromUrl(audioFile: String) {
        try {
            val url = "https://storage.googleapis.com/public-race-coaching/$audioFile"
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(url)
                prepareAsync()
                setOnPreparedListener { 
                    LatencyTracker.markAudioPlaybackStarted()
                    start() 
                    Log.d("AudioDeliveryManager", "Playing audio from: $url")
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("AudioDeliveryManager", "MediaPlayer error: $what, $extra")
                    true
                }
            }
        } catch (e: Exception) {
            Log.e("AudioDeliveryManager", "Failed to play audio", e)
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
