package com.syntaxjester.gunplalog

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ManagerActivity : AppCompatActivity() {
    private lateinit var store: Store
    private var mode = "stats"
    private val blue = Color.parseColor("#2E7EF0")
    private val dark = Color.parseColor("#171A20")
    private val muted = Color.parseColor("#8792A3")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        store = Store(this)
        mode = intent.getStringExtra("mode") ?: "stats"
        render()
    }

    private fun render() {
        when (mode) {
            "wishlist" -> renderWishlist()
            "cabinet" -> renderNameManager("柜子管理", "点击查看柜内物品，长按可删除", "cabinets", "柜子")
            "location" -> renderNameManager("位置管理", "管理物品的存放位置与位置备注", "locations", "位置")
            "category" -> renderCategory()
            else -> renderStats()
        }
    }

    private fun page(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(18), dp(20), dp(26))
        setBackgroundResource(R.drawable.bg_app_paper)
    }

    private fun header(title: String, subtitle: String, add: (() -> Unit)? = null): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(ImageView(this@ManagerActivity).apply {
                setImageResource(R.drawable.ic_manager_back)
                setBackgroundResource(R.drawable.bg_manager_circle)
                setPadding(dp(14), dp(14), dp(14), dp(14))
                setOnClickListener { finish() }
            }, LinearLayout.LayoutParams(dp(58), dp(58)))
            addView(LinearLayout(this@ManagerActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), 0, dp(8), 0)
                addView(label(title, 28f, dark, true))
                addView(label(subtitle, 14f, muted, true).apply { maxLines = 1 })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (add != null) addView(ImageView(this@ManagerActivity).apply {
                setImageResource(R.drawable.ic_manager_add)
                setBackgroundResource(R.drawable.bg_manager_circle)
                setPadding(dp(13), dp(13), dp(13), dp(13))
                setOnClickListener { add() }
            }, LinearLayout.LayoutParams(dp(58), dp(58)))
        }
    }

    private fun renderStats() {
        val items = store.load()
        val owned = items.filter { it.status == 1 }
        val total = owned.sumOf { it.price }
        val root = page()
        root.addView(header("资产统计", "查看收藏价值、品类与状态", null))
        val date = SimpleDateFormat("yyyy.MM.dd", Locale.US).format(Date())
        root.addView(card().apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(label("日期范围", 14f, dark, true), LinearLayout.LayoutParams(0, dp(44), 1f))
            addView(label("首次记录 — $date  ›", 13f, muted, true))
        }, margins(top = 18))
        root.addView(card().apply {
            addView(label("我的资产", 18f, dark, true))
            addView(rowOfStats(listOf("当前资产价值" to money(total), "物品数量" to owned.size.toString(), "今日成本" to money(0.0))))
        }, margins(top = 14))
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(metricCard("二手回收", money(0.0), "查看回收明细 ›", "#E8F8F1"), LinearLayout.LayoutParams(0, dp(126), 1f).apply { marginEnd = dp(7) })
            addView(metricCard("历史投入", money(total), "全部购入成本", "#FFF3DE"), LinearLayout.LayoutParams(0, dp(126), 1f).apply { marginStart = dp(7) })
        }, margins(top = 14))
        root.addView(section("趋势图", "阶段日均成本：${money(if (owned.isEmpty()) 0.0 else total / owned.size)}", "已根据 ${owned.size} 件收藏计算当前资产趋势"), margins(top = 14))
        val grades = items.groupingBy { it.grade }.eachCount().entries.sortedByDescending { it.value }
        root.addView(section("资产分布", "按当前资产价值查看品类占比", if (grades.isEmpty()) "所选日期内还没有可统计的资产" else grades.joinToString("　") { "${it.key} ${it.value}" }), margins(top = 14))
        root.addView(section("品类分布", "按物品数量查看主要收藏品类", if (grades.isEmpty()) "所选日期内还没有品类数据" else grades.joinToString("\n") { "${it.key}　${it.value} 件" }), margins(top = 14))
        root.addView(label("状态统计", 19f, dark, true).apply { setPadding(0, dp(20), 0, dp(8)) })
        root.addView(statusCard("退役统计", items.count { it.status == 2 }, "回收金额 ${money(0.0)}", "#E7F8F1"))
        root.addView(statusCard("心愿统计", items.count { it.status == 0 }, "预计投入 ${money(items.filter { it.status == 0 }.sumOf { it.price })}", "#FFF3DE"), margins(top = 10))
        setContentView(ScrollView(this).apply { fitsSystemWindows = true; addView(root) })
    }

    private fun renderWishlist() {
        val items = store.load()
        val wanted = items.filter { it.status == 0 }
        val root = page()
        root.addView(header("心愿管理", "把冲动留给冷静，把喜欢留给时间") { openNewWish() })
        root.addView(card().apply {
            orientation = LinearLayout.HORIZONTAL
            listOf("冷静期\n0", "心愿清单\n${wanted.size}", "已实现\n${items.count { it.status == 1 }}", "断念\n${items.count { it.status == 2 }}").forEachIndexed { i, s ->
                addView(label(s, 14f, if (i == 1) blue else muted, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, dp(76), 1f))
            }
        }, margins(top = 18))
        if (wanted.isEmpty()) {
            root.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, dp(150), 0, 0)
                addView(ImageView(this@ManagerActivity).apply { setImageResource(R.drawable.ic_asset_wishlist) }, LinearLayout.LayoutParams(dp(96), dp(96)))
                addView(label("还没有种下心愿", 24f, dark, true).apply { setPadding(0, dp(22), 0, 0) })
                addView(label("先记下来，给喜欢一点时间", 16f, muted, true).apply { setPadding(0, dp(12), 0, dp(28)) })
                addView(label("添加一个心愿", 17f, Color.WHITE, true).apply {
                    gravity = Gravity.CENTER; setBackgroundResource(R.drawable.bg_manager_primary); setOnClickListener { openNewWish() }
                }, LinearLayout.LayoutParams(dp(190), dp(52)))
            })
        } else wanted.forEach { item ->
            root.addView(itemCard(item).apply { setOnClickListener { openWishEditor(item) } }, margins(top = 12))
        }
        setContentView(ScrollView(this).apply { fitsSystemWindows = true; addView(root) })
    }

    private fun openNewWish() {
        val sheet = EditSheet()
        sheet.onSaved = { saved -> store.save(store.load().apply { add(saved) }); render() }
        sheet.show(supportFragmentManager, "wish")
    }

    private fun openWishEditor(item: Item) {
        val sheet = EditSheet().apply {
            arguments = Bundle().apply { putString("item", item.toJson().toString()) }
            onSaved = { saved ->
                val all = store.load()
                val index = all.indexOfFirst { it.id == saved.id }
                if (index >= 0) all[index] = saved else all.add(saved)
                store.save(all)
                render()
            }
        }
        sheet.show(supportFragmentManager, "wish-edit")
    }

    private fun renderCategory() {
        val items = store.load()
        val counts = items.groupingBy { it.grade }.eachCount().toList().sortedByDescending { it.second }
        val root = page()
        root.addView(header("分类管理", "按规格查看模型分类", null))
        if (counts.isEmpty()) {
            root.addView(card().apply {
                gravity = Gravity.CENTER
                setPadding(dp(20), dp(80), dp(20), dp(80))
                addView(ImageView(this@ManagerActivity).apply { setImageResource(R.drawable.ic_asset_category) }, LinearLayout.LayoutParams(dp(90), dp(90)))
                addView(label("还没有分类数据", 25f, dark, true).apply { setPadding(0, dp(20), 0, 0) })
                addView(label("添加物品后会按规格自动归类", 16f, muted, true).apply { setPadding(0, dp(12), 0, 0) })
            }, margins(top = 120))
        } else counts.forEach { (grade, count) ->
            root.addView(card().apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                addView(ImageView(this@ManagerActivity).apply { setImageResource(R.drawable.ic_asset_category) }, LinearLayout.LayoutParams(dp(48), dp(48)))
                addView(label(grade, 18f, dark, true).apply { setPadding(dp(14), 0, 0, 0) }, LinearLayout.LayoutParams(0, dp(56), 1f))
                addView(label("$count 件  ›", 15f, muted, true))
                setOnClickListener { AlertDialog.Builder(this@ManagerActivity).setTitle(grade).setItems(items.filter { it.grade == grade }.map { it.name }.toTypedArray(), null).setPositiveButton("关闭", null).show() }
            }, margins(top = 12))
        }
        setContentView(ScrollView(this).apply { fitsSystemWindows = true; addView(root) })
    }

    private fun renderNameManager(title: String, subtitle: String, key: String, noun: String) {
        val names = nameEntries(key)
        val root = page()
        root.addView(header(title, subtitle) { addName(key, noun) })
        if (names.isEmpty()) {
            root.addView(card().apply {
                gravity = Gravity.CENTER
                setPadding(dp(20), dp(72), dp(20), dp(72))
                if (mode == "cabinet") addView(ImageView(this@ManagerActivity).apply { setImageResource(R.drawable.ic_asset_cabinet) }, LinearLayout.LayoutParams(dp(94), dp(94)))
                addView(label("还没有$noun", 26f, dark, true).apply { setPadding(0, dp(18), 0, 0) })
                addView(label("点击右上角＋，创建第一个${if (noun == "柜子") "收藏柜" else "存放位置"}", 16f, muted, true).apply { setPadding(0, dp(14), 0, 0) })
            }, margins(top = 150))
        } else names.forEach { name ->
            root.addView(card().apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                addView(ImageView(this@ManagerActivity).apply { setImageResource(if (mode == "cabinet") R.drawable.ic_asset_cabinet else R.drawable.ic_asset_location) }, LinearLayout.LayoutParams(dp(48), dp(48)))
                addView(LinearLayout(this@ManagerActivity).apply {
                    orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0)
                    addView(label(name, 18f, dark, true)); addView(label("轻触查看，长按删除", 13f, muted, false))
                }, LinearLayout.LayoutParams(0, dp(66), 1f))
                setOnClickListener { AlertDialog.Builder(this@ManagerActivity).setTitle(name).setMessage("当前没有关联物品。可在物品备注中填写此${noun}名称。 ").setPositiveButton("知道了", null).show() }
                setOnLongClickListener { deleteName(key, noun, name); true }
            }, margins(top = 12))
        }
        setContentView(ScrollView(this).apply { fitsSystemWindows = true; addView(root) })
    }

    private fun addName(key: String, noun: String) {
        val input = EditText(this).apply { hint = "输入${noun}名称"; setSingleLine(true) }
        AlertDialog.Builder(this).setTitle("添加$noun").setView(input).setPositiveButton("保存") { _, _ ->
            val value = input.text.toString().trim()
            if (value.isNotEmpty()) { val data = nameEntries(key); if (value !in data) data.add(value); saveNames(key, data); render() }
        }.setNegativeButton("取消", null).show()
    }

    private fun deleteName(key: String, noun: String, name: String) {
        AlertDialog.Builder(this).setTitle("删除$noun").setMessage("确定删除「${name}」吗？").setPositiveButton("删除") { _, _ ->
            saveNames(key, nameEntries(key).apply { remove(name) }); render()
        }.setNegativeButton("取消", null).show()
    }

    private fun nameEntries(key: String) = store.prefs.getString(key, "").orEmpty().split("\n").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
    private fun saveNames(key: String, data: List<String>) = store.prefs.edit().putString(key, data.joinToString("\n")).apply()
    private fun itemCard(item: Item) = card().apply { addView(label(item.name, 18f, dark, true)); addView(label("${item.grade} · ${item.scale} · ${money(item.price)}", 14f, muted, false)) }

    private fun rowOfStats(data: List<Pair<String, String>>) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(18), 0, 0)
        data.forEach { (a, b) -> addView(LinearLayout(this@ManagerActivity).apply { orientation = LinearLayout.VERTICAL; addView(label(a, 12f, muted, false)); addView(label(b, 20f, dark, true)) }, LinearLayout.LayoutParams(0, dp(62), 1f)) }
    }
    private fun metricCard(a: String, b: String, c: String, color: String) = card(color).apply { addView(label(a, 13f, muted, false)); addView(label(b, 21f, dark, true)); addView(label(c, 12f, muted, false)) }
    private fun section(title: String, sub: String, body: String) = card().apply { addView(label(title, 19f, dark, true)); addView(label(sub, 13f, muted, false)); addView(label(body, 14f, muted, false).apply { gravity = Gravity.CENTER; setBackgroundResource(R.drawable.bg_manager_placeholder); setPadding(dp(16), dp(28), dp(16), dp(28)) }) }
    private fun statusCard(title: String, count: Int, right: String, color: String) = card(color).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; addView(label("$title\n$count 件", 15f, dark, true), LinearLayout.LayoutParams(0, dp(58), 1f)); addView(label("$right  ›", 14f, dark, true)) }
    private fun card(color: String = "#FFFFFF") = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16)); background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.parseColor(color)); cornerRadius = dp(20).toFloat(); setStroke(dp(1), Color.parseColor("#DFE5ED")) } }
    private fun label(s: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply { text = s; textSize = size; setTextColor(color); if (bold) setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER_VERTICAL }
    private fun margins(top: Int = 0) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top) }
    private fun money(v: Double) = "¥" + String.format(Locale.US, "%.2f", v)
    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()
}
