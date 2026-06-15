package to.influ.sdk

/**
 * Configuration for [InfluTo.initialize].
 *
 * @property apiKey Your InfluTo API key (from the dashboard).
 * @property debug Enable debug logging.
 * @property apiUrl Base API URL. Defaults to `https://influ.to/api` (override for testing).
 * @property appVersion Your app's version string, reported on `/sdk/init` for telemetry.
 * @property autoCapture Automatically capture + report Play Billing purchases (store-direct
 *   apps only). Default `true`. When on, `initialize` reflectively starts the optional
 *   `to.influ:android-sdk-billing` module (if that artifact is on the classpath) — no manual
 *   `reportPurchase` needed. It activates only when the backend reports the app is store-direct
 *   (RevenueCat apps are unaffected). Set `false` to manage purchase reporting yourself.
 * @property revenueCatAttributeSetter OPTIONAL. If the host uses RevenueCat, wire this to
 *   `Purchases.sharedInstance.setAttributes(it)`. The SDK calls it with
 *   `{influto_code, influto_referral:"true"}` on attribution / setReferralCode. If null,
 *   the SDK falls back to a reflection-based RevenueCat detection (no compile dependency).
 * @property oneTimeProductIds Your one-time / consumable Play product ids. A Play purchase
 *   carries no product TYPE, so default-on auto-capture can only route a one-time product to
 *   one-time validation (vs subscription) if its id is listed here. Omit if you only sell
 *   subscriptions.
 */
data class InfluToConfig(
    val apiKey: String,
    val debug: Boolean = false,
    val apiUrl: String = "https://influ.to/api",
    val appVersion: String? = null,
    val autoCapture: Boolean = true,
    val revenueCatAttributeSetter: ((Map<String, String>) -> Unit)? = null,
    val oneTimeProductIds: Set<String> = emptySet(),
)
