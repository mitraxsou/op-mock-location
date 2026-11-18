package com.pwc.mockpresence.mock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.pwc.mockpresence.data.OfficeStore
import com.pwc.mockpresence.data.PlanStore

/**
 * adb control, for scripted tests. Examples:
 *   adb shell am broadcast -a com.pwc.mockpresence.CMD --es cmd inside --es office "Mumbai WC"
 *   adb shell am broadcast -a com.pwc.mockpresence.CMD --es cmd outside
 *   adb shell am broadcast -a com.pwc.mockpresence.CMD --es cmd edge --es meters -10
 *   adb shell am broadcast -a com.pwc.mockpresence.CMD --es cmd at --es lat 19.07 --es lng 72.87 --es accuracy 150
 *   adb shell am broadcast -a com.pwc.mockpresence.CMD --es cmd scenario --es id long_break --es speed x60
 *   adb shell am broadcast -a com.pwc.mockpresence.CMD --es cmd plan --es name "Mumbai, BKC, Andheri" --es speed x60
 *   adb shell am broadcast -a com.pwc.mockpresence.CMD --es cmd stop
 * Android blocks starting the service from the background, so start any mock once from the app first.
 */
class CommandReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        val cmd = intent.getStringExtra("cmd")?.trim()?.lowercase()
        if (cmd == null) {
            MockState.log("adb: missing --es cmd")
            return
        }
        val store = OfficeStore(ctx)
        intent.getStringExtra("office")?.let { name ->
            val o = store.findByName(name)
            if (o != null) store.selectedId = o.id else MockState.log("adb: no office named '$name'")
        }

        val svc: Intent = when (cmd) {
            "inside" -> spot(ctx, "centre", num(intent, "meters") ?: 0.0)
            "outside" -> spot(ctx, "edge", num(intent, "meters") ?: 400.0)
            "edge" -> spot(ctx, "edge", num(intent, "meters") ?: 0.0)
            "at" -> {
                val lat = num(intent, "lat")
                val lng = num(intent, "lng")
                if (lat == null || lng == null) {
                    MockState.log("adb: 'at' needs lat and lng")
                    return
                }
                MockService.intent(ctx, MockService.ACTION_STATIC)
                    .putExtra(MockService.EXTRA_LAT, lat)
                    .putExtra(MockService.EXTRA_LNG, lng)
                    .putExtra(MockService.EXTRA_LABEL, "adb point")
            }
            "scenario" -> {
                val id = intent.getStringExtra("id")
                val b = intent.getStringExtra("officeB")?.let { store.findByName(it)?.id }
                MockService.intent(ctx, MockService.ACTION_SCENARIO)
                    .putExtra(MockService.EXTRA_SCENARIO, id)
                    .putExtra(MockService.EXTRA_OFFICE_A, store.selectedId)
                    .putExtra(MockService.EXTRA_OFFICE_B, b)
                    .putExtra(MockService.EXTRA_SCALE, scale(intent))
            }
            "plan" -> {
                val name = intent.getStringExtra("name").orEmpty()
                val plan = PlanStore(ctx).findByName(name)
                if (plan == null) {
                    MockState.log("adb: no plan named '$name'")
                    return
                }
                MockService.intent(ctx, MockService.ACTION_PLAN)
                    .putExtra(MockService.EXTRA_PLAN_ID, plan.id)
                    .putExtra(MockService.EXTRA_SCALE, scale(intent))
            }
            "stop" -> {
                if (!MockService.isRunning) return
                MockService.intent(ctx, MockService.ACTION_STOP)
            }
            else -> {
                MockState.log("adb: unknown cmd '$cmd'")
                return
            }
        }
        num(intent, "accuracy")?.let { svc.putExtra(MockService.EXTRA_ACCURACY, it.toFloat()) }
        num(intent, "jitter")?.let { svc.putExtra(MockService.EXTRA_JITTER, it) }
        num(intent, "interval")?.let { svc.putExtra(MockService.EXTRA_INTERVAL, it.toLong()) }

        try {
            ContextCompat.startForegroundService(ctx, svc)
            MockState.log("adb: $cmd")
        } catch (e: Exception) {
            MockState.log("adb: Android blocked the background start. Start any mock once from the app, then retry.")
        }
    }

    private fun spot(ctx: Context, kind: String, meters: Double): Intent =
        MockService.intent(ctx, MockService.ACTION_SPOT)
            .putExtra(MockService.EXTRA_KIND, kind)
            .putExtra(MockService.EXTRA_METERS, meters)

    /** Seconds per scenario minute from --es speed real, x4, x60 or a number. */
    private fun scale(i: Intent): Double = when (val s = i.getStringExtra("speed")?.lowercase()) {
        null, "real" -> 60.0
        "x4" -> 15.0
        "x60" -> 1.0
        else -> s.toDoubleOrNull() ?: 60.0
    }

    /** Accepts --es, --ei, --ef or --ed values. */
    @Suppress("DEPRECATION")
    private fun num(i: Intent, key: String): Double? = i.extras?.get(key)?.toString()?.toDoubleOrNull()
}
