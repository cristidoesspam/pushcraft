package app.pushcraft

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pushcraft.notify.EXTRA_TEMPLATE
import app.pushcraft.notify.Scheduler
import app.pushcraft.ui.AppRoot
import app.pushcraft.ui.MainViewModel
import app.pushcraft.ui.theme.PushcraftTheme

class MainActivity : ComponentActivity() {
    private lateinit var vm: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm = ViewModelProvider(this)[MainViewModel::class.java]
        if (savedInstanceState == null) {
            Scheduler.rearmAll(this)
            handleIntent(intent)
        }
        setContent {
            val data by vm.repo.state.collectAsStateWithLifecycle()
            val dark = when (data.themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            PushcraftTheme(dark = dark, dynamic = data.dynamicColor) {
                AppRoot(vm, data)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val id = intent?.getStringExtra(EXTRA_TEMPLATE) ?: return
        vm.repo.template(id)?.let { vm.edit(it) }
    }
}
