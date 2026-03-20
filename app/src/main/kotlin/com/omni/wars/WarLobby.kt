package com.omni.wars

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.*
import android.view.animation.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.credentials.*
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.*
import kotlinx.coroutines.*

class WarLobby : AppCompatActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db   by lazy { FirebaseDatabase.getInstance() }
    private lateinit var credMgr: CredentialManager
    private lateinit var root: FrameLayout
    private var chatListener: ValueEventListener? = null
    private val WEB_CLIENT_ID = "803753518892-ng5db5cc6uc2j6fdu4g19ee3htm54nr4.apps.googleusercontent.com"

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()
    private fun mkGrad(vararg colors: Int, radius: Float = 0f) = GradientDrawable().apply { setColors(colors); cornerRadius = radius }
    private fun mkBtn(text: String, bg: Int, fg: Int = Color.WHITE) = Button(this).apply {
        setText(text); setTextColor(fg)
        background = mkGrad(bg, Color.argb(255,20,20,20), radius = dp(14f).toFloat())
        textSize = 16f; typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(20f), dp(14f), dp(20f), dp(14f))
        isAllCaps = false; stateListAnimator = null
    }
    private fun tv(text: String, size: Float, col: Int = Color.WHITE) = TextView(this).apply {
        setText(text); textSize = size; setTextColor(col)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WarSettings.init(this)
        credMgr = CredentialManager.create(this)
        root = FrameLayout(this)
        setContentView(root)
        showSplash()
    }

    private fun showSplash() {
        root.removeAllViews()
        val bg = View(this).apply { setBackgroundColor(Color.parseColor("#0A0A1A")) }
        root.addView(bg, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        val title = TextView(this).apply {
            text = "OMNI\nWARS"; textSize = 64f; setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setShadowLayer(20f, 0f, 0f, Color.parseColor("#4488FF"))
        }
        val sub = TextView(this).apply {
            text = "3D Online Savaş"; textSize = 18f
            setTextColor(Color.parseColor("#88BBFF")); gravity = Gravity.CENTER
        }
        val progress = ProgressBar(this).apply { isIndeterminate = true }
        val space1 = View(this)
        val space2 = View(this)

        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        }
        ll.addView(title,   ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        ll.addView(sub,     ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        ll.addView(space1,  ViewGroup.LayoutParams.WRAP_CONTENT, dp(32f))
        ll.addView(progress, dp(48f), dp(48f))

        val lp = FrameLayout.LayoutParams(dp(300f), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        root.addView(ll, lp)

        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(title, "scaleX", 0.6f, 1f),
                ObjectAnimator.ofFloat(title, "scaleY", 0.6f, 1f),
                ObjectAnimator.ofFloat(title, "alpha", 0f, 1f)
            )
            duration = 900; interpolator = OvershootInterpolator(1.5f); start()
        }
        ObjectAnimator.ofFloat(title, "scaleX", 1f, 1.03f, 1f).apply {
            duration = 2000; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; startDelay = 1000; start()
        }

        WeaponConfigManager.fetch {}
        lifecycleScope.launch {
            delay(2200)
            if (auth.currentUser != null) showMenu() else showAuthScreen()
        }
    }

    private fun showAuthScreen() {
        root.removeAllViews()
        val bg = View(this).apply { setBackgroundColor(Color.parseColor("#080812")) }
        root.addView(bg, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        val icon = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher); scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val title = tv("OMNI WARS", 38f).apply {
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setShadowLayer(15f, 0f, 0f, Color.parseColor("#3366FF"))
        }
        val sub = tv("Savaşa girmek için giriş yap", 14f, Color.parseColor("#8899AA")).apply { gravity = Gravity.CENTER }
        val btnGoogle = mkBtn("  Google ile Giriş Yap", Color.parseColor("#1A73E8")).apply {
            setOnClickListener { signInGoogle() }
        }
        val divider = tv("──── veya ────", 12f, Color.parseColor("#445566")).apply { gravity = Gravity.CENTER }
        val btnGuest = mkBtn("Misafir Olarak Oyna", Color.parseColor("#2A2A3A"), Color.parseColor("#AABBCC")).apply {
            setOnClickListener { signInAnonymously() }
        }
        val spacerA = View(this); val spacerB = View(this); val spacerC = View(this); val spacerD = View(this)

        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(40f), 0, dp(40f), 0)
        }
        ll.addView(icon,      dp(80f), dp(80f))
        ll.addView(spacerA,   ViewGroup.LayoutParams.MATCH_PARENT, dp(20f))
        ll.addView(title,     ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        ll.addView(sub,       ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        ll.addView(spacerB,   ViewGroup.LayoutParams.MATCH_PARENT, dp(40f))
        ll.addView(btnGoogle, ViewGroup.LayoutParams.MATCH_PARENT, dp(54f))
        ll.addView(spacerC,   ViewGroup.LayoutParams.MATCH_PARENT, dp(16f))
        ll.addView(divider,   ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        ll.addView(spacerD,   ViewGroup.LayoutParams.MATCH_PARENT, dp(16f))
        ll.addView(btnGuest,  ViewGroup.LayoutParams.MATCH_PARENT, dp(50f))

        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        root.addView(ll, lp)
        ll.alpha = 0f
        ll.animate().alpha(1f).translationYBy(-30f).setDuration(600).setInterpolator(DecelerateInterpolator()).start()
    }

    private fun signInGoogle() {
        lifecycleScope.launch {
            try {
                val opt = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false).setServerClientId(WEB_CLIENT_ID).setAutoSelectEnabled(true).build()
                val res  = credMgr.getCredential(this@WarLobby, GetCredentialRequest.Builder().addCredentialOption(opt).build())
                val cred = GoogleIdTokenCredential.createFrom(res.credential.data)
                auth.signInWithCredential(GoogleAuthProvider.getCredential(cred.idToken, null))
                    .addOnSuccessListener { showMenu() }.addOnFailureListener { signInAnonymously() }
            } catch (e: Exception) { signInAnonymously() }
        }
    }

    private fun signInAnonymously() {
        auth.signInAnonymously()
            .addOnSuccessListener { showMenu() }
            .addOnFailureListener { Toast.makeText(this, "Bağlantı hatası!", Toast.LENGTH_SHORT).show() }
    }

    private fun showMenu() {
        root.removeAllViews()
        val bg = View(this).apply { setBackgroundColor(Color.parseColor("#080812")) }
        root.addView(bg, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        val user = auth.currentUser
        val name = if (user?.isAnonymous == true) WarSettings.playerName else (user?.displayName ?: WarSettings.playerName)

        val greeting = tv("Merhaba, $name 👋", 14f, Color.parseColor("#88AACC")).apply { setPadding(dp(20f), dp(50f), dp(20f), 0) }
        val titleV   = tv("OMNI WARS", 44f).apply {
            typeface = Typeface.DEFAULT_BOLD; setPadding(dp(20f), dp(8f), dp(20f), 0)
            setShadowLayer(20f, 0f, 0f, Color.parseColor("#2244FF"))
        }
        val statsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(dp(20f), dp(16f), dp(20f), 0)
        }
        statsRow.addView(tv("💀 ${WarSettings.totalKills} Öldürme", 13f, Color.parseColor("#AABBCC")), ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        statsRow.addView(tv("  🪙 ${WarSettings.savedCoins} Altın", 13f, Color.parseColor("#FFD700")), ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val sp1=View(this); val sp2=View(this); val sp3=View(this); val sp4=View(this)
        val btnPlay     = mkBtn("⚔  SAVAŞA GİR",    Color.parseColor("#1E50CC")).apply { textSize=20f; setOnClickListener { showLobby() } }
        val btnBase     = mkBtn("🏰  ÜS / LOBİ",    Color.parseColor("#1A3A1A")).apply { setOnClickListener { showBase() } }
        val btnChat     = mkBtn("💬  GLOBAL CHAT",  Color.parseColor("#1A1A3A")).apply { setOnClickListener { showGlobalChat() } }
        val btnSettings = mkBtn("⚙  AYARLAR", Color.parseColor("#252525"), Color.parseColor("#AABBCC")).apply { setOnClickListener { showSettingsDialog() } }

        val ll = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        ll.addView(greeting,    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        ll.addView(titleV,      ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        ll.addView(statsRow,    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        ll.addView(sp1,         ViewGroup.LayoutParams.MATCH_PARENT, dp(32f))

        val lpBtn = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62f)).apply { setMargins(dp(20f),0,dp(20f),dp(12f)) }
        val lpBtnSm = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54f)).apply { setMargins(dp(20f),0,dp(20f),dp(10f)) }
        val lpBtnXs = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48f)).apply { setMargins(dp(20f),0,dp(20f),dp(10f)) }

        ll.addView(btnPlay,     lpBtn)
        ll.addView(btnBase,     lpBtnSm)
        ll.addView(btnChat,     lpBtnSm)
        ll.addView(btnSettings, lpBtnXs)

        root.addView(ScrollView(this).also { it.addView(ll) }, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        AnimatorSet().apply {
            playTogether(ObjectAnimator.ofFloat(ll,"translationX",-80f,0f), ObjectAnimator.ofFloat(ll,"alpha",0f,1f))
            duration=500; interpolator=DecelerateInterpolator(); start()
        }
    }

    private fun showLobby() {
        root.removeAllViews()
        val bg = View(this).apply { setBackgroundColor(Color.parseColor("#080812")) }
        root.addView(bg, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        val title  = tv("ODA SEÇ", 32f).apply { typeface = Typeface.DEFAULT_BOLD; setPadding(dp(20f), dp(50f), 0, dp(24f)) }
        val btnCr  = mkBtn("➕  ODA KUR",       Color.parseColor("#1E7A3A")).apply { setOnClickListener { showRoomDialog(true)  } }
        val btnJn  = mkBtn("🚪  ODAYA KATIL",   Color.parseColor("#1E50CC")).apply { setOnClickListener { showRoomDialog(false) } }
        val btnBk  = mkBtn("← Geri", Color.parseColor("#222233"), Color.parseColor("#8899AA")).apply { setOnClickListener { showMenu() } }
        val sp1=View(this); val sp2=View(this)

        val ll = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20f),0,dp(20f),dp(20f)) }
        ll.addView(title, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        ll.addView(btnCr, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58f)).apply { bottomMargin=dp(12f) })
        ll.addView(btnJn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58f)).apply { bottomMargin=dp(24f) })
        ll.addView(btnBk, ViewGroup.LayoutParams.MATCH_PARENT, dp(48f))
        root.addView(ll, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private fun showRoomDialog(create: Boolean) {
        val et = EditText(this).apply {
            hint = "Oda adı girin..."; setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1A2035"))
            setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
        }
        val wrap = FrameLayout(this).apply { setPadding(dp(16f), dp(8f), dp(16f), dp(8f)) }
        wrap.addView(et, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        AlertDialog.Builder(this)
            .setTitle(if (create) "Oda Oluştur" else "Odaya Katıl")
            .setView(wrap)
            .setPositiveButton(if (create) "Oluştur" else "Katıl") { _, _ ->
                val name = et.text.toString().trim()
                if (name.isEmpty()) Toast.makeText(this, "Oda adı boş olamaz!", Toast.LENGTH_SHORT).show()
                else launchGame(name, create)
            }
            .setNegativeButton("İptal", null).show()
    }

    private fun launchGame(roomName: String, isHost: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        startActivity(Intent(this, WarMap::class.java).apply {
            putExtra("ROOM", roomName); putExtra("UID", uid)
            putExtra("HOST", isHost)
            putExtra("NAME", auth.currentUser?.displayName ?: WarSettings.playerName)
        })
    }

    private fun showBase() {
        root.removeAllViews()
        val bg = View(this).apply { setBackgroundColor(Color.parseColor("#0A0A0A")) }
        root.addView(bg, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        val coinTv = tv("🪙 Altın: ${WarSettings.savedCoins}", 24f, Color.parseColor("#FFD700")).apply {
            typeface = Typeface.DEFAULT_BOLD; setPadding(dp(20f), dp(50f), 0, dp(24f))
        }
        val header = tv("⬆ KALICI YÜKSELTMELERİ", 18f, Color.parseColor("#AABBCC")).apply { setPadding(0,0,0,dp(16f)) }

        fun mkRow(label: String, level: Int, cost: Int, onBuy: () -> Unit): LinearLayout {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dp(12f))
            }
            val lbl = tv("$label  Lv$level  →  ${cost}g", 16f)
            lbl.layoutParams = LinearLayout.LayoutParams(0, dp(48f), 1f)
            val btn = mkBtn("AL", Color.parseColor("#224422")).apply {
                layoutParams = LinearLayout.LayoutParams(dp(80f), dp(44f))
                setOnClickListener {
                    if (WarSettings.savedCoins >= cost) {
                        WarSettings.savedCoins -= cost; onBuy()
                        coinTv.text = "🪙 Altın: ${WarSettings.savedCoins}"
                    } else Toast.makeText(context, "Yetersiz altın!", Toast.LENGTH_SHORT).show()
                }
            }
            row.addView(lbl); row.addView(btn); return row
        }

        val btnBack = mkBtn("← Geri", Color.parseColor("#222233"), Color.parseColor("#8899AA")).apply { setOnClickListener { showMenu() } }
        val sp = View(this)

        val ll = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20f),0,dp(20f),dp(20f)) }
        ll.addView(coinTv,   ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        ll.addView(header,   ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        ll.addView(mkRow("⚔ Silah Hasarı", WarSettings.weaponLevel, 80) { WarSettings.weaponLevel++ }, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        ll.addView(mkRow("❤ Temel Can",    WarSettings.healthLevel,  60) { WarSettings.healthLevel++ }, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        ll.addView(mkRow("⚡ Hareket Hızı", WarSettings.speedLevel,   50) { WarSettings.speedLevel++ }, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        ll.addView(sp,     ViewGroup.LayoutParams.MATCH_PARENT, dp(24f))
        ll.addView(btnBack, ViewGroup.LayoutParams.MATCH_PARENT, dp(48f))
        root.addView(ScrollView(this).also { it.addView(ll) }, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private fun showGlobalChat() {
        root.removeAllViews()
        val bg = View(this).apply { setBackgroundColor(Color.parseColor("#080812")) }
        root.addView(bg, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        val msgList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12f),0,dp(12f),dp(8f)) }
        val sv      = ScrollView(this).also { it.addView(msgList) }
        val et      = EditText(this).apply {
            hint = "Mesaj yaz..."; setTextColor(Color.WHITE); setHintTextColor(Color.parseColor("#445566"))
            setBackgroundColor(Color.parseColor("#1A1A2E"))
            setPadding(dp(12f),dp(10f),dp(12f),dp(10f))
        }
        val sendBtn = mkBtn("→", Color.parseColor("#1E50CC"))

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(dp(8f),dp(8f),dp(8f),dp(40f))
            setBackgroundColor(Color.parseColor("#0E0E1E"))
        }
        inputRow.addView(et,      LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        inputRow.addView(sendBtn, LinearLayout.LayoutParams(dp(54f), dp(46f)).apply { setMargins(dp(8f),0,0,0) })

        val titleTv = tv("💬 GLOBAL CHAT", 22f).apply { typeface = Typeface.DEFAULT_BOLD; setPadding(dp(16f),dp(48f),dp(16f),dp(12f)) }
        val btnBack = mkBtn("←", Color.parseColor("#1A1A2A")).apply {
            setOnClickListener {
                chatListener?.let { l -> db.getReference("global_chat").removeEventListener(l) }
                showMenu()
            }
        }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(titleTv,  ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(sv,       LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(inputRow, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val frame = FrameLayout(this)
        frame.addView(col, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        val backLp = FrameLayout.LayoutParams(dp(44f), dp(44f)).apply { topMargin=dp(40f); leftMargin=dp(8f) }
        frame.addView(btnBack, backLp)
        root.addView(frame, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        fun addMsg(name: String, text: String, isMe: Boolean) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = if (isMe) Gravity.END else Gravity.START
                setPadding(dp(4f),dp(4f),dp(4f),dp(4f))
            }
            val bubble = tv(if (!isMe) "$name\n$text" else text, 13f).apply {
                setPadding(dp(10f),dp(8f),dp(10f),dp(8f))
                setBackgroundColor(if(isMe) Color.parseColor("#1E50CC") else Color.parseColor("#1A1A2E"))
            }
            row.addView(bubble, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            msgList.addView(row, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            sv.post { sv.fullScroll(View.FOCUS_DOWN) }
        }

        val chatRef = db.getReference("global_chat")
        chatListener = chatRef.limitToLast(50).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                msgList.removeAllViews()
                val me = auth.currentUser?.uid ?: ""
                for (child in snap.children) {
                    val n = child.child("name").value as? String ?: "?"; val t = child.child("text").value as? String ?: continue
                    addMsg(n, t, child.child("uid").value as? String == me)
                }
            }
            override fun onCancelled(e: DatabaseError) {}
        })

        sendBtn.setOnClickListener {
            val msg = et.text.toString().trim(); if (msg.isEmpty()) return@setOnClickListener
            val me = auth.currentUser ?: return@setOnClickListener
            chatRef.push().setValue(mapOf("uid" to me.uid, "name" to (me.displayName ?: WarSettings.playerName), "text" to msg, "ts" to System.currentTimeMillis()))
            et.text.clear()
        }
    }

    private fun showSettingsDialog() {
        AlertDialog.Builder(this).setTitle("⚙ Ayarlar")
            .setItems(arrayOf(
                "🎨 Grafik: ${WarSettings.quality.label}",
                "🔊 Ses: ${(WarSettings.masterVolume * 100).toInt()}%",
                "📳 Titreşim: ${if (WarSettings.vibrationEnabled) "Açık" else "Kapalı"}",
                "🔍 Kaliteyi Otomatik Belirle"
            )) { _, i ->
                when (i) {
                    0 -> { val q = WarSettings.Quality.values(); WarSettings.quality = q[(WarSettings.quality.ordinal+1) % q.size] }
                    1 -> { WarSettings.masterVolume = if (WarSettings.masterVolume > 0.5f) 0.3f else 1.0f }
                    2 -> { WarSettings.vibrationEnabled = !WarSettings.vibrationEnabled }
                    3 -> WarSettings.autoDetectQuality()
                }
                showSettingsDialog()
            }
            .setNegativeButton("Kapat", null).show()
    }

    override fun onDestroy() { super.onDestroy(); WarSettings.release() }
}
