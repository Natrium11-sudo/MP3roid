package com.mp3roid.player.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val BASS_MAX = 1000

data class EqBand(
    val index: Int,
    val frequencyHz: Int,
    val levelMb: Int,
    val minMb: Int,
    val maxMb: Int,
)

data class AudioEffectsState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val bands: List<EqBand> = emptyList(),
    val presets: List<String> = emptyList(),
    val preset: Int = -1,
    val bassAvailable: Boolean = false,
    val bassStrength: Int = 0,
    val bassMax: Int = BASS_MAX,
)

/**
 * Equalizer und Bass-Boost für die eigene Wiedergabe.
 *
 * Die Effekte hängen an der Audio-Session des ExoPlayers. Da der Dienst im
 * selben Prozess läuft wie die Oberfläche, reicht ein einfaches Objekt als
 * gemeinsamer Zustand – kein Binder nötig.
 */
object AudioEffects {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var sessionId: Int = -1

    private var wantEnabled = false
    private var wantPreset = -1
    private var wantBass = 0

    private val _state = MutableStateFlow(AudioEffectsState())
    val state: StateFlow<AudioEffectsState> = _state.asStateFlow()

    fun attach(id: Int) {
        if (id <= 0 || id == sessionId) return
        detach()
        sessionId = id
        try {
            val eq = Equalizer(0, id)
            equalizer = eq
            bassBoost = try {
                BassBoost(0, id)
            } catch (t: Throwable) {
                null
            }
            val bands = (0 until eq.numberOfBands.toInt()).map { i ->
                val band = i.toShort()
                val range = eq.getBandLevelRange()
                val freqRange = try {
                    eq.getBandFreqRange(band)
                } catch (t: Throwable) {
                    null
                }
                EqBand(
                    index = i,
                    frequencyHz = freqRange?.let { (it[0] + it[1]) / 2000 } ?: 0,
                    levelMb = eq.getBandLevel(band).toInt(),
                    minMb = range[0].toInt(),
                    maxMb = range[1].toInt(),
                )
            }
            val presets = (0 until eq.numberOfPresets.toInt()).map { i ->
                try {
                    eq.getPresetName(i.toShort())
                } catch (t: Throwable) {
                    "Preset $i"
                }
            }
            _state.value = AudioEffectsState(
                available = true,
                enabled = eq.enabled,
                bands = bands,
                presets = presets,
                preset = -1,
                bassAvailable = bassBoost?.strengthSupported == true,
                bassStrength = if (bassBoost?.strengthSupported == true) {
                    bassBoost?.roundedStrength?.toInt() ?: 0
                } else {
                    0
                },
            )
            applyStored()
        } catch (t: Throwable) {
            equalizer = null
            bassBoost = null
            _state.value = AudioEffectsState(available = false)
        }
    }

    /** Wendet die gespeicherten Wunschwerte an, sobald eine Session da ist. */
    fun applyStored(enabled: Boolean = wantEnabled, preset: Int = wantPreset, bass: Int = wantBass) {
        wantEnabled = enabled
        wantPreset = preset
        wantBass = bass
        val eq = equalizer ?: return
        try {
            eq.enabled = false
            if (preset >= 0) {
                eq.usePreset(preset.toShort())
            }
            if (bass > 0 && bassBoost?.strengthSupported == true) {
                bassBoost?.setStrength(bass.coerceIn(0, BASS_MAX).toShort())
            }
            eq.enabled = enabled
            pushState()
        } catch (t: Throwable) {
            // Gerät ohne brauchbare Effekte: einfach still bleiben.
        }
    }

    fun setEnabled(enabled: Boolean) {
        wantEnabled = enabled
        try {
            equalizer?.enabled = enabled
        } catch (t: Throwable) {
            // ignorieren
        }
        pushState()
    }

    fun setBand(index: Int, levelMb: Int) {
        val eq = equalizer ?: return
        try {
            val band = index.toShort()
            val range = eq.getBandLevelRange()
            eq.setBandLevel(band, levelMb.coerceIn(range[0].toInt(), range[1].toInt()).toShort())
            wantPreset = -1
            pushState()
        } catch (t: Throwable) {
            // ignorieren
        }
    }

    fun setPreset(index: Int) {
        wantPreset = index
        try {
            equalizer?.usePreset(index.toShort())
        } catch (t: Throwable) {
            // ignorieren
        }
        pushState()
    }

    fun setBass(strength: Int) {
        wantBass = strength
        val bass = bassBoost ?: return
        try {
            if (bass.strengthSupported) {
                bass.setStrength(strength.coerceIn(0, BASS_MAX).toShort())
            }
            pushState()
        } catch (t: Throwable) {
            // ignorieren
        }
    }

    fun detach() {
        try {
            equalizer?.release()
        } catch (t: Throwable) {
            // ignorieren
        }
        try {
            bassBoost?.release()
        } catch (t: Throwable) {
            // ignorieren
        }
        equalizer = null
        bassBoost = null
        sessionId = -1
        _state.value = AudioEffectsState(enabled = wantEnabled, preset = wantPreset, bassStrength = wantBass)
    }

    private fun pushState() {
        val eq = equalizer
        val current = _state.value
        if (eq == null) {
            _state.value = current.copy(enabled = wantEnabled, preset = wantPreset, bassStrength = wantBass)
            return
        }
        val bands = current.bands.map { band ->
            val level = try {
                eq.getBandLevel(band.index.toShort()).toInt()
            } catch (t: Throwable) {
                band.levelMb
            }
            band.copy(levelMb = level)
        }
        _state.value = current.copy(
            available = true,
            enabled = try {
                eq.enabled
            } catch (t: Throwable) {
                wantEnabled
            },
            bands = bands,
            preset = wantPreset,
            bassAvailable = bassBoost?.strengthSupported == true,
            bassStrength = try {
                bassBoost?.roundedStrength?.toInt() ?: wantBass
            } catch (t: Throwable) {
                wantBass
            },
        )
    }
}
