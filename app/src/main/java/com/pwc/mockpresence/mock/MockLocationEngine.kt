package com.pwc.mockpresence.mock

import android.annotation.SuppressLint
import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import com.google.android.gms.location.LocationServices
import com.pwc.mockpresence.model.LatLng

/**
 * Pushes fake fixes to every source Office Presence can read:
 * the fused provider (geofencing, getCurrentLocation, PresenceService sampling)
 * and the GPS and network providers (anything using LocationManager directly).
 */
class MockLocationEngine(context: Context) {
    private val ctx = context.applicationContext
    private val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val fused = LocationServices.getFusedLocationProviderClient(ctx)
    private val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    private val added = mutableSetOf<String>()

    /** Returns an error message, or null when the mock providers are on. */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun start(): String? {
        if (!MockCheck.isMockApp(ctx)) {
            return "Not the mock location app. Open Developer options > Select mock location app > OP Mock Location."
        }
        for (p in providers) {
            try {
                runCatching { lm.removeTestProvider(p) }
                lm.addTestProvider(
                    p, false, false, false, false, true, true, true,
                    Criteria.POWER_LOW, Criteria.ACCURACY_FINE,
                )
                lm.setTestProviderEnabled(p, true)
                added += p
            } catch (e: SecurityException) {
                return "Android refused the $p test provider: ${e.message}"
            } catch (e: Exception) {
                MockState.log("Could not add $p test provider: ${e.message}")
            }
        }
        try {
            fused.setMockMode(true)
                .addOnFailureListener { MockState.log("Fused mock mode failed: ${it.message}") }
        } catch (e: SecurityException) {
            return "Location permission is needed for fused mock mode."
        }
        return null
    }

    /** Returns an error message, or null when every provider accepted the fix. */
    @SuppressLint("MissingPermission")
    fun push(p: LatLng, accuracyM: Float, speedMps: Float, bearingDeg: Float): String? {
        var error: String? = null
        for (prov in added) {
            try {
                lm.setTestProviderLocation(prov, build(prov, p, accuracyM, speedMps, bearingDeg))
            } catch (e: Exception) {
                error = "$prov: ${e.message}"
            }
        }
        try {
            fused.setMockLocation(build("fused", p, accuracyM, speedMps, bearingDeg))
        } catch (e: Exception) {
            error = "fused: ${e.message}"
        }
        return error
    }

    /** Turns mock mode off and gives the real GPS back. */
    @SuppressLint("MissingPermission")
    fun stop() {
        runCatching { fused.setMockMode(false) }
        for (p in added) {
            runCatching { lm.setTestProviderEnabled(p, false) }
            runCatching { lm.removeTestProvider(p) }
        }
        added.clear()
    }

    private fun build(provider: String, p: LatLng, acc: Float, speed: Float, bearing: Float) =
        Location(provider).apply {
            latitude = p.lat
            longitude = p.lng
            accuracy = acc
            altitude = 12.0
            this.speed = speed
            this.bearing = bearing
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            verticalAccuracyMeters = 4f
            speedAccuracyMetersPerSecond = 0.5f
            bearingAccuracyDegrees = 15f
        }
}
