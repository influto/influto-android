package to.influ.sdk.internal

import org.json.JSONArray
import org.json.JSONObject
import to.influ.sdk.AccessResult
import to.influ.sdk.AttributionResult
import to.influ.sdk.Campaign
import to.influ.sdk.CampaignInfo
import to.influ.sdk.CodeErrorCode
import to.influ.sdk.CodeValidationResult
import to.influ.sdk.Influencer
import to.influ.sdk.PurchaseResult
import to.influ.sdk.SetCodeResult

/** Maps backend JSON (`org.json`) to/from the public data classes. */
internal object JsonCodec {

    private fun JSONObject.strOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key) else null

    private fun JSONObject.dblOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() } else null

    private fun JSONObject.intOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null

    private fun JSONObject.boolOr(key: String, default: Boolean): Boolean =
        if (has(key) && !isNull(key)) optBoolean(key, default) else default

    private fun JSONObject.objOrNull(key: String): JSONObject? =
        if (has(key) && !isNull(key)) optJSONObject(key) else null

    // ---- responses ----

    fun toCampaignList(arr: JSONArray): List<Campaign> = buildList {
        for (i in 0 until arr.length()) {
            val j = arr.optJSONObject(i) ?: continue
            add(
                Campaign(
                    id = j.strOrNull("id") ?: "",
                    name = j.strOrNull("name") ?: "",
                    description = j.strOrNull("description"),
                    commissionPercentage = j.dblOrNull("commission_percentage"),
                )
            )
        }
    }

    fun toCodeValidationResult(j: JSONObject) = CodeValidationResult(
        valid = j.boolOr("valid", false),
        code = j.strOrNull("code"),
        campaign = toCampaignInfo(j.objOrNull("campaign")),
        influencer = toInfluencer(j.objOrNull("influencer")),
        customData = jsonObjectToMap(j.objOrNull("custom_data")),
        message = j.strOrNull("message"),
        error = j.strOrNull("error"),
        errorCode = parseErrorCode(j.strOrNull("error_code")),
    )

    fun toSetCodeResult(j: JSONObject) = SetCodeResult(
        success = j.boolOr("success", false),
        code = j.strOrNull("code"),
        message = j.strOrNull("message"),
        campaign = toCampaignInfo(j.objOrNull("campaign")),
        freeAccess = if (j.has("free_access") && !j.isNull("free_access")) j.optBoolean("free_access") else null,
        grantsAccess = if (j.has("grants_access") && !j.isNull("grants_access")) j.optBoolean("grants_access") else null,
        entitlement = j.strOrNull("entitlement"),
        expiresAt = j.strOrNull("expires_at"),
    )

    fun toAccessResult(j: JSONObject) = AccessResult(
        hasAccess = j.boolOr("has_access", false),
        source = j.strOrNull("source"),
        entitlement = j.strOrNull("entitlement"),
        expiresAt = j.strOrNull("expires_at"),
        code = j.strOrNull("code"),
    )

    fun toPurchaseResult(j: JSONObject) = PurchaseResult(
        success = j.boolOr("success", false),
        validated = j.strOrNull("validated"),
        environment = j.strOrNull("environment"),
        eventType = j.strOrNull("event_type"),
        result = jsonObjectToMap(j.objOrNull("result")),
    )

    private fun toCampaignInfo(j: JSONObject?): CampaignInfo? {
        if (j == null) return null
        return CampaignInfo(
            id = j.strOrNull("id") ?: "",
            name = j.strOrNull("name") ?: "",
            description = j.strOrNull("description"),
            commissionPercentage = j.dblOrNull("commission_percentage"),
            campaignType = j.strOrNull("campaign_type"),
        )
    }

    private fun toInfluencer(j: JSONObject?): Influencer? {
        if (j == null) return null
        return Influencer(
            name = j.strOrNull("name") ?: "",
            socialHandle = j.strOrNull("social_handle"),
            followerCount = j.intOrNull("follower_count"),
        )
    }

    private fun parseErrorCode(s: String?): CodeErrorCode? = when (s) {
        null -> null
        "INVALID_FORMAT" -> CodeErrorCode.INVALID_FORMAT
        "CODE_NOT_FOUND" -> CodeErrorCode.CODE_NOT_FOUND
        "CODE_EXPIRED" -> CodeErrorCode.CODE_EXPIRED
        "NETWORK_ERROR" -> CodeErrorCode.NETWORK_ERROR
        else -> CodeErrorCode.UNKNOWN
    }

    // ---- local attribution blob (camelCase, matching the RN persistence shape) ----

    fun attributionToJson(a: AttributionResult): JSONObject = JSONObject().apply {
        put("attributed", a.attributed)
        a.referralCode?.let { put("referralCode", it) }
        a.attributionMethod?.let { put("attributionMethod", it) }
        a.clickedAt?.let { put("clickedAt", it) }
        a.message?.let { put("message", it) }
    }

    fun attributionFromJson(j: JSONObject) = AttributionResult(
        attributed = j.boolOr("attributed", false),
        referralCode = j.strOrNull("referralCode"),
        attributionMethod = j.strOrNull("attributionMethod"),
        clickedAt = j.strOrNull("clickedAt"),
        message = j.strOrNull("message"),
    )

    // ---- generic map <-> json (properties / custom_data / result) ----

    fun mapToJsonObject(m: Map<String, Any?>?): JSONObject {
        val j = JSONObject()
        m?.forEach { (k, v) -> j.put(k, v ?: JSONObject.NULL) }
        return j
    }

    fun jsonObjectToMap(j: JSONObject?): Map<String, Any?>? {
        if (j == null) return null
        val m = LinkedHashMap<String, Any?>()
        for (key in j.keys()) {
            m[key] = unwrap(j.get(key))
        }
        return m
    }

    private fun jsonArrayToList(a: JSONArray): List<Any?> =
        (0 until a.length()).map { unwrap(a.get(it)) }

    private fun unwrap(v: Any?): Any? = when (v) {
        is JSONObject -> jsonObjectToMap(v)
        is JSONArray -> jsonArrayToList(v)
        JSONObject.NULL -> null
        else -> v
    }
}
