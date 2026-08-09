package com.example.wordaire.ads

import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * Manages full-screen interstitial ads that appear between levels.
 */
class InterstitialAdManager(private val activity: FragmentActivity) {

    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false

    fun load() {
        if (interstitialAd != null || isLoading) return
        isLoading = true
        Log.d("InterstitialAd", "Loading ad...")
        
        InterstitialAd.load(
            activity,
            INTERSTITIAL_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d("InterstitialAd", "Ad loaded successfully")
                    interstitialAd = ad
                    isLoading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e("InterstitialAd", "Ad failed to load: ${error.message}")
                    interstitialAd = null
                    isLoading = false
                }
            }
        )
    }

    fun show(onAdDismissed: () -> Unit) {
        val ad = interstitialAd
        if (ad == null) {
            Log.d("InterstitialAd", "Ad not ready, skipping")
            load()
            onAdDismissed()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d("InterstitialAd", "Ad dismissed")
                interstitialAd = null
                load()
                onAdDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                Log.e("InterstitialAd", "Ad failed to show: ${error.message}")
                interstitialAd = null
                load()
                onAdDismissed()
            }
        }
        ad.show(activity)
    }

    private companion object {
        // Real Interstitial Ad Unit ID for Production
        const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-9704349236169325/7366277671"
    }
}
