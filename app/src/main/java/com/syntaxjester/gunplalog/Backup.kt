package com.syntaxjester.gunplalog

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 备份包格式（.gunpla.zip）：
 *   backup.json          —— { version, createdAt, count, items: [...] }
 *   images/<uuid>.jpg    —— 所有被引用到的模型美图
 *
 * 用 ZIP 而不是纯 JSON，是为了让美图一起走；恢复时图片会原样落回
 * filesDir/images，文件名即 Item.photo，不需要重映射。
 */
object Backup {

    const val FORMAT_VERSION = 1
    const val EXT = ".gunpla.zip"
    const val MIME = "application/zip"

    /** 恢复模式 */
    const val MODE_MERGE = 0     // 合并：同 id 跳过，其余追加
    const val MODE_REPLACE = 1   // 覆盖：清空现有记录后写入

    data class Result(val added: Int, val skipped: Int, val photos: Int, val total: Int)

    fun defaultName(): String =
        "gunplalog_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date()) + EXT

    // ---------- 写出 ----------

    fun write(ctx: Context, items: List<Item>, out: OutputStream) {
        ZipOutputStream(out.buffered()).use { zip ->
            val meta = JSONObject().apply {
                put("version", FORMAT_VERSION)
                put("createdAt", System.currentTimeMillis())
                put("createdAtText", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                put("app", "GunplaLog")
                put("count", items.size)
                put("items", JSONArray().apply { items.forEach { put(it.toJson()) } })
            }
            zip.putNextEntry(ZipEntry("backup.json"))
            zip.write(meta.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            items.forEach { item ->
                val name = item.photo
                if (name.isBlank()) return@forEach
                val f = Photos.file(ctx, name)
                if (!f.exists() || f.length() == 0L) return@forEach
                try {
                    zip.putNextEntry(ZipEntry("images/$name"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun toBytes(ctx: Context, items: List<Item>): ByteArray {
        val bos = ByteArrayOutputStream()
        write(ctx, items, bos)
        return bos.toByteArray()
    }

    // ---------- 读入 ----------

    /**
     * 从备份流恢复。返回统计；调用方负责持久化 current 列表。
     * current 会被就地修改。
     */
    fun restore(
        ctx: Context,
        input: InputStream,
        current: MutableList<Item>,
        mode: Int
    ): Result {
        var json: String? = null
        val photoNames = mutableSetOf<String>()

        ZipInputStream(input.buffered()).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    name == "backup.json" || name.endsWith("/backup.json") -> {
                        json = zip.readBytes().toString(Charsets.UTF_8)
                    }
                    name.startsWith("images/") && !entry.isDirectory -> {
                        val fileName = name.substringAfterLast('/')
                        if (fileName.isNotBlank() && safeName(fileName)) {
                            try {
                                Photos.file(ctx, fileName).outputStream().use { zip.copyTo(it) }
                                photoNames.add(fileName)
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val raw = json ?: throw IllegalArgumentException("备份文件缺少 backup.json")
        val obj = JSONObject(raw)
        val arr = obj.optJSONArray("items") ?: JSONArray()

        val incoming = mutableListOf<Item>()
        for (i in 0 until arr.length()) {
            try {
                incoming.add(Item.fromJson(arr.getJSONObject(i)))
            } catch (_: Exception) {
            }
        }

        var added = 0
        var skipped = 0

        if (mode == MODE_REPLACE) {
            // 先记下旧图，替换后清理不再被引用的
            val oldPhotos = current.mapNotNull { it.photo.takeIf { p -> p.isNotBlank() } }.toSet()
            current.clear()
            current.addAll(incoming)
            added = incoming.size
            val keep = incoming.mapNotNull { it.photo.takeIf { p -> p.isNotBlank() } }.toSet()
            oldPhotos.filter { it !in keep }.forEach { Photos.delete(ctx, it) }
        } else {
            val existingIds = current.map { it.id }.toMutableSet()
            incoming.forEach { item ->
                if (item.id in existingIds) {
                    skipped++
                } else {
                    current.add(item)
                    existingIds.add(item.id)
                    added++
                }
            }
        }

        return Result(added, skipped, photoNames.size, incoming.size)
    }

    /** 只读元信息，用于恢复前给用户看「这份备份是什么」 */
    fun peek(input: InputStream): JSONObject? {
        try {
            ZipInputStream(input.buffered()).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "backup.json" || entry.name.endsWith("/backup.json")) {
                        return JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } catch (_: Exception) {
        }
        return null
    }

    /** 防目录穿越：只接受纯文件名 */
    private fun safeName(name: String): Boolean =
        !name.contains("..") && !name.contains('/') && !name.contains('\\') && name.isNotBlank()

    /** 本地兜底备份目录（外部私有目录，便于用户自己翻出来） */
    fun localDir(ctx: Context): File {
        val d = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "backups")
        if (!d.exists()) d.mkdirs()
        return d
    }
}
