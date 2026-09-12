package com.syntaxjester.gunplalog

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

/** 图示中的个性化设置：选项即时写入本地并影响物品卡片。 */
class SettingsActivity : AppCompatActivity() {
    private val dark = Color.parseColor("#161920")
    private val muted = Color.parseColor("#778398")
    private val prefs by lazy { getSharedPreferences("gunplalog", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        render()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_me_page)
            setPadding(dp(22), dp(18), dp(22), dp(32))
        }
        root.addView(header())
        root.addView(settingCard(
            title = "物品卡片",
            summary = "控制两列与三列模式中的辅助信息",
            option = "显示日期和服役天数",
            description = "开启后，右上角显示服役天数；其他辅助信息显示在贴纸与名称之间。\n\n卡片模式不会显示次数加减按钮；次数调整仅在列表模式提供。",
            key = "showDateService",
            defaultValue = false
        ), margin(top = 26))
        root.addView(settingCard(
            title = "价格信息",
            summary = "控制物品列表、两列和三列视图中的价格补充信息",
            option = "显示日均成本",
            description = "在蓝色价格右侧显示“日均：¥金额”，按购入总价与服役天数计算。",
            key = "showAverageCost",
            defaultValue = false
        ), margin(top = 22))
        setContentView(ScrollView(this).apply { fitsSystemWindows = true; addView(root) })
    }

    private fun header(): LinearLayout = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(ImageView(this@SettingsActivity).apply {
            setImageResource(R.drawable.ic_manager_back)
            setBackgroundResource(R.drawable.bg_manager_circle)
            setPadding(dp(13), dp(13), dp(13), dp(13))
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(54), dp(54)))
        addView(TextView(this@SettingsActivity).apply {
            text = "个性化"
            textSize = 28f
            setTextColor(dark)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(0, dp(54), 1f))
        addView(LinearLayout(this@SettingsActivity), LinearLayout.LayoutParams(dp(54), dp(54)))
    }

    private fun settingCard(title: String, summary: String, option: String, description: String, key: String, defaultValue: Boolean): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_manager_card)
            setPadding(dp(22), dp(22), dp(22), dp(22))
            addView(text(title, 23f, dark, true))
            addView(text(summary, 15f, muted, true).apply { setPadding(0, dp(12), 0, dp(24)) })
            addView(LinearLayout(this@SettingsActivity).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(text(option, 19f, dark, true), LinearLayout.LayoutParams(0, dp(48), 1f))
                addView(Switch(this@SettingsActivity).apply {
                    isChecked = prefs.getBoolean(key, defaultValue)
                    setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean(key, checked).apply() }
                })
            })
            addView(text(description, 15f, muted, true).apply {
                setLineSpacing(dp(4).toFloat(), 1f)
                setPadding(0, dp(8), 0, 0)
            })
        }
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }
    private fun margin(top: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
