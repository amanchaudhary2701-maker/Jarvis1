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
            val message = intent.getStringExtra("message") ?: ""
            val state = intent.getStringExtra("state") ?: "IDLE"

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
            setBackgroundColor(Color.parseColor("#030712"))
            setPadding(52, 85, 52, 60)
        }

        val title = TextView(this).apply {
            text = "J.A.R.V.I.S  PRO"
            textSize = 26f
            setTextColor(Color.parseColor("#00F5FF"))
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.28f
            gravity = Gravity.CENTER
        }

        statusText = TextView(this).apply {
            text = "NEURAL CORE STANDBY"
            textSize = 13f
            setTextColor(Color.parseColor("#67E8F9"))
            letterSpacing = 0.14f
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 20)
        }

        orbView = JarvisPro3DHudView(this).apply {
            layoutParams = LinearLayout.LayoutParams(760, 760)
        }

        responseText = TextView(this).apply {
            text = "Boliye 'Jarvis' aur koi bhi sawaal puchiye..."
            textSize = 14f
            setTextColor(Color.parseColor("#CBD5E1"))
            gravity = Gravity.CENTER
            setPadding(32, 26, 32, 26)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F172A"))
                setStroke(2, Color.parseColor("#1E3A5F"))
                cornerRadius = 28f
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 20
                bottomMargin = 32
            }
        }

        val startBtn = Button(this).apply {
            text = "ACTIVATE JARVIS PRO"
            setTextColor(Color.parseColor("#030712"))
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#00F5FF"))
                cornerRadius = 48f
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                132
            )
            setOnClickListener {
                startForegroundService(Intent(this@MainActivity, JarvisService::class.java))
                statusText.text = "ACTIVE — LISTENING FOR 'JARVIS'"
                orbView.setMode("ACTIVE")
            }
        }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                122
            ).apply { topMargin = 24 }
        }

        val stopBtn = Button(this).apply {
            text = "DEACTIVATE"
            setTextColor(Color.parseColor("#FF4D6D"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F172A"))
                setStroke(3, Color.parseColor("#FF4D6D"))
                cornerRadius = 44f
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                marginEnd = 14
            }
            setOnClickListener {
                stopService(Intent(this@MainActivity, JarvisService::class.java))
                statusText.text = "SYSTEM OFFLINE"
                responseText.text = "Jarvis band kar diya gaya hai."
                orbView.setMode("IDLE")
            }
        }

        val adminBtn = Button(this).apply {
            text = "ADMIN PANEL"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F172A"))
                setStroke(3, Color.parseColor("#38BDF8"))
                cornerRadius = 44f
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                marginStart = 14
            }
            setOnClickListener {
                val adminUrl = "https://amanchaudhary2701-maker.github.io/Jarvis1/admin.html"
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(adminUrl)))
            }
        }

        btnRow.addView(stopBtn)
        btnRow.addView(adminBtn)

        root.addView(title)
        root.addView(statusText)
        root.addView(orbView)
        root.addView(responseText)
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
    private var mode = "IDLE" // IDLE, ACTIVE, SPEAKING

    private val particleAngles = FloatArray(16) { it * 22.5f }
    private val particleSpeeds = FloatArray(16) { 0.6f + Random.nextFloat() * 1.4f }
    private val particleRadii = FloatArray(16) { 0.72f + Random.nextFloat() * 0.48f }

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
            duration = 4200
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
        val cy = height * 0.44f
        val pulseAmp = when (mode) {
            "SPEAKING" -> 22f
            "ACTIVE" -> 12f
            else -> 5f
        }
        val speedMult = if (mode == "SPEAKING") 1.8f else 1f
        val primaryColor = if (mode == "SPEAKING") Color.parseColor("#38BDF8") else Color.parseColor("#00F5FF")
        val secondaryColor = if (mode == "SPEAKING") Color.parseColor("#A855F7") else Color.parseColor("#0284C7")

        val pulse = sin(Math.toRadians((angle * 2 * speedMult).toDouble())).toFloat()
        val baseRadius = width * 0.30f + (pulse * pulseAmp)

        // Glowing 3D Energy Core
        corePaint.shader = RadialGradient(
            cx, cy, baseRadius * 0.85f,
            intArrayOf(
                Color.parseColor("#E600F5FF"),
                Color.parseColor("#550284C7"),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.50f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, baseRadius * 0.85f, corePaint)

        // Outer Radar Ring
        dashedPaint.color = secondaryColor
        dashedPaint.alpha = 200
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(angle * 0.7f * speedMult)
        canvas.drawCircle(0f, 0f, baseRadius * 1.18f, dashedPaint)
        canvas.restore()

        // 3D Gyroscope Rings
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

        // Orbiting 3D Particles
        particlePaint.color = primaryColor
        for (i in particleAngles.indices) {
            val rad = Math.toRadians(((angle * particleSpeeds[i] * speedMult) + particleAngles[i]).toDouble())
            val px = cx + (cos(rad) * baseRadius * particleRadii[i]).toFloat()
            val py = cy + (sin(rad) * baseRadius * particleRadii[i] * 0.55f).toFloat()
            val size = 3.5f + ((sin(rad).toFloat() + 1f) * 2.5f)
            particlePaint.alpha = (110 + ((sin(rad).toFloat() + 1f) * 70)).toInt().coerceIn(80, 255)
            canvas.drawCircle(px, py, size, particlePaint)
        }

        // Bottom Audio Equalizer Bars
        val barCount = 15
        val barWidth = 10f
        val barGap = 14f
        val totalWidth = barCount * (barWidth + barGap)
        val startX = cx - totalWidth / 2f
        val baseY = height * 0.90f
        wavePaint.color = primaryColor

        for (i in 0 until barCount) {
            val phase = Math.toRadians((angle * 4 * speedMult + i * 28).toDouble())
            val maxH = when (mode) {
                "SPEAKING" -> 68f
                "ACTIVE" -> 34f
                else -> 12f
            }
            val barH = 10f + ((sin(phase).toFloat() + 1f) * 0.5f * maxH)
            val left = startX + i * (barWidth + barGap)
            canvas.drawRoundRect(left, baseY - barH, left + barWidth, baseY, 6f, 6f, wavePaint)
        }
    }
}
