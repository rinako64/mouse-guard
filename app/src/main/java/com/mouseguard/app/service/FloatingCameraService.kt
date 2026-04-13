package com.mouseguard.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.Display
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Surface
import android.view.View
import android.view.WindowManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.mouseguard.app.R
import com.mouseguard.app.databinding.LayoutFloatingOverlayBinding
import com.mouseguard.app.detection.FaceAnalyzer
import java.util.concurrent.Executors

class FloatingCameraService : Service(), LifecycleOwner {

    companion object {
        const val ACTION_STOP = "action_stop"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "mouth_guard"
        private const val TAG = "MouthGuard"
        var isRunning = false
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private lateinit var windowManager: WindowManager
    private lateinit var displayManager: DisplayManager
    private var overlayBinding: LayoutFloatingOverlayBinding? = null
    private var imageAnalysisUseCase: ImageAnalysis? = null

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) {
                // 回転値だけ更新（状態リセットはしない）
                val rotation = getTargetRotation()
                imageAnalysisUseCase?.targetRotation = rotation
                Log.d(TAG, "Display rotation updated: $rotation")
            }
        }
    }
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    // --- 口開閉の状態管理 ---
    private var isMouthOpen = false
    private var mouthOpenSince = 0L
    private var consecutiveClosedCount = 0
    private var consecutiveOpenCount = 0
    private val OPEN_DEBOUNCE = 2
    private val CLOSE_DEBOUNCE = 8
    private val MIN_OPEN_HOLD_MS = 3000L

    // --- オーディオフォーカス ---
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        displayManager = getSystemService(DisplayManager::class.java)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        displayManager.registerDisplayListener(displayListener, mainHandler)

        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener { }
            .build()

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        setupOverlay()
        startCamera()
    }

    private fun getTargetRotation(): Int {
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
            ?: return Surface.ROTATION_0
        return display.rotation
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        if (hasAudioFocus) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            hasAudioFocus = false
        }
        displayManager.unregisterDisplayListener(displayListener)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        cameraExecutor.shutdown()
        overlayBinding?.let { runCatching { windowManager.removeView(it.root) } }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.app_name),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, FloatingCameraService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.stop_service),
                stopPending
            )
            .build()
    }

    private fun setupOverlay() {
        overlayBinding = LayoutFloatingOverlayBinding.inflate(LayoutInflater.from(this))
        overlayBinding!!.root.visibility = View.GONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        windowManager.addView(overlayBinding!!.root, params)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // ImageAnalysis のみ（Previewなし → 解像度制約なし）
            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setTargetRotation(getTargetRotation())
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { a ->
                    a.setAnalyzer(cameraExecutor, FaceAnalyzer { result ->
                        val now = System.currentTimeMillis()

                        if (result == null) {
                            // 顔未検出 → 状態を維持（閉じ判定にカウントしない）
                            consecutiveOpenCount = 0
                            return@FaceAnalyzer
                        }

                        val isOpen = result.isOpen
                        Log.d(TAG, "avgR=${"%.3f".format(result.ratio)} maxR=${"%.3f".format(result.maxRatio)} open=$isOpen state=$isMouthOpen openCnt=$consecutiveOpenCount closeCnt=$consecutiveClosedCount")

                        if (isOpen) {
                            consecutiveClosedCount = 0
                            consecutiveOpenCount++
                            if (!isMouthOpen && consecutiveOpenCount >= OPEN_DEBOUNCE) {
                                isMouthOpen = true
                                mouthOpenSince = now
                                Log.d(TAG, ">>> MOUTH OPEN detected!")
                                mainHandler.post { onMouthStateChanged(true) }
                            }
                        } else {
                            consecutiveOpenCount = 0
                            consecutiveClosedCount++
                            if (isMouthOpen
                                && consecutiveClosedCount >= CLOSE_DEBOUNCE
                                && now - mouthOpenSince >= MIN_OPEN_HOLD_MS) {
                                isMouthOpen = false
                                Log.d(TAG, ">>> MOUTH CLOSED detected!")
                                mainHandler.post { onMouthStateChanged(false) }
                            }
                        }
                    })
                }

            imageAnalysisUseCase = analysis

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, analysis)
                Log.d(TAG, "Camera bound: Analysis only (no preview)")
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun onMouthStateChanged(isOpen: Boolean) {
        Log.d(TAG, "=== onMouthStateChanged: isOpen=$isOpen ===")
        if (isOpen) {
            overlayBinding?.tvOverlayMessage?.setText(R.string.mouth_open_warning)
            overlayBinding?.root?.visibility = View.VISIBLE
            pauseMedia()
        } else {
            overlayBinding?.root?.visibility = View.GONE
            resumeMedia()
        }
    }

    private fun pauseMedia() {
        audioFocusRequest?.let {
            val result = audioManager.requestAudioFocus(it)
            hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            Log.d(TAG, "requestAudioFocus: ${if (hasAudioFocus) "granted" else "denied"}")
        }
        sendMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
    }

    private fun resumeMedia() {
        if (hasAudioFocus) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            hasAudioFocus = false
            Log.d(TAG, "abandonAudioFocus")
        }
        sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
    }

    private fun sendMediaKey(keyCode: Int) {
        val now = SystemClock.uptimeMillis()
        audioManager.dispatchMediaKeyEvent(
            KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0)
        )
        audioManager.dispatchMediaKeyEvent(
            KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0)
        )
    }
}
