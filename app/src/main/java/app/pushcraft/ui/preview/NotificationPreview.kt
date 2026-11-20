package app.pushcraft.ui.preview

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pushcraft.data.NotifAction
import app.pushcraft.data.NotifStyle
import app.pushcraft.data.Sender
import app.pushcraft.data.SenderSource
import app.pushcraft.ui.AppGlyph
import app.pushcraft.ui.MainViewModel
import app.pushcraft.ui.PreviewPlatform
import app.pushcraft.ui.PreviewSurface
import app.pushcraft.ui.Segmented
import app.pushcraft.ui.rememberImage
import app.pushcraft.ui.theme.LocalGlass
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class PreviewModel(
    val title: String,
    val body: String,
    val sender: Sender,
    val style: NotifStyle,
    val imagePath: String?,
    val actions: List<NotifAction>,
)

private val wallpapers = listOf(
    listOf(Color(0xFF4F46E5), Color(0xFF9333EA), Color(0xFFEC4899)),
    listOf(Color(0xFFF97316), Color(0xFFEC4899), Color(0xFF8B5CF6)),
    listOf(Color(0xFF0EA5E9), Color(0xFF14B8A6), Color(0xFF1E3A8A)),
    listOf(Color(0xFF475569), Color(0xFF0F172A), Color(0xFF334155)),
)

private const val BODY_PLACEHOLDER = "Your message appears here as you type."
private const val TITLE_PLACEHOLDER = "Notification title"

@Composable
fun PreviewStage(vm: MainViewModel, model: PreviewModel, compact: Boolean, onOverflow: (Boolean) -> Unit) {
    val dark = vm.previewDark ?: LocalGlass.current.dark
    val collapsed = compact || vm.previewCollapsed
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Segmented(
                options = PreviewPlatform.entries,
                selected = vm.previewPlatform,
                label = { it.label },
                onSelect = { vm.previewPlatform = it },
                modifier = Modifier.width(176.dp).height(40.dp),
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { vm.previewDark = !dark }) {
                Icon(if (dark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode, "Toggle preview theme")
            }
            IconButton(onClick = { vm.wallpaper = (vm.wallpaper + 1) % wallpapers.size }) {
                Icon(Icons.Rounded.Palette, "Change wallpaper")
            }
            IconButton(onClick = { vm.previewCollapsed = !vm.previewCollapsed }, enabled = !compact) {
                Icon(if (collapsed) Icons.Rounded.UnfoldMore else Icons.Rounded.UnfoldLess, "Collapse preview")
            }
        }
        Spacer(Modifier.height(6.dp))

        val surface = vm.previewSurface
        val colors = wallpapers[vm.wallpaper % wallpapers.size]
        Box(
            Modifier
                .fillMaxWidth()
                .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(colors))
                .background(if (dark) Color.Black.copy(alpha = 0.35f) else Color.Transparent)
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(28.dp))
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = if (collapsed) 12.dp else 8.dp)) {
                if (!collapsed) {
                    if (surface == PreviewSurface.LOCK) LockClock(vm.previewPlatform) else StatusBar()
                }
                val expanded = surface == PreviewSurface.EXPANDED && !collapsed
                when (vm.previewPlatform) {
                    PreviewPlatform.ANDROID -> AndroidCard(model, expanded, dark, onOverflow)
                    PreviewPlatform.IOS -> IosCard(model, expanded, dark, onOverflow)
                }
                if (!collapsed && surface != PreviewSurface.EXPANDED) Spacer(Modifier.height(8.dp))
            }
        }

        if (!collapsed) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PreviewSurface.entries.forEach { s ->
                    FilterChip(
                        selected = s == surface,
                        onClick = { vm.previewSurface = s },
                        label = { Text(s.label) },
                        colors = FilterChipDefaults.filterChipColors(),
                    )
                }
                Text(
                    if (vm.previewPlatform == PreviewPlatform.IOS) "iOS mockup · preview only" else "As Android will show it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusBar() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("9:41", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        Icon(Icons.Rounded.SignalCellularAlt, null, tint = Color.White, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Rounded.Wifi, null, tint = Color.White, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Rounded.BatteryFull, null, tint = Color.White, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun LockClock(platform: PreviewPlatform) {
    val date = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d")) }
    Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (platform == PreviewPlatform.IOS) {
            Text(date, color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("9:41", color = Color.White, fontSize = 62.sp, fontWeight = FontWeight.Bold, letterSpacing = (-2).sp, lineHeight = 66.sp)
        } else {
            Text("9:41", color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Light, lineHeight = 60.sp)
            Text(date, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
        }
    }
}

@Composable
private fun AndroidCard(m: PreviewModel, expanded: Boolean, dark: Boolean, onOverflow: (Boolean) -> Unit) {
    val bg = if (dark) Color(0xFF211F26) else Color(0xFFF7F2FA)
    val fg = if (dark) Color(0xFFE6E0E9) else Color(0xFF1D1B20)
    val sub = fg.copy(alpha = 0.66f)
    val accent = Color(m.sender.accent)
    val actionColor = if (dark) lerp(accent, Color.White, 0.45f) else lerp(accent, Color.Black, 0.1f)
    val avatar = rememberImage(m.sender.iconPath)
    val picture = rememberImage(m.imagePath)
    val messaging = m.style == NotifStyle.MESSAGING
    val showPicture = expanded && m.style == NotifStyle.BIG_PICTURE && picture != null
    val bodyLines = when {
        !expanded -> 1
        m.style == NotifStyle.BIG_TEXT || messaging -> 8
        else -> 1
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            if (messaging && avatar != null) {
                Image(avatar, null, Modifier.size(32.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.size(32.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Notifications, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val header = buildString {
                        append("Pushcraft")
                        if (m.sender.source != SenderSource.DEFAULT) append(" • ").append(m.sender.name)
                        append(" • now")
                    }
                    Text(header, fontSize = 12.sp, color = sub, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = sub, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    if (messaging) m.sender.name else m.title.ifBlank { TITLE_PLACEHOLDER },
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = fg,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    m.body.ifBlank { BODY_PLACEHOLDER },
                    fontSize = 14.sp, lineHeight = 19.sp, color = fg.copy(alpha = 0.86f),
                    maxLines = bodyLines, overflow = TextOverflow.Ellipsis,
                    onTextLayout = { onOverflow(it.hasVisualOverflow) },
                )
            }
            if (!messaging && avatar != null && !showPicture) {
                Spacer(Modifier.width(10.dp))
                Image(avatar, null, Modifier.size(40.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            }
        }
        if (showPicture) {
            Spacer(Modifier.height(10.dp))
            Image(
                picture, null,
                Modifier.fillMaxWidth().heightIn(max = 190.dp).clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop,
            )
        }
        if (expanded && m.actions.isNotEmpty()) {
            Row(Modifier.padding(start = 42.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                m.actions.take(3).forEach {
                    Text(it.label.ifBlank { it.kind.defaultLabel }, color = actionColor, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun IosCard(m: PreviewModel, expanded: Boolean, dark: Boolean, onOverflow: (Boolean) -> Unit) {
    val bg = if (dark) Color(0xFF1C1C1E).copy(alpha = 0.8f) else Color.White.copy(alpha = 0.8f)
    val fg = if (dark) Color.White else Color.Black
    val sub = fg.copy(alpha = 0.5f)
    val avatar = rememberImage(m.sender.iconPath)
    val picture = rememberImage(m.imagePath)
    val messaging = m.style == NotifStyle.MESSAGING
    val hasPicture = m.style == NotifStyle.BIG_PICTURE && picture != null

    Column {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(bg)) {
            if (expanded && hasPicture) {
                Image(picture, null, Modifier.fillMaxWidth().heightIn(max = 200.dp), contentScale = ContentScale.Crop)
            }
            Row(Modifier.padding(12.dp)) {
                Box(Modifier.size(38.dp)) {
                    if (messaging) {
                        if (avatar != null) Image(avatar, null, Modifier.size(38.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                        else AppGlyph(38.dp, shape = CircleShape)
                        Box(Modifier.align(Alignment.BottomEnd).offset(x = 3.dp, y = 3.dp)) {
                            AppGlyph(16.dp, corner = 4.dp)
                        }
                    } else if (avatar != null) {
                        Image(avatar, null, Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)), contentScale = ContentScale.Crop)
                    } else {
                        AppGlyph(38.dp, corner = 9.dp)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (messaging) m.sender.name else m.title.ifBlank { m.sender.name },
                            fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = fg,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        Text("now", fontSize = 13.sp, color = sub)
                    }
                    Text(
                        m.body.ifBlank { BODY_PLACEHOLDER },
                        fontSize = 15.sp, lineHeight = 19.sp, color = fg,
                        maxLines = if (expanded) 12 else 4, overflow = TextOverflow.Ellipsis,
                        onTextLayout = { onOverflow(it.hasVisualOverflow) },
                    )
                }
                if (!expanded && hasPicture) {
                    Spacer(Modifier.width(10.dp))
                    Image(picture, null, Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                }
            }
        }
        if (expanded && m.actions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(bg)) {
                m.actions.take(3).forEachIndexed { i, a ->
                    if (i > 0) HorizontalDivider(color = fg.copy(alpha = 0.12f))
                    Text(
                        a.label.ifBlank { a.kind.defaultLabel },
                        color = if (a.destructive) Color(0xFFFF453A) else fg,
                        fontSize = 15.sp,
                        modifier = Modifier.fillMaxWidth().clickable { }.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}
