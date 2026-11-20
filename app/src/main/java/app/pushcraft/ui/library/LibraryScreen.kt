package app.pushcraft.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pushcraft.data.AppData
import app.pushcraft.data.LogEntry
import app.pushcraft.data.LogKind
import app.pushcraft.data.SeqStep
import app.pushcraft.data.Sequence
import app.pushcraft.data.Template
import app.pushcraft.ui.Avatar
import app.pushcraft.ui.EmptyState
import app.pushcraft.ui.GlassCard
import app.pushcraft.ui.MainViewModel
import app.pushcraft.ui.bottomSpace
import app.pushcraft.ui.ScreenTitle
import app.pushcraft.ui.Segmented
import app.pushcraft.ui.compose.SequenceEditorDialog
import android.text.format.DateUtils

@Composable
fun LibraryScreen(vm: MainViewModel, data: AppData) {
    var section by remember { mutableIntStateOf(0) }
    var editingSeq by remember { mutableStateOf<Sequence?>(null) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenTitle("Library", "${data.templates.size} saved · ${data.sequences.size} sequences") {
            FilledTonalButton(
                onClick = {
                    if (section == 1) editingSeq = Sequence(steps = listOf(SeqStep(senderId = data.senders.firstOrNull()?.id ?: "pushcraft")))
                    else { vm.newDraft(); vm.tab = app.pushcraft.ui.AppTab.CREATE }
                }
            ) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("New")
            }
        }
        Segmented(
            options = listOf(0, 1, 2),
            selected = section,
            label = { listOf("Notifications", "Sequences", "Activity")[it] },
            onSelect = { section = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        when (section) {
            0 -> Templates(vm, data)
            1 -> Sequences(vm, data, onEdit = { editingSeq = it })
            else -> Activity(data)
        }
    }

    editingSeq?.let { seq ->
        SequenceEditorDialog(vm, data, seq, onDismiss = { editingSeq = null })
    }
}

@Composable
private fun Sequences(vm: MainViewModel, data: AppData, onEdit: (Sequence) -> Unit) {
    if (data.sequences.isEmpty()) {
        EmptyState(
            Icons.AutoMirrored.Rounded.List,
            "No sequences yet",
            "A sequence fires several messages in a row with delays — like a fake group chat. Tap New to make one.",
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottomSpace()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(data.sequences.sortedByDescending { it.updatedAt }, key = { it.id }) { seq ->
            SequenceCard(vm, data, seq, onEdit = { onEdit(seq) })
        }
    }
}

@Composable
private fun SequenceCard(vm: MainViewModel, data: AppData, seq: Sequence, onEdit: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val totalMs = seq.steps.sumOf { it.delayMs }
    GlassCard(onClick = onEdit, padding = PaddingValues(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(seq.name.ifBlank { "Untitled sequence" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    seq.steps.filter { it.body.isNotBlank() }.joinToString("  →  ") { it.body }.ifBlank { "No messages yet" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${seq.steps.size} message${if (seq.steps.size == 1) "" else "s"} · ${"%.1f".format(totalMs / 1000f).removeSuffix(".0")}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            IconButton(onClick = { vm.playSequence(seq) }) { Icon(Icons.Rounded.PlayArrow, "Play", tint = MaterialTheme.colorScheme.primary) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("Duplicate") }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) }, onClick = { menu = false; vm.duplicateSequence(seq) })
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; vm.deleteSequence(seq.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Templates(vm: MainViewModel, data: AppData) {
    if (data.templates.isEmpty()) {
        EmptyState(Icons.Rounded.Inventory2, "Nothing saved yet", "Notifications you save or send show up here.")
        return
    }
    var query by remember { mutableStateOf("") }
    val list = data.templates
        .filter {
            query.isBlank() ||
                it.title.contains(query, true) ||
                it.body.contains(query, true) ||
                vm.repo.sender(it.senderId).name.contains(query, true)
        }
        .sortedByDescending { it.updatedAt }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottomSpace()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (data.templates.size >= 4) {
            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") } },
                    placeholder = { Text("Search notifications") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                )
            }
        }
        if (list.isEmpty()) {
            item { Text("No matches for \"$query\"", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
        }
        items(list, key = { it.id }) { t ->
            TemplateCard(vm, data, t)
        }
    }
}

@Composable
private fun TemplateCard(vm: MainViewModel, data: AppData, t: Template) {
    val sender = vm.repo.sender(t.senderId)
    val schedules = data.schedules.count { it.templateId == t.id && it.enabled && it.nextAt != null }
    val sent = data.counters["sent:${t.id}"] ?: 0
    var menu by remember { mutableStateOf(false) }
    GlassCard(onClick = { vm.edit(t) }, padding = PaddingValues(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Avatar(sender, 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(sender.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Text(t.title.ifBlank { "(no title)" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (t.body.isNotBlank()) {
                    Text(t.body, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    listOfNotNull(
                        t.style.label,
                        t.actions.size.takeIf { it > 0 }?.let { "$it button${if (it > 1) "s" else ""}" },
                        t.actions.count { it.script.isNotBlank() }.takeIf { it > 0 }?.let { "scripted" },
                        schedules.takeIf { it > 0 }?.let { "$it scheduled" },
                        sent.takeIf { it > 0 }?.let { "sent $it×" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Send now") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Send, null) },
                        onClick = { menu = false; vm.sendTemplate(t) },
                    )
                    DropdownMenuItem(
                        text = { Text("Duplicate") },
                        leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                        onClick = { menu = false; vm.duplicate(t) },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; vm.deleteTemplate(t) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Activity(data: AppData) {
    if (data.log.isEmpty()) {
        EmptyState(Icons.Rounded.History, "No activity yet", "Sends, button taps, replies and script output appear here.")
        return
    }
    val titles = data.templates.associate { it.id to it.title.ifBlank { "(no title)" } }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottomSpace())) {
        items(data.log) { e -> LogRow(e, e.templateId?.let { titles[it] }) }
    }
}

@Composable
private fun LogRow(e: LogEntry, templateTitle: String?) {
    val (icon, tint) = when (e.kind) {
        LogKind.SENT -> Icons.Rounded.NotificationsActive to MaterialTheme.colorScheme.primary
        LogKind.TAP -> Icons.Rounded.TouchApp to Color(0xFF0EA5E9)
        LogKind.REPLY -> Icons.AutoMirrored.Rounded.Reply to Color(0xFF22C55E)
        LogKind.SCRIPT -> Icons.Rounded.Code to MaterialTheme.colorScheme.secondary
        LogKind.ERROR -> Icons.Rounded.ErrorOutline to MaterialTheme.colorScheme.error
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp).padding(top = 2.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.message, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(templateTitle, DateUtils.getRelativeTimeSpanString(e.at).toString()).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
