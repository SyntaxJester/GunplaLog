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

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            if (uri != null) doExport(uri)
        }
    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) doImport(uri)
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
        popup.menu.add(getString(R.string.menu_export))
        popup.menu.add(getString(R.string.menu_import))

        popup.setOnMenuItemClickListener { mi ->
            when {
                mi.itemId >= 100 -> {
                    sortMode = mi.itemId - 100
                    store.prefs.edit().putInt("sortMode", sortMode).apply()
                    refresh()
                    true
                }
                mi.title == getString(R.string.menu_stats) -> { showStats(); true }
                mi.title == getString(R.string.menu_export) -> {
                    exportLauncher.launch("gunplalog_" + System.currentTimeMillis() + ".csv")
                    true
                }
                mi.title == getString(R.string.menu_import) -> {
                    importLauncher.launch(arrayOf("*/*"))
                    true
                }
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

    // ---------- CSV ----------

    private fun csvEsc(s: String): String =
        if (s.contains(',') || s.contains('"') || s.contains('\n')) {
            "\"" + s.replace("\"", "\"\"") + "\""
        } else s

    private fun doExport(uri: Uri) {
        try {
            val sb = StringBuilder()
            sb.append("名称,规格,比例,价格,购买日期,状态,备注").append('\n')
            for (it2 in items) {
                sb.append(csvEsc(it2.name)).append(',')
                    .append(it2.grade).append(',')
                    .append(csvEsc(it2.scale)).append(',')
                    .append(String.format(java.util.Locale.US, "%.2f", it2.price)).append(',')
                    .append(it2.date).append(',')
                    .append(Item.statusName(it2.status)).append(',')
                    .append(csvEsc(it2.note)).append('\n')
            }
            contentResolver.openOutputStream(uri)?.use { os ->
                os.write(sb.toString().toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(this, getString(R.string.export_done_fmt, items.size), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.export_fail_fmt, e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    private fun doImport(uri: Uri) {
        try {
            val text = contentResolver.openInputStream(uri)?.use { ins ->
                ins.readBytes().toString(Charsets.UTF_8)
            } ?: return
            var added = 0
            var skipped = 0
            for ((i, line) in text.lines().withIndex()) {
                val raw = line.trim()
                if (raw.isEmpty()) continue
                if (i == 0 && raw.startsWith("名称")) continue
                val cols = parseCsvLine(raw)
                if (cols.size < 4) { skipped++; continue }
                val name = cols[0].trim()
                if (name.isEmpty()) { skipped++; continue }
                val date = if (cols.size > 4) cols[4].trim() else ""
                if (items.any { it.name == name && it.date == date }) { skipped++; continue }
                items.add(
                    Item(
                        id = java.util.UUID.randomUUID().toString(),
                        name = name,
                        grade = Grades.normalize(cols[1]),
                        scale = cols[2].trim(),
                        price = cols[3].trim().toDoubleOrNull() ?: 0.0,
                        date = date,
                        status = statusFrom(if (cols.size > 5) cols[5].trim() else ""),
                        note = if (cols.size > 6) cols[6].trim() else "",
                        photo = "",
                        createdAt = System.currentTimeMillis()
                    )
                )
                added++
            }
            store.save(items)
            buildFilterPills()
            refresh()
            Toast.makeText(this, getString(R.string.import_done_fmt, added, skipped), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.import_fail_fmt, e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    private fun statusFrom(s: String): Int = when {
        s.contains("想买") -> 0
        s.contains("已出") -> 2
        else -> 1
    }

    private fun parseCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQ = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (inQ) {
                if (c == '"') {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"'); i++
                    } else inQ = false
                } else sb.append(c)
            } else when (c) {
                '"' -> inQ = true
                ',' -> { out.add(sb.toString()); sb.setLength(0) }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString())
        return out
    }
}
