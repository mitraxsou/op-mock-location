package com.pwc.mockpresence.model

import com.pwc.mockpresence.model.Spots.centre
import com.pwc.mockpresence.model.Spots.desk
import com.pwc.mockpresence.model.Spots.edge
import com.pwc.mockpresence.model.Spots.outside

/**
 * Built-in test scenarios. Expected results assume the default Office Presence rules:
 * gap 15 min, sample every 5 min, fence radius (max accepted accuracy) 100 m.
 */
object Scenarios {
    val all: List<Scenario> = listOf(
        Scenario(
            id = "arrive_stay",
            title = "Arrive, stay, leave",
            description = "Starts 400 m outside the fence, walks in, stays 60 minutes, walks out.",
            expected = "Arrived notification and In on Today. One visit of about an hour. The day counts with this office's points. Left notification after the exit, then Out.",
            officesNeeded = 1,
            steps = listOf(
                Step.Hold("Outside", 3.0, outside()),
                Step.Move("Walk in", 4.0, outside(), desk()),
                Step.Hold("At desk", 60.0, desk()),
                Step.Move("Walk out", 4.0, desk(), outside()),
                Step.Hold("Gone", 20.0, outside()),
            ),
        ),
        Scenario(
            id = "short_break",
            title = "Short break (under the gap)",
            description = "Inside 30 minutes, steps out for 10 minutes, back for 30 minutes.",
            expected = "Still one visit. A 10 minute break is shorter than the 15 minute gap.",
            officesNeeded = 1,
            steps = listOf(
                Step.Hold("Inside", 30.0, desk()),
                Step.Move("Step out", 1.0, desk(), outside(m = 150.0)),
                Step.Hold("Coffee outside", 8.0, outside(m = 150.0)),
                Step.Move("Back in", 1.0, outside(m = 150.0), desk()),
                Step.Hold("Inside again", 30.0, desk()),
                Step.Move("Leave", 2.0, desk(), outside()),
                Step.Hold("Gone", 20.0, outside()),
            ),
        ),
        Scenario(
            id = "long_break",
            title = "Long break (over the gap)",
            description = "Inside 30 minutes, away for 25 minutes, back for 30 minutes.",
            expected = "Two visits on the same day. The day still counts once, with the same points.",
            officesNeeded = 1,
            steps = listOf(
                Step.Hold("Inside", 30.0, desk()),
                Step.Move("Step out", 1.0, desk(), outside(m = 300.0)),
                Step.Hold("Lunch outside", 23.0, outside(m = 300.0)),
                Step.Move("Back in", 1.0, outside(m = 300.0), desk()),
                Step.Hold("Inside again", 30.0, desk()),
                Step.Move("Leave", 2.0, desk(), outside()),
                Step.Hold("Gone", 20.0, outside()),
            ),
        ),
        Scenario(
            id = "two_offices",
            title = "Two offices in one day",
            description = "45 minutes at office A, 15 minutes travel, 45 minutes at office B.",
            expected = "Two visits, one per office. Travel time is not counted. The day earns the higher points of the two offices, not the sum.",
            officesNeeded = 2,
            steps = listOf(
                Step.Hold("At office A", 45.0, centre(0)),
                Step.Move("Travel A to B", 15.0, centre(0), centre(1), accuracyM = 10f),
                Step.Hold("At office B", 45.0, centre(1)),
                Step.Move("Leave B", 3.0, centre(1), outside(1)),
                Step.Hold("Gone", 20.0, outside(1)),
            ),
        ),
        Scenario(
            id = "poor_accuracy",
            title = "Inside with weak GPS",
            description = "Inside the fence with 150 m accuracy for 20 minutes, then a good fix for 20 minutes.",
            expected = "While accuracy is 150 m the app shows Looking for the office and no visit starts, because fixes worse than the fence radius (100 m) are ignored. The visit starts once accuracy improves.",
            officesNeeded = 1,
            steps = listOf(
                Step.Hold("Outside", 3.0, outside()),
                Step.Move("Walk in", 3.0, outside(), desk(), accuracyM = 150f),
                Step.Hold("Inside, weak GPS", 20.0, desk(), accuracyM = 150f, jitterM = 30.0),
                Step.Hold("Inside, GPS recovers", 20.0, desk()),
                Step.Move("Leave", 3.0, desk(), outside()),
                Step.Hold("Gone", 20.0, outside()),
            ),
        ),
        Scenario(
            id = "coarse_exit",
            title = "Leave with coarse GPS",
            description = "Inside 20 minutes, then walks out with 40 m accuracy and stays outside.",
            expected = "The phone ignores a geofence exit worse than 15 m, so there may be no Left notification straight away. The server still closes the visit after 15 minutes without inside evidence.",
            officesNeeded = 1,
            steps = listOf(
                Step.Hold("Inside", 20.0, desk()),
                Step.Move("Leave, coarse fix", 2.0, desk(), outside(), accuracyM = 40f),
                Step.Hold("Outside, coarse fix", 25.0, outside(), accuracyM = 40f, jitterM = 15.0),
            ),
        ),
        Scenario(
            id = "edge_flap",
            title = "Standing at the fence edge",
            description = "Hovers on the fence line, moving 25 m in and out, for 30 minutes.",
            expected = "Geofence enter and exit may fire several times. The result should still be one continuous visit, not many short ones.",
            officesNeeded = 1,
            steps = listOf(
                Step.Hold("On the edge", 30.0, edge(), wobbleM = 25.0),
                Step.Move("Leave", 2.0, edge(), outside()),
                Step.Hold("Gone", 20.0, outside()),
            ),
        ),
        Scenario(
            id = "gps_silent",
            title = "GPS goes quiet inside",
            description = "Inside 20 minutes, then no fixes at all for 30 minutes, then a fix outside.",
            expected = "The visit should end about 15 minutes after the last inside fix, not when GPS comes back. Some location calls may still return the last fake fix from cache, so note what you see.",
            officesNeeded = 1,
            steps = listOf(
                Step.Hold("Inside", 20.0, desk()),
                Step.Silence("No fixes", 30.0),
                Step.Hold("Outside", 10.0, outside()),
            ),
        ),
        Scenario(
            id = "long_stay",
            title = "Long stay (midnight test)",
            description = "Stays inside for 4 hours. Start it around 23:00 on the phone clock, in real time only.",
            expected = "The phone writes a check out and check in at local midnight. Month shows two days, each counted separately.",
            officesNeeded = 1,
            steps = listOf(
                Step.Hold("Inside", 240.0, desk()),
            ),
        ),
    )

    fun byId(id: String?): Scenario? = all.firstOrNull { it.id == id }
}
