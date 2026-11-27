package com.example.jarvisai

import android.Manifest
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var responseText: TextView
    private lateinit var orbView: JarvisPro3DHudView

    private val hudReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getStringExtra("status") ?: return
            val message = intent?.getStringExtra("message") ?: ""
            val state = intent?.getStringExtra("state") ?: "SLEEP"

            statusText.text = status
            if (message.isNotEmpty()) {
                responseText.text = message
            }
            orbView.setMode(state)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 101)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#020617"))
            setPadding(48, 70, 48, 48)
        }

        val title = TextView(this).apply {
            text = "J.A.R.V.I.S  PRO"
            textSize = 26f
            setTextColor(Color.parseColor("#00F5FF"))
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.25f
            gravity = Gravity.CENTER
        }

        statusText = TextView(this).apply {
            text = "STANDBY — SAY 'JARVIS' TO WAKE"
            textSize = 12f
            setTextColor(Color.parseColor("#67E8F9"))
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 16)
        }

        orbView = JarvisPro3DHudView(this).apply {
            layoutParams = LinearLayout.LayoutParams(680, 680)
        }

        val scrollBox = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                320
            ).apply {
                topMargin = 14
                bottomMargin = 22
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0B1324"))
                setStroke(2, Color.parseColor("#1E3A5F"))
                cornerRadius = 24f
            }
            setPadding(24, 20, 24, 20)
        }

        responseText = TextView(this).apply {
            text = "Boliye 'Jarvis' aur koi bhi sawaal puchiye ya 'Jarvis phone ka analysis karo' boliye."
            textSize = 14f
            setTextColor(Color.parseColor("#E2E8F0"))
        }
        scrollBox.addView(responseText)

        val startBtn = Button(this).apply {
            text = "ACTIVATE JARVIS PRO"
            setTextColor(Color.parseColor("#020617"))
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#00F5FF"))
                cornerRadius = 44f
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                126
            )
            setOnClickListener {
                startForegroundService(Intent(this@MainActivity, JarvisService::class.java))
                statusText.text = "SLEEP MODE — SAY 'JARVIS'"
                orbView.setMode("SLEEP")
            }
        }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                114
            ).apply { topMargin = 20 }
        }

        val stopBtn = Button(this).apply {
            text = "DEACTIVATE"
            setTextColor(Color.parseColor("#FF4D6D"))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0B1324"))
                setStroke(2, Color.parseColor("#FF4D6D"))
                cornerRadius = 40f
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                marginEnd = 10
            }
            setOnClickListener {
                stopService(Intent(this@MainActivity, JarvisService::class.java))
                statusText.text = "SYSTEM OFFLINE"
                responseText.text = "Jarvis band kar diya gaya hai."
                orbView.setMode("IDLE")
            }
        }

        val analyzeBtn = Button(this).apply {
            text = "PHONE ANALYSIS"
            setTextColor(Color.parseColor("#A855F7"))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0B1324"))
                setStroke(2, Color.parseColor("#A855F7"))
                cornerRadius = 40f
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                marginStart = 5
                marginEnd = 5
            }
            setOnClickListener {
                val intent = Intent(this@MainActivity, JarvisService::class.java).apply {
                    action = "ACTION_ANALYZE_PHONE"
                }
                startForegroundService(intent)
            }
        }

        val adminBtn = Button(this).apply {
            text = "ADMIN PANEL"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0B1324"))
                setStroke(2, Color.parseColor("#38BDF8"))
                cornerRadius = 40f
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                marginStart = 10
            }
            setOnClickListener {
                val adminUrl = "https://amanchaudhary2701-maker.github.io/Jarvis1/admin.html"
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(adminUrl)))
            }
        }

        btnRow.addView(stopBtn)
        btnRow.addView(analyzeBtn)
        btnRow.addView(adminBtn)

        root.addView(title)
        root.addView(statusText)
        root.addView(orbView)
        root.addView(scrollBox)
        root.addView(startBtn)
        root.addView(btnRow)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter("com.example.jarvisai.HUD_UPDATE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(hudReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(hudReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(hudReceiver)
        } catch (_: Exception) {}
    }
}

class JarvisPro3DHudView(context: Context) : View(context) {
    private var angle = 0f
    private var mode = "IDLE"

    private val particleAngles = FloatArray(18) { it * 20f }
    private val particleSpeeds = FloatArray(18) { 0.6f + Random.nextFloat() * 1.4f }
    private val particleRadii = FloatArray(18) { 0.70f + Random.nextFloat() * 0.50f }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private val dashedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 7f
        pathEffect = DashPathEffect(floatArrayOf(24f, 16f), 0f)
    }

    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    init {
        ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 4000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                angle = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun setMode(newMode: String) {
        mode = newMode
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height * 0.43f

        val (pulseAmp, speedMult, primaryColor, secondaryColor) = when (mode) {
            "SPEAKING" -> Quad(24f, 1.9f, Color.parseColor("#38BDF8"), Color.parseColor("#A855F7"))
            "LISTENING" -> Quad(16f, 1.4f, Color.parseColor("#00F5FF"), Color.parseColor("#22D3EE"))
            "SLEEP" -> Quad(6f, 0.7f, Color.parseColor("#0284C7"), Color.parseColor("#1E3A8A"))
            else -> Quad(4f, 0.6f, Color.parseColor("#475569"), Color.parseColor("#1E293B"))
        }

        val pulse = sin(Math.toRadians((angle * 2 * speedMult).toDouble())).toFloat()
        val baseRadius = width * 0.29f + (pulse * pulseAmp)

        corePaint.shader = RadialGradient(
            cx, cy, baseRadius * 0.85f,
            intArrayOf(
                primaryColor,
                secondaryColor,
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        corePaint.alpha = if (mode == "SLEEP") 140 else 220
        canvas.drawCircle(cx, cy, baseRadius * 0.85f, corePaint)

        dashedPaint.color = secondaryColor
        dashedPaint.alpha = 200
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(angle * 0.7f * speedMult)
        canvas.drawCircle(0f, 0f, baseRadius * 1.16f, dashedPaint)
        canvas.restore()

        ringPaint.color = primaryColor
        for (i in 0..2) {
            canvas.save()
            canvas.translate(cx, cy)
            val rot = if (i % 2 == 0) angle * (1f + i * 0.35f) * speedMult else -angle * (1.2f + i * 0.2f) * speedMult
            canvas.rotate(rot + i * 60f)
            canvas.scale(1f, 0.38f + i * 0.12f)
            ringPaint.alpha = 230 - i * 35
            canvas.drawCircle(0f, 0f, baseRadius * (0.92f + i * 0.08f), ringPaint)
            canvas.restore()
        }

        particlePaint.color = primaryColor
        for (i in particleAngles.indices) {
            val rad = Math.toRadians(((angle * particleSpeeds[i] * speedMult) + particleAngles[i]).toDouble())
            val px = cx + (cos(rad) * baseRadius * particleRadii[i]).toFloat()
            val py = cy + (sin(rad) * baseRadius * particleRadii[i] * 0.55f).toFloat()
            val size = 3.5f + ((sin(rad).toFloat() + 1f) * 2.5f)
            particlePaint.alpha = (110 + ((sin(rad).toFloat() + 1f) * 70)).toInt().coerceIn(80, 255)
            canvas.drawCircle(px, py, size, particlePaint)
        }

        val barCount = 15
        val barWidth = 10f
        val barGap = 13f
        val totalWidth = barCount * (barWidth + barGap)
        val startX = cx - totalWidth / 2f
        val baseY = height * 0.89f
        wavePaint.color = primaryColor

        for (i in 0 until barCount) {
            val phase = Math.toRadians((angle * 4 * speedMult + i * 28).toDouble())
            val maxH = when (mode) {
                "SPEAKING" -> 66f
                "LISTENING" -> 42f
                "SLEEP" -> 10f
                else -> 6f
            }
            val barH = 8f + ((sin(phase).toFloat() + 1f) * 0.5f * maxH)
            val left = startX + i * (barWidth + barGap)
            canvas.drawRoundRect(left, baseY - barH, left + barWidth, baseY, 6f, 6f, wavePaint)
        }
    }

    private data class Quad(val a: Float, val b: Float, val c: Int, val d: Int)
}
