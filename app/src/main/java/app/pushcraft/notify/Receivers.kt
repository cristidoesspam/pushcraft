package app.pushcraft.notify

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import app.pushcraft.data.Repository

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FIRE_SCHEDULE -> intent.getStringExtra(EXTRA_SCHEDULE)?.let { Scheduler.fire(ctx, it) }
            ACTION_FIRE_ONESHOT -> intent.getStringExtra(EXTRA_TEMPLATE)
                ?.let { Repository.get(ctx).template(it) }
                ?.let { Poster.post(ctx, it) }
        }
    }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val launch = ActionHandler.handle(ctx, intent) ?: return
        val t = intent.getStringExtra(EXTRA_TEMPLATE)?.let { Repository.get(ctx).template(it) } ?: return
        Poster.postLaunchOffer(ctx, t, launch, launch.dataString ?: launch.`package` ?: "Open")
    }
}

class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Scheduler.rearmAll(ctx)
    }
}

class ActionTrampolineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launch = ActionHandler.handle(this, intent)
        if (launch != null) {
            try {
                startActivity(launch)
            } catch (e: Exception) {
                Toast.makeText(this, "No app can open that", Toast.LENGTH_SHORT).show()
            }
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
