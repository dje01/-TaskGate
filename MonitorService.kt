package com.taskgate
import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.*
import android.os.*
import android.provider.Telephony

class MonitorService : Service() {
    private val h = Handler(Looper.getMainLooper())
    private var current = ""
    private lateinit var system: Set<String>
    private val tick = object : Runnable { override fun run() { check(); h.postDelayed(this, 1000) } }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("tg", "TaskGate", NotificationManager.IMPORTANCE_LOW))
        startForeground(1, Notification.Builder(this, "tg").setContentTitle("TaskGate actif")
            .setSmallIcon(android.R.drawable.ic_lock_lock).build())
        system = sysAllowed()
        h.removeCallbacks(tick); h.post(tick)
        return START_STICKY
    }

    /** Apps toujours autorisées pour ne jamais te bloquer hors de ton téléphone. */
    private fun sysAllowed(): Set<String> {
        val s = mutableSetOf("android", "com.android.systemui", "com.android.settings", packageName,
            "com.google.android.packageinstaller", "com.android.packageinstaller",
            "com.google.android.permissioncontroller", "com.android.permissioncontroller")
        listOf(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), Intent(Intent.ACTION_DIAL)).forEach { i ->
            packageManager.queryIntentActivities(i, 0).forEach { s.add(it.activityInfo.packageName) } }
        Telephony.Sms.getDefaultSmsPackage(this)?.let { s.add(it) }
        return s
    }

    private fun foregroundApp(): String {
        val usm = getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val ev = usm.queryEvents(now - 5000, now); val e = UsageEvents.Event()
        while (ev.hasNextEvent()) { ev.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) current = e.packageName }
        return current
    }

    private fun check() {
        if (!getSystemService(PowerManager::class.java).isInteractive) return
        val pkg = foregroundApp()
        val tasks = Store.tasks(this)
        // 1) crédite le temps passé aux tâches chronométrées qui contiennent cette app
        tasks.filter { !it.manual && pkg in it.apps && !Store.done(this, it) }.forEach { Store.addSec(this, it, 1) }
        // 2) bloque si des tâches restent à faire
        val left = tasks.filter { !Store.done(this, it) }
        if (left.isEmpty() || pkg.isEmpty() || pkg in system) return
        val useful = left.flatMap { it.apps }.toSet()      // apps des tâches non finies : jamais bloquées
        if (pkg in useful) return
        val block = if (Store.blockAll(this)) pkg !in Store.allowed(this) else pkg in Store.blocked(this)
        if (block) startActivity(Intent(this, BlockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun onDestroy() { h.removeCallbacks(tick) }
    override fun onBind(i: Intent?): IBinder? = null
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) { c.startForegroundService(Intent(c, MonitorService::class.java)) }
}
