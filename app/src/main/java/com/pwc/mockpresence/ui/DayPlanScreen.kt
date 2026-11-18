@file:OptIn(ExperimentalMaterial3Api::class)

package com.pwc.mockpresence.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pwc.mockpresence.data.PlanStore
import com.pwc.mockpresence.mock.MockService
import com.pwc.mockpresence.model.DayPlan
import com.pwc.mockpresence.model.Office
import com.pwc.mockpresence.model.PlanBuilder
import com.pwc.mockpresence.model.PlanStop
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// ---------------- Day plan ----------------

@Composable
internal fun DayPlanTab(offices: List<Office>, selectedId: String?) {
    val ctx = LocalContext.current
    val store = remember { PlanStore(ctx) }
    var plans by remember { mutableStateOf(store.load()) }
    var editing by remember { mutableStateOf<DayPlan?>(null) }
    var scale by remember { mutableDoubleStateOf(60.0) }
    var msg by remember { mutableStateOf<String?>(null) }
    var toDelete by remember { mutableStateOf<DayPlan?>(null) }

    if (offices.isEmpty()) {
        Text("Add an office first in the Offices tab.")
        return
    }

    fun run(plan: DayPlan) = startService(
        ctx,
        MockService.intent(ctx, MockService.ACTION_PLAN)
            .putExtra(MockService.EXTRA_PLAN_ID, plan.id)
            .putExtra(MockService.EXTRA_SCALE, scale),
    )

    fun newPlan() {
        val office = offices.firstOrNull { it.id == selectedId } ?: offices.first()
        msg = null
        editing = DayPlan(
            id = PlanStore.newId(),
            name = "New plan",
            stops = listOf(PlanStop(office.id, 60, 20)),
        )
    }

    val current = editing
    if (current != null) {
        PlanEditor(
            initial = current,
            offices = offices,
            selectedId = selectedId,
            onBack = { editing = null },
            onSave = { plan, andRun ->
                val saved = plan.copy(updatedAt = System.currentTimeMillis())
                store.save(saved)
                plans = store.load()
                if (andRun) run(saved)
                msg = "Plan saved"
                editing = null
            },
        )
        return
    }

    msg?.let { Text(it, fontWeight = FontWeight.Medium) }
    if (plans.isEmpty()) {
        Text("Plan a day across offices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("Add stops, set how long to stay and travel, then run it like a scenario.")
        Button(onClick = ::newPlan) { Text("New plan") }
        return
    }

    Label("Saved plans")
    plans.forEach { plan ->
        PlanCard(
            plan = plan,
            offices = offices,
            onRun = { run(plan) },
            onEdit = { msg = null; editing = plan },
            onDelete = { toDelete = plan },
        )
    }
    SpeedPicker(scale) { scale = it }
    Button(onClick = ::newPlan, modifier = Modifier.fillMaxWidth()) { Text("+ New plan") }

    toDelete?.let { plan ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete this plan?") },
            confirmButton = {
                TextButton(onClick = {
                    store.delete(plan.id)
                    plans = store.load()
                    toDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PlanCard(
    plan: DayPlan,
    offices: List<Office>,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val built = PlanBuilder.build(plan, offices)
    val total = built?.first?.totalMinutes?.toInt() ?: PlanBuilder.totalMinutes(plan)
    val n = plan.stops.size
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(plan.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("$n ${if (n == 1) "stop" else "stops"} · ${PlanBuilder.formatMinutes(total)}", style = MaterialTheme.typography.bodyMedium)
            Text(
                plan.stops.joinToString(" → ") { s ->
                    "${offices.firstOrNull { it.id == s.officeId }?.name ?: "?"} ${s.stayMinutes}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (built == null) {
                Text("Office missing, edit this plan", color = MaterialTheme.colorScheme.error)
            } else {
                Text(PlanBuilder.expectedSummary(plan, offices), style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (built != null) Button(onClick = onRun) { Text("Run") }
                OutlinedButton(onClick = onEdit) { Text("Edit") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

// ---------------- editor ----------------

@Composable
private fun PlanEditor(
    initial: DayPlan,
    offices: List<Office>,
    selectedId: String?,
    onBack: () -> Unit,
    onSave: (DayPlan, Boolean) -> Unit,
) {
    var plan by remember(initial.id) { mutableStateOf(initial) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }

    fun setStop(i: Int, s: PlanStop) {
        plan = plan.copy(stops = plan.stops.toMutableList().also { it[i] = s })
    }

    /** Swaps office and stay between two positions. Travel rows stay where they are. */
    fun swap(a: Int, b: Int) {
        val list = plan.stops.toMutableList()
        val sa = list[a]
        val sb = list[b]
        list[a] = sa.copy(officeId = sb.officeId, stayMinutes = sb.stayMinutes)
        list[b] = sb.copy(officeId = sa.officeId, stayMinutes = sa.stayMinutes)
        plan = plan.copy(stops = list)
    }

    fun trySave(andRun: Boolean) {
        val name = plan.name.trim()
        if (name.isEmpty() || name.length > 40) {
            nameError = "Enter a plan name."
            return
        }
        onSave(plan.copy(name = name), andRun)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { if (plan != initial) confirmDiscard = true else onBack() }) { Text("← Plans") }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { trySave(andRun = false) }) { Text("Save") }
    }

    Label("Plan name")
    OutlinedTextField(
        value = plan.name,
        onValueChange = { plan = plan.copy(name = it.take(40)); nameError = null },
        singleLine = true,
        isError = nameError != null,
        supportingText = nameError?.let { e -> { Text(e) } },
        modifier = Modifier.fillMaxWidth(),
    )

    Label("Timeline")
    PlanTimeline(plan, offices)

    val n = plan.stops.size
    plan.stops.forEachIndexed { i, stop ->
        StopCard(
            index = i,
            count = n,
            stop = stop,
            offices = offices,
            onChange = { setStop(i, it) },
            onUp = { swap(i, i - 1) },
            onDown = { swap(i, i + 1) },
            onRemove = { plan = plan.copy(stops = plan.stops.filterIndexed { j, _ -> j != i }) },
        )
        if (i < n - 1) {
            Row(
                Modifier.padding(start = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Travel", style = MaterialTheme.typography.bodyMedium)
                Stepper(
                    value = stop.travelToNextMinutes,
                    range = 0..240,
                    step = 5,
                    format = { if (it == 0) "Jump (no travel)" else "$it min" },
                    onChange = { setStop(i, stop.copy(travelToNextMinutes = it)) },
                )
            }
        }
    }
    val full = n >= PlanBuilder.MAX_STOPS
    OutlinedButton(
        onClick = {
            val used = plan.stops.map { it.officeId }.toSet()
            val office = offices.firstOrNull { it.id !in used }
                ?: offices.firstOrNull { it.id == selectedId }
                ?: offices.first()
            val list = plan.stops.toMutableList()
            list[list.lastIndex] = list.last().copy(travelToNextMinutes = 20)
            list += PlanStop(office.id, 60, 20)
            plan = plan.copy(stops = list)
        },
        enabled = !full,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(if (full) "12 stops is the limit" else "+ Add stop") }

    Label("Before and after")
    StepperRow("Start outside", plan.startOutsideMinutes) { plan = plan.copy(startOutsideMinutes = it) }
    StepperRow("End outside", plan.endOutsideMinutes) { plan = plan.copy(endOutsideMinutes = it) }

    Label("GPS accuracy at stops")
    ChipRow {
        listOf(5f, 12f, 40f, 150f).forEach { a ->
            FilterChip(
                selected = plan.accuracyM == a,
                onClick = { plan = plan.copy(accuracyM = a) },
                label = { Text("±${a.toInt()} m") },
            )
        }
    }

    Label("Expected in Office Presence")
    PlanBuilder.expectedLines(plan, offices).forEachIndexed { i, line ->
        if (i == 0) Text(line, fontWeight = FontWeight.Medium) else Hint(line)
    }
    val nowMin = LocalTime.now().toSecondOfDay() / 60
    PlanBuilder.warnings(plan, offices, nowMin).forEach {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }

    Button(onClick = { trySave(andRun = true) }, modifier = Modifier.fillMaxWidth()) { Text("Save and run") }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; onBack() }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StopCard(
    index: Int,
    count: Int,
    stop: PlanStop,
    offices: List<Office>,
    onChange: (PlanStop) -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("${index + 1}", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.weight(1f))
                if (index > 0) TextButton(onClick = onUp) { Text("Up") }
                if (index < count - 1) TextButton(onClick = onDown) { Text("Down") }
                if (count > 1) TextButton(onClick = onRemove) { Text("Remove") }
            }
            if (offices.none { it.id == stop.officeId }) {
                Text("Office missing, edit this plan", color = MaterialTheme.colorScheme.error)
            }
            OfficePicker("Office", offices, stop.officeId) { onChange(stop.copy(officeId = it)) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Stay", style = MaterialTheme.typography.bodyMedium)
                Stepper(
                    value = stop.stayMinutes,
                    range = 1..600,
                    step = 5,
                    editable = true,
                    onChange = { onChange(stop.copy(stayMinutes = it)) },
                )
            }
        }
    }
}

@Composable
private fun StepperRow(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Stepper(value = value, range = 0..60, step = 1, onChange = onChange)
    }
}

/** Minus and plus buttons around a minute value. With editable, tapping the number opens a small field. */
@Composable
private fun Stepper(
    value: Int,
    range: IntRange,
    step: Int,
    onChange: (Int) -> Unit,
    editable: Boolean = false,
    format: (Int) -> String = { "$it min" },
) {
    var typing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }

    fun commit() {
        text.toIntOrNull()?.let { onChange(it.coerceIn(range)) }
        typing = false
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick = { onChange((value - step).coerceIn(range)) }, enabled = value > range.first) { Text("−") }
        if (typing) {
            OutlinedTextField(
                value = text,
                onValueChange = { t -> text = t.filter { it.isDigit() }.take(3) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                modifier = Modifier.width(80.dp),
            )
            TextButton(onClick = ::commit) { Text("OK") }
        } else {
            Text(
                format(value),
                modifier = if (editable) {
                    Modifier.clickable { text = value.toString(); typing = true }.padding(4.dp)
                } else Modifier.padding(4.dp),
            )
        }
        OutlinedButton(onClick = { onChange((value + step).coerceIn(range)) }, enabled = value < range.last) { Text("+") }
    }
}

private data class Segment(val minutes: Int, val office: String?)

/** One bar with segments proportional to minutes: stays in the accent colour, everything else neutral. */
@Composable
private fun PlanTimeline(plan: DayPlan, offices: List<Office>) {
    val segments = buildList {
        if (plan.startOutsideMinutes > 0) add(Segment(plan.startOutsideMinutes + 2, null))
        plan.stops.forEachIndexed { i, s ->
            add(Segment(s.stayMinutes, offices.firstOrNull { it.id == s.officeId }?.name ?: "?"))
            if (i < plan.stops.lastIndex && s.travelToNextMinutes > 0) add(Segment(s.travelToNextMinutes, null))
        }
        add(Segment(plan.endOutsideMinutes + 2, null))
    }
    val total = PlanBuilder.build(plan, offices)?.first?.totalMinutes?.toInt() ?: PlanBuilder.totalMinutes(plan)
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val neutral = MaterialTheme.colorScheme.surfaceVariant

    Row(
        Modifier
            .fillMaxWidth()
            .height(16.dp)
            .clip(RoundedCornerShape(4.dp)),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        segments.forEach { seg ->
            Box(
                Modifier
                    .weight(seg.minutes.toFloat())
                    .fillMaxHeight()
                    .background(if (seg.office != null) accent else neutral),
                contentAlignment = Alignment.Center,
            ) {
                if (seg.office != null && seg.minutes.toFloat() / total >= 0.15f) {
                    Text(
                        seg.office,
                        color = onAccent,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }
    }
    val fmt = DateTimeFormatter.ofPattern("HH:mm")
    val now = LocalTime.now()
    Row {
        Hint("${now.format(fmt)} start (real time)")
        Spacer(Modifier.weight(1f))
        Hint(now.plusMinutes(total.toLong()).format(fmt))
    }
}
