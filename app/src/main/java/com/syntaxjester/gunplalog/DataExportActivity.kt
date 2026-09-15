package com.syntaxjester.gunplalog

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

/** 集中处理本地备份、恢复和坚果云同步的数据导出中心。 */
class DataExportActivity : AppCompatActivity() {
    private lateinit var store: Store
    private val blue = Color.parseColor("#2E7EF0")
    private val dark = Color.parseColor("#171A20")
    private val muted = Color.parseColor("#7C8798")

    private val backupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument(Backup.MIME)) { uri ->
        if (uri != null) doLocalBackup(uri)
    }
    private val restoreLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) doLocalRestore(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        store = Store(this)
        render()
    }

    private fun render() {
        val items = store.load()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_me_page)
            setPadding(dp(14), dp(14), dp(14), dp(20))
        }
        root.addView(header())
        root.addView(summary(items.size), margin(18))
        root.addView(sectionTitle("数据备份"), margin(24))
        root.addView(actionCard(R.drawable.ic_export_backup, "备份数据", "导出全部物品、图片与记录为安全备份包") { pickBackupTarget() }, margin(10))
        root.addView(actionCard(R.drawable.ic_export_restore, "恢复数据", "从本地文件或坚果云备份还原数据") { pickRestoreSource() }, margin(10))
        root.addView(sectionTitle("云端同步"), margin(24))
        root.addView(actionCard(R.drawable.ic_export_cloud, "坚果云 WebDAV", if (WebDav.load(this).configured) "已配置，可备份并恢复云端数据" else "配置坚果云账号后启用云端备份") { CloudSheet().show(supportFragmentManager, "cloud") }, margin(10))
        root.addView(TextView(this).apply {
            text = "备份包包含物品资料和已保存的图片；恢复时可选择合并或覆盖现有数据。"
            textSize = 13f; setTextColor(muted); gravity = Gravity.CENTER
            setPadding(dp(16), dp(28), dp(16), 0)
        })
        setContentView(ScrollView(this).apply { fitsSystemWindows = true; addView(root) })
    }

    private fun header() = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(ImageView(this@DataExportActivity).apply {
            setImageResource(R.drawable.ic_manager_back); setBackgroundResource(R.drawable.bg_manager_circle)
            setPadding(dp(13), dp(13), dp(13), dp(13)); setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(54), dp(54)))
        addView(LinearLayout(this@DataExportActivity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(15), 0, 0, 0)
            addView(text("数据导出", 22f, dark, true))
            addView(text("管理备份、恢复与云端同步", 12f, muted, false))
        }, LinearLayout.LayoutParams(0, dp(54), 1f))
    }

    private fun summary(count: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setBackgroundResource(R.drawable.bg_export_summary)
        setPadding(dp(15), dp(13), dp(15), dp(13))
        addView(text("保护你的收藏数据", 16f, Color.WHITE, true))
        addView(text("当前共有 $count 条物品记录，建议定期创建完整备份。", 12f, Color.WHITE, false).apply { alpha = .9f; setPadding(0, dp(4), 0, 0) })
    }

    private fun actionCard(icon: Int, title: String, sub: String, click: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setBackgroundResource(R.drawable.bg_manager_card); setPadding(dp(12), dp(11), dp(12), dp(11)); isClickable = true
        setOnClickListener { click() }
        addView(ImageView(this@DataExportActivity).apply { setImageResource(icon) }, LinearLayout.LayoutParams(dp(36), dp(36)))
        addView(LinearLayout(this@DataExportActivity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(11), 0, dp(6), 0)
            addView(text(title, 15f, dark, true))
            addView(text(sub, 12f, muted, false).apply { setPadding(0, dp(2), 0, 0) })
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(text("›", 31f, Color.parseColor("#9BA7B8"), false))
    }

    private fun sectionTitle(value: String) = text(value, 16f, Color.parseColor("#8391A7"), true)

    private fun pickBackupTarget() {
        val items = store.load()
        if (items.isEmpty()) { toast(getString(R.string.backup_empty)); return }
        AlertDialog.Builder(this).setTitle("备份到哪里")
            .setItems(arrayOf("保存到本地文件", "备份到坚果云")) { _, w -> if (w == 0) backupLauncher.launch(Backup.defaultName()) else cloudBackup() }
            .setNegativeButton("取消", null).show()
    }

    private fun pickRestoreSource() {
        AlertDialog.Builder(this).setTitle("从哪里恢复")
            .setItems(arrayOf("从本地备份恢复", "从坚果云恢复")) { _, w -> if (w == 0) restoreLauncher.launch(arrayOf("*/*")) else cloudRestore() }
            .setNegativeButton("取消", null).show()
    }

    private fun doLocalBackup(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { Backup.write(this, store.load(), it) }
            toast("备份创建成功")
        } catch (e: Exception) { toast("备份失败：${e.message.orEmpty()}") }
    }

    private fun doLocalRestore(uri: Uri) {
        val bytes = try { contentResolver.openInputStream(uri)?.use { it.readBytes() } } catch (_: Exception) { null }
        if (bytes == null || bytes.isEmpty()) { toast(getString(R.string.restore_bad_file)); return }
        confirmAndRestore(bytes)
    }

    private fun cloudBackup() {
        val cfg = WebDav.load(this)
        if (!cfg.configured) { CloudSheet().show(supportFragmentManager, "cloud"); return }
        val dialog = progress("正在上传到坚果云…")
        val name = Backup.defaultName()
        val data = Backup.toBytes(this, store.load())
        Thread {
            val error = try { WebDav.upload(cfg, name, data) } catch (e: Exception) { e.message ?: "未知错误" }
            runOnUiThread { dialog.dismiss(); toast(if (error == null) "云端备份成功" else "备份失败：$error") }
        }.start()
    }

    private fun cloudRestore() {
        val cfg = WebDav.load(this)
        if (!cfg.configured) { CloudSheet().show(supportFragmentManager, "cloud"); return }
        val dialog = progress("正在读取云端备份…")
        Thread {
            val list = try { WebDav.list(cfg) } catch (_: Exception) { emptyList() }
            runOnUiThread {
                dialog.dismiss()
                if (list.isEmpty()) toast("坚果云上还没有备份文件") else {
                    AlertDialog.Builder(this).setTitle("选择要恢复的备份")
                        .setItems(list.map { "${it.name} · ${(it.size / 1024).coerceAtLeast(1)} KB" }.toTypedArray()) { _, i -> downloadAndRestore(cfg, list[i].name) }
                        .setNegativeButton("取消", null).show()
                }
            }
        }.start()
    }

    private fun downloadAndRestore(cfg: WebDav.Config, name: String) {
        val dialog = progress("正在下载备份…")
        Thread {
            val bytes = try { WebDav.download(cfg, name) } catch (_: Exception) { null }
            runOnUiThread { dialog.dismiss(); if (bytes == null) toast("下载失败") else confirmAndRestore(bytes) }
        }.start()
    }

    private fun confirmAndRestore(bytes: ByteArray) {
        val meta = Backup.peek(java.io.ByteArrayInputStream(bytes)) ?: run { toast(getString(R.string.restore_bad_file)); return }
        val count = meta.optInt("count", 0); val created = meta.optString("createdAtText", "未知")
        AlertDialog.Builder(this).setTitle("恢复方式")
            .setMessage("备份包含 $count 条记录（$created）\n\n合并会保留现有记录；覆盖会完全按备份还原。")
            .setPositiveButton("合并") { _, _ -> applyRestore(bytes, Backup.MODE_MERGE) }
            .setNeutralButton("覆盖") { _, _ -> applyRestore(bytes, Backup.MODE_REPLACE) }
            .setNegativeButton("取消", null).show()
    }

    private fun applyRestore(bytes: ByteArray, mode: Int) {
        try {
            val current = store.load(); val result = Backup.restore(this, java.io.ByteArrayInputStream(bytes), current, mode)
            store.save(current); Photos.gc(this, current)
            toast("恢复完成：新增 ${result.added} 条，图片 ${result.photos} 张")
            render()
        } catch (e: Exception) { toast("恢复失败：${e.message.orEmpty()}") }
    }

    private fun progress(message: String) = AlertDialog.Builder(this).setMessage(message).setCancelable(false).create().apply { show() }
    private fun text(v: String, s: Float, c: Int, b: Boolean) = TextView(this).apply { text = v; textSize = s; setTextColor(c); if (b) setTypeface(typeface, android.graphics.Typeface.BOLD) }
    private fun margin(top: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(v: String) = Toast.makeText(this, v, Toast.LENGTH_SHORT).show()
}
