package com.munawar.ai

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Keeps the latest notifications in memory only (never saved to disk). Works only after the user grants Notification access.
 * v0.5: OTP-looking codes are masked in normal reads (spoken only on request via "read otp"), supports replying to a
 * notification without opening the app (messaging apps with a reply action), and clearing notifications.
 */
class NotifListener : NotificationListenerService() {
    class Item(val key: String, val app: String, val title: String, val text: String, val sbn: StatusBarNotification) {
        val line: String get() = "$app: $title — $text".trim()
    }

    override fun onListenerConnected() {
        inst = this
        try { activeNotifications?.forEach { add(it) } } catch (e: Exception) {}
    }

    override fun onListenerDisconnected() { if (inst === this) inst = null }

    override fun onNotificationPosted(sbn: StatusBarNotification) { add(sbn) }

    override fun onDestroy() { synchronized(items) { items.clear() }; if (inst === this) inst = null; super.onDestroy() }

    private fun add(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0 || n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val title = n.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = n.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        if (title.isBlank() && text.isBlank()) return
        val app = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString() } catch (e: Exception) { sbn.packageName }
        synchronized(items) {
            items.removeAll { it.key == sbn.key }
            items.add(0, Item(sbn.key, app, title, text, sbn))
            while (items.size > 40) items.removeAt(items.size - 1)
        }
        Habits.onNotification(sbn.packageName)
        try { Buddy.onNotification(app, sbn.packageName, title, text, n.category) } catch (e: Exception) {}
    }

    companion object {
        private val items = ArrayList<Item>()
        @Volatile var inst: NotifListener? = null
        private val otpWords = Regex("(?i)otp|one[- ]time|verification|verify|code|pin|passcode|password")
        private val digits = Regex("\\b\\d{4,8}\\b")

        internal fun mask(s: String) = if (otpWords.containsMatchIn(s)) digits.replace(s, "••••") else s

        /** Latest notifications, with OTP-like codes hidden. */
        fun recent(n: Int = 10): List<String> = synchronized(items) { items.take(n).map { mask(it.line) } }

        /** Only when the user explicitly asks: the newest notification that looks like it carries a one-time code. */
        fun latestOtp(): Pair<String, String>? {
            synchronized(items) {
                for (item in items) {
                    val body = item.title + " " + item.text
                    if (otpWords.containsMatchIn(body)) {
                        val m = digits.find(body)
                        if (m != null) return Pair(item.app, m.value)
                    }
                }
            }
            return null
        }

        /** Replies to the newest notification whose app or title contains [who] (blank = newest repliable). */
        fun reply(who: String, text: String): String {
            val q = who.lowercase().trim()
            val item = synchronized(items) {
                items.firstOrNull { n -> (q.isEmpty() || n.app.lowercase().contains(q) || n.title.lowercase().contains(q)) && replyAction(n.sbn.notification) != null }
            } ?: return "No notification with a reply option found" + (if (q.isNotEmpty()) " for \"$who\"." else ".")
            val act = replyAction(item.sbn.notification) ?: return "That notification can't be replied to."
            val ri = act.remoteInputs ?: return "That notification can't be replied to."
            val results = Bundle()
            for (r in ri) results.putCharSequence(r.resultKey, text)
            val intent = Intent()
            RemoteInput.addResultsToIntent(ri, intent, results)
            return try {
                act.actionIntent.send(inst, 0, intent)
                "Replied to ${item.app} (${item.title}): \"$text\""
            } catch (e: Exception) { "Couldn't send the reply (${e.javaClass.simpleName})." }
        }

        private fun replyAction(n: Notification?): Notification.Action? =
            n?.actions?.firstOrNull { a -> a.remoteInputs?.isNotEmpty() == true && a.actionIntent != null }

        fun clearAll(): Boolean {
            val i = inst ?: return false
            return try { i.cancelAllNotifications(); synchronized(items) { items.clear() }; true } catch (e: Exception) { false }
        }
    }
}
