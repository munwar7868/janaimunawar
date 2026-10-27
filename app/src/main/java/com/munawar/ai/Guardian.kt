package com.munawar.ai

import android.content.Context
import android.os.PowerManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The last few things Munawar did or noticed by itself (shown on the Home screen). */
object Feed {
    val items = java.util.concurrent.CopyOnWriteArrayList<String>()
    fun add(s: String) {
        items.add(0, s)
        while (items.size > 8) items.removeAt(items.size - 1)
        AssistantBus.post()
    }
}

/**
 * Looks after the phone while you do other things (runs inside the Accessibility service, every 1.5 s while the screen is on):
 * skips YouTube ads, allows the Android permission pop-ups Munawar asked for, and closes "app not responding" boxes.
 */
object Guardian {
    @Volatile var expectPermUntil = 0L
    private var lastAd = 0L
    private var lastFix = 0L

    fun expectPermission() { expectPermUntil = System.currentTimeMillis() + 40_000L }

    fun tick(s: MunawarAccessibility) {
        val p = Core.prefs
        val pm = s.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) return
        val root = s.rootInActiveWindow ?: return
        val pkg = root.packageName?.toString() ?: return
        val now = System.currentTimeMillis()

        if (p.autoGrant && now < expectPermUntil && pkg.contains("permissioncontroller")) {
            for (q in listOf("While using the app", "Allow all the time", "Allow", "ALLOW")) {
                if (s.clickText(q)) { expectPermUntil = 0L; Feed.add("✅ Allowed a permission for you"); return }
            }
        }
        if (p.autoSkipAds && pkg == "com.google.android.youtube" && now - lastAd > 3000L) {
            for (q in listOf("Skip ads", "Skip ad", "Skip Ads")) {
                if (s.clickText(q)) { lastAd = now; Feed.add("⏭ Skipped a YouTube ad"); return }
            }
        }
        if (pkg == "android" && now - lastFix > 5000L) {
            if (s.clickText("Wait")) { lastFix = now; Feed.add("🛠 Kept a slow app waiting") }
            else if (s.clickText("Close app")) { lastFix = now; Feed.add("🛠 Closed an app that stopped") }
        }
    }

    /** After opening a special-access screen: if Accessibility already works, let the agent flip the switch for you. */
    fun assistGrant(a: Host, what: String) {
        if (!Core.prefs.autoGrant || A11y.svc() == null) return
        Core.scope.launch {
            delay(1800)
            Agent.run(a, "On the Android screen that is open now, turn ON the switch or permission for the app named 'Munawar AI' (open its row first if needed) and confirm any dialog by choosing Allow, OK or Activate. Setting: $what. Stop as soon as it is on.")
        }
    }
}
