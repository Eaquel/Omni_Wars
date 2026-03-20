package com.omni.wars

import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

object WarCheat {

    private const val TAG = "WarCheat"
    private var reportCount = 0

    init { System.loadLibrary("warcore") }

    external fun nativeValidateMove(x: Float, z: Float): Boolean
    external fun nativeValidateDamage(damage: Float): Boolean
    external fun nativeSetMaxSpeed(speed: Float)
    external fun nativeGetToken(x: Float, z: Float): Long
    external fun nativeIsIntegrityOk(): Boolean

    fun validatePositionUpdate(uid: String, newX: Float, newZ: Float, oldX: Float, oldZ: Float, dtMs: Long): Boolean {
        if (!nativeValidateMove(newX, newZ)) {
            reportCheat(uid, "speed_hack", "pos=($newX,$newZ)")
            return false
        }
        if (dtMs > 0) {
            val dx = newX - oldX; val dz = newZ - oldZ
            val dist = sqrt((dx * dx + dz * dz).toDouble()).toFloat()
            val speed = dist / (dtMs / 1000f)
            if (speed > 20f) {
                reportCheat(uid, "teleport", "speed=${speed.toInt()}")
                return false
            }
        }
        return true
    }

    fun validateDamageEvent(uid: String, damage: Float, weaponName: String): Boolean {
        if (!nativeValidateDamage(damage)) {
            reportCheat(uid, "damage_hack", "dmg=$damage weapon=$weaponName")
            return false
        }
        val weapon = WarWeapon.values().find { it.name == weaponName }
        val maxDmg = weapon?.baseDamage?.let { it * 2.5f } ?: 250f
        if (damage > maxDmg) {
            reportCheat(uid, "damage_overflow", "dmg=$damage max=$maxDmg")
            return false
        }
        return true
    }

    fun validateWeaponFire(uid: String, weapon: WarWeapon, lastFireMs: Long): Boolean {
        val now = System.currentTimeMillis()
        val elapsed = now - lastFireMs
        val minInterval = (weapon.fireRateMs * 0.7f).toLong()
        if (lastFireMs > 0 && elapsed < minInterval) {
            reportCheat(uid, "fire_rate_hack", "elapsed=${elapsed}ms min=${minInterval}ms")
            return false
        }
        return true
    }

    private fun reportCheat(uid: String, type: String, detail: String) {
        reportCount++
        Log.w(TAG, "CHEAT DETECTED uid=$uid type=$type detail=$detail (#$reportCount)")
        if (reportCount > 3) {
            try {
                FirebaseDatabase.getInstance()
                    .getReference("reports/$uid")
                    .setValue(mapOf("type" to type, "detail" to detail, "ts" to System.currentTimeMillis()))
            } catch (e: Exception) { Log.e(TAG, "Report failed", e) }
        }
    }

    fun reset() { reportCount = 0 }
}
