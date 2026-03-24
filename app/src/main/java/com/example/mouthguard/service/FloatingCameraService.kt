package com.example.mouthguard.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.PixelFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.Surface
import android.view.LayoutInflater
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
import com.example.mouthguard.R
import com.example.mouthguard.databinding.LayoutFloatingCameraBinding
import com.example.mouthguard.databinding.LayoutFloatingOverlayBinding
import com.example.mouthguard.detection.FaceAnalyzer
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
    private var cameraBinding: LayoutFloatingCameraBinding? = null
    private var overlayBinding: LayoutFloatingOverlayBinding? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isMouthOpen = false
    private var consecutiveClosedCount = 0
    private var consecutiveOpenCount = 0
    private val CLOSE_DEBOUNCE_FRAMES = 2
    private val OPEN_DEBOUNCE_FRAMES = 2
    private var imageAnalysisUseCase: ImageAnalysis? = null
    private var frameCount = 0
    private val WARMUP_FRAMES = 4
    private var calibrated = false
    private var frontCameraSensorOrientation = 270  // デフォルト値

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) {
                updateAnalysisRotation()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        displayManager = getSystemService(DisplayManager::class.java)
        displayManager.registerDisplayListener(displayListener, mainHandler)
        detectFrontCameraSensorOrientation()

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        setupFloatingCamera()
        setupOverlay()
        startCamera()
    }

    private fun detectFrontCameraSensorOrientation() {
        try {
            val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
            for (id in cameraManager.cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                if (chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    frontCameraSensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 270
                    Log.d(TAG, "Front camera sensor orientation: $frontCameraSensorOrientation")
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get sensor orientation", e)
        }
    }

    /** ML Kit用の回転値を計算（フロントカメラ対応） */
    private fun getRotationForMLKit(): Int {
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        val displayRotation = display?.rotation ?: Surface.ROTATION_0
        val displayDegrees = displayRotation * 90
        // フロントカメラ: (sensor + display) % 360
        val rotation = (frontCameraSensorOrientation + displayDegrees) % 360
        return rotation
    }

    private fun getTargetRotation(): Int {
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
            ?: return Surface.ROTATION_0
        return display.rotation
    }

    private fun updateAnalysisRotation() {
        val targetRotation = getTargetRotation()
        Log.d(TAG, "Display changed -> targetRotation=$targetRotation")
        imageAnalysisUseCase?.targetRotation = targetRotation
        consecutiveOpenCount = 0
        consecutiveClosedCount = 0
        frameCount = 0
        calibrated = false

        if (isMouthOpen) {
            isMouthOpen = false
            mainHandler.post { onMouthStateChanged(false) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        displayManager.unregisterDisplayListener(displayListener)
        cameraExecutor.shutdown()
        cameraBinding?.let { runCatching { windowManager.removeView(it.root) } }
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

    private fun setupFloatingCamera() {
        cameraBinding = LayoutFloatingCameraBinding.inflate(LayoutInflater.from(this))
        cameraBinding!!.btnStop.setOnClickListener { stopSelf() }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 16
            y = 120
            // 他アプリの画面回転をブロックしない
            screenOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
        windowManager.addView(cameraBinding!!.root, params)
    }

    private fun setupOverlay() {
        overlayBinding = LayoutFloatingOverlayBinding.inflate(LayoutInflater.from(this))
        overlayBinding!!.root.visibility = View.GONE

        overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            // 他アプリの画面回転をブロックしない
            screenOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
        windowManager.addView(overlayBinding!!.root, overlayParams!!)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val analysis = ImageAnalysis.Builder()
                .setTargetRotation(getTargetRotation())
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { a ->
                    a.setAnalyzer(cameraExecutor, FaceAnalyzer(::getRotationForMLKit) { result ->
                        frameCount++

                        // ウォームアップ：カメラ安定前はスキップ
                        if (frameCount <= WARMUP_FRAMES) {
                            Log.d(TAG, "Warmup $frameCount/$WARMUP_FRAMES")
                            return@FaceAnalyzer
                        }

                        if (result == null) {
                            consecutiveOpenCount = 0
                            consecutiveClosedCount++
                            if (isMouthOpen && consecutiveClosedCount >= CLOSE_DEBOUNCE_FRAMES * 2) {
                                isMouthOpen = false
                                mainHandler.post { onMouthStateChanged(false) }
                            }
                            return@FaceAnalyzer
                        }

                        val isOpen = result.isOpen
                        Log.d(TAG, "ratio=${"%.3f".format(result.ratio)} open=$isOpen cal=$calibrated state=$isMouthOpen")

                        // キャリブレーション：最初に「閉じ」を2回連続で確認してから判定開始
                        if (!calibrated) {
                            if (!isOpen) {
                                consecutiveClosedCount++
                                if (consecutiveClosedCount >= 2) {
                                    calibrated = true
                                    consecutiveClosedCount = 0
                                    Log.d(TAG, "Calibrated — closed mouth baseline confirmed")
                                }
                            } else {
                                consecutiveClosedCount = 0
                            }
                            return@FaceAnalyzer
                        }

                        if (isOpen) {
                            consecutiveClosedCount = 0
                            consecutiveOpenCount++
                            if (!isMouthOpen && consecutiveOpenCount >= OPEN_DEBOUNCE_FRAMES) {
                                isMouthOpen = true
                                mainHandler.post { onMouthStateChanged(true) }
                            }
                        } else {
                            consecutiveOpenCount = 0
                            consecutiveClosedCount++
                            if (isMouthOpen && consecutiveClosedCount >= CLOSE_DEBOUNCE_FRAMES) {
                                isMouthOpen = false
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
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun onMouthStateChanged(isOpen: Boolean) {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        if (isOpen) {
            // 警告テキストを設定して表示
            overlayBinding?.tvOverlayMessage?.setText(R.string.mouth_open_warning)
            audioManager.dispatchMediaKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE)
            )
            audioManager.dispatchMediaKeyEvent(
                KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE)
            )
            overlayBinding?.root?.visibility = View.VISIBLE
        } else {
            audioManager.dispatchMediaKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)
            )
            audioManager.dispatchMediaKeyEvent(
                KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY)
            )
            overlayBinding?.root?.visibility = View.GONE
        }
    }
}
