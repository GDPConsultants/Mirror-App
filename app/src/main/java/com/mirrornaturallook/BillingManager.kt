package com.mirrornaturallook

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.*

/**
 * Handles Google Play Billing for the $5 lifetime premium purchase.
 *
 * Product setup in Play Console:
 *   - Product ID: mirror_lifetime_premium
 *   - Type: One-time (non-consumable)
 *   - Price: $4.99 USD
 */
class BillingManager(
    private val context: Context,
    private val premiumManager: PremiumManager,
    private val onPurchaseSuccess: () -> Unit,
    private val onPurchaseFailed: (String) -> Unit
) {
    companion object {
        private const val TAG = "BillingManager"
    }

    private var billingClient: BillingClient? = null
    private var productDetails: ProductDetails? = null

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { handlePurchase(it) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.d(TAG, "User cancelled purchase")
            }
            else -> {
                onPurchaseFailed("Purchase failed: ${result.debugMessage}")
            }
        }
    }

    fun init() {
        billingClient = BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases()
            .build()

        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing connected")
                    CoroutineScope(Dispatchers.IO).launch {
                        queryProductDetails()
                        restorePurchases()
                    }
                }
            }
            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing disconnected")
            }
        })
    }

    private suspend fun queryProductDetails() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PremiumManager.PRODUCT_ID_LIFETIME)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        val result = billingClient?.queryProductDetails(params)
        if (result?.billingResult?.responseCode == BillingClient.BillingResponseCode.OK) {
            productDetails = result.productDetailsList?.firstOrNull()
            Log.d(TAG, "Product loaded: ${productDetails?.name}")
        }
    }

    /** Returns price string like "$4.99" to display in UI */
    fun getPriceString(): String {
        return productDetails
            ?.oneTimePurchaseOfferDetails
            ?.formattedPrice
            ?: "$4.99"
    }

    fun launchPurchase(activity: Activity) {
        val details = productDetails ?: run {
            onPurchaseFailed("Store not available. Please try again.")
            return
        }
        val productDetailsParams = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .build()
        )
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParams)
            .build()

        billingClient?.launchBillingFlow(activity, flowParams)
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                CoroutineScope(Dispatchers.IO).launch {
                    billingClient?.acknowledgePurchase(params)
                }
            }
            premiumManager.activatePremium(purchase.purchaseToken)
            CoroutineScope(Dispatchers.Main).launch {
                onPurchaseSuccess()
            }
        }
    }

    /** Restore purchases — called on app start and from Paywall */
    suspend fun restorePurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val result = billingClient?.queryPurchasesAsync(params)
        val purchases = result?.purchasesList ?: emptyList()
        purchases.forEach { purchase ->
            if (purchase.products.contains(PremiumManager.PRODUCT_ID_LIFETIME) &&
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                premiumManager.activatePremium(purchase.purchaseToken)
                withContext(Dispatchers.Main) {
                    onPurchaseSuccess()
                }
            }
        }
    }

    fun destroy() {
        billingClient?.endConnection()
    }
}
