package af.hazaragi.keyboard

import android.content.Context
import org.json.JSONObject

data class KeyDef(val label: String, val variants: List<String>)
data class Layout(val id: String, val name: String, val rtl: Boolean, val rows: List<List<KeyDef>>)

object Layouts {
    val order = listOf("hazaragi", "fa", "en", "ar")   // پیش‌فرض: هزارگی
    private val cache = HashMap<String, Layout>()

    fun get(c: Context, id: String): Layout = cache.getOrPut(id) {
        val txt = c.assets.open("layouts/$id.json").bufferedReader().use { it.readText() }
        val o = JSONObject(txt)
        val rows = o.getJSONArray("rows")
        Layout(o.getString("id"), o.getString("name"), o.getBoolean("rtl"),
            (0 until rows.length()).map { r ->
                val row = rows.getJSONArray(r)
                (0 until row.length()).map { i ->
                    val k = row.getJSONObject(i)
                    val v = k.getJSONArray("variants")
                    KeyDef(k.getString("label"), (0 until v.length()).map { v.getString(it) })
                }
            })
    }
}
