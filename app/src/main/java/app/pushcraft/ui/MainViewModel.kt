package app.pushcraft.ui

import android.app.Application
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pushcraft.data.NotifStyle
import app.pushcraft.data.Preset
import app.pushcraft.data.Sender
import app.pushcraft.data.PresetCatalog
import app.pushcraft.data.Repository
import app.pushcraft.data.SeqStep
import app.pushcraft.data.Sequence
import app.pushcraft.data.SpamConfig
import app.pushcraft.data.SpamMode
import app.pushcraft.data.Template
import app.pushcraft.data.newId
import app.pushcraft.notify.Poster
import app.pushcraft.notify.Scheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AppTab(val label: String, val icon: ImageVector) {
    CREATE("Create", Icons.Rounded.AddCircle),
    LIBRARY("Library", Icons.Rounded.Inventory2),
    SCHEDULED("Scheduled", Icons.Rounded.Schedule),
    SETTINGS("Settings", Icons.Rounded.Tune),
}

enum class PreviewPlatform(val label: String) { ANDROID("Android"), IOS("iOS") }
enum class PreviewSurface(val label: String) { BANNER("Banner"), LOCK("Lock screen"), EXPANDED("Expanded") }

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val repo = Repository.get(app)
    private val ctx get() = getApplication<Application>()

    var tab by mutableStateOf(AppTab.CREATE)
    var draft by mutableStateOf(Template())
        private set

    var previewPlatform by mutableStateOf(PreviewPlatform.ANDROID)
    var previewSurface by mutableStateOf(PreviewSurface.BANNER)
    var previewDark by mutableStateOf<Boolean?>(null)
    var wallpaper by mutableIntStateOf(0)
    var previewCollapsed by mutableStateOf(false)

    var message by mutableStateOf<String?>(null)
        private set
    private var messageJob: Job? = null

    val isSaved: Boolean get() = repo.data.templates.any { it.id == draft.id }

    fun updateDraft(fn: (Template) -> Template) {
        draft = fn(draft)
    }

    fun edit(t: Template) {
        draft = t
        tab = AppTab.CREATE
    }

    fun newDraft() {
        draft = Template(style = NotifStyle.MESSAGING)
    }

    fun saveDraft(): Template {
        val t = draft.copy(updatedAt = System.currentTimeMillis())
        repo.upsertTemplate(t)
        draft = t
        return t
    }

    fun send(delayMs: Long) {
        val t = saveDraft()
        if (delayMs <= 0) {
            Poster.post(ctx, t)
            flash("Sent")
        } else {
            Scheduler.sendLater(ctx, t.id, delayMs)
            flash("Arrives in ${delayMs / 1000}s. Lock your phone to see the lock screen")
        }
    }

    fun sendTemplate(t: Template) {
        Poster.post(ctx, t)
        flash("Sent")
    }

    fun duplicate(t: Template) {
        repo.upsertTemplate(t.copy(id = newId(), actions = t.actions.map { it.copy(id = newId()) }, updatedAt = System.currentTimeMillis()))
        flash("Duplicated")
    }

    fun deleteTemplate(t: Template) {
        val schedules = repo.data.schedules.filter { it.templateId == t.id }
        schedules.forEach { Scheduler.cancel(ctx, it.id) }
        repo.deleteTemplate(t.id)
        if (draft.id == t.id) newDraft()
        flash("Deleted \"${t.title.ifBlank { "notification" }}\"", "Undo") {
            repo.upsertTemplate(t)
            schedules.forEach { Scheduler.save(ctx, it) }
        }
    }

    fun clearShownNotifications() {
        NotificationManagerCompat.from(ctx).cancelAll()
        flash("Cleared shown notifications")
    }

    fun sendTestNotification() {
        Poster.postCustom(
            ctx,
            Sender.Default,
            "Test notification",
            "If you can see this, notifications are working ✅",
            NotifStyle.STANDARD,
        )
        flash("Test sent")
    }

    fun applyPreset(preset: Preset) {
        edit(PresetCatalog.apply(ctx, repo, preset))
    }

    var burst by mutableStateOf<BurstState?>(null)
        private set
    private var burstJob: Job? = null

    data class BurstState(val done: Int, val total: Int, val label: String)

    fun startSpam(config: SpamConfig) {
        val t = saveDraft()
        repo.setSpam(config)
        val count = config.count.coerceIn(1, app.pushcraft.data.SPAM_MAX)
        val variants = config.variants.map { it.trim() }.filter { it.isNotEmpty() }
        burstJob?.cancel()
        burst = BurstState(0, count, "Spamming")
        burstJob = viewModelScope.launch {
            for (i in 0 until count) {
                ensureActive()
                if (config.mode == SpamMode.CYCLE && variants.isNotEmpty())
                    Poster.post(ctx, t, bodyOverride = variants[i % variants.size])
                else
                    Poster.post(ctx, t)
                burst = BurstState(i + 1, count, "Spamming")
                if (i < count - 1 && isActive) delay(config.intervalMs.coerceIn(0, 10_000))
            }
            burst = null
            flash("Sent $count notifications")
        }
    }

    fun playSequence(seq: Sequence) {
        val steps = seq.steps.filter { it.body.isNotBlank() || it.title.isNotBlank() }
        if (steps.isEmpty()) {
            flash("This sequence has no messages")
            return
        }
        burstJob?.cancel()
        burst = BurstState(0, steps.size, seq.name.ifBlank { "Sequence" })
        burstJob = viewModelScope.launch {
            steps.forEachIndexed { i, step ->
                ensureActive()
                if (step.delayMs > 0) delay(step.delayMs.coerceIn(0, 60_000))
                ensureActive()
                val sender = repo.sender(step.senderId)
                Poster.postCustom(ctx, sender, step.title, step.body, step.style, step.importance)
                burst = BurstState(i + 1, steps.size, seq.name.ifBlank { "Sequence" })
            }
            burst = null
            flash("Played \"${seq.name.ifBlank { "sequence" }}\"")
        }
    }

    fun stopBurst() {
        burstJob?.cancel()
        burstJob = null
        burst = null
        flash("Stopped")
    }

    fun saveSequence(s: Sequence): Sequence {
        val saved = s.copy(updatedAt = System.currentTimeMillis())
        repo.upsertSequence(saved)
        return saved
    }

    fun duplicateSequence(s: Sequence) {
        repo.upsertSequence(
            s.copy(
                id = newId(),
                name = "${s.name} copy",
                steps = s.steps.map { it.copy(id = newId()) },
                updatedAt = System.currentTimeMillis(),
            )
        )
        flash("Duplicated")
    }

    fun deleteSequence(id: String) {
        val seq = repo.sequence(id)
        repo.deleteSequence(id)
        if (seq != null) flash("Deleted \"${seq.name.ifBlank { "sequence" }}\"", "Undo") { repo.upsertSequence(seq) }
        else flash("Deleted")
    }

    var messageAction by mutableStateOf<Pair<String, () -> Unit>?>(null)
        private set

    fun flash(msg: String) = flash(msg, null, null)

    fun flash(msg: String, actionLabel: String?, action: (() -> Unit)?) {
        message = msg
        messageAction = if (actionLabel != null && action != null) actionLabel to action else null
        messageJob?.cancel()
        messageJob = viewModelScope.launch {
            delay(if (messageAction != null) 4500 else 2800)
            message = null
            messageAction = null
        }
    }

    fun runMessageAction() {
        messageAction?.second?.invoke()
        messageAction = null
        message = null
        messageJob?.cancel()
    }
}
