package af.hazaragi.keyboard

import android.content.Context
import android.graphics.BitmapFactory
import android.view.Gravity
import android.view.View
import android.widget.*
import java.io.File

private val EMOJI = linkedMapOf(
    "😀" to "😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 🙃 😉 😍 🥰 😘 😗 😋 😛 😜 🤪 😎 🤩 🥳 😏 😒 😞 😔 😟 😕 🙁 😣 😖 😫 😩 🥺 😢 😭 😤 😠 😡 🤬 🤯 😳 🥵 🥶 😱 😨 😰 😥 😓 🤗 🤔 🤭 🤫 😶 😐 😑 😬 🙄 😯 😲 😴 🤤 😪 😵 🤐 🥴 🤢 🤮 🤧 😷 🤒 🤕 🤠 🥸 😈 👻 💀 🤡 😺 😸 😹 😻 🙈 🙉 🙊",
    "👋" to "👋 🤚 🖐️ ✋ 🖖 👌 🤌 🤏 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ 👍 👎 ✊ 👊 🤛 🤜 👏 🙌 👐 🤲 🤝 🙏 ✍️ 💪 👀 👂 👃 👣 🧠",
    "❤️" to "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💟 ♥️ 😍 🥰 😘 💋 💌 💐 🌹 🥀 🌺",
    "👳" to "👳 👳‍♂️ 👳‍♀️ 🧕 👨 👩 🧔 👴 👵 👦 👧 👶 🧑‍🌾 👨‍🌾 👩‍🌾 🧑‍🍳 👘 🥻 👗 👚 👕 🧥 🧣 🧤 🧦 👞 👟 👢 👒 🎩 🧢 👑 💍 📿 👜 👝 🎒 🕶️ 👓 💄 💅",
    "💒" to "💒 👰 👰‍♀️ 🤵 🤵‍♂️ 💍 💐 🌹 💑 💏 👫 👪 🎊 🎉 🥂 🍾 🎁 🎂 🍰 🎈 🎀 🕯️ ✨ 💃 🕺 🎶 🎵 🥁 🎤 🎼 🪘 🕊️ 🤲 🤝 🙏 🧿",
    "🌸" to "🌸 🌹 🌺 🌻 🌼 🌷 🌱 🌿 ☘️ 🍀 🍁 🍂 🍃 🌲 🌳 🌴 🌵 🌾 🌽 🏔️ ⛰️ 🌄 🌅 🌙 ⭐ 🌟 ☀️ ⛅ ☁️ 🌧️ ❄️ ⛄ 🔥 💧 🌊 🐎 🐑 🐐 🐏 🐄 🐂 🐪 🐫 🐕 🐈 🐓 🦅 🕊️ 🐦 🦆 🦉 🦋 🐝 🐞",
    "🍞" to "🍞 🫓 🥖 🥙 🥘 🍲 🍛 🍚 🥟 🍢 🍖 🍗 🥩 🥗 🍎 🍏 🍐 🍊 🍋 🍉 🍇 🍈 🍓 🍒 🍑 🥭 🍅 🥕 🥔 🧅 🧄 🌶️ 🥜 🌰 🍯 🥛 🧈 🧀 🥚 🍳 ☕ 🍵 🫖 🍽️ 🍰 🎂 🍬",
    "🏔️" to "🏔️ ⛰️ 🏞️ 🌄 🌅 🌇 🏕️ ⛺ 🏠 🏡 🏘️ 🏰 🕌 🕋 🛕 ⛲ 🌉 🚗 🚕 🚌 🚐 🛻 🚜 🏍️ 🚲 ✈️ 🛫 🚀 🗺️ 🧭 🌍 🌏 🌎",
    "🪕" to "🪕 🎻 🎸 🥁 🪘 🎵 🎶 🎤 🎧 📚 📖 📜 ✍️ 🖋️ 🏺 🧶 🧵 🪡 🧺 🛖 🏆 🥇 🎓 🕯️ 🧿 📿 🪞 🔔",
    "⭐" to "✅ ❌ ⭐ ✨ 🎉 🎊 🎁 💯 🔔 📌 📍 💡 ⚡ 🔥 💪 👑 🏆 ⏰ 📞 📱 💻 📷 🎥 💬 ✉️ 📦 🔒 🔑 ➡️ ⬅️ ⬆️ ⬇️ ↩️ ♻️ ⚠️ ❗ ❓ ➕ ➖ ✔️ ©️ ®️ ™️",
    "🏳️" to "🇦🇫 🇮🇷 🇵🇰 🇹🇷 🇹🇯 🇺🇿 🇹🇲 🇮🇳 🇸🇦 🇦🇪 🇶🇦 🇰🇼 🇮🇶 🇪🇬 🇺🇸 🇨🇦 🇬🇧 🇩🇪 🇫🇷 🇮🇹 🇪🇸 🇳🇱 🇸🇪 🇳🇴 🇩🇰 🇫🇮 🇦🇺 🇳🇿 🇯🇵 🇰🇷 🇨🇳 🇷🇺 🇧🇷"
)

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
    fun tab(label: String, act: () -> Unit) = TextView(c).apply {
        text = label; textSize = 20f; gravity = Gravity.CENTER; setTextColor(t.text)
        layoutParams = LinearLayout.LayoutParams(c.dp(48), c.dp(44))
        setOnClickListener { act() }
    }
    val tabs = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    tabs.addView(tab("🕘") { show(Prefs.recents(c)) })
    EMOJI.forEach { (icon, list) -> tabs.addView(tab(icon) { show(list.split(" ")) }) }
    val bar = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    bar.addView(TextView(c).apply {
        text = "ابج"; textSize = 15f; gravity = Gravity.CENTER; setTextColor(t.v1)
        layoutParams = LinearLayout.LayoutParams(c.dp(56), c.dp(44)); setOnClickListener { onBack() }
    })
    bar.addView(HorizontalScrollView(c).apply { isHorizontalScrollBarEnabled = false; addView(tabs) },
        LinearLayout.LayoutParams(0, c.dp(44), 1f))
    col.addView(bar)
    col.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
    col.layoutParams = FrameLayout.LayoutParams(-1, heightPx)
    show(EMOJI.values.first().split(" "))
    return col
}

fun stickerPanel(c: Context, t: KbTheme, heightPx: Int, onPick: (File) -> Unit, onBack: () -> Unit, onAdd: () -> Unit): View {
    val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_LTR }
    val all = StickerStore.list(c)
    val grid = GridLayout(c).apply { columnCount = 3 }
    val w = c.resources.displayMetrics.widthPixels / 3

    fun show(cat: String) {
        grid.removeAllViews()
        all.filter { StickerStore.category(it) == cat }.forEach { f ->
            grid.addView(ImageView(c).apply {
                setImageBitmap(BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = 2 }))
                scaleType = ImageView.ScaleType.FIT_CENTER; setPadding(c.dp(6), c.dp(6), c.dp(6), c.dp(6))
                layoutParams = GridLayout.LayoutParams().apply { width = w; height = c.dp(96) }
                setOnClickListener { onPick(f) }
                setOnLongClickListener {
                    if (f.name.startsWith("user_")) { f.delete(); visibility = View.GONE; true } else false  // حذف استیکر شخصی
                }
            })
        }
    }
    val bar = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    fun chip(label: String, act: () -> Unit) = TextView(c).apply {
        text = label; textSize = 14f; gravity = Gravity.CENTER; setTextColor(t.text)
        setPadding(c.dp(12), 0, c.dp(12), 0); layoutParams = LinearLayout.LayoutParams(-2, c.dp(44))
        setOnClickListener { act() }
    }
    bar.addView(chip("ابج") { onBack() }.apply { setTextColor(t.v1) })
    val cats = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
    StickerStore.categories.forEach { (k, name) -> cats.addView(chip(name) { show(k) }) }
    bar.addView(HorizontalScrollView(c).apply { isHorizontalScrollBarEnabled = false; addView(cats) },
        LinearLayout.LayoutParams(0, c.dp(44), 1f))
    bar.addView(chip("＋") { onAdd() }.apply { setTextColor(t.v1) })
    col.addView(bar)
    col.addView(ScrollView(c).apply { addView(grid) }, LinearLayout.LayoutParams(-1, 0, 1f))
    col.layoutParams = FrameLayout.LayoutParams(-1, heightPx)
    show("lebas")
    return col
}
