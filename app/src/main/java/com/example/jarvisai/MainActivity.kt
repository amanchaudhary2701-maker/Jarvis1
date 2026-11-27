package com.example.jarvisai

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 101)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(80, 200, 80, 80)
        }

        val startBtn = Button(this).apply {
            text = "Start Jarvis (Background)"
            setOnClickListener {
                val intent = Intent(this@MainActivity, JarvisService::class.java)
                startForegroundService(intent)
            }
        }

        val stopBtn = Button(this).apply {
            text = "Stop Jarvis"
            setOnClickListener {
                stopService(Intent(this@MainActivity, JarvisService::class.java))
            }
        }

        layout.addView(startBtn)
        layout.addView(stopBtn)
        setContentView(layout)
    }
}
