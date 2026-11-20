package app.pushcraft.ui.schedule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.pushcraft.data.AppData
import app.pushcraft.data.Schedule
import app.pushcraft.notify.Poster
import app.pushcraft.notify.Scheduler
import app.pushcraft.ui.Avatar
import app.pushcraft.ui.EmptyState
import app.pushcraft.ui.GlassCard
import app.pushcraft.ui.InfoBanner
import app.pushcraft.ui.MainViewModel
import app.pushcraft.ui.bottomSpace
import app.pushcraft.ui.ScreenTitle
import app.pushcraft.ui.SectionHeader
import app.pushcraft.ui.compose.ScheduleSheet
import app.pushcraft.ui.compose.formatWhen
import app.pushcraft.ui.compose.repeatSummary
import app.pushcraft.ui.settings.Perms
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun ScheduledScreen(vm: MainViewModel, data: AppData) {
    val ctx = LocalContext.current
    var exact by remember { mutableStateOf(Scheduler.canExact(ctx)) }
    var notifications by remember { mutableStateOf(Poster.canPost(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        exact = Scheduler.canExact(ctx)
        notifications = Poster.canPost(ctx)
    }
    var editing by remember { mutableStateOf<Schedule?>(null) }

    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    fun group(s: Schedule): String {
        val next = s.nextAt
        if (!s.enabled) return "Paused"
        if (next == null) return "Finished"
        val d = Instant.ofEpochMilli(next).atZone(zone).toLocalDate()
        return when {
            d == today -> "Today"
            d == today.plusDays(1) -> "Tomorrow"
            d.isBefore(today.plusDays(7)) -> "This week"
            else -> "Later"
        }
    }
    val order = listOf("Today", "Tomorrow", "This week", "Later", "Paused", "Finished")
    val grouped = data.schedules
        .sortedBy { it.nextAt ?: Long.MAX_VALUE }
        .groupBy(::group)
        .toSortedMap(compareBy { order.indexOf(it) })
    val active = data.schedules.count { it.enabled && it.nextAt != null }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenTitle("Scheduled", if (active == 0) "Nothing upcoming" else "$active upcoming")
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomSpace())) {
            if (!notifications) item {
                InfoBanner(Icons.Rounded.Warning, "Notifications are blocked, so scheduled ones won't appear.",
                    tint = MaterialTheme.colorScheme.error, actionLabel = "Allow", onAction = { Perms.openNotificationSettings(ctx) },
                    modifier = Modifier.padding(top = 8.dp))
            }
            if (!exact) item {
                InfoBanner(Icons.Rounded.Warning, "Exact alarms are off. Android may deliver these a few minutes late.",
                    tint = MaterialTheme.colorScheme.tertiary, actionLabel = "Allow", onAction = { Perms.openExactAlarmSettings(ctx) },
                    modifier = Modifier.padding(top = 8.dp))
            }
            if (data.schedules.isEmpty()) item {
                EmptyState(Icons.Rounded.EventBusy, "Nothing scheduled", "Open a notification in Create and tap Schedule to set a date, time and repeat.")
            }
            grouped.forEach { (label, list) ->
                item(key = "h-$label") { SectionHeader(label) }
                items(list, key = { it.id }) { s -> ScheduleRow(vm, data, s, onEdit = { editing = s }) }
            }
        }
    }

    editing?.let { s ->
        ScheduleSheet(
            existing = s,
            onSave = { firstAt, repeat, interval ->
                Scheduler.save(ctx, s.copy(firstAt = firstAt, repeat = repeat, intervalMinutes = interval, enabled = true))
                editing = null
                vm.flash("Schedule updated")
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun ScheduleRow(vm: MainViewModel, data: AppData, s: Schedule, onEdit: () -> Unit) {
    val ctx = LocalContext.current
    val t = data.templates.find { it.id == s.templateId } ?: return
    val sender = vm.repo.sender(t.senderId)
    var menu by remember { mutableStateOf(false) }
    GlassCard(
        modifier = Modifier.padding(bottom = 10.dp),
        onClick = { vm.edit(t) },
        padding = PaddingValues(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(sender, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title.ifBlank { "(no title)" }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    (s.nextAt?.let { formatWhen(it) } ?: "Done") + " · " + repeatSummary(s),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (s.enabled && s.nextAt != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (s.firedCount > 0) {
                    Text("Fired ${s.firedCount}×", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Switch(checked = s.enabled, onCheckedChange = { Scheduler.setEnabled(ctx, s, it) })
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Send now") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Send, null) },
                        onClick = { menu = false; vm.sendTemplate(t) })
                    DropdownMenuItem(text = { Text("Edit schedule") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                        onClick = { menu = false; onEdit() })
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; Scheduler.delete(ctx, s.id); vm.flash("Schedule deleted") },
                    )
                }
            }
        }
    }
}
