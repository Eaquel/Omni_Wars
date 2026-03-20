package com.omni.wars

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object WarSettings {

    private lateinit var prefs: SharedPreferences
    private var vibrator: Vibrator? = null

    init { System.loadLibrary("warcore") }

    external fun nativeSetQuality(level: Int)
    external fun nativeGetQuality(): Int
    external fun nativeSetVolume(master: Float, sfx: Float)
    external fun nativeGetFPS(): Float
    external fun nativeGetRecommendedQuality(): Int
    external fun nativeInitAudio()
    external fun nativeReleaseAudio()

    enum class Quality(val label: String, val shadowsOn: Boolean, val particles: Boolean, val targetFps: Int) {
        LOW("Düşük", false, false, 30),
        MEDIUM("Orta", false, true, 45),
        HIGH("Yüksek", true, true, 60)
    }

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("war_settings", Context.MODE_PRIVATE)
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val q = quality.ordinal
        nativeSetQuality(q)
        nativeSetVolume(masterVolume, sfxVolume)
        nativeInitAudio()
    }

    fun release() { nativeReleaseAudio() }

    var quality: Quality
        get() = Quality.values().getOrElse(prefs.getInt("quality", 1)) { Quality.MEDIUM }
        set(v) { prefs.edit().putInt("quality", v.ordinal).apply(); nativeSetQuality(v.ordinal) }

    var masterVolume: Float
        get() = prefs.getFloat("master_vol", 0.8f)
        set(v) { prefs.edit().putFloat("master_vol", v).apply(); nativeSetVolume(v, sfxVolume) }

    var sfxVolume: Float
        get() = prefs.getFloat("sfx_vol", 1.0f)
        set(v) { prefs.edit().putFloat("sfx_vol", v).apply(); nativeSetVolume(masterVolume, v) }

    var vibrationEnabled: Boolean
        get() = prefs.getBoolean("vibration", true)
        set(v) { prefs.edit().putBoolean("vibration", v).apply() }

    var playerName: String
        get() = prefs.getString("player_name", "Savaşçı") ?: "Savaşçı"
        set(v) { prefs.edit().putString("player_name", v.take(16)).apply() }

    var savedCoins: Int
        get() = prefs.getInt("coins", 0)
        set(v) { prefs.edit().putInt("coins", v).apply() }

    var totalKills: Int
        get() = prefs.getInt("total_kills", 0)
        set(v) { prefs.edit().putInt("total_kills", v).apply() }

    var equippedWeapon: String
        get() = prefs.getString("weapon", WarWeapon.FIST.name) ?: WarWeapon.FIST.name
        set(v) { prefs.edit().putString("weapon", v).apply() }

    var weaponLevel: Int
        get() = prefs.getInt("weapon_level", 1)
        set(v) { prefs.edit().putInt("weapon_level", v.coerceIn(1, 10)).apply() }

    var healthLevel: Int
        get() = prefs.getInt("health_level", 1)
        set(v) { prefs.edit().putInt("health_level", v.coerceIn(1, 10)).apply() }

    var speedLevel: Int
        get() = prefs.getInt("speed_level", 1)
        set(v) { prefs.edit().putInt("speed_level", v.coerceIn(1, 10)).apply() }

    fun maxHealth() = 100f + (healthLevel - 1) * 20f
    fun moveSpeed() = 6f + (speedLevel - 1) * 1.2f
    fun weaponDmgMult() = 1f + (weaponLevel - 1) * 0.25f

    fun vibrate(durationMs: Long = 50L) {
        if (!vibrationEnabled) return
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else { @Suppress("DEPRECATION") it.vibrate(durationMs) }
        }
    }

    fun autoDetectQuality() {
        val rec = nativeGetRecommendedQuality()
        quality = Quality.values().getOrElse(rec) { Quality.MEDIUM }
    }
}
