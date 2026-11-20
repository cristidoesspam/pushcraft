package app.pushcraft.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.pushcraft.data.AppData
import app.pushcraft.notify.Poster
import app.pushcraft.notify.Scheduler
import app.pushcraft.ui.AppGlyph
import app.pushcraft.ui.GlassCard
import app.pushcraft.ui.MainViewModel
import app.pushcraft.ui.bottomSpace
import app.pushcraft.ui.ScreenTitle
import app.pushcraft.ui.SectionHeader
import app.pushcraft.ui.Segmented

object Perms {
    private fun open(ctx: Context, intent: Intent) {
        runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure {
                runCatching {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
    }

    fun openNotificationSettings(ctx: Context) =
        open(ctx, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))

    fun openExactAlarmSettings(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 31) {
            open(ctx, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
        }
    }

    fun openBatterySettings(ctx: Context) = open(ctx, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
}

@Composable
fun SettingsScreen(vm: MainViewModel, data: AppData) {
    val ctx = LocalContext.current
    var notifications by remember { mutableStateOf(Poster.canPost(ctx)) }
    var exact by remember { mutableStateOf(Scheduler.canExact(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notifications = Poster.canPost(ctx)
        exact = Scheduler.canExact(ctx)
    }
    var themeMode by remember(data.themeMode) { mutableIntStateOf(data.themeMode) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenTitle("Settings")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionHeader("Appearance")
            Segmented(
                options = listOf(0, 1, 2),
                selected = themeMode,
                label = { listOf("System", "Light", "Dark")[it] },
                onSelect = { themeMode = it; vm.repo.setThemeMode(it) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (Build.VERSION.SDK_INT >= 31) {
                Spacer(Modifier.height(8.dp))
                GlassCard(padding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Wallpaper colors", style = MaterialTheme.typography.bodyLarge)
                            Text("Tint Pushcraft from your Android theme", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = data.dynamicColor, onCheckedChange = { vm.repo.setDynamicColor(it) })
                    }
                }
            }

            SectionHeader("Permissions")
            GlassCard(padding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
                PermRow(
                    Icons.Rounded.Notifications, "Notifications",
                    if (notifications) "Allowed" else "Blocked: nothing will appear", notifications,
                    action = if (notifications) "Manage" else "Allow",
                ) { Perms.openNotificationSettings(ctx) }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                PermRow(
                    Icons.Rounded.Alarm, "Exact alarms",
                    if (exact) "Scheduled notifications arrive on the minute" else "Off: schedules may drift a few minutes", exact,
                    action = if (Build.VERSION.SDK_INT >= 31) (if (exact) "Manage" else "Allow") else null,
                ) { Perms.openExactAlarmSettings(ctx) }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                PermRow(
                    Icons.Rounded.BatteryChargingFull, "Battery optimization",
                    "Some phones delay alarms for optimized apps. Exempt Pushcraft if schedules arrive late.", null,
                    action = "Open",
                ) { Perms.openBatterySettings(ctx) }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                PermRow(
                    Icons.Rounded.NotificationsActive, "Send a test",
                    "Post a sample notification to check it all works", null,
                    action = "Test",
                ) { vm.sendTestNotification() }
            }

            SectionHeader("About")
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppGlyph(48.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Pushcraft", style = MaterialTheme.typography.titleMedium)
                        Text("Version 1.2 · mock notification studio", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(14.dp))
                AboutLine(Icons.Rounded.Shield, "Android always names the app that posted a notification, so the header reads \"Pushcraft\". Your sender appears as the large icon, the text beside the app name, and the chat avatar.")
                AboutLine(Icons.Rounded.Code, "Scripts run on the device in a sandbox: no network, files or device access, 500 ms and 10 effects per tap.")
            }

            SectionHeader("Data")
            GlassCard(padding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Clear shown notifications", modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.clearShownNotifications() }) { Text("Clear") }
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Activity log · ${data.log.size} entries", modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.repo.clearLog(); vm.flash("Activity cleared") }) { Text("Clear") }
                }
            }
            Spacer(Modifier.height(bottomSpace()))
        }
    }
}

@Composable
private fun PermRow(icon: ImageVector, title: String, status: String, ok: Boolean?, action: String?, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon, null,
            tint = when (ok) { true -> Color(0xFF22C55E); false -> MaterialTheme.colorScheme.error; null -> MaterialTheme.colorScheme.onSurfaceVariant },
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun AboutLine(icon: ImageVector, text: String) {
    Row(Modifier.padding(vertical = 6.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp).padding(top = 2.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
