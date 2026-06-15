package to.influ.sample

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Minimal Google Play Billing (v9) purchase manager — the best-practice reference an
 * integrator can copy. Connect → query a product by id → launch the flow → read the
 * `purchaseToken` → acknowledge → hand the token to `InfluTo.reportPurchase`.
 *
 * Real purchases require the app uploaded to a Play Console track + a license tester
 * (Play Billing can't transact on a bare emulator). In CI this just compiles.
 */
class BillingManager(
    context: Context,
    private val scope: CoroutineScope,
    private val onPurchaseToken: suspend (purchaseToken: String) -> Unit,
    private val onLog: (String) -> Unit,
) {
    private val updateListener = PurchasesUpdatedListener { result, purchases ->
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK ->
                purchases?.forEach { p -> scope.launch { handlePurchase(p) } }
            BillingClient.BillingResponseCode.USER_CANCELED -> onLog("Purchase cancelled")
            else -> onLog("Purchase failed: ${result.responseCode} ${result.debugMessage}")
        }
    }

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(updateListener)
        // Mandatory since v7; the no-arg overload was removed in v9.
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection() // v8+: leave onBillingServiceDisconnected a no-op
        .build()

    /** Connect to Play Billing. Returns true on OK. */
    suspend fun connect(): Boolean = suspendCancellableCoroutine { cont ->
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
            }
            override fun onBillingServiceDisconnected() { /* auto-reconnect handles it */ }
        })
    }

    /**
     * Query [productId] (tries SUBS, then INAPP) and launch the purchase flow. The
     * result is delivered to the PurchasesUpdatedListener → [handlePurchase].
     */
    suspend fun launchPurchase(activity: Activity, productId: String) {
        val pd = queryProduct(productId, BillingClient.ProductType.SUBS)
            ?: queryProduct(productId, BillingClient.ProductType.INAPP)
        if (pd == null) {
            onLog("Product '$productId' not found. Define it (Active) in Play Console and join the test track.")
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(pd)
        val offer = pd.subscriptionOfferDetails?.firstOrNull()
        if (offer != null) productParams.setOfferToken(offer.offerToken) // required for subs

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams.build()))
            .build()
        billingClient.launchBillingFlow(activity, flowParams)
    }

    private suspend fun queryProduct(productId: String, type: String) =
        billingClient.queryProductDetails(
            QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(productId)
                            .setProductType(type)
                            .build()
                    )
                )
                .build()
        ).productDetailsList?.firstOrNull()

    private suspend fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
            onLog("Purchase state=${purchase.purchaseState} (pending?) — waiting.")
            return
        }
        val token = purchase.purchaseToken
        if (!purchase.isAcknowledged) {
            // Acknowledge within 3 days or Play auto-refunds.
            billingClient.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()
            )
        }
        onPurchaseToken(token)
    }

    fun endConnection() = billingClient.endConnection()
}
