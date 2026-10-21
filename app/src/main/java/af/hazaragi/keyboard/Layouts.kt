package af.hazaragi.keyboard

import android.content.Context
import org.json.JSONObject

data class KeyDef(val label: String, val variants: List<String>, val extras: List<String>)
data class Layout(val id: String, val name: String, val rtl: Boolean, val side: Boolean, val rows: List<List<KeyDef>>)

object Layouts {
    private val cache = HashMap<String, Layout>()

    fun get(c: Context, id: String): Layout = cache.getOrPut(id) {
        val txt = c.assets.open("layouts/$id.json").bufferedReader().use { it.readText() }
        val o = JSONObject(txt)
        val rows = o.getJSONArray("rows")
        fun list(a: org.json.JSONArray) = (0 until a.length()).map { a.getString(it) }
        Layout(o.getString("id"), o.getString("name"), o.getBoolean("rtl"), o.optBoolean("side", true),
            (0 until rows.length()).map { r ->
                val row = rows.getJSONArray(r)
                (0 until row.length()).map { i ->
                    val k = row.getJSONObject(i)
                    KeyDef(k.getString("label"), list(k.getJSONArray("variants")), list(k.getJSONArray("extras")))
                }
            })
    }
}
