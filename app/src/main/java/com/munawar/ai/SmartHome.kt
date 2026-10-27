package com.munawar.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Smart home via the Home Assistant REST API (you provide URL + long-lived token + device list in Settings).
 * Device list format, one per line:  bedroom light = light.bedroom
 */
object SmartHome {
    fun devices(p: Prefs): Map<String, String> = p.haDevices.lines().mapNotNull {
        val i = it.indexOf('=')
        if (i > 0) it.substring(0, i).trim().lowercase() to it.substring(i + 1).trim() else null
    }.filter { it.first.isNotEmpty() && it.second.isNotEmpty() }.toMap()

    suspend fun handle(p: Prefs, l: String): Automation.Result? {
        val devs = devices(p)
        if (devs.isEmpty()) return null
        val name = devs.keys.filter { l.contains(it) }.maxByOrNull { it.length } ?: return null
        val action = when {
            Regex("\\b(off|band|bujha|bujhao|bandh)\\b").containsMatchIn(l) -> "turn_off"
            Regex("\\b(on|chalu|jalao|jala|chala)\\b").containsMatchIn(l) -> "turn_on"
            l.contains("toggle") -> "toggle"
            else -> return null
        }
        val token = p.secret("ha")
        if (p.haUrl.isBlank() || token == null) return Automation.Result(false, "Set your Home Assistant URL and token in Settings → Smart Home.")
        val entity = devs[name]!!
        return withContext(Dispatchers.IO) {
            try {
                val c = URL("${p.haUrl}/api/services/homeassistant/$action").openConnection() as HttpURLConnection
                c.requestMethod = "POST"; c.connectTimeout = 8000; c.readTimeout = 10000
                c.setRequestProperty("Authorization", "Bearer $token")
                c.setRequestProperty("Content-Type", "application/json")
                c.doOutput = true
                c.outputStream.use { it.write(JSONObject().put("entity_id", entity).toString().toByteArray()) }
                val code = c.responseCode
                if (code in 200..299) Automation.Result(true, "Home Assistant accepted: $action for \"$name\".")
                else Automation.Result(false, "Home Assistant replied $code (${if (code == 401) "token rejected" else "check entity id"}).")
            } catch (e: Exception) {
                Automation.Result(false, "Couldn't reach Home Assistant at ${p.haUrl}. Same Wi-Fi? Correct URL?")
            }
        }
    }
}
