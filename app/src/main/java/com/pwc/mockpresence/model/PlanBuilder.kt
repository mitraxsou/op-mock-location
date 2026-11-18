package com.pwc.mockpresence.model

import com.pwc.mockpresence.mock.Geo
import com.pwc.mockpresence.model.Spots.desk
import com.pwc.mockpresence.model.Spots.outside

/**
 * Turns a day plan into scenario steps for MockService, and predicts what Office Presence should show.
 * Expected results assume the default rules: gap 15 min, points = highest office of the day.
 * Pure Kotlin, no Android imports, so it can be unit tested.
 */
object PlanBuilder {
    const val GAP_MINUTES = 15
    const val MAX_STOPS = 12
    private const val WALK_MINUTES = 2
    private const val TRAVEL_ACCURACY_M = 10f
    private const val SAME_OFFICE_STEP_OUT_M = 300.0
    private const val MAX_ACCURACY_M = 100f

    /** The plan's offices in stop order, one entry per stop, or null if any office is missing. */
    fun offices(plan: DayPlan, all: List<Office>): List<Office>? {
        if (plan.stops.isEmpty()) return null
        return plan.stops.map { s -> all.firstOrNull { it.id == s.officeId } ?: return null }
    }

    fun build(plan: DayPlan, all: List<Office>): Pair<Scenario, List<Office>>? {
        val offices = offices(plan, all) ?: return null
        val n = plan.stops.size
        val last = n - 1
        val start = plan.startOutsideMinutes
        val steps = mutableListOf<Step>()

        if (start > 0) steps += Step.Hold("Outside ${offices[0].name}", start.toDouble(), outside(0))
        plan.stops.forEachIndexed { i, stop ->
            val office = offices[i]
            if (i == 0 && start > 0) {
                steps += Step.Move("Walk in to ${office.name}", WALK_MINUTES.toDouble(), outside(0), desk(0))
            }
            steps += Step.Hold(
                "At ${office.name} (stop ${i + 1} of $n)",
                stop.stayMinutes.toDouble(),
                desk(i),
                accuracyM = plan.accuracyM,
            )
            val travel = stop.travelToNextMinutes
            if (i < last && travel > 0) {
                val next = offices[i + 1]
                if (next.id != office.id) {
                    steps += Step.Move(
                        "Travel to ${next.name}", travel.toDouble(), desk(i), desk(i + 1),
                        accuracyM = TRAVEL_ACCURACY_M,
                    )
                } else {
                    val out = outside(i, SAME_OFFICE_STEP_OUT_M)
                    steps += Step.Move("Step out", (travel / 2).toDouble(), desk(i), out)
                    steps += Step.Move("Back in to ${office.name}", (travel - travel / 2).toDouble(), out, desk(i + 1))
                }
            }
        }
        steps += Step.Move("Leave ${offices[last].name}", WALK_MINUTES.toDouble(), desk(last), outside(last))
        if (plan.endOutsideMinutes > 0) {
            steps += Step.Hold("Gone", plan.endOutsideMinutes.toDouble(), outside(last))
        }

        val scenario = Scenario(
            id = "plan:" + plan.id,
            title = plan.name,
            description = plan.stops.mapIndexed { i, s -> "${offices[i].name} ${s.stayMinutes}" }.joinToString(" → "),
            expected = expectedText(plan, all),
            officesNeeded = 1,
            steps = steps,
        )
        return scenario to offices
    }

    /** Same formula as the built steps. Use the built Scenario.totalMinutes when the plan can be built. */
    fun totalMinutes(plan: DayPlan): Int {
        val start = plan.startOutsideMinutes
        val travel = plan.stops.dropLast(1).sumOf { it.travelToNextMinutes }
        return start + (if (start > 0) WALK_MINUTES else 0) + plan.stops.sumOf { it.stayMinutes } +
            travel + WALK_MINUTES + plan.endOutsideMinutes
    }

    fun expectedVisits(plan: DayPlan): Int {
        var visits = plan.stops.size
        plan.stops.zipWithNext().forEach { (a, b) ->
            if (a.officeId == b.officeId && a.travelToNextMinutes < GAP_MINUTES) visits--
        }
        return visits
    }

    fun dayPoints(plan: DayPlan, all: List<Office>): Int =
        plan.stops.mapNotNull { s -> all.firstOrNull { it.id == s.officeId }?.points }.maxOrNull() ?: 0

    /** First line of the expected result, for example "3 visits · Day points 2". */
    fun expectedSummary(plan: DayPlan, all: List<Office>): String {
        val v = expectedVisits(plan)
        return "$v ${if (v == 1) "visit" else "visits"} · Day points ${dayPoints(plan, all)}"
    }

    fun expectedLines(plan: DayPlan, all: List<Office>): List<String> {
        val lines = mutableListOf(expectedSummary(plan, all))
        if (plan.stops.map { it.officeId }.distinct().size >= 2) lines += "Highest office counts, not the sum."
        lines += "Travel time is not counted."
        return lines
    }

    fun expectedText(plan: DayPlan, all: List<Office>): String = expectedLines(plan, all).joinToString("\n")

    /**
     * Warnings shown under Expected. They do not block saving.
     * startMinuteOfDay is the phone clock (minutes since midnight) if the plan started now at real time.
     */
    fun warnings(plan: DayPlan, all: List<Office>, startMinuteOfDay: Int): List<String> {
        val out = mutableListOf<String>()
        val byId = all.associateBy { it.id }
        plan.stops.zipWithNext().forEach { (a, b) ->
            val next = byId[b.officeId] ?: return@forEach
            if (a.officeId != b.officeId && a.travelToNextMinutes < GAP_MINUTES) {
                out += "Travel to ${next.name} is under the 15 min gap. Office Presence should still split the visits because the office changes."
            }
        }
        if (plan.accuracyM > MAX_ACCURACY_M) {
            out += "Accuracy worse than 100 m. Office Presence ignores these fixes, so no visits should appear."
        }
        val total = build(plan, all)?.first?.totalMinutes?.toInt() ?: totalMinutes(plan)
        if (startMinuteOfDay + total > 23 * 60 + 59) {
            out += "At real time this plan crosses midnight. Visits will be split into two days."
        }
        val seen = mutableSetOf<Set<String>>()
        plan.stops.zipWithNext().forEach { (sa, sb) ->
            val a = byId[sa.officeId] ?: return@forEach
            val b = byId[sb.officeId] ?: return@forEach
            if (a.id == b.id || !seen.add(setOf(a.id, b.id))) return@forEach
            if (Geo.distance(a.centre, b.centre) < a.radiusM + b.radiusM) {
                out += "${a.name} and ${b.name} fences overlap. Travel between them may never look like Out."
            }
        }
        return out.distinct()
    }

    /** "5 h 42 min", "2 h" or "45 min". */
    fun formatMinutes(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "$m min"
            m == 0 -> "$h h"
            else -> "$h h $m min"
        }
    }
}
