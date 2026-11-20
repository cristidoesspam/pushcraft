package app.pushcraft.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.pushcraft.data.AppData
import app.pushcraft.data.NotifStyle
import app.pushcraft.data.SeqStep
import app.pushcraft.data.Sender
import app.pushcraft.data.Sequence
import app.pushcraft.data.newId
import app.pushcraft.ui.Avatar
import app.pushcraft.ui.MainViewModel

private val DELAY_CHOICES = listOf(0L, 500L, 1000L, 1500L, 2000L, 3000L, 5000L)
private fun delayText(ms: Long) = if (ms == 0L) "now" else if (ms < 1000) "${ms}ms" else "${"%.1f".format(ms / 1000f).removeSuffix(".0")}s"

@Composable
fun SequenceEditorDialog(vm: MainViewModel, data: AppData, initial: Sequence, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var steps by remember { mutableStateOf(initial.steps.ifEmpty { listOf(SeqStep(senderId = data.senders.firstOrNull()?.id ?: initial.steps.firstOrNull()?.senderId ?: "pushcraft")) }) }
    val senders = remember(data.senders) { listOf(Sender.Default) + data.senders }

    fun updateStep(i: Int, fn: (SeqStep) -> SeqStep) {
        steps = steps.toMutableList().also { it[i] = fn(it[i]) }
    }
    fun commit(): Sequence {
        val seq = initial.copy(name = name.trim().ifBlank { "Untitled sequence" }, steps = steps)
        return vm.saveSequence(seq)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close") }
                    Text("Sequence", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.playSequence(commit()) }) {
                        Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Play")
                    }
                    TextButton(onClick = { commit(); vm.flash("Saved"); onDismiss() }) {
                        Icon(Icons.Rounded.Save, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Save")
                    }
                }

                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    item {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(40) },
                            label = { Text("Sequence name") },
                            placeholder = { Text("e.g. Minecraft invite") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                        )
                        Text(
                            "Each message fires after its delay, one after another. Change the sender per line to fake a group chat.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 10.dp),
                        )
                    }
                    itemsIndexed(steps, key = { _, s -> s.id }) { i, step ->
                        StepCard(
                            index = i,
                            step = step,
                            senders = senders,
                            canMoveUp = i > 0,
                            canMoveDown = i < steps.size - 1,
                            onChange = { updateStep(i) { _ -> it } },
                            onMoveUp = { steps = steps.toMutableList().also { it.add(i - 1, it.removeAt(i)) } },
                            onMoveDown = { steps = steps.toMutableList().also { it.add(i + 1, it.removeAt(i)) } },
                            onDelete = { if (steps.size > 1) steps = steps.filterIndexed { idx, _ -> idx != i } },
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    item {
                        OutlinedButton(
                            onClick = {
                                val last = steps.lastOrNull()
                                steps = steps + SeqStep(senderId = last?.senderId ?: senders.first().id, style = last?.style ?: NotifStyle.MESSAGING, delayMs = 1000)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("Add message")
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StepCard(
    index: Int,
    step: SeqStep,
    senders: List<Sender>,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onChange: (SeqStep) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    var senderMenu by remember { mutableStateOf(false) }
    val sender = senders.find { it.id == step.senderId } ?: senders.first()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${index + 1}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            Box {
                Row(
                    Modifier.clip(RoundedCornerShape(20.dp)).clickable { senderMenu = true }.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(sender, 28.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(sender.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(120.dp))
                }
                DropdownMenu(expanded = senderMenu, onDismissRequest = { senderMenu = false }) {
                    senders.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(s.name) },
                            leadingIcon = { Avatar(s, 24.dp) },
                            onClick = { onChange(step.copy(senderId = s.id)); senderMenu = false },
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onMoveUp, enabled = canMoveUp) { Icon(Icons.Rounded.ArrowUpward, "Up", Modifier.size(18.dp)) }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) { Icon(Icons.Rounded.ArrowDownward, "Down", Modifier.size(18.dp)) }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "Delete", Modifier.size(18.dp)) }
        }
        OutlinedTextField(
            value = step.body,
            onValueChange = { onChange(step.copy(body = it)) },
            label = { Text("Message") },
            placeholder = { Text("minecraaft at 7 lads?") },
            minLines = 1,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (index == 0) "Start after" else "Wait", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            DELAY_CHOICES.forEach { d ->
                FilterChip(selected = step.delayMs == d, onClick = { onChange(step.copy(delayMs = d)) }, label = { Text(delayText(d)) })
            }
        }
    }
}
