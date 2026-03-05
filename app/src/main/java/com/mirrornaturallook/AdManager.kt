package com.mirrornaturallook

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * AdMob ad manager.
 *
 * Replace TEST ad unit IDs with your real AdMob unit IDs from admob.google.com:
 *   BANNER_AD_UNIT_ID      → Banner shown at bottom of mirror
 *   INTERSTITIAL_AD_UNIT_ID → Full-screen ad between sessions
 *   REWARDED_AD_UNIT_ID    → "Watch ad = 1hr no ads" offer
 */
class AdManager(private val context: Context) {

    companion object {
        private const val TAG = "AdManager"

        // ⚠️ Replace these with your real AdMob unit IDs before publishing
        const val BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"       // Test
        const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712" // Test
        const val REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"     // Test
    }

    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var bannerAdView: AdView? = null

    // ── Banner Ad ──────────────────────────────────────────────────────────────

    fun loadBanner(container: FrameLayout) {
        bannerAdView = AdView(context).apply {
            adUnitId = BANNER_AD_UNIT_ID
            setAdSize(AdSize.BANNER)
            adListener = object : AdListener() {
                override fun onAdLoaded() { visibility = View.VISIBLE }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Banner failed: ${error.message}")
                    visibility = View.GONE
                }
            }
            loadAd(AdRequest.Builder().build())
        }
        container.removeAllViews()
        container.addView(bannerAdView)
    }

    fun pauseBanner() { bannerAdView?.pause() }
    fun resumeBanner() { bannerAdView?.resume() }
    fun destroyBanner() { bannerAdView?.destroy(); bannerAdView = null }

    // ── Interstitial Ad ────────────────────────────────────────────────────────

    fun loadInterstitial() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(context, INTERSTITIAL_AD_UNIT_ID, adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    Log.d(TAG, "Interstitial loaded")
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Interstitial failed: ${error.message}")
                    interstitialAd = null
                }
            })
    }

    fun showInterstitial(activity: Activity, onDismissed: () -> Unit = {}) {
        val ad = interstitialAd ?: run { onDismissed(); return }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                loadInterstitial() // pre-load next
                onDismissed()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitialAd = null
                onDismissed()
            }
        }
        ad.show(activity)
    }

    // ── Rewarded Ad ────────────────────────────────────────────────────────────

    fun loadRewarded() {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(context, REWARDED_AD_UNIT_ID, adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    Log.d(TAG, "Rewarded loaded")
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Rewarded failed: ${error.message}")
                    rewardedAd = null
                }
            })
    }

    fun showRewarded(activity: Activity, onRewarded: () -> Unit, onDismissed: () -> Unit = {}) {
        val ad = rewardedAd ?: run { onDismissed(); return }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                loadRewarded()
                onDismissed()
            }
        }
        ad.show(activity) { onRewarded() }
    }

    fun isRewardedReady() = rewardedAd != null
    fun isInterstitialReady() = interstitialAd != null
}
