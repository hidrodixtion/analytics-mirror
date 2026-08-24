import android.content.Context
import androidx.core.content.edit
import com.hightouch.analytics.Cartographer
import com.hightouch.analytics.Middleware
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import timber.log.Timber

/**
 * Mirrors every HighTouch event to a local log-viewer app over plain HTTP. Pass-through: the payload is handed to the next
 * middleware untouched.
 *
 * Debug builds only:
 *
 * if (BuildConfig.DEBUG) useSourceMiddleware(AnalyticsMirrorMiddleware(context))
 *
 * The default host is the emulator alias for the host machine's loopback. On a physical device
 * point it at the Mac's LAN IP: `middleware.host = "192.168.1.42:9977"`.
 */
class AnalyticsMirrorMiddleware(context: Context) : Middleware {

    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val executor = Executors.newSingleThreadExecutor()

    // The SDK's own serialiser, so the viewer gets the same JSON the backend receives. org.json's
    // JSONObject(Map) is not a substitute: it silently nulls out every value it cannot classify,
    // which is anything that is not a primitive, Collection, Map or java.* type — enums included.
    private val cartographer = Cartographer.Builder().build()

    var host: String
        get() = preferences.getString(HOST, DEFAULT_HOST) ?: DEFAULT_HOST
        set(value) = preferences.edit { putString(HOST, value) }

    override fun intercept(chain: Middleware.Chain) {
        val payload = chain.payload()
        send(payload)
        chain.proceed(payload)
    }

    private fun send(payload: Map<String, Any?>) {
        // Host is resolved per send so a change takes effect without an app restart.
        val url = runCatching { URL("http://$host/event") }.getOrNull() ?: return
        // Serialised on the caller's thread: the payload keeps travelling down the chain.
        val body = cartographer.toJson(payload).toByteArray()

        executor.execute {
            runCatching {
                val connection = url.openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "POST"
                    connection.connectTimeout = TIMEOUT_MILLIS
                    connection.readTimeout = TIMEOUT_MILLIS
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.use { stream -> stream.write(body) }
                    connection.responseCode
                } finally {
                    connection.disconnect()
                }
            }.onFailure { error -> Timber.w(error, "Failed to mirror analytics event to $host") }
        }
    }

    companion object {
        private const val PREFERENCES = "analytics_mirror"
        private const val HOST = "host"

        /** `10.0.2.2` is the emulator's alias for the host machine's loopback. */
        private const val DEFAULT_HOST = "10.0.2.2:9977"
        private const val TIMEOUT_MILLIS = 2000
    }
}
