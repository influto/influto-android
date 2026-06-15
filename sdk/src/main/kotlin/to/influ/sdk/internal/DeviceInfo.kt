package to.influ.sdk.internal

import android.content.res.Resources
import android.os.Build
import org.json.JSONObject
import java.util.Locale
import java.util.TimeZone

/**
 * Builds the `/sdk/track-install` body from built-in platform sources (no third-party deps).
 * Deliberately does NOT collect the advertising ID (so no Ad-ID permission or
 * ads Data-Safety disclosure is required).
 */
internal object DeviceInfo {

    fun trackInstallBody(): JSONObject = JSONObject().apply {
        put("platform", "android")
        put("device_brand", Build.BRAND)
        put("device_model", Build.MODEL)
        put("os_version", Build.VERSION.RELEASE)
        try {
            val dm = Resources.getSystem().displayMetrics
            put("screen_resolution", "${dm.widthPixels}x${dm.heightPixels}")
        } catch (_: Exception) {
            // omit — never send a placeholder
        }
        put("timezone", TimeZone.getDefault().id)
        put("language", Locale.getDefault().toLanguageTag())
        // device_id intentionally omitted.
    }
}
