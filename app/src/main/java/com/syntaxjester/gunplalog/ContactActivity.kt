package com.syntaxjester.gunplalog

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat

class ContactActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(root())
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun root() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.bg_me_page)
        addView(header())
        addView(contentScrollView(), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0
        ).apply { weight = 1f })
    }

    private fun header() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundResource(R.drawable.bg_card_header)
        setPadding(dp(16), dp(14), dp(16), dp(14))
        addView(ImageView(this@ContactActivity).apply {
            setImageResource(R.drawable.ic_manager_back)
            setBackgroundResource(R.drawable.bg_manager_circle)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        addView(txt("联系我们", 20f, Color.parseColor("#171A20"), true),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(14)
            })
    }

    private fun contentScrollView() = ScrollView(this).apply {
        fitsSystemWindows = true
        addView(LinearLayout(this@ContactActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))

            // GitHub 卡片
            addView(cardRow(R.drawable.ic_more_github, "GitHub", "开源仓库 & Issue", null) {
                startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/SyntaxJester/GunplaLog")))
            })

            // QQ 卡片
            addView(cardRow(R.drawable.ic_more_contact, "QQ", "Privat5418", null) {
                val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("QQ", "Privat5418"))
                toast("已复制 QQ：Privat5418")
            })

            // 说明文字
            val note = txt("如有功能建议、Bug 反馈或合作意向，欢迎通过以上渠道联系。\nGitHub 仓库开源，欢迎 Star & Fork。", 13f, Color.parseColor("#8E99A9"), false)
            note.setPadding(dp(4), dp(12), 0, 0)
            addView(note)
        })
    }

    private fun cardRow(iconRes: Int, title: String, subtitle: String, url: String?, click: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        background = ContextCompat.getDrawable(this@ContactActivity, R.drawable.bg_manager_card)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) }

        addView(ImageView(this@ContactActivity).apply {
            setImageResource(iconRes)
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        }, LinearLayout.LayoutParams(dp(44), dp(44)))

        val body = LinearLayout(this@ContactActivity).apply { orientation = LinearLayout.VERTICAL }
        body.addView(txt(title, 16f, Color.parseColor("#171A20"), true))
        if (subtitle.isNotBlank()) body.addView(txt(subtitle, 13f, Color.parseColor("#8E99A9"), false).apply { setPadding(0, dp(4), 0, 0) })
        addView(body, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT).apply { weight = 1f; marginStart = dp(12) })

        addView(ImageView(this@ContactActivity).apply {
            setImageResource(R.drawable.ic_arrow_right)
        }, LinearLayout.LayoutParams(dp(18), dp(18)).apply { gravity = Gravity.END })

        setOnClickListener { click() }
    }

    private fun txt(text: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density + .5f).toInt()
    private fun toast(msg: String) = android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
}