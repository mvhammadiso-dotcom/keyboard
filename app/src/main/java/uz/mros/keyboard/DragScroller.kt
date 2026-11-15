package uz.mros.keyboard

import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.OverScroller
import kotlin.math.abs

/** Barmoq bilan surish + inersiya (fling) — emoji va bufer ro'yxatlari uchun. */
class DragScroller(context: Context, private val view: View, private val horizontal: Boolean) {
    private val scroller = OverScroller(context)
    private var velocity: VelocityTracker? = null
    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val minFling = ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat()

    var pos = 0f
        private set
    var max = 0f
        set(v) {
            field = if (v < 0f) 0f else v
            if (pos > field) pos = field
        }
    var dragging = false
        private set
    /** Bosilganda ro'yxat harakatda edi — bu bosish "tap" hisoblanmaydi */
    var stoppedFling = false
        private set

    private var downC = 0f
    private var lastC = 0f

    private fun coord(e: MotionEvent) = if (horizontal) e.x else e.y

    fun onTouch(e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                stoppedFling = !scroller.isFinished
                scroller.forceFinished(true)
                velocity?.recycle()
                velocity = VelocityTracker.obtain()
                velocity?.addMovement(e)
                downC = coord(e)
                lastC = downC
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(e)
                val c = coord(e)
                if (!dragging && abs(c - downC) > slop) {
                    dragging = true
                    lastC = c
                }
                if (dragging) {
                    pos = (pos - (c - lastC)).coerceIn(0f, max)
                    lastC = c
                    view.invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                val vt = velocity
                if (dragging && vt != null) {
                    vt.addMovement(e)
                    vt.computeCurrentVelocity(1000)
                    val v = -(if (horizontal) vt.xVelocity else vt.yVelocity)
                    if (abs(v) > minFling) {
                        if (horizontal) {
                            scroller.fling(pos.toInt(), 0, v.toInt(), 0, 0, max.toInt(), 0, 0)
                        } else {
                            scroller.fling(0, pos.toInt(), 0, v.toInt(), 0, 0, 0, max.toInt())
                        }
                        view.postInvalidateOnAnimation()
                    }
                }
                velocity?.recycle()
                velocity = null
            }
            MotionEvent.ACTION_CANCEL -> {
                velocity?.recycle()
                velocity = null
                dragging = false
            }
        }
    }

    /** onDraw ichida chaqiriladi. true = hali harakatda. */
    fun update(): Boolean {
        if (scroller.computeScrollOffset()) {
            pos = (if (horizontal) scroller.currX else scroller.currY).toFloat().coerceIn(0f, max)
            return true
        }
        return false
    }

    fun scrollTo(target: Float, animate: Boolean) {
        val t = target.coerceIn(0f, max)
        scroller.forceFinished(true)
        if (animate) {
            val d = (t - pos).toInt()
            if (horizontal) scroller.startScroll(pos.toInt(), 0, d, 0, 320)
            else scroller.startScroll(0, pos.toInt(), 0, d, 320)
            view.postInvalidateOnAnimation()
        } else {
            pos = t
            view.invalidate()
        }
    }

    fun reset() {
        scroller.forceFinished(true)
        pos = 0f
    }
}
