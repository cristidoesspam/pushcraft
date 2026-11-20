package app.pushcraft.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.pushcraft.data.AppData
import app.pushcraft.ui.compose.ComposerScreen
import app.pushcraft.ui.library.LibraryScreen
import app.pushcraft.ui.schedule.ScheduledScreen
import app.pushcraft.ui.settings.SettingsScreen
import app.pushcraft.ui.theme.LocalGlass

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppRoot(vm: MainViewModel, data: AppData) {
    BackHandler(enabled = vm.tab != AppTab.CREATE) { vm.tab = AppTab.CREATE }
    AuroraBackground {
        AnimatedContent(
            targetState = vm.tab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "tab",
        ) { tab ->
            when (tab) {
                AppTab.CREATE -> ComposerScreen(vm, data)
                AppTab.LIBRARY -> LibraryScreen(vm, data)
                AppTab.SCHEDULED -> ScheduledScreen(vm, data)
                AppTab.SETTINGS -> SettingsScreen(vm, data)
            }
        }

        if (!WindowInsets.isImeVisible) {
            GlassNavBar(vm.tab, { vm.tab = it }, Modifier.align(Alignment.BottomCenter))
        }

        AnimatedVisibility(
            visible = vm.burst != null,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp, start = 16.dp, end = 16.dp),
        ) {
            val b = vm.burst
            val glass = LocalGlass.current
            val shape = RoundedCornerShape(24.dp)
            Row(
                Modifier
                    .clip(shape)
                    .background(glass.fillStrong)
                    .border(1.dp, glass.stroke, shape)
                    .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(b?.label ?: "", style = MaterialTheme.typography.labelLarge)
                    Text("${b?.done ?: 0} / ${b?.total ?: 0}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(16.dp))
                FilledTonalButton(onClick = { vm.stopBurst() }, colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)) {
                    Icon(Icons.Rounded.Stop, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Stop")
                }
            }
        }

        AnimatedVisibility(
            visible = vm.message != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 92.dp, start = 24.dp, end = 24.dp),
        ) {
            val glass = LocalGlass.current
            val action = vm.messageAction
            Row(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.95f))
                    .border(1.dp, glass.stroke, RoundedCornerShape(20.dp))
                    .padding(start = 18.dp, end = if (action != null) 6.dp else 18.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    vm.message ?: "",
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                if (action != null) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { vm.runMessageAction() }) {
                        Text(action.first, color = MaterialTheme.colorScheme.inversePrimary)
                    }
                }
            }
        }
    }
}
