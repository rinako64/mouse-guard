package com.example.mouthguard.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.mouthguard.R
import android.widget.LinearLayout
import com.example.mouthguard.service.FloatingCameraService

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

        btnStart.setOnClickListener { checkAndStart() }
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

    private fun updateButtons() {
        val running = FloatingCameraService.isRunning
        btnStart.alpha = if (running) 0.5f else 1.0f
        btnStart.isClickable = !running
        btnStop.alpha = if (running) 1.0f else 0.5f
        btnStop.isClickable = running
    }
}
