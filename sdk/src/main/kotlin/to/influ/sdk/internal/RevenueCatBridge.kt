package to.influ.sdk.internal

import android.util.Log

/**
 * Sets the InfluTo RevenueCat subscriber attributes WITHOUT a hard dependency on
 * RevenueCat. Prefers a host-supplied setter (the clean path); falls back to reflection
 * so a host that simply has the RevenueCat SDK on its classpath gets auto-integration
 * (matching the RN SDK's `require('react-native-purchases')` behavior).
 *
 * The two attributes the backend's RC webhook + Targeting rules read:
 *   influto_code     = <referral code>
 *   influto_referral = "true"   (a STRING — a boolean would break Targeting)
 */
internal object RevenueCatBridge {

    fun setInfluToAttributes(
        code: String,
        hostSetter: ((Map<String, String>) -> Unit)?,
        debug: Boolean,
    ) {
        val attrs = mapOf("influto_code" to code, "influto_referral" to "true")

        if (hostSetter != null) {
            try {
                hostSetter(attrs)
                if (debug) Log.d("InfluTo", "RevenueCat attributes set via host setter")
            } catch (e: Throwable) {
                if (debug) Log.d("InfluTo", "host RevenueCat setter threw: ${e.message}")
            }
            return
        }

        // Fallback: reflection (no compile dependency, no crash if absent).
        try {
            val purchasesClass = Class.forName("com.revenuecat.purchases.Purchases")
            val shared = purchasesClass.getMethod("getSharedInstance").invoke(null) ?: return
            val setAttributes = shared.javaClass.getMethod("setAttributes", Map::class.java)
            setAttributes.invoke(shared, attrs)
            if (debug) Log.d("InfluTo", "RevenueCat attributes set via reflection")
        } catch (_: ClassNotFoundException) {
            if (debug) Log.d("InfluTo", "RevenueCat not found — set influto_code manually")
        } catch (e: Throwable) {
            // present but not configured yet, or API mismatch — fail soft.
            if (debug) Log.d("InfluTo", "RevenueCat present but not usable: ${e.message}")
        }
    }
}
