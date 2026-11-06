// SPDX-FileCopyrightText: MIRACLO
// SPDX-License-Identifier: Apache-2.0

package org.miraclo.dolby

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

/** Everything one Dolby profile sounds like, in the units the app shows. */
data class Tune(
    val eq: FloatArray = FloatArray(BANDS),   // dB per band, EQ_MIN..EQ_MAX
    val bass: Float = 0f,                     // dB, tone
    val treble: Float = 0f,                   // dB, tone
    val virtSpeaker: Boolean = true,
    val virtHeadphones: Boolean = true,
    val width: Int = 50,                      // 0..100
    val dialogue: Boolean = false,
    val dialogueAmount: Int = 50,             // 0..100
    val leveller: Boolean = false,
    val ieq: Int = 0,                         // 0 = off, then the stock choices
    val bassEnhancer: Boolean = false,
) {
    fun withBand(i: Int, db: Float): Tune = copy(eq = eq.copyOf().also { it[i] = db })

    fun toJson(): JSONObject = JSONObject()
        .put("eq", JSONArray().also { a -> eq.forEach { a.put(it.toDouble()) } })
        .put("bass", bass.toDouble()).put("treble", treble.toDouble())
        .put("virtSpeaker", virtSpeaker).put("virtHeadphones", virtHeadphones)
        .put("width", width).put("dialogue", dialogue).put("dialogueAmount", dialogueAmount)
        .put("leveller", leveller).put("ieq", ieq).put("bassEnhancer", bassEnhancer)

    /** The 20 engine equalizer gains: the bands, plus the bass and treble tone. */
    fun geq(): IntArray {
        val unit = Dap.GEQ_PER_DB
        return IntArray(Dap.GEQ_HZ.size) { i ->
            val f = Dap.GEQ_HZ[i]
            val db = at(eq, f) + bass * lowWeight(f) + treble * highWeight(f)
            (db * unit).roundToInt().coerceIn(-Dap.GEQ_LIMIT, Dap.GEQ_LIMIT)
        }
    }

    fun sameAs(o: Tune): Boolean = toJson().toString() == o.toJson().toString()

    override fun equals(other: Any?): Boolean = other is Tune && sameAs(other)
    override fun hashCode(): Int = toJson().toString().hashCode()

    companion object {
        const val BANDS = 10
        val HZ = floatArrayOf(32f, 64f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
        val HZ_LABEL = arrayOf("32", "64", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
        const val EQ_MIN = -12f
        const val EQ_MAX = 12f
        const val TONE_MIN = -6f
        const val TONE_MAX = 6f

        fun fromJson(o: JSONObject, base: Tune): Tune {
            val a = o.optJSONArray("eq")
            val eq = FloatArray(BANDS) { i ->
                if (a != null && i < a.length()) a.optDouble(i, 0.0).toFloat() else base.eq[i]
            }
            return Tune(
                eq = eq,
                bass = o.optDouble("bass", base.bass.toDouble()).toFloat(),
                treble = o.optDouble("treble", base.treble.toDouble()).toFloat(),
                virtSpeaker = o.optBoolean("virtSpeaker", base.virtSpeaker),
                virtHeadphones = o.optBoolean("virtHeadphones", base.virtHeadphones),
                width = o.optInt("width", base.width),
                dialogue = o.optBoolean("dialogue", base.dialogue),
                dialogueAmount = o.optInt("dialogueAmount", base.dialogueAmount),
                leveller = o.optBoolean("leveller", base.leveller),
                ieq = o.optInt("ieq", base.ieq),
                bassEnhancer = o.optBoolean("bassEnhancer", base.bassEnhancer),
            )
        }

        /** Gain at [f] Hz on the line through the band points (log frequency). */
        fun at(eq: FloatArray, f: Float): Float {
            if (f <= HZ[0]) return eq[0]
            if (f >= HZ[BANDS - 1]) return eq[BANDS - 1]
            var i = 0
            while (HZ[i + 1] < f) i++
            val t = (ln(f) - ln(HZ[i])) / (ln(HZ[i + 1]) - ln(HZ[i]))
            return eq[i] + (eq[i + 1] - eq[i]) * t
        }

        /** The ten bands that come closest to 20 engine gains (used for the stock values). */
        fun bandsFromGeq(g: IntArray): FloatArray {
            val unit = Dap.GEQ_PER_DB.toFloat()
            return FloatArray(BANDS) { i ->
                val f = HZ[i]
                var j = 0
                while (j < Dap.GEQ_HZ.size - 2 && Dap.GEQ_HZ[j + 1] < f) j++
                val f0 = Dap.GEQ_HZ[j]; val f1 = Dap.GEQ_HZ[j + 1]
                val t = ((ln(f) - ln(f0)) / (ln(f1) - ln(f0))).coerceIn(0f, 1f)
                val db = (g[j] + (g[j + 1] - g[j]) * t) / unit
                (db * 2).roundToInt() / 2f
            }
        }

        private fun lowWeight(f: Float): Float = ramp(f, 400f, 100f)
        private fun highWeight(f: Float): Float = ramp(f, 2000f, 8000f)

        /** 0 at [from] Hz, 1 at [to] Hz, smooth in between (log frequency). */
        private fun ramp(f: Float, from: Float, to: Float): Float {
            val t = ((ln(f) - ln(from)) / (ln(to) - ln(from))).coerceIn(0f, 1f)
            return t * t * (3 - 2 * t)
        }

        fun dbText(v: Float): String {
            val r = (v * 2).roundToInt() / 2f
            val s = if (abs(r - r.roundToInt()) < 0.01f) r.roundToInt().toString() else r.toString()
            return if (r > 0) "+$s dB" else "$s dB"
        }
    }
}

/** A sound preset: one of the EasyEffects presets, or one the user saved. */
data class Preset(
    val name: String,
    val desc: String,
    val tags: List<String>,
    val closeMatch: Boolean,
    val user: Boolean,
    val tune: Tune,
) {
    val key: String get() = (if (user) "user:" else "builtin:") + name

    fun toJson(): JSONObject = JSONObject().put("name", name).put("tune", tune.toJson())

    companion object {
        /** A preset from the bundled presets file, on top of the Custom profile's stock values. */
        fun builtin(o: JSONObject, base: Tune): Preset {
            val fx = o.getJSONObject("fx")
            val eq = o.getJSONArray("eq")
            val virt = fx.optBoolean("virt", false)
            val t = Tune(
                eq = FloatArray(Tune.BANDS) { eq.getDouble(it).toFloat() },
                bass = fx.optDouble("bass", 0.0).toFloat(),
                treble = fx.optDouble("treble", 0.0).toFloat(),
                virtSpeaker = virt, virtHeadphones = virt,
                width = fx.optInt("width", base.width),
                dialogue = fx.optBoolean("de", false),
                dialogueAmount = base.dialogueAmount,
                leveller = fx.optBoolean("lev", false),
                ieq = fx.optInt("ieq", 0),
                bassEnhancer = fx.optBoolean("bassEnh", false),
            )
            val tags = o.getJSONArray("tags")
            return Preset(o.getString("name"), o.getString("desc"),
                List(tags.length()) { tags.getString(it) }, o.optBoolean("close"), false, t)
        }

        fun user(o: JSONObject, base: Tune): Preset {
            val t = Tune.fromJson(o.getJSONObject("tune"), base)
            return Preset(o.getString("name"), "Saved by you", listOf("EQ"), false, true, t)
        }
    }
}
