package com.munawar.ai

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Process-wide objects shared by the UI and the background service (so the assistant keeps working with the app closed). */
object Core {
    @Volatile private var inited = false
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    lateinit var appCtx: Context
    lateinit var prefs: Prefs
    lateinit var memory: MemoryStore
    lateinit var notes: NotesStore
    lateinit var speaker: Speaker
    lateinit var chatEngine: Engine
    lateinit var codeEngine: Engine
    lateinit var callEngine: Engine

    @Synchronized
    fun init(ctx: Context) {
        if (inited) return
        val app = ctx.applicationContext
        appCtx = app
        Stats.init(app)
        prefs = Prefs(app)
        try { Theme.apply(prefs) } catch (e: Exception) {}
        try { Hush.rescue(app) } catch (e: Exception) {}
        A11y.allowed = prefs.a11yAllowed
        memory = MemoryStore(app)
        notes = NotesStore(app)
        speaker = Speaker(app, prefs)
        chatEngine = Engine(app, prefs, memory, "chat")
        codeEngine = Engine(app, prefs, memory, "code", coding = true)
        callEngine = Engine(app, prefs, memory, "call", voice = true)
        val act: suspend (String) -> String = { cmd ->
            val r = Commands.run(Hosts.get(), cmd, false)
            if (r == null) {
                val g = Agent.run(Hosts.get(), cmd)
                (if (g.ok) "" else "FAILED: ") + g.message
            } else (if (r.ok) "" else "FAILED: ") + r.message
        }
        chatEngine.actions = act
        callEngine.actions = act
        inited = true
        try { Watchdog.schedule(app) } catch (e: Exception) {}
    }
}

object Hosts {
    private var bg: BackgroundHost? = null

    /** The on-screen host when the app is visible, otherwise a background host (notifications / overlay instead of dialogs). */
    fun get(): Host = MainActivity.visibleHost ?: (bg ?: BackgroundHost(Core.appCtx).also { bg = it })
}
