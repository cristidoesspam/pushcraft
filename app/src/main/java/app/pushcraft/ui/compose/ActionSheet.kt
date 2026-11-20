package app.pushcraft.ui.compose

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pushcraft.data.ActionKind
import app.pushcraft.data.AppInfo
import app.pushcraft.data.InstalledApps
import app.pushcraft.data.NotifAction
import app.pushcraft.notify.ActionHandler
import app.pushcraft.script.ScriptExamples
import app.pushcraft.ui.AppIcon
import app.pushcraft.ui.InfoBanner
import app.pushcraft.ui.script.ScriptEditorDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ActionSheet(
    initial: NotifAction,
    isNew: Boolean,
    draftTitle: String,
    draftBody: String,
    vars: Map<String, Any?>,
    onSave: (NotifAction) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    var work by remember { mutableStateOf(initial) }
    var showScript by remember { mutableStateOf(false) }
    var showApps by remember { mutableStateOf(false) }

    val problem: String? = when {
        work.kind == ActionKind.OPEN_URL && work.target.isBlank() -> "Add a link"
        work.kind == ActionKind.OPEN_APP && work.target.isBlank() -> "Choose an app"
        work.kind == ActionKind.SCRIPT && work.script.isBlank() -> "Write a script"
        else -> null
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(if (isNew) "Add button" else "Edit button", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = work.label,
                onValueChange = { work = work.copy(label = it.take(24)) },
                label = { Text("Button label") },
                placeholder = { Text(work.kind.defaultLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            Text("When tapped", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionKind.entries.forEach { k ->
                    FilterChip(
                        selected = work.kind == k,
                        onClick = { work = work.copy(kind = k) },
                        label = { Text(k.label) },
                        leadingIcon = { Icon(k.icon(), null, Modifier.size(FilterChipDefaults.IconSize)) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            when (work.kind) {
                ActionKind.OPEN_URL -> {
                    OutlinedTextField(
                        value = work.target,
                        onValueChange = { work = work.copy(target = it.trim()) },
                        label = { Text("Link or deep link") },
                        placeholder = { Text("https://…  geo:…  spotify:…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        onClick = {
                            val intent = ActionHandler.viewIntent(work.target)
                            try {
                                if (intent != null) ctx.startActivity(intent)
                            } catch (e: ActivityNotFoundException) {
                                android.widget.Toast.makeText(ctx, "No app can open that link", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = work.target.isNotBlank(),
                    ) { Text("Test link") }
                }
                ActionKind.OPEN_APP -> {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .clickable { showApps = true }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (work.target.isNotBlank()) {
                            AppIcon(work.target, 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(InstalledApps.label(ctx, work.target) ?: work.target, modifier = Modifier.weight(1f))
                        } else {
                            Text("Choose an app to open", modifier = Modifier.weight(1f))
                        }
                        Text("Change", color = MaterialTheme.colorScheme.primary)
                    }
                }
                ActionKind.REPLY -> OutlinedTextField(
                    value = work.placeholder,
                    onValueChange = { work = work.copy(placeholder = it.take(40)) },
                    label = { Text("Placeholder in the reply box") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ActionKind.SNOOZE -> {
                    Text("Show it again in…", style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 5, 10, 15, 30, 60, 120).forEach { m ->
                            FilterChip(
                                selected = work.snoozeMinutes == m,
                                onClick = { work = work.copy(snoozeMinutes = m) },
                                label = { Text(if (m < 60) "$m min" else "${m / 60} h") },
                            )
                        }
                    }
                }
                ActionKind.DISMISS -> Text(
                    "Just closes the notification. Taps are still counted in Activity.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ActionKind.SCRIPT -> {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .clickable { showScript = true }
                            .padding(14.dp)
                    ) {
                        Text(
                            work.script.ifBlank { "// Tap to write a script" }.lines().take(6).joinToString("\n"),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { showScript = true }) {
                            Icon(Icons.Rounded.Code, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Edit script")
                        }
                        Spacer(Modifier.weight(1f))
                        if (work.script.isBlank()) {
                            TextButton(onClick = { work = work.copy(script = ScriptExamples.SMART_REPLY.code, withInput = true) }) { Text("Use example") }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Text box", style = MaterialTheme.typography.bodyLarge)
                            Text("Lets the user type; the text arrives as event.input", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = work.withInput, onCheckedChange = { work = work.copy(withInput = it) })
                    }
                    if (work.withInput) {
                        OutlinedTextField(
                            value = work.placeholder,
                            onValueChange = { work = work.copy(placeholder = it.take(40)) },
                            label = { Text("Text box placeholder") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Destructive style", style = MaterialTheme.typography.bodyLarge)
                    Text("Red label in the iOS mockup", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = work.destructive, onCheckedChange = { work = work.copy(destructive = it) })
            }
            if (work.opensUi) {
                Spacer(Modifier.height(10.dp))
                InfoBanner(
                    Icons.Rounded.Info,
                    "This button opens another app, so Android routes the tap through Pushcraft for a split second.",
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isNew) {
                    TextButton(onClick = onDelete) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                }
                Spacer(Modifier.weight(1f))
                if (problem != null) {
                    Text(problem, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                }
                Button(onClick = { onSave(work) }, enabled = problem == null) { Text(if (isNew) "Add" else "Done") }
            }
        }
    }

    if (showScript) {
        ScriptEditorDialog(
            initial = work.script,
            actionLabel = work.label.ifBlank { work.kind.defaultLabel },
            withInput = work.withInput,
            title = draftTitle,
            body = draftBody,
            vars = vars,
            onDone = { code -> work = work.copy(script = code); showScript = false },
            onDismiss = { showScript = false },
        )
    }
    if (showApps) {
        AppChooserDialog(
            onPick = { pkg -> work = work.copy(target = pkg); showApps = false },
            onDismiss = { showApps = false },
        )
    }
}

@Composable
private fun AppChooserDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    val apps by produceState<List<AppInfo>?>(null) { value = withContext(Dispatchers.IO) { InstalledApps.list(ctx) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Open which app?") },
        text = {
            Column {
                OutlinedTextField(value = query, onValueChange = { query = it }, placeholder = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                val list = apps
                if (list == null) {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(list.filter { query.isBlank() || it.label.contains(query, true) }, key = { it.packageName }) { app ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(app.packageName) }.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppIcon(app.packageName, 32.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
    )
}
