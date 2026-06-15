package to.influ.sample

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Tiny client for the SDK-key-authed `GET /sdk/recent-conversions` feedback endpoint,
 * so the sample can confirm in-app that a purchase landed in InfluTo (and attributed).
 */
object SampleBackend {
    suspend fun recentConversionsSummary(baseUrl: String, apiKey: String, appUserId: String): String =
        withContext(Dispatchers.IO) {
            val q = URLEncoder.encode(appUserId, "UTF-8")
            val conn = URL("$baseUrl/sdk/recent-conversions?app_user_id=$q&limit=10")
                .openConnection() as HttpURLConnection
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            try {
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
                if (code !in 200..299) return@withContext "HTTP $code: $body"
                summarize(body)
            } catch (e: Exception) {
                "Couldn't check: ${e.message}"
            } finally {
                conn.disconnect()
            }
        }

    private fun summarize(json: String): String {
        val obj = JSONObject(json)
        val convs = obj.optJSONArray("conversions")
        if (convs == null || convs.length() == 0) return "No purchase recorded yet for this user."
        val first = convs.getJSONObject(0)
        val code = if (first.isNull("referral_code")) "—" else first.optString("referral_code")
        return "✅ ${obj.optInt("count")} event(s) · ${obj.optInt("attributed_count")} attributed.\n" +
            "Latest: ${first.optString("event_type")} · ${first.optString("environment")} · code=$code"
    }
}
