package com.example.wordaire.ui.gameplay

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator

/** Lightweight game feedback without shipping large audio assets. */
class GameSoundEffects {
    private var tones: ToneGenerator? = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
    private var soundEnabled = true

    fun setSoundEnabled(enabled: Boolean) {
        soundEnabled = enabled
    }

    fun isSoundEnabled(): Boolean = soundEnabled

    fun cardDrawn() {
        if (soundEnabled) try { tones?.startTone(ToneGenerator.TONE_PROP_BEEP2, 70) } catch (e: Exception) {}
    }

    fun correctDrop() {
        if (soundEnabled) try { tones?.startTone(ToneGenerator.TONE_PROP_ACK, 120) } catch (e: Exception) {}
    }

    fun wrongDrop() {
        if (soundEnabled) try { tones?.startTone(ToneGenerator.TONE_PROP_NACK, 180) } catch (e: Exception) {}
    }

    fun levelComplete() {
        if (soundEnabled) try { tones?.startTone(ToneGenerator.TONE_PROP_BEEP2, 280) } catch (e: Exception) {}
    }

    fun release() {
        tones?.release()
        tones = null
    }
}
