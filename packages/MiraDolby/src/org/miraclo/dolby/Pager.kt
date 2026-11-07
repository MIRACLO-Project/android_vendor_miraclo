// SPDX-FileCopyrightText: MIRACLO
// SPDX-License-Identifier: Apache-2.0
package org.miraclo.dolby

import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Side-by-side pages, one on screen at a time: swipe left or right, or call [show]. Vertical
 * scrolling and the sliders keep their touches (a slider asks its parents not to intercept).
 */
class SwipePager(c: Context) : FrameLayout(c) {
    private val pages = ArrayList<View>()
    var index = 0; private set
    var onPage: (Int) -> Unit = {}
    private val slop = ViewConfiguration.get(c).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var vt: VelocityTracker? = null
    private val ease = PathInterpolator(0.2f, 0f, 0f, 1f)

    fun add(v: View) {
        pages.add(v)
        addView(v, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        layoutPages(0f, false)
    }

    fun show(i: Int, animate: Boolean = true) {
        index = i.coerceIn(0, pages.size - 1)
        layoutPages(0f, animate)
        onPage(index)
    }

    private fun layoutPages(drag: Float, animate: Boolean) {
        val w = if (width > 0) width.toFloat() else resources.displayMetrics.widthPixels.toFloat()
        pages.forEachIndexed { i, v ->
            val x = (i - index) * w + drag
            if (animate) v.animate().translationX(x).setDuration(280).setInterpolator(ease).start()
            else { v.animate().cancel(); v.translationX = x }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        layoutPages(0f, false)
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y; dragging = false
                vt?.recycle(); vt = VelocityTracker.obtain(); vt?.addMovement(e)
            }
            MotionEvent.ACTION_MOVE -> {
                vt?.addMovement(e)
                val dx = e.x - downX
                val dy = e.y - downY
                if (abs(dx) > slop * 2 && abs(dx) > abs(dy) * 2) {
                    dragging = true
                    downX = e.x
                    return true
                }
            }
        }
        return false
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        vt?.addMovement(e)
        val w = width.coerceAtLeast(1).toFloat()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_MOVE -> {
                var d = e.x - downX
                // resist past the first and last page
                if ((index == 0 && d > 0) || (index == pages.size - 1 && d < 0)) d *= 0.25f
                layoutPages(d, false)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val d = e.x - downX
                vt?.computeCurrentVelocity(1000)
                val v = vt?.xVelocity ?: 0f
                vt?.recycle(); vt = null
                dragging = false
                val next = when {
                    (d < -w / 4 || v < -1200f) && index < pages.size - 1 -> index + 1
                    (d > w / 4 || v > 1200f) && index > 0 -> index - 1
                    else -> index
                }
                show(next)
            }
        }
        return true
    }
}
