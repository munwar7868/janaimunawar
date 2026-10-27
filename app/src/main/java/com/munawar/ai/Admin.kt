package com.munawar.ai

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** Device-admin receiver: lets Munawar lock the screen instantly, even without Accessibility. It cannot wipe or read anything. */
class AdminReceiver : DeviceAdminReceiver()

object Admin {
    private fun comp(ctx: Context) = ComponentName(ctx, AdminReceiver::class.java)
    private fun dpm(ctx: Context) = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    fun active(ctx: Context): Boolean = try { dpm(ctx).isAdminActive(comp(ctx)) } catch (e: Exception) { false }

    fun intent(ctx: Context): Intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, comp(ctx))
        .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Lets Munawar AI lock your screen when you say “lock”. It can't read or erase anything.")

    fun lockNow(ctx: Context): Boolean {
        if (!active(ctx)) return false
        return try { dpm(ctx).lockNow(); true } catch (e: Exception) { false }
    }
}

/** What Munawar is feeling right now (read from its own words); the orb tints its aura and the Home screen shows it. */
object Emotion {
    // 0 calm, 1 happy, 2 caring/sad, 3 excited, 4 curious, 5 playful, 6 surprised, 7 loving
    private val rules = listOf(
        3 to Regex("(?i)wow|amazing|awesome|fantastic|kamaal|dhamaka|yay|let's go|chalo shuru|!{2,}|superb|zabardast"),
        6 to Regex("(?i)oh no|arre|kya baat|really\\?|sach mein|seriously|unbelievable|achanak|surprise"),
        5 to Regex("(?i)haha|hehe|lol|😂|😜|mazak|joke|masti|chidha|tease"),
        7 to Regex("(?i)love|pyaar|dost|friend|miss you|yaad|dil se|shukriya|thank|dhanyavaad|❤"),
        2 to Regex("(?i)sorry|sad|dukh|afsos|maaf|tension|pareshan|take care|khayal|unfortunately|thak|rest|aaram|worried|chinta"),
        1 to Regex("(?i)great|wonderful|nice|badhiya|mast|khushi|congrat|shabash|wah|happy|good morning|good evening|achha|bahut accha|😊|😄"),
        4 to Regex("(?i)\\?|kya|kaise|kyun|tell me|batao|interesting|hmm|curious")
    )

    fun detect(text: String): Int {
        for ((m, re) in rules) if (re.containsMatchIn(text)) return m
        return 0
    }

    fun color(mood: Int): Int = when (mood) {
        1 -> 0xFFFACC15.toInt()
        2 -> 0xFF60A5FA.toInt()
        3 -> 0xFFF472B6.toInt()
        4 -> 0xFF22D3EE.toInt()
        5 -> 0xFFA3E635.toInt()
        6 -> 0xFFF97316.toInt()
        7 -> 0xFFFB7185.toInt()
        else -> 0
    }

    fun label(mood: Int): String = when (mood) { 1 -> "😊"; 2 -> "🫂"; 3 -> "🤩"; 4 -> "🤔"; 5 -> "😜"; 6 -> "😮"; 7 -> "🥰"; else -> "" }

    fun name(mood: Int): String = when (mood) {
        1 -> "Khush"; 2 -> "Caring"; 3 -> "Excited"; 4 -> "Curious"; 5 -> "Playful"; 6 -> "Surprised"; 7 -> "Pyaar"; else -> "Calm"
    }
}
