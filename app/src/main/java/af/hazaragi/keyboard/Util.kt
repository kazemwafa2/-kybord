package af.hazaragi.keyboard

import android.content.Context
import android.os.Handler
import android.view.MotionEvent
import android.view.View

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

/** اجرای مکرر اکشن هنگام نگه‌داشتن (حذف، کشیده‌نویسی) */
fun attachRepeat(v: View, h: Handler, action: () -> Unit) {
    val rep = object : Runnable {
        override fun run() { action(); h.postDelayed(this, 55) }
    }
    v.setOnTouchListener { view, e ->
        when (e.action) {
            MotionEvent.ACTION_DOWN -> { view.alpha = 0.6f; action(); h.postDelayed(rep, 380) }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { view.alpha = 1f; h.removeCallbacks(rep) }
        }
        true
    }
}
