package com.pwc.mockpresence.mock

import android.util.Log
import com.pwc.mockpresence.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Shared state between the service and the UI. */
object MockState {
    data class Status(
        val running: Boolean = false,
        val mode: String = "",
        val label: String = "",
        val point: LatLng? = null,
        val accuracyM: Float = 0f,
        val emitting: Boolean = false,
        val stepIndex: Int = 0,
        val stepCount: Int = 0,
        val stepLabel: String = "",
        val stepRemainingSec: Long = 0,
        val nearest: String? = null,
        val nearestDistanceM: Double? = null,
        val insideNearest: Boolean = false,
        val fixes: Long = 0,
        val error: String? = null,
    )

    val status = MutableStateFlow(Status())
    val log = MutableStateFlow<List<String>>(emptyList())

    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun log(message: String) {
        Log.i("OPMock", message)
        val line = "${LocalTime.now().format(timeFmt)}  $message"
        log.update { (listOf(line) + it).take(400) }
    }

    fun clearLog() {
        log.value = emptyList()
    }
}
