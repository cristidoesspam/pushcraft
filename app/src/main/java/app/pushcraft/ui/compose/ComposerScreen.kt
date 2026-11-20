package app.pushcraft.ui.compose

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.pushcraft.data.ActionKind
import app.pushcraft.data.AppData
import app.pushcraft.data.Images
import app.pushcraft.data.Importance
import app.pushcraft.data.InstalledApps
import app.pushcraft.data.MAX_ACTIONS
import app.pushcraft.data.NotifAction
import app.pushcraft.data.NotifStyle
import app.pushcraft.data.SenderSource
import app.pushcraft.data.TemplateVars
import app.pushcraft.notify.Poster
import app.pushcraft.ui.AppTab
import app.pushcraft.ui.Avatar
import app.pushcraft.ui.GlassCard
import app.pushcraft.ui.InfoBanner
import app.pushcraft.ui.MainViewModel
import app.pushcraft.ui.bottomSpace
import app.pushcraft.ui.ScreenTitle
import app.pushcraft.ui.SectionHeader
import app.pushcraft.ui.Segmented
import app.pushcraft.ui.preview.PreviewModel
import app.pushcraft.ui.preview.PreviewStage
import app.pushcraft.ui.rememberImage
import app.pushcraft.ui.settings.Perms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Field { TITLE, BODY }

fun ActionKind.icon(): ImageVector = when (this) {
    ActionKind.OPEN_URL -> Icons.Rounded.Link
    ActionKind.OPEN_APP -> Icons.Rounded.Apps
    ActionKind.REPLY -> Icons.AutoMirrored.Rounded.Reply
    ActionKind.SNOOZE -> Icons.Rounded.Snooze
    ActionKind.DISMISS -> Icons.Rounded.Close
    ActionKind.SCRIPT -> Icons.Rounded.Code
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComposerScreen(vm: MainViewModel, data: AppData) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val draft = vm.draft
    val sender = data.senders.find { it.id == draft.senderId } ?: vm.repo.sender(draft.senderId)

    var showSenders by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<NotifAction?>(null) }
    var editingIsNew by remember { mutableStateOf(false) }
    var showSchedule by remember { mutableStateOf(false) }
    var showPresets by remember { mutableStateOf(false) }
    var showSpam by remember { mutableStateOf(false) }
    var truncated by remember { mutableStateOf(false) }
    var lastField by remember { mutableStateOf(Field.BODY) }
    var delayMs by remember { mutableStateOf(0L) }
    var pendingSend by remember { mutableStateOf<(() -> Unit)?>(null) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pendingSend?.invoke() else vm.flash("Notifications are blocked. Turn them on in Settings")
        pendingSend = null
    }
    fun withPermission(block: () -> Unit) {
        when {
            Poster.canPost(ctx) -> block()
            Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED -> {
                pendingSend = block
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            else -> {
                vm.flash("Notifications are off for Pushcraft")
                Perms.openNotificationSettings(ctx)
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val path = withContext(Dispatchers.IO) { Images.pictureFromUri(ctx, uri) }
            if (path != null) vm.updateDraft { it.copy(imagePath = path, style = NotifStyle.BIG_PICTURE) }
            else vm.flash("Couldn't read that image")
        }
    }

    val now = System.currentTimeMillis()
    val vars = data.vars[draft.id] ?: emptyMap()
    val count = (data.counters["sent:${draft.id}"] ?: 0) + 1
    val model = PreviewModel(
        title = TemplateVars.render(draft.title, now, vars, count, preview = true),
        body = TemplateVars.render(draft.body, now, vars, count, preview = true),
        sender = sender,
        style = draft.style,
        imagePath = draft.imagePath,
        actions = draft.actions,
    )
    val imeVisible = WindowInsets.isImeVisible
    val schedules = data.schedules.count { it.templateId == draft.id }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenTitle(
            title = if (vm.isSaved) "Edit notification" else "New notification",
            subtitle = if (vm.isSaved) "Saved in Library" else "Not saved yet",
        ) {
            IconButton(onClick = { showPresets = true }) { Icon(Icons.Rounded.AutoAwesome, "Presets") }
            IconButton(onClick = { vm.newDraft() }) { Icon(Icons.AutoMirrored.Rounded.NoteAdd, "New") }
            IconButton(onClick = { vm.saveDraft(); vm.flash("Saved to Library") }) { Icon(Icons.Rounded.Save, "Save") }
        }

        PreviewStage(vm, model, compact = imeVisible, onOverflow = { if (truncated != it) truncated = it })

        Column(
            Modifier
                .weight(1f)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            SectionHeader("Sender")
            GlassCard(onClick = { showSenders = true }, padding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(sender, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(sender.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            when (sender.source) {
                                SenderSource.DEFAULT -> "Default · tap to choose a custom sender"
                                SenderSource.CUSTOM -> "Custom sender"
                                SenderSource.APP -> "From app · ${sender.packageName?.let { InstalledApps.label(ctx, it) } ?: sender.packageName}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            SectionHeader("Content")
            OutlinedTextField(
                value = draft.title,
                onValueChange = { v -> vm.updateDraft { it.copy(title = v) } },
                label = { Text("Title") },
                singleLine = true,
                supportingText = { Text("${draft.title.length} / 50", color = if (draft.title.length > 50) MaterialTheme.colorScheme.error else Color.Unspecified) },
                modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) lastField = Field.TITLE },
                shape = RoundedCornerShape(14.dp),
            )
            OutlinedTextField(
                value = draft.body,
                onValueChange = { v -> vm.updateDraft { it.copy(body = v) } },
                label = { Text("Message") },
                minLines = 3,
                maxLines = 8,
                supportingText = {
                    Text(
                        if (truncated) "${draft.body.length} chars · cut off in this view" else "${draft.body.length} chars",
                        color = if (truncated) MaterialTheme.colorScheme.tertiary else Color.Unspecified,
                    )
                },
                modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) lastField = Field.BODY },
                shape = RoundedCornerShape(14.dp),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TemplateVars.chips.forEach { chip ->
                    AssistChip(
                        onClick = {
                            vm.updateDraft {
                                if (lastField == Field.TITLE) it.copy(title = it.title + chip) else it.copy(body = it.body + chip)
                            }
                        },
                        label = { Text(chip, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }

            SectionHeader("Style")
            Segmented(
                options = NotifStyle.entries,
                selected = draft.style,
                label = { it.label },
                onSelect = { s -> vm.updateDraft { it.copy(style = s) } },
                modifier = Modifier.fillMaxWidth(),
            )
            if (draft.style == NotifStyle.BIG_PICTURE) {
                Spacer(Modifier.height(10.dp))
                val pic = rememberImage(draft.imagePath)
                GlassCard(padding = androidx.compose.foundation.layout.PaddingValues(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                            if (pic != null) Image(pic, null, Modifier.size(56.dp), contentScale = ContentScale.Crop)
                            else Icon(Icons.Rounded.AddPhotoAlternate, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(if (pic != null) "Shown when expanded" else "Pick an image to show when expanded", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                            Text(if (pic != null) "Change" else "Choose")
                        }
                        if (pic != null) IconButton(onClick = { vm.updateDraft { it.copy(imagePath = null) } }) { Icon(Icons.Rounded.Close, "Remove image") }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Delivery", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Segmented(
                    options = Importance.entries,
                    selected = draft.importance,
                    label = { it.label },
                    onSelect = { i -> vm.updateDraft { it.copy(importance = i) } },
                    modifier = Modifier.width(200.dp),
                )
            }
            Text(
                if (draft.importance == Importance.BANNER) "Pops up on screen with sound" else "Silent, appears only in the notification shade",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, start = 2.dp),
            )

            SectionHeader("Buttons", trailing = "${draft.actions.size} / $MAX_ACTIONS")
            if (draft.actions.isNotEmpty()) {
                GlassCard(padding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
                    draft.actions.forEach { a ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { editing = a; editingIsNew = false },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Icon(a.kind.icon(), null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(a.label.ifBlank { a.kind.defaultLabel }, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text(
                                        a.kind.label + (if (a.opensUi) " · opens app" else "") + (if (a.hasInput) " · text box" else ""),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            OutlinedButton(
                onClick = { editing = NotifAction(); editingIsNew = true },
                enabled = draft.actions.size < MAX_ACTIONS,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(8.dp))
                Text(if (draft.actions.size < MAX_ACTIONS) "Add button" else "Android shows up to 3 buttons")
            }

            SectionHeader("Send", trailing = if (schedules > 0) "$schedules scheduled" else null)
            if (!Poster.canPost(ctx) && Build.VERSION.SDK_INT < 33) {
                InfoBanner(Icons.Rounded.Warning, "Notifications are turned off for Pushcraft.", tint = MaterialTheme.colorScheme.error,
                    actionLabel = "Turn on", onAction = { Perms.openNotificationSettings(ctx) })
                Spacer(Modifier.height(8.dp))
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0L to "Now", 5_000L to "In 5s", 10_000L to "In 10s", 30_000L to "In 30s", 60_000L to "In 1 min").forEach { (ms, label) ->
                    FilterChip(selected = delayMs == ms, onClick = { delayMs = ms }, label = { Text(label) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); withPermission { vm.send(delayMs) } },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (delayMs == 0L) "Send now" else "Send ${delayLabel(delayMs)}")
                }
                OutlinedButton(
                    onClick = { withPermission { showSchedule = true } },
                    modifier = Modifier.height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Rounded.Event, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Schedule")
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); withPermission { showSpam = true } },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Rounded.Bolt, null)
                Spacer(Modifier.width(8.dp))
                Text("Spam this notification")
            }
            if (schedules > 0) {
                TextButton(onClick = { vm.tab = AppTab.SCHEDULED }) { Text("See scheduled ($schedules)") }
            }
            Spacer(Modifier.height(bottomSpace()))
        }
    }

    if (showSenders) {
        SenderSheet(
            vm = vm,
            data = data,
            selectedId = draft.senderId,
            onSelect = { id -> vm.updateDraft { it.copy(senderId = id) }; showSenders = false },
            onDismiss = { showSenders = false },
        )
    }
    editing?.let { action ->
        ActionSheet(
            initial = action,
            isNew = editingIsNew,
            draftTitle = model.title,
            draftBody = model.body,
            vars = vars,
            onSave = { saved ->
                vm.updateDraft { t ->
                    val list = if (editingIsNew) t.actions + saved else t.actions.map { if (it.id == saved.id) saved else it }
                    t.copy(actions = list)
                }
                editing = null
            },
            onDelete = {
                vm.updateDraft { t -> t.copy(actions = t.actions.filterNot { it.id == action.id }) }
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
    if (showSchedule) {
        ScheduleSheet(
            onSave = { firstAt, repeat, interval ->
                val t = vm.saveDraft()
                app.pushcraft.notify.Scheduler.save(
                    ctx, app.pushcraft.data.Schedule(templateId = t.id, firstAt = firstAt, repeat = repeat, intervalMinutes = interval)
                )
                showSchedule = false
                vm.flash("Scheduled")
            },
            onDismiss = { showSchedule = false },
        )
    }
    if (showPresets) {
        PresetSheet(
            onPick = { preset -> vm.applyPreset(preset); showPresets = false; vm.flash("Loaded \"${preset.name}\"") },
            onDismiss = { showPresets = false },
        )
    }
    if (showSpam) {
        SpamSheet(
            initial = data.spam,
            onStart = { config -> showSpam = false; vm.startSpam(config) },
            onDismiss = { showSpam = false },
        )
    }
}

private fun delayLabel(ms: Long) = if (ms >= 60_000) "in ${ms / 60_000} min" else "in ${ms / 1000}s"
