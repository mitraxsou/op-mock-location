package com.pwc.mockpresence.model

data class PlanStop(
    val officeId: String,
    val stayMinutes: Int,          // 1..600
    val travelToNextMinutes: Int,  // 0..240, ignored on the last stop
)

/** An ordered list of office stops, run through the same engine as the built-in scenarios. */
data class DayPlan(
    val id: String,
    val name: String,
    val stops: List<PlanStop>,     // 1..12
    val startOutsideMinutes: Int = 3,   // 0..60
    val endOutsideMinutes: Int = 20,    // 0..60
    val accuracyM: Float = 6f,
    val updatedAt: Long = System.currentTimeMillis(),
)
