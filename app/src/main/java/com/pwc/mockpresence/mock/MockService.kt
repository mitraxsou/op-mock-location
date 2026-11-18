package com.pwc.mockpresence.mock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.pwc.mockpresence.data.OfficeStore
import com.pwc.mockpresence.data.PlanStore
import com.pwc.mockpresence.model.LatLng
import com.pwc.mockpresence.model.Office
import com.pwc.mockpresence.model.PlanBuilder
import com.pwc.mockpresence.model.Scenario
import com.pwc.mockpresence.model.Scenarios
import com.pwc.mockpresence.model.Spot
import com.pwc.mockpresence.model.SpotKind
import com.pwc.mockpresence.model.Step
import com.pwc.mockpresence.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Foreground service that keeps sending fake fixes while the app is in the background.
 * One "program" runs at a time: a fixed position or a scenario.
 */
class MockService : Service() {

    companion object {
        const val ACTION_STATIC = "com.pwc.mockpresence.action.STATIC"
        const val ACTION_SPOT = "com.pwc.mockpresence.action.SPOT"
        const val ACTION_SCENARIO = "com.pwc.mockpresence.action.SCENARIO"
        const val ACTION_PLAN = "com.pwc.mockpresence.action.PLAN"
        const val ACTION_STOP = "com.pwc.mockpresence.action.STOP"

        const val EXTRA_LAT = "lat"
        const val EXTRA_LNG = "lng"
        const val EXTRA_ACCURACY = "accuracy"
        const val EXTRA_JITTER = "jitter"
        const val EXTRA_LABEL = "label"
        const val EXTRA_INTERVAL = "intervalMs"
        const val EXTRA_KIND = "kind"          // "centre" or "edge"
        const val EXTRA_METERS = "meters"
        const val EXTRA_SCENARIO = "scenario"
        const val EXTRA_OFFICE_A = "officeA"
        const val EXTRA_OFFICE_B = "officeB"
        const val EXTRA_SCALE = "secondsPerMinute"
        const val EXTRA_PLAN_ID = "planId"

        private const val CHANNEL = "mock_location"
        private const val NOTIF_ID = 42

        @Volatile
        var isRunning = false
            private set

        fun intent(ctx: Context, action: String): Intent =
            Intent(ctx, MockService::class.java).setAction(action)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private lateinit var engine: MockLocationEngine
    private lateinit var store: OfficeStore
    private var engineOn = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var fixes = 0L
    private var lastPushError: String? = null
    private var intervalMs = 1000L
    private var knownOffices: List<Office> = emptyList()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        engine = MockLocationEngine(this)
        store = OfficeStore(this)
        val channel = NotificationChannel(CHANNEL, "Mock location", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!goForeground("Starting")) {
            stopSelf()
            return START_NOT_STICKY
        }
        isRunning = true
        val i = intent
        if (i == null || i.action == null || i.action == ACTION_STOP) {
            shutdown("Stopped. Real GPS is back.", isError = false)
            return START_NOT_STICKY
        }
        if (!ensureEngine()) return START_NOT_STICKY

        knownOffices = store.load()
        intervalMs = i.getLongExtra(EXTRA_INTERVAL, intervalMs).coerceIn(250L, 60_000L)
        when (i.action) {
            ACTION_STATIC -> runStatic(
                LatLng(i.getDoubleExtra(EXTRA_LAT, 0.0), i.getDoubleExtra(EXTRA_LNG, 0.0)),
                i.getFloatExtra(EXTRA_ACCURACY, 6f),
                i.getDoubleExtra(EXTRA_JITTER, 3.0),
                i.getStringExtra(EXTRA_LABEL) ?: "Custom point",
            )
            ACTION_SPOT -> runSpot(i)
            ACTION_SCENARIO -> runScenario(i)
            ACTION_PLAN -> runPlan(i)
        }
        return START_NOT_STICKY
    }

    // ---------- programs ----------

    private fun runSpot(i: Intent) {
        val office = store.selected()
        if (office == null) {
            MockState.log("No office saved. Add one in the Offices tab.")
            return
        }
        val kind = if (i.getStringExtra(EXTRA_KIND) == "edge") SpotKind.FROM_EDGE else SpotKind.CENTRE
        val meters = i.getDoubleExtra(EXTRA_METERS, 0.0)
        val p = Geo.resolve(Spot(0, kind, meters), listOf(office))
        val where = if (kind == SpotKind.CENTRE) "${meters.toInt()} m from centre" else "edge ${signed(meters)} m"
        val label = (i.getStringExtra(EXTRA_LABEL) ?: where) + " · " + office.name
        runStatic(p, i.getFloatExtra(EXTRA_ACCURACY, 6f), i.getDoubleExtra(EXTRA_JITTER, 3.0), label)
    }

    private fun runStatic(point: LatLng, accuracyM: Float, jitterM: Double, label: String) {
        job?.cancel()
        MockState.log("Hold: $label (${fmt(point)}, ±${accuracyM.toInt()} m, every ${intervalMs} ms)")
        MockState.status.update { it.copy(stepCount = 0, stepLabel = "") }
        notifyText(label)
        job = scope.launch {
            val rnd = Random(System.nanoTime())
            while (isActive) {
                val p = if (jitterM > 0) Geo.jitter(point, jitterM, rnd) else point
                emit(p, accuracyM, "Fixed position", label, 0f, 0f)
                delay(intervalMs)
            }
        }
    }

    private fun runScenario(i: Intent) {
        val sc = Scenarios.byId(i.getStringExtra(EXTRA_SCENARIO))
        if (sc == null) {
            MockState.log("Unknown scenario '${i.getStringExtra(EXTRA_SCENARIO)}'.")
            return
        }
        val all = knownOffices
        val a = all.firstOrNull { it.id == i.getStringExtra(EXTRA_OFFICE_A) } ?: store.selected()
        if (a == null) {
            MockState.log("No office saved. Add one in the Offices tab.")
            return
        }
        val b = all.firstOrNull { it.id == i.getStringExtra(EXTRA_OFFICE_B) && it.id != a.id }
            ?: all.firstOrNull { it.id != a.id }
        val offices = if (b != null) listOf(a, b) else listOf(a)
        if (sc.officesNeeded > offices.size) {
            MockState.log("'${sc.title}' needs two different offices.")
            return
        }
        val secPerMin = i.getDoubleExtra(EXTRA_SCALE, 60.0).coerceIn(0.5, 60.0)

        MockState.log(
            "Scenario '${sc.title}' at ${offices.joinToString(" and ") { it.name }}, " +
                "1 scenario minute = ${fmtNum(secPerMin)} s"
        )
        runSteps(sc, offices, secPerMin, mode = "Scenario")
    }

    private fun runPlan(i: Intent) {
        val id = i.getStringExtra(EXTRA_PLAN_ID)
        val plan = PlanStore(this).get(id)
        if (plan == null) {
            MockState.log("Unknown plan '$id'.")
            return
        }
        val built = PlanBuilder.build(plan, knownOffices)
        if (built == null) {
            MockState.log("Plan has a missing office")
            return
        }
        val (sc, offices) = built
        val secPerMin = i.getDoubleExtra(EXTRA_SCALE, 60.0).coerceIn(0.5, 60.0)
        MockState.log("Plan '${plan.name}' with ${plan.stops.size} stops")
        runSteps(sc, offices, secPerMin, mode = "Day plan")
    }

    /** One loop for built-in scenarios and day plans. offices is indexed by Spot.office. */
    private fun runSteps(sc: Scenario, offices: List<Office>, secPerMin: Double, mode: String) {
        job?.cancel()
        job = scope.launch {
            val rnd = Random(System.nanoTime())
            var last = Geo.resolve(Spot(0, SpotKind.CENTRE, 0.0), offices)
            var lastAcc = 6f
            for ((idx, step) in sc.steps.withIndex()) {
                val totalMs = (step.minutes * secPerMin * 1000).toLong().coerceAtLeast(intervalMs)
                MockState.log("Step ${idx + 1}/${sc.steps.size}: ${step.label} (${fmtNum(step.minutes)} min)")
                notifyText("${sc.title}: ${step.label}")
                val start = SystemClock.elapsedRealtime()
                while (isActive) {
                    val elapsed = SystemClock.elapsedRealtime() - start
                    if (elapsed >= totalMs) break
                    val f = elapsed.toDouble() / totalMs
                    when (step) {
                        is Step.Hold -> {
                            val wobble = if (step.wobbleM > 0) {
                                step.wobbleM * sin(2 * PI * elapsed / (3 * secPerMin * 1000))
                            } else 0.0
                            val base = Geo.resolve(step.spot.copy(meters = step.spot.meters + wobble), offices)
                            last = if (step.jitterM > 0) Geo.jitter(base, step.jitterM, rnd) else base
                            lastAcc = step.accuracyM
                            emit(last, lastAcc, mode, sc.title, 0f, 0f)
                        }
                        is Step.Move -> {
                            val from = Geo.resolve(step.from, offices)
                            val to = Geo.resolve(step.to, offices)
                            last = Geo.lerp(from, to, f)
                            lastAcc = step.accuracyM
                            val speed = (Geo.distance(from, to) / (totalMs / 1000.0)).toFloat()
                            emit(last, lastAcc, mode, sc.title, speed, Geo.bearing(from, to).toFloat())
                        }
                        is Step.Silence -> MockState.status.update {
                            it.copy(running = true, mode = mode, label = sc.title, emitting = false)
                        }
                    }
                    MockState.status.update {
                        it.copy(
                            stepIndex = idx,
                            stepCount = sc.steps.size,
                            stepLabel = step.label,
                            stepRemainingSec = (totalMs - elapsed) / 1000,
                        )
                    }
                    delay(intervalMs)
                }
            }
            MockState.log("$mode finished. Holding the last position until you stop.")
            notifyText("${sc.title}: finished, holding position")
            MockState.status.update { it.copy(stepCount = 0, stepLabel = "") }
            while (isActive) {
                emit(last, lastAcc, "$mode finished", sc.title, 0f, 0f)
                delay(intervalMs)
            }
        }
    }

    // ---------- plumbing ----------

    private fun emit(p: LatLng, acc: Float, mode: String, label: String, speed: Float, bearing: Float) {
        val err = engine.push(p, acc, speed, bearing)
        if (err != null && err != lastPushError) MockState.log("Push failed: $err")
        lastPushError = err
        fixes++
        val nearest = knownOffices.minByOrNull { Geo.distance(p, it.centre) }
        val d = nearest?.let { Geo.distance(p, it.centre) }
        MockState.status.update {
            it.copy(
                running = true,
                mode = mode,
                label = label,
                point = p,
                accuracyM = acc,
                emitting = true,
                nearest = nearest?.name,
                nearestDistanceM = d,
                insideNearest = nearest != null && d != null && d <= nearest.radiusM,
                fixes = fixes,
                error = null,
            )
        }
    }

    private fun ensureEngine(): Boolean {
        if (engineOn) return true
        val err = engine.start()
        if (err != null) {
            shutdown(err, isError = true)
            return false
        }
        engineOn = true
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OPMock:emit").apply {
            setReferenceCounted(false)
            acquire(12 * 60 * 60 * 1000L)
        }
        MockState.log("Mock providers on (fused, gps, network).")
        return true
    }

    private fun shutdown(message: String?, isError: Boolean) {
        job?.cancel()
        job = null
        if (engineOn) {
            engine.stop()
            engineOn = false
        }
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        message?.let { MockState.log(it) }
        MockState.status.value = MockState.Status(error = if (isError) message else null)
        isRunning = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        job?.cancel()
        if (engineOn) engine.stop()
        engineOn = false
        wakeLock?.let { if (it.isHeld) it.release() }
        scope.cancel()
        isRunning = false
        if (MockState.status.value.running) MockState.status.value = MockState.Status()
        super.onDestroy()
    }

    private fun goForeground(text: String): Boolean = try {
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            buildNotification(text),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
        )
        true
    } catch (e: Exception) {
        MockState.log("Could not start the background service: ${e.message}. Grant location permission.")
        false
    }

    private fun notifyText(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            flags,
        )
        fun spot(code: Int, kind: String, meters: Double, label: String) = PendingIntent.getService(
            this, code,
            intent(this, ACTION_SPOT)
                .putExtra(EXTRA_KIND, kind)
                .putExtra(EXTRA_METERS, meters)
                .putExtra(EXTRA_LABEL, label),
            flags,
        )
        val stop = PendingIntent.getService(this, 9, intent(this, ACTION_STOP), flags)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Mock location on")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .addAction(0, "Inside", spot(1, "centre", 0.0, "Inside"))
            .addAction(0, "Outside", spot(2, "edge", 400.0, "Outside"))
            .addAction(0, "Stop", stop)
            .build()
    }

    private fun fmt(p: LatLng) = String.format(Locale.US, "%.6f, %.6f", p.lat, p.lng)
    private fun fmtNum(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.US, "%.1f", v)
    private fun signed(v: Double) = (if (v >= 0) "+" else "") + v.toInt()
}
