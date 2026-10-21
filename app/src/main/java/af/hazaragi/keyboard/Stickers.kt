package af.hazaragi.keyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

object StickerStore {
    fun dir(c: Context): File = File(c.filesDir, "stickers").apply { mkdirs() }

    /** کپی استیکرهای همراه برنامه (assets/stickers) در بار اول */
    fun ensureBundled(c: Context) {
        val mark = File(dir(c), ".init")
        if (mark.exists()) return
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
