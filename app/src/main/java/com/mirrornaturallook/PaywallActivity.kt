package com.mirrornaturallook

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mirrornaturallook.databinding.ActivityPaywallBinding
import kotlinx.coroutines.*

class PaywallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPaywallBinding
    private lateinit var premiumManager: PremiumManager
    private lateinit var billingManager: BillingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaywallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        premiumManager = PremiumManager(this)

        billingManager = BillingManager(
            context = this,
            premiumManager = premiumManager,
            onPurchaseSuccess = {
                Toast.makeText(this, "Welcome to Premium! ✓", Toast.LENGTH_LONG).show()
                setResult(RESULT_OK)
                finish()
            },
            onPurchaseFailed = { msg ->
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                binding.btnUnlock.isEnabled = true
                binding.btnUnlock.text = "Unlock for \$5  →"
            }
        )
        billingManager.init()

        setupUI()
    }

    private fun setupUI() {
        val days = premiumManager.trialDaysLeft
        val isExpired = premiumManager.isTrialExpired

        // Header message
        if (isExpired) {
            binding.tvTitle.text = "Your 30-day free trial has ended"
            binding.tvSubtitle.text = "Unlock lifetime ad-free access for a one-time payment."
            binding.btnContinueWithAds.visibility = View.GONE
        } else {
            binding.tvTitle.text = "$days days left in your trial"
            binding.tvSubtitle.text = "Upgrade now and never see an ad again."
            binding.btnContinueWithAds.visibility = View.VISIBLE
        }

        // Purchase
        binding.btnUnlock.setOnClickListener {
            binding.btnUnlock.isEnabled = false
            binding.btnUnlock.text = "Processing…"
            billingManager.launchPurchase(this)
        }

        // Continue with ads (only during trial)
        binding.btnContinueWithAds.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        // Restore purchase
        binding.btnRestore.setOnClickListener {
            binding.btnRestore.isEnabled = false
            binding.btnRestore.text = "Restoring…"
            CoroutineScope(Dispatchers.Main).launch {
                billingManager.restorePurchases()
                delay(2000)
                if (premiumManager.isPremium) {
                    Toast.makeText(this@PaywallActivity, "✓ Purchase restored!", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                } else {
                    Toast.makeText(this@PaywallActivity, "No previous purchase found.", Toast.LENGTH_SHORT).show()
                    binding.btnRestore.isEnabled = true
                    binding.btnRestore.text = "Restore previous purchase"
                }
            }
        }
    }

    override fun onBackPressed() {
        if (!premiumManager.isTrialExpired) {
            super.onBackPressed()
        }
        // If trial expired, user cannot dismiss paywall without action
    }

    override fun onDestroy() {
        super.onDestroy()
        billingManager.destroy()
    }
}
