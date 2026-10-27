package com.munawar.ai

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import kotlinx.coroutines.delay

/** Full phone control by voice: brightness, rotation, Do-Not-Disturb, ringer, quick-settings tiles, panels, power menu. */
object PhoneControl {
    private fun ok(m: String) = Automation.Result(true, m)
    private fun no(m: String) = Automation.Result(false, m)

    private val tiles = mapOf(
        "wifi" to listOf("Wi-Fi", "Wi‑Fi", "WiFi", "Internet"),
        "bluetooth" to listOf("Bluetooth"),
        "hotspot" to listOf("Hotspot", "Mobile hotspot"),
        "airplane" to listOf("Airplane mode", "Flight mode", "Airplane"),
        "nfc" to listOf("NFC"),
        "location" to listOf("Location"),
        "data" to listOf("Mobile data", "Internet")
    )

    private fun key(w: String): String = when (w) {
        "wifi", "wi-fi", "wi fi" -> "wifi"
        "bluetooth" -> "bluetooth"
        "hotspot", "mobile hotspot" -> "hotspot"
        "airplane mode", "flight mode", "airplane" -> "airplane"
        "nfc" -> "nfc"
        "location", "gps" -> "location"
        else -> "data"
    }

    suspend fun handle(a: Host, l: String): Automation.Result? {
        // ---- panels
        if (Regex("^(?:open |show |pull down )?(?:quick settings|quick panel|control panel|control center)$").matches(l)) return global(a, AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS, "Quick settings opened.")
        if (Regex("^(?:open |show )?(?:power menu|power off menu|shutdown menu|restart menu)$").matches(l)) return global(a, AccessibilityService.GLOBAL_ACTION_POWER_DIALOG, "Power menu opened. Choose Power off or Restart yourself.")

        // ---- brightness
        Regex("^(?:set |turn )?(?:screen )?brightness(?: to)?\\s*(\\d{1,3})\\s*(?:%|percent)?$").find(l)?.let { return brightness(a, it.groupValues[1].toInt().coerceIn(1, 100)) }
        Regex("^(?:set )?(?:screen )?brightness (max|maximum|full|high|min|minimum|low|auto|automatic)$|^(?:max|full) brightness$|^(?:min|low) brightness$").find(l)?.let { m ->
            val w = (m.groupValues[1].ifEmpty { l.substringBefore(" ") })
            return when (w) {
                "max", "maximum", "full", "high" -> brightness(a, 100)
                "min", "minimum", "low" -> brightness(a, 3)
                else -> autoBrightness(a)
            }
        }

        // ---- rotation
        Regex("^(?:turn |switch )?(on|off) (?:the )?(?:auto[- ]?rotate|auto[- ]?rotation|rotation|screen rotation)$|^(?:turn |switch )?(?:the )?(?:auto[- ]?rotate|auto[- ]?rotation|rotation|screen rotation) (on|off)$").find(l)?.let { m ->
            val on = (m.groupValues[1].ifEmpty { m.groupValues[2] }) == "on"
            if (!Perms.writeSettings(a)) return needWrite(a)
            Settings.System.putInt(a.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (on) 1 else 0)
            return ok("Auto-rotate ${if (on) "on" else "off"}.")
        }

        // ---- do not disturb / ringer
        if (Regex("^(?:turn |switch )?(on|off) (?:do not disturb|dnd)$|^(?:turn |switch )?(?:do not disturb|dnd)(?: mode)?\\s*(on|off)?$|^(?:turn |switch )?(on|off) silent mode$|^silent mode\\s*(on|off)?$").matches(l)) {
            val off = l.contains("off")
            return dnd(a, !off)
        }
        if (Regex("^(?:vibrate|vibration) mode$|^(?:set )?phone (?:to )?vibrate$").matches(l)) return ringer(a, AudioManager.RINGER_MODE_VIBRATE, "Vibrate mode on.")
        if (Regex("^(?:ring|ringer|normal sound|sound) mode$|^(?:unmute|ring) (?:my )?phone$").matches(l)) return ringer(a, AudioManager.RINGER_MODE_NORMAL, "Ringer is back on.")

        // ---- quick-settings toggles through the Quick Settings panel
        val re1 = Regex("^(?:please )?(?:turn |switch )?(on|off|enable|disable|start|stop) (?:the |my )?(wi-?fi|wi fi|bluetooth|mobile hotspot|hotspot|airplane mode|flight mode|nfc|location|gps|mobile data|data)$")
        val re2 = Regex("^(?:please )?(?:turn |switch )?(?:the |my )?(wi-?fi|wi fi|bluetooth|mobile hotspot|hotspot|airplane mode|flight mode|nfc|location|gps|mobile data|data) (on|off)$")
        re1.find(l)?.let { m -> return toggle(a, key(m.groupValues[2]), m.groupValues[1] in listOf("on", "enable", "start")) }
        re2.find(l)?.let { m -> return toggle(a, key(m.groupValues[1]), m.groupValues[2] == "on") }
        return null
    }

    private fun global(a: Host, action: Int, msg: String): Automation.Result {
        val s = A11y.svc() ?: return no(A11y.NEEDS)
        return Automation.Result(s.global(action), msg)
    }

    private fun needWrite(a: Host): Automation.Result {
        try { a.startActivity(Perms.writeSettingsIntent(a)) } catch (e: Exception) {}
        return no("Allow “Modify system settings” for Munawar on the screen that just opened, then say it again.")
    }

    private fun brightness(a: Host, pct: Int): Automation.Result {
        if (!Perms.writeSettings(a)) return needWrite(a)
        Settings.System.putInt(a.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(a.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (pct * 255 / 100).coerceIn(1, 255))
        return ok("Brightness $pct%.")
    }

    private fun autoBrightness(a: Host): Automation.Result {
        if (!Perms.writeSettings(a)) return needWrite(a)
        Settings.System.putInt(a.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
        return ok("Adaptive brightness on.")
    }

    private fun dnd(a: Host, on: Boolean): Automation.Result {
        if (!Perms.dndAccess(a)) {
            try { a.startActivity(Perms.dndIntent()) } catch (e: Exception) {}
            return no("Allow “Do Not Disturb access” for Munawar on the screen that just opened, then say it again.")
        }
        val nm = a.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_NONE else NotificationManager.INTERRUPTION_FILTER_ALL)
        return ok("Do Not Disturb ${if (on) "on" else "off"}.")
    }

    private fun ringer(a: Host, mode: Int, msg: String): Automation.Result = try {
        (a.getSystemService(Context.AUDIO_SERVICE) as AudioManager).ringerMode = mode; ok(msg)
    } catch (e: SecurityException) { no("Android needs Do Not Disturb access for that: allow it in ☰ → Control center.") }

    private suspend fun toggle(a: Host, k: String, want: Boolean): Automation.Result? {
        val s = A11y.svc()
        if (s == null) {
            // without Accessibility: Wi-Fi / Bluetooth fall through to the normal handler (opens their panel)
            if (k == "wifi" || k == "bluetooth") return null
            return no(A11y.NEEDS)
        }
        val labels = tiles[k] ?: return null
        s.global(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
        delay(1100)
        var msg = s.toggleTile(labels, want)
        if (msg == null) { delay(800); msg = s.toggleTile(labels, want) }
        delay(350)
        s.global(AccessibilityService.GLOBAL_ACTION_BACK)
        return if (msg == null) no("I couldn't find the ${labels[0]} tile in Quick Settings. Add it to your Quick Settings panel and try again.")
        else ok("${labels[0]} ${if (want) "on" else "off"} ($msg).")
    }
}
