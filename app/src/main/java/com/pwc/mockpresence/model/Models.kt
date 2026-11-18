package com.pwc.mockpresence.model

data class LatLng(val lat: Double, val lng: Double)

/** Mirror of an Office Presence office. Use the same centre and radius as Admin > Offices. */
data class Office(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val radiusM: Double,
    val points: Int = 1,
) {
    val centre: LatLng get() = LatLng(lat, lng)
}

enum class SpotKind {
    /** meters from the office centre */
    CENTRE,
    /** meters beyond the office radius (negative = inside the fence) */
    FROM_EDGE,
}

/** A point relative to an office in the scenario's office list (0 = office A, 1 = office B). */
data class Spot(
    val office: Int,
    val kind: SpotKind,
    val meters: Double,
    val bearing: Double = 90.0,
)

sealed interface Step {
    val label: String
    val minutes: Double

    /** Stay at a spot. wobbleM moves the point in and out by that many meters every 3 scenario minutes. */
    data class Hold(
        override val label: String,
        override val minutes: Double,
        val spot: Spot,
        val accuracyM: Float = 6f,
        val jitterM: Double = 3.0,
        val wobbleM: Double = 0.0,
    ) : Step

    /** Walk in a straight line from one spot to another. */
    data class Move(
        override val label: String,
        override val minutes: Double,
        val from: Spot,
        val to: Spot,
        val accuracyM: Float = 8f,
    ) : Step

    /** Send no fixes at all (GPS lost, phone in a basement). */
    data class Silence(
        override val label: String,
        override val minutes: Double,
    ) : Step
}

data class Scenario(
    val id: String,
    val title: String,
    val description: String,
    val expected: String,
    val officesNeeded: Int,
    val steps: List<Step>,
) {
    val totalMinutes: Double get() = steps.sumOf { it.minutes }
}
