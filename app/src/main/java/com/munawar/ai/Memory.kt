package com.munawar.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Turn(val role: String, val text: String) // role: "user" | "model"
data class Memory(val id: Long, var text: String, val ts: Long)

/** User-approved memories, stored locally in app-private storage (separate from the API key). */
class MemoryStore(ctx: Context) {
    private val f = File(ctx.filesDir, "memories.json")
    private val items = mutableListOf<Memory>()

    init {
        try {
            if (f.exists()) {
                val a = JSONArray(f.readText())
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    items.add(Memory(o.getLong("id"), o.getString("text"), o.getLong("ts")))
                }
            }
        } catch (e: Exception) { items.clear() }
    }

    private fun save() {
        val a = JSONArray()
        items.forEach { a.put(JSONObject().put("id", it.id).put("text", it.text).put("ts", it.ts)) }
        f.writeText(a.toString())
    }

    @Synchronized fun all(): List<Memory> = items.toList()
    @Synchronized fun add(t: String) { items.add(Memory(System.currentTimeMillis(), t.trim(), System.currentTimeMillis())); save() }
    @Synchronized fun update(id: Long, t: String) { items.find { it.id == id }?.text = t.trim(); save() }
    @Synchronized fun delete(id: Long) { items.removeAll { it.id == id }; save() }
    @Synchronized fun clear() { items.clear(); save() }

    @Synchronized fun exportJson(): String {
        val a = JSONArray()
        items.forEach { a.put(JSONObject().put("id", it.id).put("text", it.text).put("ts", it.ts)) }
        return a.toString()
    }

    /** Merges memories from a backup; skips ones already present. Returns how many were added. */
    @Synchronized fun importJson(json: String): Int {
        var n = 0
        val a = JSONArray(json)
        for (i in 0 until a.length()) {
            val t = a.getJSONObject(i).optString("text").trim()
            if (t.isNotEmpty() && items.none { it.text == t }) {
                items.add(Memory(System.currentTimeMillis() + i, t, System.currentTimeMillis())); n++
            }
        }
        save()
        return n
    }
}

/** Persistent chat transcript per screen key ("chat", "code", "call"). */
class ChatHistory(ctx: Context, key: String) {
    private val f = File(ctx.filesDir, "history_$key.json")
    private val items = mutableListOf<Turn>()

    init {
        try {
            if (f.exists()) {
                val a = JSONArray(f.readText())
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    items.add(Turn(o.getString("r"), o.getString("t")))
                }
            }
        } catch (e: Exception) { items.clear() }
    }

    private fun save() {
        val a = JSONArray()
        items.forEach { a.put(JSONObject().put("r", it.role).put("t", it.text)) }
        f.writeText(a.toString())
    }

    @Synchronized fun add(t: Turn) { items.add(t); while (items.size > 100) items.removeAt(0); save() }
    @Synchronized fun removeLast() { if (items.isNotEmpty()) { items.removeAt(items.size - 1); save() } }
    @Synchronized fun recent(n: Int): List<Turn> = items.takeLast(n)
    @Synchronized fun all(): List<Turn> = items.toList()
    @Synchronized fun clear() { items.clear(); save() }
}
