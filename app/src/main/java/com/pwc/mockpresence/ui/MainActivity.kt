@file:OptIn(ExperimentalMaterial3Api::class)

package com.pwc.mockpresence.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.pwc.mockpresence.data.OfficeStore
import com.pwc.mockpresence.mock.Geo
import com.pwc.mockpresence.mock.MockCheck
import com.pwc.mockpresence.mock.MockService
import com.pwc.mockpresence.mock.MockState
import com.pwc.mockpresence.model.LatLng
import com.pwc.mockpresence.model.Office
import com.pwc.mockpresence.model.Scenario
import com.pwc.mockpresence.model.Scenarios
import com.pwc.mockpresence.model.Spot
import com.pwc.mockpresence.model.SpotKind
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var resumeTick by mutableIntStateOf(0)

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { resumeTick++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    MockApp(resumeTick, ::askPermissions)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }

    private fun askPermissions() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
        permLauncher.launch(perms.toTypedArray())
    }
}

// ---------------- setup checks ----------------

private data class SetupChecks(val location: Boolean, val notifications: Boolean, val mockApp: Boolean) {
    val allGood get() = location && notifications && mockApp

    companion object {
        fun read(ctx: Context) = SetupChecks(
            location = granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION),
            notifications = Build.VERSION.SDK_INT < 33 || granted(ctx, Manifest.permission.POST_NOTIFICATIONS),
            mockApp = MockCheck.isMockApp(ctx),
        )

        private fun granted(ctx: Context, p: String) =
            ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    }
}

// ---------------- root ----------------

@Composable
private fun MockApp(resumeTick: Int, askPermissions: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { OfficeStore(ctx) }
    var offices by remember { mutableStateOf(store.load()) }
    var selectedId by remember { mutableStateOf(store.selected()?.id) }
    val status by MockState.status.collectAsState()
    val log by MockState.log.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(if (offices.isEmpty()) 3 else 0) }
    val checks = remember(resumeTick) { SetupChecks.read(ctx) }

    fun select(id: String) {
        selectedId = id
        store.selectedId = id
    }

    fun saveOffices(list: List<Office>) {
        store.save(list)
        offices = list
        if (list.none { it.id == selectedId }) {
            selectedId = list.firstOrNull()?.id
            store.selectedId = selectedId
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Office Presence mock location", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            "Use with a test account only. Fake fixes count as real presence today.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!checks.allGood) SetupCard(checks, askPermissions)
        StatusCard(status)
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
            listOf("Control", "Scenarios", "Day plan", "Offices", "Log").forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
            }
        }
        when (tab) {
            0 -> ControlTab(offices, selectedId, ::select)
            1 -> ScenariosTab(offices, selectedId)
            2 -> DayPlanTab(offices, selectedId)
            3 -> OfficesTab(offices, selectedId, ::select, ::saveOffices)
            else -> LogTab(log)
        }
    }
}

@Composable
private fun SetupCard(c: SetupChecks, askPermissions: () -> Unit) {
    val ctx = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Setup needed", fontWeight = FontWeight.SemiBold)
            CheckLine("Location permission", c.location)
            CheckLine("Notifications", c.notifications)
            CheckLine("Chosen as mock location app", c.mockApp)
            if (!c.location || !c.notifications) {
                Button(onClick = askPermissions) { Text("Grant permissions") }
            }
            if (!c.mockApp) {
                Text(
                    "Settings > Developer options > Select mock location app > OP Mock Location. " +
                        "If Developer options is hidden, tap Build number 7 times in About phone.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = { openDeveloperOptions(ctx) }) { Text("Open Developer options") }
            }
        }
    }
}

@Composable
private fun CheckLine(label: String, ok: Boolean) {
    Text((if (ok) "✓  " else "✗  ") + label)
}

@Composable
private fun StatusCard(s: MockState.Status) {
    val ctx = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (s.running) s.mode else "Off",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (s.running) OutlinedButton(onClick = { stopMock(ctx) }) { Text("Stop") }
            }
            if (!s.running) {
                Text(s.error ?: "Real GPS is in use. Pick a position or run a scenario.", style = MaterialTheme.typography.bodySmall)
                return@Column
            }
            if (s.label.isNotBlank()) Text(s.label)
            if (s.stepCount > 0) {
                Text("Step ${s.stepIndex + 1} of ${s.stepCount}: ${s.stepLabel}, ${fmtSecs(s.stepRemainingSec)} left")
            }
            s.point?.let {
                Text(
                    "${fmt6(it.lat)}, ${fmt6(it.lng)}  ±${s.accuracyM.toInt()} m",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                )
            }
            s.nearest?.let { name ->
                Text("Nearest office: $name, ${s.nearestDistanceM?.toInt()} m from centre")
            }
            Text("Office Presence should see: ${expectation(s)}", fontWeight = FontWeight.Medium)
            Text("Fixes sent: ${s.fixes}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun expectation(s: MockState.Status): String = when {
    !s.emitting -> "no new fixes"
    s.accuracyM > 100f -> "ignored, accuracy worse than 100 m"
    s.insideNearest -> "In at ${s.nearest}"
    else -> "Out"
}

// ---------------- Control ----------------

private enum class Pos(val label: String, val kind: SpotKind, val meters: Double) {
    CENTRE("Centre", SpotKind.CENTRE, 0.0),
    EDGE_IN("Edge -10 m", SpotKind.FROM_EDGE, -10.0),
    EDGE_OUT("Edge +20 m", SpotKind.FROM_EDGE, 20.0),
    OUTSIDE("Outside +400 m", SpotKind.FROM_EDGE, 400.0),
    FAR("Far, 5 km", SpotKind.CENTRE, 5000.0),
}

@Composable
private fun ControlTab(offices: List<Office>, selectedId: String?, onSelect: (String) -> Unit) {
    val ctx = LocalContext.current
    var pos by remember { mutableStateOf(Pos.CENTRE) }
    var acc by remember { mutableFloatStateOf(6f) }
    var jitter by remember { mutableStateOf(true) }
    var intervalMs by remember { mutableLongStateOf(1000L) }
    var custom by remember { mutableStateOf("") }

    if (offices.isEmpty()) {
        Text("Add an office first in the Offices tab.")
        return
    }
    val office = offices.firstOrNull { it.id == selectedId } ?: offices.first()

    OfficePicker("Office", offices, office.id, onSelect)

    Label("Position")
    ChipRow {
        Pos.entries.forEach { p ->
            FilterChip(selected = pos == p, onClick = { pos = p }, label = { Text(p.label) })
        }
    }
    Label("GPS accuracy")
    ChipRow {
        listOf(5f, 12f, 40f, 150f).forEach { a ->
            FilterChip(selected = acc == a, onClick = { acc = a }, label = { Text("±${a.toInt()} m") })
        }
    }
    Hint(accuracyHint(acc))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = jitter, onCheckedChange = { jitter = it })
        Spacer(Modifier.width(8.dp))
        Text("Small random jitter (3 m), like real GPS")
    }
    Label("Send a fix every")
    ChipRow {
        listOf(1000L, 2000L, 5000L).forEach { ms ->
            FilterChip(selected = intervalMs == ms, onClick = { intervalMs = ms }, label = { Text("${ms / 1000} s") })
        }
    }
    Button(
        onClick = {
            val p = Geo.resolve(Spot(0, pos.kind, pos.meters), listOf(office))
            startStatic(ctx, p, acc, if (jitter) 3.0 else 0.0, "${pos.label} · ${office.name}", intervalMs)
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Mock this position") }

    HorizontalDivider()
    OutlinedTextField(
        value = custom,
        onValueChange = { custom = it },
        label = { Text("Custom point: lat, lng") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedButton(
        onClick = {
            val p = Geo.parseLatLng(custom)
            if (p == null) toast(ctx, "Enter a point like 19.0760, 72.8777")
            else startStatic(ctx, p, acc, if (jitter) 3.0 else 0.0, "Custom point", intervalMs)
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Mock custom point") }
    Hint("Tip: the notification has Inside, Outside and Stop buttons, so you can switch without opening this app.")
}

private fun accuracyHint(a: Float) = when {
    a <= 12f -> "Good fix. Counts as inside or outside normally."
    a <= 100f -> "The server accepts it, but the phone ignores a geofence exit worse than 15 m."
    else -> "Worse than the default fence radius (100 m). Office Presence ignores it for In and Out."
}

// ---------------- Scenarios ----------------

@Composable
private fun ScenariosTab(offices: List<Office>, selectedId: String?) {
    val ctx = LocalContext.current
    var aId by remember { mutableStateOf(selectedId ?: offices.firstOrNull()?.id) }
    var bId by remember { mutableStateOf(offices.firstOrNull { it.id != aId }?.id) }
    var scale by remember { mutableDoubleStateOf(60.0) }

    if (offices.isEmpty()) {
        Text("Add an office first in the Offices tab.")
        return
    }
    OfficePicker("Office A", offices, aId) { aId = it }
    if (offices.size > 1) OfficePicker("Office B (two office scenarios)", offices, bId) { bId = it }

    SpeedPicker(scale) { scale = it }
    val twoReady = offices.size > 1 && bId != null && bId != aId
    Scenarios.all.forEach { sc ->
        ScenarioCard(sc, enabled = sc.officesNeeded <= 1 || twoReady) {
            val a = aId ?: return@ScenarioCard
            startService(
                ctx,
                MockService.intent(ctx, MockService.ACTION_SCENARIO)
                    .putExtra(MockService.EXTRA_SCENARIO, sc.id)
                    .putExtra(MockService.EXTRA_OFFICE_A, a)
                    .putExtra(MockService.EXTRA_OFFICE_B, bId)
                    .putExtra(MockService.EXTRA_SCALE, scale),
            )
        }
    }
}

@Composable
private fun ScenarioCard(sc: Scenario, enabled: Boolean, onRun: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(sc.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(sc.description, style = MaterialTheme.typography.bodyMedium)
            Text(
                sc.steps.joinToString("  →  ") { "${it.label} ${fmtMin(it.minutes)}" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Expect: ${sc.expected}", style = MaterialTheme.typography.bodySmall)
            Button(onClick = onRun, enabled = enabled) {
                Text(if (enabled) "Run (${fmtMin(sc.totalMinutes)} at real time)" else "Needs two different offices")
            }
        }
    }
}

// ---------------- Offices ----------------

@Composable
private fun OfficesTab(
    offices: List<Office>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onSave: (List<Office>) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var coords by remember { mutableStateOf("") }
    var radius by remember { mutableStateOf("100") }
    var points by remember { mutableStateOf("1") }
    var csv by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }

    Hint("Copy the centre, radius and points from Office Presence Admin > Offices so the fences match exactly.")
    if (offices.isEmpty()) Text("No offices yet.")
    offices.forEach { o ->
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = o.id == selectedId, onClick = { onSelect(o.id) })
                Column(Modifier.weight(1f)) {
                    Text(o.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${fmt6(o.lat)}, ${fmt6(o.lng)} · ${o.radiusM.toInt()} m · ${o.points} pt",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = { onSave(offices.filter { it.id != o.id }) }) { Text("Delete") }
            }
        }
    }

    Label("Add an office")
    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(
        coords, { coords = it },
        label = { Text("Centre: lat, lng (paste from Google Maps)") },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            radius, { radius = it }, label = { Text("Radius m") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            points, { points = it }, label = { Text("Points") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
        )
    }
    Button(onClick = {
        val p = Geo.parseLatLng(coords)
        val r = radius.toDoubleOrNull()
        val pts = points.toIntOrNull()
        msg = when {
            name.isBlank() -> "Enter a name."
            p == null -> "Enter the centre as lat, lng."
            r == null || r !in 20.0..5000.0 -> "Radius must be 20 to 5000 m."
            pts == null || pts !in 0..100 -> "Points must be 0 to 100."
            else -> {
                onSave(offices + Office(OfficeStore.newId(), name.trim(), p.lat, p.lng, r, pts))
                name = ""; coords = ""
                "Office added."
            }
        }
    }) { Text("Add office") }

    Label("Import CSV")
    Hint("Same format as the admin bulk import: name,latitude,longitude,radiusMeters,pointsPerVisitDay[,addressLabel]. Header row optional.")
    OutlinedTextField(
        csv, { csv = it },
        minLines = 4,
        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = {
        val (list, errors) = OfficeStore.parseCsv(csv)
        if (list.isNotEmpty()) {
            onSave(offices + list)
            csv = ""
        }
        msg = "Imported ${list.size}." + if (errors.isNotEmpty()) " Skipped ${errors.joinToString(", ")}." else ""
    }) { Text("Import") }
    msg?.let { Hint(it) }
}

// ---------------- Log ----------------

@Composable
private fun LogTab(lines: List<String>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Newest first", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        TextButton(onClick = { MockState.clearLog() }) { Text("Clear") }
    }
    if (lines.isEmpty()) Text("Nothing yet.")
    lines.forEach { Text(it, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
}

// ---------------- small parts ----------------

/** Seconds per scenario minute: 60 (real time), 15 (x4) or 1 (x60). */
@Composable
internal fun SpeedPicker(scale: Double, onChange: (Double) -> Unit) {
    Label("Speed")
    ChipRow {
        listOf(60.0 to "Real time", 15.0 to "x4", 1.0 to "x60").forEach { (v, l) ->
            FilterChip(selected = scale == v, onClick = { onChange(v) }, label = { Text(l) })
        }
    }
    Hint(
        when (scale) {
            60.0 -> "Real minutes. Results follow the normal rules (gap 15 min, sample every 5 min)."
            1.0 -> "1 scenario minute = 1 second. Turn on Developer mode in Office Presence (test gap 15 s, sampling 15 s) so the gap rule scales the same way."
            else -> "1 scenario minute = 15 seconds. Good for watching notifications and Today. Gap results will not match the real rules."
        }
    )
}

@Composable
internal fun OfficePicker(label: String, offices: List<Office>, selectedId: String?, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = offices.firstOrNull { it.id == selectedId }
    Column {
        Label(label)
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                Text(current?.let { "${it.name} (${it.radiusM.toInt()} m, ${it.points} pt)" } ?: "Choose an office")
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                offices.forEach { o ->
                    DropdownMenuItem(
                        text = { Text("${o.name} (${o.radiusM.toInt()} m, ${o.points} pt)") },
                        onClick = { onSelect(o.id); open = false },
                    )
                }
            }
        }
    }
}

@Composable
internal fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
internal fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge)
}

@Composable
internal fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// ---------------- actions ----------------

internal fun startService(ctx: Context, intent: Intent) {
    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        toast(ctx, "Grant location permission first.")
        return
    }
    if (!MockCheck.isMockApp(ctx)) {
        toast(ctx, "Choose this app as the mock location app in Developer options.")
        return
    }
    ContextCompat.startForegroundService(ctx, intent)
}

private fun startStatic(ctx: Context, p: LatLng, acc: Float, jitter: Double, label: String, intervalMs: Long) {
    startService(
        ctx,
        MockService.intent(ctx, MockService.ACTION_STATIC)
            .putExtra(MockService.EXTRA_LAT, p.lat)
            .putExtra(MockService.EXTRA_LNG, p.lng)
            .putExtra(MockService.EXTRA_ACCURACY, acc)
            .putExtra(MockService.EXTRA_JITTER, jitter)
            .putExtra(MockService.EXTRA_LABEL, label)
            .putExtra(MockService.EXTRA_INTERVAL, intervalMs),
    )
}

private fun stopMock(ctx: Context) {
    if (MockService.isRunning) ctx.startService(MockService.intent(ctx, MockService.ACTION_STOP))
}

private fun openDeveloperOptions(ctx: Context) {
    try {
        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
    } catch (e: Exception) {
        ctx.startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}

private fun toast(ctx: Context, text: String) = Toast.makeText(ctx, text, Toast.LENGTH_LONG).show()

private fun fmt6(v: Double) = String.format(Locale.US, "%.6f", v)

private fun fmtMin(m: Double) =
    if (m % 1.0 == 0.0) "${m.toInt()} min" else String.format(Locale.US, "%.1f min", m)

private fun fmtSecs(s: Long) = String.format(Locale.US, "%d:%02d", s / 60, s % 60)
