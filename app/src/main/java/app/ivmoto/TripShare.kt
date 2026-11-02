// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

/** Speed samples of a trip, prepared for the chart. */
object SpeedSeries {
    fun kmh(points: List<TrackPoint>): List<Float> = points.map { (it.speedMs * 3.6f).coerceAtLeast(0f) }

    /** Averages [values] into at most [buckets] points, keeping every sample in exactly one bucket. */
    fun resample(values: List<Float>, buckets: Int): List<Float> {
        if (values.isEmpty() || buckets <= 0) return emptyList()
        if (values.size <= buckets) return values
        return (0 until buckets).map { b ->
            val from = b * values.size / buckets
            val to = (b + 1) * values.size / buckets
            values.subList(from, to).average().toFloat()
        }
    }
}

/** A small speed-over-time line chart. */
class SpeedChartView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var values: List<Float> = emptyList()
        set(v) {
            field = v
            invalidate()
        }

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f * resources.displayMetrics.density
        strokeJoin = Paint.Join.ROUND
        color = ContextCompat.getColor(context, R.color.ivm_cyan)
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grid = Paint().apply {
        strokeWidth = 1f
        color = 0x33FFFFFF
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f * resources.displayMetrics.scaledDensity
        color = ContextCompat.getColor(context, R.color.text_secondary)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val pad = 8f * resources.displayMetrics.density
        for (i in 1..3) canvas.drawLine(0f, h * i / 4f, w, h * i / 4f, grid)
        if (values.size < 2) return
        val max = maxOf(values.max(), 10f)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = pad + (w - 2 * pad) * i / (values.size - 1)
            val y = h - pad - (h - 2 * pad - label.textSize) * (v / max)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val area = Path(path).apply {
            lineTo(w - pad, h)
            lineTo(pad, h)
            close()
        }
        fill.shader = LinearGradient(0f, 0f, 0f, h, 0x5538E1FF, 0x0038E1FF, Shader.TileMode.CLAMP)
        canvas.drawPath(area, fill)
        canvas.drawPath(path, line)
        canvas.drawText("${max.toInt()} km/h", pad, label.textSize, label)
    }
}

/** Draws a shareable picture of a trip: route, distance, time and speeds. */
object TripShareImage {
    fun render(ctx: Context, trip: Trip, title: String, mapShot: Bitmap? = null): Bitmap {
        val w = 1080
        val h = 1350
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = Paint().apply { shader = LinearGradient(0f, 0f, 0f, h.toFloat(), Color.parseColor("#0F3A6B"), Color.parseColor("#070E1C"), Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)

        ContextCompat.getDrawable(ctx, R.drawable.ic_logo)?.apply {
            setBounds(60, 60, 60 + 120, 60 + 120)
            draw(c)
        }
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 64f; typeface = Typeface.DEFAULT_BOLD }
        val muted = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#8CA3C7"); textSize = 36f }
        c.drawText(title, 210f, 120f, white)
        c.drawText(trip.dateText() + " · " + trip.timeRangeText(), 210f, 170f, muted)

        if (mapShot != null) {
            // The real map with the route on it, centre-cropped into a rounded box.
            val box = RectF(60f, 250f, 1020f, 870f)
            val scale = maxOf(box.width() / mapShot.width, box.height() / mapShot.height)
            val sw = mapShot.width * scale
            val sh = mapShot.height * scale
            val save = c.save()
            c.clipPath(Path().apply { addRoundRect(box, 32f, 32f, Path.Direction.CW) })
            c.drawBitmap(mapShot, null, RectF(box.centerX() - sw / 2, box.centerY() - sh / 2, box.centerX() + sw / 2, box.centerY() + sh / 2), Paint(Paint.FILTER_BITMAP_FLAG))
            c.restoreToCount(save)
        } else {
            val route = TripRouteThumb.render(
                trip, 960, 620,
                Color.parseColor("#38E1FF"), Color.parseColor("#14264A"), Color.parseColor("#34D399"), Color.parseColor("#FF5A52"),
            )
            c.drawBitmap(route, 60f, 250f, null)
        }

        val stats = listOf(
            trip.distanceText() to ctx.getString(R.string.td_distance),
            trip.durationText() to ctx.getString(R.string.td_time),
            "${trip.avgKmh} km/h" to ctx.getString(R.string.td_avg),
            "${trip.maxKmh} km/h" to ctx.getString(R.string.td_max),
        )
        val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0F1B33") }
        val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 60f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#8CA3C7"); textSize = 34f; textAlign = Paint.Align.CENTER }
        stats.forEachIndexed { i, (value, name) ->
            val col = i % 2
            val row = i / 2
            val left = 60f + col * 490f
            val top = 910f + row * 190f
            c.drawRoundRect(RectF(left, top, left + 470f, top + 170f), 32f, 32f, card)
            c.drawText(value, left + 235f, top + 90f, big)
            c.drawText(name, left + 235f, top + 140f, small)
        }
        small.textSize = 30f
        c.drawText("IVMoto", w / 2f, h - 40f, small)
        return bmp
    }
}
