package com.example.jarvisai

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.*
import android.provider.ContactsContract
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class JarvisService : Service(), TextToSpeech.OnInitListener {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var tts: TextToSpeech
    private lateinit var audioManager: AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient()

    private var isSpeaking = false
    private var isListening = false
    private var isAwakeForCommand = false
    private var lastCommand = ""
    private var lastCommandTime = 0L

    private fun getApiKey(): String {
        val prefs = getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
        return prefs.getString("gemini_api_key", "") ?: ""
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        tts = TextToSpeech(this, this)
        setupSpeechRecognizer()
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_ANALYZE_PHONE") {
            mainHandler.postDelayed({ performFullPhoneAnalysis() }, 500)
        }
        return START_STICKY
    }

    private fun sendHudUpdate(status: String, message: String = "", state: String = "SLEEP") {
        val intent = Intent("com.example.jarvisai.HUD_UPDATE").apply {
            setPackage(packageName)
            putExtra("status", status)
            putExtra("message", message)
            putExtra("state", state)
        }
        sendBroadcast(intent)
    }

    private fun startForegroundNotification() {
        val channelId = "jarvis_channel"
        val channel = NotificationChannel(channelId, "Jarvis Pro Assistant", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Jarvis Pro Standby")
            .setContentText("'Jarvis' bolne par activate hoga...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, notification)
        }
    }

    private fun muteBeep(mute: Boolean) {
        try {
            val direction = if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
            audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, direction, 0)
            audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, direction, 0)
        } catch (_: Exception) {}
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                isListening = false
                if (audioManager.isMusicActive && !isAwakeForCommand) {
                    restartListening(2500)
                    return
                }

                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.lowercase(Locale.ROOT)?.trim() ?: ""

                if (spoken.isEmpty()) {
                    restartListening(1400)
                    return
                }

                if (isAwakeForCommand) {
                    isAwakeForCommand = false
                    val cleanCmd = spoken.replace(Regex("\b(hello|hey|hi|jarvis)\b"), "").trim()
                    if (cleanCmd.isNotEmpty()) {
                        processCommand(cleanCmd)
                    } else {
                        goToSleep("Command sunai nahi diya, mic band kar raha hoon. Dobara 'Jarvis' boliye.")
                    }
                    return
                }

                if (spoken.contains("jarvis")) {
                    val afterWake = spoken.substringAfter("jarvis").replace(Regex("\b(hello|hey|hi)\b"), "").trim()
                    if (afterWake.isNotEmpty()) {
                        processCommand(afterWake)
                    } else {
                        isAwakeForCommand = true
                        sendHudUpdate("AWAKE — COMMAND BOLIYE", "Jarvis active hai, apna command boliye...", "LISTENING")
                        speakAndListenOnce("Ji sir, boliye kya karna hai?")
                    }
                } else {
                    restartListening(1400)
                }
            }

            override fun onError(error: Int) {
                isListening = false
                if (isAwakeForCommand) {
                    isAwakeForCommand = false
                    goToSleep("Koi command nahi mila, mic band kar diya hai.")
                } else if (!isSpeaking) {
                    restartListening(1800)
                }
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startListening() {
        if (isSpeaking || isListening) return

        // Jab Instagram Reels, YouTube ya koi video/audio chal raha ho to mic ko pause rakho
        if (audioManager.isMusicActive && !isAwakeForCommand) {
            sendHudUpdate("MEDIA PLAYING — MIC PAUSED", "Video/Audio chal raha hai, mic standby mein paused hai...", "SLEEP")
            restartListening(2500)
            return
        }

        isListening = true
        muteBeep(true)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        }
        try {
            speechRecognizer.startListening(intent)
        } catch (_: Exception) {
            isListening = false
            restartListening(2000)
        }
    }

    private fun restartListening(delayMs: Long) {
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({ startListening() }, delayMs)
    }

    private fun processCommand(cmd: String) {
        val now = System.currentTimeMillis()
        if (cmd == lastCommand && (now - lastCommandTime) < 8000) {
            goToSleep("Yeh command abhi chal chuka hai.")
            return
        }
        lastCommand = cmd
        lastCommandTime = now
        speechRecognizer.cancel()
        sendHudUpdate("EXECUTING: $cmd", "Command: $cmd", "SPEAKING")

        when {
            cmd.contains("analysis") || cmd.contains("analyze") || cmd.contains("phone status") || cmd.contains("device info") -> {
                performFullPhoneAnalysis()
            }
            cmd.contains("instagram") || cmd.contains("insta") -> {
                openApp("com.instagram.android", "https://www.instagram.com/")
                goToSleep("Instagram open kar raha hoon.", pauseAfterMs = 4500)
            }
            cmd.contains("youtube") -> {
                openApp("com.google.android.youtube", "https://www.youtube.com/")
                goToSleep("YouTube open kar raha hoon.", pauseAfterMs = 4500)
            }
            cmd.contains("whatsapp") -> {
                openApp("com.whatsapp", "https://wa.me/")
                goToSleep("WhatsApp open kar raha hoon.", pauseAfterMs = 4000)
            }
            cmd.contains("gmail") || cmd.contains("email") -> {
                openApp("com.google.android.gm", "https://mail.google.com/")
                goToSleep("Gmail open kar raha hoon.", pauseAfterMs = 4000)
            }
            cmd.contains("camera") -> {
                val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                goToSleep("Camera open kar raha hoon.", pauseAfterMs = 4000)
            }
            cmd.contains("volume") && (cmd.contains("up") || cmd.contains("badha") || cmd.contains("tez") || cmd.contains("full")) -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                goToSleep("Volume badha diya hai sir.")
            }
            cmd.contains("volume") && (cmd.contains("down") || cmd.contains("kam") || cmd.contains("low")) -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                goToSleep("Volume kam kar diya hai sir.")
            }
            cmd.contains("time") || cmd.contains("samay") || cmd.contains("kitne baje") -> {
                val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                goToSleep("Abhi $time ho rahe hain sir.")
            }
            cmd.contains("date") || cmd.contains("tarikh") -> {
                val date = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()).format(Date())
                goToSleep("Aaj $date hai sir.")
            }
            cmd.contains("call") || cmd.contains("phone") -> {
                val target = cmd.replace(Regex("\b(call|phone|lagao|karo|ko|ke|pass|paas)\b"), "").trim()
                callContact(target)
            }
            else -> askGeminiAndSleep(cmd)
        }
    }

    private fun performFullPhoneAnalysis() {
        sendHudUpdate("ANALYZING PHONE...", "Phone ka full system analysis kar raha hoon...", "SPEAKING")

        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val batteryPct = if (level >= 0) (level * 100) / scale else -1
        val statusCode = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val chargingState = if (statusCode == BatteryManager.BATTERY_STATUS_CHARGING || statusCode == BatteryManager.BATTERY_STATUS_FULL) "Charging" else "Not Charging"

        val actManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        val availRamGb = String.format(Locale.US, "%.2f", memInfo.availMem / (1024.0 * 1024.0 * 1024.0))
        val totalRamGb = String.format(Locale.US, "%.2f", memInfo.totalMem / (1024.0 * 1024.0 * 1024.0))

        val statFs = StatFs(Environment.getDataDirectory().path)
        val freeStorageGb = String.format(Locale.US, "%.1f", statFs.availableBytes / (1024.0 * 1024.0 * 1024.0))
        val totalStorageGb = String.format(Locale.US, "%.1f", statFs.totalBytes / (1024.0 * 1024.0 * 1024.0))

        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNet = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(activeNet)
        val netType = when {
            caps == null -> "Offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi Connected"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile Data Active"
            else -> "Connected"
        }

        val installedCount = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0)).size
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstalledApplications(0).size
            }
        } catch (_: Exception) { 0 }

        val reportText = """
            PHONE FULL ANALYSIS:
            • Model: ${Build.MANUFACTURER.uppercase()} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})
            • Battery: $batteryPct% ($chargingState)
            • RAM: $availRamGb GB free / $totalRamGb GB total
            • Storage: $freeStorageGb GB free / $totalStorageGb GB total
            • Network: $netType
            • Installed Packages: $installedCount
        """.trimIndent()

        val spokenSummary = "Phone analysis complete sir. Battery $batteryPct percent hai, free RAM $availRamGb GB hai, aur free storage $freeStorageGb GB bacha hai. Network $netType hai."
        goToSleep(spokenSummary, hudMessage = reportText)
    }

    private fun openApp(packageName: String, fallbackUrl: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(launchIntent)
        } else {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl))
            webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(webIntent)
        }
    }

    private fun callContact(targetName: String) {
        if (targetName.length < 2) {
            goToSleep("Kisko call lagani hai, naam clear nahi mila.")
            return
        }
        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ), null, null, null
        )
        cursor?.use {
            while (it.moveToNext()) {
                val name = it.getString(0)?.lowercase(Locale.ROOT) ?: ""
                val number = it.getString(1) ?: ""
                if (name.contains(targetName)) {
                    val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
                    callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(callIntent)
                    goToSleep("$name ko call laga raha hoon.", pauseAfterMs = 5000)
                    return
                }
            }
        }
        goToSleep("Yeh naam contacts mein nahi mila sir.")
    }

    private fun askGeminiAndSleep(question: String) {
        val key = getApiKey()
        if (!key.startsWith("AIza")) {
            goToSleep("Pehle Jarvis app mein SET GEMINI API KEY button dabakar apni AIzaSy wali API key save kijiye sir.")
            return
        }

        isSpeaking = true
        speechRecognizer.cancel()
        sendHudUpdate("THINKING...", "Sawaal: $question", "SPEAKING")

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$key"
        val prompt = "Tum Jarvis Pro AI assistant ho. User ke kisi bhi sawaal ka exact aur clear jawaab 2 se 3 chhoti lines mein natural Hinglish mein do. Koi emoji ya symbol mat lagana. Sawaal: $question"

        val jsonBody = JSONObject().put(
            "contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            )
        )
        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                goToSleep("Internet connection check kijiye sir.")
            }
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                try {
                    val rawReply = JSONObject(body)
                        .getJSONArray("candidates").getJSONObject(0)
                        .getJSONObject("content").getJSONArray("parts")
                        .getJSONObject(0).getString("text")
                    val cleanReply = rawReply.replace(Regex("[*#`_]"), "").trim()
                    goToSleep(cleanReply)
                } catch (e: Exception) {
                    goToSleep("Jawaab nahi aaya, SET GEMINI API KEY mein valid key check kijiye sir.")
                }
            }
        })
    }

    private fun speakAndListenOnce(text: String) {
        mainHandler.post {
            isSpeaking = true
            isListening = false
            speechRecognizer.cancel()
            muteBeep(false)

            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    isSpeaking = false
                    restartListening(500)
                }
                override fun onError(utteranceId: String?) {
                    isSpeaking = false
                    restartListening(500)
                }
            })

            tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), "jarvis_wake")
        }
    }

    private fun goToSleep(spokenText: String, hudMessage: String = spokenText, pauseAfterMs: Long = 1600) {
        mainHandler.post {
            isSpeaking = true
            isListening = false
            isAwakeForCommand = false
            speechRecognizer.cancel()
            mainHandler.removeCallbacksAndMessages(null)
            muteBeep(false)
            sendHudUpdate("JARVIS SPEAKING", hudMessage, "SPEAKING")

            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    isSpeaking = false
                    sendHudUpdate("SLEEP MODE — SAY 'JARVIS'", hudMessage, "SLEEP")
                    restartListening(pauseAfterMs)
                }
                override fun onError(utteranceId: String?) {
                    isSpeaking = false
                    sendHudUpdate("SLEEP MODE — SAY 'JARVIS'", hudMessage, "SLEEP")
                    restartListening(pauseAfterMs)
                }
            })

            tts.speak(spokenText, TextToSpeech.QUEUE_FLUSH, Bundle(), "jarvis_sleep")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("en", "IN")
            goToSleep("Jarvis Pro ready hai. Jab zaroorat ho, Jarvis boliye.")
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        muteBeep(false)
        speechRecognizer.destroy()
        tts.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
