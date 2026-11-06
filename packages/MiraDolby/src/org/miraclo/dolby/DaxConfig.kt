// SPDX-FileCopyrightText: MIRACLO
// SPDX-License-Identifier: Apache-2.0

package org.miraclo.dolby

import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File

/** Mira Dolby's profile cards, in the order they are shown. */
enum class Kind(val label: String, val icon: String) {
    DYNAMIC("Dynamic", "M3 12h3l3-7 4 14 3-7h5"),
    MOVIE("Movie", "M3 6h18v12H3z M10 9.5v5l4-2.5z"),
    MUSIC("Music", "M9 18V5l11-2v13 M9 18a2.5 2.5 0 1 1-5 0a2.5 2.5 0 0 1 5 0 M20 16a2.5 2.5 0 1 1-5 0a2.5 2.5 0 0 1 5 0"),
    GAME("Game", "M7 7h10a5 5 0 0 1 0 10H7A5 5 0 0 1 7 7z M7 10.5v3 M5.5 12h3"),
    VOICE("Voice", "M12 3a3 3 0 0 1 3 3v5a3 3 0 0 1-6 0V6a3 3 0 0 1 3-3z M5 11a7 7 0 0 0 14 0 M12 18v3"),
    CUSTOM("Custom", "M4 6h10 M18 6h2 M4 12h4 M12 12h8 M4 18h12 M16 4v4 M10 10v4 M18 16v4"),
}

/**
 * What the phone's own Dolby configuration (/vendor/etc/dolby/dax-default.xml) offers:
 * the engine's number for each profile, and the intelligent-equalizer choices.
 */
class DaxConfig private constructor(val profileIds: Map<Kind, Int>, val ieqNames: List<String>) {

    fun id(kind: Kind): Int = profileIds[kind] ?: FALLBACK.getValue(kind)

    fun kindOf(id: Int): Kind = Kind.values().firstOrNull { id(it) == id } ?: Kind.DYNAMIC

    companion object {
        private const val PATH = "/vendor/etc/dolby/dax-default.xml"
        private val FALLBACK = mapOf(Kind.DYNAMIC to 0, Kind.MOVIE to 1, Kind.MUSIC to 2,
            Kind.CUSTOM to 3, Kind.VOICE to 4, Kind.GAME to 5)
        private val FALLBACK_IEQ = listOf("Open", "Rich", "Focused")

        fun load(): DaxConfig {
            val ids = HashMap<Kind, Int>()
            val ieq = ArrayList<String>()
            try {
                File(PATH).inputStream().use { input ->
                    val p = Xml.newPullParser()
                    p.setInput(input, null)
                    while (p.next() != XmlPullParser.END_DOCUMENT) {
                        if (p.eventType != XmlPullParser.START_TAG) continue
                        val id = p.getAttributeValue(null, "id") ?: continue
                        if (p.name == "profile") {
                            val name = (p.getAttributeValue(null, "name") ?: "").lowercase()
                            val n = id.toIntOrNull() ?: continue
                            Kind.values().firstOrNull { name.contains(it.name.lowercase()) }
                                ?.let { if (it !in ids) ids[it] = n }
                        } else if (id.startsWith("ieq_")) {
                            val label = id.removePrefix("ieq_").replace('_', ' ')
                                .replaceFirstChar { it.uppercase() }
                            if (label !in ieq) ieq.add(label)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(Dap.TAG, "could not read $PATH: " + e.message)
            }
            val cfg = DaxConfig(ids, if (ieq.isEmpty()) FALLBACK_IEQ else ieq)
            Log.i(Dap.TAG, "profiles " + Kind.values().joinToString { it.label + "=" + cfg.id(it) } +
                (if (ids.isEmpty()) " (defaults)" else "") + "; intelligent EQ " + cfg.ieqNames)
            return cfg
        }
    }
}
