package com.taskgate
import android.app.Activity
import android.app.AlertDialog
import android.app.AppOpsManager
import android.content.Intent
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.text.InputType
import android.widget.*
import java.util.UUID

class MainActivity : Activity() {
    private lateinit var apps: List<Pair<String, String>>   // (nom, package)
    private lateinit var root: LinearLayout

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val pm = packageManager
        apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
            .filter { it.second != packageName }.distinctBy { it.second }.sortedBy { it.first.lowercase() }
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 60, 40, 60) }
        setContentView(ScrollView(this).apply { addView(root) })
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 0)
    }
    override fun onResume() {
        super.onResume(); render()
        if (hasUsage() && Settings.canDrawOverlays(this)) startForegroundService(Intent(this, MonitorService::class.java))
    }

    private fun label(pkg: String) = apps.firstOrNull { it.second == pkg }?.first ?: pkg
    private fun hasUsage() = getSystemService(AppOpsManager::class.java)
        .unsafeCheckOpNoThrow("android:get_usage_stats", Process.myUid(), packageName) == AppOpsManager.MODE_ALLOWED
    private fun title(t: String) = TextView(this).apply { text = t; textSize = 18f; setPadding(0, 40, 0, 10) }
    private fun btn(t: String, f: () -> Unit) = Button(this).apply { text = t; isAllCaps = false; setOnClickListener { f() } }

    private fun render() {
        root.removeAllViews()
        root.addView(TextView(this).apply { text = "TaskGate"; textSize = 28f })
        root.addView(title("Autorisations"))
        root.addView(btn("1. Accès à l'usage ${if (hasUsage()) "✅" else "❌"}") { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) })
        root.addView(btn("2. Affichage par-dessus les apps ${if (Settings.canDrawOverlays(this)) "✅" else "❌"}") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) })

        val tasks = Store.tasks(this)
        root.addView(title("Mes tâches du jour"))
        if (tasks.isEmpty()) root.addView(TextView(this).apply { text = "Aucune tâche : rien n'est bloqué." })
        tasks.forEach { t ->
            val st = if (Store.done(this, t)) "✅ fait" else if (t.manual) "à valider" else "${Store.sec(this, t) / 60}/${t.minutes} min"
            root.addView(btn("${t.name} : $st") { taskMenu(t) })
        }
        root.addView(btn("➕ Ajouter une tâche") { addTask() })

        root.addView(title("Que bloquer tant que tout n'est pas fait ?"))
        val all = Store.blockAll(this)
        root.addView(btn(if (all) "Mode : TOUT bloquer sauf apps autorisées (toucher pour changer)" else "Mode : bloquer SEULEMENT les apps choisies (toucher pour changer)") {
            Store.setBlockAll(this, !all); render() })
        if (all) root.addView(btn("Apps toujours autorisées (${Store.allowed(this).size})") {
            pickMany("Apps toujours autorisées", Store.allowed(this)) { Store.setAllowed(this, it); render() } })
        else root.addView(btn("Apps à bloquer (${Store.blocked(this).size})") {
            pickMany("Apps à bloquer", Store.blocked(this)) { Store.setBlocked(this, it); render() } })
        root.addView(TextView(this).apply { textSize = 13f; setPadding(0, 20, 0, 0)
            text = "Téléphone, SMS, accueil et réglages restent toujours accessibles. Les apps de tes tâches non finies sont débloquées." })
    }

    private fun taskMenu(t: Task) {
        val items = mutableListOf<String>(); val acts = mutableListOf<() -> Unit>()
        if (t.manual) { val d = Store.done(this, t)
            items.add(if (d) "Annuler la validation" else "Marquer comme fait ✅"); acts.add { Store.setManual(this, t, !d); render() } }
        items.add("Modifier les apps"); acts.add { pickMany("Apps de « ${t.name} »", t.apps) { s ->
            Store.saveTasks(this, Store.tasks(this).map { if (it.id == t.id) it.copy(apps = s) else it }); render() } }
        items.add("Supprimer"); acts.add { Store.saveTasks(this, Store.tasks(this).filter { it.id != t.id }); render() }
        AlertDialog.Builder(this).setTitle(t.name).setItems(items.toTypedArray()) { _, i -> acts[i]() }.show()
    }

    private fun addTask() {
        val name = EditText(this).apply { hint = "Nom (ex : Anglais, Sport)" }
        val min = EditText(this).apply { hint = "Minutes par jour"; inputType = InputType.TYPE_CLASS_NUMBER; setText("15") }
        val manual = CheckBox(this).apply { text = "Je valide moi-même (ex : sport sans app)" }
        val v = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(50, 20, 50, 0)
            addView(name); addView(min); addView(manual) }
        AlertDialog.Builder(this).setTitle("Nouvelle tâche").setView(v).setNegativeButton("Annuler", null)
            .setPositiveButton("Suivant") { _, _ ->
                pickMany(if (manual.isChecked) "Apps utiles (facultatif)" else "Apps qui comptent pour cette tâche", emptySet()) { sel ->
                    val t = Task(UUID.randomUUID().toString().take(8), name.text.toString().ifBlank { "Tâche" },
                        min.text.toString().toIntOrNull() ?: 15, sel, manual.isChecked)
                    Store.saveTasks(this, Store.tasks(this) + t); render() } }.show()
    }

    private fun pickMany(title: String, current: Set<String>, onOk: (Set<String>) -> Unit) {
        val sel = current.toMutableSet()
        AlertDialog.Builder(this).setTitle(title)
            .setMultiChoiceItems(apps.map { it.first }.toTypedArray(), BooleanArray(apps.size) { apps[it].second in sel }) { _, i, on ->
                if (on) sel.add(apps[i].second) else sel.remove(apps[i].second) }
            .setPositiveButton("OK") { _, _ -> onOk(sel) }.setNegativeButton("Annuler", null).show()
    }
}
