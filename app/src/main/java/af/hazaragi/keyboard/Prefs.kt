package af.hazaragi.keyboard

import android.content.Context

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("hk", Context.MODE_PRIVATE)
    fun theme(c: Context): String = sp(c).getString("theme", "neon") ?: "neon"
    fun setTheme(c: Context, id: String) = sp(c).edit().putString("theme", id).apply()
    fun bool(c: Context, k: String, def: Boolean) = sp(c).getBoolean(k, def)
    fun setBool(c: Context, k: String, v: Boolean) = sp(c).edit().putBoolean(k, v).apply()
    fun vibrate(c: Context) = bool(c, "vib", true)
    fun sound(c: Context) = bool(c, "snd", false)
    fun anim(c: Context) = bool(c, "anim", true)
    fun headerImage(c: Context) = bool(c, "himg", true)
    fun height(c: Context) = sp(c).getInt("height", 50)
    fun setHeight(c: Context, v: Int) = sp(c).edit().putInt("height", v).apply()
    fun recents(c: Context): List<String> =
        (sp(c).getString("recent", "") ?: "").split(" ").filter { it.isNotEmpty() }
    fun addRecent(c: Context, e: String) {
        val l = (listOf(e) + recents(c).filter { it != e }).take(24)
        sp(c).edit().putString("recent", l.joinToString(" ")).apply()
    }
}
