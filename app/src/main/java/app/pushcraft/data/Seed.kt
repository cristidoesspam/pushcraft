package app.pushcraft.data

import android.content.Context
import app.pushcraft.script.ScriptExamples

object Seed {
    fun starterData(ctx: Context, d: AppData): AppData {
        val coffee = Sender(
            id = newId(), name = "Coffee Club",
            iconPath = runCatching { Images.monogram(ctx, "☕", 0xFF8B5E3C.toInt()) }.getOrNull(),
            accent = 0xFF8B5E3C.toInt(),
        )
        val water = Sender(
            id = newId(), name = "Hydrate",
            iconPath = runCatching { Images.monogram(ctx, "💧", 0xFF2563EB.toInt()) }.getOrNull(),
            accent = 0xFF2563EB.toInt(),
        )
        val maya = Sender(
            id = newId(), name = "Maya",
            iconPath = runCatching { Images.monogram(ctx, "M", 0xFFDB2777.toInt()) }.getOrNull(),
            accent = 0xFFDB2777.toInt(),
        )
        val now = System.currentTimeMillis()
        val templates = listOf(
            Template(
                senderId = coffee.id,
                title = "Your oat latte is ready",
                body = "Pick it up at counter {{pick:2|3|4}}. It's {{time}}, still warm.",
                style = NotifStyle.BIG_TEXT,
                actions = listOf(
                    NotifAction(label = "On my way", kind = ActionKind.SCRIPT, withInput = true,
                        placeholder = "Running late?", script = ScriptExamples.SMART_REPLY.code),
                    NotifAction(label = "Snooze 10m", kind = ActionKind.SNOOZE, snoozeMinutes = 10),
                ),
                updatedAt = now,
            ),
            Template(
                senderId = water.id,
                title = "Water break",
                body = "Glass #{{count}} today. Your future self says thanks.",
                style = NotifStyle.STANDARD,
                actions = listOf(
                    NotifAction(label = "Done", kind = ActionKind.SCRIPT, script = ScriptExamples.STREAK.code),
                    NotifAction(label = "Later", kind = ActionKind.SCRIPT, script = ScriptExamples.PATIENT_SNOOZE.code),
                ),
                updatedAt = now - 1,
            ),
            Template(
                senderId = maya.id,
                title = "Maya",
                body = "are you still coming tonight?? 👀",
                style = NotifStyle.MESSAGING,
                actions = listOf(
                    NotifAction(label = "Reply", kind = ActionKind.REPLY, placeholder = "Message Maya"),
                    NotifAction(label = "Open map", kind = ActionKind.OPEN_URL, target = "geo:0,0?q=pizza"),
                ),
                updatedAt = now - 2,
            ),
        )
        val jake = Sender(
            id = newId(), name = "Jake",
            iconPath = runCatching { Images.monogram(ctx, "J", 0xFF0EA5E9.toInt()) }.getOrNull(),
            accent = 0xFF0EA5E9.toInt(),
        )
        val lads = Sender(
            id = newId(), name = "The Lads",
            iconPath = runCatching { Images.monogram(ctx, "🎮", 0xFF22C55E.toInt()) }.getOrNull(),
            accent = 0xFF22C55E.toInt(),
        )
        val sequences = listOf(
            Sequence(
                name = "Minecraft invite",
                steps = listOf(
                    SeqStep(senderId = lads.id, title = "The Lads", body = "minecraaft at 7 lads?", delayMs = 0),
                    SeqStep(senderId = jake.id, title = "Jake", body = "yes", delayMs = 2000),
                    SeqStep(senderId = maya.id, title = "Maya", body = "ofc", delayMs = 1000),
                ),
            ),
        )
        return d.copy(
            senders = d.senders + listOf(coffee, water, maya, jake, lads),
            templates = d.templates + templates,
            sequences = d.sequences + sequences,
            seeded = true,
        )
    }
}
