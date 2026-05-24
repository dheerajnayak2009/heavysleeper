package com.example.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.*
import java.util.*
import kotlin.math.sin

class SoundSynthesizer(private val context: Context) : TextToSpeech.OnInitListener {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val synthJob = Job()
    private val synthScope = CoroutineScope(Dispatchers.Default + synthJob)
    private var soundPlayingJob: Job? = null

    private var currentFrequency = 1100f
    private var overkillMode = false
    private var isRunning = false

    // Abuse logs for TTS to shout at the user
    private val standardPhrases = listOf(
        "ALERT! DETECTED EXTREME SLOTH STATE!",
        "SHAKE THE PHONE NOW! DISOBEDIENCE IS PUNISHED!",
        "WAKE UP! SHAKE TWENTY TIMES! CHOP CHOP!",
        "NO EXCUSES! SHAKE ME BEFORE I CALL YOUR BOSS!",
        "GET UP! GET UP! GET UP!"
    )

    private val overkillPhrases = listOf(
        "SNOOZE DETECTED! ANNIHILATION SEQUENCE INITIATED!",
        "WEAKNESS DETECTED! DO NOT DISOBEY THE ALARM SYSTEM!",
        "INITIATING VOLUME SLEDGEHAMMER! SHAKE FASTER!",
        "STRENGTHEN YOUR REVOLVE AND SHAKE ME THIRTY TIMES!",
        "YOU CHOSE SNOOZE, NOW CHOOSE CRITICAL AWAKENING CHASSIS DAMAGE!"
    )

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isTtsReady = true
                tts?.setSpeechRate(1.2f)
                tts?.setPitch(1.3f)
            }
        }
    }

    /**
     * Set maximum volume across alarm and music streams.
     * Overrides manual physical volume down presses.
     */
    fun forceMaximumVolume() {
        try {
            val maxAlarm = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val maxMusic = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarm, 0)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusic, 0)
        } catch (e: Exception) {
            Log.e("SoundSynthesizer", "Error forcing volume", e)
        }
    }

    /**
     * Starts the alarm audio loop and periodic speech shouts.
     */
    fun startAlarm(overkill: Boolean = false) {
        if (isRunning) return
        isRunning = true
        overkillMode = overkill
        forceMaximumVolume()

        // Loop that forces maximum volume every 1 second to combat physical hardware volume-down keys
        soundPlayingJob = synthScope.launch {
            // Launch the synthesizer core oscillator thread
            val synthTrackJob = launch { playSynthOscillator() }

            // Loop for TTS voices and volume clamping
            var ttsIndex = 0
            while (isActive) {
                forceMaximumVolume()

                // Shout at user via TTS periodically
                if (isTtsReady) {
                    val textList = if (overkillMode) overkillPhrases else standardPhrases
                    val textToShout = textList[ttsIndex % textList.size]
                    ttsIndex++

                    // Speak text
                    tts?.setSpeechRate(if (overkillMode) 1.6f else 1.2f)
                    tts?.setPitch(if (overkillMode) 1.5f else 1.2f)
                    tts?.speak(textToShout, TextToSpeech.QUEUE_ADD, null, "heavy_sleeper_shout")
                }

                // Pause between phrases (longer pause for normal, shorter/louder/more chaotic during overkill)
                delay(if (overkillMode) 6000L else 11000L)
            }
        }
    }

    /**
     * Core dynamic siren synthesis loop using AudioTrack
     */
    private suspend fun playSynthOscillator() = withContext(Dispatchers.Default) {
        val sampleRate = 44100
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val audioTrack = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            Log.e("SoundSynthesizer", "Failed to construct AudioTrack, aborting oscillator", e)
            return@withContext
        }

        audioTrack.play()

        val bufferSize = 8000
        val buffer = ShortArray(bufferSize)
        var phase = 0.0

        // In overkill, frequencies sweep much faster and reach highly piercing pitches
        var carrierFreq = if (overkillMode) 2200.0 else 1100.0
        var factor = 1.0

        try {
            while (coroutineContext.isActive) {
                // Synthesize aggressive frequency modulation (FM / siren sweeps)
                for (i in 0 until bufferSize) {
                    val t = i.toDouble() / sampleRate
                    
                    // Frequency Sweep: Modulation creates a rising and falling pitch (wah-wah alarm siren look)
                    val modRate = if (overkillMode) 8.0 else 3.5
                    val modWidth = if (overkillMode) 800.0 else 400.0
                    val currentFreq = carrierFreq + (sin(2.0 * Math.PI * modRate * (phase / sampleRate)) * modWidth)

                    // Audio Sample calculation
                    buffer[i] = (sin(2.0 * Math.PI * currentFreq * t) * Short.MAX_VALUE * 0.9).toInt().toShort()
                    phase += 1.0
                }

                // Write the wave sample buffer to the AudioTrack playing on maximum volume
                audioTrack.write(buffer, 0, buffer.size)
                
                // Keep keeping track of phase limits to prevent float overflows
                if (phase > 1_000_000) phase = 0.0
                
                // Force MAX system volume block inside sound stream
                forceMaximumVolume()
                delay(10)
            }
        } catch (e: Exception) {
            Log.e("SoundSynthesizer", "Synthesizer stream crash/closure", e)
        } finally {
            try {
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    /**
     * Stop all synthesized alarm sounds and speech.
     */
    fun stopAlarm() {
        isRunning = false
        soundPlayingJob?.cancel()
        soundPlayingJob = null
        try {
            tts?.stop()
        } catch (e: Exception) {
            // ignore
        }
    }

    fun release() {
        stopAlarm()
        synthJob.cancel()
        try {
            tts?.shutdown()
        } catch (e: Exception) {
            // ignore
        }
    }
}
