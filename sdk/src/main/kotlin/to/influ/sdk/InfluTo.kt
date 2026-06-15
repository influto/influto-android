package to.influ.sdk

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import to.influ.sdk.internal.DeviceInfo
import to.influ.sdk.internal.HttpClient
import to.influ.sdk.internal.JsonCodec
import to.influ.sdk.internal.RevenueCatBridge
import to.influ.sdk.internal.Storage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * InfluTo Android SDK — influencer attribution + store-direct purchase validation.
 *
 * Singleton; mirrors the React Native SDK's public surface and behaviors. Every network
 * method ships as a `suspend` fun (idiomatic Kotlin) and a `Result`-callback overload
 * (Java / non-coroutine interop). Fail-soft: only [initialize] and [reportPurchase] throw.
 *
 * ```kotlin
 * InfluTo.initialize(context, InfluToConfig(apiKey = "it_..."))
 * val attr = InfluTo.checkAttribution()
 * ```
 */
object InfluTo {

    private const val SDK_VERSION = "1.0.0"

    private var config: InfluToConfig? = null
    private var storage: Storage? = null
    private var http: HttpClient? = null

    @Volatile
    private var initialized = false

    /** Fire-and-forget scope for callback overloads + non-blocking events. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val debug: Boolean get() = config?.debug == true
    private fun requireHttp(): HttpClient = http ?: throw InfluToError.NotInitialized
    private fun requireStorage(): Storage = storage ?: throw InfluToError.NotInitialized

    private fun JSONObject.strOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    // ------------------------------------------------------------------ initialize

    /** Call once at startup. POST `/sdk/init`. THROWS on failure. */
    suspend fun initialize(context: Context, config: InfluToConfig) {
        this.config = config
        this.storage = Storage(context)
        this.http = HttpClient(config.apiUrl, config.apiKey, config.debug)
        try {
            val body = JSONObject().apply {
                put("app_version", config.appVersion ?: "unknown")
                put("sdk_version", SDK_VERSION)
                put("platform", "android")
            }
            val resp = requireHttp().postObject("/sdk/init", body)
            if (resp.optBoolean("initialized", false)) {
                initialized = true
                requireStorage().putString(Storage.INITIALIZED, "true")
                if (debug) Log.d("InfluTo", "SDK initialized")
                // Auto-capture purchases by default for store-direct apps (one-line integration).
                // RevenueCat apps report store_direct=false → stays silent.
                if (config.autoCapture && resp.optBoolean("store_direct", false)) {
                    tryStartBillingAutoCapture(context, config.oneTimeProductIds)
                }
            }
        } catch (e: Throwable) {
            Log.e("InfluTo", "Initialization failed: ${e.message}")
            throw e
        }
    }

    /** Reflectively start the optional `:sdk-billing` module if it's on the classpath, so the
     *  core `:sdk` keeps no Play Billing dependency (same pattern as the RevenueCat bridge). */
    private fun tryStartBillingAutoCapture(context: Context, oneTimeProductIds: Set<String>) {
        try {
            val clazz = Class.forName("to.influ.sdk.billing.InfluToBilling")
            val instance = clazz.getField("INSTANCE").get(null)
            // Prefer the 2-arg overload so declared one-time SKUs route to one-time validation;
            // fall back to the 1-arg form for an older :sdk-billing without it.
            try {
                clazz.getMethod("startObservation", Context::class.java, Set::class.java)
                    .invoke(instance, context, oneTimeProductIds)
            } catch (e: NoSuchMethodException) {
                clazz.getMethod("startObservation", Context::class.java).invoke(instance, context)
            }
            if (debug) Log.d("InfluTo", "auto-capture started (sdk-billing)")
        } catch (e: ClassNotFoundException) {
            if (debug) Log.d("InfluTo", "auto-capture: add to.influ:android-sdk-billing to enable it")
        } catch (e: Throwable) {
            Log.w("InfluTo", "auto-capture start failed: ${e.message}")
        }
    }

    fun initialize(context: Context, config: InfluToConfig, callback: (Result<Unit>) -> Unit) {
        scope.launch { callback(runCatching { initialize(context, config) }) }
    }

    // ------------------------------------------------------------- checkAttribution

    /** Track install + resolve install attribution. Fail-soft → `{attributed=false}`. */
    suspend fun checkAttribution(): AttributionResult {
        if (!initialized) throw InfluToError.NotInitialized
        return try {
            requireStorage().getString(Storage.ATTRIBUTION)?.let {
                return JsonCodec.attributionFromJson(JSONObject(it))
            }
            val resp = requireHttp().postObject("/sdk/track-install", DeviceInfo.trackInstallBody())
            val code = resp.strOrNull("referral_code")
            if (resp.optBoolean("attributed", false) && code != null) {
                val attribution = AttributionResult(
                    attributed = true,
                    referralCode = code,
                    attributionMethod = resp.strOrNull("attribution_method"),
                    clickedAt = resp.strOrNull("clicked_at"),
                    message = resp.strOrNull("message"),
                )
                requireStorage().putString(
                    Storage.ATTRIBUTION, JsonCodec.attributionToJson(attribution).toString()
                )
                requireStorage().putString(Storage.INFLUTO_CODE, code)
                setRevenueCatAttributes(code)
                attribution
            } else {
                AttributionResult(
                    attributed = false,
                    message = resp.strOrNull("message") ?: "No attribution found",
                )
            }
        } catch (e: Throwable) {
            if (debug) Log.d("InfluTo", "checkAttribution error: ${e.message}")
            AttributionResult(attributed = false, message = "Error checking attribution")
        }
    }

    fun checkAttribution(callback: (Result<AttributionResult>) -> Unit) {
        scope.launch { callback(runCatching { checkAttribution() }) }
    }

    // ----------------------------------------------------------------- identifyUser

    /** Persist app_user_id + POST `/sdk/identify`. Fire-and-forget; never throws. */
    suspend fun identifyUser(appUserId: String, properties: Map<String, Any?>? = null) {
        if (!initialized) {
            if (debug) Log.w("InfluTo", "SDK not initialized")
            return
        }
        requireStorage().putString(Storage.APP_USER_ID, appUserId)
        try {
            val body = JSONObject().apply {
                put("app_user_id", appUserId)
                put("properties", JsonCodec.mapToJsonObject(properties))
            }
            requireHttp().postObject("/sdk/identify", body)
        } catch (e: Throwable) {
            if (debug) Log.d("InfluTo", "identify error: ${e.message}")
        }
    }

    fun identifyUser(
        appUserId: String,
        properties: Map<String, Any?>? = null,
        callback: ((Result<Unit>) -> Unit)? = null,
    ) {
        scope.launch { val r = runCatching { identifyUser(appUserId, properties) }; callback?.invoke(r) }
    }

    // ------------------------------------------------------------------- trackEvent

    /** POST `/sdk/event` with an auto-generated UUID eventId if absent. Never throws. */
    suspend fun trackEvent(
        eventType: String,
        appUserId: String,
        properties: Map<String, Any?>? = null,
        referralCode: String? = null,
        eventId: String? = null,
    ) {
        if (!initialized) {
            if (debug) Log.w("InfluTo", "SDK not initialized")
            return
        }
        try {
            val body = JSONObject().apply {
                put("eventType", eventType)
                put("appUserId", appUserId)
                if (properties != null) put("properties", JsonCodec.mapToJsonObject(properties))
                if (referralCode != null) put("referralCode", referralCode)
                put("eventId", eventId ?: UUID.randomUUID().toString())
            }
            requireHttp().postObject("/sdk/event", body)
        } catch (e: Throwable) {
            if (debug) Log.d("InfluTo", "trackEvent error: ${e.message}")
        }
    }

    fun trackEvent(
        eventType: String,
        appUserId: String,
        properties: Map<String, Any?>? = null,
        referralCode: String? = null,
        eventId: String? = null,
        callback: ((Result<Unit>) -> Unit)? = null,
    ) {
        scope.launch {
            val r = runCatching { trackEvent(eventType, appUserId, properties, referralCode, eventId) }
            callback?.invoke(r)
        }
    }

    // ------------------------------------------------------------ getActiveCampaigns

    suspend fun getActiveCampaigns(): List<Campaign> {
        if (!initialized) return emptyList()
        return try {
            JsonCodec.toCampaignList(requireHttp().getArray("/sdk/campaigns"))
        } catch (e: Throwable) {
            if (debug) Log.d("InfluTo", "campaigns error: ${e.message}")
            emptyList()
        }
    }

    fun getActiveCampaigns(callback: (Result<List<Campaign>>) -> Unit) {
        scope.launch { callback(runCatching { getActiveCampaigns() }) }
    }

    // ----------------------------------------------------------- local read helpers

    /** Local read of the stored referral code. */
    fun getReferralCode(): String? = storage?.getString(Storage.INFLUTO_CODE)

    /** Local: stored code only if the stored attribution is `attributed`. */
    fun getPrefilledCode(): String? {
        val stored = storage?.getString(Storage.ATTRIBUTION) ?: return null
        return try {
            val a = JsonCodec.attributionFromJson(JSONObject(stored))
            if (a.attributed) a.referralCode else null
        } catch (_: Exception) {
            null
        }
    }

    // ----------------------------------------------------------------- validateCode

    suspend fun validateCode(code: String): CodeValidationResult {
        if (!initialized) {
            return CodeValidationResult(
                valid = false, error = "SDK not initialized", errorCode = CodeErrorCode.NETWORK_ERROR
            )
        }
        return try {
            val body = JSONObject().put("code", code.trim().uppercase())
            JsonCodec.toCodeValidationResult(requireHttp().postObject("/sdk/validate-code", body))
        } catch (e: Throwable) {
            if (debug) Log.d("InfluTo", "validateCode error: ${e.message}")
            CodeValidationResult(
                valid = false,
                error = "Network error or invalid response",
                errorCode = CodeErrorCode.NETWORK_ERROR,
            )
        }
    }

    fun validateCode(code: String, callback: (Result<CodeValidationResult>) -> Unit) {
        scope.launch { callback(runCatching { validateCode(code) }) }
    }

    // --------------------------------------------------------------- setReferralCode

    suspend fun setReferralCode(code: String, appUserId: String? = null): SetCodeResult {
        if (!initialized) return SetCodeResult(success = false, message = "SDK not initialized")
        val normalized = code.trim().uppercase()
        return try {
            requireStorage().putString(Storage.INFLUTO_CODE, normalized)
            val attribution = AttributionResult(
                attributed = true,
                referralCode = normalized,
                attributionMethod = "manual_entry",
                clickedAt = isoNow(),
                message = "Manually entered code",
            )
            requireStorage().putString(
                Storage.ATTRIBUTION, JsonCodec.attributionToJson(attribution).toString()
            )
            setRevenueCatAttributes(normalized)

            val body = JSONObject().put("code", normalized)
            if (appUserId != null) {
                body.put("app_user_id", appUserId)
                requireStorage().putString(Storage.APP_USER_ID, appUserId)
            }
            JsonCodec.toSetCodeResult(requireHttp().postObject("/sdk/set-referral-code", body))
        } catch (e: Throwable) {
            if (debug) Log.d("InfluTo", "setReferralCode error: ${e.message}")
            SetCodeResult(success = false, message = "Failed to set code: ${e.message}")
        }
    }

    fun setReferralCode(
        code: String,
        appUserId: String? = null,
        callback: (Result<SetCodeResult>) -> Unit,
    ) {
        scope.launch { callback(runCatching { setReferralCode(code, appUserId) }) }
    }

    // ------------------------------------------------------------------- applyCode

    suspend fun applyCode(code: String, appUserId: String? = null): CodeValidationResult {
        val validation = validateCode(code)
        if (!validation.valid) return validation.copy(applied = false)
        val set = setReferralCode(code, appUserId)
        return validation.copy(applied = set.success)
    }

    fun applyCode(code: String, appUserId: String? = null, callback: (Result<CodeValidationResult>) -> Unit) {
        scope.launch { callback(runCatching { applyCode(code, appUserId) }) }
    }

    // ------------------------------------------------------------------- checkAccess

    @Volatile private var accessCache: Triple<String, AccessResult, Long>? = null

    /**
     * Server-authoritative premium-access check (platform-independent comp). Works for BOTH
     * RevenueCat and store-direct apps. Fail-soft → `AccessResult(hasAccess=false)`; caches a
     * positive result ~5 min. Recommended gate: `rcEntitlementActive || checkAccess(uid).hasAccess`.
     */
    suspend fun checkAccess(appUserId: String? = null): AccessResult {
        if (!initialized) return AccessResult(hasAccess = false)
        val uid = appUserId ?: storage?.getString(Storage.APP_USER_ID)
            ?: return AccessResult(hasAccess = false)
        accessCache?.let { (cachedUid, result, ts) ->
            if (cachedUid == uid && result.hasAccess &&
                System.currentTimeMillis() - ts < 300_000L) return result
        }
        // Persisted positive cache survives cold starts (same 5-min TTL as the in-memory one).
        loadPersistedAccess(uid)?.let { return it }
        return try {
            val escaped = java.net.URLEncoder.encode(uid, "UTF-8")
            val resp = requireHttp().getObject("/sdk/access?app_user_id=$escaped")
            val result = JsonCodec.toAccessResult(resp)
            if (result.hasAccess) persistAccess(uid, result)
            result
        } catch (e: Throwable) {
            if (debug) Log.d("InfluTo", "checkAccess error: ${e.message}")
            AccessResult(hasAccess = false)
        }
    }

    /** Cache a positive access result in memory + persist it under [Storage.ACCESS]. */
    private fun persistAccess(uid: String, result: AccessResult) {
        val now = System.currentTimeMillis()
        accessCache = Triple(uid, result, now)
        try {
            val access = org.json.JSONObject()
                .put("has_access", result.hasAccess)
                .putOpt("source", result.source)
                .putOpt("entitlement", result.entitlement)
                .putOpt("expires_at", result.expiresAt)
                .putOpt("code", result.code)
            val env = org.json.JSONObject().put("uid", uid).put("ts", now).put("access", access)
            storage?.putString(Storage.ACCESS, env.toString())
        } catch (_: Throwable) { /* best-effort; server is the backstop */ }
    }

    /** Read a still-valid (~5 min) persisted positive access result for [uid], else null. */
    private fun loadPersistedAccess(uid: String): AccessResult? {
        val raw = storage?.getString(Storage.ACCESS) ?: return null
        return try {
            val env = org.json.JSONObject(raw)
            val ts = env.optLong("ts")
            if (env.optString("uid") != uid) return null
            if (System.currentTimeMillis() - ts >= 300_000L) return null
            val result = JsonCodec.toAccessResult(env.getJSONObject("access"))
            if (!result.hasAccess) return null
            accessCache = Triple(uid, result, ts)
            result
        } catch (_: Throwable) { null }
    }

    fun checkAccess(appUserId: String? = null, callback: (Result<AccessResult>) -> Unit) {
        scope.launch { callback(runCatching { checkAccess(appUserId) }) }
    }

    // --------------------------------------------------------------- reportPurchase

    /**
     * Store-direct purchase report (no RevenueCat). Pass the Google Play Billing
     * `purchaseToken` (`Purchase.getPurchaseToken()`). THROWS on failure;
     * [InfluToError.Server.retryable] is true for a 503 (FX unavailable).
     */
    suspend fun reportPurchase(
        purchaseToken: String,
        appUserId: String? = null,
        referralCode: String? = null,
        productId: String? = null,
        price: Double? = null,
        currency: String? = null,
    ): PurchaseResult {
        if (!initialized) throw InfluToError.NotInitialized
        val code = referralCode ?: requireStorage().getString(Storage.INFLUTO_CODE)
        val user = appUserId ?: requireStorage().getString(Storage.APP_USER_ID)
        val body = JSONObject().apply {
            put("platform", "android")
            put("purchaseToken", purchaseToken)
            // One-time products only: productId routes the backend to products.get; price is
            // the amount the user paid (ProductPurchase carries none). Omit for subscriptions.
            if (productId != null) put("productId", productId)
            if (price != null) put("price", price)
            if (currency != null) put("currency", currency)
            if (code != null) put("referralCode", code)
            if (user != null) put("appUserId", user)
        }
        val resp = requireHttp().postObject("/sdk/purchase", body)
        if (debug) Log.d("InfluTo", "purchase reported: ${resp.strOrNull("validated")}")
        return JsonCodec.toPurchaseResult(resp)
    }

    fun reportPurchase(
        purchaseToken: String,
        appUserId: String? = null,
        referralCode: String? = null,
        productId: String? = null,
        price: Double? = null,
        currency: String? = null,
        callback: (Result<PurchaseResult>) -> Unit,
    ) {
        scope.launch {
            callback(runCatching {
                reportPurchase(purchaseToken, appUserId, referralCode, productId, price, currency)
            })
        }
    }

    // ------------------------------------------------------------- clearAttribution

    fun clearAttribution() {
        storage?.remove(Storage.ATTRIBUTION, Storage.INFLUTO_CODE, Storage.APP_USER_ID)
    }

    // ------------------------------------------------------------------- internals

    private fun setRevenueCatAttributes(code: String) {
        RevenueCatBridge.setInfluToAttributes(code, config?.revenueCatAttributeSetter, debug)
    }

    private fun isoNow(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date())
    }
}
