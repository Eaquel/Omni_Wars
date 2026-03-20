package com.omni.wars

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.ktx.Firebase
import org.json.JSONObject

enum class WeaponCategory { MELEE, FIREARM }

enum class WarWeapon(
    val displayName: String,
    val emoji: String,
    val category: WeaponCategory,
    val baseDamage: Float,
    val fireRateMs: Long,
    val range: Float,
    val bulletSpeed: Float,
    val magazineSize: Int,
    val reloadMs: Long,
    val cost: Int,
    val bulletR: Float, val bulletG: Float, val bulletB: Float,
    val ricochet: Boolean = false,
    val unlockKills: Int = 0
) {
    FIST   ("Yumruk",           "👊", WeaponCategory.MELEE,   14f,  750L, 100f,  0f, 999, 0L,    0,   1f,0.8f,0.5f),
    KNIFE  ("Bıçak",            "🔪", WeaponCategory.MELEE,   32f,  380L, 110f,  0f, 999, 0L,   80,  0.9f,0.7f,0.4f),
    SPEAR  ("Mızrak",           "🏹", WeaponCategory.MELEE,   28f,  950L, 170f,  0f, 999, 0L,  120,  0.6f,0.9f,0.4f, unlockKills=5),
    SWORD  ("Kılıç",            "⚔️", WeaponCategory.MELEE,   42f,  600L, 120f,  0f, 999, 0L,  200,  0.8f,0.8f,0.2f, unlockKills=10),
    AXE    ("Balta",            "🪓", WeaponCategory.MELEE,   58f, 1300L, 120f,  0f, 999, 0L,  300,  0.6f,0.4f,0.2f, unlockKills=20),
    CLUB   ("Sopa",             "🏏", WeaponCategory.MELEE,   36f,  880L, 108f,  0f, 999, 0L,  150,  0.7f,0.5f,0.3f),
    SHIELD ("Kalkan",           "🛡️", WeaponCategory.MELEE,   20f,  480L, 100f,  0f, 999, 0L,  180,  0.5f,0.6f,0.9f),
    PISTOL ("Tabanca",          "🔫", WeaponCategory.FIREARM, 38f,  580L, 520f, 22f,  12, 1500L, 350, 1f,0.9f,0.1f),
    SMG    ("Makineli Tüfek",   "🔫", WeaponCategory.FIREARM, 20f,  115L, 390f, 21f,  30, 2200L, 600, 1f,0.6f,0.1f, unlockKills=30),
    SNIPER ("Keskin Nişancı",   "🎯", WeaponCategory.FIREARM,155f, 2400L,1250f, 42f,   5, 3000L, 900, 0.3f,0.9f,1f, ricochet=true, unlockKills=50),
    SHOTGUN("Av Tüfeği",        "💥", WeaponCategory.FIREARM, 28f,  900L, 280f, 17f,   6, 2000L, 450, 1f,0.7f,0.3f, unlockKills=25),
}

data class WeaponMod(
    val id: String,
    val name: String,
    val fireRateMultiplier: Float = 1f,
    val damageMultiplier: Float   = 1f,
    val magazineBonus: Int        = 0,
    val rangeMultiplier: Float    = 1f,
    val cost: Int                 = 100
)

val ALL_MODS = listOf(
    WeaponMod("light_trigger",   "Hafif Tetik",          fireRateMultiplier=0.75f, cost=120),
    WeaponMod("ext_magazine",    "Uzatılmış Şarjör",     magazineBonus=10,         cost=100),
    WeaponMod("heavy_barrel",    "Ağır Namlu",           damageMultiplier=1.3f,    cost=160),
    WeaponMod("scope",           "Dürbün",               rangeMultiplier=1.5f,     cost=140),
    WeaponMod("silencer",        "Susturucu",            damageMultiplier=0.9f, fireRateMultiplier=0.9f, cost=110),
    WeaponMod("grip",            "Tutuş Sapı",           damageMultiplier=1.1f, fireRateMultiplier=0.95f, cost=80),
)

enum class LootType { HEALTH_PACK, AMMO_PACK, SPEED_BOOST, SHIELD_BOOST, COIN_BAG, WEAPON_CRATE }

data class LootDrop(
    val id: String,
    val type: LootType,
    val x: Float, val z: Float,
    var collected: Boolean = false
) {
    val displayEmoji: String get() = when(type) {
        LootType.HEALTH_PACK  -> "❤️"
        LootType.AMMO_PACK    -> "📦"
        LootType.SPEED_BOOST  -> "⚡"
        LootType.SHIELD_BOOST -> "🛡️"
        LootType.COIN_BAG     -> "🪙"
        LootType.WEAPON_CRATE -> "🎁"
    }
    val healAmount: Float get() = if(type==LootType.HEALTH_PACK) 40f else 0f
    val ammoAmount: Int   get() = if(type==LootType.AMMO_PACK)   15  else 0
    val coinAmount: Int   get() = if(type==LootType.COIN_BAG)    50  else 0
}

object WeaponConfigManager {
    private val rc: FirebaseRemoteConfig by lazy {
        Firebase.remoteConfig.apply {
            val defaults = mutableMapOf<String, Any>()
            WarWeapon.values().forEach { w ->
                defaults["weapon_${w.name}"] = buildJson(w)
            }
            setDefaultsAsync(defaults)
        }
    }

    fun fetch(onDone: () -> Unit) {
        rc.fetchAndActivate().addOnCompleteListener { onDone() }
    }

    fun getDamage(w: WarWeapon): Float   = rc.getString("weapon_${w.name}").tryGet("damage", w.baseDamage)
    fun getFireRate(w: WarWeapon): Long  = rc.getString("weapon_${w.name}").tryGetLong("fireRate", w.fireRateMs)
    fun getRange(w: WarWeapon): Float    = rc.getString("weapon_${w.name}").tryGet("range", w.range)
    fun getMagazine(w: WarWeapon): Int   = rc.getString("weapon_${w.name}").tryGetInt("magazineSize", w.magazineSize)

    private fun buildJson(w: WarWeapon) =
        """{"damage":${w.baseDamage},"fireRate":${w.fireRateMs},"range":${w.range},"magazineSize":${w.magazineSize}}"""

    private fun String.tryGet(key: String, default: Float) = try { JSONObject(this).getDouble(key).toFloat() } catch(e: Exception) { default }
    private fun String.tryGetLong(key: String, default: Long) = try { JSONObject(this).getLong(key) } catch(e: Exception) { default }
    private fun String.tryGetInt(key: String, default: Int) = try { JSONObject(this).getInt(key) } catch(e: Exception) { default }
}
