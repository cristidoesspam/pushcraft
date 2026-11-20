package app.pushcraft.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import app.pushcraft.MainActivity
import app.pushcraft.R
import app.pushcraft.data.ActionKind
import app.pushcraft.data.Images
import app.pushcraft.data.Importance
import app.pushcraft.data.LogKind
import app.pushcraft.data.MAX_ACTIONS
import app.pushcraft.data.NotifAction
import app.pushcraft.data.NotifStyle
import app.pushcraft.data.Repository
import app.pushcraft.data.Sender
import app.pushcraft.data.SenderSource
import app.pushcraft.data.Template
import app.pushcraft.data.TemplateVars
import app.pushcraft.data.newId

const val EXTRA_TEMPLATE = "pushcraft.template"
const val EXTRA_ACTION = "pushcraft.action"
const val EXTRA_NOTIF = "pushcraft.notif"
const val EXTRA_SCHEDULE = "pushcraft.schedule"

object Poster {
    const val CH_BANNER = "banner"
    const val CH_QUIET = "quiet"
    const val KEY_REPLY = "reply"

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_BANNER, "Banners", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Pop-up notifications you create" }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_QUIET, "Quiet", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Silent notifications that only appear in the shade" }
        )
    }

    fun canPost(ctx: Context) = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    fun post(
        ctx: Context,
        t: Template,
        titleOverride: String? = null,
        bodyOverride: String? = null,
        withActions: Boolean = true,
        senderOverride: Sender? = null,
    ): Int {
        val repo = Repository.get(ctx)
        val sender = senderOverride ?: repo.sender(t.senderId)
        val now = System.currentTimeMillis()
        val notifId = repo.nextNotifId()
        val count = if (titleOverride == null && senderOverride == null) repo.increment("sent:${t.id}") else repo.counter("sent:${t.id}")
        val vars = repo.vars(t.id)
        val title = titleOverride ?: TemplateVars.render(t.title, now, vars, count)
        val body = bodyOverride ?: TemplateVars.render(t.body, now, vars, count)
        val avatar: Bitmap? = Images.load(sender.iconPath)
        val banner = t.importance == Importance.BANNER

        val b = NotificationCompat.Builder(ctx, if (banner) CH_BANNER else CH_QUIET)
            .setSmallIcon(R.drawable.ic_stat_pushcraft)
            .setColor(sender.accent)
            .setContentTitle(title)
            .setContentText(body)
            .setWhen(now)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setPriority(if (banner) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent(ctx, t.id, notifId))
        if (sender.source != SenderSource.DEFAULT) b.setSubText(sender.name)

        when (t.style) {
            NotifStyle.MESSAGING -> {
                val me = Person.Builder().setName("You").setKey("me").build()
                val them = Person.Builder()
                    .setName(sender.name)
                    .setKey(sender.id)
                    .apply { avatar?.let { setIcon(IconCompat.createWithBitmap(it)) } }
                    .build()
                b.setStyle(NotificationCompat.MessagingStyle(me).addMessage(body, now, them))
                b.setCategory(NotificationCompat.CATEGORY_MESSAGE)
            }
            NotifStyle.BIG_PICTURE -> {
                b.setLargeIcon(avatar)
                Images.load(t.imagePath)?.let { pic ->
                    b.setStyle(
                        NotificationCompat.BigPictureStyle()
                            .bigPicture(pic)
                            .bigLargeIcon(null as Bitmap?)
                            .setSummaryText(body)
                    )
                }
            }
            NotifStyle.BIG_TEXT -> {
                b.setLargeIcon(avatar)
                b.setStyle(NotificationCompat.BigTextStyle().bigText(body))
            }
            NotifStyle.STANDARD -> b.setLargeIcon(avatar)
        }

        if (withActions) {
            t.actions.take(MAX_ACTIONS).forEachIndexed { i, a -> b.addAction(buildAction(ctx, t, a, notifId, i)) }
        }

        try {
            NotificationManagerCompat.from(ctx).notify(notifId, b.build())
            repo.log(LogKind.SENT, t.id, title.ifBlank { "(no title)" })
        } catch (e: SecurityException) {
            repo.log(LogKind.ERROR, t.id, "Notification permission missing")
        }
        return notifId
    }

    fun postCustom(
        ctx: Context,
        sender: Sender,
        title: String,
        body: String,
        style: NotifStyle,
        importance: Importance = Importance.BANNER,
    ): Int {
        val ephemeral = Template(
            id = "seq_${newId()}",
            senderId = sender.id,
            title = title,
            body = body,
            style = style,
            importance = importance,
            actions = emptyList(),
        )
        return post(ctx, ephemeral, withActions = false, senderOverride = sender)
    }

    fun postLaunchOffer(ctx: Context, t: Template, launch: Intent, label: String) {
        val repo = Repository.get(ctx)
        val sender = repo.sender(t.senderId)
        val id = repo.nextNotifId()
        val pi = PendingIntent.getActivity(
            ctx, id, launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, Poster.CH_BANNER)
            .setSmallIcon(R.drawable.ic_stat_pushcraft)
            .setColor(sender.accent)
            .setContentTitle("Tap to open")
            .setContentText(label)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(id, n) }
    }

    fun cancel(ctx: Context, notifId: Int) {
        if (notifId >= 0) NotificationManagerCompat.from(ctx).cancel(notifId)
    }

    private fun openAppIntent(ctx: Context, templateId: String, notifId: Int): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java)
            .putExtra(EXTRA_TEMPLATE, templateId)
            .setData(Uri.parse("pushcraft://open/$notifId"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            ctx, notifId, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun buildAction(ctx: Context, t: Template, a: NotifAction, notifId: Int, index: Int): NotificationCompat.Action {
        val target = if (a.opensUi) ActionTrampolineActivity::class.java else ActionReceiver::class.java
        val intent = Intent(ctx, target)
            .putExtra(EXTRA_TEMPLATE, t.id)
            .putExtra(EXTRA_ACTION, a.id)
            .putExtra(EXTRA_NOTIF, notifId)
            .setData(Uri.parse("pushcraft://action/$notifId/${a.id}"))
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (a.hasInput) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        val requestCode = notifId * 4 + index
        val pi = if (a.opensUi) {
            PendingIntent.getActivity(ctx, requestCode, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags)
        } else {
            PendingIntent.getBroadcast(ctx, requestCode, intent, flags)
        }
        val label = a.label.ifBlank { a.kind.defaultLabel }
        val builder = NotificationCompat.Action.Builder(0, label, pi)
        if (a.hasInput) {
            builder.addRemoteInput(RemoteInput.Builder(KEY_REPLY).setLabel(a.placeholder.ifBlank { "Reply…" }).build())
            builder.setAllowGeneratedReplies(a.kind == ActionKind.REPLY)
            builder.setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
        }
        return builder.build()
    }
}
