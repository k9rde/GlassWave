package com.example.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

object LocationHelper {

    @SuppressLint("MissingPermission")
    suspend fun getQuickMapsLink(context: Context): String? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        val lastBest = listOfNotNull(
            runCatching { manager.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull(),
            runCatching { manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()
        ).maxByOrNull { it.time }

        if (lastBest != null && (System.currentTimeMillis() - lastBest.time) < 120_000L) {
            return formatMapsUrl(lastBest)
        }

        val fresh = withTimeoutOrNull(5000L) {
            requestSingleFix(manager)
        } ?: lastBest

        return fresh?.let { formatMapsUrl(it) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestSingleFix(manager: LocationManager): Location? =
        suspendCancellableCoroutine { cont ->
            val provider = when {
                manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> {
                    if (cont.isActive) cont.resume(null)
                    return@suspendCancellableCoroutine
                }
            }

            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    manager.removeUpdates(this)
                    if (cont.isActive) cont.resume(location)
                }

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            try {
                manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                cont.invokeOnCancellation {
                    runCatching { manager.removeUpdates(listener) }
                }
            } catch (_: Exception) {
                if (cont.isActive) cont.resume(null)
            }
        }

    private fun formatMapsUrl(location: Location): String {
        val lat = String.format(java.util.Locale.US, "%.6f", location.latitude)
        val lng = String.format(java.util.Locale.US, "%.6f", location.longitude)
        return "https://maps.google.com/?q=$lat,$lng"
    }
}
