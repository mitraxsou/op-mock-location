package com.pwc.mockpresence.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanBuilderTest {
    private val a = Office("a", "Mumbai WC", 19.0760, 72.8777, 100.0, points = 1)
    private val b = Office("b", "BKC Tower", 19.0660, 72.8680, 150.0, points = 2)
    private val c = Office("c", "Andheri Hub", 19.1197, 72.8468, 100.0, points = 1)
    private val all = listOf(a, b, c)

    private fun plan(vararg stops: PlanStop, start: Int = 3, end: Int = 20) =
        DayPlan(id = "p1", name = "Test plan", stops = stops.toList(), startOutsideMinutes = start, endOutsideMinutes = end)

    private val threeOffices = plan(
        PlanStop("a", 90, 20),
        PlanStop("b", 120, 25),
        PlanStop("c", 60, 0),
    )

    private fun labels(p: DayPlan) = PlanBuilder.build(p, all)!!.first.steps.map { it.label }

    @Test
    fun threeOfficePlanStepsAndTotal() {
        val (sc, offices) = PlanBuilder.build(threeOffices, all)!!
        assertEquals(
            listOf(
                "Outside Mumbai WC",
                "Walk in to Mumbai WC",
                "At Mumbai WC (stop 1 of 3)",
                "Travel to BKC Tower",
                "At BKC Tower (stop 2 of 3)",
                "Travel to Andheri Hub",
                "At Andheri Hub (stop 3 of 3)",
                "Leave Andheri Hub",
                "Gone",
            ),
            sc.steps.map { it.label },
        )
        assertEquals(342.0, sc.totalMinutes, 0.0)
        assertEquals(342, PlanBuilder.totalMinutes(threeOffices))
        assertEquals("plan:p1", sc.id)
        assertEquals("Test plan", sc.title)
        assertEquals(1, sc.officesNeeded)
        assertEquals(listOf(a, b, c), offices)
        val travel = sc.steps.filterIsInstance<Step.Move>().filter { it.label.startsWith("Travel") }
        assertTrue(travel.all { it.accuracyM == 10f })
        val stays = sc.steps.filterIsInstance<Step.Hold>().filter { it.label.startsWith("At ") }
        assertEquals(listOf(0, 1, 2), stays.map { it.spot.office })
        assertTrue(stays.all { it.accuracyM == 6f })
        assertEquals("5 h 42 min", PlanBuilder.formatMinutes(342))
    }

    @Test
    fun threeOfficePlanExpected() {
        assertEquals("3 visits · Day points 2", PlanBuilder.expectedSummary(threeOffices, all))
        assertEquals(
            "3 visits · Day points 2\nHighest office counts, not the sum.\nTravel time is not counted.",
            PlanBuilder.expectedText(threeOffices, all),
        )
    }

    @Test
    fun sameOfficeShortBreakIsOneVisit() {
        val p = plan(PlanStop("a", 30, 10), PlanStop("a", 30, 0))
        val steps = labels(p)
        assertTrue("Step out" in steps)
        assertTrue("Back in to Mumbai WC" in steps)
        assertEquals(1, PlanBuilder.expectedVisits(p))
        val (sc, offices) = PlanBuilder.build(p, all)!!
        assertEquals(2, offices.size)
        val moves = sc.steps.filterIsInstance<Step.Move>()
        assertEquals(5.0, moves.first { it.label == "Step out" }.minutes, 0.0)
        assertEquals(5.0, moves.first { it.label == "Back in to Mumbai WC" }.minutes, 0.0)
        assertFalse(PlanBuilder.expectedText(p, all).contains("Highest office counts"))
    }

    @Test
    fun sameOfficeLongBreakIsTwoVisits() {
        val p = plan(PlanStop("a", 30, 25), PlanStop("a", 30, 0))
        assertEquals(2, PlanBuilder.expectedVisits(p))
        assertEquals("2 visits · Day points 1", PlanBuilder.expectedSummary(p, all))
        val (sc, _) = PlanBuilder.build(p, all)!!
        assertEquals(3 + 2 + 30 + 25 + 30 + 2 + 20, sc.totalMinutes.toInt())
    }

    @Test
    fun zeroTravelBetweenOfficesHasNoTravelStep() {
        val p = plan(PlanStop("a", 30, 0), PlanStop("b", 30, 0))
        val steps = labels(p)
        assertTrue(steps.none { it.startsWith("Travel") || it == "Step out" })
        assertEquals(2, PlanBuilder.expectedVisits(p))
    }

    @Test
    fun deletedOfficeBuildsNothing() {
        val p = plan(PlanStop("a", 30, 20), PlanStop("gone", 30, 0))
        assertNull(PlanBuilder.build(p, all))
        assertNull(PlanBuilder.build(threeOffices, listOf(a, b)))
    }

    @Test
    fun noStartOutsideSkipsWalkIn() {
        val p = plan(PlanStop("a", 30, 0), start = 0, end = 0)
        assertEquals(listOf("At Mumbai WC (stop 1 of 1)", "Leave Mumbai WC"), labels(p))
        assertEquals(32, PlanBuilder.totalMinutes(p))
    }

    @Test
    fun warnings() {
        val near = Office("n", "Next door", 19.0761, 72.8778, 100.0)
        val p = plan(PlanStop("a", 30, 10), PlanStop("n", 30, 0)).copy(accuracyM = 150f)
        val w = PlanBuilder.warnings(p, all + near, startMinuteOfDay = 23 * 60)
        assertTrue(w.contains("Travel to Next door is under the 15 min gap. Office Presence should still split the visits because the office changes."))
        assertTrue(w.contains("Accuracy worse than 100 m. Office Presence ignores these fixes, so no visits should appear."))
        assertTrue(w.contains("At real time this plan crosses midnight. Visits will be split into two days."))
        assertTrue(w.contains("Mumbai WC and Next door fences overlap. Travel between them may never look like Out."))
        assertTrue(PlanBuilder.warnings(threeOffices, all, startMinuteOfDay = 9 * 60).isEmpty())
    }
}
