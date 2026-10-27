package com.munawar.ai

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class GeminiException(msg: String) : Exception(msg)

/** Optional file/image sent inline with the last user message. */
data class Attachment(val mime: String, val b64: String, val name: String = "")

/** Minimal client for the official Gemini REST API (generativelanguage.googleapis.com, v1beta). */
object Gemini {
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta"

    private fun call(method: String, url: String, key: String, body: String?): String {
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.requestMethod = method
            c.connectTimeout = 15000
            c.readTimeout = 90000
            c.setRequestProperty("x-goog-api-key", key) // header, not URL, so it can't leak into logs
            c.setRequestProperty("Content-Type", "application/json")
            if (body != null) {
                c.doOutput = true
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw GeminiException(friendly(code, text))
            return text
        } catch (e: IOException) {
            throw GeminiException("Network problem. Check your internet connection and try again.")
        }
    }

    private fun friendly(code: Int, text: String): String {
        val apiMsg = try {
            JSONObject(text).optJSONObject("error")?.optString("message") ?: ""
        } catch (e: Exception) { "" }
        return when (code) {
            400 -> if (apiMsg.contains("API key", true)) "API key is not valid. Check it in Settings." else "Bad request: ${apiMsg.take(200)}"
            401, 403 -> "API key rejected or lacks permission. Check the key in Settings. (${apiMsg.take(120)})"
            404 -> "Model not found. Pick another model in Settings (use 'Load models')."
            413 -> "File too large for Gemini. Try a smaller file."
            429 -> "Quota or rate limit reached. Wait a bit or check your Gemini API plan."
            in 500..599 -> "Google server error ($code). Try again shortly."
            else -> "Error $code: ${apiMsg.take(200)}"
        }
    }

    suspend fun generate(
        key: String, model: String, temp: Float, system: String,
        turns: List<Turn>, webSearch: Boolean = false, attach: Attachment? = null
    ): String = withContext(Dispatchers.IO) {
        try {
            val contents = JSONArray()
            val list = turns.dropWhile { it.role != "user" }
            list.forEachIndexed { idx, t ->
                val parts = JSONArray().put(JSONObject().put("text", t.text))
                if (attach != null && idx == list.lastIndex && t.role == "user") {
                    parts.put(JSONObject().put("inlineData", JSONObject().put("mimeType", attach.mime).put("data", attach.b64)))
                }
                contents.put(JSONObject().put("role", t.role).put("parts", parts))
            }
            val body = JSONObject()
                .put("contents", contents)
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                .put("generationConfig", genConfig(model, temp))
            if (webSearch) body.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))

            val res = JSONObject(call("POST", "$BASE/models/${model.removePrefix("models/")}:generateContent", key, body.toString()))
            val cand = res.optJSONArray("candidates")?.optJSONObject(0)
            if (cand == null) {
                val reason = res.optJSONObject("promptFeedback")?.optString("blockReason") ?: ""
                throw GeminiException(if (reason.isNotEmpty()) "Request blocked by Gemini safety filter ($reason)." else "Empty response from Gemini.")
            }
            val parts = cand.optJSONObject("content")?.optJSONArray("parts")
            val sb = StringBuilder()
            if (parts != null) for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text", ""))
            val out = sb.toString().trim()
            if (out.isEmpty()) throw GeminiException("Gemini returned no text (finish reason: ${cand.optString("finishReason", "unknown")}).")
            Stats.ok(res.optJSONObject("usageMetadata")?.optInt("totalTokenCount", 0) ?: 0)
            out
        } catch (e: GeminiException) {
            Stats.fail(e.message ?: "error"); throw e
        }
    }

    /** Gemini 3.x works best at its default temperature, so we leave it out for those models. */
    private fun genConfig(model: String, temp: Float): JSONObject =
        if (model.removePrefix("models/").startsWith("gemini-3")) JSONObject() else JSONObject().put("temperature", temp.toDouble())

    private suspend fun post(url: String, key: String, body: String): String = withContext(Dispatchers.IO) { call("POST", url, key, body) }

    private fun actionDecl(): JSONObject = JSONObject()
        .put("name", "phone_action")
        .put("description", Persona.ACTION_SPEC)
        .put(
            "parameters", JSONObject().put("type", "OBJECT")
                .put("properties", JSONObject().put("command", JSONObject().put("type", "STRING").put("description", "One command in the grammar described above")))
                .put("required", JSONArray().put("command"))
        )

    /**
     * Chat with optional phone-action tool calling. [onAction] runs on the caller's thread (main), so it may show dialogs
     * and start activities. Loop: model -> tool call -> result -> model, up to 5 steps.
     */
    suspend fun chat(
        key: String, model: String, temp: Float, system: String, turns: List<Turn>,
        webSearch: Boolean, onAction: (suspend (String) -> String)?
    ): String {
        try {
            val contents = JSONArray()
            for (t in turns.dropWhile { it.role != "user" }) {
                contents.put(JSONObject().put("role", t.role).put("parts", JSONArray().put(JSONObject().put("text", t.text))))
            }
            val tools: JSONArray? = when {
                webSearch -> JSONArray().put(JSONObject().put("google_search", JSONObject()))
                onAction != null -> JSONArray().put(JSONObject().put("functionDeclarations", JSONArray().put(actionDecl())))
                else -> null
            }
            val url = "$BASE/models/${model.removePrefix("models/")}:generateContent"
            var lastAction = ""
            for (step in 0 until 5) {
                val body = JSONObject().put("contents", contents)
                    .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                    .put("generationConfig", genConfig(model, temp))
                if (tools != null) body.put("tools", tools)
                val res = JSONObject(post(url, key, body.toString()))
                val cand = res.optJSONArray("candidates")?.optJSONObject(0)
                if (cand == null) {
                    val reason = res.optJSONObject("promptFeedback")?.optString("blockReason") ?: ""
                    throw GeminiException(if (reason.isNotEmpty()) "Request blocked by Gemini safety filter ($reason)." else "Empty response from Gemini.")
                }
                val content = cand.optJSONObject("content")
                val parts = content?.optJSONArray("parts") ?: JSONArray()
                val calls = ArrayList<JSONObject>()
                val sb = StringBuilder()
                for (i in 0 until parts.length()) {
                    val p = parts.getJSONObject(i)
                    p.optJSONObject("functionCall")?.let { calls.add(it) }
                    if (p.has("text") && !p.optBoolean("thought", false)) sb.append(p.optString("text"))
                }
                Stats.ok(res.optJSONObject("usageMetadata")?.optInt("totalTokenCount", 0) ?: 0)
                if (calls.isEmpty() || onAction == null) {
                    val out = sb.toString().trim()
                    if (out.isNotEmpty()) return out
                    if (lastAction.isNotEmpty()) return lastAction
                    throw GeminiException("Gemini returned no text (finish reason: ${cand.optString("finishReason", "unknown")}).")
                }
                contents.put(content) // keep the raw model turn (includes thought signatures)
                val respParts = JSONArray()
                for (c in calls) {
                    val cmd = c.optJSONObject("args")?.optString("command") ?: ""
                    val r = try { onAction(cmd) } catch (e: Exception) { "Failed: ${e.message}" }
                    lastAction = r
                    val fr = JSONObject().put("name", c.optString("name")).put("response", JSONObject().put("result", r))
                    if (c.optString("id").isNotEmpty()) fr.put("id", c.optString("id"))
                    respParts.put(JSONObject().put("functionResponse", fr))
                }
                contents.put(JSONObject().put("role", "user").put("parts", respParts))
            }
            return lastAction.ifEmpty { throw GeminiException("Too many steps.") }
        } catch (e: GeminiException) {
            Stats.fail(e.message ?: "error"); throw e
        }
    }

    /** Image generation with a Gemini image model. Returns (image bytes, caption). */
    suspend fun generateImage(key: String, model: String, prompt: String): Pair<ByteArray, String> {
        try {
            val body = JSONObject()
                .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
                .put("generationConfig", JSONObject().put("responseModalities", JSONArray().put("TEXT").put("IMAGE")))
            val res = JSONObject(post("$BASE/models/${model.removePrefix("models/")}:generateContent", key, body.toString()))
            val parts = res.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            var img: ByteArray? = null
            val cap = StringBuilder()
            if (parts != null) for (i in 0 until parts.length()) {
                val p = parts.getJSONObject(i)
                val d = p.optJSONObject("inlineData")
                if (d != null && img == null) img = Base64.decode(d.getString("data"), Base64.DEFAULT)
                if (p.has("text")) cap.append(p.optString("text"))
            }
            Stats.ok(res.optJSONObject("usageMetadata")?.optInt("totalTokenCount", 0) ?: 0)
            return Pair(img ?: throw GeminiException("No image returned. ${cap.toString().take(160).ifBlank { "Try a different prompt or an image model from Settings." }}"), cap.toString().trim())
        } catch (e: GeminiException) {
            Stats.fail(e.message ?: "error"); throw e
        }
    }

    /** Lists models supporting [method] ("generateContent" or "bidiGenerateContent" for Live). Names are never guessed. */
    suspend fun listModels(key: String, method: String = "generateContent"): List<String> = withContext(Dispatchers.IO) {
        val res = JSONObject(call("GET", "$BASE/models?pageSize=200", key, null))
        val arr = res.optJSONArray("models") ?: JSONArray()
        val out = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val m = arr.getJSONObject(i)
            val methods = m.optJSONArray("supportedGenerationMethods") ?: continue
            var ok = false
            for (j in 0 until methods.length()) if (methods.getString(j) == method) ok = true
            val name = m.optString("name").removePrefix("models/")
            if (ok && name.startsWith("gemini")) out.add(name)
        }
        out.sorted()
    }

    suspend fun test(key: String, model: String): String =
        generate(key, model, 0f, "Reply with the single word OK.", listOf(Turn("user", "ping")))
}
