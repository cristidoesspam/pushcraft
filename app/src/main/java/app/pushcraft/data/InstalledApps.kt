package app.pushcraft.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap

data class AppInfo(val packageName: String, val label: String)

object InstalledApps {
    @Suppress("DEPRECATION")
    fun list(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcher, 0)
            .map { AppInfo(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.packageName }
            .filter { it.packageName != ctx.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun label(ctx: Context, pkg: String): String? = runCatching {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrNull()

    fun icon(ctx: Context, pkg: String, size: Int): Bitmap? = runCatching {
        ctx.packageManager.getApplicationIcon(pkg).toBitmap(size, size, Bitmap.Config.ARGB_8888)
    }.getOrNull()

    fun exportIcon(ctx: Context, pkg: String): String? =
        icon(ctx, pkg, 256)?.let { Images.save(ctx, it, "senders", "app_${pkg}_${newId()}.png") }

    fun launchIntent(ctx: Context, pkg: String): Intent? =
        ctx.packageManager.getLaunchIntentForPackage(pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
