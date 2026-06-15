package to.influ.sdk.billing

import android.content.Context

/**
 * SharedPreferences-backed dedup set of already-auto-reported `purchaseToken`s, so opt-in
 * observation/back-sync never re-reports across launches. Kept in its own prefs file so the
 * billing module doesn't reach into `:sdk` internals.
 */
internal class SentPurchaseStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isSent(purchaseToken: String): Boolean = prefs.getBoolean(key(purchaseToken), false)

    fun markSent(purchaseToken: String) {
        prefs.edit().putBoolean(key(purchaseToken), true).apply()
    }

    private fun key(purchaseToken: String) = KEY_PREFIX + purchaseToken

    companion object {
        private const val PREFS = "influto_billing_sync_prefs"
        private const val KEY_PREFIX = "influto.purchase_sync.android."
    }
}
