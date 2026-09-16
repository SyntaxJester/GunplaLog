package com.syntaxjester.gunplalog

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

/** 分组查看页面：按分类/品牌/柜子展示物品。 */
class GroupViewActivity : AppCompatActivity() {
    private lateinit var store: Store
    private var items = listOf<Item>()
    private var groupMode = 0 // 10=分类 11=品牌 12=柜子

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        store = Store(this)
        items = store.load().filter { it.status == 1 || it.status == 0 }
        groupMode = intent.getIntExtra("mode", 10)
        setContentView(root())
    }

    private fun root() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setBackgroundResource(R.drawable.bg_me_page)
        addView(header())
        addView(ScrollView(this@GroupViewActivity).apply {
            fitsSystemWindows = true
            addView(itemsList())
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0).apply { weight = 1f })
    }

    private fun header() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setBackgroundResource(R.drawable.bg_card_header)
        setPadding(dp(16), dp(14), dp(16), dp(14))
        addView(ImageView(this@GroupViewActivity).apply {
            setImageResource(R.drawable.ic_manager_back)
            setBackgroundResource(R.drawable.bg_manager_circle)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        val modeTitle = when (groupMode) { 10 -> "按 IP 查看"; 11 -> "按品牌查看"; else -> "按柜子查看" }
        addView(txt(modeTitle, 20f, Color.parseColor("#171A20"), true),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(14) })
    }

    private fun itemsList() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(16), dp(12), dp(32))
        val groups = buildGroups()
        groups.forEach { (label, groupList) ->
            addView(groupSection(label, groupList), margin(0))
        }
    }

    private fun buildGroups(): List<Pair<String, List<Item>>> {
        val keyOf: (Item) -> String = when (groupMode) {
            10 -> { item -> item.category.ifBlank { "未分类" } }
            11 -> { item -> item.brand.ifBlank { "未填品牌" } }
            12 -> { item -> item.cabinet.ifBlank { "未入柜" } }
            else -> return emptyList()
        }
        return items.groupBy(keyOf).toList().sortedBy { it.first }
    }

    private fun groupSection(label: String, list: List<Item>) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(LinearLayout(this@GroupViewActivity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(txt(label, 18f, Color.parseColor("#171A20"), true))
            addView(txt(" ${list.size} 个分类", 13f, Color.parseColor("#7E8BA0"), false))
            addView(View(this@GroupViewActivity).apply { setBackgroundColor(Color.parseColor("#E8ECF4")) },
                LinearLayout.LayoutParams(0, dp(24)).apply { weight = 1f; marginStart = dp(8); marginEnd = dp(8) })
            addView(ImageView(this@GroupViewActivity).apply { setImageResource(R.drawable.ic_arrow_right) },
                LinearLayout.LayoutParams(dp(18), dp(18)).apply { gravity = Gravity.CENTER_VERTICAL })
        })
        list.take(3).forEach { item ->
            addView(itemCard(item), margin(12))
        }
    }

    private fun itemCard(item: Item) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setBackgroundResource(R.drawable.bg_manager_card); setPadding(dp(10), dp(10), dp(10), dp(14))
        val img = ImageView(this@GroupViewActivity).apply {
            if (item.photo.isNotBlank()) Photos.decode(this@GroupViewActivity, item.photo, 320)?.let { setImageBitmap(it) }
            else setImageResource(R.drawable.ic_asset_location)
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(120)).apply {
                topMargin = dp(6); bottomMargin = dp(4)
            }
        }
        addView(img)
        addView(txt(item.name, 15f, Color.parseColor("#171A20"), true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        val priceLabel = if (item.price > 0) "¥%.2f".format(item.price) else "免费"
        addView(txt("1 件 · $priceLabel", 13f, Color.parseColor("#7E8BA0"), false).apply { setPadding(0, dp(4), 0, 0) })
        setOnClickListener { openItem(item) }
    }

    private fun openItem(item: Item) {
        val sheet = EditSheet()
        val args = Bundle()
        args.putString("item", item.toJson().toString())
        sheet.arguments = args
        sheet.show(supportFragmentManager, "edit")
    }

    private fun txt(text: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun scroll(v: View) = ScrollView(this).apply { fitsSystemWindows = true; addView(v) }
    private fun margin(top: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density + .5f).toInt()
}