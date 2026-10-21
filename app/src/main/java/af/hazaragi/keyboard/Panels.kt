package af.hazaragi.keyboard

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import java.io.File

private val EMOJI = linkedMapOf(
    "😀" to "😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 🙃 😉 😍 🥰 😘 😗 😋 😛 😜 🤪 😎 🤩 🥳 😏 😒 😞 😔 😟 😕 🙁 😣 😖 😫 😩 🥺 😢 😭 😤 😠 😡 🤬 🤯 😳 🥵 🥶 😱 😨 😰 😥 😓 🤗 🤔 🤭 🤫 😶 😐 😑 😬 🙄 😯 😲 😴 🤤 😪 😵 🤐 🥴 🤢 🤮 🤧 😷 🤒 🤕 🙏 👍 👎 👏 🙌 👋 🤝 ✌️ 🤞 👌",
    "❤️" to "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💟 ♥️ 😍 🥰 😘 💋 💐 🌹",
    "🌸" to "🌸 🌹 🌺 🌻 🌼 🌷 🌱 🌿 ☘️ 🍀 🍁 🍂 🍃 🌲 🌳 🌴 🌵 🏔️ ⛰️ 🌄 🌅 🌙 ⭐ 🌟 ☀️ ⛅ ☁️ 🌧️ ❄️ ⛄ 🔥 💧 🌊 🐎 🐑 🐐 🐄 🐕 🐈 🦅 🕊️ 🐦 🦋 🐝",
    "🍞" to "🍞 🥖 🥙 🍲 🍛 🍚 🍢 🍖 🍗 🥩 🥗 🍎 🍏 🍇 🍉 🍊 🍋 🍓 🍒 🥭 🍑 🥜 🌰 🍯 🥛 ☕ 🍵 🫖 🍽️ 🍰 🎂 🍬",
    "🕌" to "🕌 🏔️ 🏠 🏡 🏰 🕋 🚗 🚌 ✈️ 🚀 🗺️ 🧭 ⛺ 🏕️ 🌍 🌏 🎵 🎶 🎸 🎻 🥁 🎤 🎧 📚 ✍️ 📖 🎓",
    "⭐" to "✅ ❌ ⭐ ✨ 🎉 🎊 🎁 💯 🔔 📌 💡 ⚡ 🤲 🙏 💪 👑 🏆 🥇 ⏰ 📞 💬 📷 🔒 🔑 ➡️ ⬅️ ⬆️ ⬇️"
)

private fun box(c: Context, t: KbTheme, radius: Int = 10) = GradientDrawable().apply {
    cornerRadius = c.dp(radius).toFloat(); setColor(t.keyBottom); setStroke(c.dp(1), t.border)
}

fun emojiPanel(c: Context, t: KbTheme, heightPx: Int, onPick: (String) -> Unit, onBack: () -> Unit): View {
    val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_LTR }
    val grid = GridLayout(c).apply { columnCount = 8 }
    val scroll = ScrollView(c).apply { addView(grid) }

    fun show(items: List<String>) {
        grid.removeAllViews()
        items.forEach { e ->
            grid.addView(TextView(c).apply {
                text = e; textSize = 26f; gravity = Gravity.CENTER
                layoutParams = GridLayout.LayoutParams().apply {
                    width = c.resources.displayMetrics.widthPixels / 8; height = c.dp(48)
                }
                setOnClickListener { Prefs.addRecent(c, e); onPick(e) }
            })
        }
    }
    val tabs = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    fun tab(label: String, weight: Float, act: () -> Unit) = TextView(c).apply {
        text = label; textSize = 20f; gravity = Gravity.CENTER; setTextColor(t.text)
        layoutParams = LinearLayout.LayoutParams(0, c.dp(44), weight)
        setOnClickListener { act() }
    }
    tabs.addView(tab("ابج", 1.2f, onBack).apply { textSize = 15f; setTextColor(t.v1) })
    tabs.addView(tab("🕘", 1f) { show(Prefs.recents(c)) })
    EMOJI.forEach { (icon, list) -> tabs.addView(tab(icon, 1f) { show(list.split(" ")) }) }
    col.addView(tabs)
    col.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
    col.layoutParams = FrameLayout.LayoutParams(-1, heightPx)
    show(EMOJI.values.first().split(" "))
    return col
}

fun stickerPanel(c: Context, t: KbTheme, heightPx: Int, onPick: (File) -> Unit, onBack: () -> Unit, onAdd: () -> Unit): View {
    val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_LTR }
    val bar = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    fun tab(label: String, act: () -> Unit) = TextView(c).apply {
        text = label; textSize = 15f; gravity = Gravity.CENTER; setTextColor(t.v1)
        layoutParams = LinearLayout.LayoutParams(0, c.dp(44), 1f); setOnClickListener { act() }
    }
    bar.addView(tab("ابج", onBack))
    bar.addView(TextView(c).apply {
        text = "استیکرهای هزارگی"; textSize = 14f; gravity = Gravity.CENTER; setTextColor(t.text)
        layoutParams = LinearLayout.LayoutParams(0, c.dp(44), 3f)
    })
    bar.addView(tab("＋ افزودن", onAdd))
    col.addView(bar)
    val grid = GridLayout(c).apply { columnCount = 3 }
    val w = c.resources.displayMetrics.widthPixels / 3
    StickerStore.list(c).forEach { f ->
        grid.addView(ImageView(c).apply {
            setImageBitmap(BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = 2 }))
            scaleType = ImageView.ScaleType.FIT_CENTER; setPadding(c.dp(6), c.dp(6), c.dp(6), c.dp(6))
            layoutParams = GridLayout.LayoutParams().apply { width = w; height = c.dp(92) }
            setOnClickListener { onPick(f) }
            setOnLongClickListener {
                if (f.name.startsWith("user_")) { f.delete(); visibility = View.GONE; true } else false  // حذف استیکرهای شخصی
            }
        })
    }
    col.addView(ScrollView(c).apply { addView(grid) }, LinearLayout.LayoutParams(-1, 0, 1f))
    col.layoutParams = FrameLayout.LayoutParams(-1, heightPx)
    return col
}
