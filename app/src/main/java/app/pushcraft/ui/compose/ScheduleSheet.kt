package app.pushcraft.ui.compose

import android.text.format.DateFormat
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.pushcraft.data.Repeat
import app.pushcraft.data.Schedule
import app.pushcraft.notify.Scheduler
import app.pushcraft.ui.InfoBanner
import app.pushcraft.ui.settings.Perms
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val INTERVALS = listOf(15, 30, 60, 120, 240, 720)

fun intervalLabel(m: Int) = if (m < 60) "$m min" else if (m % 60 == 0) "${m / 60} h" else "${m / 60} h ${m % 60} min"

fun repeatSummary(s: Schedule): String = when (s.repeat) {
    Repeat.INTERVAL -> "Every ${intervalLabel(s.intervalMinutes)}"
    else -> s.repeat.label
}

fun formatWhen(millis: Long): String {
    val t = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    val today = LocalDate.now()
    val day = when (t.toLocalDate()) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> t.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
    }
    return "$day, " + t.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleSheet(
    existing: Schedule? = null,
    onSave: (firstAt: Long, repeat: Repeat, intervalMinutes: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val start = remember {
        existing?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it.firstAt), ZoneId.systemDefault()) }
            ?: LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)
    }
    var date by remember { mutableStateOf(start.toLocalDate()) }
    var time by remember { mutableStateOf(start.toLocalTime()) }
    var repeat by remember { mutableStateOf(existing?.repeat ?: Repeat.NONE) }
    var interval by remember { mutableIntStateOf(existing?.intervalMinutes ?: 60) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var exact by remember { mutableStateOf(Scheduler.canExact(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { exact = Scheduler.canExact(ctx) }

    val firstAt = LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val preview = Schedule(templateId = "", firstAt = firstAt, repeat = repeat, intervalMinutes = interval)
    val next = Scheduler.computeNext(preview, System.currentTimeMillis())
    val inPast = repeat == Repeat.NONE && firstAt <= System.currentTimeMillis()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(if (existing == null) "Schedule" else "Edit schedule", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(date.format(DateTimeFormatter.ofPattern("EEE, MMM d")))
                }
                OutlinedButton(onClick = { pickTime = true }, modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Rounded.AccessTime, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)))
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Repeat", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Repeat.entries.forEach { r ->
                    FilterChip(selected = repeat == r, onClick = { repeat = r }, label = { Text(r.label) })
                }
            }
            if (repeat == Repeat.INTERVAL) {
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    INTERVALS.forEach { m ->
                        FilterChip(selected = interval == m, onClick = { interval = m }, label = { Text(intervalLabel(m)) })
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                when {
                    inPast -> "Pick a time in the future"
                    next == null -> "No upcoming occurrence"
                    else -> "Next: ${formatWhen(next)}" + if (repeat != Repeat.NONE) " · then ${repeatSummary(preview).lowercase()}" else ""
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (inPast) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!exact) {
                Spacer(Modifier.height(12.dp))
                InfoBanner(
                    Icons.Rounded.Warning,
                    "Exact alarms are off, so Android may deliver this a few minutes late.",
                    tint = MaterialTheme.colorScheme.tertiary,
                    actionLabel = "Allow",
                    onAction = { Perms.openExactAlarmSettings(ctx) },
                )
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { onSave(firstAt, repeat, interval) },
                enabled = !inPast && next != null,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text(if (existing == null) "Schedule" else "Save") }
        }
    }

    if (pickDate) {
        val todayUtc = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayUtc
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
    if (pickTime) {
        val state = rememberTimePickerState(time.hour, time.minute, DateFormat.is24HourFormat(ctx))
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = { time = LocalTime.of(state.hour, state.minute); pickTime = false }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Cancel") } },
            text = { Column(horizontalAlignment = Alignment.CenterHorizontally) { TimePicker(state) } },
        )
    }
}
