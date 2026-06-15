package to.influ.sdk

/** Errors thrown by the throwing methods ([InfluTo.initialize], [InfluTo.reportPurchase]). */
sealed class InfluToError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** A method requiring initialization was called before [InfluTo.initialize]. */
    object NotInitialized :
        InfluToError("InfluTo SDK not initialized. Call InfluTo.initialize() first.")

    /** A transport-level failure (no/failed connection). */
    data class Transport(override val cause: Throwable?) :
        InfluToError("Network transport error", cause)

    /** The server returned a non-2xx status. [retryable] is true for 503 (FX unavailable). */
    data class Server(val status: Int, val body: String?) :
        InfluToError("Server error $status" + (body?.let { ": ${it.take(200)}" } ?: "")) {
        val retryable: Boolean get() = status == 503 || status >= 500
        /** 400 = the app is not configured for store-direct validation on this platform. */
        val notConfigured: Boolean get() = status == 400
    }

    /** The response body could not be parsed. */
    data class Decoding(override val cause: Throwable?) :
        InfluToError("Failed to decode response", cause)
}
