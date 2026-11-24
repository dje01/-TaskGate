package com.taskgate
import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** apps = apps qui comptent (tâche chronométrée) ou apps utiles autorisées (tâche manuelle). */
data class Task(val id: String, val name: String, val minutes: Int, val apps: Set<String>, val manual: Boolean)

object Store {
    private fun p(c: Context) = c.getSharedPreferences("tg2", Context.MODE_PRIVATE)

    fun tasks(c: Context): List<Task> {
        val a = JSONArray(p(c).getString("tasks", "[]"))
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i); val j = o.getJSONArray("apps")
            Task(o.getString("id"), o.getString("name"), o.getInt("minutes"),
                (0 until j.length()).map { j.getString(it) }.toSet(), o.getBoolean("manual"))
        }
    }
    fun saveTasks(c: Context, l: List<Task>) {
        val a = JSONArray()
        l.forEach { t -> a.put(JSONObject().put("id", t.id).put("name", t.name).put("minutes", t.minutes)
            .put("apps", JSONArray(t.apps.toList())).put("manual", t.manual)) }
        p(c).edit().putString("tasks", a.toString()).apply()
    }

    // Réglages : mode de blocage + listes
    fun blockAll(c: Context) = p(c).getBoolean("blockAll", true)
    fun setBlockAll(c: Context, v: Boolean) = p(c).edit().putBoolean("blockAll", v).apply()
    fun blocked(c: Context): Set<String> = p(c).getStringSet("blocked", emptySet()) ?: emptySet()
    fun allowed(c: Context): Set<String> = p(c).getStringSet("allowed", emptySet()) ?: emptySet()
    fun setBlocked(c: Context, s: Set<String>) = p(c).edit().putStringSet("blocked", HashSet(s)).apply()
    fun setAllowed(c: Context, s: Set<String>) = p(c).edit().putStringSet("allowed", HashSet(s)).apply()

    // Progression du jour (remise à zéro automatique chaque jour)
    private fun fresh(c: Context): SharedPreferences {
        val pr = p(c); val today = LocalDate.now().toString()
        if (pr.getString("day", "") != today) {
            val e = pr.edit()
            pr.all.keys.filter { it.startsWith("sec_") || it.startsWith("man_") }.forEach { e.remove(it) }
            e.putString("day", today).apply()
        }
        return pr
    }
    fun sec(c: Context, t: Task) = fresh(c).getInt("sec_${t.id}", 0)
    fun addSec(c: Context, t: Task, n: Int) { val pr = fresh(c); pr.edit().putInt("sec_${t.id}", pr.getInt("sec_${t.id}", 0) + n).apply() }
    fun setManual(c: Context, t: Task, v: Boolean) = fresh(c).edit().putBoolean("man_${t.id}", v).apply()
    fun done(c: Context, t: Task) = if (t.manual) fresh(c).getBoolean("man_${t.id}", false) else sec(c, t) >= t.minutes * 60
    fun allDone(c: Context) = tasks(c).all { done(c, it) }
}
