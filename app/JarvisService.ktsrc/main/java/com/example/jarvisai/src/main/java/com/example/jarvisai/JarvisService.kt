package com.example.jarvisai

import android.Manifest
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
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
import kotlin.math.sin

class MainActivity : AppCompatActivity() {

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
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#050A14"))
            setPadding(60, 100, 60, 100)
        }

        val title = TextView(this).apply {
            text = "J.A.R.V.I.S  AI"
            textSize = 26f
            setTextColor(Color.parseColor("#00F0FF"))
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.25f
            gravity = Gravity.CENTER
        }

        val statusText = TextView(this).apply {
            text = "3D Neural Core Ready"
            textSize = 14f
            setTextColor(Color.parseColor("#7AA2C4"))
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 40)
        }

        val orbView = Jarvis3DOrbView(this).apply {
            layoutParams = LinearLayout.LayoutParams(720, 720)
        }

        val startBtn = Button(this).apply {
            text = "ACTIVATE JARVIS"
            setTextColor(Color.parseColor("#050A14"))
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#00F0FF"))
                cornerRadius = 50f
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                140
            ).apply { topMargin = 60 }

            setOnClickListener {
                startForegroundService(Intent(this@MainActivity, JarvisService::class.java))
                statusText.text = "Background Active — Boliye 'Jarvis'"
                orbView.setActive(true)
            }
        }

        val stopBtn = Button(this).apply {
            text = "DEACTIVATE"
            setTextColor(Color.parseColor("#FF4D6D"))
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#111B2E"))
                setStroke(3, Color.parseColor("#FF4D6D"))
                cornerRadius = 50f
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                130
            ).apply { topMargin = 30 }

            setOnClickListener {
                stopService(Intent(this@MainActivity, JarvisService::class.java))
                statusText.text = "Jarvis Offline"
                orbView.setActive(false)
            }
        }

        root.addView(title)
        root.addView(statusText)
        root.addView(orbView)
        root.addView(startBtn)
        root.addView(stopBtn)
        setContentView(root)
    }
}

class Jarvis3DOrbView(context: Context) : View(context) {
    private var angle = 0f
    private var pulse = 0f
    private var isActive = false

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.parseColor("#00F0FF")
    }

    private val dashedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        color = Color.parseColor("#00A8FF")
        pathEffect = DashPathEffect(floatArrayOf(28f, 18f), 0f)
    }

    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    init {
        ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 5000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                angle = it.animatedValue as Float
                pulse = sin(Math.toRadians(angle.toDouble() * 2)).toFloat()
                invalidate()
            }
            start()
        }
    }

    fun setActive(active: Boolean) {
        isActive = active
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val baseRadius = width * 0.34f + (pulse * if (isActive) 16f else 6f)

        // Glowing 3D Core
        corePaint.shader = RadialGradient(
            cx, cy, baseRadius * 0.75f,
            intArrayOf(
                Color.parseColor("#CC00F0FF"),
                Color.parseColor("#440077FF"),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, baseRadius * 0.75f, corePaint)

        // 3D Gyroscopic Ring 1
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(angle)
        canvas.scale(1f, 0.42f)
        ringPaint.alpha = 220
        canvas.drawCircle(0f, 0f, baseRadius, ringPaint)
        canvas.restore()

        // 3D Gyroscopic Ring 2
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(-angle * 1.3f + 60f)
        canvas.scale(0.45f, 1f)
        ringPaint.alpha = 180
        canvas.drawCircle(0f, 0f, baseRadius * 0.92f, ringPaint)
        canvas.restore()

        // 3D Gyroscopic Ring 3 (Dashed Orbit)
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(angle * 0.8f + 120f)
        canvas.scale(1f, 0.65f)
        canvas.drawCircle(0f, 0f, baseRadius * 1.08f, dashedPaint)
        canvas.restore()
    }
}
