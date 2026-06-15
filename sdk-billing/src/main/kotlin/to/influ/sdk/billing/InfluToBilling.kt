package to.influ.sdk.billing

import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import to.influ.sdk.InfluTo
import kotlin.coroutines.resume

/**
 * Counters from [InfluToBilling.syncExistingPurchases]: `fetched` = purchases seen,
 * `sent` = newly reported, `failed` = reports that threw (deduped ones count as neither).
 */
data class PurchaseSyncResult(
    val fetched: Int,
    val sent: Int,
    val failed: Int,
    val success: Boolean,
)

/**
 * OPT-IN automatic Play Billing purchase capture + one-shot back-sync. Lives in the separate
 * `to.influ:android-sdk-billing` artifact so the core `:sdk` keeps no Play Billing dependency.
 * Runs only when the host calls it, so it never double-reports against the RevenueCat or
 * manual-[InfluTo.reportPurchase] paths.
 *
 *  - [report] (recommended): hand us a `Purchase` from the host's OWN BillingClient — no
 *    second client is created. Pass `oneTime=true` (+ the price you paid) for one-time products.
 *  - [startObservation]: we run our own connected BillingClient — use only when InfluTo is the
 *    sole biller. Pass `oneTimeProductIds` so consumable / one-time SKUs route correctly.
 *
 * Subscriptions vs one-time: the Play `Purchase` object doesn't carry the product TYPE, so the
 * back-sync infers it from the per-type `queryPurchasesAsync` (SUBS vs INAPP), and the live
 * observer / host-fed paths rely on the declared `oneTimeProductIds` / `oneTime` flag. For a
 * one-time product the SDK sends `productId` (Google `products.get` needs it) + the queried
 * price; subscriptions send neither. Deduped on `purchaseToken`; the backend is the final anchor.
 */
object InfluToBilling {

    private const val TAG = "InfluToBilling"

    /** Module-local IO scope for the self-contained observer's callbacks. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var observerClient: BillingClient? = null
    private var observerOneTimeIds: Set<String> = emptySet()

    private enum class Outcome { SENT, FAILED, SKIPPED }

    // ----------------------------------------------------- mode A: report(purchase)

    /**
     * Report a single `Purchase` the host already obtained from its own BillingClient. Pass
     * `oneTime=true` (+ the `price`/`currency` you charged) for a one-time / consumable product;
     * subscriptions leave them default. Deduped on `purchaseToken`; only reports `PURCHASED`
     * state. Suspends for the network call but never throws. Returns true if newly reported.
     */
    suspend fun report(
        context: Context,
        purchase: Purchase,
        oneTime: Boolean = false,
        price: Double? = null,
        currency: String? = null,
    ): Boolean {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return false
        val productId = if (oneTime) purchase.products.firstOrNull() else null
        return reportOutcome(context.applicationContext, purchase, productId, price, currency) == Outcome.SENT
    }

    // ------------------------------------------------- mode B: one-shot back-sync

    /**
     * One-shot back-sync of EXISTING purchases. Spins up a temporary BillingClient, connects,
     * `queryPurchasesAsync` for SUBS then INAPP, reports each not-yet-sent purchase (INAPP →
     * one-time, with its queried price), and ends the connection. Returns the counters.
     */
    suspend fun syncExistingPurchases(context: Context): PurchaseSyncResult {
        val app = context.applicationContext
        val client = newClient(app, listener = PurchasesUpdatedListener { _, _ -> /* no-op */ })
        return try {
            if (!client.connect()) {
                Log.w(TAG, "syncExistingPurchases: BillingClient failed to connect")
                PurchaseSyncResult(0, 0, 0, success = false)
            } else {
                var fetched = 0; var sent = 0; var failed = 0
                // SUBS first (never one-time → no productId), then INAPP (one-time → productId + price).
                for (p in client.queryPurchasesForType(BillingClient.ProductType.SUBS)) {
                    if (p.purchaseState != Purchase.PurchaseState.PURCHASED) continue
                    fetched++
                    when (reportOutcome(app, p, null, null, null)) {
                        Outcome.SENT -> sent++; Outcome.FAILED -> failed++; Outcome.SKIPPED -> {}
                    }
                }
                for (p in client.queryPurchasesForType(BillingClient.ProductType.INAPP)) {
                    if (p.purchaseState != Purchase.PurchaseState.PURCHASED) continue
                    fetched++
                    val pid = p.products.firstOrNull()
                    val price = if (pid != null) client.oneTimePrice(pid) else null
                    when (reportOutcome(app, p, pid, price?.first, price?.second)) {
                        Outcome.SENT -> sent++; Outcome.FAILED -> failed++; Outcome.SKIPPED -> {}
                    }
                }
                PurchaseSyncResult(fetched, sent, failed, success = failed == 0)
            }
        } finally {
            client.endConnection()
        }
    }

    // -------------------------------------------- mode B2: self-contained observer

    /**
     * FULL-AUTO opt-in: start our OWN connected BillingClient that auto-reports new purchases,
     * and immediately back-syncs existing ones. Pass [oneTimeProductIds] so consumable / one-time
     * SKUs are routed correctly (the live `Purchase` carries no product type). Use only when
     * InfluTo is the sole biller. Idempotent. `@JvmOverloads` keeps a no-arg-extras
     * `startObservation(Context)` for the core SDK's reflection-based default activation.
     */
    @JvmOverloads
    fun startObservation(context: Context, oneTimeProductIds: Set<String> = emptySet()) {
        val app = context.applicationContext
        if (observerClient != null) { Log.d(TAG, "already observing"); return }
        observerOneTimeIds = oneTimeProductIds

        val listener = PurchasesUpdatedListener { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                scope.launch { reportLive(app, purchases) }
            }
        }
        val client = newClient(app, listener)
        observerClient = client
        scope.launch {
            if (client.connect()) {
                syncExistingPurchases(app) // sweep history once on enable
            } else {
                Log.w(TAG, "startObservation: BillingClient failed to connect")
            }
        }
    }

    /** Stop + disconnect the self-contained observer. Safe when not observing. */
    fun stopObservation() {
        observerClient?.endConnection()
        observerClient = null
        observerOneTimeIds = emptySet()
    }

    // --------------------------------------------------------------- internals

    /** Live `onPurchasesUpdated` purchases: route one-time SKUs (declared) with their price. */
    private suspend fun reportLive(appContext: Context, purchases: List<Purchase>) {
        val client = observerClient
        for (p in purchases) {
            if (p.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            val pid = p.products.firstOrNull()
            val oneTime = pid != null && pid in observerOneTimeIds
            val price = if (oneTime && client != null) client.oneTimePrice(pid!!) else null
            reportOutcome(appContext, p, if (oneTime) pid else null, price?.first, price?.second)
        }
    }

    private suspend fun reportOutcome(
        appContext: Context, purchase: Purchase,
        productId: String?, price: Double?, currency: String?,
    ): Outcome {
        val token = purchase.purchaseToken
        val dedup = SentPurchaseStore(appContext)
        if (dedup.isSent(token)) return Outcome.SKIPPED
        return try {
            // Reuse the exact manual path: attaches stored referral code + app_user_id, POSTs.
            InfluTo.reportPurchase(purchaseToken = token, productId = productId, price = price, currency = currency)
            dedup.markSent(token)
            Outcome.SENT
        } catch (e: Throwable) {
            Log.w(TAG, "auto reportPurchase failed for token: ${e.message}")
            Outcome.FAILED
        }
    }

    private fun newClient(context: Context, listener: PurchasesUpdatedListener): BillingClient =
        BillingClient.newBuilder(context)
            .setListener(listener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .enableAutoServiceReconnection() // v8+: leave onBillingServiceDisconnected a no-op
            .build()

    private suspend fun BillingClient.connect(): Boolean = suspendCancellableCoroutine { cont ->
        startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (cont.isActive) cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
            }
            override fun onBillingServiceDisconnected() { /* auto-reconnect handles it */ }
        })
    }

    private suspend fun BillingClient.queryPurchasesForType(type: String): List<Purchase> {
        val result = queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(type).build()
        )
        return if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            result.purchasesList
        } else {
            Log.w(TAG, "queryPurchasesAsync($type) -> ${result.billingResult.responseCode}")
            emptyList()
        }
    }

    /** The one-time price+currency for an INAPP product, from its ProductDetails (null on miss). */
    private suspend fun BillingClient.oneTimePrice(productId: String): Pair<Double, String>? {
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(productId)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            )
        ).build()
        val pd = try {
            queryProductDetails(params).productDetailsList?.firstOrNull()
        } catch (e: Throwable) {
            Log.w(TAG, "oneTimePrice query failed for $productId: ${e.message}"); null
        } ?: return null
        val offer = pd.oneTimePurchaseOfferDetails ?: return null
        return (offer.priceAmountMicros / 1_000_000.0) to offer.priceCurrencyCode
    }
}
