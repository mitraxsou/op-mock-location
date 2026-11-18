package com.pwc.mockpresence.data

import android.content.Context
import com.pwc.mockpresence.model.DayPlan
import com.pwc.mockpresence.model.PlanStop
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class PlanStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("mock_presence", Context.MODE_PRIVATE)

    /** Newest updatedAt first. */
    fun load(): List<DayPlan> = try {
        val arr = JSONArray(prefs.getString(KEY_PLANS, "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val stops = o.getJSONArray("stops")
            DayPlan(
                id = o.getString("id"),
                name = o.getString("name"),
                stops = (0 until stops.length()).map { j ->
                    val s = stops.getJSONObject(j)
                    PlanStop(
                        officeId = s.getString("officeId"),
                        stayMinutes = s.getInt("stayMinutes"),
                        travelToNextMinutes = s.optInt("travelToNextMinutes", 0),
                    )
                },
                startOutsideMinutes = o.optInt("startOutsideMinutes", 3),
                endOutsideMinutes = o.optInt("endOutsideMinutes", 20),
                accuracyM = o.optDouble("accuracyM", 6.0).toFloat(),
                updatedAt = o.optLong("updatedAt", 0L),
            )
        }.sortedByDescending { it.updatedAt }
    } catch (e: Exception) {
        emptyList()
    }

    /** Inserts or replaces by id. */
    fun save(plan: DayPlan) {
        write(load().filter { it.id != plan.id } + plan)
    }

    fun delete(id: String) {
        write(load().filter { it.id != id })
    }

    fun get(id: String?): DayPlan? = load().firstOrNull { it.id == id }

    fun findByName(name: String): DayPlan? =
        load().firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }

    private fun write(list: List<DayPlan>) {
        val arr = JSONArray()
        list.sortedByDescending { it.updatedAt }.forEach { p ->
            val stops = JSONArray()
            p.stops.forEach {
                stops.put(
                    JSONObject()
                        .put("officeId", it.officeId)
                        .put("stayMinutes", it.stayMinutes)
                        .put("travelToNextMinutes", it.travelToNextMinutes)
                )
            }
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("stops", stops)
                    .put("startOutsideMinutes", p.startOutsideMinutes)
                    .put("endOutsideMinutes", p.endOutsideMinutes)
                    .put("accuracyM", p.accuracyM.toDouble())
                    .put("updatedAt", p.updatedAt)
            )
        }
        prefs.edit().putString(KEY_PLANS, arr.toString()).apply()
    }

    companion object {
        private const val KEY_PLANS = "plans"

        fun newId(): String = UUID.randomUUID().toString()
    }
}
