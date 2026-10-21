package af.hazaragi.keyboard

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*

class SettingsActivity : Activity() {

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(24), dp(20), dp(24))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        fun title(s: String, size: Float = 16f) = TextView(this).apply {
            text = s; textSize = size; setPadding(0, dp(18), 0, dp(6))
        }
        fun button(s: String, act: () -> Unit) = Button(this).apply { text = s; setOnClickListener { act() } }
        fun sw(s: String, k: String, def: Boolean) = Switch(this).apply {
            text = s; isChecked = Prefs.bool(context, k, def); setPadding(0, dp(8), 0, dp(8))
            setOnCheckedChangeListener { _, v -> Prefs.setBool(context, k, v) }
        }

        col.addView(title("هزارگی؛ زبان ما، هویت ما", 22f))
        col.addView(button("۱) فعال‌کردن کیبورد") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) })
        col.addView(button("۲) انتخاب کیبورد هزارگی") { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() })

        col.addView(title("انتخاب تم"))
        val g = RadioGroup(this)
        val cur = Prefs.theme(this)
        Themes.all.forEach { t ->
            g.addView(RadioButton(this).apply {
                text = t.name; id = View.generateViewId(); isChecked = t.id == cur
                setOnClickListener { Prefs.setTheme(context, t.id) }
            })
        }
        col.addView(g)

        col.addView(title("ارتفاع کلیدها"))
        col.addView(SeekBar(this).apply {
            max = 32; progress = Prefs.height(context) - 36
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { Prefs.setHeight(context, 36 + p) }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })

        col.addView(title("امکانات"))
        col.addView(sw("نمایش تصویر هزارستان بالای کیبورد", "himg", true))
        col.addView(sw("لرزش کلیدها", "vib", true))
        col.addView(sw("صدای کلیدها", "snd", false))
        col.addView(sw("انیمیشن کلیدها", "anim", true))

        col.addView(title("استیکر"))
        col.addView(button("ساخت استیکر از تصویر گالری") {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "image/*" }
            startActivityForResult(i, 7)
        })
        col.addView(TextView(this).apply { text = "برای حذف استیکر شخصی، روی آن در کیبورد نگه دارید."; textSize = 12f })

        setContentView(ScrollView(this).apply { addView(col) })
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 7 && res == RESULT_OK) {
            val uri: Uri = data?.data ?: return
            val ok = StickerStore.importFrom(this, uri)
            Toast.makeText(this, if (ok) "استیکر ساخته شد" else "خطا در ساخت استیکر", Toast.LENGTH_SHORT).show()
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
