package com.omni.wars

import android.content.Context
import android.graphics.*
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.firebase.database.*
import kotlinx.coroutines.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

object WarNative {
    init { System.loadLibrary("warcore") }
    @JvmStatic external fun nativeInit(w: Int, h: Int)
    @JvmStatic external fun nativeRender(dt: Float)
    @JvmStatic external fun nativeSetPlayer(idx: Int, x: Float, y: Float, z: Float, hp: Float, maxHp: Float, rotY: Float, weapon: Int, alive: Boolean)
    @JvmStatic external fun nativeSetEnemy(idx: Int, x: Float, y: Float, z: Float, hp: Float, maxHp: Float, alive: Boolean)
    @JvmStatic external fun nativeFire(ox: Float, oy: Float, oz: Float, dx: Float, dy: Float, dz: Float, dmg: Float, spd: Float, range: Float, cr: Float, cg: Float, cb: Float, weapon: Int)
    @JvmStatic external fun nativeScreenShake(intensity: Float)
    @JvmStatic external fun nativeSpawnEffect(x: Float, y: Float, z: Float, r: Float, g: Float, b: Float, type: Int)
    @JvmStatic external fun nativeSetDayFactor(f: Float)
    @JvmStatic external fun nativeSetLocalPlayer(idx: Int)
    @JvmStatic external fun nativeCleanup()
}

class WarMap : AppCompatActivity() {

    private lateinit var glView: WarGLView

    private fun pxi(dp: Float) = (dp * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val roomName   = intent.getStringExtra("ROOM")  ?: ""
        val uid        = intent.getStringExtra("UID")   ?: ""
        val isHost     = intent.getBooleanExtra("HOST", false)
        val playerName = intent.getStringExtra("NAME")  ?: WarSettings.playerName

        glView = WarGLView(this, roomName, uid, isHost, playerName, FirebaseDatabase.getInstance()) {
            glView.pause()
            showShopOverlay()
        }

        val frame = FrameLayout(this)
        frame.addView(glView,         ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        frame.addView(glView.hudView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setContentView(frame)
    }

    private fun showShopOverlay() {
        val overlay = FrameLayout(this)
        overlay.setBackgroundColor(Color.argb(220, 8, 8, 18))
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pxi(20f), pxi(40f), pxi(20f), pxi(20f))
        }
        fun tv(t: String, sz: Float = 16f, col: Int = Color.WHITE) = TextView(this).apply {
            text = t; textSize = sz; setTextColor(col); typeface = Typeface.DEFAULT_BOLD
        }
        fun mkBtn(t: String, c: Int) = Button(this).apply {
            text = t; setTextColor(Color.WHITE); setBackgroundColor(c)
            textSize = 14f; isAllCaps = false; stateListAnimator = null
        }
        ll.addView(tv("🏪  MARKET", 28f, Color.parseColor("#FFD700")))
        val coinTv = tv("🪙 Altın: ${glView.myCoins}", 20f, Color.parseColor("#FFD700"))
        coinTv.setPadding(0, pxi(6f), 0, pxi(20f))
        ll.addView(coinTv)

        WarWeapon.values().filter { it.cost > 0 }.forEach { w ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, pxi(8f))
            }
            val lbl = tv("${w.emoji} ${w.displayName}  —  ${w.cost}g", 14f)
            lbl.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            val btn = mkBtn("AL", Color.parseColor("#1E4488")).apply {
                layoutParams = LinearLayout.LayoutParams(pxi(64f), pxi(40f))
                setOnClickListener {
                    if (glView.myCoins >= w.cost) {
                        glView.myCoins -= w.cost
                        glView.currentWeapon = w
                        glView.ammo = WeaponConfigManager.getMagazine(w)
                        coinTv.text = "🪙 Altın: ${glView.myCoins}"
                        Toast.makeText(this@WarMap, "${w.displayName} alındı!", Toast.LENGTH_SHORT).show()
                    } else Toast.makeText(this@WarMap, "Yetersiz altın!", Toast.LENGTH_SHORT).show()
                }
            }
            row.addView(lbl); row.addView(btn); ll.addView(row)
        }

        val space = View(this)
        space.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, pxi(16f))
        ll.addView(space)

        val closeBtn = mkBtn("KAPAT  ✕", Color.parseColor("#AA2222"))
        closeBtn.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, pxi(52f))
        closeBtn.setOnClickListener {
            (overlay.parent as? ViewGroup)?.removeView(overlay)
            glView.resume()
        }
        ll.addView(closeBtn)

        overlay.addView(ScrollView(this).also { it.addView(ll) })
        (window.decorView as? FrameLayout)?.addView(
            overlay, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    override fun onResume()  { super.onResume();  glView.resume() }
    override fun onPause()   { super.onPause();   glView.pause() }
    override fun onDestroy() { super.onDestroy(); glView.cleanup() }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { glView.cleanup(); super.onBackPressed() }
}

class WarGLView(
    context: Context,
    private val roomName: String,
    private val uid: String,
    private val isHost: Boolean,
    private val playerName: String,
    private val db: FirebaseDatabase,
    private val onShop: () -> Unit
) : GLSurfaceView(context) {

    val hudView = WarHudView(context)

    var myX = 0f; var myZ = 0f; var myRotY = 0f
    var myHp = 100f; var maxHp = 100f
    var myCoins = WarSettings.savedCoins
    var currentWeapon: WarWeapon = WarWeapon.values().find { it.name == WarSettings.equippedWeapon } ?: WarWeapon.FIST
    var ammo = WeaponConfigManager.getMagazine(currentWeapon)

    private var isReloading = false; private var lastFireMs = 0L; private var reloadStart = 0L
    private var kills = 0; private var gameTimeMs = 0L; private var regenAcc = 0f; private var dayFactor = 1f
    private var lastSyncMs = 0L

    private val others  = HashMap<String, FloatArray>()
    private val enemies = HashMap<String, FloatArray>()
    private var playersLsn: ValueEventListener? = null
    private var enemiesLsn: ValueEventListener? = null

    private var jActive = false; private var jPx = 0f; private var jPy = 0f
    private var jBx = 0f; private var jBy = 0f; private var jPid = -1; private var jR = 0f
    private var atkDown = false; private var atkPid = -1
    private var atkBx = 0f; private var atkBy = 0f; private var atkR = 0f
    private var shopBx = 0f; private var shopBy = 0f; private var shopR = 0f

    private fun dpF(v: Float) = v * context.resources.displayMetrics.density

    private val renderer = object : Renderer {
        private var lastNs = 0L
        override fun onSurfaceCreated(gl: GL10?, cfg: EGLConfig?) { lastNs = System.nanoTime() }
        override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
            WarNative.nativeInit(w, h)
            jPx = dpF(200f); jPy = h - dpF(220f); jBx = jPx; jBy = jPy; jR = dpF(130f)
            atkBx = w - dpF(200f); atkBy = h - dpF(200f); atkR = dpF(86f)
            shopBx = dpF(72f); shopBy = dpF(72f); shopR = dpF(46f)
            hudView.setLayout(w, h, jPx, jPy, jR, atkBx, atkBy, atkR, shopBx, shopBy, shopR)
            initFirebase()
            if (isHost) spawnEnemyLoop()
        }
        override fun onDrawFrame(gl: GL10?) {
            val now = System.nanoTime()
            val dt  = ((now - lastNs) / 1e9f).coerceIn(0f, 0.05f); lastNs = now
            gameTimeMs += (dt * 1000).toLong()
            dayFactor = (sin(gameTimeMs / 120000.0 * Math.PI * 2) * 0.5 + 0.5).toFloat().coerceIn(0.05f, 1f)
            WarNative.nativeSetDayFactor(dayFactor)
            WarNative.nativeSetPlayer(0, myX, 0f, myZ, myHp, maxHp, myRotY, currentWeapon.ordinal, true)
            updateMovement(dt); updateWeapon(); syncEnemyAI(dt); autoRegen(dt)
            val nowMs = System.currentTimeMillis()
            if (nowMs - lastSyncMs > 100L) { lastSyncMs = nowMs; syncFirebase() }
            WarNative.nativeRender(dt)
            val atkReady = nowMs > lastFireMs + WeaponConfigManager.getFireRate(currentWeapon)
            hudView.post {
                hudView.update(myHp, maxHp, ammo, WeaponConfigManager.getMagazine(currentWeapon),
                    currentWeapon, isReloading, myCoins, kills, others.size + 1, enemies.size,
                    jBx, jBy, jPx, jPy, jR, atkDown, atkReady, dayFactor, atkBx, atkBy, atkR, shopBx, shopBy, shopR)
            }
        }
    }

    init {
        setEGLContextClientVersion(3); setRenderer(renderer); renderMode = RENDERMODE_CONTINUOUSLY
        maxHp = WarSettings.maxHealth(); myHp = maxHp
    }

    private fun updateMovement(dt: Float) {
        if (!jActive) return
        val dx = jBx - jPx; val dz = jBy - jPy
        val dist = sqrt(dx * dx + dz * dz); if (dist < 8f) return
        val speed = WarSettings.moveSpeed() * 60f * dt
        val nx = myX + (dx / dist) * speed; val nz = myZ + (dz / dist) * speed
        if (WarCheat.validatePositionUpdate(uid, nx, nz, myX, myZ, (dt * 1000).toLong())) {
            myX = nx.coerceIn(-490f, 490f); myZ = nz.coerceIn(-490f, 490f); myRotY = atan2(dx, dz)
        }
    }

    private fun updateWeapon() {
        val now = System.currentTimeMillis()
        if (isReloading && now - reloadStart >= WeaponConfigManager.getFireRate(currentWeapon) * 3) {
            isReloading = false; ammo = WeaponConfigManager.getMagazine(currentWeapon)
        }
        if (!atkDown || isReloading) return
        if (now - lastFireMs < WeaponConfigManager.getFireRate(currentWeapon)) return
        if (currentWeapon.category == WeaponCategory.FIREARM && ammo <= 0) {
            if (!isReloading) { isReloading = true; reloadStart = now }; return
        }
        if (!WarCheat.validateWeaponFire(uid, currentWeapon, lastFireMs)) return
        lastFireMs = now
        val nearest = enemies.values.minByOrNull { (it[0]-myX).pow(2) + (it[2]-myZ).pow(2) }
        when (currentWeapon.category) {
            WeaponCategory.MELEE -> {
                nearest?.let { e ->
                    if (sqrt((e[0]-myX).pow(2)+(e[2]-myZ).pow(2)) < WeaponConfigManager.getRange(currentWeapon)) {
                        val dmg = WeaponConfigManager.getDamage(currentWeapon) * WarSettings.weaponDmgMult()
                        if (WarCheat.validateDamageEvent(uid, dmg, currentWeapon.name))
                            hitEnemy(e[8].toInt().toString(), dmg, e[0], e[2])
                    }
                }
                WarNative.nativeScreenShake(0.2f); WarSettings.vibrate(30)
            }
            WeaponCategory.FIREARM -> {
                ammo--
                val tdx = nearest?.let { it[0]-myX } ?: sin(myRotY)
                val tdz = nearest?.let { it[2]-myZ } ?: cos(myRotY)
                val len = sqrt(tdx*tdx+tdz*tdz).coerceAtLeast(0.001f)
                val spread = if (currentWeapon == WarWeapon.SMG) 0.08f else 0f
                val angle = atan2(tdz/len, tdx/len) + (Math.random().toFloat()-0.5f)*spread
                val dmg = WeaponConfigManager.getDamage(currentWeapon) * WarSettings.weaponDmgMult()
                WarNative.nativeFire(myX, 0.9f, myZ, cos(angle), 0f, sin(angle), dmg,
                    currentWeapon.bulletSpeed, WeaponConfigManager.getRange(currentWeapon),
                    currentWeapon.bulletR, currentWeapon.bulletG, currentWeapon.bulletB, currentWeapon.ordinal)
                when (currentWeapon) {
                    WarWeapon.SNIPER  -> WarNative.nativeScreenShake(0.8f)
                    WarWeapon.SHOTGUN -> {
                        repeat(4) {
                            val sa = atan2(tdz/len, tdx/len) + (Math.random().toFloat()-0.5f)*0.25f
                            WarNative.nativeFire(myX, 0.9f, myZ, cos(sa), 0f, sin(sa), dmg*0.4f,
                                currentWeapon.bulletSpeed, WeaponConfigManager.getRange(currentWeapon)*0.5f,
                                currentWeapon.bulletR, currentWeapon.bulletG, currentWeapon.bulletB, currentWeapon.ordinal)
                        }
                        WarNative.nativeScreenShake(0.5f)
                    }
                    else -> {}
                }
                WarSettings.vibrate(15)
                if (ammo <= 0 && !isReloading) { isReloading = true; reloadStart = now }
            }
        }
    }

    private fun hitEnemy(id: String, damage: Float, ex: Float, ez: Float) {
        val e = enemies[id] ?: return
        e[3] -= damage
        WarNative.nativeSpawnEffect(ex, 0f, ez, 1f, 0.1f, 0f, 0)
        if (e[3] <= 0f) {
            enemies.remove(id); kills++; myCoins += 10
            WarSettings.totalKills++; WarSettings.savedCoins = myCoins
            WarNative.nativeSpawnEffect(ex, 0f, ez, 1f, 0.1f, 0f, 1)
            if (isHost) db.getReference("rooms/$roomName/enemies/$id").removeValue()
        } else {
            if (isHost) db.getReference("rooms/$roomName/enemies/$id/health").setValue(e[3])
        }
    }

    private fun syncEnemyAI(dt: Float) {
        if (!isHost) return
        val targets = mutableListOf(floatArrayOf(myX, 0f, myZ)).also { l -> others.values.forEach { l.add(it) } }
        for ((_, e) in enemies.toMap()) {
            if (e[3] <= 0f) continue
            var nd = Float.MAX_VALUE; var px = myX; var pz = myZ
            for (p in targets) { val d = sqrt((p[0]-e[0]).pow(2)+(p[2]-e[2]).pow(2)); if (d < nd) { nd=d; px=p[0]; pz=p[2] } }
            val dx=px-e[0]; val dz=pz-e[2]; val dist=sqrt(dx*dx+dz*dz)
            if (dist > 1.2f) { e[0] += (dx/dist)*e[5]*60f*dt; e[2] += (dz/dist)*e[5]*60f*dt }
            if (dist < 1.5f) { myHp = (myHp-e[4]*dt).coerceAtLeast(0f); WarNative.nativeScreenShake(0.1f) }
            val idx = e[8].toInt().and(0x7F).coerceIn(0, 47)
            WarNative.nativeSetEnemy(idx, e[0], 0f, e[2], e[3], e[7], e[3] > 0f)
        }
    }

    private fun autoRegen(dt: Float) {
        regenAcc += dt
        if (regenAcc >= 2f) { regenAcc = 0f; myHp = (myHp + 0.5f * WarSettings.healthLevel).coerceAtMost(maxHp) }
    }

    private fun syncFirebase() {
        db.getReference("rooms/$roomName/players/$uid").updateChildren(
            mapOf("x" to myX, "z" to myZ, "hp" to myHp, "rot" to myRotY,
                "weapon" to currentWeapon.name, "name" to playerName))
    }

    private fun spawnEnemyLoop() {
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                delay(3000); if (enemies.size < 24) spawnEnemy()
            }
        }
    }

    private fun spawnEnemy() {
        val id = "e_${System.currentTimeMillis()}"
        val angle = Math.random().toFloat() * 2 * PI.toFloat()
        val dist  = 30f + Math.random().toFloat() * 40f
        db.getReference("rooms/$roomName/enemies/$id").setValue(mapOf(
            "x" to (myX + cos(angle)*dist).coerceIn(-480f,480f),
            "z" to (myZ + sin(angle)*dist).coerceIn(-480f,480f),
            "hp" to (60f + Math.random()*80f), "maxHp" to 100f,
            "spd" to (1.8f + Math.random()*1.5f), "dmg" to (8f + Math.random()*10f)))
    }

    private fun initFirebase() {
        playersLsn = db.getReference("rooms/$roomName/players").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                others.clear(); var idx = 1
                for (child in snap.children) {
                    if (child.key == uid) continue
                    val x   = (child.child("x").value as? Number)?.toFloat() ?: 0f
                    val z   = (child.child("z").value as? Number)?.toFloat() ?: 0f
                    val hp  = (child.child("hp").value as? Number)?.toFloat() ?: 100f
                    val rot = (child.child("rot").value as? Number)?.toFloat() ?: 0f
                    val wep = WarWeapon.values().indexOfFirst { it.name == child.child("weapon").value }.coerceAtLeast(0)
                    others[child.key ?: continue] = floatArrayOf(x, 0f, z)
                    WarNative.nativeSetPlayer(idx, x, 0f, z, hp, 100f, rot, wep, true); idx++
                }
            }
            override fun onCancelled(e: DatabaseError) {}
        })
        enemiesLsn = db.getReference("rooms/$roomName/enemies").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                enemies.clear(); var idx = 0
                for (child in snap.children) {
                    val id  = child.key ?: continue
                    val x   = (child.child("x").value as? Number)?.toFloat() ?: 0f
                    val z   = (child.child("z").value as? Number)?.toFloat() ?: 0f
                    val hp  = (child.child("hp").value as? Number)?.toFloat() ?: 60f
                    val mhp = (child.child("maxHp").value as? Number)?.toFloat() ?: 60f
                    val dmg = (child.child("dmg").value as? Number)?.toFloat() ?: 10f
                    val spd = (child.child("spd").value as? Number)?.toFloat() ?: 2f
                    enemies[id] = floatArrayOf(x, 0f, z, hp, dmg, spd, 0f, mhp, idx.toFloat())
                    WarNative.nativeSetEnemy(idx, x, 0f, z, hp, mhp, hp > 0f); idx++
                }
            }
            override fun onCancelled(e: DatabaseError) {}
        })
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val pi = ev.actionIndex; val pid = ev.getPointerId(pi); val mask = ev.actionMasked
        when (mask) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val x = ev.getX(pi); val y = ev.getY(pi)
                if (sqrt((x-shopBx).pow(2)+(y-shopBy).pow(2)) < shopR+20f) { onShop(); return true }
                if (sqrt((x-jPx).pow(2)+(y-jPy).pow(2)) < jR+50f && !jActive) { jActive=true; jPid=pid; moveJoy(x,y) }
                if (sqrt((x-atkBx).pow(2)+(y-atkBy).pow(2)) < atkR+30f) { atkDown=true; atkPid=pid }
            }
            MotionEvent.ACTION_MOVE -> { for (i in 0 until ev.pointerCount) { if (ev.getPointerId(i)==jPid) moveJoy(ev.getX(i),ev.getY(i)) } }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                if (pid==jPid)  { jActive=false; jPid=-1; jBx=jPx; jBy=jPy }
                if (pid==atkPid){ atkDown=false; atkPid=-1 }
            }
        }
        return true
    }

    private fun moveJoy(tx: Float, ty: Float) {
        val dx=tx-jPx; val dy=ty-jPy; val d=sqrt(dx*dx+dy*dy)
        if(d<=jR){ jBx=tx; jBy=ty } else { jBx=jPx+dx/d*jR; jBy=jPy+dy/d*jR }
    }

    fun pause()  { onPause() }
    fun resume() { onResume() }
    fun cleanup() {
        WarNative.nativeCleanup()
        playersLsn?.let { db.getReference("rooms/$roomName/players").removeEventListener(it) }
        enemiesLsn?.let { db.getReference("rooms/$roomName/enemies").removeEventListener(it) }
        db.getReference("rooms/$roomName/players/$uid").removeValue()
        WarSettings.savedCoins = myCoins; WarCheat.reset()
    }
}

class WarHudView(context: Context) : View(context) {
    private var hp=100f; private var maxHp=100f; private var ammo=12; private var maxAmmo=12
    private var weapon=WarWeapon.FIST; private var reloading=false; private var coins=0
    private var kills=0; private var players=1; private var enemyCount=0; private var dayFactor=1f
    private var jbx=0f; private var jby=0f; private var jpx=0f; private var jpy=0f; private var jR=0f
    private var atkDown=false; private var atkReady=true
    private var atkBx=0f; private var atkBy=0f; private var atkR=0f
    private var shopBx=0f; private var shopBy=0f; private var shopR=0f

    private fun dp(v: Float) = v * resources.displayMetrics.density

    private val pBar  = Paint().apply { isAntiAlias = true }
    private val pBg   = Paint().apply { color = Color.argb(160,0,0,0); isAntiAlias = true }
    private val pTxt  = Paint().apply { color = Color.WHITE; isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD; setShadowLayer(3f,1f,1f,Color.BLACK) }
    private val pJoy  = Paint().apply { color = Color.argb(60,200,200,255); isAntiAlias = true }
    private val pJoyB = Paint().apply { color = Color.argb(130,255,255,255); isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = 3f }
    private val pJoyH = Paint().apply { color = Color.argb(200,100,170,255); isAntiAlias = true }
    private val pAtk  = Paint().apply { isAntiAlias = true }
    private val pShop = Paint().apply { color = Color.argb(200,255,180,0); isAntiAlias = true }

    fun setLayout(w: Int, h: Int, jx: Float, jy: Float, jr: Float, ax: Float, ay: Float, ar: Float, sx: Float, sy: Float, sr: Float) {
        jpx=jx; jpy=jy; jbx=jx; jby=jy; jR=jr
        atkBx=ax; atkBy=ay; atkR=ar; shopBx=sx; shopBy=sy; shopR=sr
    }

    fun update(hp: Float, maxHp: Float, ammo: Int, maxAmmo: Int, weapon: WarWeapon,
               reloading: Boolean, coins: Int, kills: Int, players: Int, enemies: Int,
               jbx: Float, jby: Float, jpx: Float, jpy: Float, jR: Float,
               atkDown: Boolean, atkReady: Boolean, dayFactor: Float,
               atkBx: Float, atkBy: Float, atkR: Float,
               shopBx: Float, shopBy: Float, shopR: Float) {
        this.hp=hp; this.maxHp=maxHp; this.ammo=ammo; this.maxAmmo=maxAmmo
        this.weapon=weapon; this.reloading=reloading; this.coins=coins
        this.kills=kills; this.players=players; this.enemyCount=enemies
        this.jbx=jbx; this.jby=jby; this.jpx=jpx; this.jpy=jpy; this.jR=jR
        this.atkDown=atkDown; this.atkReady=atkReady; this.dayFactor=dayFactor
        this.atkBx=atkBx; this.atkBy=atkBy; this.atkR=atkR
        this.shopBx=shopBx; this.shopBy=shopBy; this.shopR=shopR
        invalidate()
    }

    override fun onDraw(cv: Canvas) {
        val sw = width.toFloat()
        val hpFrac = (hp/maxHp).coerceIn(0f,1f)
        val bw=dp(260f); val bh=dp(16f); val bx=dp(12f); val by=dp(46f)
        pBar.color=Color.argb(180,20,20,20); cv.drawRoundRect(bx,by,bx+bw,by+bh,bh/2,bh/2,pBar)
        pBar.color=when{ hpFrac>0.6f->Color.rgb(50,220,80); hpFrac>0.3f->Color.rgb(255,180,0); else->Color.rgb(220,50,50) }
        cv.drawRoundRect(bx,by,bx+bw*hpFrac,by+bh,bh/2,bh/2,pBar)
        pTxt.textSize=dp(13f); pTxt.color=Color.WHITE
        cv.drawText("❤ ${hp.toInt()}/${maxHp.toInt()}", bx, by-dp(4f), pTxt)

        cv.drawRoundRect(bx,dp(70f),bx+dp(260f),dp(112f),dp(10f),dp(10f),pBg)
        cv.drawText(if(reloading) "🔄 Dolduruluyor..." else "${weapon.emoji} ${weapon.displayName}", bx+dp(6f), dp(89f), pTxt)
        pTxt.color=when{ ammo>maxAmmo*0.5f->Color.WHITE; ammo>0->Color.parseColor("#FFAA00"); else->Color.parseColor("#FF3333") }
        cv.drawText(if(weapon.category==WeaponCategory.FIREARM) "🔫 $ammo / $maxAmmo" else "∞", bx+dp(6f), dp(109f), pTxt)

        cv.drawRoundRect(sw-dp(210f),dp(10f),sw-dp(10f),dp(52f),dp(12f),dp(12f),pBg)
        pTxt.color=Color.parseColor("#FFD700"); pTxt.textSize=dp(14f)
        cv.drawText("🪙 $coins", sw-dp(200f), dp(37f), pTxt)

        cv.drawRoundRect(sw/2-dp(120f),dp(10f),sw/2+dp(120f),dp(50f),dp(12f),dp(12f),pBg)
        pTxt.color=Color.WHITE; pTxt.textSize=dp(12f); pTxt.textAlign=Paint.Align.CENTER
        cv.drawText("👥 $players   💀 $kills   👾 $enemyCount", sw/2, dp(35f), pTxt)
        pTxt.textAlign=Paint.Align.LEFT

        if (dayFactor < 0.35f) { pTxt.color=Color.parseColor("#6688FF"); pTxt.textSize=dp(12f); cv.drawText("🌙 Gece", sw-dp(95f), dp(70f), pTxt) }

        cv.drawCircle(jpx,jpy,jR,pJoy); cv.drawCircle(jpx,jpy,jR,pJoyB); cv.drawCircle(jbx,jby,jR*0.4f,pJoyH)

        pAtk.color=if(atkReady) Color.argb(200,200,40,40) else Color.argb(90,100,20,20)
        cv.drawCircle(atkBx,atkBy,atkR,pAtk)
        pTxt.color=Color.WHITE; pTxt.textSize=dp(20f); pTxt.textAlign=Paint.Align.CENTER
        cv.drawText("⚔", atkBx, atkBy+dp(8f), pTxt); pTxt.textAlign=Paint.Align.LEFT

        cv.drawCircle(shopBx,shopBy,shopR,pShop)
        pTxt.color=Color.BLACK; pTxt.textSize=dp(14f); pTxt.textAlign=Paint.Align.CENTER
        cv.drawText("🏪", shopBx, shopBy+dp(5f), pTxt); pTxt.textAlign=Paint.Align.LEFT
    }
}
