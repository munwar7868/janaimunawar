package com.munawar.ai

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

interface Screen {
    val view: View
    fun onShow() {}
    fun onHide() {}
}

class MainActivity : Activity() {
    val prefs: Prefs get() = Core.prefs
    val memory: MemoryStore get() = Core.memory
    val notes: NotesStore get() = Core.notes
    val speaker: Speaker get() = Core.speaker
    val chatEngine: Engine get() = Core.chatEngine
    val codeEngine: Engine get() = Core.codeEngine
    val callEngine: Engine get() = Core.callEngine
    val scope get() = Core.scope
    val orbs = ArrayList<OrbView>()
    val host: ActivityHost by lazy { ActivityHost(this) }

    private lateinit var aurora: AuroraView
    private lateinit var container: FrameLayout
    private lateinit var topBar: LinearLayout
    private lateinit var miniOrb: OrbView
    private lateinit var titleView: TextView
    private lateinit var wakeBtn: Button
    private lateinit var wakeStatus: TextView
    private lateinit var edgeView: EdgeLightView
    private lateinit var scrim: View
    private lateinit var drawer: LinearLayout
    private val drawerItems = LinkedHashMap<String, Button>()
    private var drawerW = 0
    private var setup: SetupScreen? = null
    private val screens = LinkedHashMap<String, Screen>()
    private var current: String? = null
    private var nextCode = 1000
    private val permCbs = HashMap<Int, (Boolean) -> Unit>()
    private val resultCbs = HashMap<Int, (Int, Intent?) -> Unit>()

    private val menu = listOf(
        "#ASSISTANT" to "",
        "Home" to "🏠  Home (talk)", "Chat" to "💬  Chat", "Phone" to "📱  Phone commands",
        "#TOOLS" to "",
        "Features" to "✨  Features (all tools)", "Files" to "📁  Files & photos", "Tools" to "🧰  Tools & images",
        "Notes" to "📝  Notes & tasks", "Code" to "💻  Code assistant", "Memory" to "🧠  Memory",
        "#AUTOMATION" to "",
        "Triggers" to "⚡  Automation & triggers",
        "#SYSTEM" to "",
        "Control" to "🚀  Control center & permissions", "Settings" to "⚙  Settings", "About" to "🔒  About & privacy"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Core.init(this)
        window.statusBarColor = Theme.BG
        window.navigationBarColor = Theme.BG
        if (Theme.isLight) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or (if (Build.VERSION.SDK_INT >= 26) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
        }
        try { Triggers.startLive(this); Triggers.scheduleTimes(this) } catch (e: Exception) {}

        val rootFrame = FrameLayout(this)
        aurora = AuroraView(this).also { it.lite = prefs.liteMode }
        rootFrame.addView(aurora, FrameLayout.LayoutParams(MATCH, MATCH))

        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        topBar = hbox().apply {
            setPadding(dp(8), dp(6), dp(10), dp(6))
            background = glassBg(Theme.CARD, dp(28).toFloat(), 1); elevation = dp(4).toFloat()
        }
        topBar.addView(iconButton("☰") { openDrawer() }, lp(dp(44), dp(44), m = 2))
        miniOrb = OrbView(this).also { orbs.add(it); it.lite = prefs.liteMode }
        miniOrb.setOnClickListener { startCall() }
        topBar.addView(miniOrb, lp(dp(48), dp(48), m = 2))
        val titles = vbox()
        titleView = RainbowText(this, "Munawar AI", 18f)
        wakeStatus = label("", 10f, Theme.MUTED)
        titles.addView(titleView); titles.addView(wakeStatus)
        topBar.addView(titles, lp(0, WRAP, 1f, 4))
        wakeBtn = button("👂 OFF", Theme.CARD) { setWake(!prefs.wakeEnabled) }
        wakeBtn.textSize = 12f
        topBar.addView(wakeBtn, lp(WRAP, WRAP))
        column.addView(topBar, lp(MATCH, WRAP, m = 8))
        container = FrameLayout(this)
        column.addView(container, LinearLayout.LayoutParams(MATCH, 0, 1f))
        rootFrame.addView(column, FrameLayout.LayoutParams(MATCH, MATCH))

        // ---- slide-in menu (☰)
        scrim = View(this).apply { setBackgroundColor(0x99000000.toInt()); visibility = View.GONE; setOnClickListener { closeDrawer() } }
        rootFrame.addView(scrim, FrameLayout.LayoutParams(MATCH, MATCH))
        drawerW = dp(290)
        drawer = vbox().apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(mix(Theme.BG, Theme.PURPLE, 0.22f), Theme.BG, mix(Theme.BG, Theme.CYAN, 0.10f)))
            visibility = View.GONE; setPadding(dp(14), dp(28), dp(14), dp(14)); elevation = dp(16).toFloat()
            isClickable = true
        }
        drawer.addView(ImageView(this).apply { setImageResource(R.drawable.orb_logo) }, lp(dp(72), dp(72)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        drawer.addView(RainbowText(this, "MUNAWAR AI", 20f).apply { gravity = Gravity.CENTER })
        drawer.addView(label("Created by ${Persona.CREATOR}", 11f, Theme.MUTED).apply { gravity = Gravity.CENTER })
        drawer.addView(label("📞 ${Persona.DEV_PHONE}", 11f, Theme.MUTED).apply { gravity = Gravity.CENTER })
        val menuBox = vbox()
        menu.forEach { (key, text) ->
            if (key.startsWith("#")) menuBox.addView(label(key.substring(1), 11f, Theme.MUTED, true).apply { setPadding(dp(6), dp(12), 0, dp(2)) })
            else {
                val b = button(text, Theme.CARD) { closeDrawer(); show(key) }.apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }
                drawerItems[key] = b
                menuBox.addView(b, lp(MATCH, WRAP, m = 2))
            }
        }
        menuBox.addView(label("APPEARANCE", 11f, Theme.MUTED, true).apply { setPadding(dp(6), dp(12), 0, dp(2)) })
        menuBox.addView(button(if (Theme.isLight) "🌙  Switch to dark theme" else "☀  Switch to white theme", Theme.CARD) {
            prefs.uiTheme = if (Theme.isLight) "navy" else "white"; prefs.accentHex = ""; prefs.bgHex = ""; Theme.apply(prefs); recreate()
        }.apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }, lp(MATCH, WRAP, m = 2))
        drawer.addView(android.widget.ScrollView(this).apply { addView(menuBox) }, LinearLayout.LayoutParams(MATCH, 0, 1f))
        rootFrame.addView(drawer, FrameLayout.LayoutParams(drawerW, MATCH))
        edgeView = EdgeLightView(this).apply { visibility = View.GONE; isClickable = false; isFocusable = false; lightOn = prefs.edgeLight; thicknessDp = prefs.edgeThickness; speed = prefs.edgeSpeed; glow = prefs.edgeGlowPct / 100f; setAnimStyle(prefs.edgeStyle); setPalette(if (prefs.edgeColors == "accent") intArrayOf(Theme.PURPLE, Theme.CYAN, Theme.PINK, Theme.PURPLE) else if (prefs.edgeColors == "orb") OrbDesigns.colors(prefs.orbPal) else intArrayOf(Color.parseColor("#22D3EE"), Color.parseColor("#8B5CF6"), Color.parseColor("#F472B6"), Color.parseColor("#22D3EE"))) }
        rootFrame.addView(edgeView, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(rootFrame)

        refreshWakeUi()
        if (!prefs.onboarded) showSetup() else show("Home")
        handleIntent(intent)
    }

    // ---------- menu ----------
    private fun openDrawer() {
        for ((k, b) in drawerItems) {
            val on = k == current
            b.background = if (on) pillBg(Theme.PURPLE, dp(Theme.corner + 10).toFloat()) else glassBg(Theme.CARD, dp(Theme.corner + 10).toFloat(), 1)
            b.setTextColor(if (on) Color.WHITE else Theme.TEXT)
        }
        scrim.visibility = View.VISIBLE; scrim.alpha = 0f; scrim.animate().alpha(1f).setDuration(160).start()
        drawer.visibility = View.VISIBLE; drawer.translationX = -drawerW.toFloat()
        drawer.animate().translationX(0f).setDuration(200).start()
    }

    private fun closeDrawer() {
        if (drawer.visibility != View.VISIBLE) return
        drawer.animate().translationX(-drawerW.toFloat()).setDuration(160).withEndAction { drawer.visibility = View.GONE }.start()
        scrim.animate().alpha(0f).setDuration(160).withEndAction { scrim.visibility = View.GONE }.start()
    }

    // ---------- first-run setup ----------
    private fun showSetup() {
        topBar.visibility = View.GONE
        val s = SetupScreen(this)
        setup = s
        container.removeAllViews()
        container.addView(s.view, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    fun finishSetup() {
        prefs.onboarded = true
        setup = null
        topBar.visibility = View.VISIBLE
        show("Home")
    }

    // ---------- orb (all orbs share one state) ----------
    /** Start the background Gemini conversation (keeps running when the app is minimised or closed). */
    fun startCall() {
        show("Home")
        requestPerm(Manifest.permission.RECORD_AUDIO) { ok ->
            if (!ok) { toast("Microphone permission is needed to talk to Munawar."); return@requestPerm }
            val go: () -> Unit = {
                AssistantService.send(this, AssistantService.A_SESSION)
                if (!Perms.overlay(this) && !prefs.overlayAsked) { prefs.overlayAsked = true; overlayDialog() }
            }
            if (Build.VERSION.SDK_INT >= 33 && !Perms.has(this, Manifest.permission.POST_NOTIFICATIONS)) requestPerm(Manifest.permission.POST_NOTIFICATIONS) { go() } else go()
        }
    }

    private fun overlayDialog() {
        AlertDialog.Builder(this).setTitle("Allow “Display over other apps”?")
            .setMessage("With it, the colourful screen-edge light shows over every app, the cancel-card for calls/messages appears over other apps, and Munawar can open apps (YouTube, WhatsApp…) while you are in another app.")
            .setPositiveButton("Allow") { _, _ -> try { startActivity(Perms.overlayIntent(this)) } catch (e: Exception) {} }
            .setNegativeButton("Not now", null).show()
    }
    fun orbState(s: OrbView.S) { orbs.forEach { it.state = s } }
    fun orbLevel(l: Float) { orbs.forEach { it.level = l } }
    private fun orbStandby(on: Boolean) { orbs.forEach { it.standby = on } }

    /** Speak a reply with the orb in SPEAKING state. */
    fun say(text: String) {
        if (!prefs.speak || AssistantBus.sessionOn) return
        orbState(OrbView.S.SPEAKING)
        speaker.speak(text) { if (!AssistantBus.sessionOn) orbState(OrbView.S.IDLE) }
    }

    fun generateImage(prompt: String) { show("Tools"); (screens["Tools"] as? ToolsScreen)?.generate(prompt) }

    /** Opens the Tools screen with a specific tool preselected (website, image, research, gamecoach, phonehealth…). */
    fun openTool(id: String) { show("Tools"); (screens["Tools"] as? ToolsScreen)?.select(id) }

    // ---------- wake word ("Hey Munawar", runs in the background service) ----------
    fun refreshWakeUi() {
        wakeBtn.text = if (prefs.wakeEnabled) "👂 ON" else "👂 OFF"
        wakeBtn.background = roundBg(if (prefs.wakeEnabled) Theme.PURPLE else Theme.CARD, dp(24).toFloat())
        orbStandby(prefs.wakeEnabled)
        (screens["Control"] as? ControlScreen)?.onShow()
    }

    fun setWake(on: Boolean) {
        if (!on) {
            prefs.wakeEnabled = false
            AssistantService.send(this, AssistantService.A_WAKE_OFF); refreshWakeUi(); return
        }
        requestPerm(Manifest.permission.RECORD_AUDIO) { ok ->
            if (!ok) { toast("Microphone permission is needed for “Hey Munawar”."); return@requestPerm }
            val go: () -> Unit = {
                prefs.wakeEnabled = true
                AssistantService.send(this, AssistantService.A_WAKE_ON); refreshWakeUi()
                if (!Perms.batteryIgnored(this)) toast("Tip: ☰ → Control → Battery → Unrestricted, so Android doesn't stop background listening.")
            }
            if (Build.VERSION.SDK_INT >= 33) requestPerm(Manifest.permission.POST_NOTIFICATIONS) { go() } else go()
        }
    }

    // ---------- live state from the background service ----------
    private val busListener: () -> Unit = {
        orbState(AssistantBus.state); orbLevel(AssistantBus.level); orbStandby(AssistantBus.standby || prefs.wakeEnabled)
        edgeView.state = AssistantBus.state; edgeView.level = AssistantBus.level
        val showEdge = AssistantBus.sessionOn && prefs.edgeLight && !EdgeLight.fullShown
        if ((edgeView.visibility == View.VISIBLE) != showEdge) edgeView.visibility = if (showEdge) View.VISIBLE else View.GONE
        val ws = when {
            AssistantBus.sessionOn -> "In conversation"
            AssistantBus.standby -> "Listening for “Hey Munawar”…"
            else -> ""
        }
        if (wakeStatus.text != ws) wakeStatus.text = ws
    }

    // ---------- navigation ----------
    private fun create(name: String): Screen = when (name) {
        "Home" -> HomeScreen(this)
        "Chat" -> ChatScreen(this, false)
        "Code" -> ChatScreen(this, true)
        "Tools" -> ToolsScreen(this)
        "Phone" -> PhoneScreen(this)
        "Notes" -> NotesScreen(this)
        "Memory" -> MemoryScreen(this)
        "Control" -> ControlScreen(this)
        "Settings" -> SettingsScreen(this)
        "Features" -> FeaturesScreen(this)
        "Files" -> FilesScreen(this)
        "Triggers" -> TriggersScreen(this)
        else -> AboutScreen(this)
    }

    fun show(name: String) {
        closeDrawer()
        current?.let { screens[it]?.onHide() }
        Motion.reset()
        val s = screens.getOrPut(name) { create(name) }
        container.removeAllViews()
        val v = s.view
        container.addView(v, FrameLayout.LayoutParams(MATCH, MATCH))
        val lvl = prefs.uiAnim
        if (lvl == 0) { v.alpha = 1f; v.translationX = 0f; v.scaleX = 1f; v.scaleY = 1f }
        else {
            v.alpha = 0f; v.translationX = dp(if (lvl == 2) 44 else 24).toFloat()
            if (lvl == 2) { v.scaleX = 0.96f; v.scaleY = 0.96f }
            v.animate().alpha(1f).translationX(0f).scaleX(1f).scaleY(1f).setDuration(if (lvl == 2) 340L else 170L).start()
        }
        current = name
        val onHome = name == "Home"
        miniOrb.visibility = if (onHome) View.GONE else View.VISIBLE
        titleView.text = menu.firstOrNull { it.first == name && it.second.length > 3 }?.second?.substring(3)?.trim() ?: "Munawar AI"
        if (onHome) titleView.text = "Munawar AI"
        s.onShow()
    }

    private fun handleIntent(i: Intent?) {
        if (i == null || !prefs.onboarded) return
        val cmd = i.getStringExtra("cmd")
        val auto = i.getBooleanExtra("autocall", false)
        val img = i.getStringExtra("genimage")
        val scr = i.getStringExtra("screen")
        i.removeExtra("cmd"); i.removeExtra("autocall"); i.removeExtra("genimage"); i.removeExtra("screen")
        if (!scr.isNullOrBlank()) show(scr)
        else if (!img.isNullOrBlank()) generateImage(img)
        else if (auto) startCall()
        else if (!cmd.isNullOrBlank()) { show("Chat"); (screens["Chat"] as? ChatScreen)?.submit(cmd) }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    fun requestPerm(perm: String, cb: (Boolean) -> Unit) = requestAnyPerm(arrayOf(perm), cb)

    /** Asks for the permissions together; cb(true) if at least one is granted (e.g. fine OR approximate location). */
    /** Asks for every runtime permission that is still missing, in one go. */
    fun requestAllPerms(cb: () -> Unit) {
        val need = Perms.allRuntime().filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (need.isEmpty()) { cb(); return }
        Guardian.expectPermission()
        val code = nextCode++
        permCbs[code] = { _ -> cb() }
        requestPermissions(need.toTypedArray(), code)
    }

    /** Opens the next special-access screen (overlay, notification access, accessibility, battery, system settings, DND). */
    fun openNextSpecial() {
        val n = Perms.nextSpecial(this)
        if (n == null) { toast("✔ Everything is allowed."); return }
        toast("Allow: ${n.first}")
        try { startActivity(n.second); Guardian.assistGrant(host, n.first) } catch (e: Exception) { toast("Couldn't open that screen.") }
    }

    fun requestAnyPerm(perms: Array<String>, cb: (Boolean) -> Unit) {
        if (perms.any { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) { cb(true); return }
        val code = nextCode++
        permCbs[code] = { ok ->
            if (!ok && perms.none { shouldShowRequestPermissionRationale(it) }) blockedDialog()
            cb(ok)
        }
        Guardian.expectPermission()
        requestPermissions(perms, code)
    }

    private fun blockedDialog() {
        AlertDialog.Builder(this).setTitle("Permission blocked")
            .setMessage("Android won't show this permission prompt again. Open the app's settings → Permissions and allow it there.")
            .setPositiveButton("Open settings") { _, _ -> try { startActivity(Perms.appInfoIntent(this)) } catch (e: Exception) {} }
            .setNegativeButton("Not now", null).show()
    }

    @Suppress("DEPRECATION")
    fun startForResult(i: Intent, cb: (Int, Intent?) -> Unit) {
        val code = nextCode++
        resultCbs[code] = cb
        try { startActivityForResult(i, code) } catch (e: Exception) { resultCbs.remove(code); toast("Not available on this phone.") }
    }

    @Suppress("DEPRECATION")
    fun startSenderForResult(sender: android.content.IntentSender, cb: (Int, Intent?) -> Unit) {
        val code = nextCode++
        resultCbs[code] = cb
        try { startIntentSenderForResult(sender, code, null, 0, 0, 0) } catch (e: Exception) { resultCbs.remove(code); toast("Not available on this phone.") }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permCbs.remove(requestCode)?.invoke(grantResults.any { it == PackageManager.PERMISSION_GRANTED })
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        resultCbs.remove(requestCode)?.invoke(resultCode, data)
    }

    override fun onResume() {
        super.onResume()
        visibleHost = host
        aurora.paused = false; aurora.invalidate()
        if (!AssistantBus.listeners.contains(busListener)) AssistantBus.listeners.add(busListener)
        busListener()
        if (prefs.wakeEnabled && !AssistantService.running && Perms.has(this, Manifest.permission.RECORD_AUDIO)) AssistantService.send(this, AssistantService.A_WAKE_ON)
        setup?.onShow()
        current?.let { screens[it]?.onShow() }
    }

    override fun onPause() {
        super.onPause()
        visibleHost = null
        aurora.paused = true
        AssistantBus.listeners.remove(busListener)
        (screens["Home"] as? HomeScreen)?.pauseAnim()
        speaker.stop()          // chat replies only; the background conversation keeps running
        orbState(OrbView.S.IDLE); orbLevel(0f)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        when {
            drawer.visibility == View.VISIBLE -> closeDrawer()
            prefs.onboarded && current != "Home" -> show("Home")
            else -> super.onBackPressed()
        }
    }

    override fun onDestroy() {
        screens.values.forEach { it.onHide() }
        super.onDestroy()
    }

    companion object { @Volatile var visibleHost: ActivityHost? = null }
}
