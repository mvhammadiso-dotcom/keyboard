package uz.mros.keyboard

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

/** Oddiy vektor ikonkalar (o'zimizniki), chiziq qalinligi paint'dan olinadi. */
object Icons {
    private val path = Path()
    private val oval = RectF()

    private inline fun stroke(p: Paint, block: () -> Unit) {
        val old = p.style
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        block()
        p.style = old
    }

    private inline fun fill(p: Paint, block: () -> Unit) {
        val old = p.style
        p.style = Paint.Style.FILL
        block()
        p.style = old
    }

    /** state: 0 o'chiq, 1 bir martalik, 2 qulflangan (CAPS) */
    fun shift(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint, state: Int) {
        path.reset()
        path.moveTo(cx, cy - 0.44f * s)
        path.lineTo(cx + 0.42f * s, cy + 0.02f * s)
        path.lineTo(cx + 0.2f * s, cy + 0.02f * s)
        path.lineTo(cx + 0.2f * s, cy + 0.36f * s)
        path.lineTo(cx - 0.2f * s, cy + 0.36f * s)
        path.lineTo(cx - 0.2f * s, cy + 0.02f * s)
        path.lineTo(cx - 0.42f * s, cy + 0.02f * s)
        path.close()
        if (state == 0) stroke(p) { c.drawPath(path, p) } else fill(p) { c.drawPath(path, p) }
        if (state == 2) stroke(p) { c.drawLine(cx - 0.2f * s, cy + 0.5f * s, cx + 0.2f * s, cy + 0.5f * s, p) }
    }

    fun backspace(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        val ox = cx - 0.02f * s
        path.reset()
        path.moveTo(ox - 0.5f * s, cy)
        path.lineTo(ox - 0.2f * s, cy - 0.34f * s)
        path.lineTo(ox + 0.5f * s, cy - 0.34f * s)
        path.lineTo(ox + 0.5f * s, cy + 0.34f * s)
        path.lineTo(ox - 0.2f * s, cy + 0.34f * s)
        path.close()
        stroke(p) {
            c.drawPath(path, p)
            val x0 = ox + 0.02f * s
            val x1 = ox + 0.3f * s
            c.drawLine(x0, cy - 0.14f * s, x1, cy + 0.14f * s, p)
            c.drawLine(x1, cy - 0.14f * s, x0, cy + 0.14f * s, p)
        }
    }

    fun enter(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        stroke(p) {
            path.reset()
            path.moveTo(cx + 0.4f * s, cy - 0.34f * s)
            path.lineTo(cx + 0.4f * s, cy + 0.1f * s)
            path.lineTo(cx - 0.38f * s, cy + 0.1f * s)
            c.drawPath(path, p)
            path.reset()
            path.moveTo(cx - 0.16f * s, cy - 0.12f * s)
            path.lineTo(cx - 0.38f * s, cy + 0.1f * s)
            path.lineTo(cx - 0.16f * s, cy + 0.32f * s)
            c.drawPath(path, p)
        }
    }

    fun search(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        stroke(p) {
            c.drawCircle(cx - 0.08f * s, cy - 0.08f * s, 0.28f * s, p)
            c.drawLine(cx + 0.13f * s, cy + 0.13f * s, cx + 0.4f * s, cy + 0.4f * s, p)
        }
    }

    fun arrowRight(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        stroke(p) {
            c.drawLine(cx - 0.4f * s, cy, cx + 0.4f * s, cy, p)
            path.reset()
            path.moveTo(cx + 0.12f * s, cy - 0.28f * s)
            path.lineTo(cx + 0.4f * s, cy)
            path.lineTo(cx + 0.12f * s, cy + 0.28f * s)
            c.drawPath(path, p)
        }
    }

    fun arrowUp(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        stroke(p) {
            c.drawLine(cx, cy + 0.4f * s, cx, cy - 0.4f * s, p)
            path.reset()
            path.moveTo(cx - 0.28f * s, cy - 0.12f * s)
            path.lineTo(cx, cy - 0.4f * s)
            path.lineTo(cx + 0.28f * s, cy - 0.12f * s)
            c.drawPath(path, p)
        }
    }

    fun check(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        stroke(p) {
            path.reset()
            path.moveTo(cx - 0.36f * s, cy + 0.02f * s)
            path.lineTo(cx - 0.1f * s, cy + 0.28f * s)
            path.lineTo(cx + 0.38f * s, cy - 0.28f * s)
            c.drawPath(path, p)
        }
    }

    fun globe(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        stroke(p) {
            val r = 0.42f * s
            c.drawCircle(cx, cy, r, p)
            oval.set(cx - 0.18f * s, cy - r, cx + 0.18f * s, cy + r)
            c.drawOval(oval, p)
            c.drawLine(cx - r, cy, cx + r, cy, p)
            val hy = 0.21f * s
            val hx = 0.36f * s
            c.drawLine(cx - hx, cy - hy, cx + hx, cy - hy, p)
            c.drawLine(cx - hx, cy + hy, cx + hx, cy + hy, p)
        }
    }

    fun smiley(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        stroke(p) {
            c.drawCircle(cx, cy, 0.42f * s, p)
            oval.set(cx - 0.24f * s, cy - 0.14f * s, cx + 0.24f * s, cy + 0.24f * s)
            c.drawArc(oval, 25f, 130f, false, p)
        }
        fill(p) {
            c.drawCircle(cx - 0.14f * s, cy - 0.1f * s, 0.055f * s, p)
            c.drawCircle(cx + 0.14f * s, cy - 0.1f * s, 0.055f * s, p)
        }
    }

    fun clipboard(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        stroke(p) {
            oval.set(cx - 0.32f * s, cy - 0.34f * s, cx + 0.32f * s, cy + 0.44f * s)
            c.drawRoundRect(oval, 0.08f * s, 0.08f * s, p)
            oval.set(cx - 0.14f * s, cy - 0.44f * s, cx + 0.14f * s, cy - 0.26f * s)
            c.drawRoundRect(oval, 0.05f * s, 0.05f * s, p)
            c.drawLine(cx - 0.16f * s, cy, cx + 0.16f * s, cy, p)
            c.drawLine(cx - 0.16f * s, cy + 0.18f * s, cx + 0.08f * s, cy + 0.18f * s, p)
        }
    }

    fun gear(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        stroke(p) {
            c.drawCircle(cx, cy, 0.15f * s, p)
            c.drawCircle(cx, cy, 0.32f * s, p)
            for (i in 0 until 8) {
                val a = Math.PI * i / 4.0
                val x0 = cx + (0.32f * s) * cos(a).toFloat()
                val y0 = cy + (0.32f * s) * sin(a).toFloat()
                val x1 = cx + (0.45f * s) * cos(a).toFloat()
                val y1 = cy + (0.45f * s) * sin(a).toFloat()
                c.drawLine(x0, y0, x1, y1, p)
            }
        }
    }

    fun chevronDown(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        stroke(p) {
            path.reset()
            path.moveTo(cx - 0.32f * s, cy - 0.12f * s)
            path.lineTo(cx, cy + 0.18f * s)
            path.lineTo(cx + 0.32f * s, cy - 0.12f * s)
            c.drawPath(path, p)
        }
    }

    fun chevronLeft(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        stroke(p) {
            path.reset()
            path.moveTo(cx + 0.14f * s, cy - 0.34f * s)
            path.lineTo(cx - 0.18f * s, cy)
            path.lineTo(cx + 0.14f * s, cy + 0.34f * s)
            c.drawPath(path, p)
        }
    }

    fun trash(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        stroke(p) {
            c.drawLine(cx - 0.38f * s, cy - 0.28f * s, cx + 0.38f * s, cy - 0.28f * s, p)
            path.reset()
            path.moveTo(cx - 0.12f * s, cy - 0.28f * s)
            path.lineTo(cx - 0.12f * s, cy - 0.4f * s)
            path.lineTo(cx + 0.12f * s, cy - 0.4f * s)
            path.lineTo(cx + 0.12f * s, cy - 0.28f * s)
            c.drawPath(path, p)
            path.reset()
            path.moveTo(cx - 0.29f * s, cy - 0.28f * s)
            path.lineTo(cx - 0.23f * s, cy + 0.42f * s)
            path.lineTo(cx + 0.23f * s, cy + 0.42f * s)
            path.lineTo(cx + 0.29f * s, cy - 0.28f * s)
            c.drawPath(path, p)
        }
    }

    fun close(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        stroke(p) {
            c.drawLine(cx - 0.28f * s, cy - 0.28f * s, cx + 0.28f * s, cy + 0.28f * s, p)
            c.drawLine(cx + 0.28f * s, cy - 0.28f * s, cx - 0.28f * s, cy + 0.28f * s, p)
        }
    }

    /** mode: 0 avto (yarim doira), 1 quyosh, 2 oy */
    fun theme(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint, mode: Int) {
        when (mode) {
            1 -> {
                stroke(p) {
                    c.drawCircle(cx, cy, 0.18f * s, p)
                    for (i in 0 until 8) {
                        val a = Math.PI * i / 4.0
                        c.drawLine(
                            cx + 0.3f * s * cos(a).toFloat(), cy + 0.3f * s * sin(a).toFloat(),
                            cx + 0.43f * s * cos(a).toFloat(), cy + 0.43f * s * sin(a).toFloat(), p
                        )
                    }
                }
            }
            2 -> {
                path.reset()
                path.addCircle(cx, cy, 0.38f * s, Path.Direction.CW)
                val cut = Path()
                cut.addCircle(cx + 0.2f * s, cy - 0.16f * s, 0.32f * s, Path.Direction.CW)
                path.op(cut, Path.Op.DIFFERENCE)
                fill(p) { c.drawPath(path, p) }
            }
            else -> {
                stroke(p) { c.drawCircle(cx, cy, 0.38f * s, p) }
                oval.set(cx - 0.38f * s, cy - 0.38f * s, cx + 0.38f * s, cy + 0.38f * s)
                fill(p) { c.drawArc(oval, 90f, 180f, true, p) }
            }
        }
    }
}
