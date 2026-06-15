package to.influ.sdk.internal

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import to.influ.sdk.InfluToError
import java.net.HttpURLConnection
import java.net.URL

/**
 * No-third-party-dep HTTP over [HttpURLConnection] (the "built-in fetch" analog), wrapped in
 * `suspend`/`Dispatchers.IO`. Sets Bearer auth + JSON headers; throws [InfluToError.Server]
 * on non-2xx and [InfluToError.Transport] on connection failure.
 */
internal class HttpClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val debug: Boolean,
) {
    suspend fun postObject(path: String, body: JSONObject): JSONObject {
        val text = request("POST", path, body.toString())
        return JSONObject(text)
    }

    suspend fun getArray(path: String): JSONArray {
        val text = request("GET", path, null)
        // The endpoint may return a bare array, or {} on an edge — normalize.
        return if (text.trimStart().startsWith("[")) JSONArray(text) else JSONArray()
    }

    suspend fun getObject(path: String): JSONObject {
        val text = request("GET", path, null)
        return JSONObject(text)
    }

    private suspend fun request(method: String, path: String, body: String?): String =
        withContext(Dispatchers.IO) {
            val conn = try {
                (URL(baseUrl + path).openConnection() as HttpURLConnection)
            } catch (e: Exception) {
                throw InfluToError.Transport(e)
            }
            try {
                conn.requestMethod = method
                conn.connectTimeout = 30_000
                conn.readTimeout = 30_000
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                conn.setRequestProperty("Accept", "application/json")
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                if (debug) Log.d("InfluTo", "$method $path -> $code")
                if (code !in 200..299) throw InfluToError.Server(code, text)
                text
            } catch (e: InfluToError) {
                throw e
            } catch (e: Exception) {
                throw InfluToError.Transport(e)
            } finally {
                conn.disconnect()
            }
        }
}
