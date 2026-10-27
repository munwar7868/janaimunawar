package com.munawar.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Release(val tag: String, val page: String, val apkUrl: String?, val notes: String)

/** Checks GitHub Releases. Android never allows silent self-install, so we open the download for the user. */
object Updater {
    fun currentVersion(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0"
    } catch (e: Exception) { "0" }

    suspend fun latest(repo: String): Release = withContext(Dispatchers.IO) {
        if (!Regex("^[\\w.-]+/[\\w.-]+$").matches(repo)) throw GeminiException("Set the GitHub repo as owner/name in Settings (e.g. yourname/MunawarAI).")
        try {
            val c = URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection
            c.connectTimeout = 15000; c.readTimeout = 20000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            val code = c.responseCode
            if (code == 404) throw GeminiException("No release found in $repo yet. Push a tag like v0.2.1 to publish one.")
            if (code !in 200..299) throw GeminiException("GitHub error $code")
            val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val assets = j.optJSONArray("assets")
            var apk: String? = null
            if (assets != null) for (i in 0 until assets.length()) {
                val u = assets.getJSONObject(i).optString("browser_download_url")
                if (u.endsWith(".apk")) { apk = u; break }
            }
            Release(j.optString("tag_name"), j.optString("html_url"), apk, j.optString("body").take(400))
        } catch (e: java.io.IOException) {
            throw GeminiException("Network problem reaching GitHub.")
        }
    }

    fun isNewer(tag: String, current: String): Boolean {
        fun parts(s: String) = s.trim().removePrefix("v").split(".", "-").map { it.toIntOrNull() ?: 0 }
        val a = parts(tag); val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
