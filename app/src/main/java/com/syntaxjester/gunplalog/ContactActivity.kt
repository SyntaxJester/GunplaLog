package com.syntaxjester.gunplalog

import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
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

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_me_page)
        }

        // 标题栏
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_card_header)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val backBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_manager_back)
            setBackgroundResource(R.drawable.bg_manager_circle)
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        backBtn.setOnClickListener { finish() }
        header.addView(backBtn, LinearLayout.LayoutParams(dp(44), dp(44)))
        header.addView(TextView(this).apply {
            text = "联系我们"
            textSize = 20f
            setTextColor(Color.parseColor("#171A20"))
            setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            weight = 1f
            marginStart = dp(14)
        })
        root.addView(header, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // 内容区域
        val scrollView = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }

        // GitHub 卡片
        val githubCard = cardRow(
            R.drawable.ic_more_github,
            "GitHub",
            "开源仓库 & Issue",
            null
        ) {
            startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://github.com/SyntaxJester/GunplaLog")))
        }
        content.addView(githubCard)

        // QQ 卡片
        val qqCard = cardRow(
            R.drawable.ic_more_contact,
            "QQ",
            "Privat5418",
            null
        ) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("QQ", "Privat5418"))
            toast("已复制 QQ：Privat5418")
        }
        content.addView(qqCard)

        // 说明文字
        content.addView(TextView(this).apply {
            text = "如有功能建议、Bug 反馈或合作意向，欢迎通过以上渠道联系。\nGitHub 仓库开源，欢迎 Star & Fork。"
            textSize = 13f
            setTextColor(Color.parseColor("#8E99A9"))
            setPadding(dp(4), dp(12), 0, 0)
        })

        scrollView.addView(content, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0).apply { weight = 1f })

        setContentView(root)
    }

    private fun cardRow(iconRes: Int, title: String, subtitle: String, url: String?, click: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
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
            body.addView(TextView(this@ContactActivity).apply {
                text = title
                textSize = 16f
                setTextColor(Color.parseColor("#171A20"))
                setTypeface(typeface, Typeface.BOLD)
            })
            if (subtitle.isNotBlank()) {
                body.addView(TextView(this@ContactActivity).apply {
                    text = subtitle
                    textSize = 13f
                    setTextColor(Color.parseColor("#8E99A9"))
                    setPadding(0, dp(4), 0, 0)
                })
            }
            addView(body, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                weight = 1f
                marginStart = dp(12)
            })

            addView(ImageView(this@ContactActivity).apply {
                setImageResource(R.drawable.ic_arrow_right)
            }, LinearLayout.LayoutParams(dp(18), dp(18)).apply { gravity = Gravity.END })

            setOnClickListener { click() }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density + .5f).toInt()
    private fun toast(msg: String) = android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
}