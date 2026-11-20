package app.pushcraft.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.util.LruCache
import java.io.File

object Images {
    private val cache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private fun dir(ctx: Context, sub: String) = File(ctx.filesDir, sub).apply { mkdirs() }

    fun load(path: String?): Bitmap? {
        if (path == null) return null
        cache.get(path)?.let { return it }
        val bmp = runCatching { BitmapFactory.decodeFile(path) }.getOrNull() ?: return null
        cache.put(path, bmp)
        return bmp
    }

    fun save(ctx: Context, bmp: Bitmap, sub: String, name: String, jpeg: Boolean = false): String {
        val file = File(dir(ctx, sub), name)
        file.outputStream().use {
            bmp.compress(if (jpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG, 92, it)
        }
        cache.remove(file.absolutePath)
        return file.absolutePath
    }

    private fun decode(ctx: Context, uri: Uri, target: Int): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = maxOf(info.size.width, info.size.height)
                var sample = 1
                while (longest / (sample * 2) >= target) sample *= 2
                decoder.setTargetSampleSize(sample)
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (longest / (sample * 2) >= target) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }
    }.getOrNull()

    fun avatarFromUri(ctx: Context, uri: Uri, size: Int = 256): String? {
        val src = decode(ctx, uri, size) ?: return null
        val side = minOf(src.width, src.height)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(
            src,
            Rect((src.width - side) / 2, (src.height - side) / 2, (src.width + side) / 2, (src.height + side) / 2),
            Rect(0, 0, size, size),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        return save(ctx, out, "senders", "${newId()}.png")
    }

    fun pictureFromUri(ctx: Context, uri: Uri, max: Int = 1080): String? {
        val src = decode(ctx, uri, max) ?: return null
        val scale = minOf(1f, max.toFloat() / maxOf(src.width, src.height))
        val bmp = if (scale < 1f) {
            Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true)
        } else src
        return save(ctx, bmp, "pictures", "${newId()}.jpg", jpeg = true)
    }

    fun monogram(ctx: Context, text: String, background: Int, size: Int = 256): String {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background }
        canvas.drawOval(RectF(0f, 0f, size.toFloat(), size.toFloat()), bg)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = size * if (text.codePointCount(0, text.length) <= 1) 0.5f else 0.38f
        }
        val y = size / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(text, size / 2f, y, paint)
        return save(ctx, bmp, "senders", "${newId()}.png")
    }

    fun accentOf(bmp: Bitmap): Int {
        val small = Bitmap.createScaledBitmap(bmp, 24, 24, true)
        var r = 0L; var g = 0L; var b = 0L; var n = 0
        val hsv = FloatArray(3)
        for (x in 0 until 24) for (y in 0 until 24) {
            val c = small.getPixel(x, y)
            if (Color.alpha(c) < 128) continue
            Color.colorToHSV(c, hsv)
            if (hsv[1] < 0.25f || hsv[2] < 0.2f) continue
            r += Color.red(c); g += Color.green(c); b += Color.blue(c); n++
        }
        if (n == 0) return 0xFF5B5BF6.toInt()
        return Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }
}
