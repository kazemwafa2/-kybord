package af.hazaragi.keyboard

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

/** پس‌زمینه‌ی کلید مثل طرح: قاب آبی نئونی، گرادیان سرمه‌ای و گوشه‌های طلایی */
class KeyBg(
    private val top: Int, private val bottom: Int, private val border: Int,
    private val radius: Float, private val stroke: Float, private val ornament: Boolean
) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gold = 0xFFD4A640.toInt()

    override fun draw(c: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val r = RectF(b).apply { inset(stroke / 2, stroke / 2) }
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, b.top.toFloat(), 0f, b.bottom.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        c.drawRoundRect(r, radius, radius, p)
        p.shader = null
        p.style = Paint.Style.STROKE; p.strokeWidth = stroke; p.color = border
        c.drawRoundRect(r, radius, radius, p)
        if (ornament) {
            p.color = gold; p.strokeWidth = stroke * 0.8f; p.strokeCap = Paint.Cap.ROUND
            val len = minOf(b.width(), b.height()) * 0.22f
            val m = stroke * 2.6f
            val l = b.left + m; val rr = b.right - m; val t = b.top + m; val bt = b.bottom - m
            // گوشه‌ی بالا-راست و پایین-چپ (بالا-چپ برای نشانه‌ی حرف پنهان خالی می‌ماند)
            c.drawLine(rr - len, t, rr, t, p); c.drawLine(rr, t, rr, t + len, p)
            c.drawLine(l, bt - len, l, bt, p); c.drawLine(l, bt, l + len, bt, p)
        }
    }

    override fun setAlpha(alpha: Int) { p.alpha = alpha }
    override fun setColorFilter(cf: ColorFilter?) { p.colorFilter = cf }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
