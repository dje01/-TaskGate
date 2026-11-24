package com.taskgate
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.*
import android.view.Gravity
import android.widget.*

class BlockActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    private lateinit var info: TextView
    private lateinit var box: LinearLayout
    private var shown = ""

    private val upd = object : Runnable { override fun run() {
        val c = this@BlockActivity
        val left = Store.tasks(c).filter { !Store.done(c, it) }
        if (left.isEmpty()) { finish(); return }
        info.text = left.joinToString("\n") { t ->
            if (t.manual) "• ${t.name} : à valider" else "• ${t.name} : ${Store.sec(c, t) / 60} / ${t.minutes} min" }
        val key = left.joinToString { it.id }
        if (key != shown) { shown = key; buttons(left) }
        h.postDelayed(this, 1000) } }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(60, 60, 60, 60); setBackgroundColor(Color.parseColor("#111827")) }
        fun tv(t: String, s: Float) = TextView(this).apply { text = t; textSize = s; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(0, 12, 0, 12) }
        l.addView(tv("🔒 Téléphone verrouillé", 26f)); l.addView(tv("Termine tes tâches du jour :", 16f))
        info = tv("", 17f); l.addView(info)
        box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; l.addView(box)
        l.addView(Button(this).apply { text = "Retour à l'accueil"; setOnClickListener { home() } })
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.parseColor("#111827")); isFillViewport = true; addView(l) })
    }

    private fun buttons(left: List<Task>) {
        box.removeAllViews()
        left.forEach { t ->
            if (t.manual) box.addView(Button(this).apply { text = "✅ J'ai fini : ${t.name}"
                setOnClickListener { AlertDialog.Builder(this@BlockActivity).setTitle(t.name).setMessage("Tu confirmes l'avoir vraiment fait ?")
                    .setPositiveButton("Oui, c'est fait") { _, _ -> Store.setManual(this@BlockActivity, t, true) }
                    .setNegativeButton("Pas encore", null).show() } })
            t.apps.take(4).forEach { pkg ->
                val intent = packageManager.getLaunchIntentForPackage(pkg) ?: return@forEach
                val label = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)) } catch (e: Exception) { pkg }
                box.addView(Button(this).apply { text = "Ouvrir $label (${t.name})"
                    setOnClickListener { startActivity(intent); finish() } })
            }
        }
    }
    private fun home() { startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); finish() }
    override fun onBackPressed() = home()
    override fun onResume() { super.onResume(); shown = ""; h.post(upd) }
    override fun onPause() { super.onPause(); h.removeCallbacks(upd) }
}
