package com.munawar.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Tiny blocking HTTP helper (call from Dispatchers.IO). Throws Exception with a readable message. */
object Net {
    fun request(method: String, url: String, headers: Map<String, String> = emptyMap(), body: String? = null, timeoutMs: Int = 60000): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 15000
            c.readTimeout = timeoutMs
            for ((k, v) in headers) c.setRequestProperty(k, v)
            if (body != null) {
                c.doOutput = true
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw Exception("HTTP $code: ${text.take(180)}")
            return text
        } finally { c.disconnect() }
    }
}

/** Optional third-party connectors. Every key is typed by the user and stored encrypted (Settings → Connectors). */
object Connectors {
    suspend fun githubRepos(p: Prefs): String = withContext(Dispatchers.IO) {
        val tok = p.secret("github")
        val user = p.githubUser
        if (tok == null && user.isBlank()) return@withContext "Add your GitHub username (and optionally a token) in Settings → Connectors."
        val url = if (tok != null) "https://api.github.com/user/repos?per_page=15&sort=updated" else "https://api.github.com/users/$user/repos?per_page=15&sort=updated"
        val h = HashMap<String, String>()
        h["Accept"] = "application/vnd.github+json"
        h["User-Agent"] = "MunawarAI"
        if (tok != null) h["Authorization"] = "Bearer $tok"
        val arr = JSONArray(Net.request("GET", url, h))
        if (arr.length() == 0) return@withContext "No repositories found."
        val sb = StringBuilder("Your latest repositories:\n")
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            sb.append("• ").append(r.optString("full_name")).append(if (r.optBoolean("private")) " (private)" else "").append('\n')
        }
        sb.toString().trim()
    }

    suspend fun telegram(p: Prefs, text: String): String = withContext(Dispatchers.IO) {
        val tok = p.secret("telegram")
        if (tok == null || p.telegramChat.isBlank()) return@withContext "Add your Telegram bot token and chat id in Settings → Connectors."
        val body = JSONObject().put("chat_id", p.telegramChat).put("text", text).toString()
        Net.request("POST", "https://api.telegram.org/bot$tok/sendMessage", mapOf("Content-Type" to "application/json"), body)
        "Sent to Telegram: \"$text\""
    }

    private suspend fun tavily(key: String, q: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject().put("query", q).put("max_results", 5).put("include_answer", true).toString()
        val res = JSONObject(Net.request("POST", "https://api.tavily.com/search", mapOf("Content-Type" to "application/json", "Authorization" to "Bearer $key"), body))
        val sb = StringBuilder()
        val ans = res.optString("answer")
        if (ans.isNotBlank() && ans != "null") sb.append("Answer: ").append(ans).append("\n\n")
        val arr = res.optJSONArray("results") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            sb.append("- ").append(r.optString("title")).append(" (").append(r.optString("url")).append("): ").append(r.optString("content").take(300)).append('\n')
        }
        sb.toString()
    }

    /** Deep research: Tavily results (if a key is saved) summarised by Gemini, otherwise Gemini with Google Search grounding. */
    suspend fun research(p: Prefs, topic: String): String {
        val key = p.apiKey() ?: return "Add your Gemini API key in Settings first."
        val tav = p.secret("tavily")
        val sys = "You are a research assistant inside Munawar AI. Write a clear, well-organised summary with 5-8 key findings and a short conclusion. " +
            "Mention source names or links when you have them. Say honestly if information is uncertain. Reply in the user's language."
        return if (tav != null) {
            val found = try { tavily(tav, topic) } catch (e: Exception) { "" }
            val prompt = if (found.isBlank()) topic else "Topic: $topic\n\nWeb search results:\n$found"
            Gemini.generate(key, p.model, 0.4f, sys, listOf(Turn("user", prompt)), found.isBlank())
        } else Gemini.generate(key, p.model, 0.4f, sys, listOf(Turn("user", topic)), true)
    }

    /** Ask another AI provider (Groq / OpenAI / Claude) through its own API key. */
    suspend fun altChat(p: Prefs, prompt: String, provider: String = p.altProvider): String = withContext(Dispatchers.IO) {
        val prov = provider
        val key = p.secret(prov) ?: return@withContext "Add your $prov API key in Settings → Connectors."
        when (prov) {
            "claude" -> {
                val model = p.altModel.ifBlank { "claude-sonnet-5-5" }
                val body = JSONObject().put("model", model).put("max_tokens", 1024)
                    .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt))).toString()
                val res = JSONObject(Net.request("POST", "https://api.anthropic.com/v1/messages",
                    mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01", "Content-Type" to "application/json"), body))
                val arr = res.optJSONArray("content") ?: JSONArray()
                val sb = StringBuilder()
                for (i in 0 until arr.length()) sb.append(arr.getJSONObject(i).optString("text"))
                sb.toString().trim().ifEmpty { "Empty reply." }
            }
            else -> {
                val url = if (prov == "openai") "https://api.openai.com/v1/chat/completions" else "https://api.groq.com/openai/v1/chat/completions"
                val model = p.altModel.ifBlank { if (prov == "openai") "gpt-4o-mini" else "llama-3.3-70b-versatile" }
                val body = JSONObject().put("model", model)
                    .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt))).toString()
                val res = JSONObject(Net.request("POST", url, mapOf("Authorization" to "Bearer $key", "Content-Type" to "application/json"), body))
                res.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")?.trim()?.ifEmpty { null } ?: "Empty reply."
            }
        }
    }
}
