package com.example.jarvisai

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
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
    private var lastCommand = ""
    private var lastCommandTime = 0L

    private val apiKey = "AQ.Ab8RN6JWH6cIOlov2cgCXKch50esxD-pVloBAk7dlbRk-LgVXQ"

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        tts = TextToSpeech(this, this)
        setupSpeechRecognizer()
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun sendHudUpdate(status: String, message: String = "", state: String = "ACTIVE") {
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
            .setContentTitle("Jarvis Pro Active")
            .setContentText("Background mein 'Jarvis' sun raha hoon...")
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
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.lowercase(Locale.ROOT) ?: ""

                if (spoken.contains("jarvis")) {
                    val cmd = spoken.replace(Regex("(hello|hey|hi|jarvis)"), "").trim()
                    val now = System.currentTimeMillis()

                    if (cmd.isNotEmpty() && cmd == lastCommand && (now - lastCommandTime) < 15000) {
                        restartListening(2000)
                        return
                    }

                    if (cmd.isNotEmpty()) {
                        lastCommand = cmd
                        lastCommandTime = now
                        speechRecognizer.cancel()
                        sendHudUpdate("PROCESSING COMMAND...", "Aapne kaha: $cmd", "ACTIVE")
                        handleCommand(cmd)
                    } else {
                        speak("Ji sir, boliye kya sawaal hai?")
                    }
                } else {
                    restartListening(1200)
                }
            }

            override fun onError(error: Int) {
                isListening = false
                if (!isSpeaking) {
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

    private fun handleCommand(cmd: String) {
        when {
            cmd.contains("instagram") || cmd.contains("insta") -> {
                speak("Instagram open kar raha hoon.", pauseAfterMs = 5000)
                openApp("com.instagram.android", "https://www.instagram.com/")
            }
            cmd.contains("youtube") -> {
                speak("YouTube open kar raha hoon.", pauseAfterMs = 5000)
                openApp("com.google.android.youtube", "https://www.youtube.com/")
            }
            cmd.contains("whatsapp") -> {
                speak("WhatsApp open kar raha hoon.", pauseAfterMs = 5000)
                openApp("com.whatsapp", "https://wa.me/")
            }
            cmd.contains("gmail") || cmd.contains("email") -> {
                speak("Gmail open kar raha hoon.", pauseAfterMs = 5000)
                openApp("com.google.android.gm", "https://mail.google.com/")
            }
            cmd.contains("chrome") || cmd.contains("browser") -> {
                speak("Chrome open kar raha hoon.", pauseAfterMs = 5000)
                openApp("com.android.chrome", "https://www.google.com/")
            }
            cmd.contains("camera") -> {
                speak("Camera open kar raha hoon.", pauseAfterMs = 5000)
                val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
            cmd.contains("volume") && (cmd.contains("up") || cmd.contains("badha") || cmd.contains("tez") || cmd.contains("full")) -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
                speak("Volume badha diya hai sir.")
            }
            cmd.contains("volume") && (cmd.contains("down") || cmd.contains("kam") || cmd.contains("low")) -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
                speak("Volume kam kar diya hai sir.")
            }
            cmd.contains("time") || cmd.contains("samay") || cmd.contains("kitne baje") -> {
                val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                speak("Abhi $time ho rahe hain sir.")
            }
            cmd.contains("date") || cmd.contains("tarikh") || cmd.contains("aaj ka din") -> {
                val date = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()).format(Date())
                speak("Aaj $date hai sir.")
            }
            cmd.contains("battery") -> {
                val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                speak("Phone ki battery abhi $level percent hai sir.")
            }
            cmd.contains("call") || cmd.contains("phone") -> {
                val target = cmd.replace(Regex("\\b(call|phone|lagao|karo|ko|ke|pass|paas)\\b"), "").trim()
                callContact(target)
            }
            else -> askGemini(cmd)
        }
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
            speak("Kisko call lagani hai, naam boliye sir.")
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
                    speak("$name ko call laga raha hoon.", pauseAfterMs = 6000)
                    val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
                    callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(callIntent)
                    return
                }
            }
        }
        speak("Yeh naam contacts mein nahi mila sir.")
    }

    private fun askGemini(question: String) {
        isSpeaking = true
        speechRecognizer.cancel()
        sendHudUpdate("THINKING...", "Sawaal: $question", "ACTIVE")

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
        val prompt = "Tum Jarvis Pro AI assistant ho. User ke kisi bhi sawaal (GK, study, maths, coding, daily life) ka exact aur smart jawaab 2 se 3 chhoti lines mein natural Hinglish mein do. Koi emoji, star, ya markdown symbol mat lagana. Sawaal: $question"

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
                speak("Internet connection check kijiye sir.")
            }
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                try {
                    val rawReply = JSONObject(body)
                        .getJSONArray("candidates").getJSONObject(0)
                        .getJSONObject("content").getJSONArray("parts")
                        .getJSONObject(0).getString("text")
                    val cleanReply = rawReply.replace(Regex("[*#`_]"), "").trim()
                    speak(cleanReply)
                } catch (e: Exception) {
                    speak("AI server se jawaab nahi aaya, apni Gemini API key check kijiye sir.")
                }
            }
        })
    }

    private fun speak(text: String, pauseAfterMs: Long = 2000) {
        mainHandler.post {
            isSpeaking = true
            isListening = false
            speechRecognizer.cancel()
            muteBeep(false)
            sendHudUpdate("JARVIS SPEAKING", text, "SPEAKING")

            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    isSpeaking = false
                    sendHudUpdate("ACTIVE — LISTENING FOR 'JARVIS'", text, "ACTIVE")
                    restartListening(pauseAfterMs)
                }
                override fun onError(utteranceId: String?) {
                    isSpeaking = false
                    sendHudUpdate("ACTIVE — LISTENING FOR 'JARVIS'", text, "ACTIVE")
                    restartListening(pauseAfterMs)
                }
            })

            val params = Bundle()
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "jarvis_utterance")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("en", "IN")
            speak("Jarvis Pro 3D core active ho gaya hai sir.")
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
