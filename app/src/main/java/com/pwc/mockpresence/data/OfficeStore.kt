package com.pwc.mockpresence.data

import android.content.Context
import com.pwc.mockpresence.model.Office
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class OfficeStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("mock_presence", Context.MODE_PRIVATE)

    fun load(): List<Office> = try {
        val arr = JSONArray(prefs.getString(KEY_OFFICES, "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Office(
                id = o.getString("id"),
                name = o.getString("name"),
                lat = o.getDouble("lat"),
                lng = o.getDouble("lng"),
                radiusM = o.getDouble("radiusM"),
                points = o.optInt("points", 1),
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun save(list: List<Office>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("lat", it.lat)
                    .put("lng", it.lng)
                    .put("radiusM", it.radiusM)
                    .put("points", it.points)
            )
        }
        prefs.edit().putString(KEY_OFFICES, arr.toString()).apply()
    }

    var selectedId: String?
        get() = prefs.getString(KEY_SELECTED, null)
        set(value) = prefs.edit().putString(KEY_SELECTED, value).apply()

    fun selected(): Office? {
        val all = load()
        return all.firstOrNull { it.id == selectedId } ?: all.firstOrNull()
    }

    fun findByName(name: String): Office? =
        load().firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }

    companion object {
        private const val KEY_OFFICES = "offices"
        private const val KEY_SELECTED = "selected_office"

        fun newId(): String = UUID.randomUUID().toString()

        /**
         * Same format as the Office Presence admin bulk import:
         * name,latitude,longitude,radiusMeters,pointsPerVisitDay[,addressLabel]
         * A header row is skipped automatically.
         */
        fun parseCsv(text: String): Pair<List<Office>, List<String>> {
            val out = mutableListOf<Office>()
            val errors = mutableListOf<String>()
            text.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEachIndexed { n, line ->
                val c = splitCsv(line)
                if (n == 0 && c.getOrNull(1)?.toDoubleOrNull() == null) return@forEachIndexed
                val name = c.getOrNull(0)?.trim().orEmpty()
                val lat = c.getOrNull(1)?.toDoubleOrNull()
                val lng = c.getOrNull(2)?.toDoubleOrNull()
                val radius = c.getOrNull(3)?.toDoubleOrNull() ?: 100.0
                val points = c.getOrNull(4)?.toIntOrNull() ?: 1
                if (name.isEmpty() || lat == null || lng == null ||
                    lat !in -90.0..90.0 || lng !in -180.0..180.0
                ) {
                    errors += "line ${n + 1}"
                } else {
                    out += Office(newId(), name, lat, lng, radius.coerceIn(20.0, 5000.0), points)
                }
            }
            return out to errors
        }

        private fun splitCsv(line: String): List<String> {
            val result = mutableListOf<String>()
            val cur = StringBuilder()
            var quoted = false
            var i = 0
            while (i < line.length) {
                val ch = line[i]
                when {
                    ch == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> {
                        cur.append('"'); i++
                    }
                    ch == '"' -> quoted = !quoted
                    ch == ',' && !quoted -> {
                        result += cur.toString(); cur.clear()
                    }
                    else -> cur.append(ch)
                }
                i++
            }
            result += cur.toString()
            return result.map { it.trim() }
        }
    }
}
