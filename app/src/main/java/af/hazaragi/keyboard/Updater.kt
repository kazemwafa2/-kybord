package af.hazaragi.keyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/** تقسیم متن طولانی به تکه‌های امن؛ جفت‌جانشین (ایموجی) هیچ‌وقت وسط شکسته نمی‌شود */
fun chunkText(s: String, max: Int = 4000): List<String> {
    if (s.length <= max) return listOf(s)
    val out = ArrayList<String>()
    var i = 0
    while (i < s.length) {
        var e = minOf(i + max, s.length)
        if (e < s.length && Character.isHighSurrogate(s[e - 1]) && Character.isLowSurrogate(s[e])) e--
        out.add(s.substring(i, e))
        i = e
    }
    return out
}

object Versions {
    fun clean(v: String): String = v.trim().removePrefix("v").removePrefix("V")

    fun parse(v: String): List<Int> =
        clean(v).split('.', '-', '+').takeWhile { it.isNotEmpty() && it.all { ch -> ch.isDigit() } }.map { it.toInt() }

    /** منفی: a قدیمی‌تر؛ صفر: برابر؛ مثبت: a جدیدتر */
    fun compare(a: String, b: String): Int {
        val x = parse(a)
        val y = parse(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }
}

class UpdateException(msg: String) : Exception(msg)

data class UpdateInfo(
    val versionName: String, val versionCode: Long, val notes: String,
    val assetName: String, val assetUrl: String, val assetSize: Long,
    val sha256: String?, val isZip: Boolean, val important: Boolean
)

sealed class CheckResult {
    object UpToDate : CheckResult()
    data class Available(val info: UpdateInfo) : CheckResult()
    data class Failed(val message: String) : CheckResult()
}

/**
 * بررسی نسخه‌ی جدید از GitHub Releases مخزن رسمی.
 * فقط یک درخواست GET به GitHub زده می‌شود؛ هیچ متنِ تایپ‌شده یا اطلاعات کاربر ارسال نمی‌شود.
 */
object UpdateChecker {
    const val API = "https://api.github.com/repos/kazemwafa2/-kybord/releases/latest"
    const val PAGE = "https://github.com/kazemwafa2/-kybord/releases"
    private const val AUTHORITY = "af.hazaragi.keyboard.stickers"
    private const val HOUR = 3600L * 1000L

    private fun sp(c: Context) = c.getSharedPreferences("hk_update", Context.MODE_PRIVATE)

    fun installedName(c: Context): String = try {
        c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: "0"
    } catch (e: Exception) { "0" }

    fun installedCode(c: Context): Long = try {
        PackageInfoCompat.getLongVersionCode(c.packageManager.getPackageInfo(c.packageName, 0))
    } catch (e: Exception) { 0L }

    // ------------------------------------------------------------ شبکه
    private class Resp(val code: Int, val body: String, val rateLimited: Boolean)

    private fun get(c: Context, url: String): Resp {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "HazaragiKeyboard/" + installedName(c))
            val code = conn.responseCode
            val limited = (code == 403 && conn.getHeaderField("X-RateLimit-Remaining") == "0") || code == 429
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            return Resp(code, body, limited)
        } finally {
            conn.disconnect()
        }
    }

    // ------------------------------------------------------------ بررسی
    /** باید روی نخ پس‌زمینه صدا زده شود. manual=true کش را نادیده می‌گیرد. */
    fun check(c: Context, manual: Boolean): CheckResult {
        val now = System.currentTimeMillis()
        val cacheFresh = !manual && now - sp(c).getLong("cache_at", 0) < 6 * HOUR
        var body: String? = if (cacheFresh) sp(c).getString("cache", null) else null
        if (body == null) {
            try {
                val r = get(c, API)
                when {
                    r.code == 200 -> {
                        body = r.body
                        sp(c).edit().putString("cache", body).putLong("cache_at", now).apply()
                    }
                    r.rateLimited -> {
                        body = sp(c).getString("cache", null)
                        if (body == null) return CheckResult.Failed("محدودیت درخواست GitHub؛ کمی بعد دوباره تلاش کنید")
                    }
                    r.code == 404 -> return CheckResult.Failed("هنوز نسخه‌ای در GitHub منتشر نشده است")
                    else -> return CheckResult.Failed("خطای سرور GitHub (" + r.code + ")")
                }
            } catch (e: IOException) {
                return CheckResult.Failed("اینترنت قطع است یا GitHub در دسترس نیست")
            } catch (e: Exception) {
                return CheckResult.Failed("بررسی نسخه ناموفق بود")
            }
        }
        sp(c).edit().putLong("last_check", now).apply()
        val text = body ?: return CheckResult.Failed("بررسی نسخه ناموفق بود")
        return try { parse(c, text) } catch (e: Exception) { CheckResult.Failed("پاسخ GitHub قابل‌فهم نبود") }
    }

    private fun parse(c: Context, body: String): CheckResult {
        val j = JSONObject(body)
        // نسخه‌ی آزمایشی/پیش‌انتشار هرگز به‌عنوان نسخه‌ی پایدار پیشنهاد نمی‌شود
        if (j.optBoolean("draft") || j.optBoolean("prerelease")) return CheckResult.UpToDate
        val tag = j.optString("tag_name", "")
        if (tag.isEmpty()) return CheckResult.Failed("برچسب نسخه در انتشار پیدا نشد")
        val notes = j.optString("body", "")

        var apk: JSONObject? = null
        var zip: JSONObject? = null
        var meta: JSONObject? = null
        var shaAsset: JSONObject? = null
        val assets = j.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val n = a.optString("name").lowercase()
                if (n.endsWith(".apk") && apk == null) apk = a
                else if (n.endsWith(".zip") && zip == null) zip = a
                else if (n == "version.json") meta = a
                else if (n.endsWith(".sha256")) shaAsset = a
            }
        }
        val chosen = apk ?: zip ?: return CheckResult.Failed("فایل نصب در این انتشار پیدا نشد")
        val isZip = chosen.optString("name").lowercase().endsWith(".zip")

        var sha: String? = null
        var vCode = -1L
        val digest = chosen.optString("digest", "")
        if (digest.startsWith("sha256:")) sha = digest.removePrefix("sha256:").lowercase()
        if (meta != null) {
            try {
                val m = JSONObject(get(c, meta.getString("browser_download_url")).body)
                vCode = m.optLong("versionCode", -1L)
                val ms = m.optString("sha256", "").lowercase()
                if (sha == null && !isZip && ms.length == 64) sha = ms
            } catch (e: Exception) { /* اختیاری */ }
        }
        if (sha == null && shaAsset != null && !isZip) {
            try {
                sha = Regex("[a-fA-F0-9]{64}").find(get(c, shaAsset.getString("browser_download_url")).body)?.value?.lowercase()
            } catch (e: Exception) { /* اختیاری */ }
        }
        if (sha == null && !isZip) {
            sha = Regex("(?i)sha-?256\\D{0,6}([a-f0-9]{64})").find(notes)?.groupValues?.get(1)?.lowercase()
        }

        val newer = if (vCode > 0) vCode > installedCode(c) else Versions.compare(tag, installedName(c)) > 0
        if (!newer) {
            sp(c).edit().remove("pending").apply()
            File(c.cacheDir, "updates").deleteRecursively()
            return CheckResult.UpToDate
        }
        sp(c).edit().putString("pending", Versions.clean(tag)).apply()
        val info = UpdateInfo(
            versionName = Versions.clean(tag), versionCode = vCode, notes = notes.trim().take(900),
            assetName = chosen.optString("name"), assetUrl = chosen.getString("browser_download_url"),
            assetSize = chosen.optLong("size", -1L), sha256 = sha, isZip = isZip,
            important = Regex("امنیتی|security", RegexOption.IGNORE_CASE).containsMatchIn(notes)
        )
        return CheckResult.Available(info)
    }

    // ------------------------------------------------------------ بررسی خودکار (حداکثر روزی یک بار)
    fun autoCheckAsync(c: Context) {
        val app = c.applicationContext
        if (!Prefs.bool(app, "autoupd", true)) return
        val now = System.currentTimeMillis()
        if (now - sp(app).getLong("last_try", 0) < 24 * HOUR) return
        sp(app).edit().putLong("last_try", now).apply()
        Thread { try { check(app, false) } catch (e: Exception) { } }.start()
    }

    /** هنگام باز شدن تنظیمات: حداکثر هر ۱۲ ساعت یک بررسی خودکار */
    fun dueForOpenCheck(c: Context): Boolean {
        val now = System.currentTimeMillis()
        if (now - sp(c).getLong("open_check", 0) < 12 * HOUR) return false
        sp(c).edit().putLong("open_check", now).apply()
        return true
    }

    /** نسخه‌ی جدیدِ در انتظار که کاربر «بعداً» نزده است؛ در غیر این صورت null */
    fun pendingVersion(c: Context): String? {
        val v = sp(c).getString("pending", null) ?: return null
        if (Versions.compare(v, installedName(c)) <= 0) { sp(c).edit().remove("pending").apply(); return null }
        if (sp(c).getString("snooze_ver", "") == v && System.currentTimeMillis() < sp(c).getLong("snooze_until", 0)) return null
        return v
    }

    fun snooze(c: Context, ver: String) {
        sp(c).edit().putString("snooze_ver", ver).putLong("snooze_until", System.currentTimeMillis() + 72 * HOUR).apply()
    }

    // ------------------------------------------------------------ دانلود و اعتبارسنجی
    fun readyApk(c: Context): File? {
        val f = File(c.cacheDir, "updates/update.apk")
        return if (f.exists() && try { validate(c, f); true } catch (e: Exception) { false }) f else null
    }

    @Throws(Exception::class)
    fun download(c: Context, info: UpdateInfo, onProgress: (Int) -> Unit): File {
        val dir = File(c.cacheDir, "updates")
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        val tmp = File(dir, "download.part")
        val md = MessageDigest.getInstance("SHA-256")
        var total = 0L
        val conn = URL(info.assetUrl).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.setRequestProperty("User-Agent", "HazaragiKeyboard/" + installedName(c))
            if (conn.responseCode != 200) throw UpdateException("دانلود ناموفق بود (" + conn.responseCode + ")")
            conn.inputStream.use { ins ->
                FileOutputStream(tmp).use { out ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        md.update(buf, 0, n)
                        total += n
                        if (info.assetSize > 0) onProgress((total * 100 / info.assetSize).toInt().coerceAtMost(100))
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        if (info.assetSize > 0 && total != info.assetSize) {
            tmp.delete()
            throw UpdateException("دانلود ناقص بود؛ دوباره تلاش کنید")
        }
        val got = md.digest().joinToString("") { "%02x".format(it) }
        if (info.sha256 != null && !got.equals(info.sha256, ignoreCase = true)) {
            tmp.delete()
            throw UpdateException("هش فایل با مقدار اعلام‌شده نمی‌خواند؛ نصب لغو شد")
        }
        val apk = File(dir, "update.apk")
        if (info.isZip) {
            var found = false
            ZipInputStream(tmp.inputStream()).use { zin ->
                while (true) {
                    val e = zin.nextEntry ?: break
                    if (!e.isDirectory && e.name.lowercase().endsWith(".apk")) {
                        FileOutputStream(apk).use { out ->
                            val buf = ByteArray(32 * 1024)
                            var sum = 0L
                            while (true) {
                                val n = zin.read(buf)
                                if (n < 0) break
                                sum += n
                                if (sum > 300L * 1024 * 1024) throw UpdateException("فایل ZIP نامعتبر است")
                                out.write(buf, 0, n)
                            }
                        }
                        found = true
                        break
                    }
                }
            }
            tmp.delete()
            if (!found) throw UpdateException("داخل فایل ZIP هیچ APK پیدا نشد")
        } else if (!tmp.renameTo(apk)) {
            throw UpdateException("ذخیره‌ی فایل ناموفق بود")
        }
        try { validate(c, apk) } catch (e: Exception) { apk.delete(); throw e }
        return apk
    }

    @Throws(Exception::class)
    private fun validate(c: Context, f: File) {
        val pi = c.packageManager.getPackageArchiveInfo(f.absolutePath, 0)
            ?: throw UpdateException("فایل APK خراب است")
        if (pi.packageName != c.packageName) throw UpdateException("این فایل متعلق به کیبورد هزارگی نیست")
        if (PackageInfoCompat.getLongVersionCode(pi) <= installedCode(c)) {
            throw UpdateException("فایل دانلودشده جدیدتر از نسخه‌ی نصب‌شده نیست")
        }
        val ai = pi.applicationInfo
        if (Build.VERSION.SDK_INT >= 24 && ai != null && ai.minSdkVersion > Build.VERSION.SDK_INT) {
            throw UpdateException("این نسخه با اندروید این گوشی سازگار نیست")
        }
    }

    // ------------------------------------------------------------ نصب
    fun needsInstallPermission(c: Context): Boolean =
        Build.VERSION.SDK_INT >= 26 && !c.packageManager.canRequestPackageInstalls()

    /** نصب‌کننده‌ی رسمی اندروید را باز می‌کند؛ تأیید نهایی با خود کاربر است */
    fun install(a: Activity, apk: File) {
        if (needsInstallPermission(a)) {
            a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.packageName)))
            return
        }
        val uri = FileProvider.getUriForFile(a, AUTHORITY, apk)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        a.startActivity(i)
    }
}
