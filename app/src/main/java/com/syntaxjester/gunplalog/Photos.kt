package com.syntaxjester.gunplalog

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 模型美图存储：统一放在 filesDir/images/<uuid>.jpg。
 * 导入时会修正 EXIF 方向并压缩到长边 1600px，避免原图直接塞进内部存储。
 */
object Photos {

    private const val MAX_EDGE = 1600
    private const val QUALITY = 88

    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun dir(ctx: Context): File {
        val d = File(ctx.filesDir, "images")
        if (!d.exists()) d.mkdirs()
        return d
    }

    fun file(ctx: Context, name: String): File = File(dir(ctx), name)

    fun exists(ctx: Context, name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val f = file(ctx, name)
        return f.exists() && f.length() > 0
    }

    /** 相机输出用的临时文件（cacheDir/camera，走 FileProvider 授权） */
    fun cameraTemp(ctx: Context): File {
        val d = File(ctx.cacheDir, "camera")
        if (!d.exists()) d.mkdirs()
        return File(d, "shot_" + System.currentTimeMillis() + ".jpg")
    }

    fun uriFor(ctx: Context, f: File): Uri =
        FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)

    /** 从相册等 content:// Uri 导入，返回内部文件名；失败返回 null */
    fun importFrom(ctx: Context, uri: Uri): String? {
        val tmp = File(ctx.cacheDir, "import_" + System.currentTimeMillis() + ".tmp")
        try {
            val ins = ctx.contentResolver.openInputStream(uri) ?: return null
            ins.use { input ->
                FileOutputStream(tmp).use { out -> input.copyTo(out) }
            }
            return importFromFile(ctx, tmp)
        } catch (_: Exception) {
            return null
        } finally {
            try {
                tmp.delete()
            } catch (_: Exception) {
            }
        }
    }

    /** 从本地文件导入（相机拍完的临时文件） */
    fun importFromFile(ctx: Context, src: File): String? {
        try {
            if (!src.exists() || src.length() == 0L) return null

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(src.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleFor(bounds.outWidth, bounds.outHeight, MAX_EDGE)
            }
            var bmp = BitmapFactory.decodeFile(src.absolutePath, opts) ?: return null
            bmp = applyExif(src, bmp)
            bmp = scaleDown(bmp, MAX_EDGE)

            val name = UUID.randomUUID().toString() + ".jpg"
            FileOutputStream(file(ctx, name)).use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            }
            return name
        } catch (_: Exception) {
            return null
        }
    }

    /** 解码到指定长边（用于列表缩略图 / 大图预览），带内存缓存 */
    fun decode(ctx: Context, name: String?, reqEdge: Int): Bitmap? {
        if (name.isNullOrBlank()) return null
        val key = "$name@$reqEdge"
        cache.get(key)?.let { return it }
        try {
            val f = file(ctx, name)
            if (!f.exists()) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.absolutePath, bounds)
            if (bounds.outWidth <= 0) return null
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleFor(bounds.outWidth, bounds.outHeight, reqEdge)
            }
            val bmp = BitmapFactory.decodeFile(f.absolutePath, opts) ?: return null
            cache.put(key, bmp)
            return bmp
        } catch (_: Exception) {
            return null
        }
    }

    fun delete(ctx: Context, name: String?) {
        if (name.isNullOrBlank()) return
        try {
            file(ctx, name).delete()
        } catch (_: Exception) {
        }
        try {
            cache.snapshot().keys
                .filter { it.startsWith("$name@") }
                .forEach { cache.remove(it) }
        } catch (_: Exception) {
        }
    }

    /** 清理没有被任何记录引用的孤儿图片 */
    fun gc(ctx: Context, items: List<Item>) {
        try {
            val used = items.mapNotNull { it.photo.takeIf { p -> p.isNotBlank() } }.toSet()
            dir(ctx).listFiles()?.forEach { f ->
                if (f.name !in used) f.delete()
            }
        } catch (_: Exception) {
        }
    }

    // ---------- 内部 ----------

    private fun sampleFor(w: Int, h: Int, reqEdge: Int): Int {
        var sample = 1
        var longEdge = if (w > h) w else h
        while (longEdge / 2 >= reqEdge) {
            longEdge /= 2
            sample *= 2
        }
        return sample
    }

    private fun applyExif(src: File, bmp: Bitmap): Bitmap {
        val degrees = try {
            when (ExifInterface(src.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } catch (_: Exception) {
            0f
        }
        if (degrees == 0f) return bmp
        return try {
            val m = Matrix().apply { postRotate(degrees) }
            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        } catch (_: Exception) {
            bmp
        }
    }

    private fun scaleDown(bmp: Bitmap, maxEdge: Int): Bitmap {
        val longEdge = if (bmp.width > bmp.height) bmp.width else bmp.height
        if (longEdge <= maxEdge) return bmp
        val ratio = maxEdge.toFloat() / longEdge
        val w = (bmp.width * ratio).toInt().coerceAtLeast(1)
        val h = (bmp.height * ratio).toInt().coerceAtLeast(1)
        return try {
            Bitmap.createScaledBitmap(bmp, w, h, true)
        } catch (_: Exception) {
            bmp
        }
    }
}
