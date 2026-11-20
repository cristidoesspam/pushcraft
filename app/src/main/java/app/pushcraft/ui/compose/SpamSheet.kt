package app.pushcraft.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pushcraft.data.SPAM_MAX
import app.pushcraft.data.SpamConfig
import app.pushcraft.data.SpamMode
import app.pushcraft.ui.Segmented

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpamSheet(initial: SpamConfig, onStart: (SpamConfig) -> Unit, onDismiss: () -> Unit) {
    var count by remember { mutableFloatStateOf(initial.count.coerceIn(1, SPAM_MAX).toFloat()) }
    var interval by remember { mutableFloatStateOf(initial.intervalMs.coerceIn(0, 5000).toFloat()) }
    var mode by remember { mutableStateOf(initial.mode) }
    var variantsText by remember { mutableStateOf(initial.variants.joinToString("\n")) }

    val variants = variantsText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    val duration = (count.toInt() - 1).coerceAtLeast(0) * (interval.toInt() / 1000f)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text("Spam mode", style = MaterialTheme.typography.titleLarge)
            Text(
                "Fires the current notification over and over. Keep Pushcraft open while it runs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )

            Spacer(Modifier.height(18.dp))
            Row {
                Text("How many", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text("${count.toInt()}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Slider(value = count, onValueChange = { count = it }, valueRange = 1f..SPAM_MAX.toFloat(), steps = SPAM_MAX - 2)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 25, 50, 100).forEach { n ->
                    FilterChip(selected = count.toInt() == n, onClick = { count = n.toFloat() }, label = { Text("$n") })
                }
            }
            Spacer(Modifier.height(10.dp))

            Row {
                Text("Gap between each", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text(
                    if (interval < 1000) "${interval.toInt()} ms" else "%.1fs".format(interval / 1000f),
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(value = interval, onValueChange = { interval = it }, valueRange = 0f..5000f, steps = 49)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "instant", 250 to "0.25s", 500 to "0.5s", 1000 to "1s", 2000 to "2s").forEach { (ms, lbl) ->
                    FilterChip(selected = interval.toInt() == ms, onClick = { interval = ms.toFloat() }, label = { Text(lbl) })
                }
            }

            Spacer(Modifier.height(10.dp))
            Segmented(
                options = SpamMode.entries,
                selected = mode,
                label = { it.label },
                onSelect = { mode = it },
                modifier = Modifier.fillMaxWidth(),
            )
            if (mode == SpamMode.CYCLE) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = variantsText,
                    onValueChange = { variantsText = it },
                    label = { Text("Messages, one per line") },
                    placeholder = { Text("stop ignoring me\nhello??\nI know you're online") },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
                Text(
                    if (variants.isEmpty()) "Add at least one line, or it falls back to the message above"
                    else "Cycles through ${variants.size} message${if (variants.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Sends the current message each time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "≈ ${"%.1f".format(duration)}s total" + if (count.toInt() >= 30) " · that's a lot" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    onStart(SpamConfig(count = count.toInt(), intervalMs = interval.toLong(), mode = mode, variants = variants))
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Rounded.Bolt, null)
                Spacer(Modifier.width(8.dp))
                Text("Start spamming ${count.toInt()}×")
            }
        }
    }
}
