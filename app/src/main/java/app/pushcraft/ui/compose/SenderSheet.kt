package app.pushcraft.ui.compose

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pushcraft.data.AppData
import app.pushcraft.data.AppInfo
import app.pushcraft.data.Images
import app.pushcraft.data.InstalledApps
import app.pushcraft.data.Sender
import app.pushcraft.data.SenderSource
import app.pushcraft.data.newId
import app.pushcraft.ui.AppIcon
import app.pushcraft.ui.Avatar
import app.pushcraft.ui.MainViewModel
import app.pushcraft.ui.rememberImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val AccentChoices = listOf(
    0xFF5B5BF6, 0xFF7C3AED, 0xFFDB2777, 0xFFE11D48, 0xFFEA580C,
    0xFFCA8A04, 0xFF16A34A, 0xFF0891B2, 0xFF2563EB, 0xFF8B5E3C, 0xFF334155,
).map { it.toInt() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SenderSheet(
    vm: MainViewModel,
    data: AppData,
    selectedId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            Text("Choose sender", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
            Text(
                "Android still labels the notification \"Pushcraft\". The sender shows as the icon and name beside it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            PrimaryTabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
                listOf("Saved", "Custom", "Installed apps").forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
                }
            }
            when (tab) {
                0 -> SavedSenders(vm, data, selectedId, onSelect)
                1 -> CustomSender(vm, onCreated = onSelect)
                else -> AppSenders(vm, onCreated = onSelect)
            }
        }
    }
}

@Composable
private fun SavedSenders(vm: MainViewModel, data: AppData, selectedId: String, onSelect: (String) -> Unit) {
    val all = listOf(Sender.Default) + data.senders
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        items(all, key = { it.id }) { s ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onSelect(s.id) }
                    .background(if (s.id == selectedId) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(s, 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        when (s.source) {
                            SenderSource.DEFAULT -> "Pushcraft's own icon"
                            SenderSource.CUSTOM -> "Custom"
                            SenderSource.APP -> "Copied from ${s.packageName}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (s.id == selectedId) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                if (s.source != SenderSource.DEFAULT) {
                    IconButton(onClick = {
                        vm.repo.deleteSender(s.id)
                        if (s.id == selectedId) onSelect(Sender.Default.id)
                    }) { Icon(Icons.Rounded.DeleteOutline, "Delete sender") }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomSender(vm: MainViewModel, onCreated: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var iconPath by remember { mutableStateOf<String?>(null) }
    var monogram by remember { mutableStateOf("") }
    var accent by remember { mutableIntStateOf(AccentChoices[0]) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val path = withContext(Dispatchers.IO) { Images.avatarFromUri(ctx, uri) }
            if (path != null) {
                iconPath = path
                val bmp = Images.load(path)
                if (bmp != null) accent = Images.accentOf(bmp)
            } else vm.flash("Couldn't read that image")
        }
    }
    val icon = rememberImage(iconPath)

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(84.dp).clip(CircleShape).background(Color(accent)).border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    icon != null -> Image(icon, null, Modifier.size(84.dp), contentScale = ContentScale.Crop)
                    monogram.isNotBlank() -> Text(monogram, color = Color.White, fontSize = 34.sp, textAlign = TextAlign.Center)
                    else -> Icon(Icons.Rounded.Photo, null, tint = Color.White)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (icon != null) "Change photo" else "Choose photo") }
                OutlinedTextField(
                    value = monogram,
                    onValueChange = { v -> monogram = v.take(4); if (v.isNotBlank()) iconPath = null },
                    label = { Text("…or emoji / initials") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(40) },
            label = { Text("Sender name") },
            placeholder = { Text("e.g. Coffee Club") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Text("Accent color", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AccentChoices.forEach { c ->
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(c))
                        .then(if (c == accent) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                        .clickable { accent = c }
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                val path = iconPath ?: monogram.takeIf { it.isNotBlank() }?.let { Images.monogram(ctx, it, accent) }
                val s = Sender(id = newId(), name = name.trim().ifBlank { "Custom sender" }, iconPath = path, accent = accent)
                vm.repo.upsertSender(s)
                onCreated(s.id)
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) { Text("Save and use") }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun AppSenders(vm: MainViewModel, onCreated: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val apps by produceState<List<AppInfo>?>(null) {
        value = withContext(Dispatchers.IO) { InstalledApps.list(ctx) }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            placeholder = { Text("Search apps") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            shape = RoundedCornerShape(16.dp),
        )
        val list = apps
        if (list == null || busy) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            val filtered = list.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(78.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                busy = true
                                scope.launch {
                                    val path = withContext(Dispatchers.IO) { InstalledApps.exportIcon(ctx, app.packageName) }
                                    val accent = path?.let { Images.load(it) }?.let { Images.accentOf(it) } ?: AccentChoices[0]
                                    val s = Sender(
                                        id = newId(), name = app.label, iconPath = path,
                                        source = SenderSource.APP, packageName = app.packageName, accent = accent,
                                    )
                                    vm.repo.upsertSender(s)
                                    busy = false
                                    onCreated(s.id)
                                }
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AppIcon(app.packageName, 48.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(app.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}
