package af.hazaragi.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

interface Header {
    val view: View
    fun setPreview(s: String)
}

/** قاب با نسبت ثابت؛ فرزندان بر اساس مختصات طراحی (dw × dh) جای‌گذاری می‌شوند */
class RatioFrame(ctx: Context, private val dw: Int, private val dh: Int) : FrameLayout(ctx) {
    private val rects = HashMap<View, IntArray>()
    fun addAt(v: View, l: Int, t: Int, r: Int, b: Int) { rects[v] = intArrayOf(l, t, r, b); addView(v) }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec)
        val h = w * dh / dw
        setMeasuredDimension(w, h)
        val sx = w.toFloat() / dw
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            val r = rects[c]
            val cw = if (r == null) w else ((r[2] - r[0]) * sx).toInt()
            val ch = if (r == null) h else ((r[3] - r[1]) * sx).toInt()
            c.measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY))
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val sx = (r - l).toFloat() / dw
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            val q = rects[c]
            if (q == null) c.layout(0, 0, r - l, b - t)
            else c.layout((q[0] * sx).toInt(), (q[1] * sx).toInt(), (q[2] * sx).toInt(), (q[3] * sx).toInt())
        }
    }
}

class ImageHeader(
    ctx: Context, handler: Handler,
    onEmoji: () -> Unit, onBack: () -> Unit, onSend: () -> Unit
) : Header {
    private val frame = RatioFrame(ctx, 1536, 285)
    private val tv = TextView(ctx)
    override val view: View get() = frame

    init {
        frame.addView(ImageView(ctx).apply {
            setImageResource(R.drawable.hk_header); scaleType = ImageView.ScaleType.FIT_XY
        })
        tv.apply {
            textSize = 15f; setTextColor(Color.WHITE); maxLines = 1
            ellipsize = TextUtils.TruncateAt.START
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
        }
        frame.addAt(tv, 150, 219, 650, 272)
        frame.addAt(View(ctx).apply { setOnClickListener { onEmoji() } }, 25, 192, 135, 282)
        frame.addAt(View(ctx).also { attachRepeat(it, handler) { onBack() } }, 1295, 190, 1400, 282)
        frame.addAt(View(ctx).apply { setOnClickListener { onSend() } }, 1410, 186, 1518, 282)
    }
    override fun setPreview(s: String) { tv.text = s }
}

class PlainHeader(
    ctx: Context, t: KbTheme, handler: Handler,
    onEmoji: () -> Unit, onBack: () -> Unit, onSend: () -> Unit
) : Header {
    private val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private val tv = TextView(ctx)
    override val view: View get() = col

    init {
        col.addView(TextView(ctx).apply {
            text = "هزارگی  ·  زبان ما، هویت ما"; textSize = 14f; setTextColor(t.v1)
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(0, ctx.dp(5), 0, ctx.dp(2))
        })
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_LTR
            gravity = Gravity.CENTER_VERTICAL; setPadding(ctx.dp(6), 0, ctx.dp(6), ctx.dp(2))
        }
        fun btn(txt: String) = TextView(ctx).apply {
            text = txt; textSize = 20f; setTextColor(t.text); gravity = Gravity.CENTER
            setPadding(ctx.dp(12), ctx.dp(6), ctx.dp(12), ctx.dp(6))
        }
        row.addView(btn("😊").apply { setOnClickListener { onEmoji() } })
        tv.apply {
            textSize = 15f; setTextColor(t.text); maxLines = 1
            ellipsize = TextUtils.TruncateAt.START
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
        }
        row.addView(tv, LinearLayout.LayoutParams(0, ctx.dp(36), 1f))
        row.addView(btn("⌫").also { attachRepeat(it, handler) { onBack() } })
        row.addView(btn("➤").apply { setOnClickListener { onSend() } })
        col.addView(row)
    }
    override fun setPreview(s: String) { tv.text = s }
}

fun makeFooter(ctx: Context, t: KbTheme, image: Boolean, onPick: (String) -> Unit): View {
    val items = listOf("❤️", "💙", "🏔️", "🌸", "🕌")
    if (image) {
        val f = RatioFrame(ctx, 1536, 106)
        f.addView(ImageView(ctx).apply { setImageResource(R.drawable.hk_footer); scaleType = ImageView.ScaleType.FIT_XY })
        val cx = listOf(1265, 1315, 1367, 1418, 1470)
        items.forEachIndexed { i, e ->
            f.addAt(View(ctx).apply { setOnClickListener { onPick(e) } }, cx[i] - 26, 20, cx[i] + 26, 90)
        }
        return f
    }
    val row = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
        layoutDirection = View.LAYOUT_DIRECTION_LTR; setPadding(0, ctx.dp(2), 0, ctx.dp(4))
    }
    row.addView(TextView(ctx).apply {
        text = "هزارستان؛ سرزمین تاریخ و فرهنگ"; textSize = 11f; setTextColor(t.v1)
        setPadding(0, 0, ctx.dp(12), 0)
    })
    items.forEach { e ->
        row.addView(TextView(ctx).apply {
            text = e; textSize = 20f; setPadding(ctx.dp(8), 0, ctx.dp(8), 0)
            setOnClickListener { onPick(e) }
        })
    }
    return row
}
