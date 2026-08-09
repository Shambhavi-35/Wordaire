package com.example.wordaire.ads

import android.view.ViewGroup
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/**
 * Manages the lifecycle and display of banner ads.
 */
class BannerAdManager(private val container: ViewGroup) {

    private var adView: AdView? = null

    fun load() {
        if (adView != null) return

        val context = container.context
        adView = AdView(context).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = TEST_BANNER_AD_UNIT_ID
            loadAd(AdRequest.Builder().build())
        }

        container.addView(adView)
    }

    fun destroy() {
        adView?.destroy()
        adView = null
        container.removeAllViews()
    }

    private companion object {
        // YOUR REAL BANNER AD UNIT ID (Used for Production APK)
        const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-9704349236169325/4918529571"
    }
}
