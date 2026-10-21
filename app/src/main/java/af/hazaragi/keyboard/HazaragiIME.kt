package af.hazaragi.keyboard

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.InputType
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.text.TextUtils
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.widget.TextViewCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import java.io.File

class HazaragiIME : InputMethodService() {

    private enum class Mode { KEYS, SYM, EMOJI, STICKER, CLIP }

    private val handler = Handler(Looper.getMainLooper())
    private val langs = listOf("hazaragi", "fa", "en", "ar")
    private val marks = arrayOf("", "\u064E", "\u0650", "\u064F")   // بدون، فتحه، کسره، ضمه
    private var lang = "hazaragi"            // زبان پیش‌فرض: هزارگی
    private var mode = Mode.KEYS
    private var shift = false
    private var caps = false
    private var lastShiftTap = 0L
    private var harakat = 0

    private var root: LinearLayout? = null
    private var content: FrameLayout? = null
    private var header: Header? = null

    private var symPage = 0

    // ---- کلیپ‌بورد: متن/لینک کپی‌شده هنگام تایپ بالای کیبورد می‌آید (فقط در حافظه، ذخیره نمی‌شود)
    private val clipHistory = ArrayList<String>()
    private var lastPasted: String? = null
    private var clipBar: LinearLayout? = null
    private var clipLabel: TextView? = null
    private var cm: ClipboardManager? = null
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        lastPasted = null; readClip(); refreshClipBar()
    }

    override fun onCreate() {
        super.onCreate()
        cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        try { cm?.addPrimaryClipChangedListener(clipListener) } catch (_: Exception) {}
    }

    override fun onDestroy() {
        try { cm?.removePrimaryClipChangedListener(clipListener) } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun readClip() {
        val text = try {
            cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        } catch (_: Exception) { null }
        if (text.isNullOrBlank() || text.length > 20000) return
        if (clipHistory.firstOrNull() == text) return
        clipHistory.remove(text)
        clipHistory.add(0, text)
        while (clipHistory.size > 15) clipHistory.removeAt(clipHistory.size - 1)
    }

    private fun isSecureField(): Boolean {
        val it = currentInputEditorInfo?.inputType ?: 0
        val v = it and InputType.TYPE_MASK_VARIATION
        val cls = it and InputType.TYPE_MASK_CLASS
        return (cls == InputType.TYPE_CLASS_TEXT && (v == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            v == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD || v == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)) ||
            (cls == InputType.TYPE_CLASS_NUMBER && v == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
    }

    private fun refreshClipBar() {
        val bar = clipBar ?: return
        val cur = clipHistory.firstOrNull()
        val show = cur != null && cur != lastPasted && !isSecureField() && mode != Mode.CLIP
        bar.visibility = if (show) View.VISIBLE else View.GONE
        if (show && cur != null) {
            val link = cur.startsWith("http", true) || cur.startsWith("www.", true)
            clipLabel?.text = (if (link) "🔗  " else "📋  ") + cur.replace("\n", " ")
        }
    }

    private fun makeClipBar(t: KbTheme): View {
        val b = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = keyBg(t); setPadding(dp(10), 0, dp(4), 0); visibility = View.GONE
        }
        val lb = TextView(this).apply {
            textSize = 14f; setTextColor(t.text); maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        clipLabel = lb
        b.addView(lb, LinearLayout.LayoutParams(0, -2, 1f))
        b.addView(TextView(this).apply {
            text = "✕"; textSize = 16f; setTextColor(t.v1); setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener { lastPasted = clipHistory.firstOrNull(); refreshClipBar() }
        })
        // یک ضربه روی نوار = چسباندن
        b.setOnClickListener { clipHistory.firstOrNull()?.let { commit(it); lastPasted = it; refreshClipBar() } }
        clipBar = b
        return b
    }

    private fun clipPanel(t: KbTheme, h: Int): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(6), dp(4), dp(6), dp(4)); layoutParams = FrameLayout.LayoutParams(-1, h)
        }
        fun chip(txt: String, act: () -> Unit) = TextView(this).apply {
            text = txt; textSize = 14f; setTextColor(t.v1); setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { act() }
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(chip("ابج") { setMode(Mode.KEYS); refreshClipBar() })
        top.addView(TextView(this).apply {
            text = "کلیپ‌بورد"; textSize = 15f; setTextColor(t.text); gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(chip("پاک‌کردن") { clipHistory.clear(); lastPasted = null; fillContent() })
        col.addView(top)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (clipHistory.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "چیزی کپی نشده است"; textSize = 14f; setTextColor(t.text); gravity = Gravity.CENTER; setPadding(0, dp(24), 0, 0)
            })
        }
        clipHistory.forEach { item ->
            list.addView(TextView(this).apply {
                text = item; maxLines = 2; ellipsize = TextUtils.TruncateAt.END; textSize = 14f; setTextColor(t.text)
                background = keyBg(t); setPadding(dp(10), dp(8), dp(10), dp(8))
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(2), 0, dp(2)) }
                setOnClickListener { commit(item); lastPasted = item; setMode(Mode.KEYS); refreshClipBar() }
            })
        }
        col.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
        return col
    }
    private fun rowH() = dp(Prefs.height(this))
    private fun keysHeight() = rowH() * 48 / 10 + dp(8)

    override fun onEvaluateFullscreenMode() = false

    override fun onCreateInputView(): View {
        val r = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root = r
        rebuild()
        return r
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        readClip()
        if (!restarting) { lang = "hazaragi"; mode = Mode.KEYS; shift = false; caps = false; harakat = 0 }
        if (root != null) rebuild()
    }

    override fun onUpdateSelection(a: Int, b: Int, c: Int, d: Int, e: Int, f: Int) {
        super.onUpdateSelection(a, b, c, d, e, f)
        updatePreview()
    }

    // ---------------------------------------------------------------- ساخت رابط
    private fun rebuild() {
        val r = root ?: return
        val t = Themes.current(this)
        val portrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        val img = t.useImages && Prefs.headerImage(this) && portrait
        r.removeAllViews()
        r.setBackgroundColor(t.bg)
        val h: Header = if (img) ImageHeader(this, handler, { setMode(Mode.EMOJI) }, { backspace() }, { enter() })
        else PlainHeader(this, t, handler, { setMode(Mode.EMOJI) }, { backspace() }, { enter() })
        header = h
        r.addView(h.view, LinearLayout.LayoutParams(-1, -2))
        r.addView(makeClipBar(t), LinearLayout.LayoutParams(-1, dp(36)).apply { setMargins(dp(6), dp(3), dp(6), dp(1)) })
        val c = FrameLayout(this)
        content = c
        r.addView(c, LinearLayout.LayoutParams(-1, -2))
        fillContent()
        refreshClipBar()
        r.addView(langBar(t), LinearLayout.LayoutParams(-1, dp(46)))
        if (portrait) r.addView(makeFooter(this, t, img) { commit(it) }, LinearLayout.LayoutParams(-1, -2))
        updatePreview()
    }

    private fun setMode(m: Mode) { mode = m; fillContent(); refreshClipBar() }

    private fun fillContent() {
        val c = content ?: return
        val t = Themes.current(this)
        val panelH = keysHeight()
        c.removeAllViews()
        when (mode) {
            Mode.KEYS, Mode.SYM -> c.addView(buildKeys(t))
            Mode.CLIP -> c.addView(clipPanel(t, panelH))
            Mode.EMOJI -> c.addView(emojiPanel(this, t, panelH, { commit(it) }, { setMode(Mode.KEYS) }))
            Mode.STICKER -> c.addView(stickerPanel(this, t, panelH, { sendSticker(it) }, { setMode(Mode.KEYS) }, { openSettings() }))
        }
    }

    private fun keyBg(t: KbTheme): android.graphics.drawable.Drawable {
        val fancy = t.useImages || t.id == "gold"
        return KeyBg(t.keyTop, t.keyBottom, t.border, dp(11).toFloat(), dp(if (t.useImages) 2 else 1).toFloat(), fancy)
    }

    private fun cell(w: Float, t: KbTheme) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(0, -1, w).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
        background = keyBg(t)
    }

    private fun buildKeys(t: KbTheme): View {
        val id = if (mode == Mode.SYM) (if (symPage == 0) "sym" else "sym2") else lang
        val lay = Layouts.get(this, id)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(3), dp(4), dp(3), dp(4))
        }
        lay.rows.forEachIndexed { i, row ->
            val isNum = i == 0
            val last = i == lay.rows.size - 1
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_LTR   // ترتیب کلیدها در فایل JSON همان ترتیب دیداری چپ‌به‌راست است
            }
            // مثل کیبورد سامسونگ: شیفت سمت چپ، حذف سمت راست
            if (last && lay.side) r.addView(leftKey(t, lay))
            row.forEach { r.addView(letterKey(it, t, lay, isNum)) }
            if (last && lay.side) r.addView(backKey(t))
            col.addView(r, LinearLayout.LayoutParams(-1, if (isNum) rowH() * 8 / 10 else rowH()))
        }
        col.addView(bottomRow(t, lay), LinearLayout.LayoutParams(-1, rowH()))
        return col
    }

    private fun backKey(t: KbTheme): View {
        val bk = cell(1.4f, t)
        bk.addView(TextView(this).apply { text = "⌫"; textSize = 22f; setTextColor(t.text); gravity = Gravity.CENTER })
        attachRepeat(bk, handler) { backspace() }
        return bk
    }

    /** کلید چپ ردیف سوم: شیفت / حالت حرکت‌گذاری / صفحه‌ی نشانه‌ها */
    private fun leftKey(t: KbTheme, lay: Layout): View {
        val rtlKeys = lay.rtl && mode == Mode.KEYS
        val sh = cell(1.4f, t)
        val tv = TextView(this).apply { textSize = 22f; gravity = Gravity.CENTER; setTextColor(t.border) }
        fun paint() {
            when {
                mode == Mode.SYM -> { tv.text = if (symPage == 0) "۱/۲" else "۲/۲"; tv.textSize = 15f; tv.setTextColor(t.v1) }
                rtlKeys -> {
                    tv.text = if (harakat == 0) "⇧" else "⇧\nـ" + marks[harakat]
                    tv.textSize = if (harakat == 0) 22f else 15f
                    tv.setTextColor(if (harakat == 0) t.border else intArrayOf(0, t.v1, t.v2, t.v3)[harakat])
                }
                else -> { tv.text = if (caps) "⇪" else "⇧"; tv.setTextColor(if (caps) t.v2 else if (shift) t.v1 else t.border) }
            }
        }
        paint(); sh.addView(tv)
        sh.setOnClickListener {
            feedback(it)
            when {
                mode == Mode.SYM -> { symPage = 1 - symPage; fillContent() }
                rtlKeys -> { harakat = (harakat + 1) % 4; paint() }
                else -> {
                    val now = android.os.SystemClock.uptimeMillis()
                    if (shift && !caps && now - lastShiftTap < 400) caps = true
                    else if (shift) { shift = false; caps = false }
                    else { shift = true; caps = false }
                    lastShiftTap = now
                    fillContent()
                }
            }
        }
        return sh
    }

    private fun bottomRow(t: KbTheme, lay: Layout): View {
        val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_LTR }
        val rtlKeys = lay.rtl && mode == Mode.KEYS

        val md = cell(1.4f, t)
        md.addView(TextView(this).apply {
            text = if (mode == Mode.SYM) "ابج" else "!#۱"; textSize = 15f; setTextColor(t.v1); gravity = Gravity.CENTER
        })
        md.setOnClickListener {
            feedback(it)
            if (mode == Mode.SYM) mode = Mode.KEYS else { mode = Mode.SYM; symPage = 0 }
            fillContent()
        }
        r.addView(md)

        val gl = cell(1.0f, t)
        gl.addView(TextView(this).apply { text = "🌐"; textSize = 20f; gravity = Gravity.CENTER })
        gl.setOnClickListener {
            feedback(it)
            val order = listOf("hazaragi", "fa", "en", "ar")
            lang = order[(order.indexOf(lang) + 1) % order.size]; mode = Mode.KEYS; shift = false; caps = false; harakat = 0
            rebuild()
        }
        r.addView(gl)

        val cm = cell(0.9f, t)
        val comma = if (lay.rtl) "،" else ","
        cm.addView(TextView(this).apply { text = comma; textSize = 22f; setTextColor(t.text); gravity = Gravity.CENTER })
        cm.setOnClickListener { feedback(it); commit(comma) }
        r.addView(cm)

        val sp = cell(if (rtlKeys) 3.4f else 4.5f, t)
        if (t.useImages) sp.setBackgroundResource(R.drawable.hk_space)
        else sp.addView(TextView(this).apply { text = lay.name; textSize = 14f; setTextColor(t.text); gravity = Gravity.CENTER })
        sp.setOnClickListener { feedback(it); commit(" ") }
        r.addView(sp)

        // کلید کشیده‌نویسی (تطویل): با نگه‌داشتن، «ـ» پشت‌سرهم اضافه می‌شود → سـ‌ـ‌ـلام
        if (rtlKeys) {
            val ks = cell(1.1f, t)
            ks.addView(TextView(this).apply { text = "ـــ"; textSize = 22f; setTextColor(t.v1); gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD })
            ks.addView(TextView(this).apply { text = "کشیده"; textSize = 9f; setTextColor(t.text); gravity = Gravity.CENTER })
            attachRepeat(ks, handler) { commit("\u0640") }
            r.addView(ks)
        }

        val pd = cell(0.9f, t)
        pd.addView(TextView(this).apply { text = "."; textSize = 24f; setTextColor(t.text); gravity = Gravity.CENTER })
        pd.setOnClickListener { feedback(it); commit(".") }
        r.addView(pd)

        val en = cell(1.4f, t)
        en.addView(TextView(this).apply { text = "↵"; textSize = 24f; setTextColor(t.v2); gravity = Gravity.CENTER })
        en.setOnClickListener { feedback(it); enter() }
        r.addView(en)
        return r
    }

    private fun letterKey(k: KeyDef, t: KbTheme, lay: Layout, isNum: Boolean): View {
        val w = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
            background = keyBg(t)
        }
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        w.addView(c, FrameLayout.LayoutParams(-1, -1))
        val label = if (shift && !lay.rtl) k.label.uppercase(java.util.Locale.ENGLISH) else k.label
        c.addView(TextView(this).apply {
            text = label; textSize = if (isNum) 19f else 21f; setTextColor(t.text); gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        })
        if (k.variants.isNotEmpty() && !isNum) {
            val cols = intArrayOf(t.v1, t.v2, t.v3)
            val sb = SpannableStringBuilder()
            k.variants.forEachIndexed { i, v ->
                val st = sb.length
                sb.append(v)
                sb.setSpan(ForegroundColorSpan(cols[i % 3]), st, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (i < k.variants.size - 1) sb.append(" ")
            }
            val tv = TextView(this).apply {
                text = sb; maxLines = 1; gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(1), 0, dp(1), 0)
                layoutParams = LinearLayout.LayoutParams(-1, -2)
            }
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(tv, 7, 13, 1, TypedValue.COMPLEX_UNIT_SP)
            c.addView(tv)
        }
        if (k.extras.isNotEmpty() && !isNum) {
            // نشانه‌ی حرف پنهان (مثل ض روی ص) در گوشه‌ی کلید؛ با نگه‌داشتن باز می‌شود
            w.addView(TextView(this).apply { text = k.extras[0]; textSize = 11f; setTextColor(t.v2) },
                FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.LEFT).apply { setMargins(dp(4), dp(1), 0, 0) })
        }
        var longPressed = false
        val lp = Runnable {
            longPressed = true
            w.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            if (k.variants.isNotEmpty() || k.extras.isNotEmpty()) showVariants(w, k, t)
        }
        w.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    longPressed = false
                    if (Prefs.anim(this)) { v.scaleX = 0.92f; v.scaleY = 0.92f }
                    v.alpha = 0.7f
                    handler.postDelayed(lp, 380)
                }
                MotionEvent.ACTION_UP -> {
                    v.scaleX = 1f; v.scaleY = 1f; v.alpha = 1f; handler.removeCallbacks(lp)
                    if (!longPressed) {
                        feedback(v)
                        val s = if (lay.rtl && mode == Mode.KEYS && !isNum) label + marks[harakat] else label
                        commit(s)
                        if (shift && !caps && !lay.rtl) { shift = false; fillContent() }
                    }
                }
                MotionEvent.ACTION_CANCEL -> { v.scaleX = 1f; v.scaleY = 1f; v.alpha = 1f; handler.removeCallbacks(lp) }
            }
            true
        }
        return w
    }

    /** نگه‌داشتن کلید: حرف ساده + فتحه + کسره + ضمه، و ردیف جدا برای حرف‌های پنهان (ض، غ، ظ، ژ، ذ، آ، ئ ...) */
    private fun showVariants(anchor: View, k: KeyDef, t: KbTheme) {
        val b = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(t.bg); setStroke(dp(1), t.border) }
        }
        val pw = PopupWindow(b, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        val withMarks = k.variants.isNotEmpty()
        val rows = ArrayList<List<String>>()
        rows.add(listOf(k.label) + k.variants)
        k.extras.forEach { e -> rows.add(if (withMarks) listOf(e, e + marks[1], e + marks[2], e + marks[3]) else listOf(e)) }
        rows.forEach { items ->
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
            items.forEach { s ->
                r.addView(TextView(this).apply {
                    text = s; textSize = 26f; setTextColor(t.text); gravity = Gravity.CENTER
                    setPadding(dp(12), dp(6), dp(12), dp(6))
                    setOnClickListener { commit(s); pw.dismiss() }
                })
            }
            b.addView(r)
        }
        val h = dp(52) * rows.size + dp(8)
        pw.showAsDropDown(anchor, 0, -(anchor.height + h))
    }

    private fun langBar(t: KbTheme): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_LTR
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), dp(2), dp(4), dp(2))
        }
        fun chip(txt: String, w: Float, active: Boolean, act: (View) -> Unit) = TextView(this).apply {
            text = txt; textSize = 13f; gravity = Gravity.CENTER; setTextColor(t.text)
            layoutParams = LinearLayout.LayoutParams(0, -1, w).apply { setMargins(dp(2), dp(1), dp(2), dp(1)) }
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat(); setColor(if (active) t.keyTop else 0x00000000)
                setStroke(dp(if (active) 2 else 1), if (active) t.border else 0x00000000)
            }
            setOnClickListener { act(it) }
        }
        fun pick(l: String) { lang = l; mode = Mode.KEYS; shift = false; caps = false; harakat = 0; rebuild() }
        bar.addView(chip("⚙\nتنظیمات", 1.2f, false) { openSettings() }.apply { textSize = 11f })
        bar.addView(chip("🌐 Hazaragi ▾", 2.1f, lang == "hazaragi") { pick("hazaragi") })
        bar.addView(chip("فارسی 🇮🇷", 1.5f, lang == "fa") { pick("fa") })
        bar.addView(chip("English 🇬🇧", 1.6f, lang == "en") { pick("en") })
        bar.addView(chip("العربية 🇸🇦", 1.6f, lang == "ar") { pick("ar") })
        bar.addView(chip("⌨\nطرح کیبورد", 1.4f, false) { v ->
            popupList(v, listOf(
                "حروف" to { setMode(Mode.KEYS) },
                "اعداد و نشانه‌ها" to { setMode(Mode.SYM) },
                "ایموجی" to { setMode(Mode.EMOJI) },
                "استیکر" to { setMode(Mode.STICKER) },
                "کلیپ‌بورد" to { readClip(); setMode(Mode.CLIP) },
                "تم بعدی" to { Prefs.setTheme(this, Themes.next(this).id); rebuild() }
            ))
        }.apply { textSize = 11f })
        return bar
    }

    private fun popupList(anchor: View, items: List<Pair<String, () -> Unit>>) {
        val t = Themes.current(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(t.bg); setStroke(dp(1), t.border) }
        }
        val pw = PopupWindow(box, dp(190), ViewGroup.LayoutParams.WRAP_CONTENT, true)
        items.forEach { (label, act) ->
            box.addView(TextView(this).apply {
                text = label; textSize = 16f; setTextColor(t.text)
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                setPadding(dp(16), dp(12), dp(16), dp(12))
                setOnClickListener { pw.dismiss(); act() }
            })
        }
        pw.showAsDropDown(anchor, 0, -(anchor.height + dp(46) * items.size + dp(8)))
    }

    // ---------------------------------------------------------------- متن و ورودی
    private fun feedback(v: View) {
        if (Prefs.vibrate(this)) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (Prefs.sound(this)) (getSystemService(Context.AUDIO_SERVICE) as AudioManager).playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD)
    }

    private fun commit(s: String) {
        currentInputConnection?.commitText(s, 1); updatePreview()
        if (clipBar?.visibility == View.VISIBLE) { lastPasted = clipHistory.firstOrNull(); refreshClipBar() }
    }

    private fun backspace() {
        val ic = currentInputConnection ?: return
        if (ic.getSelectedText(0).isNullOrEmpty()) ic.deleteSurroundingText(1, 0) else ic.commitText("", 1)
        updatePreview()
    }

    private fun enter() {
        val ic = currentInputConnection ?: return
        val a = (currentInputEditorInfo?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        if (a != EditorInfo.IME_ACTION_NONE && a != EditorInfo.IME_ACTION_UNSPECIFIED) ic.performEditorAction(a)
        else ic.commitText("\n", 1)
    }

    private fun updatePreview() {
        val ic = currentInputConnection ?: return
        val it = currentInputEditorInfo?.inputType ?: 0
        val v = it and InputType.TYPE_MASK_VARIATION
        val pass = (it and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT &&
            (v == InputType.TYPE_TEXT_VARIATION_PASSWORD || v == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                v == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
        val numPass = (it and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_NUMBER && v == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        // پیش‌نمایش فقط روی صفحه نمایش داده می‌شود؛ هیچ متنی ذخیره یا ارسال نمی‌شود
        header?.setPreview(if (pass || numPass) "••••" else (ic.getTextBeforeCursor(30, 0)?.toString() ?: ""))
    }

    private fun sendSticker(f: File) {
        val info = currentInputEditorInfo ?: return
        val ic = currentInputConnection ?: return
        val mime = if (f.name.endsWith(".webp")) "image/webp" else "image/png"
        val ok = EditorInfoCompat.getContentMimeTypes(info).any { ClipDescription.compareMimeTypes(mime, it) }
        if (!ok) {
            Toast.makeText(this, "این برنامه دریافت استیکر را پشتیبانی نمی‌کند", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "af.hazaragi.keyboard.stickers", f)
        val content = InputContentInfoCompat(uri, ClipDescription(f.name, arrayOf(mime)), null)
        InputConnectionCompat.commitContent(ic, info, content, InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null)
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
