package to.influ.sdk.internal

import android.content.Context

/**
 * Local key-value persistence over SharedPreferences. Stores ONLY non-sensitive
 * attribution metadata (referral code, app_user_id, an attribution JSON blob, a flag),
 * so plain SharedPreferences is the right minimal choice (not EncryptedSharedPreferences).
 * Keys mirror the React Native SDK's `@influto/` prefix byte-for-byte.
 */
internal class Storage(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("influto_prefs", Context.MODE_PRIVATE)

    fun getString(key: String): String? = prefs.getString(key, null)

    fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun remove(vararg keys: String) {
        prefs.edit().apply { keys.forEach { remove(it) } }.apply()
    }

    companion object {
        const val ATTRIBUTION = "@influto/attribution"
        const val INFLUTO_CODE = "@influto/influto_code"
        const val APP_USER_ID = "@influto/app_user_id"
        const val INITIALIZED = "@influto/initialized"
        const val ACCESS = "@influto/access"
    }
}
