package com.syntaxjester.gunplalog

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat

class FeedbackActivity : AppCompatActivity() {
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
            setOnClickListener { finish() }
        }
        header.addView(backBtn, LinearLayout.LayoutParams(dp(44), dp(44)))

        header.addView(TextView(this).apply {
            text = "意见反馈"
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
        content.addView(cardRow(
            R.drawable.ic_more_github,
            "GitHub Issue",
            "提交功能建议 / Bug 反馈",
            null
        ) {
            startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://github.com/SyntaxJester/GunplaLog/issues")))
        })

        // 复制 GitHub 链接
        content.addView(cardRow(
            R.drawable.ic_more_about,
            "复制仓库地址",
            "https://github.com/SyntaxJester/GunplaLog",
            "https://github.com/SyntaxJester/GunplaLog"
        ) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("GunplaLog GitHub", "https://github.com/SyntaxJester/GunplaLog"))
            toast("已复制 GitHub 仓库地址")
        })

        // 说明文字
        content.addView(TextView(this).apply {
            text = "感谢你的来信！\n反馈时建议附上问题截图、复现步骤和软件版本。"
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
            background = ContextCompat.getDrawable(this@FeedbackActivity, R.drawable.bg_manager_card)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }

            addView(ImageView(this@FeedbackActivity).apply {
                setImageResource(iconRes)
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            }, LinearLayout.LayoutParams(dp(44), dp(44)))

            val body = LinearLayout(this@FeedbackActivity).apply { orientation = LinearLayout.VERTICAL }
            body.addView(TextView(this@FeedbackActivity).apply {
                text = title
                textSize = 16f
                setTextColor(Color.parseColor("#171A20"))
                setTypeface(typeface, Typeface.BOLD)
            })
            if (subtitle.isNotBlank()) {
                body.addView(TextView(this@FeedbackActivity).apply {
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

            addView(ImageView(this@FeedbackActivity).apply {
                setImageResource(R.drawable.ic_arrow_right)
            }, LinearLayout.LayoutParams(dp(18), dp(18)).apply { gravity = Gravity.END })

            setOnClickListener { click() }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density + .5f).toInt()
    private fun toast(msg: String) = android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
}