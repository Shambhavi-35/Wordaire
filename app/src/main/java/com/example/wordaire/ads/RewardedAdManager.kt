package com.example.wordaire.ads

import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * Keeps rewarded-ad lifecycle code out of the game screen. The supplied ID
 * is Google's rewarded test unit and must be replaced with this game's own
 * rewarded unit ID before publishing.
 */
class RewardedAdManager(private val activity: FragmentActivity) {

    private var rewardedAd: RewardedAd? = null
    private var isLoading = false

    fun preload() {
        if (rewardedAd != null || isLoading) return
        isLoading = true
        Log.d("AdManager", "Loading ad with unit ID: $TEST_REWARDED_AD_UNIT_ID")
        RewardedAd.load(
            activity,
            TEST_REWARDED_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d("AdManager", "Ad loaded successfully")
                    isLoading = false
                    rewardedAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e("AdManager", "Ad failed to load: ${error.message} (Code: ${error.code})")
                    // Notify the user about the specific reason
                    val reason = when(error.code) {
                        3 -> "No ad fill (normal for new apps). Use Test Ads to verify."
                        0 -> "Internal error. Check internet or App ID."
                        1 -> "Invalid request. Check Ad Unit ID."
                        else -> "Error code: ${error.code}"
                    }
                    Log.w("AdManager", "Reason: $reason")
                    isLoading = false
                    rewardedAd = null
                }
            }
        )
    }

    fun show(onRewardEarned: () -> Unit, onUnavailable: () -> Unit) {
        val ad = rewardedAd
        if (ad == null) {
            Log.w("AdManager", "Show called but ad not ready")
            preload()
            onUnavailable()
            return
        }

        rewardedAd = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d("AdManager", "Ad dismissed")
                preload()
            }
            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                Log.e("AdManager", "Ad failed to show: ${error.message} (Code: ${error.code})")
                preload()
            }
        }
        ad.show(activity) { _: RewardItem -> 
            Log.d("AdManager", "Reward earned")
            onRewardEarned() 
        }
    }

    private companion object {
        // YOUR REAL REWARDED AD UNIT ID (Used for Production APK)
        const val TEST_REWARDED_AD_UNIT_ID = "ca-app-pub-9704349236169325/6257288718"
    }
}
