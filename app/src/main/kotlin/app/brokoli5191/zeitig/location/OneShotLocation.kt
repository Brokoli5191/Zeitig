package app.brokoli5191.zeitig.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import app.brokoli5191.zeitig.core.model.GeoPoint
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * "Use my location once": a single fix from the platform LocationManager – no Google Play
 * Services, no background location, nothing stored except the coordinates the user saves.
 */
object OneShotLocation {
    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun current(
        context: Context,
        timeoutMillis: Long = 20_000,
    ): GeoPoint? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val provider =
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                .firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) } ?: return null
        val location =
            withTimeoutOrNull(timeoutMillis) {
                suspendCancellableCoroutine { continuation ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val signal = CancellationSignal()
                        continuation.invokeOnCancellation { signal.cancel() }
                        manager.getCurrentLocation(provider, signal, context.mainExecutor) { continuation.resume(it) }
                    } else {
                        val listener =
                            object : LocationListenerCompat {
                                override fun onLocationChanged(location: Location) {
                                    manager.removeUpdates(this)
                                    if (continuation.isActive) continuation.resume(location)
                                }
                            }
                        continuation.invokeOnCancellation { manager.removeUpdates(listener) }
                        @Suppress("DEPRECATION")
                        manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                    }
                }
            }
        return location?.let { GeoPoint(it.latitude, it.longitude) }
    }
}
