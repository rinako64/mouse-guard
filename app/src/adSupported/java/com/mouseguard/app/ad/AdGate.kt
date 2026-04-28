package com.mouseguard.app.ad

import android.app.Activity
import android.util.DisplayMetrics
import android.util.Log
import android.view.ViewGroup
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.mouseguard.app.BuildConfig

object AdGate {

    private const val TAG = "AdGate"

    // 本番のバナー広告ユニットID（adSupported flavor のみで使用）
    private const val BANNER_PROD_ID = "ca-app-pub-6292808487320633/2061394032"
    // Google公式テストID（自己クリックでアカウント停止になるため、開発時は必ずテストIDを使う）
    private const val BANNER_TEST_ID = "ca-app-pub-3940256099942544/6300978111"

    private val bannerAdUnitId: String
        get() = if (BuildConfig.DEBUG) BANNER_TEST_ID else BANNER_PROD_ID

    private var isInitialized = false

    fun init(activity: Activity) {
        if (!isInitialized) {
            MobileAds.initialize(activity) {}
            isInitialized = true
        }
    }

    /** バナー広告をcontainerに読み込む（adSupported版のみ実体あり） */
    fun loadBanner(activity: Activity, container: ViewGroup) {
        init(activity)
        container.post {
            val adView = AdView(activity)
            adView.setAdSize(adaptiveBannerSize(activity, container))
            adView.adUnitId = bannerAdUnitId
            container.removeAllViews()
            container.addView(adView)
            try {
                adView.loadAd(AdRequest.Builder().build())
            } catch (t: Throwable) {
                Log.e(TAG, "Banner load failed", t)
            }
        }
    }

    private fun adaptiveBannerSize(activity: Activity, container: ViewGroup): AdSize {
        val metrics = DisplayMetrics().also {
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay.getMetrics(it)
        }
        val density = metrics.density.takeIf { it > 0f } ?: 1f
        val widthPx = container.width.takeIf { it > 0 } ?: metrics.widthPixels
        val widthDp = (widthPx / density).toInt().coerceAtLeast(50)
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp)
    }
}
