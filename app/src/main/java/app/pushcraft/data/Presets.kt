package app.pushcraft.data

import android.content.Context

data class Preset(
    val category: String,
    val name: String,
    val senderName: String,
    val emoji: String,
    val accent: Long,
    val title: String,
    val body: String,
    val style: NotifStyle = NotifStyle.STANDARD,
    val importance: Importance = Importance.BANNER,
    val actions: List<NotifAction> = emptyList(),
)

private fun btn(label: String, kind: ActionKind = ActionKind.DISMISS, snooze: Int = 10, placeholder: String = "Reply…") =
    NotifAction(label = label, kind = kind, snoozeMinutes = snooze, placeholder = placeholder)

object PresetCatalog {
    val presets = listOf(
        Preset(
            "Chat", "Group chat", "The Lads", "💬", 0xFF22C55E,
            title = "The Lads", body = "minecraft at 7 lads? 🎮",
            style = NotifStyle.MESSAGING,
            actions = listOf(btn("Reply", ActionKind.REPLY, placeholder = "Message The Lads"), btn("yes")),
        ),
        Preset(
            "Chat", "47 new messages", "Family Group", "👨‍👩‍👧", 0xFF8B5CF6,
            title = "Family Group", body = "47 new messages", style = NotifStyle.MESSAGING,
            actions = listOf(btn("Reply", ActionKind.REPLY, placeholder = "Message group"), btn("Mute")),
        ),
        Preset(
            "Chat", "Crush texted", "❤️ you know who", "😳", 0xFFEC4899,
            title = "❤️ you know who", body = "hey, you up? 👀", style = NotifStyle.MESSAGING,
            actions = listOf(btn("Reply", ActionKind.REPLY, placeholder = "play it cool"), btn("Later")),
        ),
        Preset(
            "Chat", "Mom", "Mom", "📱", 0xFFF97316,
            title = "Mom", body = "call me when you see this", style = NotifStyle.MESSAGING,
            actions = listOf(btn("Call back", ActionKind.OPEN_URL).copy(target = "tel:"), btn("later", ActionKind.REPLY, placeholder = "text mom")),
        ),

        Preset(
            "Social", "New follower", "Social", "👤", 0xFF3B82F6,
            title = "You have a new follower", body = "someone started following you ✨",
            actions = listOf(btn("Follow back"), btn("View")),
        ),
        Preset(
            "Social", "Liked your photo", "Photos", "📸", 0xFFE11D48,
            title = "New like", body = "your_crush and 212 others liked your photo",
            style = NotifStyle.STANDARD,
        ),
        Preset(
            "Social", "Going live", "Live", "🔴", 0xFFEF4444,
            title = "Someone is live", body = "your favourite streamer just went live", importance = Importance.BANNER,
            actions = listOf(btn("Watch"), btn("Dismiss")),
        ),
        Preset(
            "Social", "New video", "Uploads", "▶️", 0xFFDC2626,
            title = "New upload", body = "a channel you follow posted a new video",
            actions = listOf(btn("Watch"), btn("Save")),
        ),

        Preset(
            "System", "Low battery", "Battery", "🔋", 0xFFEF4444,
            title = "1% battery remaining", body = "Connect your charger. Entering power saving mode.",
            importance = Importance.BANNER,
        ),
        Preset(
            "System", "Storage full", "Storage", "⚠️", 0xFFF59E0B,
            title = "Storage almost full", body = "0.01 GB left. Delete some files to free up space.",
        ),
        Preset(
            "System", "Software update", "Updates", "⬆️", 0xFF10B981,
            title = "Update available", body = "A new version is ready to install.",
            actions = listOf(btn("Install"), btn("Later", ActionKind.SNOOZE, snooze = 60)),
        ),
        Preset(
            "System", "Alarm", "Clock", "⏰", 0xFF6366F1,
            title = "Alarm", body = "Wake up 😴", importance = Importance.BANNER,
            actions = listOf(btn("Snooze", ActionKind.SNOOZE, snooze = 5), btn("Dismiss")),
        ),

        Preset(
            "Life", "Food on the way", "Delivery", "🍕", 0xFFEA580C,
            title = "Your order is 2 min away", body = "Your rider is almost there. Meet them outside.",
            actions = listOf(btn("Track"), btn("Call rider", ActionKind.OPEN_URL).copy(target = "tel:")),
        ),
        Preset(
            "Life", "Money received", "Wallet", "💸", 0xFF16A34A,
            title = "You got paid", body = "£500.00 was added to your balance 🤑",
        ),
        Preset(
            "Life", "Reminder", "Reminder", "📌", 0xFF0891B2,
            title = "Reminder", body = "Don't forget: {{time}} · you know what you did",
            style = NotifStyle.BIG_TEXT,
            actions = listOf(btn("Done"), btn("Snooze", ActionKind.SNOOZE, snooze = 10)),
        ),
    )

    val categories: List<String> get() = presets.map { it.category }.distinct()

    fun apply(ctx: Context, repo: Repository, preset: Preset): Template {
        val existing = repo.data.senders.find { it.name == preset.senderName && it.source == SenderSource.CUSTOM }
        val sender = existing ?: Sender(
            id = newId(),
            name = preset.senderName,
            iconPath = runCatching { Images.monogram(ctx, preset.emoji, preset.accent.toInt()) }.getOrNull(),
            accent = preset.accent.toInt(),
        ).also { repo.upsertSender(it) }
        return Template(
            senderId = sender.id,
            title = preset.title,
            body = preset.body,
            style = preset.style,
            importance = preset.importance,
            actions = preset.actions.map { it.copy(id = newId()) },
        )
    }
}
