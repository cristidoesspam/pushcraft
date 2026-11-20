package app.pushcraft.notify

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.RemoteInput
import app.pushcraft.data.ActionKind
import app.pushcraft.data.InstalledApps
import app.pushcraft.data.LogKind
import app.pushcraft.data.Repository
import app.pushcraft.data.Template
import app.pushcraft.data.TemplateVars
import app.pushcraft.script.Effect
import app.pushcraft.script.ScriptEvent
import app.pushcraft.script.ScriptHost

object ActionHandler {

    fun handle(ctx: Context, intent: Intent): Intent? {
        val repo = Repository.get(ctx)
        val notifId = intent.getIntExtra(EXTRA_NOTIF, -1)
        val t = intent.getStringExtra(EXTRA_TEMPLATE)?.let { repo.template(it) } ?: return null
        val a = t.actions.find { it.id == intent.getStringExtra(EXTRA_ACTION) } ?: return null
        val input = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Poster.KEY_REPLY)?.toString()
        val taps = repo.increment("tap:${t.id}:${a.id}")
        Poster.cancel(ctx, notifId)

        val label = a.label.ifBlank { a.kind.defaultLabel }
        if (input != null) repo.log(LogKind.REPLY, t.id, "$label: \"$input\"")
        else repo.log(LogKind.TAP, t.id, "Tapped \"$label\"")

        return when (a.kind) {
            ActionKind.OPEN_URL -> viewIntent(a.target)
            ActionKind.OPEN_APP -> InstalledApps.launchIntent(ctx, a.target)
            ActionKind.SNOOZE -> { Scheduler.sendLater(ctx, t.id, a.snoozeMinutes * 60_000L); null }
            ActionKind.REPLY, ActionKind.DISMISS -> null
            ActionKind.SCRIPT -> {
                val now = System.currentTimeMillis()
                val count = repo.counter("sent:${t.id}")
                val vars = repo.vars(t.id)
                val result = ScriptHost.run(
                    a.script,
                    ScriptEvent(
                        input = input,
                        tapCount = taps,
                        action = label,
                        title = TemplateVars.render(t.title, now, vars, count),
                        body = TemplateVars.render(t.body, now, vars, count),
                        firedAt = now,
                    ),
                    vars,
                )
                result.error?.let { e ->
                    repo.log(LogKind.ERROR, t.id, "Script \"$label\" line ${e.line}: ${e.message}")
                    return null
                }
                if (result.vars != vars) repo.setVars(t.id, result.vars)
                result.logs.take(5).forEach { repo.log(LogKind.SCRIPT, t.id, it) }
                applyEffects(ctx, t, result.effects)
            }
        }
    }

    private fun applyEffects(ctx: Context, t: Template, effects: List<Effect>): Intent? {
        var launch: Intent? = null
        for (e in effects) {
            when (e) {
                is Effect.Notify -> Poster.post(ctx, t, titleOverride = e.title, bodyOverride = e.body, withActions = false)
                is Effect.Snooze -> Scheduler.sendLater(ctx, t.id, e.minutes * 60_000L)
                is Effect.OpenUrl -> if (launch == null) launch = viewIntent(e.url)
                is Effect.OpenApp -> if (launch == null) launch = InstalledApps.launchIntent(ctx, e.pkg)
            }
        }
        return launch
    }

    fun viewIntent(raw: String): Intent? {
        if (raw.isBlank()) return null
        val url = ScriptHost.normalizeUrl(raw)
        if (!ScriptHost.isAllowedUrl(url)) return null
        return Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
