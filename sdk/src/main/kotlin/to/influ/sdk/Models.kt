package to.influ.sdk

/** Result of [InfluTo.checkAttribution]. */
data class AttributionResult(
    val attributed: Boolean,
    val referralCode: String? = null,
    val attributionMethod: String? = null,
    val clickedAt: String? = null,
    val confidence: Double? = null,
    val message: String? = null,
)

/** A campaign from `/sdk/campaigns`. */
data class Campaign(
    val id: String,
    val name: String,
    val description: String? = null,
    val commissionPercentage: Double? = null,
)

/** Campaign details nested in validate/set results. */
data class CampaignInfo(
    val id: String,
    val name: String,
    val description: String? = null,
    val commissionPercentage: Double? = null,
    val campaignType: String? = null,
)

/** Influencer details, when available. */
data class Influencer(
    val name: String,
    val socialHandle: String? = null,
    val followerCount: Int? = null,
)

/** Programmatic error codes from code validation. */
enum class CodeErrorCode { INVALID_FORMAT, CODE_NOT_FOUND, CODE_EXPIRED, NETWORK_ERROR, UNKNOWN }

/** Result of [InfluTo.validateCode] / [InfluTo.applyCode]. */
data class CodeValidationResult(
    val valid: Boolean,
    val code: String? = null,
    val campaign: CampaignInfo? = null,
    val influencer: Influencer? = null,
    val customData: Map<String, Any?>? = null,
    val message: String? = null,
    val error: String? = null,
    val errorCode: CodeErrorCode? = null,
    /** Populated by [InfluTo.applyCode]. */
    val applied: Boolean? = null,
)

/** Result of [InfluTo.setReferralCode]. */
data class SetCodeResult(
    val success: Boolean,
    val code: String? = null,
    val message: String? = null,
    val campaign: CampaignInfo? = null,
    /** True when this code is a developer free-access (comp) code. */
    val freeAccess: Boolean? = null,
    /** True when the backend granted native premium access for this redemption. */
    val grantsAccess: Boolean? = null,
    /** Granted entitlement id/lookup-key, if any. */
    val entitlement: String? = null,
    /** ISO-8601 expiry, or null for open-ended. */
    val expiresAt: String? = null,
)

/** Result of [InfluTo.checkAccess] — server-authoritative premium access (platform-independent comp). */
data class AccessResult(
    val hasAccess: Boolean,
    val source: String? = null,
    val entitlement: String? = null,
    val expiresAt: String? = null,
    val code: String? = null,
)

/** Result of [InfluTo.reportPurchase] (store-direct). */
data class PurchaseResult(
    val success: Boolean,
    /** The provider that validated the purchase: "apple" | "google" (a STRING). */
    val validated: String? = null,
    /** "PRODUCTION" | "SANDBOX". */
    val environment: String? = null,
    val eventType: String? = null,
    /** Opaque pipeline result from the backend. */
    val result: Map<String, Any?>? = null,
)
