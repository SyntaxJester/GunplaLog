package com.syntaxjester.gunplalog

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 坚果云 WebDAV 客户端（也兼容其它标准 WebDAV 服务）。
 *
 * 坚果云要求使用「应用密码」而不是登录密码：
 *   网页端 → 账户信息 → 安全选项 → 添加应用密码
 * 端点固定为 https://dav.jianguoyun.com/dav/
 *
 * 注意：凭据以明文存在 SharedPreferences（应用私有目录）。这不是加密存储，
 * root 或有 adb 备份权限的环境可以读到。因此这里只接受应用密码，
 * 泄漏时可在坚果云端单独吊销，不影响主账号。
 */
object WebDav {

    const val DEFAULT_ENDPOINT = "https://dav.jianguoyun.com/dav/"
    private const val PREFS = "gunplalog_webdav"
    private const val TIMEOUT = 30_000

    data class Config(
        val endpoint: String,
        val user: String,
        val password: String,
        val dir: String
    ) {
        val configured: Boolean
            get() = endpoint.isNotBlank() && user.isNotBlank() && password.isNotBlank()
    }

    data class Entry(val name: String, val size: Long, val modified: String)

    // ---------- 配置存取 ----------

    fun load(ctx: Context): Config {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Config(
            endpoint = p.getString("endpoint", DEFAULT_ENDPOINT) ?: DEFAULT_ENDPOINT,
            user = p.getString("user", "") ?: "",
            password = p.getString("password", "") ?: "",
            dir = p.getString("dir", "GunplaLog") ?: "GunplaLog"
        )
    }

    fun save(ctx: Context, c: Config) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("endpoint", c.endpoint.trim())
            .putString("user", c.user.trim())
            .putString("password", c.password)
            .putString("dir", c.dir.trim().trim('/'))
            .apply()
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    // ---------- 请求基建 ----------

    private fun baseUrl(c: Config): String {
        var e = c.endpoint.trim()
        if (!e.endsWith("/")) e += "/"
        return e
    }

    private fun dirUrl(c: Config): String {
        val d = c.dir.trim().trim('/')
        return if (d.isEmpty()) baseUrl(c) else baseUrl(c) + encodePath(d) + "/"
    }

    private fun fileUrl(c: Config, name: String): String = dirUrl(c) + encodePath(name)

    private fun encodePath(path: String): String =
        path.split('/').joinToString("/") {
            URLEncoder.encode(it, "UTF-8").replace("+", "%20")
        }

    private fun open(c: Config, url: String, method: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = TIMEOUT
        conn.readTimeout = TIMEOUT
        conn.instanceFollowRedirects = true
        val cred = Base64.encodeToString(
            (c.user + ":" + c.password).toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )
        conn.setRequestProperty("Authorization", "Basic $cred")
        conn.setRequestProperty("User-Agent", "GunplaLog/1.2 WebDAV")
        return conn
    }

    private fun describe(code: Int): String = when (code) {
        401 -> "认证失败（401）：请确认用户名是坚果云账号邮箱，密码是「应用密码」而不是登录密码"
        403 -> "被拒绝（403）：账号可能未开启 WebDAV，或已达流量上限"
        404 -> "路径不存在（404）"
        409 -> "父目录不存在（409）"
        423 -> "文件被锁定（423），稍后再试"
        507 -> "空间不足（507）"
        in 500..599 -> "服务端错误（$code），稍后再试"
        else -> "请求失败（$code）"
    }

    // ---------- 操作 ----------

    /** 连通性 + 认证测试；成功返回 null，失败返回错误描述 */
    fun test(c: Config): String? {
        return try {
            val conn = open(c, baseUrl(c), "PROPFIND")
            conn.setRequestProperty("Depth", "0")
            conn.doOutput = false
            val code = conn.responseCode
            conn.disconnect()
            if (code in 200..299 || code == 207) null else describe(code)
        } catch (e: Exception) {
            "网络错误：" + (e.message ?: e.javaClass.simpleName)
        }
    }

    /** 确保备份目录存在（MKCOL，已存在返回 405 也算成功） */
    fun ensureDir(c: Config): String? {
        val d = c.dir.trim().trim('/')
        if (d.isEmpty()) return null
        return try {
            val conn = open(c, dirUrl(c), "MKCOL")
            val code = conn.responseCode
            conn.disconnect()
            if (code in 200..299 || code == 405 || code == 301 || code == 302) null
            else describe(code)
        } catch (e: Exception) {
            "网络错误：" + (e.message ?: e.javaClass.simpleName)
        }
    }

    /** 上传备份；成功返回 null */
    fun upload(c: Config, name: String, data: ByteArray): String? {
        ensureDir(c)?.let { return it }
        return try {
            val conn = open(c, fileUrl(c, name), "PUT")
            conn.doOutput = true
            conn.setFixedLengthStreamingMode(data.size)
            conn.setRequestProperty("Content-Type", Backup.MIME)
            conn.outputStream.use { it.write(data) }
            val code = conn.responseCode
            conn.disconnect()
            if (code in 200..299) null else describe(code)
        } catch (e: Exception) {
            "网络错误：" + (e.message ?: e.javaClass.simpleName)
        }
    }

    /** 下载备份；失败抛异常（携带可读信息） */
    fun download(c: Config, name: String): ByteArray {
        val conn = open(c, fileUrl(c, name), "GET")
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            throw IllegalStateException(describe(code))
        }
        val bos = ByteArrayOutputStream()
        conn.inputStream.use { it.copyTo(bos) }
        conn.disconnect()
        return bos.toByteArray()
    }

    /** 列出备份目录下的 .gunpla.zip，按名称倒序（新的在前） */
    fun list(c: Config): List<Entry> {
        val conn = open(c, dirUrl(c), "PROPFIND")
        conn.setRequestProperty("Depth", "1")
        conn.setRequestProperty("Content-Type", "application/xml; charset=utf-8")
        conn.doOutput = true
        val body = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:"><d:prop>
<d:displayname/><d:getcontentlength/><d:getlastmodified/>
</d:prop></d:propfind>"""
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        if (code !in 200..299 && code != 207) {
            conn.disconnect()
            throw IllegalStateException(describe(code))
        }
        val xml = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        conn.disconnect()
        return parseList(xml)
    }

    fun delete(c: Config, name: String): String? {
        return try {
            val conn = open(c, fileUrl(c, name), "DELETE")
            val code = conn.responseCode
            conn.disconnect()
            if (code in 200..299 || code == 404) null else describe(code)
        } catch (e: Exception) {
            "网络错误：" + (e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * 极简 multistatus 解析。不引三方 XML 库：按 <response> 切块，
     * 每块里抓 href / getcontentlength / getlastmodified。
     */
    private fun parseList(xml: String): List<Entry> {
        val out = mutableListOf<Entry>()
        val blocks = Regex("(?is)<(?:[a-z0-9]+:)?response\\b.*?</(?:[a-z0-9]+:)?response>")
            .findAll(xml).map { it.value }
        for (b in blocks) {
            val href = tag(b, "href") ?: continue
            val raw = href.trimEnd('/').substringAfterLast('/')
            val name = try {
                java.net.URLDecoder.decode(raw, "UTF-8")
            } catch (_: Exception) {
                raw
            }
            if (!name.endsWith(Backup.EXT)) continue
            val size = tag(b, "getcontentlength")?.toLongOrNull() ?: 0L
            val modified = tag(b, "getlastmodified") ?: ""
            out.add(Entry(name, size, modified))
        }
        return out.sortedByDescending { it.name }
    }

    private fun tag(block: String, name: String): String? =
        Regex("(?is)<(?:[a-z0-9]+:)?$name\\b[^>]*>(.*?)</(?:[a-z0-9]+:)?$name>")
            .find(block)?.groupValues?.get(1)?.trim()
}
