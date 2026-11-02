// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View

/** 270° arc speedometer ring (blue → cyan). The number itself is drawn by a TextView on top. */
class SpeedGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var speedKmh: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    var maxKmh: Float = 200f
        set(value) {
            field = if (value > 0f) value else 200f
            invalidate()
        }

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = 0xFF17294B.toInt()
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val face = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE60A1426.toInt() }
    private val rect = RectF()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val stroke = w.coerceAtMost(h) * 0.11f
        track.strokeWidth = stroke
        arc.strokeWidth = stroke
        val inset = stroke / 2f + 2f
        rect.set(inset, inset, w - inset, h - inset)
        arc.shader = SweepGradient(
            w / 2f, h / 2f,
            intArrayOf(0xFF006C9E.toInt(), 0xFF38E1FF.toInt(), 0xFF006C9E.toInt()),
            floatArrayOf(0f, 0.75f, 1f),
        )
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawOval(rect, face)
        canvas.drawArc(rect, START_ANGLE, TOTAL_SWEEP, false, track)
        val sweep = sweepFor(speedKmh, maxKmh)
        if (sweep > 0f) {
            canvas.save()
            canvas.rotate(START_ANGLE, width / 2f, height / 2f)
            canvas.drawArc(rect, 0f, sweep, false, arc)
            canvas.restore()
        }
    }

    companion object {
        private const val START_ANGLE = 135f
        private const val TOTAL_SWEEP = 270f

        /** Arc length in degrees (0..270) for [speedKmh] on a gauge topping out at [maxKmh]. */
        fun sweepFor(speedKmh: Float, maxKmh: Float = 200f): Float {
            val max = if (maxKmh > 0f) maxKmh else 200f
            return (speedKmh / max).coerceIn(0f, 1f) * TOTAL_SWEEP
        }
    }
}
