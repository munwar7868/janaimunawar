package com.munawar.ai

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Note(val id: Long, val kind: String, var text: String, var done: Boolean, val ts: Long)

/** Local notes and to-do tasks (private app storage). */
class NotesStore(ctx: Context) {
    private val f = File(ctx.filesDir, "notes.json")
    private val items = mutableListOf<Note>()

    init {
        try {
            if (f.exists()) {
                val a = JSONArray(f.readText())
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    items.add(Note(o.getLong("id"), o.getString("k"), o.getString("t"), o.optBoolean("d"), o.optLong("ts")))
                }
            }
        } catch (e: Exception) { items.clear() }
    }

    private fun save() {
        val a = JSONArray()
        items.forEach { a.put(JSONObject().put("id", it.id).put("k", it.kind).put("t", it.text).put("d", it.done).put("ts", it.ts)) }
        f.writeText(a.toString())
    }

    @Synchronized fun all(kind: String): List<Note> = items.filter { it.kind == kind }
    @Synchronized fun add(kind: String, text: String) { items.add(Note(System.nanoTime(), kind, text.trim(), false, System.currentTimeMillis())); save() }
    @Synchronized fun toggle(id: Long) { items.find { it.id == id }?.let { it.done = !it.done }; save() }
    @Synchronized fun delete(id: Long) { items.removeAll { it.id == id }; save() }
    @Synchronized fun clearDone() { items.removeAll { it.kind == "task" && it.done }; save() }
    @Synchronized fun pendingTasks(): List<Note> = items.filter { it.kind == "task" && !it.done }
}

/** In-app reminders (notification at a set time). Uses an inexact alarm, so Android may delay it by a minute or two in Doze. */
object Reminders {
    fun schedule(ctx: Context, whenMs: Long, text: String) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val i = Intent(ctx, ReminderReceiver::class.java).putExtra("text", text)
        val pi = PendingIntent.getBroadcast(ctx, (whenMs % 100000).toInt(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val text = i.getStringExtra("text") ?: "Reminder"
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("munawar_reminder", "Reminders", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(System.currentTimeMillis().toInt(),
            Notification.Builder(c, "munawar_reminder").setContentTitle("⏰ Munawar reminder").setContentText(text)
                .setSmallIcon(android.R.drawable.ic_popup_reminder).setAutoCancel(true).setContentIntent(open).build())
    }
}
