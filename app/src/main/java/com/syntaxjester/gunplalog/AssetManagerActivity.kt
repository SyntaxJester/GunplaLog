package com.syntaxjester.gunplalog

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

/** 分类、位置与柜子的完整资产管理页面。 */
class AssetManagerActivity : AppCompatActivity() {
    private lateinit var store: Store
    private var mode = "category"
    private val expandedCategories = mutableSetOf<String>()
    private var pendingPhoto: (String) -> Unit = {}
    private val blue = Color.parseColor("#2E7EF0")
    private val dark = Color.parseColor("#171A20")
    private val muted = Color.parseColor("#7E8BA0")
    private val gallery = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) Photos.importFrom(this, uri)?.let { pendingPhoto(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        store = Store(this)
        mode = intent.getStringExtra("mode") ?: "category"
        render()
    }

    private fun render() { when (mode) {
        "category" -> categoryPage()
        "cabinet" -> entityPage("柜子管理", "点击查看柜内物品，长按可删除", "cabinets", "柜子")
        else -> entityPage("位置管理", "管理物品的存放位置与位置图片", "locations", "位置")
    } }

    private fun page() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setBackgroundResource(R.drawable.bg_me_page)
        setPadding(dp(12), dp(12), dp(12), dp(16))
    }

    private fun header(title: String, subtitle: String, add: (() -> Unit)? = null) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(iconButton(R.drawable.ic_manager_back) { finish() }, LinearLayout.LayoutParams(dp(54), dp(54)))
        addView(LinearLayout(this@AssetManagerActivity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, dp(8), 0)
            addView(txt(title, 22f, dark, true)); addView(txt(subtitle, 12f, muted, false).apply { maxLines = 1 })
        }, LinearLayout.LayoutParams(0, dp(54), 1f))
        if (add != null) addView(iconButton(R.drawable.ic_manager_add, add), LinearLayout.LayoutParams(dp(54), dp(54)))
    }

    private fun categoryPage() {
        val values = AssetStore.categories(this)
        val root = page(); root.addView(header("分类管理", "管理一级分类与子分类") { editCategory(null) })
        values.forEachIndexed { index, entry ->
            root.addView(categoryCard(entry, index), margin(14))
        }
        root.addView(primary("＋  新建一级分类") { editCategory(null) }, margin(20))
        setContentView(scroll(root))
    }

    private fun categoryCard(entry: ManagedEntry, index: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setBackgroundResource(R.drawable.bg_manager_card); setPadding(dp(14), dp(11), dp(14), dp(11))
        val expanded = entry.id in expandedCategories
        val top = LinearLayout(this@AssetManagerActivity).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(View(this@AssetManagerActivity).apply { setBackgroundColor(Color.parseColor(if (index % 2 == 0) "#FF666B" else "#61A7F5")) }, LinearLayout.LayoutParams(dp(6), dp(40)).apply { marginEnd = dp(13) })
            addView(LinearLayout(this@AssetManagerActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(txt(entry.name, 18f, dark, true)); addView(txt("一级分类 · ${entry.children.size} 个子分类", 12f, muted, false))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(txt(if (expanded) "⌃" else "⌄", 23f, muted, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(36), dp(40)))
            addView(txt("⋮", 24f, muted, true).apply { gravity = Gravity.CENTER; setOnClickListener { categoryMenu(entry) } }, LinearLayout.LayoutParams(dp(36), dp(40)))
            setOnClickListener { if (expanded) expandedCategories.remove(entry.id) else expandedCategories.add(entry.id); render() }
        }
        addView(top)
        if (expanded) entry.children.forEach { child ->
            addView(LinearLayout(this@AssetManagerActivity).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(dp(20), dp(6), 0, dp(6))
                addView(View(this@AssetManagerActivity).apply { setBackgroundColor(Color.parseColor("#FFB46D")) }, LinearLayout.LayoutParams(dp(5), dp(27)).apply { marginEnd = dp(14) })
                addView(txt(child, 16f, dark, true), LinearLayout.LayoutParams(0, dp(29), 1f))
                addView(txt("⋮", 21f, muted, true).apply { gravity = Gravity.CENTER; setOnClickListener { childMenu(entry, child) } }, LinearLayout.LayoutParams(dp(36), dp(29)))
            })
        }
    }

    private fun categoryMenu(entry: ManagedEntry) {
        AlertDialog.Builder(this).setTitle(entry.name).setItems(arrayOf("添加子分类", "重命名", "删除一级分类")) { _, i ->
            when (i) { 0 -> childEditor(entry, null); 1 -> editCategory(entry); else -> {
                val all = AssetStore.categories(this); all.removeAll { it.id == entry.id }; AssetStore.save(this, "categories", all); render()
            } }
        }.show()
    }
    private fun childMenu(entry: ManagedEntry, child: String) {
        AlertDialog.Builder(this).setTitle(child).setItems(arrayOf("重命名", "删除子分类")) { _, i ->
            if (i == 0) childEditor(entry, child) else { entry.children.remove(child); persistCategory(entry); render() }
        }.show()
    }
    private fun editCategory(old: ManagedEntry?) {
        val input = EditText(this).apply { hint = "一级分类名称"; setText(old?.name.orEmpty()); setSingleLine(true) }
        AlertDialog.Builder(this).setTitle(if (old == null) "新建一级分类" else "编辑一级分类").setView(input).setPositiveButton("保存") { _, _ ->
            val name = input.text.toString().trim(); if (name.isNotEmpty()) {
                val all = AssetStore.categories(this); if (old == null) all.add(ManagedEntry(name = name)) else old.name = name
                AssetStore.save(this, "categories", all); render()
            }
        }.setNegativeButton("取消", null).show()
    }
    private fun childEditor(parent: ManagedEntry, old: String?) {
        val input = EditText(this).apply { hint = "子分类名称"; setText(old.orEmpty()); setSingleLine(true) }
        AlertDialog.Builder(this).setTitle(if (old == null) "添加子分类" else "编辑子分类").setView(input).setPositiveButton("保存") { _, _ ->
            val name = input.text.toString().trim(); if (name.isNotEmpty()) { if (old != null) parent.children.remove(old); parent.children.add(name); persistCategory(parent); render() }
        }.setNegativeButton("取消", null).show()
    }
    private fun persistCategory(entry: ManagedEntry) {
        val all = AssetStore.categories(this)
        val index = all.indexOfFirst { it.id == entry.id }
        if (index >= 0) all[index] = entry else all.add(entry)
        AssetStore.save(this, "categories", all)
    }

    private fun entityPage(title: String, subtitle: String, key: String, noun: String) {
        val values = AssetStore.entries(this, key)
        val root = page(); root.addView(header(title, subtitle) { entityEditor(key, noun, null) })
        if (values.isEmpty()) {
            root.addView(emptyEntity(noun) { entityEditor(key, noun, null) }, margin(90))
        } else values.forEach { entry -> root.addView(entityCard(entry, key, noun), margin(13)) }
        setContentView(scroll(root))
    }

    private fun emptyEntity(noun: String, click: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setBackgroundResource(R.drawable.bg_manager_card); setPadding(dp(18), dp(56), dp(18), dp(56))
        addView(ImageView(this@AssetManagerActivity).apply { setImageResource(if (noun == "柜子") R.drawable.ic_cabinet_empty else R.drawable.ic_asset_location) }, LinearLayout.LayoutParams(dp(104), dp(104)))
        addView(txt("还没有$noun", 23f, dark, true).apply { setPadding(0, dp(16), 0, 0) })
        addView(txt("点击右上角＋，创建第一个${if (noun == "柜子") "收藏柜" else "存放位置"}", 14f, muted, false).apply { setPadding(0, dp(9), 0, dp(18)) })
        addView(primary("创建$noun", click), LinearLayout.LayoutParams(dp(150), dp(42)))
    }

    private fun entityCard(entry: ManagedEntry, key: String, noun: String) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL; setBackgroundResource(R.drawable.bg_manager_card); setPadding(dp(14), dp(13), dp(14), dp(13))
        val preview = ImageView(this@AssetManagerActivity).apply {
            if (entry.photo.isNotBlank()) Photos.decode(this@AssetManagerActivity, entry.photo, 160)?.let { setImageBitmap(it) } ?: setImageResource(if (noun == "柜子") R.drawable.ic_asset_cabinet else R.drawable.ic_asset_location)
            else setImageResource(if (noun == "柜子") R.drawable.ic_asset_cabinet else R.drawable.ic_asset_location)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        addView(preview, LinearLayout.LayoutParams(dp(54), dp(54)))
        addView(LinearLayout(this@AssetManagerActivity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, dp(6), 0)
            addView(txt(entry.name, 18f, dark, true)); addView(txt(if (noun == "柜子") entry.template else "点击管理存放位置", 13f, muted, false))
        }, LinearLayout.LayoutParams(0, dp(58), 1f))
        addView(txt("⋮", 27f, muted, true).apply { gravity = Gravity.CENTER; setOnClickListener { entityMenu(entry, key, noun) } }, LinearLayout.LayoutParams(dp(40), dp(52)))
        setOnClickListener { entityEditor(key, noun, entry) }
    }

    private fun entityMenu(entry: ManagedEntry, key: String, noun: String) {
        AlertDialog.Builder(this).setTitle(entry.name).setItems(arrayOf("编辑", "删除")) { _, i ->
            if (i == 0) entityEditor(key, noun, entry) else { val all = AssetStore.entries(this, key); all.removeAll { it.id == entry.id }; AssetStore.save(this, key, all); entry.photo.takeIf { it.isNotBlank() }?.let { Photos.delete(this, it) }; render() }
        }.show()
    }

    private fun entityEditor(key: String, noun: String, old: ManagedEntry?, templateOverride: String? = null) {
        val root = page(); var photo = old?.photo.orEmpty(); var selectedTemplate = templateOverride ?: old?.template ?: "云光玻璃柜"
        root.addView(header(if (old == null) "新建$noun" else "编辑$noun", if (noun == "柜子") "给柜子命名，并选择喜欢的陈列模板" else "填写位置名称，并可添加一张位置图片"))
        val name = EditText(this).apply { hint = "${noun}名称"; setText(old?.name.orEmpty()); textSize = 17f; setBackgroundResource(R.drawable.bg_entity_input); setPadding(dp(16), 0, dp(16), 0) }
        root.addView(txt("${noun}信息", 19f, dark, true), margin(26)); root.addView(name, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(10) })
        val preview = ImageView(this).apply { setBackgroundResource(R.drawable.bg_manager_card); scaleType = ImageView.ScaleType.CENTER_CROP; setPadding(dp(16), dp(16), dp(16), dp(16)) }
        fun updatePhoto() { if (photo.isBlank()) preview.setImageResource(if (noun == "柜子") R.drawable.ic_asset_cabinet else R.drawable.ic_asset_location) else Photos.decode(this, photo, 640)?.let { preview.setImageBitmap(it) } }
        if (noun == "位置") {
            root.addView(txt("位置图片", 19f, dark, true), margin(25)); updatePhoto()
            preview.setOnClickListener { pendingPhoto = { p -> photo = p; updatePhoto() }; gallery.launch("image/*") }
            root.addView(preview, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(164)).apply { topMargin = dp(10) })
            root.addView(txt("点击图片可从相册添加或更换位置图片", 13f, muted, false).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0) })
        } else {
            root.addView(txt("选择柜子模板", 19f, dark, true), margin(25))
            root.addView(txt("云光玻璃柜 · 9 个陈列位", 13f, muted, false).apply { setPadding(0, dp(5), 0, dp(9)) })
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            listOf("通用柜子", "云光玻璃柜").forEach { template ->
                val tile = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(8), dp(10), dp(8), dp(10)); setBackgroundResource(if (template == selectedTemplate) R.drawable.bg_template_selected else R.drawable.bg_template_normal)
                    addView(ImageView(this@AssetManagerActivity).apply { setImageResource(R.drawable.ic_asset_cabinet) }, LinearLayout.LayoutParams(dp(52), dp(52)))
                    addView(txt(template, 14f, if (template == selectedTemplate) blue else dark, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(5), 0, 0) })
                    setOnClickListener { entityEditor(key, noun, old, template) }
                }
                row.addView(tile, LinearLayout.LayoutParams(0, dp(118), 1f).apply { if (template == "通用柜子") marginEnd = dp(9) else marginStart = dp(9) })
            }
            root.addView(row)
        }
        root.addView(primary(if (old == null) "创建$noun" else "保存$noun") {
            val value = name.text.toString().trim(); if (value.isBlank()) { name.error = "请输入${noun}名称"; return@primary }
            val all = AssetStore.entries(this, key); val entry = old ?: ManagedEntry(name = value)
            entry.name = value; entry.photo = photo; entry.template = selectedTemplate
            if (all.none { it.id == entry.id }) all.add(entry); AssetStore.save(this, key, all); render()
        }, margin(32))
        setContentView(scroll(root))
    }

    private fun iconButton(icon: Int, click: () -> Unit) = ImageView(this).apply { setImageResource(icon); setBackgroundResource(R.drawable.bg_manager_circle); setPadding(dp(13), dp(13), dp(13), dp(13)); setOnClickListener { click() } }
    private fun primary(label: String, click: () -> Unit) = txt(label, 17f, Color.WHITE, true).apply { gravity = Gravity.CENTER; setBackgroundResource(R.drawable.bg_manager_primary); setOnClickListener { click() } }
    private fun txt(text: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply { this.text = text; textSize = size; setTextColor(color); if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD) }
    private fun scroll(root: LinearLayout) = ScrollView(this).apply { fitsSystemWindows = true; addView(root) }
    private fun margin(top: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density + .5f).toInt()
}
