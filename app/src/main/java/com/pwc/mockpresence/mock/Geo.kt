package com.pwc.mockpresence.mock

import com.pwc.mockpresence.model.LatLng
import com.pwc.mockpresence.model.Office
import com.pwc.mockpresence.model.Spot
import com.pwc.mockpresence.model.SpotKind
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

object Geo {
    private const val EARTH_R = 6_371_000.0

    /** Point at distanceM along bearingDeg from a start point. */
    fun offset(from: LatLng, distanceM: Double, bearingDeg: Double): LatLng {
        if (distanceM <= 0.0) return from
        val d = distanceM / EARTH_R
        val br = Math.toRadians(bearingDeg)
        val lat1 = Math.toRadians(from.lat)
        val lng1 = Math.toRadians(from.lng)
        val lat2 = asin(sin(lat1) * cos(d) + cos(lat1) * sin(d) * cos(br))
        val lng2 = lng1 + atan2(sin(br) * sin(d) * cos(lat1), cos(d) - sin(lat1) * sin(lat2))
        return LatLng(Math.toDegrees(lat2), (Math.toDegrees(lng2) + 540.0) % 360.0 - 180.0)
    }

    /** Haversine distance in meters. Same idea as the Office Presence inside check. */
    fun distance(a: LatLng, b: LatLng): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2)
        return 2 * EARTH_R * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    fun bearing(a: LatLng, b: LatLng): Double {
        val p1 = Math.toRadians(a.lat)
        val p2 = Math.toRadians(b.lat)
        val dl = Math.toRadians(b.lng - a.lng)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    fun lerp(a: LatLng, b: LatLng, f: Double): LatLng {
        val t = f.coerceIn(0.0, 1.0)
        return LatLng(a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t)
    }

    fun jitter(p: LatLng, maxM: Double, rnd: Random): LatLng =
        offset(p, rnd.nextDouble() * maxM, rnd.nextDouble() * 360.0)

    fun resolve(spot: Spot, offices: List<Office>): LatLng {
        val o = offices[spot.office.coerceIn(0, offices.lastIndex)]
        val d = when (spot.kind) {
            SpotKind.CENTRE -> spot.meters
            SpotKind.FROM_EDGE -> o.radiusM + spot.meters
        }
        return offset(o.centre, d.coerceAtLeast(0.0), spot.bearing)
    }

    /** Accepts "19.0760, 72.8777" or "19.0760 72.8777" (as copied from Google Maps). */
    fun parseLatLng(text: String): LatLng? {
        val parts = text.trim().split(Regex("[,\\s]+")).filter { it.isNotEmpty() }
        if (parts.size != 2) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lng = parts[1].toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
        return LatLng(lat, lng)
    }
}
