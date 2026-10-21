package af.hazaragi.keyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

object StickerStore {
    /** دسته‌ها: پیشوند نام فایل ← عنوان */
    val categories = linkedMapOf(
        "lebas" to "لباس سنتی", "arosi" to "عروسی", "salam" to "روزمره و تبریک",
        "khorak" to "خوراک", "farhang" to "فرهنگ و تمدن", "user" to "من"
    )

    fun category(f: File): String {
        val p = f.name.substringBefore("_")
        return if (categories.containsKey(p)) p else "farhang"
    }

    fun dir(c: Context): File = File(c.filesDir, "stickers").apply { mkdirs() }

    /** کپی استیکرهای همراه برنامه (assets/stickers) — دوباره برای هر نسخه‌ی جدید */
    fun ensureBundled(c: Context) {
        val mark = File(dir(c), ".v3")
        if (mark.exists()) return
        dir(c).listFiles()?.filter { it.name.startsWith("hazaragi_") }?.forEach { it.delete() }
        c.assets.list("stickers")?.forEach { n ->
            c.assets.open("stickers/$n").use { i -> File(dir(c), n).outputStream().use { o -> i.copyTo(o) } }
        }
        mark.writeText("1")
    }

    fun list(c: Context): List<File> {
        ensureBundled(c)
        return (dir(c).listFiles() ?: emptyArray())
            .filter { it.name.endsWith(".png") || it.name.endsWith(".webp") }
            .sortedBy { it.name }
    }

    /** ساخت استیکر از تصویر شخصی (حداکثر ۵۱۲ پیکسل) */
    fun importFrom(c: Context, uri: Uri): Boolean = try {
        val bmp = c.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
        val s = 512f / maxOf(bmp.width, bmp.height)
        val out = if (s < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt(), (bmp.height * s).toInt(), true) else bmp
        File(dir(c), "user_${System.currentTimeMillis()}.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        true
    } catch (e: Exception) { false }
}
