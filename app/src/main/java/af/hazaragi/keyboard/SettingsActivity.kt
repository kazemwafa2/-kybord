package af.hazaragi.keyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*

class SettingsActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var bar: ProgressBar
    private lateinit var installBtn: Button
    private var busy = false

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

        col.addView(title("فاصله‌ی کلیدها"))
        col.addView(SeekBar(this).apply {
            max = 7; progress = Prefs.gap(context) - 1
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { Prefs.setGap(context, 1 + p) }
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

        col.addView(title("به‌روزرسانی"))
        col.addView(TextView(this).apply { text = "نسخه‌ی نصب‌شده: " + UpdateChecker.installedName(context) })
        status = TextView(this).apply { text = "برای بررسی نسخه‌ی جدید دکمه‌ی زیر را بزنید"; setPadding(0, dp(6), 0, dp(6)) }
        col.addView(status)
        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; visibility = View.GONE }
        col.addView(bar)
        col.addView(button("بررسی به‌روزرسانی") { if (!busy) runCheck(true) })
        installBtn = button("نصب نسخه‌ی دانلودشده") { UpdateChecker.readyApk(this)?.let { askInstall(it) } }
        installBtn.visibility = View.GONE
        col.addView(installBtn)
        col.addView(sw("بررسی خودکار به‌روزرسانی (حداکثر روزی یک بار)", "autoupd", true))
        col.addView(TextView(this).apply {
            text = "فقط شماره‌ی آخرین نسخه از GitHub پرسیده می‌شود؛ هیچ متنی که تایپ می‌کنید ارسال نمی‌شود."
            textSize = 12f
        })

        setContentView(ScrollView(this).apply { addView(col) })

        if (intent.getBooleanExtra("update", false)) runCheck(true)
        else if (Prefs.bool(this, "autoupd", true) && UpdateChecker.dueForOpenCheck(this)) runCheck(false)
    }

    override fun onResume() {
        super.onResume()
        installBtn.visibility = if (UpdateChecker.readyApk(this) != null) View.VISIBLE else View.GONE
    }

    // ---------------------------------------------------------------- به‌روزرسانی
    private fun runCheck(manual: Boolean) {
        status.text = "در حال بررسی…"
        Thread {
            val r = UpdateChecker.check(applicationContext, manual)
            runOnUiThread { if (!isFinishing) showResult(r, manual) }
        }.start()
    }

    private fun showResult(r: CheckResult, manual: Boolean) {
        when (r) {
            is CheckResult.UpToDate -> status.text = "کیبورد شما به‌روز است"
            is CheckResult.Failed -> status.text = r.message
            is CheckResult.Available -> {
                status.text = "نسخه‌ی جدید " + r.info.versionName + " موجود است"
                if (manual || UpdateChecker.pendingVersion(this) != null) showUpdateDialog(r.info)
            }
        }
    }

    private fun showUpdateDialog(i: UpdateInfo) {
        val msg = StringBuilder()
        if (i.important) msg.append("⚠️ این به‌روزرسانی شامل اصلاح مهم امنیتی است؛ نصب آن توصیه می‌شود.\n\n")
        msg.append("نسخه‌ی فعلی: ").append(UpdateChecker.installedName(this)).append('\n')
        msg.append("نسخه‌ی جدید: ").append(i.versionName).append("\n\n")
        msg.append(if (i.notes.isBlank()) "—" else i.notes)
        if (i.sha256 == null) msg.append("\n\n(هش فایل در این انتشار اعلام نشده؛ امضای اندروید هنگام نصب بررسی می‌شود.)")
        AlertDialog.Builder(this)
            .setTitle("به‌روزرسانی هزارگی")
            .setMessage(msg.toString())
            .setPositiveButton("دانلود و نصب نسخه جدید") { _, _ -> startDownload(i) }
            .setNegativeButton("بعداً") { _, _ ->
                UpdateChecker.snooze(this, i.versionName)
                status.text = "به‌روزرسانی به بعد موکول شد"
            }
            .show()
    }

    private fun startDownload(i: UpdateInfo) {
        if (busy) return
        busy = true
        bar.progress = 0; bar.visibility = View.VISIBLE
        status.text = "در حال دانلود…"
        Thread {
            try {
                val f = UpdateChecker.download(applicationContext, i) { p -> runOnUiThread { bar.progress = p } }
                runOnUiThread {
                    busy = false; bar.visibility = View.GONE
                    status.text = "دانلود کامل شد و فایل بررسی شد"
                    installBtn.visibility = View.VISIBLE
                    askInstall(f)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    busy = false; bar.visibility = View.GONE
                    status.text = if (e is UpdateException) e.message ?: "دانلود ناموفق بود" else "دانلود ناموفق بود؛ اینترنت را بررسی کنید"
                }
            }
        }.start()
    }

    private fun askInstall(f: java.io.File) {
        if (!UpdateChecker.needsInstallPermission(this)) { UpdateChecker.install(this, f); return }
        AlertDialog.Builder(this)
            .setTitle("اجازه‌ی نصب")
            .setMessage("برای نصب، اندروید باید اجازه‌ی «نصب برنامه از این منبع» را از شما بگیرد. در صفحه‌ی بعد گزینه را روشن کنید، سپس برگردید و دکمه‌ی «نصب نسخه‌ی دانلودشده» را بزنید.")
            .setPositiveButton("ادامه") { _, _ -> UpdateChecker.install(this, f) }
            .setNegativeButton("لغو", null)
            .show()
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
