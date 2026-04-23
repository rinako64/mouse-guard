package com.mouseguard.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.mouseguard.app.R
import com.mouseguard.app.ad.AdGate
import com.mouseguard.app.service.FloatingCameraService

class HomeActivity : AppCompatActivity() {

    private lateinit var btnStart: LinearLayout
    private lateinit var btnStop: LinearLayout
    private lateinit var imgMascot: ImageView
    private lateinit var tvStatusLabel: TextView

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
        imgMascot = findViewById(R.id.imgMascot)
        tvStatusLabel = findViewById(R.id.tvStatusLabel)

        AdGate.init(this)

        imgMascot.setOnClickListener { bounceMascot() }

        btnStart.setOnClickListener {
            AdGate.showIfNeeded(this) { checkAndStart() }
        }
        btnStop.setOnClickListener {
            stopService(Intent(this, FloatingCameraService::class.java))
            updateButtons()
            updateStatusPill()
        }

        listOf(
            R.id.tabReport to "レポート画面はまもなく公開予定です",
            R.id.tabGuide to "ガイド画面はまもなく公開予定です",
            R.id.tabSettings to "設定画面はまもなく公開予定です",
        ).forEach { (id, msg) ->
            findViewById<LinearLayout>(id).setOnClickListener {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<FrameLayout>(R.id.btnMenu).setOnClickListener {
            Toast.makeText(this, "メニューは近日公開", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        updateButtons()
        updateStatusPill()
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
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
            finish()
        }
    }

    private fun bounceMascot() {
        imgMascot.animate()
            .scaleX(1.08f).scaleY(1.08f)
            .setDuration(120)
            .withEndAction {
                imgMascot.animate()
                    .scaleX(1.0f).scaleY(1.0f)
                    .setInterpolator(OvershootInterpolator(3.5f))
                    .setDuration(300)
                    .start()
            }.start()
    }

    private fun updateButtons() {
        val running = FloatingCameraService.isRunning
        btnStart.alpha = if (running) 0.4f else 1.0f
        btnStart.isClickable = !running
        btnStop.alpha = if (running) 1.0f else 0.4f
        btnStop.isClickable = running
    }

    private fun updateStatusPill() {
        val running = FloatingCameraService.isRunning
        tvStatusLabel.text = if (running) "見守り中" else getString(R.string.home_status_standby)
        findViewById<View>(R.id.statusDot).setBackgroundResource(
            if (running) R.drawable.bg_chip_yellow else R.drawable.bg_mint_dot
        )
    }
}
