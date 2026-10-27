package com.munawar.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Settings + secrets. Secrets (Gemini, ElevenLabs, Home Assistant) are encrypted with an Android Keystore AES key. */
class Prefs(private val ctx: Context) {
    private val sp = ctx.getSharedPreferences("munawar_settings", Context.MODE_PRIVATE)
    private val sec = ctx.getSharedPreferences("munawar_secure", Context.MODE_PRIVATE)

    private fun s(k: String, d: String) = sp.getString(k, d) ?: d
    private fun putS(k: String, v: String) = sp.edit().putString(k, v).apply()
    private fun b(k: String, d: Boolean) = sp.getBoolean(k, d)
    private fun putB(k: String, v: Boolean) = sp.edit().putBoolean(k, v).apply()

    var model: String
        get() = s("model", "gemini-3.5-flash")
        set(v) = putS("model", v.trim().removePrefix("models/"))
    var temperature: Float
        get() = sp.getFloat("temp", 0.9f)
        set(v) = sp.edit().putFloat("temp", v).apply()
    var mode: String
        get() = s("mode", "girlfriend")
        set(v) = putS("mode", v)
    var language: String
        get() = s("lang", "hinglish")
        set(v) = putS("lang", v)
    var customPersona: String
        get() = s("persona", "")
        set(v) = putS("persona", v)
    var userName: String
        get() = s("user", "")
        set(v) = putS("user", v)
    var speak: Boolean
        get() = b("speak", true)
        set(v) = putB("speak", v)
    var webSearch: Boolean
        get() = b("web", false)
        set(v) = putB("web", v)
    var pitch: Float
        get() = sp.getFloat("pitch", 1.1f)
        set(v) = sp.edit().putFloat("pitch", v).apply()
    var rate: Float
        get() = sp.getFloat("rate", 1.0f)
        set(v) = sp.edit().putFloat("rate", v).apply()
    var voiceName: String
        get() = s("voice", "")
        set(v) = putS("voice", v)

    // Gemini Live
    var liveModel: String
        get() = s("live_model", "gemini-3.1-flash-live-preview")
        set(v) = putS("live_model", v.trim().removePrefix("models/"))
    var liveVoice: String
        get() = s("live_voice", "Aoede")
        set(v) = putS("live_voice", v)
    var liveDefault: Boolean
        get() = b("live_default", true)
        set(v) = putB("live_default", v)

    // ElevenLabs
    var elevenEnabled: Boolean
        get() = b("el_on", false)
        set(v) = putB("el_on", v)
    var elevenVoiceId: String
        get() = s("el_voice", "")
        set(v) = putS("el_voice", v.trim())
    var elevenModel: String
        get() = s("el_model", "eleven_multilingual_v2")
        set(v) = putS("el_model", v.trim())

    // Smart home (Home Assistant)
    var haUrl: String
        get() = s("ha_url", "")
        set(v) = putS("ha_url", v.trim().trimEnd('/'))
    var haDevices: String
        get() = s("ha_devices", "")
        set(v) = putS("ha_devices", v)

    // Updates / safety switches
    var updateRepo: String
        get() = s("repo", "")
        set(v) = putS("repo", v.trim())
    var onboarded: Boolean
        get() = b("onboarded", false)
        set(v) = putB("onboarded", v)
    var floatSize: Int
        get() = sp.getInt("float_size", 100)
        set(v) = sp.edit().putInt("float_size", v.coerceIn(56, 200)).apply()
    var countryCode: String
        get() = s("cc", "91")
        set(v) = putS("cc", v.filter { it.isDigit() })
    var imageModel: String
        get() = s("img_model", "gemini-2.5-flash-image")
        set(v) = putS("img_model", v.trim().removePrefix("models/"))
    var liteMode: Boolean
        get() = b("lite", false)
        set(v) = putB("lite", v)
    var confirmSeconds: Int
        get() = sp.getInt("confirm_s", 5)
        set(v) = sp.edit().putInt("confirm_s", v.coerceIn(0, 15)).apply()
    var edgeLight: Boolean
        get() = b("edge", false)
        set(v) = putB("edge", v)
    var edgeThickness: Int
        get() = sp.getInt("edge_dp", 5)
        set(v) = sp.edit().putInt("edge_dp", v.coerceIn(2, 14)).apply()
    var edgeSpeed: Int
        get() = sp.getInt("edge_speed", 2)
        set(v) = sp.edit().putInt("edge_speed", v.coerceIn(1, 4)).apply()
    var overlayAsked: Boolean
        get() = b("overlay_asked", false)
        set(v) = putB("overlay_asked", v)
    var wakeEnabled: Boolean
        get() = b("wake_on", false)
        set(v) = putB("wake_on", v)
    var a11yAllowed: Boolean
        get() = b("a11y_allowed", true)
        set(v) = putB("a11y_allowed", v)


    // ---- MYRA-style features (v0.5)
    var sosNumbers: String
        get() = s("sos_nums", "")
        set(v) = putS("sos_nums", v)
    var parkLat: Float
        get() = sp.getFloat("park_lat", 999f)
        set(v) = sp.edit().putFloat("park_lat", v).apply()
    var parkLng: Float
        get() = sp.getFloat("park_lng", 999f)
        set(v) = sp.edit().putFloat("park_lng", v).apply()
    var parkTime: Long
        get() = sp.getLong("park_time", 0L)
        set(v) = sp.edit().putLong("park_time", v).apply()
    var activeMode: String
        get() = s("smart_mode", "none")
        set(v) = putS("smart_mode", v)
    var orbPal: Int
        get() = sp.getInt("orb_pal", 0)
        set(v) = sp.edit().putInt("orb_pal", v.coerceIn(0, 9)).apply()
    var orbForm: Int
        get() = sp.getInt("orb_form", 0)
        set(v) = sp.edit().putInt("orb_form", v.coerceIn(0, 7)).apply()
    /** 0 = off (still orb, default), 1 = calm, 2 = full */
    var orbAnim: Int
        get() = sp.getInt("orb_anim", 2)
        set(v) = sp.edit().putInt("orb_anim", v.coerceIn(0, 2)).apply()
    var edgeStyle: String
        get() = s("edge_style", "run")
        set(v) = putS("edge_style", v)
    var edgeOverlay: Boolean
        get() = b("edge_overlay", false)
        set(v) = putB("edge_overlay", v)
    var githubUser: String
        get() = s("gh_user", "")
        set(v) = putS("gh_user", v.trim())
    var telegramChat: String
        get() = s("tg_chat", "")
        set(v) = putS("tg_chat", v.trim())
    var altProvider: String
        get() = s("alt_provider", "groq")
        set(v) = putS("alt_provider", v)
    var altModel: String
        get() = s("alt_model", "")
        set(v) = putS("alt_model", v.trim())
    var triggersJson: String
        get() = s("triggers_json", "[]")
        set(v) = putS("triggers_json", v)
    var habitsJson: String
        get() = s("habits_json", "{}")
        set(v) = putS("habits_json", v)
    var habitLearning: Boolean
        get() = b("habit_on", false)
        set(v) = putB("habit_on", v)
    var otpReadAloud: Boolean
        get() = b("otp_read", true)
        set(v) = putB("otp_read", v)


    // ---- UI 2.0 appearance (all optional, all in Settings → Appearance)
    var uiTheme: String
        get() = s("ui_theme", "navy")
        set(v) = putS("ui_theme", v)
    var accentHex: String
        get() = s("accent_hex", "")
        set(v) = putS("accent_hex", v.trim())
    var bgHex: String
        get() = s("bg_hex", "")
        set(v) = putS("bg_hex", v.trim())
    var fontPct: Int
        get() = sp.getInt("font_pct", 100)
        set(v) = sp.edit().putInt("font_pct", v.coerceIn(70, 150)).apply()
    var cornerDp: Int
        get() = sp.getInt("corner_dp", 16)
        set(v) = sp.edit().putInt("corner_dp", v.coerceIn(4, 32)).apply()
    var cardAlphaPct: Int
        get() = sp.getInt("card_alpha", 85)
        set(v) = sp.edit().putInt("card_alpha", v.coerceIn(40, 100)).apply()
    var tiltOn: Boolean
        get() = b("tilt_on", false)
        set(v) = putB("tilt_on", v)
    var tiltPct: Int
        get() = sp.getInt("tilt_pct", 60)
        set(v) = sp.edit().putInt("tilt_pct", v.coerceIn(0, 100)).apply()
    var homeOrbDp: Int
        get() = sp.getInt("home_orb_dp", 330)
        set(v) = sp.edit().putInt("home_orb_dp", v.coerceIn(220, 440)).apply()
    var orbSpeedPct: Int
        get() = sp.getInt("orb_speed", 100)
        set(v) = sp.edit().putInt("orb_speed", v.coerceIn(30, 250)).apply()
    var orbBars: Boolean
        get() = b("orb_bars", false)
        set(v) = putB("orb_bars", v)
    var orbSparkles: Boolean
        get() = b("orb_sparkles", false)
        set(v) = putB("orb_sparkles", v)
    var orbRings: Boolean
        get() = b("orb_rings", false)
        set(v) = putB("orb_rings", v)
    var edgeGlowPct: Int
        get() = sp.getInt("edge_glow", 100)
        set(v) = sp.edit().putInt("edge_glow", v.coerceIn(30, 200)).apply()
    var edgeColors: String
        get() = s("edge_colors", "orb")
        set(v) = putS("edge_colors", v)
    var bgAnim: Boolean
        get() = b("bg_anim", true)
        set(v) = putB("bg_anim", v)
    var tileAnim: Boolean
        get() = b("tile_anim", false)
        set(v) = putB("tile_anim", v)
    var secPhrase: String
        get() = s("sec_phrase", "")
        set(v) = putS("sec_phrase", v.trim().lowercase())
    var secUnlockMin: Int
        get() = sp.getInt("sec_min", 10)
        set(v) = sp.edit().putInt("sec_min", v.coerceIn(1, 120)).apply()

    var filesTree: String
        get() = s("files_tree", "")
        set(v) = putS("files_tree", v)

    fun resetAppearance() {
        sp.edit().remove("ui_theme").remove("accent_hex").remove("bg_hex").remove("font_pct").remove("corner_dp").remove("card_alpha")
            .remove("tilt_on").remove("tilt_pct").remove("home_orb_dp").remove("orb_speed").remove("orb_bars").remove("orb_sparkles")
            .remove("orb_rings").remove("edge_glow").remove("edge_colors").remove("tile_anim").remove("bg_anim").remove("ui_anim").remove("card_style").remove("orb_pal").remove("orb_form").remove("orb_anim").remove("edge_style").apply()
    }


    // ---- v0.7 senses / sound / feature switches
    /** off | screen | camera | camera_front : what Munawar watches live during a conversation */
    var visionMode: String
        get() = s("vision_mode", "screen")
        set(v) = putS("vision_mode", v)
    var loudSpeaker: Boolean
        get() = b("loud_speaker", true)
        set(v) = putB("loud_speaker", v)
    var voiceGainPct: Int
        get() = sp.getInt("voice_gain", 160)
        set(v) = sp.edit().putInt("voice_gain", v.coerceIn(100, 300)).apply()
    var voiceInterrupt: Boolean
        get() = b("voice_interrupt", false)
        set(v) = putB("voice_interrupt", v)
    var wakeBeepFix: Boolean
        get() = b("wake_beep_fix", true)
        set(v) = putB("wake_beep_fix", v)
    var autoGreet: Boolean
        get() = b("auto_greet", true)
        set(v) = putB("auto_greet", v)
    /** 0 off, 1 smooth (default), 2 full */
    var uiAnim: Int
        get() = sp.getInt("ui_anim", 2)
        set(v) = sp.edit().putInt("ui_anim", v.coerceIn(0, 2)).apply()
    var offGroups: String
        get() = s("off_groups", "")
        set(v) = putS("off_groups", v)

    // ---- v1.1 companion / autopilot
    /** always = speaks calls & messages aloud even without a conversation; session = only during a conversation; off */
    var announceMode: String
        get() = s("announce_mode", "always")
        set(v) = putS("announce_mode", v)
    var chatEveryMin: Int
        get() = sp.getInt("chat_every", 30)
        set(v) = sp.edit().putInt("chat_every", v.coerceIn(0, 240)).apply()
    var autoSkipAds: Boolean
        get() = b("auto_skip_ads", true)
        set(v) = putB("auto_skip_ads", v)
    var autoGrant: Boolean
        get() = b("auto_grant", true)
        set(v) = putB("auto_grant", v)
    var keepAlive: Boolean
        get() = b("keep_alive", true)
        set(v) = putB("keep_alive", v)
    var cardStyle: String
        get() = s("card_style", "glass")
        set(v) = putS("card_style", v)
    var routineMorning: String
        get() = s("routine_morning", "")
        set(v) = putS("routine_morning", v.trim())
    var routineNight: String
        get() = s("routine_night", "")
        set(v) = putS("routine_night", v.trim())
    var lastGreets: String
        get() = s("last_greets", "")
        set(v) = putS("last_greets", v)

    fun sttLocale(): String = if (language == "english") "en-IN" else "hi-IN"

    // ---- encrypted secrets ----
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return g.generateKey()
    }

    fun saveSecret(name: String, value: String) {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val out = c.iv + c.doFinal(value.trim().toByteArray())
        sec.edit().putString("s_$name", Base64.encodeToString(out, Base64.NO_WRAP)).apply()
    }

    fun secret(name: String): String? {
        val enc = sec.getString("s_$name", null) ?: return null
        return try {
            val d = Base64.decode(enc, Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, d.copyOfRange(0, 12)))
            String(c.doFinal(d, 12, d.size - 12))
        } catch (e: Exception) {
            null
        }
    }

    fun removeSecret(name: String) = sec.edit().remove("s_$name").apply()

    fun apiKey(): String? = secret("gemini")
    fun saveApiKey(k: String) = saveSecret("gemini", k)
    fun removeApiKey() = removeSecret("gemini")
    fun hasApiKey() = apiKey() != null

    fun wipeAll() {
        sp.edit().clear().apply(); sec.edit().clear().apply()
    }

    companion object {
        private const val ALIAS = "munawar_api_key"

        /** Chat model presets shown in Settings (first one is the default). Newer Flash models are listed too. */
        val MODEL_PRESETS = listOf(
            "gemini-3.5-flash" to "Gemini 3.5 Flash (default)",
            "gemini-3.6-flash" to "Gemini 3.6 Flash",
            "gemini-3.7-flash" to "Gemini 3.7 Flash",
            "gemini-3.8-flash" to "Gemini 3.8 Flash (newest)",
            "gemini-3.1-pro-preview" to "Gemini 3.1 Pro (preview)",
            "gemini-3.1-flash-lite" to "Gemini 3.1 Flash-Lite (cheap)",
            "gemini-2.5-flash" to "Gemini 2.5 Flash",
            "gemini-2.5-pro" to "Gemini 2.5 Pro"
        )
    }
}
