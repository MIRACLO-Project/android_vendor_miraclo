// SPDX-FileCopyrightText: MIRACLO
// SPDX-License-Identifier: Apache-2.0

package org.miraclo.dolby

import android.app.ActivityManager
import android.app.ActivityTaskManager
import android.app.TaskStackListener
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.service.quicksettings.TileService
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Mira Dolby's state, and the one place that talks to the engine. The settings are kept in
 * device-protected storage so they are back on before the phone is first unlocked.
 * Everything public is called on the main thread.
 */
object Dolby {

    private lateinit var app: Context
    private lateinit var prefs: SharedPreferences
    lateinit var dax: DaxConfig
        private set

    private val dap = Dap()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var engine: Handler
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    // Read on the engine thread too.
    private val stored = ConcurrentHashMap<Kind, Tune>()
    @Volatile private var defaults: Map<Kind, Tune> = emptyMap()
    private var builtinJson: List<JSONObject> = emptyList()
    private val userPresets = CopyOnWriteArrayList<JSONObject>()
    private val appChoices = ConcurrentHashMap<String, String>()

    var enabled = true; private set
    var kind = Kind.DYNAMIC; private set
    var presetKey: String? = null; private set
    var appsOn = true; private set
    /** The engine opened and answers. False on a phone without Dolby. */
    var available = false; private set
    /** The engine did not answer after many tries: this phone has no Dolby engine. */
    var missing = false; private set
    /** Headphones (wired, USB or Bluetooth) are connected. */
    var headphones = false; private set

    @Volatile private var foreground: String? = null
    private var appliedKey = ""
    private var retries = 0
    private var partial = 0
    private var defaultsRead = false

    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        prefs = app.createDeviceProtectedStorageContext().getSharedPreferences("dolby", Context.MODE_PRIVATE)
        dax = DaxConfig.load()
        load()
        engine = Handler(HandlerThread("MiraDolby").also { it.start() }.looper)
        watchOutputs()
        watchApps()
        watchAudioServer()
        apply(0)
    }

    // ---- reading ----------------------------------------------------------------------------

    fun tune(k: Kind = kind): Tune = stored[k] ?: defaultOf(k)

    fun defaultOf(k: Kind): Tune = defaults[k] ?: Tune()

    fun presets(): List<Preset> {
        val base = defaultOf(Kind.CUSTOM)
        return builtinJson.map { Preset.builtin(it, base) } + userPresets.map { Preset.user(it, base) }
    }

    fun preset(key: String?): Preset? = if (key == null) null else presets().firstOrNull { it.key == key }

    /** The preset in use, when the Custom profile still has its values. */
    val activePreset: Preset? get() = if (kind == Kind.CUSTOM) preset(presetKey) else null

    val presetEdited: Boolean get() = activePreset?.let { !it.tune.sameAs(tune(Kind.CUSTOM)) } ?: false

    /** What "Reset to default" goes back to: the preset's own values, else the stock profile. */
    fun baseline(): Tune = activePreset?.tune ?: defaultOf(kind)

    fun appChoice(pkg: String): String? = appChoices[pkg]

    fun ieqNames(): List<String> = listOf("Off") + dax.ieqNames

    // ---- changing ---------------------------------------------------------------------------

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }

    fun setEnabled(on: Boolean) {
        enabled = on
        prefs.edit().putBoolean("enabled", on).apply()
        changed()
    }

    /** A profile card: plays that profile; any preset is no longer in use. */
    fun selectKind(k: Kind) {
        kind = k
        presetKey = null
        prefs.edit().putString("kind", k.name).remove("preset").apply()
        changed()
    }

    /** A preset: the Custom profile takes the preset's own values (again, if picked again). */
    fun pickPreset(p: Preset) {
        kind = Kind.CUSTOM
        presetKey = p.key
        stored[Kind.CUSTOM] = p.tune
        prefs.edit().putString("kind", kind.name).putString("preset", p.key)
            .putString("tune_" + Kind.CUSTOM.name, p.tune.toJson().toString()).apply()
        changed()
    }

    /** Changes the current profile's values. */
    fun edit(change: (Tune) -> Tune) {
        val t = change(tune())
        stored[kind] = t
        prefs.edit().putString("tune_" + kind.name, t.toJson().toString()).apply()
        changed(40)
    }

    /** Every Mira Dolby setting back to how Mira ships it. */
    fun resetAll() {
        stored.clear()
        appChoices.clear()
        userPresets.clear()
        enabled = true; kind = Kind.DYNAMIC; presetKey = null; appsOn = true
        prefs.edit().clear().putString("defaults", defaultsJson().toString()).apply()
        changed()
    }

    fun saveUserPreset(name: String) {
        userPresets.removeAll { it.optString("name") == name }
        userPresets.add(JSONObject().put("name", name).put("tune", tune().toJson()))
        saveUserPresets()
        pickPreset(presets().first { it.user && it.name == name })
    }

    fun renameUserPreset(p: Preset, name: String) {
        userPresets.firstOrNull { it.optString("name") == p.name }?.put("name", name)
        val key = p.copy(name = name).key
        if (presetKey == p.key) presetKey = key
        for ((pkg, c) in appChoices) if (c == p.key) appChoices[pkg] = key
        prefs.edit().putString("preset", presetKey).putString("apps", appsJson()).apply()
        saveUserPresets()
        changed()
    }

    fun deleteUserPreset(p: Preset) {
        userPresets.removeAll { it.optString("name") == p.name }
        if (presetKey == p.key) presetKey = null
        appChoices.values.removeAll { it == p.key }
        prefs.edit().putString("preset", presetKey).putString("apps", appsJson()).apply()
        saveUserPresets()
        changed()
    }

    fun setAppsOn(on: Boolean) {
        appsOn = on
        prefs.edit().putBoolean("apps_on", on).apply()
        changed()
    }

    /** [choice] is "kind:<KIND>", a preset key, or null for the home page settings. */
    fun setApp(pkg: String, choice: String?) {
        if (choice == null) appChoices.remove(pkg) else appChoices[pkg] = choice
        prefs.edit().putString("apps", appsJson()).apply()
        changed()
    }

    fun clearApps() {
        appChoices.clear()
        appsOn = true
        prefs.edit().putString("apps", "{}").putBoolean("apps_on", true).apply()
        changed()
    }

    private fun saveUserPresets() {
        prefs.edit().putString("user_presets", JSONArray(userPresets).toString()).apply()
    }

    private fun changed(delay: Long = 0) {
        listeners.forEach { it() }
        try {
            TileService.requestListeningState(app, ComponentName(app, DolbyTile::class.java))
        } catch (e: Exception) { }
        apply(delay)
    }

    // ---- the engine -------------------------------------------------------------------------

    private fun apply(delay: Long) {
        if (!::engine.isInitialized) return
        engine.removeCallbacksAndMessages(null)
        engine.postDelayed({ applyNow(force = true) }, delay)
    }

    /** What plays now: the app's own choice when it has one, else the home page settings. */
    private fun effective(): Triple<String, Kind, Tune> {
        val choice = foreground?.let { if (appsOn) appChoices[it] else null }
        if (choice != null) {
            if (choice.startsWith("kind:")) {
                val k = Kind.values().firstOrNull { it.name == choice.removePrefix("kind:") }
                if (k != null) return Triple(choice, k, tune(k))
            } else {
                preset(choice)?.let { return Triple(choice, Kind.CUSTOM, it.tune) }
            }
        }
        return Triple("home", kind, tune(kind))
    }

    /** Runs on the engine thread. */
    private fun applyNow(force: Boolean) {
        val (key, k, t) = effective()
        if (!force && key == appliedKey) return
        if (!dap.open()) {
            if (available) main.post { available = false; listeners.forEach { it() } }
            if (retries++ < 30) engine.postDelayed({ applyNow(true) }, if (retries < 5) 2000L else 10000L)
            else main.post { missing = true; listeners.forEach { it() } }
            return
        }
        retries = 0
        if (defaults.isEmpty() && !defaultsRead) { defaultsRead = true; readDefaults() }
        val id = dax.id(k)
        val w = Dap.WIDTH_MIN + (Dap.WIDTH_MAX - Dap.WIDTH_MIN) * t.width / 100
        val ok = dap.set(id, Dap.HEADPHONE_VIRTUALIZER, intArrayOf(b(t.virtHeadphones))) and
            dap.set(id, Dap.SPEAKER_VIRTUALIZER, intArrayOf(b(t.virtSpeaker))) and
            dap.set(id, Dap.STEREO_WIDTH, intArrayOf(w)) and
            dap.set(id, Dap.DIALOGUE_ENHANCER, intArrayOf(b(t.dialogue))) and
            dap.set(id, Dap.DIALOGUE_AMOUNT, intArrayOf(Dap.DIALOGUE_MAX * t.dialogueAmount / 100)) and
            dap.set(id, Dap.VOLUME_LEVELER, intArrayOf(b(t.leveller))) and
            dap.set(id, Dap.IEQ_PRESET, intArrayOf(t.ieq)) and
            dap.set(id, Dap.BASS_ENHANCER, intArrayOf(b(t.bassEnhancer))) and
            dap.set(id, Dap.GEQ_GAINS, t.geq()) and
            dap.setProfile(id) and
            dap.setEnabled(enabled)
        if (!ok) {
            // Once more shortly (the audio server may be starting); a setting this engine does
            // not have keeps failing, and is then left as it is.
            Log.w(Dap.TAG, "some settings did not reach the engine")
            if (partial++ < 2) { engine.postDelayed({ applyNow(true) }, 2000L); return }
        }
        partial = 0
        appliedKey = key
        if (!available) main.post { available = true; missing = false; listeners.forEach { it() } }
    }

    private fun b(v: Boolean) = if (v) 1 else 0

    /** The stock values of every profile, read once from the engine after a reset. */
    private fun readDefaults() {
        val out = HashMap<Kind, Tune>()
        for (k in Kind.values()) {
            val id = dax.id(k)
            dap.resetProfile(id)
            fun one(p: Int) = dap.get(id, p, 1)?.get(0)
            val geq = dap.get(id, Dap.GEQ_GAINS, Dap.GEQ_HZ.size)
            val span = (Dap.WIDTH_MAX - Dap.WIDTH_MIN).coerceAtLeast(1)
            val base = Tune()
            out[k] = Tune(
                eq = if (geq != null) Tune.bandsFromGeq(geq) else base.eq,
                virtHeadphones = one(Dap.HEADPHONE_VIRTUALIZER)?.let { it != 0 } ?: base.virtHeadphones,
                virtSpeaker = one(Dap.SPEAKER_VIRTUALIZER)?.let { it != 0 } ?: base.virtSpeaker,
                width = one(Dap.STEREO_WIDTH)?.let { ((it - Dap.WIDTH_MIN) * 100 / span).coerceIn(0, 100) } ?: base.width,
                dialogue = one(Dap.DIALOGUE_ENHANCER)?.let { it != 0 } ?: base.dialogue,
                dialogueAmount = one(Dap.DIALOGUE_AMOUNT)?.let { (it * 100 / Dap.DIALOGUE_MAX.coerceAtLeast(1)).coerceIn(0, 100) } ?: base.dialogueAmount,
                leveller = one(Dap.VOLUME_LEVELER)?.let { it != 0 } ?: base.leveller,
                ieq = one(Dap.IEQ_PRESET)?.coerceIn(0, dax.ieqNames.size) ?: base.ieq,
                bassEnhancer = one(Dap.BASS_ENHANCER)?.let { it != 0 } ?: base.bassEnhancer,
            )
            Log.i(Dap.TAG, "stock ${k.label} (profile $id): " + out[k]!!.toJson() + " geq " + geq?.joinToString())
        }
        main.post {
            defaults = out
            prefs.edit().putString("defaults", defaultsJson().toString()).apply()
            changed()
        }
    }

    // ---- watching the phone -----------------------------------------------------------------

    private fun watchOutputs() {
        val am = app.getSystemService(AudioManager::class.java)
        val cb = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(d: Array<out AudioDeviceInfo>) = refresh()
            override fun onAudioDevicesRemoved(d: Array<out AudioDeviceInfo>) = refresh()
            fun refresh() {
                val hp = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in HEADPHONES }
                if (hp != headphones) { headphones = hp; listeners.forEach { it() } }
            }
        }
        am.registerAudioDeviceCallback(cb, main)
    }

    private fun watchApps() {
        try {
            ActivityTaskManager.getInstance().registerTaskStackListener(object : TaskStackListener() {
                override fun onTaskMovedToFront(info: ActivityManager.RunningTaskInfo) {
                    val pkg = (info.topActivity ?: info.baseActivity)?.packageName ?: return
                    if (pkg == foreground) return
                    foreground = pkg
                    engine.post { applyNow(force = false) }
                }
            })
        } catch (e: Exception) {
            Log.w(Dap.TAG, "per-app profiles unavailable: " + e.message)
        }
    }

    private fun watchAudioServer() {
        try {
            app.getSystemService(AudioManager::class.java).setAudioServerStateCallback(
                { r -> main.post(r) },
                object : AudioManager.AudioServerStateCallback() {
                    override fun onAudioServerUp() {
                        engine.post { dap.release(); applyNow(true) }
                    }
                })
        } catch (e: Exception) {
            Log.w(Dap.TAG, "audio server watch unavailable: " + e.message)
        }
    }

    private val HEADPHONES = setOf(
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_BROADCAST)

    // ---- saved state ------------------------------------------------------------------------

    private fun load() {
        enabled = prefs.getBoolean("enabled", true)
        kind = Kind.values().firstOrNull { it.name == prefs.getString("kind", null) } ?: Kind.DYNAMIC
        presetKey = prefs.getString("preset", null)
        appsOn = prefs.getBoolean("apps_on", true)
        prefs.getString("defaults", null)?.let { s ->
            try {
                val o = JSONObject(s)
                val d = HashMap<Kind, Tune>()
                for (k in Kind.values()) o.optJSONObject(k.name)?.let { d[k] = Tune.fromJson(it, Tune()) }
                defaults = d
            } catch (e: Exception) { }
        }
        for (k in Kind.values()) {
            prefs.getString("tune_" + k.name, null)?.let {
                try { stored[k] = Tune.fromJson(JSONObject(it), defaultOf(k)) } catch (e: Exception) { }
            }
        }
        try {
            val a = JSONArray(prefs.getString("user_presets", "[]"))
            for (i in 0 until a.length()) userPresets.add(a.getJSONObject(i))
        } catch (e: Exception) { }
        try {
            val o = JSONObject(prefs.getString("apps", "{}") ?: "{}")
            o.keys().forEach { appChoices[it] = o.getString(it) }
        } catch (e: Exception) { }
        builtinJson = try {
            val a = JSONObject(app.assets.open("presets.json").bufferedReader().use { it.readText() })
                .getJSONArray("presets")
            List(a.length()) { a.getJSONObject(it) }
        } catch (e: Exception) {
            Log.w(Dap.TAG, "presets file missing: " + e.message); emptyList()
        }
    }

    private fun defaultsJson(): JSONObject =
        JSONObject().also { o -> defaults.forEach { (k, t) -> o.put(k.name, t.toJson()) } }

    private fun appsJson(): String =
        JSONObject().also { o -> appChoices.forEach { (k, v) -> o.put(k, v) } }.toString()
}
