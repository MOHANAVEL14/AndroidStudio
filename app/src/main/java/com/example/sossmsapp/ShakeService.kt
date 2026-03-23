package com.example.sossmsapp

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraManager
import android.location.Location
import android.media.AudioManager
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.*
import android.provider.Settings
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import com.example.sossmsapp.risk.RiskEngine
import java.io.File
import java.util.*
import kotlin.math.sqrt

// CameraX & Lifecycle
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.example.sossmsapp.data.firebase.TriggerLogger
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ShakeService : LifecycleService(), SensorEventListener {

    private var mediaRecorder: MediaRecorder? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var dynamicThreshold = 12.0f
    private var lastShakeTime: Long = 0
    private val COOLDOWN_MS = 5000

    private var volumeObserver: ContentObserver? = null
    private var volumePressCount = 0
    private var lastPressTime: Long = 0
    private val RESET_TIME_MS = 2000

    private lateinit var audioManager: AudioManager
    private var lastVolumeLevel = -1

    private val handler = Handler(Looper.getMainLooper())
    private var sosRunnable: Runnable? = null
    private val ALERT_NOTIFICATION_ID = 100
    private val STATUS_NOTIFICATION_ID = 1
    private val CHANNEL_ID = "SOS_SERVICE_CHANNEL"

    private val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
    private val torchHandler = Handler(Looper.getMainLooper())
    private var torchRunnable: Runnable? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // --- TRAVEL MODE VARIABLES ---
    private var isTravelMonitoring = false
    private var lastKnownDistance = -1f
    private var currentDisplayDistance = ""
    private val DEVIATION_THRESHOLD = 200f
    private val VISUAL_WARNING_THRESHOLD = 20f
    private val JITTER_FILTER_METERS = 7.0f
    private var locationCallback: LocationCallback? = null
    private val riskEngine = RiskEngine()
    private lateinit var liveLocationManager: com.example.sossmsapp.features.tracking.LiveLocationManager

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        cameraExecutor = Executors.newSingleThreadExecutor()
        createNotificationChannel()
        setupVolumeObserver()
        liveLocationManager = com.example.sossmsapp.features.tracking.LiveLocationManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // CRITICAL: Call startForeground immediately
        updateStatusNotification()

        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            "ACTION_SAFE" -> stopSosCountdown()
            "ACTION_SEND_NOW" -> {
                stopSosCountdown()
                startSmsFlow()
            }
            "START_TRAVEL_MONITORING" -> startTravelLoop()
            "STOP_TRAVEL_MONITORING" -> {
                stopTravelLoop()
                checkAndStopServiceIfIdle()
            }
            else -> {
                updateThresholdFromSettings()
                setupSensors()
                checkAndStopServiceIfIdle()
            }
        }
        return START_STICKY
    }

    // --- TRAVEL MODE LOGIC ---

    @SuppressLint("MissingPermission")
    private fun startTravelLoop() {
        if (isTravelMonitoring) return
        isTravelMonitoring = true
        lastKnownDistance = -1f
        riskEngine.reset()

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateDistanceMeters(0f)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.locations.lastOrNull() ?: return
                processTravelLocation(location)
            }
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback!!, Looper.getMainLooper())
        updateStatusNotification()
    }

    private fun processTravelLocation(location: Location) {
        val prefs = getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val destLat = prefs.getString(AppConstants.KEY_DEST_LAT, null)?.toDoubleOrNull() ?: return
        val destLng = prefs.getString(AppConstants.KEY_DEST_LNG, null)?.toDoubleOrNull() ?: return

        val results = FloatArray(1)
        Location.distanceBetween(location.latitude, location.longitude, destLat, destLng, results)
        val currentDistance = results[0]

        currentDisplayDistance = if (currentDistance >= 1000) {
            String.format("%.2f km", currentDistance / 1000)
        } else {
            "${currentDistance.toInt()}m"
        }

        // --- NEW: Risk Engine Logic Integration ---
        val riskDetected = riskEngine.evaluateRisk(location, destLat, destLng)
        if (riskDetected > 0) {
            Log.e("TRAVEL_MODE", "CRITICAL RISK REACHED: Triggering SOS Notification")
            triggerRiskAlert()
        }

        var isDeviatedVisual = false
        if (lastKnownDistance != -1f) {
            val delta = currentDistance - lastKnownDistance
            if (delta > VISUAL_WARNING_THRESHOLD) isDeviatedVisual = true

            // Traditional deviation threshold still acts as a manual fallback
            if (delta > DEVIATION_THRESHOLD) triggerRiskAlert()

            if (Math.abs(delta) > JITTER_FILTER_METERS) lastKnownDistance = currentDistance
        } else {
            lastKnownDistance = currentDistance
        }

        broadcastRoute(location, isDeviatedVisual)
        updateStatusNotification()

        if (currentDistance < 30f) handleArrival()
    }

    private fun triggerRiskAlert() {
        handler.post {
            startSosCountdown()
            try {
                // Play warning tone so user knows a countdown started
                toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 500)
            } catch (e: Exception) {
                Log.e("ShakeService", "Tone error: ${e.message}")
            }
        }
    }

    private fun broadcastRoute(currentLocation: Location, isDeviated: Boolean) {
        val prefs = getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val destLat = prefs.getString(AppConstants.KEY_DEST_LAT, null)?.toDoubleOrNull() ?: return
        val destLng = prefs.getString(AppConstants.KEY_DEST_LNG, null)?.toDoubleOrNull() ?: return

        val intent = Intent("ACTION_ROUTE_READY")
        val points = arrayListOf(LatLng(currentLocation.latitude, currentLocation.longitude), LatLng(destLat, destLng))
        intent.putParcelableArrayListExtra("points", points)
        intent.putExtra("IS_DEVIATED", isDeviated)
        sendBroadcast(intent)
    }

    private fun handleArrival() {
        stopTravelLoop()
        getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(AppConstants.KEY_TRAVEL_MODE_ENABLED, false).apply()
        checkAndStopServiceIfIdle()
    }

    private fun stopTravelLoop() {
        isTravelMonitoring = false
        currentDisplayDistance = ""
        riskEngine.reset()
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        locationCallback = null
    }

    // --- NOTIFICATION & SERVICE MANAGEMENT ---

    private fun checkAndStopServiceIfIdle() {
        val prefs = getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val safeMode = prefs.getBoolean("SAFE_MODE_ENABLED", false)

        if (!safeMode && !isTravelMonitoring) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            updateStatusNotification()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "SOS Status", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateStatusNotification() {
        val prefs = getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val safeMode = prefs.getBoolean("SAFE_MODE_ENABLED", false)
        val count = ContactManager(this).getContacts().size

        val title = if (isTravelMonitoring) "Travel Monitoring Active" else "SOS Protection Active"

        val contentText = when {
            isTravelMonitoring -> "Distance: $currentDisplayDistance remaining"
            safeMode -> "Monitoring $count guardians"
            else -> "Service Standby"
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(STATUS_NOTIFICATION_ID, notification)
    }

    // --- CORE SOS LOGIC ---

    private fun setupSensors() {
        if (::sensorManager.isInitialized) return
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val gForce = sqrt((event.values[0] * event.values[0] + event.values[1] * event.values[1] + event.values[2] * event.values[2]).toDouble()).toFloat()
            if (gForce - SensorManager.GRAVITY_EARTH > dynamicThreshold) {
                if (System.currentTimeMillis() - lastShakeTime > COOLDOWN_MS) {
                    lastShakeTime = System.currentTimeMillis()
                    TriggerLogger.logTrigger(this, "SHAKE_ALERT")
                    startSosCountdown()
                }
            }
        }
    }

    private fun startSosCountdown() {
        showSosAlertNotification()
        com.example.sossmsapp.utils.SirenManager.startSiren(this)
        sosRunnable?.let { handler.removeCallbacks(it) }
        sosRunnable = Runnable { startSmsFlow(); stopSosCountdown() }
        handler.postDelayed(sosRunnable!!, 15000)
    }

    private fun stopSosCountdown() {
        sosRunnable?.let { handler.removeCallbacks(it) }
        com.example.sossmsapp.utils.SirenManager.stopSiren()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(ALERT_NOTIFICATION_ID)
        
        if (::liveLocationManager.isInitialized) {
            liveLocationManager.stopTracking()
        }
    }

    private fun showSosAlertNotification() {
        // OLD: This went to SOSReceiver (which is now blocked by Android)
        // val pSafe = PendingIntent.getBroadcast(...)

        // NEW: Direct Intent to HomeActivity to bypass "Trampoline" block
        val safeIntent = Intent(this, HomeActivity::class.java).apply {
            action = "ACTION_SAFE"
            putExtra("TRIGGER_PIN_VERIFY", true)
            // CRITICAL: Use these flags to ensure it opens the existing HomeActivity
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pSafe = PendingIntent.getActivity(
            this,
            1,
            safeIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val pSend = PendingIntent.getBroadcast(
            this,
            2,
            Intent(this, SOSReceiver::class.java).apply { action = "ACTION_SEND_NOW" },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val alert = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Safety Alert Detected!")
            .setContentText("Emergency SOS will be sent in 15 seconds.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_save, "I AM SAFE", pSafe)
            .addAction(android.R.drawable.ic_menu_send, "SEND NOW", pSend)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(ALERT_NOTIFICATION_ID, alert)
    }

    @SuppressLint("MissingPermission")
    private fun startSmsFlow() {
        val sessionId = liveLocationManager.startTracking()
        val trackingLink = "https://sossmsapp.web.app/track?id=$sessionId"
        
        val cts = CancellationTokenSource()
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnCompleteListener { task ->
                val loc = task.result
                val mapLink = if (loc != null) "\nStatic: https://maps.google.com/maps?q=${loc.latitude},${loc.longitude}" else ""
                sendSmsToGuardians("SOS Alert! I need help immediately. Track my live location here: $trackingLink $mapLink")
            }
    }

    @Suppress("DEPRECATION")
    private fun sendSmsToGuardians(fullMessage: String) {
        // This pulls from the list you just redesigned!
        val contacts = ContactManager(this).getContacts()

        if (contacts.isEmpty()) {
            Log.e("SOS_APP", "No guardians found in list!")
            return
        }

        val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            this.getSystemService(SmsManager::class.java)
        } else {
            SmsManager.getDefault()
        }

        val parts = smsManager.divideMessage(fullMessage)

        for (contact in contacts) {
            try {
                smsManager.sendMultipartTextMessage(contact.phoneNumber, null, parts, null, null)
                Log.d("SOS_APP", "Alert sent to ${contact.name}")
            } catch (e: Exception) {
                Log.e("SOS_APP", "Failed to reach ${contact.name}: ${e.message}")
            }
        }

        // Your extra security features
        blinkTorch()
        startAudioRecording()
        handler.postDelayed({ captureFrontPhoto() }, 2000)
    }

    // --- OTHER TRIGGERS ---

    private fun setupVolumeObserver() {
        val prefs = getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("ENABLE_VOLUME_TRIGGER", false)) return
        lastVolumeLevel = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        volumeObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (currentVol != lastVolumeLevel) handleVolumeClick()
                lastVolumeLevel = currentVol
            }
        }
        contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, volumeObserver!!)
    }

    private fun handleVolumeClick() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastPressTime > RESET_TIME_MS) volumePressCount = 0
        volumePressCount++
        lastPressTime = currentTime
        if (volumePressCount >= 3) {
            volumePressCount = 0
            TriggerLogger.logTrigger(this, "VOLUME_ALERT")
            startSosCountdown()
        }
    }

    private fun captureFrontPhoto() {
        val prefs = getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(AppConstants.KEY_ENABLE_PHOTO, false)) return
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val imageCapture = ImageCapture.Builder().build()
                val photoFile = File(getExternalFilesDir(null), "sos_${System.currentTimeMillis()}.jpg")
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, imageCapture)
                imageCapture.takePicture(ImageCapture.OutputFileOptions.Builder(photoFile).build(), cameraExecutor, object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) { handler.post { cameraProvider.unbindAll() } }
                    override fun onError(exc: ImageCaptureException) {}
                })
            } catch (e: Exception) {}
        }, ContextCompat.getMainExecutor(this))
    }

    @Suppress("DEPRECATION")
    private fun startAudioRecording() {
        val prefs = getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(AppConstants.KEY_ENABLE_AUDIO, false)) return
        try {
            val audioFile = File(getExternalFilesDir(null), "sos_${System.currentTimeMillis()}.mp4")
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else MediaRecorder()
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile.absolutePath)
                prepare()
                start()
            }
            handler.postDelayed({ stopAudioRecording() }, 20000)
        } catch (e: Exception) {}
    }

    private fun stopAudioRecording() {
        try {
            mediaRecorder?.apply { stop(); release() }
            mediaRecorder = null
        } catch (e: Exception) {}
    }

    private fun blinkTorch() {
        val prefs = getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("ENABLE_TORCH", false)) return
        val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val id = cm.cameraIdList[0]
            var count = 0
            torchRunnable = object : Runnable {
                override fun run() {
                    if (count < 20) {
                        cm.setTorchMode(id, count % 2 == 0)
                        count++
                        torchHandler.postDelayed(this, 500)
                    }
                }
            }
            torchHandler.post(torchRunnable!!)
        } catch (e: Exception) {}
    }

    private fun updateThresholdFromSettings() {
        val sensitivity = getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE).getString("SHAKE_SENSITIVITY", "MEDIUM")
        dynamicThreshold = when (sensitivity) { "LOW" -> 17.0f; "HIGH" -> 9.0f; else -> 13.0f }
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    override fun onDestroy() {
        stopTravelLoop()
        volumeObserver?.let { contentResolver.unregisterContentObserver(it) }
        toneGenerator.release()
        if (::sensorManager.isInitialized) sensorManager.unregisterListener(this)
        cameraExecutor.shutdown()
        super.onDestroy()
    }
}