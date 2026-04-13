package com.mouseguard.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.mouseguard.app.R
import android.widget.LinearLayout
import com.mouseguard.app.ad.AdGate
import com.mouseguard.app.service.FloatingCameraService

class HomeActivity : AppCompatActivity() {

    private lateinit var btnStart: LinearLayout
    private lateinit var btnStop: LinearLayout

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            checkOverlayAndStart()
        } else {
            Toast.makeText(this, R.string.camera_permission_denied, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)

        // 広告の初期化
        AdGate.init(this)

        // マスコットのポヨンポヨンアニメーション（沈む→浮く→戻る のループ）
        val imgMascot = findViewById<ImageView>(R.id.imgMascot)
        startBounceLoop(imgMascot)

        btnStart.setOnClickListener {
            AdGate.showIfNeeded(this) { checkAndStart() }
        }
        btnStop.setOnClickListener {
            stopService(Intent(this, FloatingCameraService::class.java))
            updateButtons()
        }
    }

    override fun onResume() {
        super.onResume()
        updateButtons()
    }

    private fun checkAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        } else {
            checkOverlayAndStart()
        }
    }

    private fun checkOverlayAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.overlay_permission_needed, Toast.LENGTH_LONG).show()
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        } else {
            startForegroundService(Intent(this, FloatingCameraService::class.java))
            Toast.makeText(this, R.string.service_started, Toast.LENGTH_SHORT).show()
            // サービス開始後、ホーム画面に強制遷移
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
            finish()
        }
    }

    private fun startBounceLoop(view: ImageView) {
        val phase1 = AnimationUtils.loadAnimation(this, R.anim.bounce)       // 沈む
        val phase2 = AnimationUtils.loadAnimation(this, R.anim.bounce_up)    // 浮く
        val phase3 = AnimationUtils.loadAnimation(this, R.anim.bounce_down)  // 戻る

        phase1.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(a: Animation?) {}
            override fun onAnimationRepeat(a: Animation?) {}
            override fun onAnimationEnd(a: Animation?) { view.startAnimation(phase2) }
        })
        phase2.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(a: Animation?) {}
            override fun onAnimationRepeat(a: Animation?) {}
            override fun onAnimationEnd(a: Animation?) { view.startAnimation(phase3) }
        })
        phase3.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(a: Animation?) {}
            override fun onAnimationRepeat(a: Animation?) {}
            override fun onAnimationEnd(a: Animation?) { view.startAnimation(phase1) }
        })

        view.startAnimation(phase1)
    }

    private fun updateButtons() {
        val running = FloatingCameraService.isRunning
        btnStart.alpha = if (running) 0.5f else 1.0f
        btnStart.isClickable = !running
        btnStop.alpha = if (running) 1.0f else 0.5f
        btnStop.isClickable = running
    }
}
