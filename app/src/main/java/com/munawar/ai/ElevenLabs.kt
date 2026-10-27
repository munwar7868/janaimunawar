package com.munawar.ai

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object ElevenLabs {
    /** Blocking (call on IO). Returns MP3 bytes. */
    fun tts(key: String, voiceId: String, model: String, text: String): ByteArray {
        try {
            val c = URL("https://api.elevenlabs.io/v1/text-to-speech/${voiceId.trim()}").openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.connectTimeout = 15000
            c.readTimeout = 60000
            c.setRequestProperty("xi-api-key", key)
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Accept", "audio/mpeg")
            c.doOutput = true
            val body = JSONObject().put("text", text.take(2500)).put("model_id", model).toString()
            c.outputStream.use { it.write(body.toByteArray()) }
            val code = c.responseCode
            if (code !in 200..299) {
                val err = c.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                val msg = try {
                    val d = JSONObject(err).opt("detail")
                    if (d is JSONObject) d.optString("message") else d?.toString() ?: ""
                } catch (e: Exception) { "" }
                throw GeminiException(
                    when (code) {
                        401 -> "ElevenLabs key rejected."
                        404 -> "ElevenLabs voice ID not found."
                        422 -> "ElevenLabs rejected the request: ${msg.take(120)}"
                        429 -> "ElevenLabs quota/rate limit reached."
                        else -> "ElevenLabs error $code ${msg.take(120)}"
                    }
                )
            }
            return c.inputStream.use { it.readBytes() }
        } catch (e: IOException) {
            throw GeminiException("Network problem reaching ElevenLabs.")
        }
    }
}
