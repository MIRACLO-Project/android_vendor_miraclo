// SPDX-FileCopyrightText: MIRACLO
// SPDX-License-Identifier: Apache-2.0

package org.miraclo.dolby

import android.media.audiofx.AudioEffect
import android.os.SystemProperties
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * The Dolby Atmos engine that ships in the phone's vendor image (the "dap" audio effect).
 * One global effect on the output mix; every setting lives in a numbered Dolby profile.
 * Integers go to the engine little-endian.
 */
class Dap {

    private var fx: AudioEffect? = null

    /** Opens the engine if it is not open (or was lost when the audio server restarted). */
    @Synchronized
    fun open(): Boolean {
        val cur = fx
        if (cur != null) {
            val ok = try { cur.hasControl() } catch (e: Exception) { false }
            if (ok) return true
            release()
        }
        return try {
            fx = AudioEffect(EFFECT_TYPE_NULL, DAP_UUID, PRIORITY, 0)
            Log.i(TAG, "engine opened")
            true
        } catch (e: Exception) {
            Log.w(TAG, "engine not available: " + e.message)
            fx = null
            false
        }
    }

    @Synchronized
    fun release() {
        try { fx?.release() } catch (e: Exception) { }
        fx = null
    }

    val isOpen: Boolean get() = fx != null

    @Synchronized
    fun setEnabled(on: Boolean): Boolean {
        val e = fx ?: return false
        return try {
            e.setEnabled(on)
            setInt(PARAM_ENABLE, if (on) 1 else 0)
        } catch (x: Exception) { fail(x) }
    }

    @Synchronized
    fun setProfile(profile: Int): Boolean = setInt(PARAM_PROFILE, profile)

    /** Puts every setting of [profile] back to the values in the stock Dolby configuration. */
    @Synchronized
    fun resetProfile(profile: Int): Boolean = setInt(PARAM_RESET_PROFILE, profile)

    @Synchronized
    fun getInt(param: Int): Int? {
        val e = fx ?: return null
        return try {
            val buf = ByteArray(12)
            if (e.getParameter(PARAM_VALUES + param, buf) < 0) null else le(buf).int
        } catch (x: Exception) { fail(x); null }
    }

    /** Writes one profile setting ([id] is a Dolby parameter id). */
    @Synchronized
    fun set(profile: Int, id: Int, values: IntArray): Boolean {
        val e = fx ?: return false
        return try {
            val b = le(ByteBuffer.allocate((values.size + 4) * 4))
            b.putInt(PARAM_DAP).putInt(values.size + 1).putInt(profile).putInt(id)
            values.forEach { b.putInt(it) }
            e.setParameter(PARAM_VALUES, b.array()) >= 0
        } catch (x: Exception) { fail(x) }
    }

    @Synchronized
    fun get(profile: Int, id: Int, length: Int): IntArray? {
        val e = fx ?: return null
        return try {
            val buf = ByteArray((length + 2) * 4)
            if (e.getParameter((id shl 16) + (profile shl 8) + PARAM_DAP + PARAM_VALUES, buf) < 0) return null
            val b = le(buf)
            IntArray(length) { b.int }
        } catch (x: Exception) { fail(x); null }
    }

    private fun setInt(param: Int, value: Int): Boolean {
        val e = fx ?: return false
        return try {
            val b = le(ByteBuffer.allocate(12)).putInt(param).putInt(1).putInt(value)
            e.setParameter(PARAM_VALUES, b.array()) >= 0
        } catch (x: Exception) { fail(x) }
    }

    private fun fail(x: Exception): Boolean {
        Log.w(TAG, "engine call failed: " + x.message)
        release()
        return false
    }

    private fun le(b: ByteBuffer): ByteBuffer = b.order(ByteOrder.LITTLE_ENDIAN)
    private fun le(a: ByteArray): ByteBuffer = ByteBuffer.wrap(a).order(ByteOrder.LITTLE_ENDIAN)

    companion object {
        const val TAG = "MiraDolby"
        private val EFFECT_TYPE_NULL: UUID = UUID.fromString("ec7178ec-e5e1-4432-a3f4-4657e6795210")
        private val DAP_UUID: UUID = UUID.fromString("9d4921da-8225-4f29-aefa-39537a04bcaa")
        private const val PRIORITY = 100

        private const val PARAM_ENABLE = 0
        private const val PARAM_VALUES = 5
        private const val PARAM_DAP = 0x1000000
        private const val PARAM_PROFILE = 0xA000000
        private const val PARAM_RESET_PROFILE = 0xC000000

        // Dolby parameter ids.
        const val HEADPHONE_VIRTUALIZER = 101
        const val SPEAKER_VIRTUALIZER = 102
        const val VOLUME_LEVELER = 103
        const val IEQ_PRESET = 104
        const val DIALOGUE_ENHANCER = 105
        const val DIALOGUE_AMOUNT = 108
        const val GEQ_GAINS = 110
        const val BASS_ENHANCER = 111
        const val STEREO_WIDTH = 113

        /** Centres of the engine's 20 graphic-equalizer bands, in Hz. */
        val GEQ_HZ = floatArrayOf(32f, 47f, 141f, 234f, 328f, 469f, 656f, 844f, 1031f, 1313f,
            1688f, 2250f, 3000f, 3750f, 4688f, 5813f, 7125f, 9000f, 11250f, 19688f)
        const val GEQ_LIMIT = 150

        // Engine units. Kept in one place, and each can be changed for testing with a property.
        val GEQ_PER_DB: Int get() = SystemProperties.getInt("persist.mira.dolby.geq_per_db", 10)
        val WIDTH_MIN: Int get() = SystemProperties.getInt("persist.mira.dolby.width_min", 4)
        val WIDTH_MAX: Int get() = SystemProperties.getInt("persist.mira.dolby.width_max", 64)
        val DIALOGUE_MAX: Int get() = SystemProperties.getInt("persist.mira.dolby.dialogue_max", 12)
    }
}
