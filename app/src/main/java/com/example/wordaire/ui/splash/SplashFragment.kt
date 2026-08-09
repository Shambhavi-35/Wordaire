package com.example.wordaire.ui.splash

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.wordaire.R

class SplashFragment : Fragment() {

    private var progressAnimator: ObjectAnimator? = null
    private var pulseAnimator: ValueAnimator? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_splash, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val glow = view.findViewById<View>(R.id.logoGlow)
        val logo = view.findViewById<View>(R.id.logoImage)
        val appName = view.findViewById<View>(R.id.appName)
        val tagline = view.findViewById<View>(R.id.tagline)
        val progressBar = view.findViewById<ProgressBar>(R.id.loadingProgress)

        // Start everything invisible/offset so the entrance reads cleanly.
        glow.alpha = 0f
        logo.apply { scaleX = 0.3f; scaleY = 0.3f; alpha = 0f; rotation = -15f }
        appName.apply { alpha = 0f; translationY = 40f }
        tagline.apply { alpha = 0f; translationY = 40f }

        // Glow fades in first, sets the mood.
        glow.animate()
            .alpha(1f)
            .setDuration(400)
            .start()

        // Logo pops in with a slight rotation settle and overshoot bounce.
        logo.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .rotation(0f)
            .setStartDelay(150)
            .setDuration(650)
            .setInterpolator(OvershootInterpolator(1.6f))
            .withEndAction { startLogoPulse(logo) }
            .start()

        // Title slides up and fades in right after the logo lands.
        appName.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(550)
            .setDuration(400)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        // Tagline follows with a small stagger for a layered feel.
        tagline.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(680)
            .setDuration(400)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        // Progress fills smoothly, starting once the text has landed.
        progressAnimator = ObjectAnimator.ofInt(progressBar, "progress", 0, 100).apply {
            duration = 1600
            startDelay = 750
            interpolator = LinearInterpolator()
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {}
                override fun onAnimationCancel(animation: Animator) {}
                override fun onAnimationRepeat(animation: Animator) {}

                override fun onAnimationEnd(animation: Animator) {
                    if (isAdded) {
                        try {
                            findNavController().navigate(
                                R.id.action_splashFragment_to_categorySelectionFragment
                            )
                        } catch (e: IllegalArgumentException) {
                            // Nav action ID missing from nav_graph.xml, or not
                            // attached to this fragment's node. Check Logcat
                            // if the app still seems stuck here.
                            e.printStackTrace()
                        }
                    }
                }
            })
            start()
        }
    }

    /** Gentle continuous breathing effect on the logo while the bar fills. */
    private fun startLogoPulse(logo: View) {
        pulseAnimator = ValueAnimator.ofFloat(1f, 1.05f).apply {
            duration = 900
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                val scale = it.animatedValue as Float
                logo.scaleX = scale
                logo.scaleY = scale
            }
            start()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Prevent animators from holding a reference to a destroyed view,
        // which would leak memory or crash if they try to update after
        // the view is gone.
        progressAnimator?.cancel()
        progressAnimator = null
        pulseAnimator?.cancel()
        pulseAnimator = null
    }
}