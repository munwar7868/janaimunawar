package com.munawar.ai

import android.Manifest
import android.content.Context
import android.os.Build
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/** Helpers for runtime permissions and the "special access" screens Android requires the user to open manually. */
object Perms {
    fun has(ctx: Context, perm: String) = ctx.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
    fun hasLocation(ctx: Context) = has(ctx, Manifest.permission.ACCESS_FINE_LOCATION) || has(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun overlay(ctx: Context) = Settings.canDrawOverlays(ctx)
    fun overlayIntent(ctx: Context) = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName))

    fun batteryIgnored(ctx: Context): Boolean =
        (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)

    fun batteryIntent(ctx: Context) = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.packageName))
    fun batteryListIntent() = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    fun a11yEnabled(ctx: Context): Boolean {
        val v = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return v.contains(ctx.packageName) && v.contains("MunawarAccessibility")
    }

    fun notifAccess(ctx: Context): Boolean =
        (Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: "").contains(ctx.packageName)

    fun appInfoIntent(ctx: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))

    fun writeSettings(ctx: Context) = Settings.System.canWrite(ctx)
    fun writeSettingsIntent(ctx: Context) = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + ctx.packageName))
    fun dndAccess(ctx: Context) = (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).isNotificationPolicyAccessGranted
    fun dndIntent() = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
    fun notifAccessIntent() = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    /** Every runtime permission Munawar can use (one tap "Allow all"). */
    fun allRuntime(): List<String> {
        val l = ArrayList<String>()
        l.add(Manifest.permission.RECORD_AUDIO)
        l.add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= 33) { l.add(Manifest.permission.POST_NOTIFICATIONS); l.add(Manifest.permission.READ_MEDIA_IMAGES) } else l.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        l.add(Manifest.permission.READ_CONTACTS); l.add(Manifest.permission.CALL_PHONE); l.add(Manifest.permission.ANSWER_PHONE_CALLS)
        l.add(Manifest.permission.READ_CALL_LOG); l.add(Manifest.permission.READ_PHONE_STATE); l.add(Manifest.permission.SEND_SMS)
        l.add(Manifest.permission.ACCESS_FINE_LOCATION); l.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        return l
    }

    /** The next special-access screen the user still has to allow (null = all done). */
    fun nextSpecial(ctx: Context): Pair<String, Intent>? {
        if (!overlay(ctx)) return Pair("Display over other apps", overlayIntent(ctx))
        if (!notifAccess(ctx)) return Pair("Notification access", notifAccessIntent())
        if (!a11yEnabled(ctx)) return Pair("Accessibility", Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        if (!Admin.active(ctx)) return Pair("Device admin (instant screen lock)", Admin.intent(ctx))
        if (!batteryIgnored(ctx)) return Pair("Battery unrestricted", batteryIntent(ctx))
        if (!writeSettings(ctx)) return Pair("Modify system settings (brightness, rotation)", writeSettingsIntent(ctx))
        if (!dndAccess(ctx)) return Pair("Do Not Disturb access", dndIntent())
        return null
    }
}
