// SPDX-FileCopyrightText: MIRACLO
// SPDX-License-Identifier: Apache-2.0

package org.miraclo.dolby

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.window.OnBackInvokedDispatcher

/** One screen of the app. [bind] puts the current settings on screen. */
abstract class Page(val a: MainActivity) {
    val p: Palette get() = a.p
    abstract val view: View
    open fun bind() {}
    open fun closed() {}
}

class MainActivity : Activity() {

    lateinit var p: Palette
        private set
    /** The output the home page shows; follows plugged-in headphones. */
    var viewHeadphones = false
    private lateinit var host: FrameLayout
    private val stack = ArrayList<Page>()
    private var topInset = 0
    private var bottomInset = 0
    private var lastHeadphones = false
    private val update: () -> Unit = {
        if (Dolby.headphones != lastHeadphones) { lastHeadphones = Dolby.headphones; viewHeadphones = lastHeadphones }
        stack.lastOrNull()?.bind()
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.decorView // MIRACLO: the window's views exist before anything uses them
        Dolby.init(this)
        p = Palette.of(this)
        lastHeadphones = Dolby.headphones
        viewHeadphones = lastHeadphones
        window.setDecorFitsSystemWindows(false)
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.TRANSPARENT
        window.isNavigationBarContrastEnforced = false
        val light = if (p.dark) 0 else
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window.decorView.windowInsetsController?.setSystemBarsAppearance(light,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
        host = FrameLayout(this)
        host.setBackgroundColor(p.bg)
        host.setOnApplyWindowInsetsListener { _, ins ->
            val b = ins.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            topInset = b.top; bottomInset = b.bottom
            for (i in 0 until host.childCount) pad(host.getChildAt(i))
            host.setPadding(b.left, 0, b.right, 0)
            ins
        }
        setContentView(host)
        onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
            if (stack.size > 1) back() else finish()
        }
        open(HomePage(this), animate = false)
    }

    override fun onResume() {
        super.onResume()
        Dolby.addListener(update)
        stack.lastOrNull()?.bind()
    }

    override fun onPause() {
        Dolby.removeListener(update)
        super.onPause()
    }

    private fun pad(v: View) {
        val inner = (v as? ScrollView)?.getChildAt(0) ?: v
        inner.setPadding(inner.paddingLeft, topInset + dp(12), inner.paddingRight, bottomInset + dp(24))
    }

    fun open(page: Page, animate: Boolean = true) {
        val old = stack.lastOrNull()?.view
        stack.add(page)
        val v = page.view
        pad(v)
        host.addView(v, FrameLayout.LayoutParams(MATCH, MATCH))
        page.bind()
        if (animate && old != null) {
            v.alpha = 0f; v.translationX = dp(36f)
            v.animate().alpha(1f).translationX(0f).setDuration(260).setInterpolator(EASE).start()
            old.animate().alpha(0f).translationX(-dp(24f)).setDuration(220).setInterpolator(EASE)
                .withEndAction { old.visibility = View.GONE }.start()
        } else old?.visibility = View.GONE
    }

    fun back() {
        if (stack.size < 2) return
        val gone = stack.removeAt(stack.size - 1)
        gone.closed()
        val top = stack.last()
        top.view.visibility = View.VISIBLE
        top.view.alpha = 0f; top.view.translationX = -dp(24f)
        top.view.animate().alpha(1f).translationX(0f).setDuration(260).setInterpolator(EASE).start()
        top.bind()
        gone.view.animate().alpha(0f).translationX(dp(36f)).setDuration(200).setInterpolator(EASE)
            .withEndAction { host.removeView(gone.view) }.start()
    }

    // ---- shared building blocks -------------------------------------------------------------

    /** A page that scrolls: a column with the side margins of the mocks. */
    fun scroller(content: LinearLayout): ScrollView = ScrollView(this).apply {
        isVerticalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        content.setPadding(dp(16), 0, dp(16), 0)
        addView(content, FrameLayout.LayoutParams(MATCH, WRAP))
    }

    /** A back button, a title and an optional line under it. */
    fun header(title: String, sub: String? = null): Pair<LinearLayout, TextView?> {
        val r = row(14)
        r.setPadding(dp(4), dp(6), dp(4), dp(4))
        val back = FrameLayout(this)
        back.background = touch(p, rounded(p.card2, dp(22f)), dp(22f))
        back.contentDescription = "Back"
        back.addView(PathIcon(this, "M19 12H5M11 6l-6 6 6 6", p.fg, 20, 2f),
            FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        back.setOnClickListener { back() }
        r.addView(back, lp(dp(44), dp(44)))
        val names = column(1)
        names.addView(text(title, 24f, p.fg, 600))
        var subView: TextView? = null
        if (sub != null) { subView = text(sub, 12f, p.fg2); names.addView(subView) }
        r.addView(names, lp(0, WRAP, 1f))
        return r to subView
    }

    fun label(s: String): TextView = text(s, 13f, p.link, 600).apply { setPadding(dp(6), dp(6), dp(6), 0) }

    fun card(radius: Float = 24f): LinearLayout = column().apply { background = rounded(p.card, dp(radius)) }

    /** The red "Reset to default" button at the end of a page, with a confirmation. */
    fun resetButton(message: String, reset: () -> Unit): View {
        val b = text("Reset to default", 15f, p.errFg, 600)
        b.gravity = Gravity.CENTER
        b.background = touch(p, rounded(p.errBg, dp(26f)), dp(26f))
        b.minHeight = dp(52)
        b.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reset this page?")
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Reset") { _, _ -> reset() }
                .show()
        }
        return b
    }

    fun openLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { }
    }

    fun gap(h: Int): View = View(this).apply { layoutParams = lp(MATCH, dp(h)) }
}
