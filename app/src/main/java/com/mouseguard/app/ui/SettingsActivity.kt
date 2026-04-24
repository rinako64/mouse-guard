package com.mouseguard.app.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.mouseguard.app.BuildConfig
import com.mouseguard.app.R
import com.mouseguard.app.settings.Sensitivity
import com.mouseguard.app.settings.SettingsStore

class SettingsActivity : AppCompatActivity() {

    private lateinit var segLoose: TextView
    private lateinit var segNormal: TextView
    private lateinit var segStrict: TextView

    private lateinit var rowPermCamera: View
    private lateinit var rowPermOverlay: View
    private lateinit var badgePermCamera: View
    private lateinit var badgePermOverlay: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        segLoose = findViewById(R.id.segLoose)
        segNormal = findViewById(R.id.segNormal)
        segStrict = findViewById(R.id.segStrict)

        segLoose.setOnClickListener { selectSensitivity(Sensitivity.LOOSE) }
        segNormal.setOnClickListener { selectSensitivity(Sensitivity.NORMAL) }
        segStrict.setOnClickListener { selectSensitivity(Sensitivity.STRICT) }

        rowPermCamera = findViewById(R.id.rowPermCamera)
        rowPermOverlay = findViewById(R.id.rowPermOverlay)
        badgePermCamera = findViewById(R.id.badgePermCamera)
        badgePermOverlay = findViewById(R.id.badgePermOverlay)

        rowPermCamera.setOnClickListener {
            if (!hasCameraPermission()) openAppDetailsSettings()
        }
        rowPermOverlay.setOnClickListener {
            if (!hasOverlayPermission()) openOverlaySettings()
        }

        findViewById<TextView>(R.id.tvVersion).text = BuildConfig.VERSION_NAME

        findViewById<View>(R.id.rowPrivacy).setOnClickListener { openPrivacyPolicy() }

        findViewById<View>(R.id.tabHome).setOnClickListener { goTab(HomeActivity::class.java) }
        findViewById<View>(R.id.tabReport).setOnClickListener { goTab(ReportActivity::class.java) }
        findViewById<View>(R.id.tabGuide).setOnClickListener { goTab(GuideActivity::class.java) }
        findViewById<View>(R.id.tabSettings).setOnClickListener { /* current */ }
    }

    override fun onResume() {
        super.onResume()
        updateSegmentUi(SettingsStore.getSensitivity(this))
        updatePermissionBadges()
    }

    private fun selectSensitivity(s: Sensitivity) {
        SettingsStore.setSensitivity(this, s)
        updateSegmentUi(s)
    }

    private fun updateSegmentUi(current: Sensitivity) {
        applySegState(segLoose, current == Sensitivity.LOOSE)
        applySegState(segNormal, current == Sensitivity.NORMAL)
        applySegState(segStrict, current == Sensitivity.STRICT)
    }

    private fun applySegState(view: TextView, active: Boolean) {
        if (active) {
            view.setBackgroundResource(R.drawable.bg_seg_on)
            view.setTextColor(0xFFFFFFFF.toInt())
        } else {
            view.setBackgroundResource(0)
            view.setTextColor(ContextCompat.getColor(this, R.color.ink_sub))
        }
    }

    private fun updatePermissionBadges() {
        renderBadge(badgePermCamera, hasCameraPermission())
        renderBadge(badgePermOverlay, hasOverlayPermission())
    }

    private fun renderBadge(badgeRoot: View, granted: Boolean) {
        val text = badgeRoot.findViewById<TextView>(R.id.badgeText)
        val dot = badgeRoot.findViewById<View>(R.id.badgeDot)
        val chevron = badgeRoot.findViewById<ImageView>(R.id.badgeChevron)

        if (granted) {
            badgeRoot.setBackgroundResource(R.drawable.bg_badge_ok)
            text.setText(R.string.perm_badge_ok)
            dot.visibility = View.VISIBLE
            chevron.visibility = View.GONE
        } else {
            badgeRoot.setBackgroundResource(R.drawable.bg_badge_ng)
            text.setText(R.string.perm_badge_ng)
            dot.visibility = View.GONE
            chevron.visibility = View.VISIBLE
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(this)

    private fun openAppDetailsSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")
            )
        )
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
        )
    }

    private fun openPrivacyPolicy() {
        val url = getString(R.string.privacy_policy_url)
        if (url.isBlank()) {
            Toast.makeText(this, R.string.privacy_url_not_configured, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.privacy_url_not_configured, Toast.LENGTH_SHORT).show()
        }
    }

    private fun <T> goTab(cls: Class<T>) {
        startActivity(Intent(this, cls).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
        finish()
    }
}
