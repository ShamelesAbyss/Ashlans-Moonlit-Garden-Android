package com.shamelesabyss.moonlitgarden.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/** Small, local-only garden ambience and UI chimes; kept independent from the Media3 music player. */
class SoundscapeController(context: Context) {
    private val appContext = context.applicationContext
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(5)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val sampleNames = mutableMapOf<Int, String>()
    private val loadedSamples = mutableMapOf<String, Int>()
    private val loadedSampleIds = mutableSetOf<Int>()
    private var ambienceVolume = 0.35f
    private var effectVolume = 0.45f
    private var weather = "clear"
    private var started = false
    private var ambienceStreamId = 0

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                loadedSampleIds += sampleId
                sampleNames[sampleId]?.let { loadedSamples[it] = sampleId }
                syncAmbience()
            }
        }
        val files = listOf(
            "ambience-garden" to "soundscape/ambience-garden.wav",
            "ambience-rain" to "soundscape/ambience-rain.wav",
            "plant" to "soundscape/sfx-plant.wav",
            "water" to "soundscape/sfx-water.wav",
            "brew" to "soundscape/sfx-brew.wav",
            "harvest" to "soundscape/sfx-harvest.wav",
            "softspark" to "soundscape/sfx-softspark.wav",
        )
        files.forEach { (name, path) ->
            runCatching {
                appContext.assets.openFd(path).use { descriptor ->
                    val sampleId = soundPool.load(descriptor, 1)
                    sampleNames[sampleId] = name
                    if (sampleId in loadedSampleIds) loadedSamples[name] = sampleId
                }
            }
        }
    }

    fun start() {
        started = true
        syncAmbience()
    }

    fun pause() {
        started = false
        stopAmbience()
        soundPool.autoPause()
    }

    fun setAmbience(volume: Float, currentWeather: String) {
        ambienceVolume = volume.coerceIn(0f, 1f)
        val nextWeather = if (currentWeather == "rain") "rain" else "clear"
        if (weather != nextWeather) {
            weather = nextWeather
            stopAmbience()
        }
        if (ambienceStreamId != 0) {
            if (ambienceVolume <= 0f) stopAmbience()
            else soundPool.setVolume(ambienceStreamId, ambienceVolume, ambienceVolume)
        } else syncAmbience()
    }

    fun setEffects(volume: Float) {
        effectVolume = volume.coerceIn(0f, 1f)
    }

    fun playEffect(name: String) {
        if (!started || effectVolume <= 0f) return
        val id = loadedSamples[name] ?: return
        soundPool.play(id, effectVolume, effectVolume, 1, 0, 1f)
    }

    fun release() {
        started = false
        stopAmbience()
        soundPool.release()
    }

    private fun syncAmbience() {
        if (!started || ambienceVolume <= 0f || ambienceStreamId != 0) return
        val name = if (weather == "rain") "ambience-rain" else "ambience-garden"
        val id = loadedSamples[name] ?: return
        ambienceStreamId = soundPool.play(id, ambienceVolume, ambienceVolume, 0, -1, 1f)
    }

    private fun stopAmbience() {
        if (ambienceStreamId != 0) {
            soundPool.stop(ambienceStreamId)
            ambienceStreamId = 0
        }
    }
}
