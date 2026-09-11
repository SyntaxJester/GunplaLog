package com.syntaxjester.gunplalog

import android.app.AlertDialog
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var store: Store
    private val items = mutableListOf<Item>()
    private lateinit var adapter: ItemAdapter
    private lateinit var homeAdapter: ItemAdapter
    private lateinit var caseAdapter: ShowCaseAdapter

    private var filterGrade = "全部"
    private var query = ""
    private var sortMode = 0
    private var currentTab = 0

    // 日历选中日期
    private lateinit var calendarView: CalendarWeekView
    private var selectedCal = Calendar.getInstance()

    // 备份恢复
    private val backupLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument(Backup.MIME)) { uri ->
            if (uri != null) doLocalBackup(uri)
        }
    private val restoreLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) doLocalRestore(uri)
        }

    // ─── Tab 容器引用 ───
    private lateinit var tabHome: View
    private lateinit var tabItems: View
    private lateinit var tabShowcase: View
    private lateinit var tabMe: View
    private lateinit var navAdd: View

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashGuard.install(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)
            // 状态栏占位
            ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.container)) { v, insets ->
                v.setPadding(0, insets.getInsets(WindowInsetsCompat.Type.statusBars()).top, 0, 0)
                WindowInsetsCompat.CONSUMED
            }
            store = Store(this)
            items.addAll(store.load())
            sortMode = store.prefs.getInt("sortMode", 0)

            initViews()
            buildFilterPills()
            refresh()
            CrashGuard.step(this, "[5] activity.onCreate complete")
            CrashGuard.consumePrevReport()?.let { showCrashDialog(it) }
        } catch (t: Throwable) {
            CrashGuard.logThrowable(this, t)
            CrashGuard.step(this, "[X] crashed: " + t.javaClass.name)
            showFatal(t)
        }
    }

    // ━━━━━━━━━━━━━━━━━ 初始化 ━━━━━━━━━━━━━━━━━

    private fun initViews() {
        tabHome = findViewById(R.id.tabHome)
        tabItems = findViewById(R.id.tabItems)
        tabShowcase = findViewById(R.id.tabShowcase)
        tabMe = findViewById(R.id.tabMe)
        navAdd = findViewById(R.id.navAdd)

        // 物品 tab
        val rv = tabItems.findViewById<RecyclerView>(R.id.recycler)
        adapter = ItemAdapter(
            onClick = { openEdit(it) },
            onLongClick = { confirmDelete(it) },
            onPhotoClick = { PhotoViewer.show(this, it.photo) }
        )
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        // 首页和物品页不能共享同一个 adapter：共享时刷新首页会覆盖物品列表
        val rvHome = tabHome.findViewById<RecyclerView>(R.id.rvHome)
        homeAdapter = ItemAdapter(
            onClick = { openEdit(it) },
            onLongClick = { confirmDelete(it) },
            onPhotoClick = { PhotoViewer.show(this, it.photo) }
        )
        rvHome.layoutManager = LinearLayoutManager(this)
        rvHome.adapter = homeAdapter

        tabItems.findViewById<EditText>(R.id.etSearch).addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString()?.trim() ?: ""
                refresh()
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        tabItems.findViewById<View>(R.id.btnStats).setOnClickListener { showStats() }

        // 展架 tab
        caseAdapter = ShowCaseAdapter(
            onItemClick = { openEdit(it) },
            onPhotoClick = { PhotoViewer.show(this, it.photo) },
            onItemLong = { confirmDelete(it) }
        )
        val caseRv = tabShowcase.findViewById<RecyclerView>(R.id.rvShowcase)
        caseRv.layoutManager = GridLayoutManager(this, 2)
        caseRv.adapter = caseAdapter

        // 我的 tab：完全按参考页组织资产管理和更多功能
        tabMe.findViewById<View>(R.id.btnMeStats).setOnClickListener { showStats() }
        tabMe.findViewById<View>(R.id.btnMeWishlist).setOnClickListener { showWishlist() }
        tabMe.findViewById<View>(R.id.btnMeCollab).setOnClickListener { manageNameList("协作管理", "collaborators", "协作者") }
        tabMe.findViewById<View>(R.id.btnMeCategory).setOnClickListener { showCategoryManager() }
        tabMe.findViewById<View>(R.id.btnMeCabinet).setOnClickListener { manageNameList("柜子管理", "cabinets", "柜子") }
        tabMe.findViewById<View>(R.id.btnMeLocation).setOnClickListener { showLocationManager() }
        tabMe.findViewById<View>(R.id.btnMeBackup).setOnClickListener { pickBackupTarget() }
        tabMe.findViewById<View>(R.id.btnMeSettings).setOnClickListener { showSettingsMenu() }
        tabMe.findViewById<View>(R.id.btnMeFeedback).setOnClickListener { openGithubRepository() }
        tabMe.findViewById<View>(R.id.btnMeAbout).setOnClickListener { showAbout() }
        tabMe.findViewById<View>(R.id.btnMeContact).setOnClickListener { showContact() }
        tabMe.findViewById<View>(R.id.btnEditProfile).setOnClickListener { editProfileName() }
        tabMe.findViewById<View>(R.id.avatarView).setOnClickListener { editProfileName() }

        // 日历周条
        calendarView = CalendarWeekView(this)
        calendarView.listener = { cal ->
            selectedCal = cal.clone() as Calendar
            refreshHome()
        }
        val calContainer = tabHome.findViewById<ViewGroup>(R.id.calendarContainer)
        calContainer.addView(calendarView.getContainer())

        // 底部导航：必须绑定 nav* 按钮，不能绑到 tab 内容容器
        switchTab(0)
        setupNav(findViewById(R.id.navHome), 0)
        setupNav(findViewById(R.id.navItems), 1)
        navAdd.setOnClickListener { openEdit(null) }
        setupNav(findViewById(R.id.navShowcase), 3)
        setupNav(findViewById(R.id.navMe), 4)
    }

    private fun setupNav(view: View, index: Int) {
        view.setOnClickListener { switchTab(index) }
    }

    private fun switchTab(idx: Int) {
        currentTab = idx
        val tabs = intArrayOf(
            R.id.tabHome, R.id.tabItems, -1, R.id.tabShowcase, R.id.tabMe
        )
        val views = arrayOf(tabHome, tabItems, null, tabShowcase, tabMe)
        val navIds = intArrayOf(
            R.id.navHome, R.id.navItems, R.id.navAdd, R.id.navShowcase, R.id.navMe
        )
        val iconIds = intArrayOf(
            R.id.navHomeIcon, R.id.navItemsIcon, -1, R.id.navShowcaseIcon, R.id.navMeIcon
        )
        val textIds = intArrayOf(
            R.id.navHomeText, R.id.navItemsText, -1, R.id.navShowcaseText, R.id.navMeText
        )

        for (i in 0..4) {
            if (i == 2) continue // 中央加号无选中态
            val v = views[i] ?: continue
            v.visibility = if (i == idx) View.VISIBLE else View.GONE
            val isSel = (i == idx)
            val navV = findViewById<View>(navIds[i])
            navV.isSelected = isSel
            if (iconIds[i] != -1) {
                val iv = findViewById<ImageView>(iconIds[i])
                iv.isSelected = isSel
                if (isSel) iv.setColorFilter(Color.parseColor("#1E4FD6"))
                else iv.setColorFilter(Color.parseColor("#9AA0AC"))
            }
            if (textIds[i] != -1) {
                val tv = findViewById<TextView>(textIds[i])
                if (isSel) tv.setTextColor(Color.parseColor("#1E4FD6"))
                else tv.setTextColor(Color.parseColor("#9AA0AC"))
            }
        }

        if (idx == 0) refreshHome()
        if (idx == 3) refreshShowcase()
        if (idx == 4) refreshMe()
    }

    // ━━━━━━━━━━━━━━━━━ 刷新 ━━━━━━━━━━━━━━━━━

    private fun refresh() {
        adapter.submit(visibleItems())
        val rv = tabItems.findViewById<RecyclerView>(R.id.recycler)
        val ev = tabItems.findViewById<View>(R.id.emptyView)
        rv.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        ev.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        val spent = items.filter { it.status == 1 }.sumOf { it.price }
        tabItems.findViewById<TextView>(R.id.tvSubtitle)
            .text = getString(R.string.subtitle_fmt, items.size, spent)
        refreshHome()
        calendarView.refreshMarks()
    }

    private fun refreshHome() {
        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(selectedCal.time)
        val dayItems = items.filter {
            it.date == dateKey
        }
        val rvHome = tabHome.findViewById<RecyclerView>(R.id.rvHome)
        val homeEmpty = tabHome.findViewById<LinearLayout>(R.id.homeEmpty)
        val tvEmpty = tabHome.findViewById<TextView>(R.id.tvHomeEmpty)
        rvHome.visibility = if (dayItems.isEmpty()) View.GONE else View.VISIBLE
        homeEmpty.visibility = if (dayItems.isEmpty()) View.VISIBLE else View.GONE
        if (dayItems.isEmpty()) {
            tvEmpty.text = getString(R.string.home_empty_today)
        }
        homeAdapter.submit(dayItems)
        // 更新日历标记
        calendarView.markedDates = items.map { it.date }.toSet()
        calendarView.refreshMarks()
    }

    private fun refreshShowcase() {
        val withPhotos = items.filter { it.photo.isNotBlank() }
        caseAdapter.submit(withPhotos)
        val rv = tabShowcase.findViewById<RecyclerView>(R.id.rvShowcase)
        val empty = tabShowcase.findViewById<View>(R.id.showcaseEmpty)
        rv.visibility = if (withPhotos.isEmpty()) View.GONE else View.VISIBLE
        empty.visibility = if (withPhotos.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun refreshMe() {
        val owned = items.filter { it.status == 1 }
        val spent = owned.sumOf { it.price }
        tabMe.findViewById<TextView>(R.id.tvMeTotal).text = items.size.toString()
        tabMe.findViewById<TextView>(R.id.tvMeSpent).text = "¥" + String.format("%.0f", spent)
        tabMe.findViewById<TextView>(R.id.tvMeTypes).text =
            items.map { it.grade }.distinct().size.toString()
        val name = store.prefs.getString("profileName", "游客")?.trim().orEmpty().ifBlank { "游客" }
        tabMe.findViewById<TextView>(R.id.tvMeName).text = name
        tabMe.findViewById<TextView>(R.id.tvMeNameHint).text =
            if (name == "游客") getString(R.string.me_name_hint) else "已记录 $name 的收藏旅程"
    }

    private fun editProfileName() {
        val input = EditText(this).apply {
            setSingleLine(true)
            hint = "输入你的昵称"
            setText(store.prefs.getString("profileName", "游客").orEmpty().takeIf { it != "游客" }.orEmpty())
            setSelection(text.length)
        }
        val pad = (24 * resources.displayMetrics.density).toInt()
        val frame = android.widget.FrameLayout(this).apply {
            setPadding(pad, 0, pad, 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle("编辑昵称")
            .setView(frame)
            .setPositiveButton("保存") { _, _ ->
                store.prefs.edit().putString("profileName", input.text.toString().trim().ifBlank { "游客" }).apply()
                refreshMe()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle("关于我们")
            .setMessage(getString(R.string.app_version) + "\n\n高达记物用于记录模型、收藏和心愿清单。数据默认保存在本机，可通过数据导出或坚果云 WebDAV 备份。\n\n开源仓库：\nhttps://github.com/SyntaxJester/GunplaLog")
            .setPositiveButton("打开 GitHub") { _, _ -> openGithubRepository() }
            .setNegativeButton(android.R.string.ok, null)
            .show()
    }

    private fun showFeatureInfo(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showWishlist() {
        val wanted = items.filter { it.status == 0 }
        val text = if (wanted.isEmpty()) "心愿单还是空的。\n在物品编辑页把状态设为「想买」即可加入。"
        else wanted.joinToString("\n") { "• ${it.name}" }
        showFeatureInfo("心愿单（${wanted.size}）", text)
    }

    private fun showCategoryManager() {
        val counts = items.groupingBy { it.grade }.eachCount().toList().sortedByDescending { it.second }
        val text = if (counts.isEmpty()) "暂无分类。添加模型后会按规格自动归类。"
        else counts.joinToString("\n") { "${it.first}　${it.second} 件" }
        showFeatureInfo("分类管理", text)
    }

    private fun showLocationManager() {
        manageNameList("位置管理", "locations", "位置")
    }

    private fun manageNameList(title: String, key: String, itemLabel: String) {
        fun entries(): MutableList<String> = store.prefs.getString(key, "").orEmpty()
            .split("\n").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        fun openList() {
            val data = entries()
            val rows = (data + "＋ 添加$itemLabel").toTypedArray()
            AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(rows) { _, which ->
                    if (which == data.size) {
                        val input = EditText(this).apply { hint = "输入$itemLabel名称"; setSingleLine(true) }
                        AlertDialog.Builder(this)
                            .setTitle("添加$itemLabel")
                            .setView(input)
                            .setPositiveButton("保存") { _, _ ->
                                val value = input.text.toString().trim()
                                if (value.isNotEmpty()) {
                                    data.add(value)
                                    store.prefs.edit().putString(key, data.joinToString("\n")).apply()
                                    toast("已添加$value")
                                }
                            }
                            .setNegativeButton(android.R.string.cancel, null).show()
                    } else {
                        AlertDialog.Builder(this)
                            .setTitle(data[which])
                            .setMessage("是否删除此$itemLabel？")
                            .setPositiveButton("删除") { _, _ ->
                                data.removeAt(which)
                                store.prefs.edit().putString(key, data.joinToString("\n")).apply()
                            }
                            .setNegativeButton(android.R.string.cancel, null).show()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null).show()
        }
        openList()
    }

    private fun showSettingsMenu() {
        AlertDialog.Builder(this)
            .setTitle("设置")
            .setItems(arrayOf("恢复数据", "坚果云同步", "编辑昵称")) { _, which ->
                when (which) {
                    0 -> pickRestoreSource()
                    1 -> openCloudSetting()
                    else -> editProfileName()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openGithubRepository() {
        val uri = Uri.parse("https://github.com/SyntaxJester/GunplaLog")
        try {
            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
        } catch (_: Exception) {
            toast("GitHub：https://github.com/SyntaxJester/GunplaLog")
        }
    }

    private fun showContact() {
        AlertDialog.Builder(this)
            .setTitle("联系我们")
            .setMessage("GitHub 仓库：\nhttps://github.com/SyntaxJester/GunplaLog\n\nQQ：Privat5418")
            .setPositiveButton("打开 GitHub") { _, _ -> openGithubRepository() }
            .setNeutralButton("复制 QQ") { _, _ ->
                val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("QQ", "Privat5418"))
                toast("已复制 QQ：Privat5418")
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ━━━━━━━━━━━━━━━━━ 排序 / 筛选 ━━━━━━━━━━━━━━━━━

    private fun visibleItems(): List<Item> {
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

    private fun buildFilterPills() {
        val row = tabItems.findViewById<LinearLayout>(R.id.pillRow)
        row.removeAllViews()
        val grades = listOf("全部") + Grades.ALL
        grades.forEach { g ->
            val pill = Pills.header(this, g)
            pill.isSelected = (g == filterGrade)
            pill.setOnClickListener {
                filterGrade = g
                Pills.select(row, pill)
                refresh()
            }
            row.addView(pill, Pills.rowParams(this))
        }
    }

    // ━━━━━━━━━━━━━━━━━ 编辑 / 删除 ━━━━━━━━━━━━━━━━━

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

    // ━━━━━━━━━━━━━━━━━ 统计 ━━━━━━━━━━━━━━━━━

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
        Grades.ALL.forEach { g -> val c = counts[g] ?: 0; if (c > 0) sb.append('\n').append(g).append(" × ").append(c) }
        val max = items.maxByOrNull { it.price }
        if (max != null && max.price > 0) sb.append("\n\n").append(getString(R.string.stats_top_fmt, max.name, max.price))
        AlertDialog.Builder(this)
            .setTitle(R.string.menu_stats)
            .setMessage(sb.toString())
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // ━━━━━━━━━━━━━━━━━ 备份恢复 ━━━━━━━━━━━━━━━━━

    private fun pickBackupTarget() {
        if (items.isEmpty()) { toast(getString(R.string.backup_empty)); return }
        AlertDialog.Builder(this)
            .setTitle(R.string.backup_pick_title)
            .setItems(arrayOf(getString(R.string.backup_local), getString(R.string.backup_cloud))) { _, w ->
                if (w == 0) backupLauncher.launch(Backup.defaultName()) else cloudBackup()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun pickRestoreSource() {
        AlertDialog.Builder(this)
            .setTitle(R.string.restore_pick_title)
            .setItems(arrayOf(getString(R.string.restore_local), getString(R.string.restore_cloud))) { _, w ->
                if (w == 0) restoreLauncher.launch(arrayOf("*/*")) else cloudRestore()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openCloudSetting() { CloudSheet().show(supportFragmentManager, "cloud") }

    private fun doLocalBackup(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { Backup.write(this, items, it) }
            toast(getString(R.string.backup_done_fmt, items.size))
        } catch (e: Exception) { toast(getString(R.string.backup_fail_fmt, e.message ?: "")) }
    }

    private fun doLocalRestore(uri: Uri) {
        val bytes = try { contentResolver.openInputStream(uri)?.use { it.readBytes() } } catch (e: Exception) { null }
        if (bytes == null || bytes.isEmpty()) { toast(getString(R.string.restore_bad_file)); return }
        confirmAndRestore(bytes)
    }

    private fun cloudBackup() {
        val cfg = WebDav.load(this)
        if (!cfg.configured) { promptCloudSetup(); return }
        val dlg = progress(getString(R.string.backup_uploading))
        val name = Backup.defaultName()
        Thread { val err = try { WebDav.upload(cfg, name, Backup.toBytes(this, items)) } catch (e: Exception) { e.message ?: "" }
            runOnUiThread { dismiss(dlg); toast(if (err == null) getString(R.string.backup_cloud_done_fmt, name) else getString(R.string.backup_fail_fmt, err)) }
        }.start()
    }

    private fun cloudRestore() {
        val cfg = WebDav.load(this)
        if (!cfg.configured) { promptCloudSetup(); return }
        val dlg = progress(getString(R.string.cloud_listing))
        Thread {
            val list = try { WebDav.list(cfg) } catch (e: Exception) { emptyList() }
            runOnUiThread { dismiss(dlg); if (list.isEmpty()) toast(getString(R.string.cloud_list_empty)) else showCloudPicker(cfg, list) }
        }.start()
    }

    private fun showCloudPicker(cfg: WebDav.Config, list: List<WebDav.Entry>) {
        val labels = list.map { it.name + if (it.size > 0) " · " + (it.size / 1024).coerceAtLeast(1) + " KB" else "" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.cloud_pick_title)
            .setItems(labels) { _, w -> downloadAndRestore(cfg, list[w].name) }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun downloadAndRestore(cfg: WebDav.Config, name: String) {
        val dlg = progress(getString(R.string.restore_downloading))
        Thread { val bytes = try { WebDav.download(cfg, name) } catch (e: Exception) { null }
            runOnUiThread { dismiss(dlg); if (bytes == null) toast("下载失败") else confirmAndRestore(bytes) }
        }.start()
    }

    private fun confirmAndRestore(bytes: ByteArray) {
        val meta = Backup.peek(java.io.ByteArrayInputStream(bytes))
        if (meta == null) { toast(getString(R.string.restore_bad_file)); return }
        val count = meta.optInt("count", 0)
        val created = meta.optString("createdAtText", "未知")
        AlertDialog.Builder(this)
            .setTitle(R.string.restore_mode_title)
            .setMessage(getString(R.string.restore_mode_msg_fmt, count, created))
            .setPositiveButton(R.string.restore_mode_merge) { _, _ -> applyRestore(bytes, Backup.MODE_MERGE) }
            .setNeutralButton(R.string.restore_mode_replace) { _, _ -> applyRestore(bytes, Backup.MODE_REPLACE) }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun applyRestore(bytes: ByteArray, mode: Int) {
        try {
            val r = Backup.restore(this, java.io.ByteArrayInputStream(bytes), items, mode)
            store.save(items); Photos.gc(this, items); buildFilterPills(); refresh()
            toast(getString(R.string.restore_done_fmt, r.added, r.skipped, r.photos))
        } catch (e: Exception) { toast(getString(R.string.restore_fail_fmt, e.message ?: "")) }
    }

    private fun promptCloudSetup() {
        AlertDialog.Builder(this)
            .setMessage(R.string.cloud_not_configured)
            .setPositiveButton(R.string.cloud_goto_setting) { _, _ -> openCloudSetting() }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    // ━━━━━━━━━━━━━━━━━ UI 工具 ━━━━━━━━━━━━━━━━━

    private fun toast(msg: String) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }

    private fun showFatal(t: Throwable) {
        val sb = StringBuilder().append("GunplaLog 启动失败诊断\n\n")
        CrashGuard.consumePrevReport()?.let { sb.append("[上次运行记录]\n").append(it).append("\n\n") }
        val sw = java.io.StringWriter()
        t.printStackTrace(java.io.PrintWriter(sw))
        sb.append("[本次崩溃堆栈]\n").append(sw.toString())
        val tv = TextView(this).apply { setText(sb.toString()); textSize = 11f; setPadding(40, 40, 40, 40); setTextIsSelectable(true) }
        setContentView(android.widget.ScrollView(this).apply { addView(tv) })
    }

    private fun showCrashDialog(text: String) {
        val tv = TextView(this).apply { setText(text); textSize = 11f; setPadding(48, 32, 48, 32); setTextIsSelectable(true) }
        AlertDialog.Builder(this)
            .setTitle("上次运行异常退出")
            .setView(android.widget.ScrollView(this).apply { addView(tv) })
            .setPositiveButton("复制日志") { _, _ -> CrashGuard.copy(this, text); Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show() }
            .setNegativeButton("知道了") { _, _ -> CrashGuard.clear(this) }
            .show()
    }

    private fun progress(msg: String): AlertDialog {
        val pad = (24 * resources.displayMetrics.density).toInt()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(pad, pad, pad, pad)
            addView(android.widget.ProgressBar(this@MainActivity))
            addView(TextView(this@MainActivity).apply { text = msg; textSize = 14f; setPadding(pad / 2, 0, 0, 0) })
        }
        return AlertDialog.Builder(this).setView(row).setCancelable(false).create().also { it.show() }
    }

    private fun dismiss(d: AlertDialog?) { try { d?.dismiss() } catch (_: Exception) {} }
}
