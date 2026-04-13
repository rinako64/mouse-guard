package com.mouseguard.app.ad

import android.app.Activity
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object AdGate {

    private const val TAG = "AdGate"

    // テスト用広告ユニットID（リリース時に本番IDへ差し替え）
    private const val AD_UNIT_ID = "ca-app-pub-6292808487320633/7675214183"

    private var rewardedAd: RewardedAd? = null
    private var isInitialized = false

    /** アプリ起動時に呼ぶ */
    fun init(activity: Activity) {
        if (!isInitialized) {
            MobileAds.initialize(activity) {}
            isInitialized = true
        }
        loadAd(activity)
    }

    /** 広告を表示し、視聴完了後にonGrantedを呼ぶ */
    fun showIfNeeded(activity: Activity, onGranted: () -> Unit) {
        val ad = rewardedAd
        if (ad == null) {
            // 広告がまだ読み込まれていない場合はそのまま通す
            Log.w(TAG, "Ad not loaded yet, granting access")
            onGranted()
            loadAd(activity)
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                loadAd(activity)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.e(TAG, "Ad failed to show: ${error.message}")
                rewardedAd = null
                onGranted()
                loadAd(activity)
            }
        }

        ad.show(activity) { _ ->
            Log.d(TAG, "User earned reward")
            onGranted()
        }
    }

    private fun loadAd(activity: Activity) {
        RewardedAd.load(
            activity,
            AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    Log.d(TAG, "Rewarded ad loaded")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    Log.e(TAG, "Rewarded ad failed to load: ${error.message}")
                }
            }
        )
    }
}
