package com.mirrornaturallook

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.view.animation.AlphaAnimation
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.mirrornaturallook.databinding.ActivityMirrorBinding
import kotlinx.coroutines.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MirrorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMirrorBinding
    private lateinit var premiumManager: PremiumManager
    private lateinit var adManager: AdManager
    private lateinit var billingManager: BillingManager

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private lateinit var cameraExecutor: ExecutorService

    // Zoom
    private var currentZoom = 1.0f
    private val zoomLevels = listOf(1.0f, 1.5f, 2.0f, 2.5f, 3.0f)
    private var zoomIndex = 0

    // Pinch gesture
    private var lastPinchDistance = 0f
    private var isPinching = false

    // UI auto-hide
    private val uiHandler = Handler(Looper.getMainLooper())
    private var isUiVisible = true
    private val hideUiRunnable = Runnable { hideSystemUI() }
    private val UI_HIDE_DELAY = 4000L

    // Theme: "dark" or "silver"
    private var currentTheme = "dark"

    // Session counter for interstitial timing
    private var sessionCount = 0

    companion object {
        private const val CAMERA_PERMISSION = Manifest.permission.CAMERA
        private const val REQUEST_CODE_PAYWALL = 1001
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera()
        else showPermissionDenied()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMirrorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        premiumManager = PremiumManager(this)
        adManager = AdManager(this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        setupBilling()
        setupFullscreen()
        setupGestures()
        setupButtons()
        setupTheme()
        checkPermissionAndStartCamera()
        updatePremiumUI()
        scheduleAds()

        // Check paywall on launch
        if (premiumManager.needsPaywall) {
            openPaywall()
        }
    }

    // ── FULLSCREEN ────────────────────────────────────────────────────────────

    private fun setupFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, binding.root)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hideSystemUI()

        // Keep screen on while app is open
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun hideSystemUI() {
        val controller = WindowInsetsControllerCompat(window, binding.root)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        isUiVisible = false
        fadeOutControls()
    }

    private fun showSystemUI() {
        val controller = WindowInsetsControllerCompat(window, binding.root)
        controller.show(WindowInsetsCompat.Type.systemBars())
        isUiVisible = true
        fadeInControls()
        resetHideTimer()
    }

    private fun resetHideTimer() {
        uiHandler.removeCallbacks(hideUiRunnable)
        uiHandler.postDelayed(hideUiRunnable, UI_HIDE_DELAY)
    }

    private fun fadeInControls() {
        binding.controlsOverlay.apply {
            visibility = View.VISIBLE
            startAnimation(AlphaAnimation(0f, 1f).apply { duration = 300; fillAfter = true })
        }
    }

    private fun fadeOutControls() {
        binding.controlsOverlay.startAnimation(
            AlphaAnimation(1f, 0f).apply {
                duration = 600
                fillAfter = true
            }
        )
        Handler(Looper.getMainLooper()).postDelayed({
            binding.controlsOverlay.visibility = View.INVISIBLE
        }, 600)
    }

    // ── CAMERA ────────────────────────────────────────────────────────────────

    private fun checkPermissionAndStartCamera() {
        when {
            ContextCompat.checkSelfPermission(this, CAMERA_PERMISSION) == PackageManager.PERMISSION_GRANTED -> startCamera()
            else -> permissionLauncher.launch(CAMERA_PERMISSION)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCamera()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCamera() {
        val provider = cameraProvider ?: return

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
        }

        // Front camera — mirror view
        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

        // Mirror-flip the preview horizontally (like a real mirror)
        binding.viewFinder.scaleX = -1f

        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(this, cameraSelector, preview)
            setupZoom()
        } catch (e: Exception) {
            showPermissionDenied()
        }
    }

    private fun showPermissionDenied() {
        binding.cameraPermissionLayout.visibility = View.VISIBLE
        binding.viewFinder.visibility = View.GONE
    }

    // ── ZOOM ──────────────────────────────────────────────────────────────────

    private fun setupZoom() {
        updateZoomButtons()
    }

    private fun setZoom(zoomRatio: Float) {
        currentZoom = zoomRatio
        camera?.cameraControl?.setZoomRatio(zoomRatio)
        updateZoomBadge()
        updateZoomButtons()
    }

    private fun updateZoomBadge() {
        if (currentZoom > 1.01f) {
            binding.zoomBadge.visibility = View.VISIBLE
            binding.zoomBadge.text = String.format("%.1f×", currentZoom)
        } else {
            binding.zoomBadge.visibility = View.GONE
        }
    }

    private fun updateZoomButtons() {
        val buttons = listOf(
            binding.zoom1x, binding.zoom15x,
            binding.zoom2x, binding.zoom25x, binding.zoom3x
        )
        buttons.forEachIndexed { i, btn ->
            val isActive = abs(zoomLevels[i] - currentZoom) < 0.1f
            btn.isSelected = isActive
            btn.alpha = if (isActive) 1.0f else 0.55f
        }
    }

    // ── PINCH GESTURE ─────────────────────────────────────────────────────────

    private fun setupGestures() {
        binding.mirrorContainer.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (!isUiVisible) showSystemUI() else resetHideTimer()
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount == 2) {
                        isPinching = true
                        lastPinchDistance = getPinchDistance(event)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isPinching && event.pointerCount == 2) {
                        val newDist = getPinchDistance(event)
                        if (lastPinchDistance > 0) {
                            val scale = newDist / lastPinchDistance
                            val maxZoom = camera?.cameraInfo?.zoomState?.value?.maxZoomRatio ?: 3f
                            val newZoom = (currentZoom * scale).coerceIn(1.0f, min(3f, maxZoom))
                            setZoom(newZoom)
                        }
                        lastPinchDistance = newDist
                    }
                }
                MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                    if (event.pointerCount <= 1) {
                        isPinching = false
                        lastPinchDistance = 0f
                        // Snap to nearest zoom level
                        val nearest = zoomLevels.minByOrNull { abs(it - currentZoom) } ?: 1f
                        zoomIndex = zoomLevels.indexOf(nearest)
                    }
                }
            }
            true
        }
    }

    private fun getPinchDistance(event: MotionEvent): Float {
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
    }

    // ── BUTTONS ───────────────────────────────────────────────────────────────

    private fun setupButtons() {
        // Zoom buttons
        binding.zoom1x.setOnClickListener { zoomIndex = 0; setZoom(1.0f) }
        binding.zoom15x.setOnClickListener { zoomIndex = 1; setZoom(1.5f) }
        binding.zoom2x.setOnClickListener { zoomIndex = 2; setZoom(2.0f) }
        binding.zoom25x.setOnClickListener { zoomIndex = 3; setZoom(2.5f) }
        binding.zoom3x.setOnClickListener { zoomIndex = 4; setZoom(3.0f) }

        // Theme toggle
        binding.btnTheme.setOnClickListener {
            currentTheme = if (currentTheme == "dark") "silver" else "dark"
            setupTheme()
        }

        // Upgrade / premium button
        binding.btnUpgrade.setOnClickListener { openPaywall() }

        // Camera permission retry
        binding.btnAllowCamera.setOnClickListener {
            checkPermissionAndStartCamera()
        }

        // Rewarded ad — watch to remove ads temporarily
        binding.btnWatchAd.setOnClickListener {
            adManager.showRewarded(
                activity = this,
                onRewarded = {
                    Toast.makeText(this, "Ads removed for 1 hour! ✓", Toast.LENGTH_SHORT).show()
                    binding.adBannerContainer.visibility = View.GONE
                    binding.btnWatchAd.visibility = View.GONE
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (!premiumManager.isPremium) {
                            binding.adBannerContainer.visibility = View.VISIBLE
                            binding.btnWatchAd.visibility = View.VISIBLE
                        }
                    }, 3600_000L) // 1 hour
                }
            )
        }
    }

    // ── THEME ─────────────────────────────────────────────────────────────────

    private fun setupTheme() {
        if (currentTheme == "dark") {
            binding.mirrorBackground.setBackgroundResource(R.drawable.bg_mirror_dark)
            binding.btnTheme.text = "☽  Dark"
            binding.zoomBar.setBackgroundResource(R.drawable.bg_zoom_bar_dark)
        } else {
            binding.mirrorBackground.setBackgroundResource(R.drawable.bg_mirror_silver)
            binding.btnTheme.text = "✦  Silver"
            binding.zoomBar.setBackgroundResource(R.drawable.bg_zoom_bar_silver)
        }
    }

    // ── PREMIUM / ADS ─────────────────────────────────────────────────────────

    private fun updatePremiumUI() {
        if (premiumManager.isPremium) {
            binding.btnUpgrade.visibility = View.GONE
            binding.adBannerContainer.visibility = View.GONE
            binding.btnWatchAd.visibility = View.GONE
        } else {
            binding.btnUpgrade.visibility = View.VISIBLE
            val days = premiumManager.trialDaysLeft
            binding.btnUpgrade.text = if (days > 0) "${days}d free · \$5" else "Get Premium"

            adManager.loadBanner(binding.adBannerContainer)
            adManager.loadRewarded()
            adManager.loadInterstitial()
            binding.btnWatchAd.visibility = View.VISIBLE
        }
    }

    private fun scheduleAds() {
        if (premiumManager.showAds) {
            // Show interstitial after every 3rd session start
            sessionCount++
            if (sessionCount % 3 == 0) {
                Handler(Looper.getMainLooper()).postDelayed({
                    adManager.showInterstitial(this)
                }, 2000)
            }
        }
    }

    private fun setupBilling() {
        billingManager = BillingManager(
            context = this,
            premiumManager = premiumManager,
            onPurchaseSuccess = {
                updatePremiumUI()
                Toast.makeText(this, "Welcome to Premium! ✓", Toast.LENGTH_LONG).show()
            },
            onPurchaseFailed = { msg ->
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        )
        billingManager.init()
    }

    private fun openPaywall() {
        startActivityForResult(
            Intent(this, PaywallActivity::class.java),
            REQUEST_CODE_PAYWALL
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PAYWALL) {
            updatePremiumUI()
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        setupFullscreen()
        adManager.resumeBanner()
        updatePremiumUI()
        if (premiumManager.needsPaywall) openPaywall()
    }

    override fun onPause() {
        super.onPause()
        adManager.pauseBanner()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        adManager.destroyBanner()
        billingManager.destroy()
        uiHandler.removeCallbacks(hideUiRunnable)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }
}
