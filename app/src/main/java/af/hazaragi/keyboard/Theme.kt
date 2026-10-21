package af.hazaragi.keyboard

import android.content.Context

data class KbTheme(
    val id: String, val name: String,
    val bg: Int, val keyTop: Int, val keyBottom: Int, val border: Int,
    val text: Int, val v1: Int, val v2: Int, val v3: Int,
    val useImages: Boolean
)

object Themes {
    val all = listOf(
        KbTheme("neon", "آبی نئونی (هزارستان)", 0xFF050B1F.toInt(), 0xFF123073.toInt(), 0xFF081A45.toInt(), 0xFF2D8CFF.toInt(),
            0xFFFFFFFF.toInt(), 0xFFFFD54F.toInt(), 0xFF4DEBFF.toInt(), 0xFFFF4DD8.toInt(), true),
        KbTheme("gold", "سرمه‌ای و طلایی", 0xFF0A1633.toInt(), 0xFF1B3068.toInt(), 0xFF0F1D44.toInt(), 0xFFD4A640.toInt(),
            0xFFFFF4D6.toInt(), 0xFFFFD54F.toInt(), 0xFFFFB74D.toInt(), 0xFFFFE082.toInt(), false),
        KbTheme("night", "شب", 0xFF000000.toInt(), 0xFF262626.toInt(), 0xFF121212.toInt(), 0xFF555555.toInt(),
            0xFFEEEEEE.toInt(), 0xFF9CCC65.toInt(), 0xFF4FC3F7.toInt(), 0xFFF06292.toInt(), false),
        KbTheme("minimal", "ساده", 0xFFEFEFEF.toInt(), 0xFFFFFFFF.toInt(), 0xFFF5F5F5.toInt(), 0xFFBDBDBD.toInt(),
            0xFF111111.toInt(), 0xFFEF6C00.toInt(), 0xFF00838F.toInt(), 0xFFAD1457.toInt(), false)
    )
    fun current(c: Context): KbTheme = all.firstOrNull { it.id == Prefs.theme(c) } ?: all[0]
    fun next(c: Context): KbTheme = all[(all.indexOf(current(c)) + 1) % all.size]
}
