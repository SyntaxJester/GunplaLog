package com.syntaxjester.gunplalog

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var store: Store
    private val items = mutableListOf<Item>()
    private lateinit var adapter: ItemAdapter
    private lateinit var tvSubtitle: TextView
    private lateinit var tvCount: TextView
    private lateinit var emptyView: View
    private lateinit var rv: RecyclerView
    private lateinit var pillRow: LinearLayout

    private var filterGrade = "全部"
    private var query = ""
    private var sortMode = 0

    private val backupLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument(Backup.MIME)) { uri ->
            if (uri != null) doLocalBackup(uri)
        }
    private val restoreLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) doLocalRestore(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashGuard.install(this)
        // 沉浸式：内容绘制到状态栏下方
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        try {
            setupUi()
            CrashGuard.step(this, "[5] activity.onCreate complete")
            CrashGuard.consumePrevReport()?.let { showCrashDialog(it) }
        } catch (t: Throwable) {
            CrashGuard.logThrowable(this, t)
            CrashGuard.step(this, "[X] crashed: " + t.javaClass.name)
            showFatal(t)
        }
    }

    private fun showFatal(t: Throwable) {
        val sb = StringBuilder()
        sb.append("GunplaLog 启动失败诊断\n\n")
        CrashGuard.prevReport()?.let { sb.append("[上次运行记录]\n").append(it).append("\n\n") }
        val sw = java.io.StringWriter()
        t.printStackTrace(java.io.PrintWriter(sw))
        sb.append("[本次崩溃堆栈]\n").append(sw.toString())
        val tv = TextView(this).apply {
            setText(sb.toString())
            textSize = 11f
            setPadding(40, 120, 40, 40)
            setTextIsSelectable(true)
        }
        setContentView(android.widget.ScrollView(this).apply { addView(tv) })
    }

    private fun setupUi() {
        setContentView(R.layout.activity_main)
        CrashGuard.step(this, "[3] setContentView ok")

        // 把状态栏高度补到头部 padding 上，避免标题被状态栏压住
        val header = findViewById<View>(R.id.header)
        val basePadding = header.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(v.paddingLeft, basePadding + top, v.paddingRight, v.paddingBottom)
            insets
        }

        store = Store(this)
        items.addAll(store.load())
        sortMode = store.prefs.getInt("sortMode", 0)

        rv = findViewById(R.id.recycler)
        emptyView = findViewById(R.id.emptyView)
        tvSubtitle = findViewById(R.id.tvSubtitle)
        tvCount = findViewById(R.id.tvCount)
        pillRow = findViewById(R.id.pillRow)

        adapter = ItemAdapter(
            onClick = { openEdit(it) },
            onLongClick = { confirmDelete(it) },
            onPhotoClick = { PhotoViewer.show(this, it.photo) }
        )
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        buildFilterPills()

        findViewById<EditText>(R.id.etSearch).addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString()?.trim() ?: ""
                refresh()
            }

            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        findViewById<View>(R.id.btnMore).setOnClickListener { showMenu(it) }
        findViewById<View>(R.id.fabAdd).setOnClickListener { openEdit(null) }
        findViewById<View>(R.id.btnStats).setOnClickListener { showStats() }

        refresh()
        CrashGuard.step(this, "[4] first refresh ok")

        // 清理没有被任何记录引用的孤儿图片
        Photos.gc(this, items)
    }

    /** 首页筛选胶囊：全部 + 18 个规格，代码创建保证选中态配色正确 */
    private fun buildFilterPills() {
        pillRow.removeAllViews()
        val labels = mutableListOf("全部")
        labels.addAll(Grades.ALL)
        labels.forEach { label ->
            val pill = Pills.header(this, label)
            pill.isSelected = (label == filterGrade)
            pill.setOnClickListener {
                filterGrade = label
                Pills.select(pillRow, pill)
                refresh()
            }
            pillRow.addView(pill, Pills.rowParams(this))
        }
    }

    private fun showCrashDialog(text: String) {
        val tv = TextView(this).apply {
            setText(text)
            textSize = 11f
            setPadding(48, 32, 48, 32)
            setTextIsSelectable(true)
        }
        val scroll = android.widget.ScrollView(this).apply { addView(tv) }
        AlertDialog.Builder(this)
            .setTitle("上次运行异常退出")
            .setView(scroll)
            .setPositiveButton("复制日志") { _, _ ->
                CrashGuard.copy(this, text)
                Toast.makeText(this, "已复制，可粘贴发给开发者", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("知道了") { _, _ -> CrashGuard.clear(this) }
            .show()
    }

    // ---------- 列表 ----------

    private fun visible(): List<Item> {
        val q = query.lowercase()
        var list = items.filter {
            (filterGrade == "全部" || it.grade == filterGrade) &&
                    (q.isEmpty() || it.name.lowercase().contains(q) || it.note.lowercase().contains(q))
        }
        list = when (sortMode) {
            1 -> list.sortedWith(compareBy({ it.date }, { it.createdAt }))
            2 -> list.sortedWith(compareByDescending<Item> { it.price }.thenByDescending { it.createdAt })
            3 -> list.sortedWith(compareBy({ it.price }, { it.createdAt }))
            4 -> list.sortedBy { it.name }
            else -> list.sortedWith(compareByDescending<Item> { it.date }.thenByDescending { it.createdAt })
        }
        return list
    }

    private fun refresh() {
        val shown = visible()
        adapter.submit(shown)
        rv.visibility = if (shown.isEmpty()) View.GONE else View.VISIBLE
        emptyView.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        val spent = items.filter { it.status == 1 }.sumOf { it.price }
        tvSubtitle.text = getString(R.string.subtitle_fmt, items.size, spent)
        tvCount.text = getString(R.string.count_fmt, items.size)
    }

    // ---------- 编辑 / 删除 ----------

    private fun openEdit(item: Item?) {
        val sheet = EditSheet()
        if (item != null) {
            val args = Bundle()
            args.putString("item", item.toJson().toString())
            sheet.arguments = args
        }
        sheet.onSaved = { saved ->
            val idx = items.indexOfFirst { it.id == saved.id }
            if (idx >= 0) items[idx] = saved else items.add(saved)
            store.save(items)
            refresh()
        }
        sheet.show(supportFragmentManager, "edit")
    }

    private fun confirmDelete(item: Item) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_title)
            .setMessage(getString(R.string.delete_msg_fmt, item.name))
            .setPositiveButton(R.string.delete_ok) { _, _ ->
                Photos.delete(this, item.photo)
                items.removeAll { it.id == item.id }
                store.save(items)
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---------- 菜单 ----------

    private fun showMenu(anchor: View) {
        val popup = androidx.appcompat.widget.PopupMenu(this, anchor)
        val sorts = arrayOf(
            getString(R.string.sort_date_desc), getString(R.string.sort_date_asc),
            getString(R.string.sort_price_desc), getString(R.string.sort_price_asc),
            getString(R.string.sort_name)
        )
        val sub = popup.menu.addSubMenu(R.string.menu_sort)
        sorts.forEachIndexed { i, s -> sub.add(9, 100 + i, i, s) }
        sub.setGroupCheckable(9, true, true)
        if (sortMode in sorts.indices) sub.getItem(sortMode).isChecked = true

        popup.menu.add(getString(R.string.menu_stats))
        popup.menu.add(getString(R.string.menu_backup))
        popup.menu.add(getString(R.string.menu_restore))
        popup.menu.add(getString(R.string.menu_cloud))

        popup.setOnMenuItemClickListener { mi ->
            when {
                mi.itemId >= 100 -> {
                    sortMode = mi.itemId - 100
                    store.prefs.edit().putInt("sortMode", sortMode).apply()
                    refresh()
                    true
                }
                mi.title == getString(R.string.menu_stats) -> { showStats(); true }
                mi.title == getString(R.string.menu_backup) -> { pickBackupTarget(); true }
                mi.title == getString(R.string.menu_restore) -> { pickRestoreSource(); true }
                mi.title == getString(R.string.menu_cloud) -> { openCloudSetting(); true }
                else -> false
            }
        }
        popup.show()
    }

    // ---------- 统计 ----------

    private fun showStats() {
        val owned = items.filter { it.status == 1 }
        val wanted = items.filter { it.status == 0 }
        val sold = items.filter { it.status == 2 }
        val spent = owned.sumOf { it.price }
        val sb = StringBuilder()
        sb.append(getString(R.string.stats_total_fmt, items.size)).append('\n')
        sb.append(getString(R.string.stats_owned_fmt, owned.size, spent)).append('\n')
        sb.append(getString(R.string.stats_wanted_fmt, wanted.size, sold.size)).append("\n\n")
        val counts = items.groupingBy { it.grade }.eachCount()
        sb.append(getString(R.string.stats_by_grade))
        Grades.ALL.forEach { g ->
            val c = counts[g] ?: 0
            if (c > 0) sb.append('\n').append(g).append(" × ").append(c)
        }
        val max = items.maxByOrNull { it.price }
        if (max != null && max.price > 0) {
            sb.append("\n\n").append(getString(R.string.stats_top_fmt, max.name, max.price))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.menu_stats)
            .setMessage(sb.toString())
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // ---------- 备份 / 恢复 ----------

    private fun pickBackupTarget() {
        if (items.isEmpty()) {
            toast(getString(R.string.backup_empty))
            return
        }
        val opts = arrayOf(getString(R.string.backup_local), getString(R.string.backup_cloud))
        AlertDialog.Builder(this)
            .setTitle(R.string.backup_pick_title)
            .setItems(opts) { _, which ->
                if (which == 0) backupLauncher.launch(Backup.defaultName())
                else cloudBackup()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun pickRestoreSource() {
        val opts = arrayOf(getString(R.string.restore_local), getString(R.string.restore_cloud))
        AlertDialog.Builder(this)
            .setTitle(R.string.restore_pick_title)
            .setItems(opts) { _, which ->
                if (which == 0) restoreLauncher.launch(arrayOf("*/*"))
                else cloudRestore()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openCloudSetting() {
        val sheet = CloudSheet()
        sheet.show(supportFragmentManager, "cloud")
    }

    // --- 本地 ---

    private fun doLocalBackup(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { os ->
                Backup.write(this, items, os)
            } ?: throw IllegalStateException("无法写入所选位置")
            toast(getString(R.string.backup_done_fmt, items.size))
        } catch (e: Exception) {
            toast(getString(R.string.backup_fail_fmt, e.message ?: e.javaClass.simpleName))
        }
    }

    private fun doLocalRestore(uri: Uri) {
        val bytes = try {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            toast(getString(R.string.restore_fail_fmt, e.message ?: ""))
            return
        }
        if (bytes == null || bytes.isEmpty()) {
            toast(getString(R.string.restore_bad_file))
            return
        }
        confirmAndRestore(bytes)
    }

    // --- 云端 ---

    private fun cloudBackup() {
        val cfg = WebDav.load(this)
        if (!cfg.configured) {
            promptCloudSetup()
            return
        }
        val dlg = progress(getString(R.string.backup_uploading))
        val name = Backup.defaultName()
        Thread {
            val err: String? = try {
                WebDav.upload(cfg, name, Backup.toBytes(this, items))
            } catch (e: Exception) {
                e.message ?: e.javaClass.simpleName
            }
            runOnUiThread {
                dismiss(dlg)
                if (err == null) toast(getString(R.string.backup_cloud_done_fmt, name))
                else toast(getString(R.string.backup_fail_fmt, err))
            }
        }.start()
    }

    private fun cloudRestore() {
        val cfg = WebDav.load(this)
        if (!cfg.configured) {
            promptCloudSetup()
            return
        }
        val dlg = progress(getString(R.string.cloud_listing))
        Thread {
            var err: String? = null
            var list: List<WebDav.Entry> = emptyList()
            try {
                list = WebDav.list(cfg)
            } catch (e: Exception) {
                err = e.message ?: e.javaClass.simpleName
            }
            val finalList = list
            val finalErr = err
            runOnUiThread {
                dismiss(dlg)
                when {
                    finalErr != null -> toast(getString(R.string.restore_fail_fmt, finalErr))
                    finalList.isEmpty() -> toast(getString(R.string.cloud_list_empty))
                    else -> showCloudPicker(cfg, finalList)
                }
            }
        }.start()
    }

    private fun showCloudPicker(cfg: WebDav.Config, list: List<WebDav.Entry>) {
        val labels = list.map { e ->
            val kb = if (e.size > 0) " · " + (e.size / 1024).coerceAtLeast(1) + " KB" else ""
            e.name + kb
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.cloud_pick_title)
            .setItems(labels) { _, which -> downloadAndRestore(cfg, list[which].name) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun downloadAndRestore(cfg: WebDav.Config, name: String) {
        val dlg = progress(getString(R.string.restore_downloading))
        Thread {
            var bytes: ByteArray? = null
            var err: String? = null
            try {
                bytes = WebDav.download(cfg, name)
            } catch (e: Exception) {
                err = e.message ?: e.javaClass.simpleName
            }
            val finalBytes = bytes
            val finalErr = err
            runOnUiThread {
                dismiss(dlg)
                if (finalErr != null || finalBytes == null) {
                    toast(getString(R.string.restore_fail_fmt, finalErr ?: ""))
                } else {
                    confirmAndRestore(finalBytes)
                }
            }
        }.start()
    }

    // --- 恢复确认 ---

    private fun confirmAndRestore(bytes: ByteArray) {
        val meta = Backup.peek(java.io.ByteArrayInputStream(bytes))
        if (meta == null) {
            toast(getString(R.string.restore_bad_file))
            return
        }
        val count = meta.optInt("count", meta.optJSONArray("items")?.length() ?: 0)
        val created = meta.optString("createdAtText").ifBlank { "未知时间" }
        AlertDialog.Builder(this)
            .setTitle(R.string.restore_mode_title)
            .setMessage(getString(R.string.restore_mode_msg_fmt, count, created))
            .setPositiveButton(R.string.restore_mode_merge) { _, _ ->
                applyRestore(bytes, Backup.MODE_MERGE)
            }
            .setNeutralButton(R.string.restore_mode_replace) { _, _ ->
                applyRestore(bytes, Backup.MODE_REPLACE)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun applyRestore(bytes: ByteArray, mode: Int) {
        try {
            val r = Backup.restore(this, java.io.ByteArrayInputStream(bytes), items, mode)
            store.save(items)
            Photos.gc(this, items)
            buildFilterPills()
            refresh()
            toast(getString(R.string.restore_done_fmt, r.added, r.skipped, r.photos))
        } catch (e: Exception) {
            toast(getString(R.string.restore_fail_fmt, e.message ?: e.javaClass.simpleName))
        }
    }

    private fun promptCloudSetup() {
        AlertDialog.Builder(this)
            .setMessage(R.string.cloud_not_configured)
            .setPositiveButton(R.string.cloud_goto_setting) { _, _ -> openCloudSetting() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---------- 小工具 ----------

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun progress(msg: String): AlertDialog {
        val pad = (24 * resources.displayMetrics.density).toInt()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(android.widget.ProgressBar(this@MainActivity))
            addView(TextView(this@MainActivity).apply {
                text = msg
                textSize = 14f
                setPadding(pad / 2, 0, 0, 0)
            })
        }
        val d = AlertDialog.Builder(this).setView(row).setCancelable(false).create()
        d.show()
        return d
    }

    private fun dismiss(d: AlertDialog?) {
        try {
            d?.dismiss()
        } catch (_: Exception) {
        }
    }
}
