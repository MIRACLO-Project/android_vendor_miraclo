// SPDX-FileCopyrightText: MIRACLO
// SPDX-License-Identifier: Apache-2.0

package org.miraclo.dolby

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.roundToInt

private fun tone(v: Float) = (v - Tune.TONE_MIN) / (Tune.TONE_MAX - Tune.TONE_MIN)
private fun toneOf(f: Float) = ((Tune.TONE_MIN + f * (Tune.TONE_MAX - Tune.TONE_MIN)) * 2).roundToInt() / 2f
private fun level(v: Int, words: Array<String>) = words[(v * words.size / 101).coerceIn(0, words.size - 1)]
private val AMOUNT = arrayOf("Low", "Medium", "High")
private val WIDTH = arrayOf("Narrow", "Normal", "Wide", "Widest")

/** Mira Dolby's home page. */
class HomePage(a: MainActivity) : Page(a) {
    private val subtitle = a.text("", 13f, p.fg2)
    private val status = a.text("", 12f, p.fg2)
    private val power = SwitchView(a, p, big = true)
    private val logo = LogoView(a, p, 7f, 98f, true, 0.42f)
    private val presetName = ellipsize(a.text("", 16f, p.fg, 600))
    private val badge = CurveBadge(a, p)
    private val outputs = ArrayList<TextView>()
    private val tabs = ArrayList<TextView>()
    private val cards = HashMap<Kind, Pair<LinearLayout, Pair<PathIcon, TextView>>>()
    private val surround = SliderRow(a, p, "Surround")
    private val dialogue = SliderRow(a, p, "Dialogue")
    private val bass = SliderRow(a, p, "Bass")
    private val pager = SwipePager(a)

    /** Speaker or headphones: shown on both pages, both stay in step. */
    private fun outputSwitch(): LinearLayout {
        val seg = a.row(6)
        seg.setPadding(a.dp(4), a.dp(4), a.dp(4), a.dp(4))
        seg.background = rounded(p.card, a.dp(22f))
        for ((i, name) in listOf("Speaker", "Headphones").withIndex()) {
            val b = a.text(name, 14f, p.fg, 600)
            b.gravity = Gravity.CENTER
            b.setOnClickListener { a.viewHeadphones = i == 1; bind() }
            outputs.add(b)
            seg.addView(b, lp(0, a.dp(38), 1f))
        }
        return seg
    }

    override val view: View = a.column().apply {
        // Title and About, then the two tabs: Sound (presets and profiles) and Tuning.
        val head = a.column(10)
        head.setPadding(a.dp(16), 0, a.dp(16), a.dp(8))
        val top = a.row()
        top.setPadding(a.dp(4), a.dp(6), a.dp(4), 0)
        val names = a.column(2)
        names.addView(a.text("Mira Dolby", 28f, p.fg, 700).apply { letterSpacing = -0.02f })
        names.addView(subtitle)
        top.addView(names, lp(0, WRAP, 1f))
        val about = FrameLayout(a)
        about.background = touch(p, rounded(p.card2, a.dp(22f)), a.dp(22f))
        about.contentDescription = "About Mira Dolby"
        about.addView(PathIcon(a, "M12 3a9 9 0 1 1 0 18a9 9 0 1 1 0-18z M12 11v6 M12 7.5v0.5", p.fg, 20),
            FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        about.setOnClickListener { a.open(AboutPage(a)) }
        top.addView(about, lp(a.dp(44), a.dp(44)))
        head.addView(top)
        val tabRow = a.row(6)
        tabRow.setPadding(a.dp(4), a.dp(4), a.dp(4), a.dp(4))
        tabRow.background = rounded(p.card, a.dp(22f))
        for ((i, name) in listOf("Sound", "Tuning").withIndex()) {
            val t = a.text(name, 14f, p.fg, 600)
            t.gravity = Gravity.CENTER
            t.setOnClickListener { pager.show(i) }
            tabs.add(t)
            tabRow.addView(t, lp(0, a.dp(38), 1f))
        }
        head.addView(tabRow)
        addView(head, lp())

        // Page 1, Sound: Dolby Atmos, the preset, the output and the profiles.
        val sound = a.column(12).apply {
            val hero = FrameLayout(a)
            hero.background = heroBackground(p, a.dp(28f))
            hero.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) = o.setRoundRect(0, 0, v.width, v.height, a.dp(28f))
            }
            hero.clipToOutline = true
            hero.addView(logo, FrameLayout.LayoutParams(MATCH, MATCH))
            val bar = a.row()
            bar.setPadding(a.dp(20), 0, a.dp(20), a.dp(16))
            val t = a.column(2)
            t.addView(a.text("Dolby Atmos", 18f, p.fg, 600))
            t.addView(status)
            bar.addView(t, lp(0, WRAP, 1f))
            bar.addView(power)
            power.contentDescription = "Dolby Atmos"
            power.onChange = { Dolby.setEnabled(it) }
            hero.addView(bar, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM))
            addView(hero, lp(MATCH, a.dp(180)))

            val pc = a.row(14)
            pc.setPadding(a.dp(12), a.dp(12), a.dp(16), a.dp(12))
            pc.background = touch(p, rounded(p.card, a.dp(24f)), a.dp(24f))
            pc.addView(badge)
            val pn = a.column(2)
            pn.addView(a.text("Sound preset", 12f, p.fg2))
            pn.addView(presetName)
            pc.addView(pn, lp(0, WRAP, 1f))
            pc.addView(a.text("Change", 13f, p.link, 600))
            pc.setOnClickListener { a.open(PresetsPage(a)) }
            addView(pc)

            addView(outputSwitch())

            addView(a.label("Profile"))
            val grid = a.column(8)
            Kind.values().toList().chunked(3).forEach { line ->
                val r = a.row(8)
                for (k in line) {
                    val c = a.column(6)
                    c.gravity = Gravity.CENTER
                    val icon = PathIcon(a, k.icon, p.fg2, 20)
                    val name = a.text(k.label, 13f, p.fg, 500)
                    name.gravity = Gravity.CENTER_HORIZONTAL
                    name.textAlignment = View.TEXT_ALIGNMENT_CENTER
                    c.addView(icon, lp(WRAP, WRAP).apply { gravity = Gravity.CENTER_HORIZONTAL })
                    c.addView(name, lp(MATCH, WRAP))
                    c.setOnClickListener { Dolby.selectKind(k) }
                    cards[k] = c to (icon to name)
                    r.addView(c, lp(0, a.dp(72), 1f))
                }
                grid.addView(r)
            }
            addView(grid)
            addView(a.text("Swipe left for surround, dialogue, bass, the equalizer and effects.", 12f, p.fg2).apply {
                gravity = Gravity.CENTER_HORIZONTAL; textAlignment = View.TEXT_ALIGNMENT_CENTER
                setPadding(a.dp(8), a.dp(4), a.dp(8), 0)
            }, lp())
        }

        // Page 2, Tuning: the sliders, the equalizer, effects, per-app, and reset.
        val tuning = a.column(12).apply {
            addView(outputSwitch())
            val sl = a.card(26f)
            sl.setPadding(0, a.dp(6), 0, a.dp(8))
            surround.slider.onChange = { f -> Dolby.edit { it.copy(width = (f * 100).roundToInt()) } }
            surround.onReset { Dolby.edit { it.copy(width = Dolby.baseline().width) } }
            dialogue.slider.onChange = { f ->
                val amount = (f * 100).roundToInt()
                Dolby.edit { it.copy(dialogueAmount = amount, dialogue = amount > 0) }
            }
            dialogue.onReset { val b = Dolby.baseline(); Dolby.edit { it.copy(dialogueAmount = b.dialogueAmount, dialogue = b.dialogue) } }
            bass.slider.steps = ((Tune.TONE_MAX - Tune.TONE_MIN) * 2).toInt()
            bass.slider.onChange = { f -> Dolby.edit { it.copy(bass = toneOf(f)) } }
            bass.onReset { Dolby.edit { it.copy(bass = Dolby.baseline().bass) } }
            sl.addView(surround); sl.addView(dialogue); sl.addView(bass)
            addView(sl)

            val nav = a.card(26f)
            val pages = listOf(
                Triple("Equalizer", "10 bands") { a.open(EqualizerPage(a)) },
                Triple("Effects", "Width, leveller and more") { a.open(EffectsPage(a)) },
                Triple("Apps", "A profile for each app") { a.open(AppsPage(a)) })
            for ((i, pg) in pages.withIndex()) {
                if (i > 0) nav.addView(View(a).apply { setBackgroundColor(p.div) },
                    lp(MATCH, a.dp(1)).apply { marginStart = a.dp(18); marginEnd = a.dp(18) })
                val r = a.row(12)
                r.setPadding(a.dp(18), a.dp(14), a.dp(16), a.dp(14))
                r.background = touch(p, null, 0f)
                val tx = a.column(2)
                tx.addView(a.text(pg.first, 15f, p.fg, 500))
                tx.addView(a.text(pg.second, 12f, p.fg2))
                r.addView(tx, lp(0, WRAP, 1f))
                r.addView(PathIcon(a, "M9 6l6 6-6 6", p.fg2, 18, 2f))
                r.setOnClickListener { pg.third() }
                nav.addView(r, lp())
            }
            nav.clipToOutline = true
            addView(nav)

            addView(a.resetButton("Every Dolby setting, preset and per-app choice goes back to how Mira ships it.") {
                Dolby.resetAll()
            }, lp().apply { topMargin = a.dp(8) })
        }

        pager.add(a.scroller(sound))
        pager.add(a.scroller(tuning))
        sound.setPadding(a.dp(16), a.dp(4), a.dp(16), a.dp(28))
        tuning.setPadding(a.dp(16), a.dp(4), a.dp(16), a.dp(28))
        pager.onPage = { bindTabs() }
        addView(pager, lp(MATCH, 0, 1f))
    }

    private fun bindTabs() {
        tabs.forEachIndexed { i, t ->
            val on = i == pager.index
            t.background = rounded(if (on) p.sel else 0, a.dp(18f))
            t.setTextColor(if (on) p.fg else p.fg2)
        }
    }

    override fun bind() {
        val hp = a.viewHeadphones
        subtitle.text = (if (hp) "Headphones" else "Speaker") + " · " + (Dolby.activePreset?.name ?: Dolby.kind.label)
        power.set(Dolby.enabled, true)
        power.isEnabled = Dolby.available
        status.text = when {
            Dolby.missing -> "Dolby Atmos is not available on this phone"
            !Dolby.available -> "Starting the Dolby engine"
            Dolby.enabled -> "Immersive sound on"
            else -> "Off: sound plays as recorded"
        }
        logo.on = Dolby.enabled
        val pr = Dolby.activePreset
        presetName.text = when {
            pr == null -> "None"
            Dolby.presetEdited -> pr.name + " (edited)"
            else -> pr.name
        }
        badge.values = Dolby.tune().eq
        outputs.forEachIndexed { i, b ->
            val on = (i % 2 == 1) == hp
            b.background = rounded(if (on) p.sel else 0, a.dp(18f))
            b.setTextColor(if (on) p.fg else p.fg2)
        }
        bindTabs()
        for ((k, v) in cards) {
            val on = k == Dolby.kind
            v.first.background = touch(p, rounded(if (on) p.accSoft else p.card, a.dp(20f),
                if (on) p.acc else 0, if (on) a.dp(2) else 0), a.dp(20f))
            v.second.first.color = if (on) p.acc else p.fg2
            v.second.first.invalidate()
            v.second.second.typeface = font(if (on) 600 else 500)
        }
        val t = Dolby.tune(); val b = Dolby.baseline()
        surround.bind(t.width / 100f, level(t.width, WIDTH), t.width != b.width)
        dialogue.bind(if (t.dialogue) t.dialogueAmount / 100f else 0f,
            if (t.dialogue) level(t.dialogueAmount, AMOUNT) else "Off",
            t.dialogueAmount != b.dialogueAmount || t.dialogue != b.dialogue)
        bass.bind(tone(t.bass), Tune.dbText(t.bass), t.bass != b.bass)
    }
}

/** Every preset, as cards with their curves. */
class PresetsPage(a: MainActivity) : Page(a) {
    private val list = a.column(10)
    private var shownKeys = ""

    override val view: View = a.scroller(a.column(12).apply {
        addView(a.header("Presets", "EasyEffects presets by JackHack96").first)
        addView(list)
    })

    override fun bind() {
        val all = Dolby.presets()
        val keys = all.joinToString { it.key } + "|" + Dolby.activePreset?.key
        if (keys == shownKeys) return
        shownKeys = keys
        list.removeAllViews()
        val builtin = all.filter { !it.user }
        val mine = all.filter { it.user }
        list.addView(noneCard())
        builtin.forEach { list.addView(card(it)) }
        if (mine.isNotEmpty()) {
            list.addView(a.label("My presets"))
            mine.forEach { list.addView(card(it)) }
        }
    }

    private fun noneCard(): View {
        val on = Dolby.activePreset == null
        val c = a.row(10)
        c.setPadding(a.dp(16), a.dp(16), a.dp(16), a.dp(16))
        c.background = touch(p, rounded(if (on) p.accSoft else p.card, a.dp(24f),
            if (on) p.acc else 0, if (on) a.dp(2) else 0), a.dp(24f))
        val names = a.column(2)
        names.addView(a.text("None", 16f, p.fg, 600))
        names.addView(a.text("No preset: the profile plays with its own sound", 12f, p.fg2))
        c.addView(names, lp(0, WRAP, 1f))
        val check = FrameLayout(a)
        check.background = rounded(if (on) p.acc else 0, a.dp(13f), if (on) p.acc else p.ringOff, a.dp(2))
        if (on) check.addView(PathIcon(a, "M5 12l5 5 9-10", 0xFFFFFFFF.toInt(), 14, 3f),
            FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        c.addView(check, lp(a.dp(26), a.dp(26)))
        c.setOnClickListener { Dolby.clearPreset() }
        return c
    }

    private fun card(pr: Preset): View {
        val on = Dolby.activePreset?.key == pr.key
        val c = a.column(8)
        c.setPadding(a.dp(16), a.dp(14), a.dp(16), a.dp(12))
        c.background = touch(p, rounded(if (on) p.accSoft else p.card, a.dp(24f),
            if (on) p.acc else 0, if (on) a.dp(2) else 0), a.dp(24f))
        if (on) c.elevation = a.dp(6f)
        val top = a.row(10)
        val names = a.column(2)
        names.addView(a.text(pr.name, 16f, p.fg, 600))
        names.addView(a.text(pr.desc, 12f, p.fg2))
        top.addView(names, lp(0, WRAP, 1f))
        val check = FrameLayout(a)
        check.background = rounded(if (on) p.acc else 0, a.dp(13f), if (on) p.acc else p.ringOff, a.dp(2))
        if (on) check.addView(PathIcon(a, "M5 12l5 5 9-10", 0xFFFFFFFF.toInt(), 14, 3f),
            FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        top.addView(check, lp(a.dp(26), a.dp(26)))
        c.addView(top)
        c.addView(CurveView(a, p, 40, 2.6f, true).apply { values = pr.tune.eq; active = on })
        val chips = FlowLayout(a, 6)
        pr.tags.forEach { chips.addView(chip(it, p.chip, p.chipFg)) }
        if (pr.closeMatch) chips.addView(chip("Close match", p.warn, p.warnFg))
        c.addView(chips)
        c.setOnClickListener {
            val again = on && Dolby.presetEdited
            Dolby.pickPreset(pr)
            if (again) Toast.makeText(a, "Preset values restored", Toast.LENGTH_SHORT).show()
        }
        if (pr.user) c.setOnLongClickListener { manage(pr); true }
        return c
    }

    private fun chip(s: String, bg: Int, fg: Int): TextView = a.text(s, 11f, fg, 600).apply {
        gravity = Gravity.CENTER
        minHeight = a.dp(24)
        setPadding(a.dp(10), 0, a.dp(10), 0)
        background = rounded(bg, a.dp(12f))
    }

    private fun manage(pr: Preset) {
        AlertDialog.Builder(a)
            .setTitle(pr.name)
            .setItems(arrayOf("Rename", "Delete")) { _, which ->
                if (which == 0) askName(a, "Rename preset", pr.name) { Dolby.renameUserPreset(pr, it) }
                else AlertDialog.Builder(a)
                    .setTitle("Delete " + pr.name + "?")
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton("Delete") { _, _ -> Dolby.deleteUserPreset(pr) }
                    .show()
            }
            .show()
    }
}

/** Asks for a preset name. */
fun askName(a: MainActivity, title: String, current: String, done: (String) -> Unit) {
    val e = EditText(a)
    e.setText(current)
    e.setSelection(current.length)
    e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
    e.hint = "Name"
    val box = FrameLayout(a)
    box.setPadding(a.dp(22), a.dp(8), a.dp(22), 0)
    box.addView(e)
    AlertDialog.Builder(a)
        .setTitle(title)
        .setView(box)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton("Save") { _, _ ->
            val n = e.text.toString().trim()
            if (n.isNotEmpty()) done(n.take(40))
        }
        .show()
}

/** The intelligent equalizer and the ten-band graphic equalizer. */
class EqualizerPage(a: MainActivity) : Page(a) {
    private val ieq = FlowLayout(a, 8)
    private val source = a.text("", 12f, p.fg2)
    private val curve = CurveView(a, p, 54, 2f, false)
    private val bands = EqBandsView(a, p)

    override val view: View = a.scroller(a.column(16).apply {
        addView(a.header("Equalizer").first)
        val s = a.column(8)
        s.addView(a.label("Intelligent equalizer"))
        Dolby.ieqNames().forEachIndexed { i, n ->
            val b = a.text(n, 14f, p.fg, 500)
            b.gravity = Gravity.CENTER
            b.minHeight = a.dp(40)
            b.setPadding(a.dp(16), 0, a.dp(16), 0)
            b.setOnClickListener { Dolby.edit { it.copy(ieq = i) } }
            ieq.addView(b)
        }
        s.addView(ieq)
        s.addView(a.text("Dolby adjusts to what is playing, so the sound keeps its character at any volume.",
            12f, p.fg2).apply { setPadding(a.dp(6), 0, a.dp(6), 0) })
        addView(s)

        val c = a.card(26f)
        c.setPadding(a.dp(14), a.dp(16), a.dp(14), a.dp(14))
        val h = a.row()
        h.setPadding(a.dp(4), 0, a.dp(4), a.dp(12))
        h.addView(a.text("Graphic equalizer", 15f, p.fg, 600), lp(0, WRAP, 1f))
        h.addView(source)
        c.addView(h)
        val well = FrameLayout(a)
        well.background = rounded(p.well, a.dp(14f))
        well.clipToOutline = true
        well.addView(curve)
        c.addView(well, lp().apply { bottomMargin = a.dp(12) })
        bands.onBand = { i, db -> Dolby.edit { it.withBand(i, db) }; curve.values = bands.gains }
        c.addView(bands)
        addView(c)

        val save = a.text("Save as my preset", 15f, 0xFFFFFFFF.toInt(), 600)
        save.gravity = Gravity.CENTER
        save.minHeight = a.dp(48)
        save.background = touch(p, rounded(p.acc, a.dp(24f)), a.dp(24f))
        save.setOnClickListener {
            askName(a, "Save as my preset", Dolby.activePreset?.name?.let { "$it (mine)" } ?: "") {
                Dolby.saveUserPreset(it)
                Toast.makeText(a, "Saved in Presets", Toast.LENGTH_SHORT).show()
            }
        }
        addView(save)
        addView(a.resetButton("The equalizer goes back to its default values.") {
            val b = Dolby.baseline()
            Dolby.edit { it.copy(eq = b.eq, ieq = b.ieq) }
        })
    })

    override fun bind() {
        val t = Dolby.tune(); val b = Dolby.baseline()
        for (i in 0 until ieq.childCount) {
            val v = ieq.getChildAt(i) as TextView
            val on = i == t.ieq
            v.background = touch(p, rounded(if (on) p.acc else p.card2, a.dp(20f)), a.dp(20f))
            v.setTextColor(if (on) 0xFFFFFFFF.toInt() else p.fg)
            v.typeface = font(if (on) 600 else 500)
        }
        val pr = Dolby.activePreset
        source.text = when {
            pr != null -> "From preset: " + pr.name
            else -> Dolby.kind.label + " profile"
        }
        bands.set(t.eq, b.eq)
        curve.values = t.eq
    }
}

/** Space, voice and tone. */
class EffectsPage(a: MainActivity) : Page(a) {
    private val virt = SwitchRow(a, p, "Surround virtualiser", sub = "")
    private val stereo = SliderRow(a, p, "Stereo width", "How far apart left and right feel")
    private val de = SwitchRow(a, p, "Dialogue enhancer", sub = "Clearer speech in films and videos")
    private val deAmount = SliderRow(a, p, "Dialogue amount", "How much voices stand out")
    private val lev = SwitchRow(a, p, "Volume leveller", sub = "Keeps quiet and loud parts even")
    private val be = SwitchRow(a, p, "Bass enhancer", sub = "Deeper low end on small speakers")
    private val bass = SliderRow(a, p, "Bass", "Low frequencies")
    private val treble = SliderRow(a, p, "Treble", "Brightness and detail")
    private var sub: TextView? = null

    override val view: View = a.scroller(a.column(12).apply {
        val (h, s) = a.header("Effects", "")
        sub = s
        addView(h)
        group(this, "Space", virt, stereo)
        group(this, "Voice and volume", de, deAmount, lev)
        group(this, "Tone", be, bass, treble)
        addView(a.resetButton("Every effect on this page goes back to its default value.") {
            val b = Dolby.baseline()
            Dolby.edit {
                it.copy(virtSpeaker = b.virtSpeaker, virtHeadphones = b.virtHeadphones, width = b.width,
                    dialogue = b.dialogue, dialogueAmount = b.dialogueAmount, leveller = b.leveller,
                    bassEnhancer = b.bassEnhancer, bass = b.bass, treble = b.treble)
            }
        }, lp().apply { topMargin = a.dp(6) })
    })

    init {
        virt.switch.onChange = { on ->
            Dolby.edit { if (a.viewHeadphones) it.copy(virtHeadphones = on) else it.copy(virtSpeaker = on) }
        }
        stereo.slider.onChange = { f -> Dolby.edit { it.copy(width = (f * 100).roundToInt()) } }
        stereo.onReset { Dolby.edit { it.copy(width = Dolby.baseline().width) } }
        de.switch.onChange = { on -> Dolby.edit { it.copy(dialogue = on) } }
        deAmount.slider.onChange = { f -> Dolby.edit { it.copy(dialogueAmount = (f * 100).roundToInt()) } }
        deAmount.onReset { Dolby.edit { it.copy(dialogueAmount = Dolby.baseline().dialogueAmount) } }
        lev.switch.onChange = { on -> Dolby.edit { it.copy(leveller = on) } }
        be.switch.onChange = { on -> Dolby.edit { it.copy(bassEnhancer = on) } }
        for (r in listOf(bass, treble)) r.slider.steps = ((Tune.TONE_MAX - Tune.TONE_MIN) * 2).toInt()
        bass.slider.onChange = { f -> Dolby.edit { it.copy(bass = toneOf(f)) } }
        bass.onReset { Dolby.edit { it.copy(bass = Dolby.baseline().bass) } }
        treble.slider.onChange = { f -> Dolby.edit { it.copy(treble = toneOf(f)) } }
        treble.onReset { Dolby.edit { it.copy(treble = Dolby.baseline().treble) } }
    }

    private fun group(into: LinearLayout, title: String, vararg rows: View) {
        val g = a.column(6)
        g.addView(a.label(title))
        val c = a.card(24f)
        c.setPadding(0, a.dp(2), 0, a.dp(2))
        rows.forEach { c.addView(it) }
        g.addView(c)
        into.addView(g)
    }

    override fun bind() {
        val t = Dolby.tune(); val b = Dolby.baseline()
        val pr = Dolby.activePreset
        sub?.text = "Saved in the " + Dolby.kind.label + " profile" + (if (pr != null) ", from " + pr.name else "")
        val hp = a.viewHeadphones
        virt.subtitle.text = if (hp) "Room-filling sound on headphones" else "Room-filling sound on the speaker"
        virt.switch.set(if (hp) t.virtHeadphones else t.virtSpeaker, true)
        stereo.bind(t.width / 100f, level(t.width, WIDTH), t.width != b.width)
        de.switch.set(t.dialogue, true)
        deAmount.bind(t.dialogueAmount / 100f, level(t.dialogueAmount, AMOUNT), t.dialogueAmount != b.dialogueAmount)
        lev.switch.set(t.leveller, true)
        be.switch.set(t.bassEnhancer, true)
        bass.bind(tone(t.bass), Tune.dbText(t.bass), abs(t.bass - b.bass) > 0.01f)
        treble.bind(tone(t.treble), Tune.dbText(t.treble), abs(t.treble - b.treble) > 0.01f)
    }
}

/** A profile or preset for each app, switched when the app opens. */
class AppsPage(a: MainActivity) : Page(a) {
    private class App(val pkg: String, val name: String, val icon: Drawable)
    private class RowViews(val sub: TextView, val chip: TextView)

    private val on = SwitchRow(a, p, "Switch by app", sub = "The profile and preset change when the app opens")
    private val list = a.column()
    private val rows = HashMap<String, RowViews>()
    private var apps: List<App> = emptyList()

    override val view: View = a.scroller(a.column(14).apply {
        addView(a.header("Per-app profiles").first)
        val c = a.card(24f)
        c.addView(on)
        addView(c)
        addView(a.label("Your apps"))
        list.background = rounded(p.card, a.dp(24f))
        list.setPadding(0, a.dp(4), 0, a.dp(4))
        list.addView(a.text("Loading your apps", 13f, p.fg2).apply { setPadding(a.dp(18), a.dp(14), a.dp(18), a.dp(14)) })
        addView(list)
        addView(a.text("Apps you have not set use the profile chosen on the home page.", 12f, p.fg2)
            .apply { setPadding(a.dp(6), 0, a.dp(6), 0) })
        addView(a.resetButton("Every app goes back to the home page settings.") { Dolby.clearApps() },
            lp().apply { topMargin = a.dp(6) })
    })

    init {
        on.switch.onChange = { Dolby.setAppsOn(it) }
        val pm = a.packageManager
        val ui = Handler(Looper.getMainLooper())
        Thread {
            val found = pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL)
                .map { it.activityInfo }
                .filter { it.packageName != a.packageName }
                .distinctBy { it.packageName }
                .map { App(it.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm)) }
                .sortedBy { it.name.lowercase() }
            ui.post { apps = found; fill(); bind() }
        }.start()
    }

    private fun fill() {
        list.removeAllViews()
        rows.clear()
        for (app in apps) {
            val r = a.row(14)
            r.setPadding(a.dp(18), a.dp(10), a.dp(18), a.dp(10))
            r.background = touch(p, null, a.dp(24f))
            r.addView(ImageView(a).apply { setImageDrawable(app.icon) }, lp(a.dp(40), a.dp(40)))
            val names = a.column(2)
            names.addView(ellipsize(a.text(app.name, 15f, p.fg)))
            val sub = ellipsize(a.text("", 12f, p.fg2))
            names.addView(sub)
            r.addView(names, lp(0, WRAP, 1f))
            val chip = a.text("", 13f, p.chipFg, 600)
            chip.gravity = Gravity.CENTER
            chip.minHeight = a.dp(32)
            chip.setPadding(a.dp(12), 0, a.dp(12), 0)
            r.addView(chip)
            r.setOnClickListener { choose(app) }
            rows[app.pkg] = RowViews(sub, chip)
            list.addView(r)
        }
    }

    private fun choose(app: App) {
        val labels = ArrayList<String>()
        val values = ArrayList<String?>()
        labels.add("Home page settings"); values.add(null)
        Kind.values().forEach { labels.add(it.label + " profile"); values.add("kind:" + it.name) }
        Dolby.presets().forEach { labels.add(it.name + " preset"); values.add(it.key) }
        val cur = values.indexOf(Dolby.appChoice(app.pkg)).coerceAtLeast(0)
        AlertDialog.Builder(a)
            .setTitle(app.name)
            .setSingleChoiceItems(labels.toTypedArray(), cur) { d, i -> Dolby.setApp(app.pkg, values[i]); d.dismiss() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun bind() {
        on.switch.set(Dolby.appsOn, true)
        list.alpha = if (Dolby.appsOn) 1f else 0.45f
        for ((pkg, v) in rows) {
            val c = Dolby.appChoice(pkg)
            val preset = if (c != null && !c.startsWith("kind:")) Dolby.preset(c) else null
            val kind = when {
                c == null -> null
                c.startsWith("kind:") -> Kind.values().firstOrNull { it.name == c.removePrefix("kind:") }
                else -> Kind.CUSTOM
            }
            v.sub.text = when {
                preset != null -> preset.name + " preset"
                kind != null -> "No preset"
                else -> "Home page settings"
            }
            v.chip.text = kind?.label ?: "Default"
            v.chip.background = rounded(if (kind != null) p.accSoft else p.chip, a.dp(16f))
            v.chip.setTextColor(if (kind != null) p.link else p.fg2)
        }
    }
}

/** About Mira Dolby, and its credits. */
class AboutPage(a: MainActivity) : Page(a) {
    override val view: View = a.scroller(a.column(14).apply {
        addView(a.header("About").first)
        val top = a.column(10)
        top.gravity = Gravity.CENTER_HORIZONTAL
        top.setPadding(0, a.dp(18), 0, a.dp(8))
        top.addView(LogoView(a, p, 9f, 96f, false), lp(a.dp(96), a.dp(96)))
        top.addView(a.text("Mira Dolby", 24f, p.fg, 700).apply { letterSpacing = -0.02f })
        val ver = try { a.packageManager.getPackageInfo(a.packageName, 0).versionName } catch (e: Exception) { null }
        top.addView(a.text("Version " + (ver ?: "1.0"), 13f, p.fg2))
        addView(top)

        val d = a.card(24f)
        d.setPadding(a.dp(18), a.dp(16), a.dp(18), a.dp(16))
        d.addView(a.text("Mira Dolby controls the Dolby Atmos sound engine built into your phone, and adds the presets below on top of it.",
            14f, p.fg))
        addView(d)

        addView(a.label("Credits"))
        val c = a.card(24f)
        c.clipToOutline = true
        c.addView(item("Presets",
            "EasyEffects Presets by JackHack96, building on work by Ziyad Nazem, Digitalone1 and susuthapa19961227. MIT licence.",
            "github.com/JackHack96/EasyEffects-Presets") { a.openLink("https://github.com/JackHack96/EasyEffects-Presets") })
        c.addView(divider())
        c.addView(item("EasyEffects", "The audio effects app by wwmm the presets were made for", null) {
            a.openLink("https://github.com/wwmm/easyeffects")
        })
        c.addView(divider())
        c.addView(item("Open-source licences", "The full text of every licence", null) { a.open(LicencesPage(a)) })
        addView(c)

        addView(a.text("Dolby, Dolby Atmos and the double-D symbol are trademarks of Dolby Laboratories Licensing Corporation. Mira Dolby is not made by Dolby.",
            11f, p.fg2).apply { setPadding(a.dp(6), a.dp(4), a.dp(6), 0) })
    })

    private fun divider(): View = View(a).apply { setBackgroundColor(p.div); layoutParams = lp(MATCH, a.dp(1)) }

    private fun item(title: String, sub: String, link: String?, click: () -> Unit): View {
        val r = a.column(3)
        r.setPadding(a.dp(18), a.dp(14), a.dp(18), a.dp(14))
        r.background = touch(p, null, 0f)
        r.addView(a.text(title, 15f, p.fg))
        r.addView(a.text(sub, 12f, p.fg2))
        if (link != null) r.addView(a.text(link, 12f, p.link).apply { setPadding(0, a.dp(2), 0, 0) })
        r.setOnClickListener { click() }
        return r
    }
}

/** The licence texts. */
class LicencesPage(a: MainActivity) : Page(a) {
    override val view: View = a.scroller(a.column(14).apply {
        addView(a.header("Open-source licences").first)
        val mit = try { a.assets.open("LICENSE-EasyEffects-Presets.txt").bufferedReader().use { it.readText() } }
            catch (e: Exception) { "" }
        addView(block("EasyEffects Presets", "MIT License", mit.trim()))
        addView(block("Mira Dolby", "Apache License 2.0",
            "Licensed under the Apache License, Version 2.0. You may obtain a copy of the licence at " +
                "https://www.apache.org/licenses/LICENSE-2.0"))
    })

    private fun block(title: String, kind: String, body: String): View {
        val c = a.card(24f)
        c.setPadding(a.dp(18), a.dp(16), a.dp(18), a.dp(16))
        c.addView(a.text(title, 15f, p.fg, 600))
        c.addView(a.text(kind, 12f, p.link, 600).apply { setPadding(0, a.dp(2), 0, a.dp(10)) })
        c.addView(a.text(body, 12f, p.fg2).apply { setTextIsSelectable(true) })
        return c
    }
}
